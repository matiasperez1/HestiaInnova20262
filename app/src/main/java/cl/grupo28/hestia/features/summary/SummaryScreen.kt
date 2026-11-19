package cl.grupo28.hestia.features.summary

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import cl.grupo28.hestia.data.CallEvents
import cl.grupo28.hestia.data.CallDetection
import kotlinx.coroutines.flow.catch
import java.io.IOException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.BuildConfig
import cl.grupo28.hestia.core.components.*
import cl.grupo28.hestia.domain.*
import cl.grupo28.hestia.features.gmail.GmailUiState
import cl.grupo28.hestia.features.automatic.AutomaticUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SummaryScreen(week: WeekRange, summary: WeeklySummary?, gmail: GmailUiState, onOpenGmail: () -> Unit) =
    SummaryScreen(week, summary, gmail, null, false, onOpenGmail = onOpenGmail)

@Composable
fun SummaryScreen(week: WeekRange, summary: WeeklySummary?, gmail: GmailUiState, automatic: AutomaticUiState?, onOpenGmail: () -> Unit) =
    SummaryScreen(week, summary, gmail, automatic, false, onOpenGmail = onOpenGmail)

@Composable
internal fun SummaryScreen(
    week: WeekRange,
    summary: WeeklySummary?,
    gmail: GmailUiState,
    automatic: AutomaticUiState?,
    callsActive: Boolean,
    callsAvailable: Boolean = true,
    demoMode: Boolean = automatic?.demoSelected == true,
    onOpenPhone: (() -> Unit)? = null,
    onAskForHelp: (() -> Unit)? = null,
    onOpenEducation: (() -> Unit)? = null,
    onOpenAlert: ((String) -> Unit)? = null,
    onOpenGmail: () -> Unit,
) {
    val callOverview = rememberCallOverview(week)
    val mail = summarizeMail(week, gmail, automatic, demoMode, fallback = summary)
    val callSummary = callOverview.summary ?: summary
    val date = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-CL"))
    Page("Tu semana", "${week.start.format(date)} — ${week.end.format(date)}") {
        HestiaCard {
            SectionHeader("Estado de protección")
            SectionHeader("Llamadas", when {
                !callsAvailable -> "Identificación no disponible"
                callsActive -> "Identificación activa"
                else -> "Identificación desactivada"
            })
            SectionHeader(if (demoMode) "Correos · DEMO LOCAL" else "Correos", mail.status)
            PrimaryButton("Revisar correos", onOpenGmail)
            if (onOpenPhone != null) SecondaryButton("Consultar Teléfono", onOpenPhone)
        }
        HestiaCard {
            SectionHeader("Actividad de esta semana")
            SectionHeader("Llamadas registradas por Hestia")
            when {
                callOverview.loading -> Text("Leyendo las llamadas guardadas…")
                callOverview.unavailable -> Text("No pudimos leer las llamadas guardadas.")
                callSummary?.registeredCalls == null -> Text("Sin registros de llamadas reales.")
                else -> {
                    val suspicious = callSummary.suspiciousCalls
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Metric(callSummary.registeredCalls, "Registradas", Modifier.weight(1f))
                        Metric(suspicious, "Sospechosas", Modifier.weight(1f))
                    }
                    callOverview.lastWarning?.let { Text("Última detección sospechosa: ${date.withZone(ZoneId.systemDefault()).format(it)}.") }
                }
            }
            SectionHeader(if (demoMode) "Correos simulados" else "Correos revisados")
            if (mail.analyzed != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Metric(mail.analyzed, "Analizados", Modifier.weight(1f))
                    Metric(mail.suspicious, "Con señales", Modifier.weight(1f))
                }
            }
            Text(mail.activity, style = MaterialTheme.typography.bodyMedium)
            mail.recentAlert?.let { alert ->
                if (mail.canOpenAlert && onOpenAlert != null) {
                    SecondaryButton(if (demoMode) "Revisar último aviso simulado" else "Revisar último aviso de riesgo alto", { onOpenAlert(alert.id) })
                }
            }
            summary?.reviewedAlerts?.let { Text("Alertas revisadas por ti: $it.") }
        }
        if (onAskForHelp != null && onOpenEducation != null) HestiaCard {
            SectionHeader("Ayuda y prevención")
            SecondaryButton("Pedir ayuda", onAskForHelp)
            SecondaryButton("Aprender a prevenir estafas", onOpenEducation)
        }
    }
}

internal data class CallOverview(
    val summary: WeeklySummary? = null,
    val samples: List<CallDetection> = emptyList(),
    val lastWarning: Instant? = null,
    val loading: Boolean = false,
    val unavailable: Boolean = false,
)

@Composable
internal fun rememberCallOverview(week: WeekRange): CallOverview {
    val context = LocalContext.current
    val overview by produceState(CallOverview(loading = true), context, week) {
        CallEvents(context).events
            .catch { failure ->
                if (failure !is IOException) throw failure
                value = value.copy(loading = false, unavailable = true)
            }
            .collect { detections ->
                val calls = detections.filter { !it.synthetic && week.contains(it.event.occurredAt) }
                val samples = if (BuildConfig.DEBUG) detections.filter { it.synthetic && week.contains(it.event.occurredAt) } else emptyList()
                val summary = calls.takeIf { it.isNotEmpty() }?.let { WeeklySummary.fromEvents(it.map(CallDetection::event), week) }
                val lastWarning = calls.filter { it.event.suspicious }.maxOfOrNull { it.event.occurredAt }
                value = CallOverview(summary, samples, lastWarning)
            }
    }
    return overview
}

@Composable
internal fun WeeklyCallCards(overview: CallOverview, callsActive: Boolean, realSummary: WeeklySummary? = null) {
    val callSummary = overview.summary
    val samples = overview.samples
    HestiaCard {
        MetricHeading("Llamadas", HestiaSymbol.PHONE)
        val registered = callSummary?.registeredCalls ?: realSummary?.registeredCalls
        if (overview.loading) {
            Text("Leyendo las llamadas guardadas…")
        } else if (overview.unavailable) {
            Text("No pudimos leer las llamadas guardadas.")
        } else if (registered == null && samples.isNotEmpty()) {
            Text("Sin registros de llamadas reales esta semana.", style = MaterialTheme.typography.bodyLarge)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric(registered, "Registradas por Hestia", Modifier.weight(1f))
                Metric(callSummary?.suspiciousCalls ?: realSummary?.suspiciousCalls, "Sospechosas", Modifier.weight(1f))
            }
        }
        Text(
            if (callsActive) "Identificación activada. Solo se cuentan las llamadas que Android entrega a Hestia."
            else "Identificación desactivada. Aquí puedes ver las detecciones guardadas de esta semana.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (samples.isNotEmpty()) HestiaCard {
        MetricHeading("Pruebas de llamadas", HestiaSymbol.PHONE)
        val reported = samples.count { it.event.suspicious }
        Text("Llamadas simuladas esta semana: ${samples.size}", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Metric(reported, "Sospechosas en prueba", Modifier.weight(1f))
            Metric(samples.size - reported, "Sin reportes en prueba", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricHeading(text: String, symbol: HestiaSymbol) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        HestiaIcon(symbol)
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun Metric(value: Int?, label: String, modifier: Modifier) {
    Column(modifier.clearAndSetSemantics { contentDescription = "$label: ${value?.toString() ?: "Sin datos"}" }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value?.toString() ?: "Sin datos", style = if (value == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
