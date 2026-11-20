package cl.grupo28.hestia

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cl.grupo28.hestia.core.theme.HestiaTheme
import cl.grupo28.hestia.data.calls.*
import cl.grupo28.hestia.features.calls.RecentCallOptions
import cl.grupo28.hestia.features.calls.reputationLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.time.Instant
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class RecentCallReportTest {
    @get:Rule val compose = createComposeRule()

    @Test fun everyCallKindCanOpenTheReportFormEvenForAKnownContact() {
        val reports = TestReports()
        val current = mutableStateOf<RecentCall?>(null)
        compose.setContent { HestiaTheme {
            current.value?.let { call -> key(call.id) {
                RecentCallOptions(call, "Contacto de prueba", {}, { current.value = null }, {}, reports)
            } }
        } }
        RecentKind.entries.forEach { kind ->
            compose.runOnIdle { current.value = recent(kind, formatted) }
            compose.onNodeWithText(kind.label).assertExists()
            tap("Reportar número")
            compose.onNodeWithText(formatted).assertExists()
            compose.onNodeWithText("Revisar reporte").assertIsNotEnabled()
            tap("Cancelar")
            tap("Cerrar")
        }
        assertEquals(0, reports.writes)
    }

    @Test fun privateOrInvalidNumbersHaveNoReportAndRealNumberNeverLeavesItsOwnFlow() {
        val reports = TestReports()
        val current = mutableStateOf(recent(RecentKind.MISSED, "", synthetic = false))
        compose.setContent { HestiaTheme {
            key(current.value.id) { RecentCallOptions(current.value, null, {}, {}, {}, reports) }
        } }
        listOf("", "Número privado", "12#").forEach { value ->
            compose.runOnIdle { current.value = recent(RecentKind.MISSED, value, synthetic = false) }
            compose.onNodeWithText("Reportar número").assertDoesNotExist()
            compose.onNodeWithText("Consultar reputación").assertDoesNotExist()
        }
        val real = "+56987654321"
        compose.runOnIdle { current.value = recent(RecentKind.MISSED, real, synthetic = false) }
        awaitEnabled("Reportar número")
        tap("Reportar número")
        compose.onNodeWithText(real).assertExists()
        compose.onNodeWithText("Descripción opcional").assertExists()
        tap("Cancelar")
        compose.onNodeWithText("Confirmar envío").assertDoesNotExist()
        assertEquals(0, reports.writes)
        assertEquals(0, reports.syncs)
    }

    @Test fun aConsultationDoesNotVoteAndConfirmedReportCorroboratesOnlyOnce() {
        val reports = TestReports()
        reports.seed(CallPersona.BRUNO, CallReportCategory.IMPERSONATION)
        val open = mutableStateOf(true)
        val call = recent(RecentKind.RECEIVED, formatted)
        compose.setContent { HestiaTheme { if (open.value) {
            RecentCallOptions(call, null, {}, { open.value = false }, {}, reports)
        } } }
        awaitEnabled("Consultar reputación")
        tap("Consultar reputación")
        awaitText("Pendiente de corroboración · 1 reporte de fraude")
        assertEquals(0, reports.writes)
        tap("Reportar número")
        tap("Posible estafa")
        tap("Revisar reporte")
        compose.onNodeWithText("¿Enviar este reporte?").assertExists()
        assertEquals(0, reports.writes)
        tap("Confirmar envío")
        awaitText("Reporte enviado")
        tap("Entendido")
        compose.onNodeWithText("Reportar número").assertDoesNotExist()
        assertEquals(1, reports.writes)
        assertEquals(2, reports.reputation.value?.counts?.get(controlled))
        compose.runOnIdle { open.value = true }
        compose.onNodeWithText("Reportar número").assertDoesNotExist()
        awaitEnabled("Consultar reputación")
        tap("Consultar reputación")
        awaitText("Sospechoso · 2 reportes de fraude independientes")
        tap("Retirar mi reporte")
        tap("Retirar reporte")
        awaitText("Reporte retirado.")
        awaitText("Pendiente de corroboración · 1 reporte de fraude")
        compose.onNodeWithText("Reportar número").assertExists()
        assertEquals(1, reports.reputation.value?.counts?.get(controlled))
    }

    @Test fun offlineSubmitStaysUnconfirmedAndRetryUsesTheSelectedCategory() {
        val reports = TestReports(online = false)
        compose.setContent { HestiaTheme {
            RecentCallOptions(recent(RecentKind.OUTGOING, formatted), null, {}, {}, {}, reports)
        } }
        tap("Reportar número")
        tap("Publicidad no deseada")
        tap("Revisar reporte")
        tap("Confirmar envío")
        awaitText("No pudimos confirmar el envío. Revisa la conexión y vuelve a intentar.")
        compose.onNodeWithText("Reporte enviado").assertDoesNotExist()
        assertEquals(0, reports.writes)
        compose.runOnIdle { reports.online = true }
        tap("Revisar reporte")
        compose.onNodeWithText("Publicidad no deseada").assertExists()
        tap("Confirmar envío")
        awaitText("Reporte enviado")
        assertEquals(CallReportCategory.UNWANTED_ADS, reports.ownership.value[CallPersona.ANA]?.get(controlled))
        assertEquals(0, reports.reputation.value?.fraudCounts?.get(controlled))
    }

    @Test fun isolatedLocalBackendConfirmsReportRefreshAndWithdrawal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("localReportsSmoke") == "true")
        val reports = DemoCallReports.from(instrumentation.targetContext)
        val author = reports.persona.value
        val other = CallPersona.entries.first { it != author }
        val number = "2025550196"
        val call = recent(RecentKind.DECLINED, "+1 (202) 555-0196")
        val open = mutableStateOf(true)
        assertFalse(runBlocking { reports.sync(author).snapshot.counts.containsKey(number) })
        try {
            compose.setContent { HestiaTheme { if (open.value) {
                RecentCallOptions(call, "Contacto de prueba", {}, { open.value = false }, {}, reports)
            } } }
            tap("Reportar número")
            tap("Posible estafa")
            tap("Revisar reporte")
            tap("Confirmar envío")
            awaitText("Reporte enviado")
            tap("Entendido")
            assertEquals("Pendiente de corroboración · 1 reporte de fraude", reputationLabel(reports.reputation.value!!, number))
            runBlocking { reports.save(author, number, CallReportCategory.POSSIBLE_FRAUD); reports.sync(author) }
            assertEquals(1, reports.reputation.value?.counts?.get(number))
            runBlocking { reports.save(other, number, CallReportCategory.IMPERSONATION); reports.sync(author) }
            assertEquals(2, reports.reputation.value?.fraudCounts?.get(number))
            compose.runOnIdle { open.value = true }
            compose.onNodeWithText("Reportar número").assertDoesNotExist()
            tap("Retirar mi reporte")
            tap("Retirar reporte")
            awaitText("Reporte retirado.")
            compose.waitUntil(15_000) { reports.reputation.value?.counts?.get(number) == 1 }
            compose.onNodeWithText("Reportar número").assertExists()
        } finally {
            runBlocking {
                reports.withdraw(author, number)
                reports.withdraw(other, number)
                reports.sync(author)
            }
        }
    }

    private fun tap(label: String) {
        val node = compose.onNodeWithText(label)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }

    private fun awaitText(label: String) = compose.waitUntil(15_000) {
        compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitEnabled(label: String) = compose.waitUntil(15_000) {
        compose.onAllNodes(hasText(label) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }

    private fun recent(kind: RecentKind, number: String, synthetic: Boolean = true) =
        RecentCall("${kind.name}-$number", number, Instant.EPOCH, kind, synthetic)

    private class TestReports(var online: Boolean = true) : DemoCallReportRepository {
        override val persona = MutableStateFlow(CallPersona.ANA)
        override val ownership = MutableStateFlow<Map<CallPersona, Map<String, CallReportCategory>>>(emptyMap())
        override val reputation = MutableStateFlow<DemoReputationSnapshot?>(null)
        private val entries = mutableMapOf<CallPersona, CallReportCategory>()
        var writes = 0
        var syncs = 0

        fun seed(author: CallPersona, category: CallReportCategory) { entries[author] = category }
        override suspend fun cached() = reputation.value
        override suspend fun sync(persona: CallPersona): DemoReports {
            syncs++
            requireConnection()
            val own = entries[persona]?.let { mapOf(controlled to it) }.orEmpty()
            ownership.value = ownership.value + (persona to own)
            val snapshot = DemoReputationSnapshot(Instant.now(), 2, mapOf(controlled to entries.size),
                mapOf(controlled to entries.values.count { it in listOf(CallReportCategory.POSSIBLE_FRAUD, CallReportCategory.IMPERSONATION) }))
            reputation.value = snapshot
            return DemoReports(snapshot, own)
        }
        override suspend fun save(persona: CallPersona, number: String, category: CallReportCategory, replace: Boolean) {
            requireConnection()
            assertEquals(controlled, number)
            writes++
            entries[persona] = category
        }
        override suspend fun withdraw(persona: CallPersona, number: String) {
            requireConnection()
            assertEquals(controlled, number)
            entries.remove(persona)
        }
        private fun requireConnection() { if (!online) throw IOException("Test offline") }
    }

    private companion object {
        const val controlled = "2025550197"
        const val formatted = "+1 (202) 555-0197"
    }
}
