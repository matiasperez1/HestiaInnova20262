package cl.grupo28.hestia.features.gmail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.components.*
import cl.grupo28.hestia.domain.gmail.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GmailPanel(state: GmailUiState, onConnect: () -> Unit, onSync: () -> Unit, onDisconnect: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    HestiaCard {
        SectionHeader("Gmail · revisión manual", if (state.verified) "Disponible para revisión manual" else "Sin conexión verificada")
        Text("Solo lectura. Revisamos la bandeja de entrada de esta semana cuando tú lo pides.", style = MaterialTheme.typography.bodyLarge)
        if (!state.eligible) Text("Primero inicia sesión y verifica tu cuenta de Hestia.", style = MaterialTheme.typography.bodyMedium)
        PrimaryButton(if (state.busy) when (state.operation) {
            GmailOperation.REVIEWING -> "Revisando correos…"
            GmailOperation.AUTHORIZING -> "Comprobando permiso…"
            GmailOperation.DISCONNECTING -> "Desconectando…"
            null -> "Un momento…"
        } else if (state.verified) "Revisar correos ahora" else "Conectar Gmail", if (state.verified) onSync else onConnect, enabled = state.eligible && !state.busy)
        if (state.snapshot != null || state.verified || state.authorizationReceived) SecondaryButton("Desconectar Gmail", { confirming = true }, enabled = !state.busy)
        state.snapshot?.let { snapshot ->
            Text("Período revisado: ${snapshot.week.start.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-CL")))} al ${snapshot.week.end.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-CL")))}.", style = MaterialTheme.typography.bodyMedium)
            Text("Última revisión: ${snapshot.records.size} correos; ${snapshot.suspicious} con señales de riesgo.", style = MaterialTheme.typography.bodyLarge)
            Text("Revisado el ${DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("es-CL")).withZone(ZoneId.systemDefault()).format(snapshot.checkedAt)}. No es seguimiento automático.", style = MaterialTheme.typography.bodyMedium)
            if (snapshot.partial) Text("Resultado parcial: se alcanzó un límite de revisión. No representa toda tu bandeja.", style = MaterialTheme.typography.bodyMedium)
        }
        state.message?.let { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyLarge) }
    }
    if (confirming) AlertDialog(onDismissRequest = { confirming = false }, title = { Text("¿Desconectar Gmail?") },
        text = { Text("Google revocará todos los permisos que Hestia tenga concedidos sobre tu cuenta, no solamente el acceso a Gmail. Borraremos la revisión local y desconectaremos el seguimiento automático si existe; sus resultados guardados se conservan. Tu sesión de Hestia no se cerrará; Google puede pedir autorización al entrar de nuevo.") },
        confirmButton = { TextButton({ confirming = false; onDisconnect() }, Modifier.heightIn(min=48.dp)) { Text("Desconectar") } },
        dismissButton = { TextButton({ confirming = false }, Modifier.heightIn(min=48.dp)) { Text("Cancelar") } })
}

@Composable
fun GmailScreen(state: GmailUiState, onConnect: () -> Unit, onSync: () -> Unit, onDisconnect: () -> Unit, automaticAvailable: Boolean = false, manualAvailable: Boolean = true, automatic: @Composable () -> Unit = {}) {
    var manual by rememberSaveable { mutableStateOf(!automaticAvailable) }
    LaunchedEffect(manualAvailable) { if(!manualAvailable)manual=false }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Text("Revisar correos", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                if (!manual) automatic()
                if (automaticAvailable && manualAvailable) SecondaryButton(if(manual) "Volver a revisión automática" else "Usar revisión manual", {manual=!manual})
                if (manual) {
                    Text("Revisión manual en este teléfono", style = MaterialTheme.typography.titleLarge)
                    GmailPanel(state, onConnect, onSync, onDisconnect)
                }
                Text("Las señales son orientativas. Un resultado bajo no garantiza autenticidad. Nunca compartas claves ni códigos.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        val snapshot = state.snapshot?.takeIf { manual }
        if (snapshot != null && snapshot.records.isEmpty() && !snapshot.partial) item { EmptyState("Sin correos para revisar", "No encontramos correos en la bandeja de entrada para el período consultado.", HestiaSymbol.MAIL) }
        items(snapshot?.records.orEmpty().sortedByDescending { it.analysis.level.ordinal }, key = { it.fingerprint }) { record ->
            HestiaCard(color = when(record.analysis.level) { EmailRisk.HIGH -> MaterialTheme.colorScheme.errorContainer; EmailRisk.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer; EmailRisk.LOW -> MaterialTheme.colorScheme.surface }) {
                Text(record.subject.ifBlank { "Correo sin asunto" }, style = MaterialTheme.typography.titleLarge)
                Text(record.sender.ifBlank { "Remitente no disponible" }, style = MaterialTheme.typography.bodyMedium)
                Text(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("es-CL")).withZone(ZoneId.systemDefault()).format(record.receivedAt), style = MaterialTheme.typography.bodyMedium)
                Text("El remitente puede ser suplantado.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HestiaIcon(if (record.analysis.level == EmailRisk.LOW) HestiaSymbol.MAIL else HestiaSymbol.ALERT)
                    Text(when(record.analysis.level) { EmailRisk.LOW -> "Sin señales evidentes"; EmailRisk.MEDIUM -> "Precaución"; EmailRisk.HIGH -> "Señales de riesgo alto" }, style = MaterialTheme.typography.titleMedium)
                }
                record.analysis.reasons.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                if (record.limitedContent) Text("El análisis del contenido fue limitado. No revisamos adjuntos.", style = MaterialTheme.typography.bodyMedium)
                Text(if (record.analysis.level == EmailRisk.LOW) "Verifica por un canal oficial antes de entregar datos o pagar."
                    else "No sigas sus enlaces ni entregues datos. Verifica por un canal oficial que ya conozcas.", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
