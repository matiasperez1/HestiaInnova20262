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
import cl.grupo28.hestia.core.components.*
import cl.grupo28.hestia.domain.*
import cl.grupo28.hestia.features.gmail.GmailUiState
import cl.grupo28.hestia.features.automatic.AutomaticUiState
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SummaryScreen(week: WeekRange, summary: WeeklySummary?, gmail: GmailUiState, onOpenGmail: () -> Unit) = SummaryScreen(week,summary,gmail,null,false,onOpenGmail)

@Composable
fun SummaryScreen(week: WeekRange, summary: WeeklySummary?, gmail: GmailUiState, automatic: AutomaticUiState?, onOpenGmail: () -> Unit) =
    SummaryScreen(week, summary, gmail, automatic, false, onOpenGmail)

@Composable
fun SummaryScreen(week: WeekRange, summary: WeeklySummary?, gmail: GmailUiState, automatic: AutomaticUiState?, callsActive: Boolean, onOpenGmail: () -> Unit) {
    val context = LocalContext.current
    val callEvents = remember(context) { CallEvents(context).events.catch { if (it !is IOException) throw it } }
    val detections by callEvents.collectAsState(initial = emptyList<CallDetection>())
    val calls = detections.filter { !it.synthetic && week.contains(it.event.occurredAt) }
    val samples = detections.filter { it.synthetic && week.contains(it.event.occurredAt) }
    val callSummary = calls.takeIf { it.isNotEmpty() }?.let { WeeklySummary.fromEvents(it.map(CallDetection::event), week) }
    val demo=automatic?.demoSelected==true
    val realSummary=summary?.takeUnless{demo}
    val remote = automatic?.summary?.takeIf { runCatching { java.time.Instant.parse(it.weekStart).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == week.start }.getOrDefault(false) }
    val automaticActive = automatic?.status?.let { automatic.eligible && automatic.checked && it.active && runCatching { java.time.Instant.parse(it.expiration).isAfter(java.time.Instant.now()) }.getOrDefault(false) } == true
    val emailSnapshot = gmail.snapshot?.takeIf { !demo && it.week == week }
    val date = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-CL"))
    Page("Tu semana", "${week.start.format(date)} — ${week.end.format(date)}") {
        HestiaCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HestiaIcon(HestiaSymbol.CLOCK)
                Text(if(demo) "DEMO LOCAL · correos simulados" else if (automaticActive) "Revisión automática de correos" else if (gmail.verified) "Revisión manual de correos" else if (callsActive) "Identificación de llamadas activada" else "Protección pendiente", style = MaterialTheme.typography.titleMedium)
            }
            Text(if(demo) "Solo datos sintéticos. No representa protección real de Gmail." else if (automaticActive) "Revisión automática disponible." else if (gmail.verified) "Revisión manual disponible." else "Gmail sin verificar.", style = MaterialTheme.typography.bodyMedium)
        }
        HestiaCard {
            MetricHeading("Llamadas", HestiaSymbol.PHONE)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric(callSummary?.registeredCalls ?: realSummary?.registeredCalls, "Registradas por Hestia", Modifier.weight(1f))
                Metric(callSummary?.suspiciousCalls ?: realSummary?.suspiciousCalls, "Sospechosas", Modifier.weight(1f))
            }
            Text(
                if (callsActive) "Identificación activada. Solo se cuentan las llamadas que Android entrega a Hestia."
                else "Identificación desactivada. Aquí puedes ver las detecciones guardadas de esta semana.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (samples.isNotEmpty()) Text(
                "Prueba de llamadas: ${samples.size} registradas · ${samples.count { it.event.suspicious }} reportadas",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        HestiaCard {
            MetricHeading("Correos", HestiaSymbol.MAIL)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric(remote?.analyzed ?: emailSnapshot?.records?.size ?: realSummary?.analyzedEmails, "Analizados", Modifier.weight(1f))
                Metric(remote?.suspicious ?: emailSnapshot?.suspicious ?: realSummary?.suspiciousEmails, "Sospechosos", Modifier.weight(1f))
            }
            if (remote != null) Text(if(demo) "Resultados de simulaciones locales; sin correos reales." else if(automaticActive) "Resultados de la revisión automática. No se suman a la revisión manual." else "Resultados guardados de la revisión automática; seguimiento sin confirmar.", style = MaterialTheme.typography.bodyMedium)
            else if (emailSnapshot != null) Text("Resultado de la última revisión manual de esta semana.", style = MaterialTheme.typography.bodyMedium)
            if (emailSnapshot?.partial == true) Text("Resultado parcial.", style = MaterialTheme.typography.bodyMedium)
            SecondaryButton("Revisar correos", onOpenGmail)
        }
        HestiaCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(realSummary?.reviewedAlerts?.toString() ?: "Sin datos", style = if (realSummary == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall)
                Text("Alertas revisadas por ti", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
        }
        if (callSummary == null && samples.isEmpty() && realSummary == null && emailSnapshot == null && remote == null) Text("Aún no hay datos para esta semana.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
