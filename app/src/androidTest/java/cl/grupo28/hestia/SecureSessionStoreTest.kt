package cl.grupo28.hestia

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cl.grupo28.hestia.data.auth.KeystoreSessionStore
import cl.grupo28.hestia.domain.auth.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SecureSessionStoreTest {
    @Test fun encryptedSessionSurvivesReopenRejectsTamperingAndClears() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "hestia-auth-instrumented-test"
        val file = File(context.noBackupFilesDir, name)
        val store = KeystoreSessionStore(context, name)
        try {
            store.clear()
            store.save(AuthSession("test-access-never-plaintext", "test-refresh-never-plaintext", 2000, Account("test-id", "test@example.invalid")))
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("test-access-never-plaintext"))
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("test-refresh-never-plaintext"))
            assertEquals("test-refresh-never-plaintext", KeystoreSessionStore(context, name).load()?.refreshToken)
            val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
            try { store.load(); fail("Tampered ciphertext must be rejected") } catch (_: InvalidSessionException) { }
            assertFalse(file.exists())
            assertNull(store.load())
        } finally { store.clear() }
    }
}
