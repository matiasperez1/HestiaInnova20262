package cl.grupo28.hestia.features.contacts

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cl.grupo28.hestia.core.components.*
import cl.grupo28.hestia.data.DataStoreSettingsRepository
import cl.grupo28.hestia.data.contacts.AgendaContact
import cl.grupo28.hestia.data.contacts.label
import cl.grupo28.hestia.data.contacts.searchAgenda
import cl.grupo28.hestia.domain.normalizeCallNumber
import cl.grupo28.hestia.features.calls.dialer.DialAttempt
import cl.grupo28.hestia.features.calls.dialer.PhoneGlyph
import cl.grupo28.hestia.features.calls.dialer.PhoneIcon
import cl.grupo28.hestia.features.calls.dialer.rememberPhoneCalling
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
internal fun AgendaPermissionActions(access: AgendaAccess) {
    when (access.permission) {
        AgendaPermission.Ask -> PrimaryButton("Permitir acceso a mis contactos", access.request)
        AgendaPermission.Denied -> {
            Text("Puedes permitir la agenda ahora o seguir usando el teclado manual.")
            PrimaryButton("Permitir acceso a mis contactos", access.request)
            SecondaryButton("Abrir Ajustes de Android", access.settings)
        }
        AgendaPermission.Settings -> {
            Text("Para recuperar el acceso, abre Ajustes de Android y elige Permisos, luego Contactos.")
            SecondaryButton("Abrir Ajustes de Android", access.settings)
        }
        AgendaPermission.Granted -> Unit
    }
}

internal enum class AgendaUse { Import, Call, PickNumber }

@Composable
internal fun AgendaDirectory(
    access: AgendaAccess,
    use: AgendaUse,
    modifier: Modifier = Modifier,
    onChooseNumber: (String) -> Unit = {},
    onDialerSetup: (() -> Unit)? = null,
    extra: @Composable (() -> Unit)? = null,
) {
    var search by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<Long?>(null) }
    val contacts = access.contacts
    val matches = remember(contacts, search) { contacts.searchAgenda(search).toList() }
    LaunchedEffect(access.permission) {
        if (access.permission != AgendaPermission.Granted) {
            selectedContact = null
            search = ""
        }
    }
    LazyColumn(
        modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 22.dp, top = 12.dp, end = 22.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                if (use == AgendaUse.Import) "Elegir contactos de confianza" else "Contactos",
                if (use == AgendaUse.Import) "Elige una persona y el número que quieres guardar en Familia."
                else "Consulta tu agenda en este teléfono y selecciona un número para llamar.",
            )
        }
        extra?.let { item { it() } }
        if (access.permission != AgendaPermission.Granted) {
            item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { AgendaPermissionActions(access) } }
        } else when (access.result) {
            AgendaResult.Loading -> item { Text("Abriendo contactos…") }
            AgendaResult.Unavailable -> item {
                Text("No se pudo abrir la agenda.")
                SecondaryButton("Volver a intentar", access.retry)
            }
            is AgendaResult.Loaded -> if (contacts.isEmpty()) {
                item { EmptyState("Tu agenda no tiene contactos con teléfono", "Puedes escribir un número en Teclado.", HestiaSymbol.FAMILY) }
            } else {
                item {
                    OutlinedTextField(search, { search = it.take(80) }, Modifier.fillMaxWidth(),
                        label = { Text("Buscar por nombre o número") }, singleLine = true)
                }
                if (matches.isEmpty()) item { Text("No encontramos contactos con esa búsqueda.") }
                items(matches, key = { it.id }) { contact ->
                    AgendaRow(contact, use, onOpen = { selectedContact = contact.id })
                }
            }
        }
    }
    contacts.firstOrNull { it.id == selectedContact }?.let { contact ->
        AgendaContactDialog(contact, use, onClose = { selectedContact = null }, onChooseNumber = {
            selectedContact = null
            onChooseNumber(it)
        }, onDialerSetup = onDialerSetup?.let { setup -> { selectedContact = null; setup() } })
    }
}

@Composable
internal fun AgendaWindow(access: AgendaAccess, use: AgendaUse, onClose: () -> Unit, onChooseNumber: (String) -> Unit = {}) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                TextButton(onClose, Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp)) { Text("Cerrar agenda") }
                AgendaDirectory(access, use, Modifier.weight(1f), onChooseNumber)
            }
        }
    }
}

