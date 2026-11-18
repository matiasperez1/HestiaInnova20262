package cl.grupo28.hestia.features.calls

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.components.HestiaCard
import cl.grupo28.hestia.core.components.PrimaryButton
import cl.grupo28.hestia.core.components.SecondaryButton
import cl.grupo28.hestia.core.components.SectionHeader
import cl.grupo28.hestia.data.DataStoreSettingsRepository
import cl.grupo28.hestia.data.calls.PhoneRecents
import cl.grupo28.hestia.data.calls.RecentsState
import cl.grupo28.hestia.data.calls.DemoCallReports
import cl.grupo28.hestia.data.calls.CallPersona
import cl.grupo28.hestia.data.calls.DemoReputationSnapshot
import cl.grupo28.hestia.data.calls.RecentCall
import cl.grupo28.hestia.data.calls.RecentKind
import cl.grupo28.hestia.data.calls.demoCallNumber
import cl.grupo28.hestia.features.calls.dialer.HestiaDialerActivity
import cl.grupo28.hestia.features.family.AddContactDialog
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun Context.openHestiaKeypad(number: String? = null) {
    startActivity(Intent(Intent.ACTION_DIAL, number?.let { Uri.fromParts("tel", it, null) }, this, HestiaDialerActivity::class.java))
}

