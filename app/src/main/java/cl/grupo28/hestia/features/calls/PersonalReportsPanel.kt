package cl.grupo28.hestia.features.calls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.components.HestiaCard
import cl.grupo28.hestia.core.components.HestiaIcon
import cl.grupo28.hestia.core.components.HestiaSymbol
import cl.grupo28.hestia.core.components.SecondaryButton
import cl.grupo28.hestia.core.components.SectionHeader
import cl.grupo28.hestia.data.PersonalReportChanged
import cl.grupo28.hestia.data.PersonalReportState
import cl.grupo28.hestia.data.PersonalReports
import cl.grupo28.hestia.domain.PERSONAL_REPORT_DESCRIPTION_LIMIT
import cl.grupo28.hestia.domain.PersonalCallReport
import cl.grupo28.hestia.domain.PersonalReportReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal const val PERSONAL_MARK_TITLE = "Número marcado por ti"
internal const val PERSONAL_MARK_TEXT = "Este número fue reportado anteriormente desde tu dispositivo."

@Composable
internal fun PersonalCallWarning(reason: PersonalReportReason, showReason: Boolean = true) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
        Column {
            PersonalMarkBanner(prominent = true, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(PERSONAL_MARK_TEXT, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                if (showReason) Text("Motivo: ${reason.label}", color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
internal fun PersonalMarkBanner(title: String = PERSONAL_MARK_TITLE, modifier: Modifier = Modifier, prominent: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = colors.error, contentColor = colors.onError) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            HestiaIcon(HestiaSymbol.ALERT, tint = colors.onError)
            val titleSemantics = if (prominent) Modifier.semantics { heading() } else Modifier
            Text(title, titleSemantics.weight(1f), fontWeight = FontWeight.Bold,
                style = if (prominent) typography.headlineMedium else typography.titleLarge)
        }
    }
}

@Composable
internal fun rememberPersonalReports(): PersonalReports {
    val context = LocalContext.current
    val reports = remember(context) { PersonalReports.from(context) }
    LaunchedEffect(reports) { reports.load() }
    return reports
}

@Composable
internal fun PersonalReportsPanel(allowEntry: Boolean = false, repository: PersonalReports? = null) {
    val reports = repository ?: rememberPersonalReports()
    val state by reports.state.collectAsState()
    val scope = rememberCoroutineScope()
    val entries = (state as? PersonalReportState.Ready)?.reports.orEmpty()
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PersonalCallReport?>(null) }
    var withdrawing by remember { mutableStateOf<PersonalCallReport?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    HestiaCard {
        SectionHeader("Números marcados por ti", "Reportes personales guardados en este dispositivo.")
        SecondaryButton(if (expanded) "Ocultar reportes personales" else "Ver reportes personales", { expanded = !expanded })
        if (allowEntry && state is PersonalReportState.Ready) {
            SecondaryButton("Marcar un número", { adding = true })
        }
        feedback?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (expanded) when (state) {
            PersonalReportState.Loading -> Text("Abriendo reportes personales…")
            PersonalReportState.Unavailable -> {
                Text("No se pudo abrir el archivo de reportes.")
                SecondaryButton("Volver a intentar", { scope.launch { reports.load() } })
            }
            is PersonalReportState.Ready -> if (entries.isEmpty()) Text("Aún no has marcado números.") else {
                OutlinedTextField(query, { query = it.take(32) }, Modifier.fillMaxWidth(),
                    label = { Text("Buscar número") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                val digits = query.filter(Char::isDigit)
                val matching = entries.values.filter { report ->
                    digits.isEmpty() || report.normalizedNumber.contains(digits) || report.originalNumber.filter(Char::isDigit).contains(digits)
                }.sortedByDescending(PersonalCallReport::modifiedAt)
                if (matching.isEmpty()) Text("No encontramos ese número.")
                matching.forEach { report ->
                    TextButton({ detail = report.normalizedNumber }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(report.originalNumber, style = MaterialTheme.typography.titleMedium)
                            Text("${report.reason.label} · ${personalDate(report.createdAt)}", style = MaterialTheme.typography.bodyMedium)
                            if (report.active) PersonalMarkBanner(title = "Activo") else Text("Retirado")
                        }
                    }
                }
            }
        }
    }
    val report = detail?.let(entries::get)
    if (report != null) AlertDialog(
        onDismissRequest = { detail = null },
        title = { Text(report.originalNumber) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (report.active) PersonalMarkBanner(title = "Activo") else Text("Retirado")
            Text(report.reason.label)
            Text("Fecha del reporte: ${personalDate(report.createdAt)}")
            Text("Último cambio: ${personalDate(report.modifiedAt)}")
            report.description?.let { Text(it) }
            if (report.active) {
                SecondaryButton("Editar reporte", { detail = null; editing = report })
                SecondaryButton("Retirar mi reporte", { detail = null; withdrawing = report })
            }
        } },
        confirmButton = { TextButton({ detail = null }, Modifier.heightIn(min = 48.dp)) { Text("Cerrar") } },
    )
    if (adding || editing != null) PersonalReportEditor(reports, existing = editing,
        onClose = { adding = false; editing = null }, onSaved = { feedback = "Reporte personal guardado." })
    withdrawing?.let { item ->
        PersonalReportWithdrawal(reports, item, onClose = { withdrawing = null }, onWithdrawn = { feedback = "Reporte personal retirado." })
    }
}

@Composable
internal fun PersonalReportEditor(
    reports: PersonalReports,
    number: String? = null,
    existing: PersonalCallReport? = null,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    var input by remember { mutableStateOf(existing?.originalNumber ?: number.orEmpty()) }
    var reason by remember { mutableStateOf(existing?.reason) }
    var note by remember { mutableStateOf(existing?.description.orEmpty()) }
    var confirming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val chosen = reason
    val valid = remember(input, existing) { existing != null || reports.normalize(input) != null }
    AlertDialog(
        onDismissRequest = { if (!busy) { if (confirming) confirming = false else onClose() } },
        title = { Text(if (confirming) "¿Guardar este reporte personal?" else if (existing != null) "Editar reporte" else "Reportar número") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (confirming || number != null || existing != null) Text(input, style = MaterialTheme.typography.titleLarge)
            else OutlinedTextField(input, { input = it.take(48) }, Modifier.fillMaxWidth(), label = { Text("Número de teléfono") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = input.isNotBlank() && !valid, supportingText = { Text("Incluye el código de país si corresponde.") })
            if (confirming) {
                chosen?.let { Text(it.label) }
                Text("Hestia te advertirá si este número vuelve a llamar. Tu reporte queda guardado solo en este dispositivo.")
                Text("El número que muestra la llamada puede ser suplantado.")
            } else {
                Text("Elige el motivo para marcar este número en tu dispositivo.")
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PersonalReportReason.entries.forEach { option ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(chosen == option, role = Role.RadioButton) { reason = option },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RadioButton(chosen == option, onClick = null)
                            Text(option.label)
                        }
                    }
                }
                OutlinedTextField(note, { note = it.take(PERSONAL_REPORT_DESCRIPTION_LIMIT) }, Modifier.fillMaxWidth(),
                    label = { Text("Descripción opcional") }, maxLines = 4)
            }
            error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton({
            if (!confirming) { confirming = true; error = null }
            else if (chosen != null && !busy) {
                busy = true
                error = null
                scope.launch {
                    try {
                        reports.save(existing?.normalizedNumber ?: input, chosen, note, existing?.id)
                        onClose()
                        onSaved()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: PersonalReportChanged) { error = "Este reporte cambió. Cierra el formulario y revisa el estado actual." }
                    catch (_: Exception) { error = "No se pudo guardar el reporte en este dispositivo." }
                    finally { busy = false }
                }
            }
        }, Modifier.heightIn(min = 48.dp), enabled = valid && chosen != null && !busy) {
            Text(if (busy) "Guardando…" else if (confirming) "Guardar reporte" else "Revisar reporte")
        } },
        dismissButton = { TextButton({ if (confirming) confirming = false else onClose() }, Modifier.heightIn(min = 48.dp), enabled = !busy) {
            Text(if (confirming) "Volver" else "Cancelar")
        } },
    )
}

@Composable
internal fun PersonalReportWithdrawal(reports: PersonalReports, report: PersonalCallReport, onClose: () -> Unit, onWithdrawn: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("¿Retirar tu reporte personal?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(report.originalNumber)
            Text("Hestia dejará de mostrar la advertencia personal de este número.")
            error?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton({
            busy = true
            error = null
            scope.launch {
                try { reports.withdraw(report); onClose(); onWithdrawn() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "No se pudo retirar el reporte. Vuelve a abrir el detalle e inténtalo otra vez." }
                finally { busy = false }
            }
        }, Modifier.heightIn(min = 48.dp), enabled = !busy) { Text("Retirar reporte") } },
        dismissButton = { TextButton(onClose, Modifier.heightIn(min = 48.dp), enabled = !busy) { Text("Cancelar") } },
    )
}

private fun personalDate(time: Instant): String = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
    .withZone(ZoneId.systemDefault()).format(time)
