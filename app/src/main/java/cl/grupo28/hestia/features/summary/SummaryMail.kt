package cl.grupo28.hestia.features.summary

import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.domain.WeeklySummary
import cl.grupo28.hestia.domain.automatic.GmailAlert
import cl.grupo28.hestia.features.automatic.AutomaticUiState
import cl.grupo28.hestia.features.automatic.ConsultationIssue
import cl.grupo28.hestia.features.gmail.GmailUiState
import java.time.Instant

internal data class SummaryMail(
    val status: String,
    val activity: String,
    val analyzed: Int?,
    val suspicious: Int?,
    val recentAlert: GmailAlert?,
    val canOpenAlert: Boolean,
)

internal fun summarizeMail(
    week: WeekRange,
    gmail: GmailUiState,
    automatic: AutomaticUiState?,
    demoMode: Boolean,
    fallback: WeeklySummary? = null,
    now: Instant = Instant.now(),
): SummaryMail {
    val automaticSource = automatic?.takeIf { it.demoSelected == demoMode }
    val automaticActive = automaticSource?.let { state ->
        state.checked && state.eligible && state.status?.active == true &&
            parseInstant(state.status.expiration)?.isAfter(now) == true
    } == true
    val automaticIssue = automaticSource?.consultationIssue
    val storedManual = gmail.snapshot?.takeUnless { demoMode }
    val manual = storedManual?.takeIf { it.week == week }
    val storedAutomatic = automaticSource?.summary
    val remote = storedAutomatic?.takeIf {
        parseInstant(it.weekStart)?.atZone(week.zone)?.toLocalDate() == week.start &&
            (demoMode || automaticActive || manual == null)
    }
    val recent = automaticSource?.alerts.orEmpty().asSequence()
        .filter { it.risk == "HIGH" }
        .mapNotNull { alert -> parseInstant(alert.receivedAt)?.let { alert to it } }
        .filter { (_, date) -> week.contains(date) && !date.isAfter(now) }
        .maxByOrNull { it.second }?.first
    val status = if (demoMode) when {
        automaticIssue == ConsultationIssue.OFFLINE -> "Sin conexión con el computador"
        automaticIssue == ConsultationIssue.ERROR -> "No pudimos consultar la demostración"
        automaticIssue == ConsultationIssue.PERMISSION_REQUIRED -> "Conexión local pendiente de permiso"
        automaticSource?.checked != true -> "Consultando la demostración…"
        automaticSource.demoInfo?.workerRunning == false -> "Conectada · procesamiento sin confirmar"
        else -> "Conectada al computador"
    } else when {
        automaticActive -> "Revisión automática activa"
        gmail.busy -> "Consultando Gmail…"
        gmail.connectionLost -> "Sin conexión con Gmail"
        gmail.verified -> "Revisión manual disponible"
        automaticIssue == ConsultationIssue.OFFLINE -> "Sin conexión con la revisión automática"
        automaticIssue == ConsultationIssue.ERROR -> "No pudimos consultar la revisión automática"
        automaticSource?.busy == true -> "Consultando la revisión automática…"
        else -> "Revisión manual sin verificar"
    }
    val activity = when {
        remote != null && demoMode && automaticSource?.checked != true -> "Resultados de la última consulta de la demostración."
        remote != null && demoMode && remote.analyzed == 0 -> "Sin actividad de demostración esta semana."
        remote != null && demoMode -> "Resultados de correos de prueba."
        remote != null && !automaticActive -> "Resultados guardados. Seguimiento automático sin confirmar."
        remote != null && remote.analyzed == 0 -> "Sin correos analizados automáticamente esta semana."
        remote != null -> "Resultados de la revisión automática."
        manual?.partial == true -> "Última revisión manual. Resultado parcial."
        manual?.records?.isEmpty() == true -> "Sin correos en el período revisado."
        manual != null -> "Resultados de la última revisión manual de esta semana."
        storedManual != null || storedAutomatic != null -> "Hay resultados anteriores en Revisar correos."
        else -> "Sin datos de correos para esta semana."
    }
    val realFallback = fallback?.takeUnless { demoMode }
    return SummaryMail(
        status, activity,
        remote?.analyzed ?: manual?.records?.size ?: realFallback?.analyzedEmails,
        remote?.suspicious ?: manual?.suspicious ?: realFallback?.suspiciousEmails,
        recent,
        automaticSource?.checked == true && automaticSource.eligible && !automaticSource.busy,
    )
}

private fun parseInstant(value: String?): Instant? = value?.let { runCatching { Instant.parse(it) }.getOrNull() }
