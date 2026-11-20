package cl.grupo28.hestia.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.accessibility.*
import cl.grupo28.hestia.domain.auth.AuthState
import cl.grupo28.hestia.features.account.AccountCard
import cl.grupo28.hestia.features.gmail.*
import cl.grupo28.hestia.features.calls.*
import cl.grupo28.hestia.core.components.*

@Composable
fun SettingsScreen(preferences: DisplayPreferences, saving: Boolean, onChange: (DisplayPreferences) -> Unit, account: AuthState, onSignIn: () -> Unit, onSignOut: () -> Unit, onRetryAccount: () -> Unit, gmail: GmailUiState, onConnectGmail: () -> Unit, onSyncGmail: () -> Unit, onDisconnectGmail: () -> Unit, onOpenGmail: () -> Unit, calls: CallSetup, showCallSettings: Boolean = true) {
    Page("Ajustes", "Hestia, a tu manera.") {
        AccountCard(account, onSignIn, onSignOut, onRetryAccount)
        GmailPanel(gmail, onConnectGmail, onSyncGmail, onDisconnectGmail)
        SecondaryButton("Ver revisión de correos", onOpenGmail)
        if (showCallSettings) HestiaCard {
            SectionHeader("Identificación de llamadas")
            CallRoleContent(calls)
            if (calls.state.active) {
                CallNoticeContent(calls)
                CallContactCoverage()
            }
        }
        if (showCallSettings) PersonalReportsPanel(allowEntry = true)
        HestiaCard {
            SectionHeader("Tamaño del texto")
            TextSize.entries.forEach { size ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                    selected = preferences.textSize == size, role = Role.RadioButton, enabled = !saving,
                    onClick = { onChange(preferences.copy(textSize = size)) }), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected = preferences.textSize == size, onClick = null)
                    Text(size.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        HestiaCard {
            SettingSwitch("Alto contraste", preferences.highContrast, !saving) { onChange(preferences.copy(highContrast = it)) }
            SettingSwitch("Reducir animaciones", preferences.reduceMotion, !saving) { onChange(preferences.copy(reduceMotion = it)) }
            Text("Lectura en voz alta · Próximamente", style = MaterialTheme.typography.bodyMedium)
        }
        SecondaryButton("Restablecer apariencia", { onChange(DisplayPreferences()) })
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, enabled = enabled, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}
