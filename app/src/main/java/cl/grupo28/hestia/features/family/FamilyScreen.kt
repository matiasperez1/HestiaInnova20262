package cl.grupo28.hestia.features.family

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.HestiaViewModel
import cl.grupo28.hestia.core.components.*
import cl.grupo28.hestia.domain.EmergencyContact
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun FamilyScreen(contacts: List<EmergencyContact>, saving: Boolean, model: HestiaViewModel, onCall: (EmergencyContact) -> Unit) {
    var managing by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editContact by remember { mutableStateOf<EmergencyContact?>(null) }
    var deleting by remember { mutableStateOf<EmergencyContact?>(null) }
    var advice by rememberSaveable { mutableStateOf(false) }
    val primary = contacts.firstOrNull { it.primary }

    fun edit(contact: EmergencyContact? = null) {
        editContact = contact
        editing = true
    }

    BackHandler(managing && !editing && deleting == null) { managing = false }
    key(managing) {
        Page(if (managing) "Administrar contactos" else "Familia", "Tus personas de confianza") {
            if (managing) {
                SecondaryButton("Volver a Familia", { managing = false })
                PrimaryButton("Agregar contacto de confianza", { edit() }, enabled = !saving)
                contacts.forEachIndexed { index, contact ->
                    HestiaCard {
                        ContactIdentity(contact)
                        if (!contact.primary) SecondaryButton("Marcar como principal", { model.setPrimaryContact(contact.id) },
                            Modifier.semantics { contentDescription = "Marcar a ${contact.name} como principal" }, enabled = !saving)
                        if (index > 0 && !contact.primary && !contacts[index - 1].primary) {
                            SecondaryButton("Subir prioridad", { model.moveContactUp(contact.id) },
                                Modifier.semantics { contentDescription = "Subir prioridad de ${contact.name}" }, enabled = !saving)
                        }
                        SecondaryButton("Editar", { edit(contact) }, Modifier.semantics { contentDescription = "Editar a ${contact.name}" }, enabled = !saving)
                        TextButton({ deleting = contact }, Modifier.heightIn(min = 48.dp)
                            .semantics { contentDescription = "Eliminar a ${contact.name}" }, enabled = !saving) { Text("Eliminar") }
                    }
                }
            } else {
                if (contacts.isEmpty()) {
                    EmptyState("Tu lista está vacía", "Agrega a una persona de confianza para poder pedirle ayuda cuando la necesites.", HestiaSymbol.FAMILY)
                    PrimaryButton("Agregar contacto de confianza", { edit() })
                } else {
                    if (primary == null) Text("Puedes marcar un contacto principal en Administrar contactos.")
                    contacts.forEach { contact ->
                        HestiaCard(color = if (contact.primary) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                            ContactIdentity(contact)
                            SecondaryButton("Llamar", { onCall(contact) }, Modifier.semantics { contentDescription = "Llamar a ${contact.name}" })
                        }
                    }
                    SecondaryButton("Administrar contactos", { managing = true })
                }
                SecondaryButton("Qué hacer si tienes dudas", { advice = true })
            }
        }
    }
    if (editing) ContactEditor(saving, { editing = false }, { name, phone, relationship ->
        model.saveContact(editContact?.id, name, phone, relationship) { editing = false }
    }, contact = editContact)
    deleting?.let { contact ->
        AlertDialog(onDismissRequest = { if (!saving) deleting = null }, title = { Text("¿Eliminar a ${contact.name}?") },
            confirmButton = { TextButton({ model.removeContact(contact.id) { deleting = null } }, Modifier.heightIn(min = 48.dp), enabled = !saving) { Text("Eliminar contacto") } },
            dismissButton = { TextButton({ deleting = null }, Modifier.heightIn(min = 48.dp), enabled = !saving) { Text("Cancelar") } })
    }
    if (advice) AlertDialog(
        onDismissRequest = { advice = false }, title = { Text("Qué hacer si tienes dudas") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Haz una pausa. No compartas claves, códigos ni datos de pago.")
            Text("Verifica con un canal oficial que ya conozcas. Puedes pedirle a alguien de confianza que revise la situación contigo.")
        } },
        confirmButton = { TextButton({ advice = false }, Modifier.heightIn(min = 48.dp)) { Text("Entendido") } },
    )
}

@Composable
private fun ContactIdentity(contact: EmergencyContact) {
    if (contact.primary) Text("Contacto principal", style = MaterialTheme.typography.labelLarge)
    SectionHeader(contact.name, contact.relationship)
    Text(contact.phone, style = MaterialTheme.typography.bodyLarge)
}

@Composable
internal fun ContactEditor(
    saving: Boolean, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit,
    contact: EmergencyContact? = null, error: String? = null,
) {
    val context = LocalContext.current
    val actions = remember(context) { FamilyAndroidActions(context) }
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(contact?.id) { mutableStateOf(contact?.name.orEmpty()) }
    var phone by rememberSaveable(contact?.id) { mutableStateOf(contact?.phone.orEmpty()) }
    var relationship by rememberSaveable(contact?.id) { mutableStateOf(contact?.relationship.orEmpty()) }
    var pickerError by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK) scope.launch {
            try {
                val chosen = uri?.let { actions.readContact(it) }
                pickerError = chosen == null
                if (chosen != null) {
                    name = chosen.first.take(60)
                    phone = chosen.second.take(24)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { pickerError = true }
        }
    }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(if (contact == null) "Agregar contacto" else "Editar contacto") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (contact == null) SecondaryButton("Elegir de mis contactos", {
                pickerError = false
                try { picker.launch(actions.contactPicker()) }
                catch (_: ActivityNotFoundException) { pickerError = true }
                catch (_: SecurityException) { pickerError = true }
            })
            if (pickerError) Text("No se pudo abrir el contacto. Puedes escribir el nombre y el teléfono.", color = MaterialTheme.colorScheme.error)
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it.take(24) }, label = { Text("Teléfono") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(relationship, { relationship = it.take(40) }, label = { Text("Relación (opcional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton({ onSave(name, phone, relationship) }, enabled = !saving && name.isNotBlank() && phone.isNotBlank(),
            modifier = Modifier.heightIn(min = 48.dp)) { Text(if (saving) "Guardando…" else "Guardar contacto") } },
        dismissButton = { TextButton(onDismiss, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } })
}
