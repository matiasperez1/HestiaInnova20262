package cl.grupo28.hestia.features.calls

import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.grupo28.hestia.core.components.HestiaCard
import cl.grupo28.hestia.core.components.Page
import cl.grupo28.hestia.core.components.PrimaryButton
import cl.grupo28.hestia.core.components.SecondaryButton
import cl.grupo28.hestia.core.components.SectionHeader
import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.features.summary.WeeklyCallCards
import cl.grupo28.hestia.features.summary.rememberCallOverview

@Composable
internal fun PhoneScreen(calls: CallSetup, onPreview: () -> Unit, onLearnCalls: () -> Unit, startsInRecents: Boolean = false) {
    val context = LocalContext.current
    var section by rememberSaveable { mutableStateOf(if (startsInRecents) PhoneSection.RECENT else PhoneSection.HOME) }
    BackHandler(section != PhoneSection.HOME) { section = PhoneSection.HOME }
    androidx.compose.runtime.key(section) {
        when (section) {
            PhoneSection.HOME -> Page("Teléfono", "Llama y encuentra a tus personas de confianza.") {
                DialerSetup(activationOnly = true)
                PrimaryButton("Marcador", { context.openHestiaKeypad() })
                SecondaryButton("Recientes", { section = PhoneSection.RECENT })
                SecondaryButton("Contactos", { section = PhoneSection.CONTACTS })
                SecondaryButton("Protección", { section = PhoneSection.PROTECTION })
            }
            PhoneSection.RECENT -> Page("Recientes", "Llamadas atendidas por el teléfono de Hestia.") {
                SecondaryButton("Volver a Teléfono", { section = PhoneSection.HOME })
                PhoneRecentList({ context.openHestiaKeypad(it) }, manage = true, onLearnCalls = onLearnCalls)
            }
            PhoneSection.CONTACTS -> Page("Contactos", "Tu lista de confianza, compartida con Familia.") {
                SecondaryButton("Volver a Teléfono", { section = PhoneSection.HOME })
                PhoneContacts({ context.openHestiaKeypad(it) }, manage = true)
            }
            PhoneSection.PROTECTION -> PhoneProtection(calls, onPreview, onLearnCalls) { section = PhoneSection.HOME }
        }
    }
}

private enum class PhoneSection { HOME, RECENT, CONTACTS, PROTECTION }

@Composable
private fun PhoneProtection(calls: CallSetup, onPreview: () -> Unit, onLearnCalls: () -> Unit, onBack: () -> Unit) {
    val overview = rememberCallOverview(WeekRange.current())
    var tools by rememberSaveable { mutableStateOf(false) }
    var reputation by rememberSaveable { mutableStateOf(false) }
    Page("Protección", "Consulta números reportados y revisa las detecciones de Hestia.") {
        SecondaryButton("Volver a Teléfono", onBack)
        DialerSetup()
        HestiaCard {
            SectionHeader("Identificación de llamadas")
            CallRoleContent(calls)
            if (calls.state.active) CallNoticeContent(calls)
            SecondaryButton("Aprender sobre llamadas sospechosas", onLearnCalls)
        }
        SecondaryButton(if (reputation) "Ocultar reputación de demostración" else "Reputación de demostración", { reputation = !reputation })
        if (reputation) DemoCallReportsCard()
        WeeklyCallCards(overview, calls.state.active)
        HestiaCard {
            SecondaryButton(if (tools) "Ocultar herramientas DEBUG" else "Herramientas DEBUG", { tools = !tools })
            if (tools) SecondaryButton("Probar alerta de llamada", onPreview)
        }
        if (tools) DefaultPhoneCard()
    }
}

private const val GOOGLE_PHONE = "com.google.android.dialer"

@Composable
private fun DefaultPhoneCard() {
    val context = LocalContext.current
    val telecom = context.getSystemService(TelecomManager::class.java)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var current by remember(context) { mutableStateOf(telecom.defaultDialerPackage) }
    DisposableEffect(lifecycle, telecom) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) current = telecom.defaultDialerPackage
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val google = Intent(Intent.ACTION_DIAL).setPackage(GOOGLE_PHONE)
    val googleInstalled = google.resolveActivity(context.packageManager) != null
    val name = when (val packageName = current) {
        null -> "Sin aplicación predeterminada"
        GOOGLE_PHONE -> "Google Teléfono"
        context.packageName -> "Hestia"
        else -> runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }
    HestiaCard {
        SectionHeader("Modo alternativo · Google Teléfono", name)
        if (current == GOOGLE_PHONE) {
            Text("Tus contactos, historial y controles de llamada siguen en Google Teléfono.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Para probar el modo alternativo, elige Google Teléfono en Android y conserva la identificación de Hestia.", style = MaterialTheme.typography.bodyMedium)
            SecondaryButton("Cambiar aplicación de teléfono", { context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) })
        }
        if (googleInstalled) SecondaryButton("Abrir Google Teléfono", { context.startActivity(google) })
    }
}
