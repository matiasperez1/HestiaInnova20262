package cl.grupo28.hestia.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The IV precedes the AES-GCM ciphertext. Keep this format compatible with existing sessions. */
internal class EncryptedDeviceFile(context: Context, name: String, private val alias: String) {
    init { require(name.matches(Regex("[a-zA-Z0-9-]+"))) }
    private val file = AtomicFile(File(context.noBackupFilesDir, name))

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun read(): ByteArray? {
        val bytes = try { file.openRead().use { it.readBytes() } }
        catch (error: FileNotFoundException) {
            if (file.baseFile.exists()) throw error
            return null
        }
        require(bytes.size > IV_BYTES + 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, IV_BYTES)))
        return cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
    }

    fun write(data: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        check(cipher.iv.size == IV_BYTES)
        val encrypted = cipher.iv + cipher.doFinal(data)
        val stream = file.startWrite()
        try { stream.write(encrypted); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }

    fun delete() {
        file.delete()
        check(!file.baseFile.exists()) { "Encrypted storage could not be cleared" }
    }

    private companion object { const val IV_BYTES = 12 }
}
