package cl.grupo28.hestia

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cl.grupo28.hestia.data.gmail.EncryptedGmailStore
import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.domain.gmail.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class GmailStoreTest {
    @Test fun metadataIsEncryptedRestoredAndIsolatedByOwner() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="hestia-gmail-isolated-test"; val store=EncryptedGmailStore(context,name)
        val file=File(context.noBackupFilesDir,name)
        try {
            store.clear()
            val snapshot=GmailSnapshot("owner-one",WeekRange.current(),Instant.now(),listOf(EmailRecord("hash-test",Instant.now(),"test@example.invalid","Private subject test",EmailAnalysis(35,EmailRisk.MEDIUM,listOf("Solicitud de claves")))),false)
            store.save(snapshot)
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("Private subject test"))
            assertEquals("Private subject test",EncryptedGmailStore(context,name).load("owner-one")?.records?.single()?.subject)
            assertNull(store.load("owner-two")); assertFalse(file.exists())
        } finally { store.clear() }
    }
    @Test fun alteredCiphertextIsDiscardedRatherThanDisplayed() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="hestia-gmail-tamper-test"; val store=EncryptedGmailStore(context,name)
        val file=File(context.noBackupFilesDir,name)
        try {
            store.save(GmailSnapshot("owner",WeekRange.current(),Instant.now(),emptyList(),false))
            val bytes=file.readBytes(); bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
            try { store.load("owner"); fail("Tampered results must not be trusted") } catch (_: java.io.IOException) { }
            assertFalse(file.exists())
        } finally { store.clear() }
    }

}
