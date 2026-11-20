package cl.grupo28.hestia.features.calls

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.components.EmptyState
import cl.grupo28.hestia.core.components.HestiaIcon
import cl.grupo28.hestia.core.components.HestiaSymbol
import cl.grupo28.hestia.core.components.PrimaryButton
import cl.grupo28.hestia.core.components.SecondaryButton
import cl.grupo28.hestia.core.components.SectionHeader
import cl.grupo28.hestia.core.components.StatusPill
import cl.grupo28.hestia.data.DataStoreSettingsRepository
import cl.grupo28.hestia.data.PersonalReportState
import cl.grupo28.hestia.data.PersonalReports
import cl.grupo28.hestia.data.calls.CallPersona
import cl.grupo28.hestia.data.calls.DemoCallReportRepository
import cl.grupo28.hestia.data.calls.DemoCallReports
import cl.grupo28.hestia.data.calls.DemoReputationSnapshot
import cl.grupo28.hestia.data.calls.PhoneRecents
import cl.grupo28.hestia.data.calls.RecentCall
import cl.grupo28.hestia.data.calls.RecentKind
import cl.grupo28.hestia.data.calls.demoCallNumber
import cl.grupo28.hestia.domain.EmergencyContact
import cl.grupo28.hestia.domain.PersonalCallReport
import cl.grupo28.hestia.domain.normalizeCallNumber
import cl.grupo28.hestia.features.calls.dialer.PhoneGlyph
import cl.grupo28.hestia.features.calls.dialer.PhoneIcon
import cl.grupo28.hestia.features.family.ContactEditor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun List<EmergencyContact>.findTrustedContact(number: String?): EmergencyContact? {
    val normalized = number?.let(::normalizeCallNumber) ?: return null
    val demo = demoCallNumber(normalized)
    return firstOrNull { normalizeCallNumber(it.phone) == normalized || (demo != null && demoCallNumber(it.phone) == demo) }
}

@Composable
internal fun PhoneContacts(onSelect: ((String) -> Unit)?, manage: Boolean = false) {
    val context = LocalContext.current
    val repository = remember(context) { DataStoreSettingsRepository(context) }
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var readError by remember { mutableStateOf(false) }
    val settings by remember(repository) { repository.settings.catch { if (it is IOException) readError = true else throw it } }
        .collectAsState(initial = null)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (manage) {
            SectionHeader("Contactos de confianza", "Se administran en Familia.")
            SecondaryButton("Agregar contacto de confianza", { adding = true; saveError = null })
        }
        val contacts = settings?.contacts
        when {
            readError -> Text("No se pudo abrir tu lista de contactos.")
            contacts == null -> Text("Abriendo contactos…")
            contacts.isEmpty() -> EmptyState("Aún no tienes contactos de confianza", "Agrega una persona desde Familia.", HestiaSymbol.FAMILY)
            else -> {
                OutlinedTextField(query, { query = it.take(60) }, Modifier.fillMaxWidth(),
                    label = { Text("Buscar por nombre o número") }, singleLine = true)
                val search = query.trim()
                val digits = search.filter(Char::isDigit)
                val matches = contacts.filter { contact ->
                    contact.name.contains(search, ignoreCase = true) ||
                        (digits.isNotEmpty() && contact.phone.filter(Char::isDigit).contains(digits))
                }
                if (matches.isEmpty()) Text("No encontramos contactos con esa búsqueda.")
                matches.forEach { contact ->
                    PhoneListRow(
                        title = contact.name,
                        identity = contact.name,
                        supporting = listOfNotNull(contact.phone, contact.relationship).joinToString(" · "),
                        onCall = onSelect?.let { { it(contact.phone) } },
                        callLabel = "Marcar a ${contact.name}",
                    )
                }
            }
        }
    }
    if (adding) ContactEditor(saving, { adding = false }, { name, phone, relationship ->
        saving = true
        saveError = null
        scope.launch {
            try {
                repository.addContact(name, phone, relationship)
                adding = false
            } catch (error: IllegalArgumentException) {
                saveError = error.message ?: "Revisa el nombre y el número."
            } catch (_: IOException) {
                saveError = "No se pudo guardar. Inténtalo de nuevo."
            } finally {
                saving = false
            }
        }
    }, error = saveError)
}

private enum class HistoryAction { Clear, Disable }

