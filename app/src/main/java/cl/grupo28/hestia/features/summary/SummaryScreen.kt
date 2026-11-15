package cl.grupo28.hestia.features.summary

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.components.*
import cl.grupo28.hestia.domain.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SummaryScreen(week: WeekRange, summary: WeeklySummary?) {
    val date = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-CL"))
    Page("Tu semana", "${week.start.format(date)} — ${week.end.format(date)}") {
        HestiaCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HestiaIcon(HestiaSymbol.CLOCK)
                Text("Protección pendiente", style = MaterialTheme.typography.titleMedium)
            }
            Text("Correos y llamadas aún sin conectar", style = MaterialTheme.typography.bodyMedium)
        }
        HestiaCard {
            MetricHeading("Llamadas", HestiaSymbol.PHONE)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric(summary?.registeredCalls, "Registradas por Hestia", Modifier.weight(1f))
                Metric(summary?.suspiciousCalls, "Sospechosas", Modifier.weight(1f))
            }
        }
        HestiaCard {
            MetricHeading("Correos", HestiaSymbol.MAIL)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Metric(summary?.analyzedEmails, "Analizados", Modifier.weight(1f))
                Metric(summary?.suspiciousEmails, "Sospechosos", Modifier.weight(1f))
            }
        }
        HestiaCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(summary?.reviewedAlerts?.toString() ?: "Sin datos", style = if (summary == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall)
                Text("Alertas revisadas por ti", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
        }
        if (summary == null) Text("Aún no hay datos para esta semana.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
