package cl.grupo28.hestia.features.family

import android.app.KeyguardManager
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.components.PrimaryButton
import cl.grupo28.hestia.core.components.SecondaryButton
import cl.grupo28.hestia.data.DataStoreSettingsRepository
import cl.grupo28.hestia.domain.HelpRequest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

@Composable
internal fun TrustHelpDialog(
    request: HelpRequest,
    onClose: () -> Unit,
    preferredId: String? = null,
    callInProgress: Boolean = false,
    canOpenNow: () -> Boolean = { true },
) {
    val context = LocalContext.current
    val actions = remember(context) { FamilyAndroidActions(context) }
    var readFailed by remember { mutableStateOf(false) }
    val contacts by remember(context) {
        DataStoreSettingsRepository(context).settings.map { it.contacts }.catch {
            if (it !is IOException) throw it
            readFailed = true
        }
    }.collectAsState(initial = null)
    var selectedId by rememberSaveable(request, preferredId) { mutableStateOf(preferredId) }
    var channel by rememberSaveable(request, preferredId) { mutableStateOf<HelpChannel?>(null) }
    var text by rememberSaveable(request, preferredId) { mutableStateOf(request.suggestedMessage()) }
    var includeNumber by rememberSaveable(request, preferredId) { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var lastLaunch by remember { mutableLongStateOf(-1_000L) }
    val contact = contacts?.firstOrNull { it.id == selectedId }
    val selectedChannel = channel.takeIf { contact != null }
    val keyguard = remember(context) { context.getSystemService(KeyguardManager::class.java) }

    fun open(target: HelpChannel) {
        val recipient = contact ?: return
        if (callInProgress || !canOpenNow() || keyguard.isKeyguardLocked) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunch < 1_000) return
        lastLaunch = now
        val failure = actions.open(target, recipient.phone, request.message(text, includeNumber))
        error = failure != null
        status = failure ?: when (target) {
            HelpChannel.CALL -> "Confirma la llamada en el marcador."
            HelpChannel.SMS -> "Revisa y confirma el envío en Mensajes."
            HelpChannel.SHARE -> "Elige la aplicación y la persona antes de enviar."
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (selectedChannel != null) "Revisa el mensaje" else "Pedir ayuda") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    readFailed -> Text("No pudimos leer tu lista de confianza.")
                    contacts == null -> Text("Abriendo tu lista de confianza…")
                    contacts.isNullOrEmpty() -> Text("En Familia puedes agregar a una persona de confianza para pedirle ayuda.")
                    contact == null -> {
                        Text("Elige a una persona de confianza.", style = MaterialTheme.typography.bodyLarge)
                        contacts.orEmpty().forEach { person ->
                            SecondaryButton(if (person.primary) "${person.name} · Principal" else person.name, {
                                selectedId = person.id
                                channel = null
                                status = null
                            })
                        }
                    }
                    else -> {
                        Text(contact.name, style = MaterialTheme.typography.titleLarge)
                        Text(contact.phone)
                        if (callInProgress) Text("Puedes preparar un mensaje. Para abrir otra aplicación, vuelve a la llamada y cuelga cuando quieras.")
                        if (selectedChannel == null) {
                            PrimaryButton("Enviar mensaje", { channel = HelpChannel.SMS; status = null })
                            SecondaryButton("Llamar", { open(HelpChannel.CALL) }, enabled = !callInProgress)
                            SecondaryButton("Compartir advertencia", { channel = HelpChannel.SHARE; status = null })
                            SecondaryButton("Elegir otra persona", { selectedId = null; status = null })
                            Text("Tu operador puede cobrar por llamadas o SMS.")
                        } else {
                            if (selectedChannel == HelpChannel.SHARE) Text("Elige a quién enviarlo en la aplicación que abras.")
                            request.demonstrationLabel?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                            OutlinedTextField(text, { text = it.take(600); status = null }, label = { Text("Mensaje") },
                                modifier = Modifier.fillMaxWidth(), minLines = 3, textStyle = MaterialTheme.typography.bodyLarge)
                            request.dateLabel()?.let { Text(it) }
                            request.shareableNumber?.let { number ->
                                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .toggleable(includeNumber, role = Role.Checkbox, onValueChange = { includeNumber = it }),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Checkbox(includeNumber, onCheckedChange = null)
                                    Text("Incluir número de la llamada $number")
                                }
                            }
                            PrimaryButton(if (selectedChannel == HelpChannel.SMS) "Abrir Mensajes" else "Abrir selector de aplicaciones",
                                { open(selectedChannel) }, enabled = text.isNotBlank() && !callInProgress)
                        }
                    }
                }
                status?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
            }
        },
        confirmButton = {
            TextButton({ if (selectedChannel != null) { channel = null; status = null } else onClose() }, Modifier.heightIn(min = 48.dp)) {
                Text(if (selectedChannel != null) "Volver" else "Cerrar")
            }
        },
    )
}