@Composable
internal fun PhoneRecentList(onSelect: (String) -> Unit, manage: Boolean = false) {
    val context = LocalContext.current
    val repository = remember(context) { PhoneRecents(context) }
    val reports = remember(context) { DemoCallReports.from(context) }
    val personalReports = rememberPersonalReports()
    val personalState by personalReports.state.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<HistoryAction?>(null) }
    var saving by remember { mutableStateOf(false) }
    var personalFeedback by remember { mutableStateOf<String?>(null) }
    var selectedCallId by rememberSaveable { mutableStateOf<String?>(null) }
    val state by remember(repository) { repository.state.catch { if (it is IOException) error = true else throw it } }
        .collectAsState(initial = null)
    val contactStore = remember(context) { DataStoreSettingsRepository(context) }
    val contacts by remember(contactStore) { contactStore.settings.catch { if (it !is IOException) throw it } }
        .collectAsState(initial = null)
    val persona by reports.persona.collectAsState()
    val snapshot by reports.reputation.collectAsState()
    val hasDemoCalls = state?.calls?.any { it.demoNumber() != null } == true
    LaunchedEffect(manage, persona, hasDemoCalls) {
        if (!hasDemoCalls) return@LaunchedEffect
        reports.cached()
        if (manage) try {
            reports.sync(persona)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) { /* The local list remains usable. */ }
    }
    fun update(operation: suspend () -> Unit) {
        saving = true
        error = false
        scope.launch {
            try { operation() }
            catch (_: IOException) { error = true }
            finally { saving = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (error) Text("No se pudo actualizar Recientes.", color = MaterialTheme.colorScheme.error)
        personalFeedback?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        val current = state
        when {
            current == null -> if (!error) Text("Abriendo llamadas recientes…")
            !current.enabled -> {
                EmptyState("Recientes está desactivado", "Si lo activas, Hestia guardará el número, la fecha y el tipo de las llamadas que gestione como teléfono predeterminado.", HestiaSymbol.PHONE)
                if (manage) PrimaryButton("Activar Recientes", { update { repository.setEnabled(true) } }, enabled = !saving)
            }
            else -> {
                if (manage) Box(Modifier.align(Alignment.End)) {
                    IconButton({ menu = true }, Modifier.size(48.dp).semantics { contentDescription = "Administrar Recientes" },
                        enabled = !saving) { PhoneIcon(PhoneGlyph.More) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Borrar historial") }, onClick = { menu = false; confirmation = HistoryAction.Clear },
                            enabled = current.calls.isNotEmpty())
                        DropdownMenuItem(text = { Text("Desactivar Recientes") }, onClick = { menu = false; confirmation = HistoryAction.Disable })
                    }
                }
                if (current.calls.isEmpty()) EmptyState("Todavía no hay llamadas",
                    "Aquí aparecerán las llamadas que gestiones con el teléfono de Hestia.", HestiaSymbol.PHONE)
                current.calls.forEach { call ->
                    val name = contacts?.contacts.orEmpty().findTrustedContact(call.number)?.name
                    val reputation = recentRisk(call, snapshot)
                    val personalNumber = remember(call, personalReports) { call.personalNumber(personalReports) }
                    PhoneListRow(
                        title = recentTitle(call, name),
                        identity = name,
                        supporting = listOfNotNull(if (name != null) call.number else null, call.kind.label, recentDate(call.at)).joinToString(" · "),
                        kind = call.kind,
                        risk = reputation?.label,
                        riskWarning = reputation?.warning == true,
                        personallyMarked = personalState.active(personalNumber) != null,
                        synthetic = call.synthetic,
                        onCall = if (normalizeCallNumber(call.number) != null) ({ onSelect(call.number) }) else null,
                        callLabel = "Marcar a ${name ?: call.number}",
                        onOptions = if (manage) ({ personalFeedback = null; selectedCallId = call.id }) else null,
                    )
                }
            }
        }
    }
    confirmation?.let { action ->
        val disable = action == HistoryAction.Disable
        val title = if (disable) "¿Desactivar y borrar Recientes?" else "¿Borrar el historial de Hestia?"
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(title) },
            text = { Text(if (disable) "Se borrará la lista de Hestia y dejarán de guardarse llamadas nuevas."
                else "Se borrarán las llamadas guardadas por Hestia. Las llamadas nuevas se seguirán guardando.") },
            confirmButton = { TextButton({
                confirmation = null
                update { if (disable) repository.setEnabled(false) else repository.clear() }
            }, Modifier.heightIn(min = 48.dp)) { Text(if (disable) "Desactivar y borrar" else "Borrar historial") } },
            dismissButton = { TextButton({ confirmation = null }, Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
        )
    }
    state?.calls?.firstOrNull { it.id == selectedCallId }?.let { call ->
        val name = contacts?.contacts.orEmpty().findTrustedContact(call.number)?.name
        key(call.id) {
            RecentCallOptions(call, name, onSelect = { selectedCallId = null; onSelect(call.number) },
                onClose = { selectedCallId = null }, onAnswered = { author -> repository.markAnswered(call.id, author) },
                personalRepository = personalReports, onPersonalResult = { personalFeedback = it })
        }
    }
}

