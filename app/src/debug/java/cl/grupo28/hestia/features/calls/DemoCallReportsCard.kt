package cl.grupo28.hestia.features.calls

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.components.HestiaCard
import cl.grupo28.hestia.core.components.PrimaryButton
import cl.grupo28.hestia.core.components.SecondaryButton
import cl.grupo28.hestia.core.components.SectionHeader
import cl.grupo28.hestia.data.calls.CALL_DEMO_PERMISSION
import cl.grupo28.hestia.data.calls.CallPersona
import cl.grupo28.hestia.data.calls.CallReportCategory
import cl.grupo28.hestia.data.calls.DemoCallReports
import cl.grupo28.hestia.data.calls.DemoReputationSnapshot
import cl.grupo28.hestia.data.calls.canSyncCallDemo
import cl.grupo28.hestia.data.calls.communityCallNumber
import cl.grupo28.hestia.domain.CallClassification
import cl.grupo28.hestia.domain.automatic.AutomaticError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal data class ReportForm(val number: String = "", val category: CallReportCategory? = null)

@Composable
internal fun DemoCallReportsCard() {
    val context = LocalContext.current
    val reports = remember(context) { DemoCallReports.from(context) }
    val scope = rememberCoroutineScope()
    val persona by reports.persona.collectAsState()
    var permitted by remember { mutableStateOf(canSyncCallDemo(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    var snapshot by remember { mutableStateOf<DemoReputationSnapshot?>(null) }
    var own by remember(persona) { mutableStateOf<Map<String, CallReportCategory>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var identities by remember { mutableStateOf(false) }
    var consult by remember { mutableStateOf(false) }
    var showOwn by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<ReportForm?>(null) }
    var withdrawal by remember { mutableStateOf<String?>(null) }

    suspend fun sync(success: String? = null, failure: String = "No pudimos actualizar. Se conserva la última sincronización.") {
        try {
            val updated = reports.sync(persona)
            snapshot = updated.snapshot
            own = updated.own
            feedback = success
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            feedback = failure
        }
    }

    fun change(withdraw: Boolean, operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        feedback = null
        scope.launch {
            try {
                operation()
                val result = if (withdraw) "Reporte retirado." else "Reporte guardado."
                sync(result, "$result La reputación se actualizará al volver a sincronizar.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                feedback = if (error is AutomaticError && error.code == "CALL_REPORT_ALREADY_EXISTS") {
                    "Ya reportado por ti. Usa Mis reportes para corregir la categoría."
                } else "No pudimos confirmar el cambio. Sincroniza y revisa tus reportes antes de reintentar."
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(persona, permitted) {
        busy = true
        feedback = null
        try {
            snapshot = reports.cached()
            if (permitted) sync()
        } finally {
            busy = false
        }
    }

    HestiaCard {
        SectionHeader("Reportes compartidos", "DEMO LOCAL · Solo números ficticios")
        SecondaryButton("Identidad de prueba: ${persona.label}", { identities = true }, enabled = !busy)
        DropdownMenu(expanded = identities, onDismissRequest = { identities = false }) {
            CallPersona.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label, style = MaterialTheme.typography.bodyLarge) }, onClick = {
                    identities = false
                    reports.select(option)
                })
            }
        }
        val date = snapshot?.syncedAt?.let { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(it) }
        Text(if (date == null) "Sin sincronizaciones previas" else "Última sincronización: $date")
        if (busy) Text("Actualizando reportes…")
        feedback?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (!permitted) {
            Text("Para sincronizar con el computador, permite la conexión local de Hestia.")
            SecondaryButton("Permitir conexión local", { permission.launch(CALL_DEMO_PERMISSION) })
        }
        PrimaryButton("Reportar número", { form = ReportForm() }, enabled = permitted && !busy)
        SecondaryButton("Consultar reputación", { consult = true })
        SecondaryButton("Mis reportes", { showOwn = !showOwn }, enabled = !busy)
        if (showOwn) {
            val ownReports = own
            when {
                ownReports == null -> Text("Sincroniza para ver tus reportes.")
                ownReports.isEmpty() -> Text("Aún no tienes reportes.")
                else -> ownReports.forEach { (number, category) ->
                    Text("$number · ${category.label}", style = MaterialTheme.typography.titleMedium)
                    SecondaryButton("Corregir reporte de $number", { form = ReportForm(number, category) }, enabled = permitted && !busy)
                    SecondaryButton("Retirar reporte de $number", { withdrawal = number }, enabled = permitted && !busy)
                }
            }
        }
        SecondaryButton("Sincronizar reputación", {
            busy = true
            scope.launch { try { sync("Reputación actualizada.") } finally { busy = false } }
        }, enabled = permitted && !busy)
    }

    if (consult) ConsultationDialog(snapshot) { consult = false }
    form?.let { initial ->
        ReportDialog(initial, persona, { form = null }, onSave = { number, category ->
            form = null
            val author = persona
            change(withdraw = false) { reports.save(author, number, category, replace = initial.category != null) }
        })
    }
    withdrawal?.let { number ->
        AlertDialog(
            onDismissRequest = { withdrawal = null },
            title = { Text("Retirar reporte") },
            text = { Text("Se retirará solo el reporte de ${persona.label} sobre $number.") },
            confirmButton = { TextButton({
                withdrawal = null
                val author = persona
                change(withdraw = true) { reports.withdraw(author, number) }
            }) { Text("Retirar") } },
            dismissButton = { TextButton({ withdrawal = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ConsultationDialog(snapshot: DemoReputationSnapshot?, onClose: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val number = communityCallNumber(input)
    val result = number?.let { snapshot?.let { data -> reputationLabel(data, it) } }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Consultar reputación") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DemoNumberField(input, { input = it })
            Text(result ?: when {
                snapshot == null -> "Sincroniza para consultar la reputación."
                input.isNotBlank() && number == null -> "Ingresa un número de la lista de prueba comunitaria."
                else -> "La consulta usa la última sincronización."
            }, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (number != null && snapshot != null) ReputationCategories(snapshot, number)
        } },
        confirmButton = { TextButton(onClose) { Text("Cerrar") } },
    )
}

@Composable
internal fun ReportDialog(
    initial: ReportForm, persona: CallPersona, onClose: () -> Unit, onSave: (String, CallReportCategory) -> Unit,
    fixed: Boolean = false, postCall: Boolean = false, contextLabel: String? = null,
    busy: Boolean = false, feedback: String? = null, submitLabel: String? = null,
    confirmBeforeSend: Boolean = false, displayNumber: String = initial.number,
    afterCallActions: @Composable () -> Unit = {},
) {
    var input by rememberSaveable { mutableStateOf(initial.number) }
    var category by rememberSaveable { mutableStateOf(initial.category) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val number = communityCallNumber(input)
    val editing = initial.category != null
    val reason = category
    if (confirming && number != null && reason != null) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("¿Enviar este reporte?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(displayNumber, style = MaterialTheme.typography.titleLarge)
                Text(reason.label)
                Text("Reportas un comportamiento observado. La identidad de quien llamó puede ser distinta de la asociada al número.")
                Text("El reporte se guardará en DEMO LOCAL con la identidad de prueba ${persona.label}.")
            } },
            confirmButton = { TextButton({ confirming = false; onSave(number, reason) }, Modifier.heightIn(min = 48.dp)) { Text("Confirmar envío") } },
            dismissButton = { TextButton({ confirming = false }, Modifier.heightIn(min = 48.dp)) { Text("Volver al motivo") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(if (postCall) "¿Quieres reportar esta llamada?" else if (editing) "Corregir reporte" else "Reportar un número") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (fixed) Text(displayNumber, style = MaterialTheme.typography.titleLarge)
            else DemoNumberField(input, { input = it }, readOnly = editing)
            contextLabel?.let { Text(it) }
            afterCallActions()
            feedback?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error) }
            Text("Elige una categoría")
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CallReportCategory.entries.filter { it.selectable || it == initial.category }.forEach { option ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected = category == option, enabled = !busy, role = Role.RadioButton, onClick = { category = option }),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = category == option, onClick = null)
                        Text(option.label)
                    }
                }
            }
            if (!confirmBeforeSend) Text("Al enviar, autorizas guardar el número ficticio y la categoría con la identidad de prueba ${persona.label}.")
        } },
        confirmButton = {
            TextButton({ if (number != null && reason != null) {
                if (confirmBeforeSend) confirming = true else onSave(number, reason)
            } }, Modifier.heightIn(min = 48.dp),
                enabled = number != null && category != null && !busy) {
                Text(when {
                    busy -> "Enviando…"
                    submitLabel != null -> submitLabel
                    confirmBeforeSend -> "Revisar reporte"
                    fixed && !editing -> "Enviar reporte"
                    else -> "Guardar reporte"
                })
            }
        },
        dismissButton = { TextButton(onClose, Modifier.heightIn(min = 48.dp), enabled = !busy) { Text(if (postCall) "Ahora no" else "Cancelar") } },
    )
}

@Composable
private fun DemoNumberField(value: String, onChange: (String) -> Unit, readOnly: Boolean = false) {
    OutlinedTextField(value, { if (it.length <= 24) onChange(it) }, Modifier.fillMaxWidth(),
        label = { Text("Número de prueba") }, readOnly = readOnly, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        supportingText = { Text("Números ficticios de 2025550100 a 2025550199.") })
}

internal fun reputationLabel(snapshot: DemoReputationSnapshot, number: String): String {
    val count = snapshot.fraudCounts[number] ?: 0
    return when (snapshot.verdict(number).classification) {
        CallClassification.PENDING_CORROBORATION -> "Pendiente de corroboración · $count ${if (count == 1) "reporte de fraude" else "reportes de fraude"}"
        CallClassification.SUSPICIOUS -> "Sospechoso · $count reportes de fraude independientes"
        CallClassification.NO_REPORTS -> if ((snapshot.counts[number] ?: 0) > 0) "Sin reportes de fraude" else "Sin reportes conocidos"
    }
}

@Composable
internal fun ReputationCategories(snapshot: DemoReputationSnapshot, number: String) {
    snapshot.categoryCounts[number].orEmpty().forEach { (category, total) -> Text("${category.label} · $total") }
}
