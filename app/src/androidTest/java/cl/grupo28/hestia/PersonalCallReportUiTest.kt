package cl.grupo28.hestia

import android.telephony.PhoneNumberUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cl.grupo28.hestia.core.theme.HestiaTheme
import cl.grupo28.hestia.data.PersonalReportStore
import cl.grupo28.hestia.data.PersonalReports
import cl.grupo28.hestia.data.calls.RecentCall
import cl.grupo28.hestia.data.calls.RecentKind
import cl.grupo28.hestia.data.security.EncryptedDeviceFile
import cl.grupo28.hestia.domain.PersonalReportReason
import cl.grupo28.hestia.domain.normalizePersonalNumber
import cl.grupo28.hestia.features.calls.HestiaIncomingCall
import cl.grupo28.hestia.features.calls.PersonalReportsPanel
import cl.grupo28.hestia.features.calls.RecentCallOptions
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PersonalCallReportUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun realRecordCanBeMarkedAndWithdrawnWithAnEncryptedStoreAndNoNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "hestia-personal-qa-${UUID.randomUUID()}"
        val file = EncryptedDeviceFile(context, fileName, "$fileName.v1")
        fun repository() = PersonalReports(PersonalReportStore(file::read, file::write), ::normalize)
        val reports = repository()
        runBlocking { reports.load() }
        val number = "+56 9 8765 4321"
        val open = mutableStateOf(true)
        val feedback = mutableStateOf("")
        try {
            compose.setContent { HestiaTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(feedback.value)
                    PersonalReportsPanel(repository = reports)
                }
                if (open.value) RecentCallOptions(
                    RecentCall("qa-personal", "+56987654321", Instant.EPOCH, RecentKind.RECEIVED, false, originalNumber = number),
                    "Contacto de prueba", {}, { open.value = false }, {}, personalRepository = reports,
                    onPersonalResult = { feedback.value = it },
                )
            } }
            tap("Reportar número")
            compose.onNodeWithText(number).assertExists()
            tap("Cancelar")
            assertNull(runBlocking { reports.active(number) })
            tap("Reportar número")
            tap("Suplantación de identidad")
            compose.onNodeWithText("Descripción opcional").performScrollTo().performTextInput("Apunte local de QA")
            tap("Revisar reporte")
            compose.onNodeWithText("¿Guardar este reporte personal?").assertExists()
            assertNull(runBlocking { reports.active(number) })
            tap("Guardar reporte")
            awaitText("Reporte personal guardado.")
            val reopened = runBlocking { repository().active("987654321") }
            assertEquals(PersonalReportReason.IMPERSONATION, reopened?.reason)
            assertEquals("Apunte local de QA", reopened?.description)
            val ciphertext = context.noBackupFilesDir.resolve(fileName).readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(ciphertext.contains("Apunte local de QA"))
            assertFalse(ciphertext.contains("+56987654321"))
            tap("Ver reportes personales")
            compose.onNodeWithText("Activo").assertExists()
            compose.runOnIdle { open.value = true }
            compose.onNodeWithText("Reportar número").assertDoesNotExist()
            tap("Consultar reputación")
            compose.onNodeWithText("Número marcado por ti").assertExists()
            compose.onNodeWithText("La reputación comunitaria aún no está disponible para este número.").assertExists()
            tap("Retirar mi reporte")
            tap("Retirar reporte")
            awaitText("Reporte personal retirado.")
            compose.onNodeWithText("Retirado").assertExists()
            assertNull(runBlocking { repository().active(number) })
            compose.runOnIdle { open.value = true }
            compose.onNodeWithText("Reportar número").assertExists()
        } finally {
            file.delete()
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("$fileName.v1") }
        }
    }

    @Test fun platformNormalizationAndIncomingUiDistinguishThePersonalWarning() {
        assertEquals("+56987654321", normalize("9 8765-4321"))
        assertEquals("+56987654321", normalize("(+56) 9 8765-4321"))
        assertEquals("+442079460958", normalize("00 44 (20) 7946-0958"))
        assertNull(normalize("Número privado"))
        assertNull(normalize("112"))
        val unlocked = mutableStateOf(true)
        compose.setContent { HestiaTheme {
            HestiaIncomingCall("+56987654321", false, null, {}, {}, showPrivateDetails = unlocked.value,
                personalReason = PersonalReportReason.IMPERSONATION)
        } }
        compose.onNodeWithText("Número marcado por ti").performScrollTo().assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("Motivo: Suplantación de identidad").assertExists()
        compose.onNodeWithText("SOSPECHOSO").assertDoesNotExist()
        compose.runOnIdle { unlocked.value = false }
        compose.onNodeWithText("Número marcado por ti").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Motivo: Suplantación de identidad").assertDoesNotExist()
        compose.onNodeWithText("+56987654321").assertDoesNotExist()
        compose.onNodeWithText("Contestar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rechazar").performScrollTo().assertIsDisplayed()
    }

    private fun normalize(number: String) = normalizePersonalNumber(number, "CL", PhoneNumberUtils::formatNumberToE164)
    private fun tap(label: String) {
        val node = compose.onNodeWithText(label)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }

    private fun awaitText(label: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
    }
}