private data class RecentReputation(val label: String, val warning: Boolean)

private fun RecentCall.demoNumber(): String? = if (synthetic) demoCallNumber(number) else null
private fun RecentCall.personalNumber(reports: PersonalReports): String? = if (synthetic) null else reports.normalize(originalNumber)

private fun recentTitle(call: RecentCall, contact: String?): String =
    contact ?: call.number.ifBlank { "Número privado" }

private fun recentRisk(call: RecentCall, snapshot: DemoReputationSnapshot?): RecentReputation? {
    val number = call.demoNumber()
    if (number != null && snapshot != null) {
        return RecentReputation(reputationLabel(snapshot, number), (snapshot.fraudCounts[number] ?: 0) > 0)
    }
    return if (call.warned) RecentReputation("Aviso de riesgo en la llamada", true) else null
}

@Composable
private fun PhoneListRow(
    title: String,
    identity: String?,
    supporting: String,
    callLabel: String,
    kind: RecentKind? = null,
    risk: String? = null,
    riskWarning: Boolean = true,
    personallyMarked: Boolean = false,
    synthetic: Boolean = false,
    onCall: (() -> Unit)? = null,
    onOptions: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val kindIcon = when (kind) {
            RecentKind.RECEIVED -> PhoneGlyph.Incoming
            RecentKind.OUTGOING -> PhoneGlyph.Outgoing
            RecentKind.MISSED -> PhoneGlyph.Missed
            RecentKind.DECLINED -> PhoneGlyph.Declined
            null -> PhoneGlyph.Contacts
        }
        Surface(Modifier.size(44.dp).clearAndSetSemantics {}, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                val initials = identity?.split(' ')?.filter(String::isNotBlank)?.take(2)?.joinToString("") { it.take(1).uppercase() }
                if (initials != null) Text(initials, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                else PhoneIcon(kindIcon)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (identity != null && kind != null) PhoneIcon(kindIcon, modifier = Modifier.size(20.dp))
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (synthetic) Text("Número de prueba", style = MaterialTheme.typography.bodyMedium)
            if (risk != null) {
                if (riskWarning) StatusPill(risk, warning = true)
                else Text(risk, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (onCall != null) IconButton(onCall, Modifier.size(48.dp).semantics { contentDescription = callLabel }) {
            HestiaIcon(HestiaSymbol.PHONE, tint = MaterialTheme.colorScheme.primary)
        }
        if (onOptions != null) IconButton(onOptions, Modifier.size(48.dp).semantics { contentDescription = "Ver opciones de $title" }) {
            PhoneIcon(PhoneGlyph.More)
        }
    }
    if (personallyMarked) PersonalMarkBanner(modifier = Modifier.padding(bottom = 8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

internal fun recentDate(at: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(at)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecentCallOptions(
    call: RecentCall,
    contact: String?,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onAnswered: (CallPersona) -> Unit,
    repository: DemoCallReportRepository? = null,
    personalRepository: PersonalReports? = null,
    onPersonalResult: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val reports = repository ?: remember(context) { DemoCallReports.from(context) }
    val scope = rememberCoroutineScope()
    val persona by reports.persona.collectAsState()
    val ownership by reports.ownership.collectAsState()
    val number = call.demoNumber()
    val personal = personalRepository ?: rememberPersonalReports()
    val personalState by personal.state.collectAsState()
    val personalNumber = remember(call, personal) { call.personalNumber(personal) }
    val personalReport = personalState.active(personalNumber)
    val valid = normalizeCallNumber(call.number) != null
    val ownsReport = number != null && ownership[persona]?.containsKey(number) == true
    val snapshot by reports.reputation.collectAsState()
    var report by remember { mutableStateOf(false) }
    var editPersonal by remember { mutableStateOf<PersonalCallReport?>(null) }
    var withdrawPersonal by remember { mutableStateOf<PersonalCallReport?>(null) }
    var confirmWithdrawal by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var consult by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        message = null
        val author = persona
        scope.launch {
            try { reports.sync(author) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { message = "No se pudo sincronizar la reputación. Se conserva la última consulta." }
            finally { refreshing = false }
        }
    }
    LaunchedEffect(reports, persona, number) {
        if (number != null) {
            reports.cached()
            refresh()
        }
    }
    if (report && number != null) {
        ContextualCallReport(
            number, { report = false }, contextLabel = listOfNotNull(contact, recentDate(call.at)).joinToString(" · "),
            onAnswered = onAnswered, onSavedClose = onClose, confirmBeforeSend = true,
            displayNumber = call.number, repository = reports,
        )
    } else if (report || editPersonal != null) {
        PersonalReportEditor(personal, call.originalNumber, editPersonal,
            onClose = { report = false; editPersonal = null }, onSaved = {
                onPersonalResult("Reporte personal guardado.")
                onClose()
            })
    } else if (withdrawPersonal != null) {
        withdrawPersonal?.let { item ->
            PersonalReportWithdrawal(personal, item, onClose = { withdrawPersonal = null }, onWithdrawn = {
                onPersonalResult("Reporte personal retirado.")
                onClose()
            })
        }
    } else {
        ModalBottomSheet(onDismissRequest = { if (!busy) onClose() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(recentTitle(call, contact), call.kind.label)
                Text(recentDate(call.at), style = MaterialTheme.typography.bodyMedium)
                if (call.synthetic) Text("Número de prueba", style = MaterialTheme.typography.bodyMedium)
                if (details) {
                    if (contact != null) Text(call.number)
                    Text("Guardada por el teléfono de Hestia.", style = MaterialTheme.typography.bodyMedium)
                    recentRisk(call, snapshot)?.let {
                        if (it.warning) StatusPill(it.label, warning = true) else Text(it.label)
                    }
                }
                if (consult) {
                    if (personalReport != null) {
                        PersonalMarkBanner()
                        Text("Motivo: ${personalReport.reason.label}")
                    } else if (!call.synthetic && personalNumber != null) Text(when (personalState) {
                        PersonalReportState.Loading -> "Abriendo reportes personales…"
                        PersonalReportState.Unavailable -> "No se pudo abrir el archivo de reportes personales."
                        is PersonalReportState.Ready -> "Sin reporte personal activo."
                    })
                    if (number == null) Text("La reputación comunitaria aún no está disponible para este número.")
                    else snapshot?.let {
                        Text(reputationLabel(it, number))
                        ReputationCategories(it, number)
                    } ?: Text("Sin reputación sincronizada.")
                }
                if (ownsReport) Text("Ya reportado por ti")
                message?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                if (valid) {
                    SheetAction("Llamar", onSelect, PhoneGlyph.Handset, enabled = !busy)
                    SheetAction("Consultar reputación", { consult = true; if (number != null) refresh() },
                        PhoneGlyph.Shield, enabled = !busy && !refreshing)
                    if (number != null) {
                        if (ownsReport) SheetAction("Retirar mi reporte", { confirmWithdrawal = true }, PhoneGlyph.Report, enabled = !busy)
                        else SheetAction("Reportar número", { report = true }, PhoneGlyph.Report, enabled = !busy)
                    } else if (personalNumber != null) {
                        if (personalReport != null) {
                            SheetAction("Retirar mi reporte", { withdrawPersonal = personalReport }, PhoneGlyph.Report)
                            SheetAction("Editar reporte", { editPersonal = personalReport }, PhoneGlyph.Info)
                        } else if (personalState is PersonalReportState.Ready) SheetAction("Reportar número", { report = true }, PhoneGlyph.Report)
                        else if (personalState == PersonalReportState.Unavailable) SheetAction("Volver a abrir reportes personales", {
                            scope.launch { personal.load() }
                        }, PhoneGlyph.Report)
                    }
                    if (!call.synthetic && contact == null) SheetAction("Agregar a contactos", {
                        if (!context.openContactEditor(call.number)) message = "No se pudo abrir el editor de contactos."
                    }, PhoneGlyph.Contacts, enabled = !busy)
                }
                SheetAction(if (details) "Ocultar detalles" else "Ver detalles de llamada", { details = !details }, PhoneGlyph.Info)
                SheetAction("Cerrar", onClose, enabled = !busy)
            }
        }
    }
    if (confirmWithdrawal && number != null) AlertDialog(
        onDismissRequest = { confirmWithdrawal = false },
        title = { Text("¿Retirar tu reporte?") },
        text = { Text("Se retirará el reporte de ${persona.label} sobre ${call.number}.") },
        confirmButton = { TextButton({
            confirmWithdrawal = false
            busy = true
            val author = persona
            scope.launch {
                try {
                    reports.withdraw(author, number)
                    message = try {
                        reports.sync(author)
                        "Reporte retirado."
                    }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { "Reporte retirado. La reputación todavía no se actualiza." }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { message = "No pudimos confirmar el retiro. Revisa la conexión y vuelve a intentar." }
                finally { busy = false }
            }
        }, Modifier.heightIn(min = 48.dp)) { Text("Retirar reporte") } },
        dismissButton = { TextButton({ confirmWithdrawal = false }, Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } },
    )
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit, glyph: PhoneGlyph? = null, enabled: Boolean = true) {
    TextButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) PhoneIcon(glyph)
            Text(label)
        }
    }
}

private fun Context.openContactEditor(number: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
        type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
        putExtra(ContactsContract.Intents.Insert.PHONE, number)
    })
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
