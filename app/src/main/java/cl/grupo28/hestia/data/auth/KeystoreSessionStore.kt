package cl.grupo28.hestia.data.auth

import android.content.Context
import cl.grupo28.hestia.data.security.EncryptedDeviceFile
import cl.grupo28.hestia.domain.auth.*
import org.json.JSONObject

class KeystoreSessionStore(context: Context, private val storageName: String = "hestia-auth-session") : SessionStore {
    private val file = EncryptedDeviceFile(context, storageName, "hestia.auth.$storageName.v1")
    override fun load(): AuthSession? {
        try {
            val bytes = file.read() ?: return null
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            return AuthSession(json.getString("access"), json.getString("refresh"), json.getLong("expires"),
                Account(json.getString("id"), json.getString("email")))
        } catch (_: Exception) {
            clear()
            throw InvalidSessionException()
        }
    }
    override fun save(session: AuthSession) {
        val json = JSONObject().put("access", session.accessToken).put("refresh", session.refreshToken)
            .put("expires", session.expiresAt).put("id", session.account.id).put("email", session.account.email)
        file.write(json.toString().toByteArray(Charsets.UTF_8))
    }
    override fun clear() = file.delete()
}
