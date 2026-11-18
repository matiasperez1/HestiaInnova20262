package cl.grupo28.hestia.data.automatic

import cl.grupo28.hestia.BuildConfig
import cl.grupo28.hestia.domain.auth.SessionStore
import cl.grupo28.hestia.domain.automatic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class SessionAutomaticCredentials(private val sessions:SessionStore):AutomaticCredentials {
    override fun bearer(owner:String):String = sessions.load()?.takeIf {it.account.id==owner && it.expiresAt>System.currentTimeMillis()/1000}?.accessToken ?: throw AutomaticError("UNAUTHENTICATED")
    override fun isCurrent(owner:String)=sessions.load()?.account?.id==owner
}

/** The existing auth repository owns refresh. This client never persists or refreshes JWTs. */
class AutomaticApi(private val sessions: SessionStore, private val base: String = BuildConfig.HESTIA_API_URL,
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(40, TimeUnit.SECONDS).build(), private val credentials:AutomaticCredentials=SessionAutomaticCredentials(sessions)) : AutomaticGateway {
    override val configured = base.isNotBlank()
    private suspend fun request(owner: String, path: String, method: String = "GET", body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        if (!configured) throw AutomaticError("NOT_CONFIGURED")
        val token=credentials.bearer(owner)
        val request = Request.Builder().url(base.trimEnd('/') + path).header("Authorization", "Bearer ${token}")
            .method(method, if (method == "GET") null else (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            val source = response.body?.source() ?: throw AutomaticError("TEMPORARY_ERROR")
            if (source.request(512_001)) throw AutomaticError("INVALID_RESPONSE")
            val json = runCatching { JSONObject(source.readUtf8()) }.getOrElse { throw AutomaticError("INVALID_RESPONSE") }
            if (!response.isSuccessful) throw AutomaticError(json.optString("error", "TEMPORARY_ERROR"))
            // A logout/account change while a request is in flight invalidates its result.
            if (!credentials.isCurrent(owner)) throw AutomaticError("OWNER_CHANGED")
            json
        }
    }
    private fun nullable(j: JSONObject, key: String) = if (j.isNull(key)) null else j.optString(key).takeIf { it.isNotBlank() }
    private fun status(j: JSONObject) = AutomaticStatus(j.getString("status"), j.getBoolean("automaticActive"), nullable(j,"lastSync"), nullable(j,"watchExpiration"), nullable(j,"monitoringStartedAt"))
    override suspend fun status(owner: String) = status(request(owner,"/v1/gmail/status"))
    override suspend fun connect(owner: String, code: String) = status(request(owner,"/v1/gmail/connect","POST",JSONObject().put("serverAuthCode",code).put("timeZone",ZoneId.systemDefault().id)))
    override suspend fun sync(owner: String) { request(owner,"/v1/gmail/sync","POST") }
    override suspend fun disconnect(owner: String, deleteEvents: Boolean) = request(owner,"/v1/gmail/disconnect","POST",JSONObject().put("deleteEvents",deleteEvents)).getBoolean("remoteRevocationConfirmed")
    override suspend fun summary(owner: String): AutomaticSummary {
        val path = (base + "/v1/gmail/summary").toHttpUrl().newBuilder().addQueryParameter("timeZone", ZoneId.systemDefault().id).build().encodedPathAndQuery()
        val j=request(owner,path);return AutomaticSummary(j.getInt("analyzed"),j.getInt("suspicious"),j.getString("weekStart"),j.getString("weekEnd"))
    }
    private fun HttpUrl.encodedPathAndQuery() = encodedPath + (encodedQuery?.let { "?$it" } ?: "")
    private fun alert(j: JSONObject): GmailAlert {val r=j.getJSONArray("reasons");return GmailAlert(j.getString("id"),j.getString("received_at"),j.getString("risk_level"),j.optString("subject"),j.optString("sender"),List(r.length()){r.getString(it)},j.getBoolean("content_limited"))}
    override suspend fun alerts(owner: String, before: String?): AlertPage {val j=request(owner,"/v1/gmail/alerts"+(before?.let { "?before=$it" } ?: ""));val a=j.getJSONArray("items");return AlertPage(List(a.length()){alert(a.getJSONObject(it))},nullable(j,"nextCursor"))}
    override suspend fun alert(owner: String,id: String): GmailAlert {require(id.matches(Regex("[a-fA-F0-9-]{36}")));return alert(request(owner,"/v1/gmail/alerts/$id"))}
    override suspend fun register(owner: String, installation: String, token: String, enabled: Boolean) { request(owner,"/v1/devices/fcm-token","POST",JSONObject().put("installationId",installation).put("token",token).put("notificationsEnabled",enabled)) }
    override suspend fun unregister(owner: String, installation: String) { request(owner,"/v1/devices/fcm-token","DELETE",JSONObject().put("installationId",installation)) }
}
