package cl.grupo28.hestia.data.gmail

import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.domain.gmail.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit

class GmailApi(private val baseUrl: String = "https://gmail.googleapis.com/gmail/v1/users/me/") : GmailGateway {
    private val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).connectTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    override suspend fun profile(token: String): String = get(token, "profile", mapOf("fields" to "emailAddress"))!!.getString("emailAddress")
    override suspend fun list(token: String, week: WeekRange, page: String?): GmailPage {
        val start = week.start.atStartOfDay(week.zone).toEpochSecond() - 1
        val end = week.start.plusWeeks(1).atStartOfDay(week.zone).toEpochSecond()
        val params = mutableMapOf("labelIds" to "INBOX", "q" to "after:$start before:$end -in:drafts", "maxResults" to "100", "fields" to "messages(id),nextPageToken")
        if (page != null) params["pageToken"] = page
        val json = get(token, "messages", params)!!
        val array = json.optJSONArray("messages")
        return GmailPage((0 until (array?.length() ?: 0)).map { array!!.getJSONObject(it).getString("id") }, json.optString("nextPageToken").takeIf { it.isNotBlank() })
    }
    override suspend fun message(token: String, id: String): GmailMessage? {
        require(id.matches(Regex("[a-zA-Z0-9_-]+")))
        val json = get(token, "messages/$id", mapOf("format" to "full", "fields" to "id,internalDate,payload"), allowMissing = true) ?: return null
        return GmailMimeParser.parse(json)
    }
    private fun get(token: String, path: String, params: Map<String, String>, allowMissing: Boolean = false): JSONObject? {
        val url = (baseUrl + path).toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (allowMissing && response.code == 404) return null
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val kind = when {
                    response.code == 401 -> GmailAccessException.Kind.AUTHORIZATION
                    response.code == 429 -> GmailAccessException.Kind.QUOTA
                    response.code == 403 && (body.contains("accessNotConfigured") || body.contains("SERVICE_DISABLED")) -> GmailAccessException.Kind.API_DISABLED
                    response.code == 403 && (body.contains("rateLimitExceeded") || body.contains("userRateLimitExceeded") || body.contains("quotaExceeded")) -> GmailAccessException.Kind.QUOTA
                    response.code == 403 && (body.contains("insufficientPermissions") || body.contains("ACCESS_TOKEN_SCOPE_INSUFFICIENT")) -> GmailAccessException.Kind.AUTHORIZATION
                    else -> GmailAccessException.Kind.UNAVAILABLE
                }
                throw GmailAccessException(kind)
            }
            // Gmail responses may contain private bodies. Never log or cache them.
            val source = response.body?.source() ?: throw IOException("Empty Gmail response")
            val buffer = okio.Buffer()
            var total = 0L
            while (true) {
                val read = source.read(buffer, 8192)
                if (read == -1L) break
                total += read
                if (total > 2_000_000) throw IOException("Message exceeds local processing limit")
            }
            return JSONObject(buffer.readUtf8())
        }
    }
}

object GmailMimeParser {
    fun parse(json: JSONObject): GmailMessage {
        val payload = json.optJSONObject("payload") ?: JSONObject()
        fun header(part: JSONObject, name: String): String {
            val headers = part.optJSONArray("headers") ?: return ""
            return (0 until headers.length()).map { headers.getJSONObject(it) }
                .firstOrNull { it.optString("name").equals(name, true) }?.optString("value").orEmpty()
        }
        val text = StringBuilder()
        var limited = false
        var visited = 0
        fun visit(part: JSONObject, depth: Int) {
            if (++visited > 200 || depth > 15 || text.length >= 65536) { limited = true; return }
            val type = part.optString("mimeType").lowercase()
            if (part.optString("filename").isNotBlank() || type == "message/rfc822" || header(part,"Content-Disposition").trimStart().startsWith("attachment",true)) {
                limited = true
                return
            }
            val children = part.optJSONArray("parts")
            if (children != null) for (i in 0 until children.length()) {
                if (visited >= 200) { limited = true; break }
                visit(children.getJSONObject(i), depth + 1)
            }
            if (children == null && type !in setOf("text/plain", "text/html")) limited = true
            if (type == "text/plain" || type == "text/html") {
                val body = part.optJSONObject("body") ?: return
                val data = body.optString("data")
                if (data.isBlank()) { if (body.optString("attachmentId").isNotBlank()) limited = true; return }
                try {
                    val charset = Regex("charset=[\"']?([^\\s;\"']+)", RegexOption.IGNORE_CASE).find(header(part, "Content-Type"))?.groupValues?.get(1)
                    val decoded = Base64.getUrlDecoder().decode(data).toString(runCatching { charset?.let { java.nio.charset.Charset.forName(it) } }.getOrNull() ?: Charsets.UTF_8)
                    val remaining = 65536 - text.length
                    if (decoded.length > remaining) limited = true
                    text.append(decoded.take(remaining))
                    if (text.length < 65536) text.append(' ')
                } catch (_: Exception) { limited = true }
            }
        }
        visit(payload, 0)
        return GmailMessage(json.getString("id"), Instant.ofEpochMilli(json.getString("internalDate").toLong()),
            header(payload, "From"), header(payload, "Subject"), header(payload, "Reply-To"), text.toString(), limited || text.isBlank())
    }
}
