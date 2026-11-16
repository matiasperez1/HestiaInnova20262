package cl.grupo28.hestia.data.auth

import cl.grupo28.hestia.domain.auth.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SupabaseAuthGateway(private val config: AuthConfiguration) : AuthGateway {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()

    override suspend fun exchange(idToken: String, rawNonce: String) = session(request("token?grant_type=id_token",
        JSONObject().put("provider", "google").put("id_token", idToken).put("nonce", rawNonce)))
    override suspend fun refresh(refreshToken: String) = session(request("token?grant_type=refresh_token",
        JSONObject().put("refresh_token", refreshToken), refreshing = true))
    override suspend fun validate(accessToken: String) = account(request("user", token = accessToken))
    override suspend fun logout(accessToken: String) {
        request("logout?scope=local", JSONObject(), accessToken)
    }
    private fun request(path: String, body: JSONObject? = null, token: String? = null, refreshing: Boolean = false): JSONObject {
        val builder = Request.Builder().url("${config.url.trimEnd('/')}/auth/v1/$path")
            .header("apikey", config.publishableKey).header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (body != null) builder.post(body.toString().toRequestBody("application/json".toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val code = runCatching { JSONObject(text).optString("error_code") }.getOrDefault("")
                val invalidToken = code in setOf("bad_jwt", "session_not_found", "session_expired", "user_not_found", "user_banned")
                val invalidRefresh = refreshing && code in setOf("refresh_token_not_found", "refresh_token_already_used")
                if (response.code in 400..499 && (invalidToken || invalidRefresh)) throw InvalidSessionException()
                // Do not propagate provider responses, request bodies or credentials to UI/logs.
                throw IOException("Authentication request failed")
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }
    private fun session(json: JSONObject): AuthSession {
        val expiresAt = json.optLong("expires_at", 0).takeIf { it > 0 }
            ?: (System.currentTimeMillis() / 1000 + json.getLong("expires_in"))
        val access = json.getString("access_token"); val refresh = json.getString("refresh_token")
        require(access.isNotBlank() && refresh.isNotBlank())
        return AuthSession(access, refresh, expiresAt, account(json.getJSONObject("user")))
    }
    private fun account(json: JSONObject): Account {
        val id = json.getString("id"); val email = json.getString("email")
        require(id.isNotBlank() && email.isNotBlank())
        return Account(id, email)
    }
}