@Composable
internal fun PhoneContacts(onSelect: ((String) -> Unit)?, manage: Boolean = false) {
    val context = LocalContext.current
    val repository = remember(context) { DataStoreSettingsRepository(context) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var readError by remember { mutableStateOf(false) }
    val settings by remember(repository) { repository.settings.catch { if (it is IOException) readError = true else throw it } }
        .collectAsState(initial = null)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (manage) PrimaryButton("Agregar contacto", { adding = true; saveError = null })
        val contacts = settings?.contacts
        when {
            readError -> Text("No se pudo abrir tu lista de contactos.")
            contacts == null -> Text("Abriendo contactos…")
            contacts.isEmpty() -> Text("Aún no tienes contactos de confianza.")
            else -> {
                OutlinedTextField(query, { query = it.take(60) }, Modifier.fillMaxWidth(),
                    label = { Text("Buscar por nombre o número") }, singleLine = true)
                val search = query.trim()
                val matches = contacts.filter { it.name.contains(search, ignoreCase = true) || it.phone.contains(search) }
                if (matches.isEmpty()) Text("No encontramos contactos con esa búsqueda.")
                matches.forEach { contact ->
                    HestiaCard {
                        SectionHeader(contact.name, contact.phone)
                        if (onSelect != null) SecondaryButton("Marcar a ${contact.name}", { onSelect(contact.phone) })
                    }
                }
            }
        }
    }
    if (adding) AddContactDialog(saving, { adding = false }, { name, phone ->
        saving = true
        saveError = null
        scope.launch {
            try {
                repository.addContact(name, phone)
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

@Composable
internal fun PhoneRecentList(onSelect: (String) -> Unit, manage: Boolean = false) {
    val context = LocalContext.current
    val repository = remember(context) { PhoneRecents(context) }
    var error by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reports = remember(context) { DemoCallReports.from(context) }
    val persona by reports.persona.collectAsState()
    val ownership by reports.ownership.collectAsState()
    LaunchedEffect(manage, persona) {
        if (manage) try { reports.sync(persona) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { /* The local list remains usable. */ }
    }
    val settings = remember(context) { DataStoreSettingsRepository(context).settings.catch { if (it !is IOException) throw it } }
    val contacts by settings.collectAsState(initial = null)
    var options by remember { mutableStateOf<RecentCall?>(null) }
    val state by remember(repository) { repository.state.catch { if (it is IOException) error = true else throw it } }
        .collectAsState(initial = null)
    fun change(enabled: Boolean) {
        saving = true
        scope.launch {
            try { repository.setEnabled(enabled) }
            catch (_: IOException) { error = true }
            finally { saving = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (error) Text("No se pudo actualizar Recientes.", color = MaterialTheme.colorScheme.error)
        val current: RecentsState? = state
        when {
            current == null -> if (!error) Text("Abriendo llamadas recientes…")
            !current.enabled -> {
                Text("Recientes está desactivado. Si lo activas, Hestia guardará el número y la fecha de las llamadas que atiende como teléfono predeterminado, solo en este dispositivo.")
                if (manage) PrimaryButton("Activar Recientes", { change(true) }, enabled = !saving)
            }
            else -> {
                if (manage) SecondaryButton("Desactivar y borrar Recientes", { confirmDisable = true }, enabled = !saving)
                if (current.calls.isEmpty()) Text("Aún no hay llamadas recientes en Hestia.")
                current.calls.forEach { call ->
                    HestiaCard {
                        SectionHeader(call.number, "${call.kind.label} · ${recentDate(call.at)}")
                        if (call.synthetic) Text("Número de prueba")
                        if (manage) {
                            val number = demoCallNumber(call.number)
                            if (number != null) {
                                val known = contacts?.contacts?.any { demoCallNumber(it.phone) == number }
                                if (ownership[persona]?.containsKey(number) == true) Text("Ya reportado por ti")
                                else if (known == false && call.kind != RecentKind.OUTGOING && persona !in call.answeredBy) {
                                    Text("¿Quieres reportar esta llamada?")
                                }
                            }
                            SecondaryButton("Ver opciones", { options = call })
                        } else SecondaryButton("Marcar ${call.number}", { onSelect(call.number) })
                    }
                }
            }
        }
    }
    if (confirmDisable) AlertDialog(
        onDismissRequest = { confirmDisable = false },
        title = { Text("Desactivar y borrar Recientes") },
        text = { Text("Se borrará la lista local de Hestia y dejarán de guardarse nuevas llamadas.") },
        confirmButton = { TextButton({ confirmDisable = false; change(false) }) { Text("Desactivar y borrar") } },
        dismissButton = { TextButton({ confirmDisable = false }) { Text("Cancelar") } },
    )
    options?.let { call ->
        val name = contacts?.contacts?.firstOrNull { demoCallNumber(it.phone)?.let { number -> number == demoCallNumber(call.number) } == true }?.name
        RecentCallOptions(call, name, onSelect = { options = null; onSelect(call.number) }, onClose = { options = null }) { author ->
            repository.markAnswered(call.id, author)
        }
    }
}

internal fun recentDate(at: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(at)

@Composable
private fun RecentCallOptions(
    call: RecentCall, contact: String?, onSelect: () -> Unit, onClose: () -> Unit,
    onAnswered: (CallPersona) -> Unit,
) {
    val context = LocalContext.current
    val reports = remember(context) { DemoCallReports.from(context) }
    val persona by reports.persona.collectAsState()
    val ownership by reports.ownership.collectAsState()
    val number = demoCallNumber(call.number)
    var snapshot by remember { mutableStateOf<DemoReputationSnapshot?>(null) }
    var report by remember { mutableStateOf(false) }
    var consult by remember { mutableStateOf(false) }
    var syncFailed by remember { mutableStateOf(false) }
    LaunchedEffect(consult, report, persona) {
        if (!report) snapshot = reports.cached()
        if (consult) {
            syncFailed = false
            try { snapshot = reports.sync(persona).snapshot }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { syncFailed = true }
        }
    }
    if (report && number != null) {
        ContextualCallReport(number, { report = false }, contextLabel = listOfNotNull(contact, recentDate(call.at)).joinToString(" · "),
            onAnswered = onAnswered)
    } else AlertDialog(
        onDismissRequest = onClose,
        title = { Text(contact ?: call.number) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (contact != null) Text(call.number)
            Text("${call.kind.label} · ${recentDate(call.at)}")
            if (number != null) {
                snapshot?.let { Text(reputationLabel(it, number)) } ?: Text("Sin reputación sincronizada")
                if (ownership[persona]?.containsKey(number) == true) Text("Ya reportado por ti")
                if (consult) {
                    snapshot?.let { ReputationCategories(it, number) }
                    if (syncFailed) Text("No se pudo actualizar. Se conserva la última consulta.")
                }
            } else Text("Los reportes de DEMO LOCAL están disponibles para números ficticios.")
            SecondaryButton("Volver a llamar", onSelect)
            if (number != null) {
                SecondaryButton("Reportar número", { report = true })
                SecondaryButton("Consultar reputación", { consult = true }, enabled = !consult)
            }
        } },
        confirmButton = { TextButton(onClose) { Text("Cerrar") } },
    )
}
