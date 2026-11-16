package cl.grupo28.hestia.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import cl.grupo28.hestia.domain.auth.*
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreSessionStore(context: Context, private val storageName: String = "hestia-auth-session") : SessionStore {
    init { require(storageName.matches(Regex("[a-zA-Z0-9-]+"))) }
    private val file = AtomicFile(File(context.noBackupFilesDir, storageName))
    private val alias = "hestia.auth.$storageName.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun load(): AuthSession? {
        if (!file.baseFile.exists()) return null
        try {
            val bytes = file.openRead().use { it.readBytes() }
            require(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
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
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(encrypted); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
    override fun clear() {
        file.delete()
        check(!file.baseFile.exists()) { "Session storage could not be cleared" }
    }
}
