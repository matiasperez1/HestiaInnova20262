package cl.grupo28.hestia.features.account

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.components.*
import cl.grupo28.hestia.domain.auth.AuthState

@Composable
fun AccountCard(state: AuthState, onSignIn: () -> Unit, onSignOut: () -> Unit, onRetry: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    HestiaCard {
        SectionHeader("Cuenta")
        when {
            state.account != null -> {
                Text(state.account.email, style = MaterialTheme.typography.bodyLarge)
                Text(if (state.verified) "Sesión iniciada en Hestia" else "Cuenta pendiente de verificar", style = MaterialTheme.typography.bodyMedium)
                if (!state.verified) PrimaryButton("Verificar cuenta", onRetry, enabled = !state.busy)
                SecondaryButton(if (state.busy) "Un momento…" else "Cerrar sesión", { confirming = true }, enabled = !state.busy)
            }
            else -> {
                Text("Puedes usar Hestia sin cuenta.", style = MaterialTheme.typography.bodyLarge)
                PrimaryButton(if (state.busy) "Un momento…" else "Iniciar sesión con Google", onSignIn, enabled = state.configured && !state.busy)
                if (!state.configured) Text("El inicio de sesión aún no está disponible en esta versión.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("Iniciar sesión no conecta Gmail ni activa la protección.", style = MaterialTheme.typography.bodyMedium)
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("¿Cerrar sesión?") },
        text = { Text("Tus contactos, lecciones y ajustes se conservarán en este teléfono.") },
        confirmButton = { TextButton({ confirming = false; onSignOut() }, Modifier.heightIn(min = 48.dp)) { Text("Cerrar sesión") } },
        dismissButton = { TextButton({ confirming = false }, Modifier.heightIn(min = 48.dp)) { Text("Cancelar") } })
}
