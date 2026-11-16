package cl.grupo28.hestia.data.auth

import cl.grupo28.hestia.BuildConfig
import java.net.URI

class AuthConfiguration(val url: String, val publishableKey: String, val webClientId: String) {
    val valid: Boolean get() = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.query == null && uri.fragment == null && uri.path.orEmpty() in listOf("", "/") &&
            publishableKey.startsWith("sb_publishable_") && publishableKey.length > 20 &&
            webClientId.endsWith(".apps.googleusercontent.com")
    }.getOrDefault(false)
    companion object {
        fun current() = AuthConfiguration(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY, BuildConfig.GOOGLE_WEB_CLIENT_ID)
    }
}
