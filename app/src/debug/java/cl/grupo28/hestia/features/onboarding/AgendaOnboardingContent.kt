package cl.grupo28.hestia.features.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cl.grupo28.hestia.core.components.PrimaryButton
import cl.grupo28.hestia.core.components.SecondaryButton
import cl.grupo28.hestia.core.components.SectionHeader
import cl.grupo28.hestia.features.contacts.*

internal const val agendaOnboardingTitle = "Agenda del teléfono"
internal const val agendaOnboardingDescription = "Puedes buscar personas en tu agenda y elegir qué números guardar como contactos de confianza en Familia."

@Composable
internal fun AgendaOnboardingContent(continueButton: @Composable (String) -> Unit) {
    val access = rememberAgendaAccess()
    var choose by rememberSaveable { mutableStateOf(false) }
    var openWhenGranted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(access.permission, openWhenGranted) {
        if (openWhenGranted && access.permission == AgendaPermission.Granted) {
            choose = true
            openWhenGranted = false
        }
    }
    AgendaPermissionActions(access.copy(
        request = { openWhenGranted = true; access.request() },
        settings = { openWhenGranted = true; access.settings() },
    ))
    if (access.permission == AgendaPermission.Granted) {
        PrimaryButton("Elegir contactos de confianza", { choose = true })
        if (choose) AgendaWindow(access, AgendaUse.Import, onClose = { choose = false })
    }
    continueButton(if (access.permission == AgendaPermission.Granted) "Continuar" else "Continuar sin agenda")
}

@Composable
internal fun AgendaRehearsal(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Paso 5 de 7", style = MaterialTheme.typography.labelLarge)
                SectionHeader(agendaOnboardingTitle, agendaOnboardingDescription)
                AgendaOnboardingContent { label -> SecondaryButton(label, onClose) }
            }
        }
    }
}
