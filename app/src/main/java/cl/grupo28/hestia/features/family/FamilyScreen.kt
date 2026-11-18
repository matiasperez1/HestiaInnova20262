package cl.grupo28.hestia.features.family

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.components.*
import cl.grupo28.hestia.domain.EmergencyContact

@Composable
fun FamilyScreen(contacts: List<EmergencyContact>, saving: Boolean, onAdd: (String, String, () -> Unit) -> Unit, onRemove: (String, () -> Unit) -> Unit, onCall: (EmergencyContact) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<EmergencyContact?>(null) }
    Page("Familia", "Tus personas de confianza.") {
        PrimaryButton("Agregar contacto", { adding = true })
        if (contacts.isEmpty()) EmptyState("Tu lista está vacía", "Agrega a alguien a quien puedas llamar si tienes dudas.", HestiaSymbol.FAMILY)
        contacts.forEach { contact ->
            HestiaCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                        Text(contact.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(contact.name, style = MaterialTheme.typography.titleLarge)
                        Text(contact.phone, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                SecondaryButton("Llamar", { onCall(contact) }, Modifier.semantics { contentDescription = "Llamar a ${contact.name}" })
                TextButton({ deleting = contact }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Eliminar a ${contact.name}" }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (adding) AddContactDialog(saving, { adding = false }, { name, phone -> onAdd(name, phone) { adding = false } })
    deleting?.let { contact ->
        AlertDialog(onDismissRequest = { if (!saving) deleting = null }, title = { Text("¿Eliminar a ${contact.name}?") },
            confirmButton = { TextButton({ onRemove(contact.id) { deleting = null } }, enabled = !saving) { Text("Eliminar contacto", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ deleting = null }, enabled = !saving) { Text("Cancelar") } })
    }
}

@Composable
internal fun AddContactDialog(saving: Boolean, onDismiss: () -> Unit, onSave: (String, String) -> Unit, error: String? = null) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("Agregar contacto") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it.take(24) }, label = { Text("Teléfono") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton({ onSave(name, phone) }, enabled = !saving && name.isNotBlank() && phone.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp)) { Text(if (saving) "Guardando…" else "Guardar contacto") } },
        dismissButton = { TextButton(onDismiss, enabled = !saving, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } })
}