@Composable
internal fun AgendaRow(contact: AgendaContact, use: AgendaUse, onOpen: () -> Unit) {
    val action = when (use) {
        AgendaUse.Import -> "Elegir a ${contact.label}"
        AgendaUse.Call -> "Números para llamar a ${contact.label}"
        AgendaUse.PickNumber -> "Elegir número de ${contact.label}"
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surface).clickable(role = Role.Button, onClickLabel = action, onClick = onOpen)
        .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        AgendaAvatar(contact)
        Column(Modifier.weight(1f)) {
            Text(contact.label, style = MaterialTheme.typography.titleMedium)
            val numbers = contact.numbers
            val subtitle = if (numbers.size > 1) "${numbers.size} números" else numbers.first().display
            if (subtitle != contact.label) Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
        if (use != AgendaUse.Import) PhoneIcon(PhoneGlyph.Handset, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AgendaAvatar(contact: AgendaContact) {
    val resolver = LocalContext.current.contentResolver
    val image by produceState<ImageBitmap?>(null, contact.thumbnail) {
        value = null
        value = contact.thumbnail?.takeIf { it.scheme == "content" }?.let { uri ->
            withContext(Dispatchers.IO) {
                try { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }
                catch (_: IOException) { null }
                catch (_: SecurityException) { null }
            }
        }
    }
    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center) {
        val photo = image
        if (photo != null) Image(photo, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (contact.name.isNotBlank()) Text(contact.name.trim().split(Regex("\\s+")).take(2)
            .joinToString("") { it.take(1).uppercase() }, style = MaterialTheme.typography.labelMedium)
        else PhoneIcon(PhoneGlyph.Contacts)
    }
}

@Composable
internal fun AgendaContactDialog(
    contact: AgendaContact,
    use: AgendaUse,
    onClose: () -> Unit,
    onChooseNumber: (String) -> Unit = {},
    onDialerSetup: (() -> Unit)? = null,
    onDial: ((String) -> DialAttempt)? = null,
) {
    val calling = if (use == AgendaUse.Call && onDial == null) rememberPhoneCalling() else null
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { DataStoreSettingsRepository(context) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var attempted by remember { mutableStateOf<DialAttempt?>(null) }
    val trusted = if (use == AgendaUse.Import) {
        val state by remember(repository) { repository.settings.catch { if (it !is IOException) throw it } }.collectAsState(null)
        state?.contacts.orEmpty()
    } else emptyList()
    AlertDialog(
        onDismissRequest = { if (!saving) onClose() },
        title = { Text(contact.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Elige un número.")
                val feedback = message ?: calling?.feedback ?: attempted?.message
                feedback?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                if (attempted == DialAttempt.NeedsRole && onDialerSetup != null) SecondaryButton("Ir a Protección", onDialerSetup)
                contact.numbers.forEach { number ->
                    Text(number.display, style = MaterialTheme.typography.bodyLarge)
                    when (use) {
                        AgendaUse.PickNumber -> SecondaryButton("Usar ${number.display}", { onChooseNumber(number.dial) })
                        AgendaUse.Call -> SecondaryButton("Llamar", {
                            attempted = onDial?.invoke(number.dial) ?: calling?.dial?.invoke(number.dial)
                            if (attempted == DialAttempt.Started) onClose()
                        }, Modifier.semantics { contentDescription = "Llamar a ${number.display}" })
                        AgendaUse.Import -> {
                            val normalized = normalizeCallNumber(number.dial)
                            if (normalized != null && trusted.any { normalizeCallNumber(it.phone) == normalized }) {
                                Text("Guardado en Familia")
                            } else SecondaryButton("Agregar a Familia", {
                                saving = true
                                message = null
                                scope.launch {
                                    try {
                                        repository.addContact(contact.label, number.dial)
                                        message = "Contacto guardado en Familia."
                                    } catch (error: IllegalArgumentException) {
                                        message = error.message ?: "Revisa el nombre y el número."
                                    } catch (_: IOException) {
                                        message = "No se pudo guardar. Inténtalo de nuevo."
                                    } finally { saving = false }
                                }
                            }, Modifier.semantics { contentDescription = "Agregar ${number.display} a Familia" }, enabled = !saving)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose, Modifier.heightIn(min = 48.dp), enabled = !saving) { Text("Volver") } },
    )
}

@Composable
internal fun AgendaMatches(access: AgendaAccess, number: String, onChooseNumber: (String) -> Unit) {
    var selected by remember { mutableStateOf<Long?>(null) }
    val contacts = access.contacts
    val matches = remember(contacts, number) {
        if (number.count(Char::isDigit) < 3) emptyList() else contacts.searchAgenda(number).take(4).toList()
    }
    if (matches.isNotEmpty()) {
        SectionHeader("Coincidencias en tu agenda")
        matches.forEach { contact -> AgendaRow(contact, AgendaUse.PickNumber) { selected = contact.id } }
    }
    contacts.firstOrNull { it.id == selected }?.let { contact ->
        AgendaContactDialog(contact, AgendaUse.PickNumber, onClose = { selected = null }, onChooseNumber = {
            selected = null
            onChooseNumber(it)
        })
    }
}
