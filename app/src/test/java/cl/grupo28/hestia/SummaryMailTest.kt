package cl.grupo28.hestia

import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.domain.automatic.AutomaticStatus
import cl.grupo28.hestia.domain.automatic.AutomaticSummary
import cl.grupo28.hestia.domain.automatic.GmailAlert
import cl.grupo28.hestia.domain.gmail.GmailSnapshot
import cl.grupo28.hestia.features.automatic.AutomaticUiState
import cl.grupo28.hestia.features.automatic.ConsultationIssue
import cl.grupo28.hestia.features.gmail.GmailUiState
import cl.grupo28.hestia.features.summary.summarizeMail
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SummaryMailTest {
    private val week = WeekRange(LocalDate.parse("2026-11-16"), ZoneOffset.UTC)
    private val now = Instant.parse("2026-11-19T10:00:00Z")
    private val manual = GmailUiState(
        verified = true, snapshot = GmailSnapshot("owner", week, now, emptyList(), false),
    )
    private val remote = AutomaticUiState(
        eligible = true, checked = true,
        status = AutomaticStatus("ACTIVE", true, null, "2026-11-20T10:00:00Z", "2026-11-16T00:00:00Z"),
        summary = AutomaticSummary(7, 2, "2026-11-16T00:00:00Z", "2026-11-23T00:00:00Z"),
    )

    @Test fun verifiedAutomaticAndManualAreNeverAddedTogether() {
        assertEquals(7, summarizeMail(week, manual, remote, false, now = now).analyzed)
        val expired = remote.copy(status = remote.status?.copy(expiration = "2026-11-18T00:00:00Z"))
        val result = summarizeMail(week, manual, expired, false, now = now)
        assertEquals(0, result.analyzed)
        assertEquals("Sin correos en el período revisado.", result.activity)
    }

    @Test fun demoModeNeverUsesRealAccountsAndShowsOnlyTheLastConfirmedResultOnFailure() {
        val switching = summarizeMail(week, manual, remote, true, now = now)
        assertNull(switching.analyzed)
        assertNull(switching.recentAlert)
        val demo = remote.copy(demoSelected = true, checked = false, consultationIssue = ConsultationIssue.OFFLINE)
        val offline = summarizeMail(week, manual, demo, true, now = now)
        val error = summarizeMail(week, manual, demo.copy(consultationIssue = ConsultationIssue.ERROR), true, now = now)
        assertEquals(7, offline.analyzed)
        assertEquals("Sin conexión con el computador", offline.status)
        assertEquals("No pudimos consultar la demostración", error.status)
        assertTrue(offline.activity.contains("última consulta"))
        assertFalse(offline.canOpenAlert)
        assertEquals(0, summarizeMail(week, manual, demo, false, now = now).analyzed)
    }

    @Test fun pastCountsAndAlertsAreNotPresentedAsCurrent() {
        fun alert(id: String, date: String, risk: String = "HIGH") =
            GmailAlert(id, date, risk, "", "", emptyList(), false)
        val automatic = remote.copy(
            summary = remote.summary?.copy(weekStart = "2026-11-09T00:00:00Z"),
            alerts = listOf(
                alert("past", "2026-11-15T23:59:00Z"),
                alert("medium", "2026-11-19T08:00:00Z", "MEDIUM"),
                alert("latest", "2026-11-18T18:00:00Z"),
                alert("older", "2026-11-17T18:00:00Z"),
                alert("future", "2026-11-21T18:00:00Z"),
            ),
        )
        val result = summarizeMail(week, GmailUiState(), automatic, false, now = now)
        assertNull(result.analyzed)
        assertEquals("latest", result.recentAlert?.id)
        assertTrue(result.canOpenAlert)
    }
}
