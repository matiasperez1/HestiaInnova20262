package cl.grupo28.hestia.features.calls.dialer

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import cl.grupo28.hestia.core.accessibility.DisplayPreferences
import cl.grupo28.hestia.core.components.*
import cl.grupo28.hestia.core.theme.HestiaTheme
import cl.grupo28.hestia.data.DataStoreSettingsRepository
import cl.grupo28.hestia.data.calls.DemoCallReports
import cl.grupo28.hestia.data.calls.PhoneRecents
import cl.grupo28.hestia.data.calls.RecentCall
import cl.grupo28.hestia.data.calls.demoCallNumber
import cl.grupo28.hestia.features.calls.HestiaIncomingCall
import cl.grupo28.hestia.features.calls.ContextualCallReport
import cl.grupo28.hestia.features.calls.recentDate
import cl.grupo28.hestia.features.calls.CallRiskCard
import cl.grupo28.hestia.features.calls.CallRiskDetails
import cl.grupo28.hestia.features.calls.CallAction
import cl.grupo28.hestia.features.calls.PhoneContacts
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

class TelecomCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!holdsDialerRole() || InCallSession.current.value == null) {
            finish()
            return
        }
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        val settings = DataStoreSettingsRepository(applicationContext).settings
        val display = settings.map { it.display }
            .catch { if (it !is IOException) throw it }
        val reports = DemoCallReports.from(this)
        val keyguard = getSystemService(KeyguardManager::class.java)
        setContent {
            val prefs by display.collectAsState(initial = DisplayPreferences())
            val ui by InCallSession.current.collectAsState()
            var shownCall by remember { mutableStateOf(InCallSession.current.value?.id) }
            var postCall by remember { mutableStateOf<RecentCall?>(null) }
            var activeReport by remember { mutableStateOf<ActiveCallReport?>(null) }
            LaunchedEffect(ui?.id) {
                val callId = ui?.id
                if (callId != null) {
                    shownCall = callId
                    postCall = null
                    if (activeReport?.callId != callId) activeReport = null
                    return@LaunchedEffect
                }
                val ended = InCallSession.takeCompleted(shownCall)
                // Keep the existing report dialog alive until the send result is shown.
                if (activeReport?.let { it.callId == shownCall } == true) return@LaunchedEffect
                val canOffer = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !keyguard.isKeyguardLocked
                if (ended == null || !canOffer) {
                    finish()
                    return@LaunchedEffect
                }
                val number = demoCallNumber(ended.number) ?: run { finish(); return@LaunchedEffect }
                val shouldOffer = try {
                    withTimeoutOrNull(2_800) {
                        val contacts = settings.first().contacts
                        if (contacts.any { demoCallNumber(it.phone) == number }) return@withTimeoutOrNull false
                        val identity = reports.persona.value
                        number !in reports.sync(identity).own
                    } == true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) { false }
                if (shouldOffer && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !keyguard.isKeyguardLocked) {
                    postCall = ended
                } else finish()
            }
            HestiaTheme(prefs) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Row(
                                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (ui?.state != Call.STATE_RINGING) {
                                    TextButton({ finish() }, Modifier.heightIn(min = 48.dp)) {
                                        HestiaIcon(HestiaSymbol.BACK)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Volver")
                                    }
                                } else {
                                    Spacer(Modifier.height(48.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    HestiaMark(Modifier.size(36.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Hestia", style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                        ui?.let { call ->
                            key(call.id) {
                                val send: (CallCommand) -> Unit = { InCallSession.service?.perform(call.id, it) }
                                if (call.state == Call.STATE_RINGING) {
                                    HestiaIncomingCall(
                                        number = call.number,
                                        reported = call.reported,
                                        testLabel = "Número de prueba".takeIf { call.synthetic },
                                        onReject = { send(CallCommand.Reject) },
                                        onAnswer = { send(CallCommand.Answer) },
                                    )
                                } else OngoingCallScreen(call, send) { number ->
                                    activeReport = ActiveCallReport(call.id, number)
                                }
                            }
                        }
                        if (ui == null) {
                            Text("Llamada finalizada", Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleLarge)
                            postCall?.let { ended ->
                                ContextualCallReport(demoCallNumber(ended.number) ?: return@let,
                                    onClose = { finish() }, postCall = true, contextLabel = recentDate(ended.at),
                                    onAnswered = { persona ->
                                        PhoneRecents(applicationContext).markAnswered(ended.id, persona)
                                    })
                            }
                        }
                        activeReport?.let { report ->
                            key(report.callId) {
                                val hangUp: (() -> Unit)? = if (ui?.let { it.id == report.callId && it.state == Call.STATE_ACTIVE } == true) {
                                    { InCallSession.service?.perform(report.callId, CallCommand.HangUp) }
                                } else null
                                ContextualCallReport(report.number, onClose = {
                                    activeReport = null
                                    if (InCallSession.current.value == null) finish()
                                }, onSubmit = hangUp)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!holdsDialerRole()) finish()
    }

    companion object {
        internal fun intent(context: Context) = Intent(context, TelecomCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

private data class ActiveCallReport(val callId: String, val number: String)

private enum class CallPanel { Keypad, Contacts, Help, Info }

@Composable
private fun OngoingCallScreen(call: CallUi, send: (CallCommand) -> Unit, onReport: (String) -> Unit) {
    var panel by rememberSaveable { mutableStateOf<CallPanel?>(null) }
    val keyguard = LocalContext.current.getSystemService(KeyguardManager::class.java)
    val connected = call.state == Call.STATE_ACTIVE
    val state = when (call.state) {
        Call.STATE_ACTIVE -> "Conectada"
        Call.STATE_HOLDING -> "En espera"
        Call.STATE_SELECT_PHONE_ACCOUNT -> "Seleccionando línea"
        Call.STATE_DISCONNECTING -> "Finalizando…"
        else -> "Conectando…"
    }
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val reportAction = call.reportNumber?.takeIf { connected && !keyguard.isDeviceLocked }?.let { number ->
        { onReport(number) }
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val screenHeight = maxHeight
        Column(
            Modifier.widthIn(max = 464.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = screenHeight).padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(call.number, style = type.displaySmall, textAlign = TextAlign.Center)
                StatusPill(state)
            }
            if (call.reported) CallRiskCard({ panel = CallPanel.Info }, Modifier.padding(bottom = 12.dp), inclusive = true,
                onReport = reportAction)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhoneControl(
                        if (call.muted) "Micrófono apagado" else "Silenciar", if (call.muted) PhoneGlyph.Muted else PhoneGlyph.Mic,
                        Modifier.weight(1f).semantics { stateDescription = if (call.muted) "Micrófono silenciado" else "Micrófono activo" },
                        enabled = connected, selected = call.muted,
                    ) { send(CallCommand.Mute) }
                    PhoneControl("Teclado", PhoneGlyph.Digits, Modifier.weight(1f), enabled = connected) { panel = CallPanel.Keypad }
                    PhoneControl(
                        "Altavoz", PhoneGlyph.Speaker,
                        Modifier.weight(1f).semantics { stateDescription = if (call.speaker) "Activo" else "Inactivo" },
                        enabled = connected && call.canSwitchSpeaker, selected = call.speaker,
                    ) { send(CallCommand.Speaker) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhoneControl("Contactos", PhoneGlyph.Contacts, Modifier.weight(1f)) { panel = CallPanel.Contacts }
                    PhoneControl("Ayuda", PhoneGlyph.Help, Modifier.weight(1f)) { panel = CallPanel.Help }
                    PhoneControl("Reportar", PhoneGlyph.Report, Modifier.weight(1f), enabled = reportAction != null) { reportAction?.invoke() }
                }
                CallAction("Colgar", colors.error, colors.onError, rotation = 135f, diameter = 96.dp) {
                    send(CallCommand.HangUp)
                }
            }
        }
    }

    when (panel) {
        CallPanel.Keypad -> if (connected) AlertDialog(
            onDismissRequest = { panel = null },
            title = { Text("Teclado de llamada") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PhoneKeypad(onKey = { InCallSession.service?.sendDigit(call.id, it) })
                }
            },
            confirmButton = { TextButton({ panel = null }) { Text("Cerrar") } },
        )
        CallPanel.Info -> if (call.reported) {
            CallRiskDetails(call.number, call.synthetic, { panel = null })
        }
        CallPanel.Contacts -> if (keyguard.isDeviceLocked) {
            CallPanelMessage("Contactos", "Desbloquea el teléfono para ver tu lista de confianza.", { panel = null })
        } else AlertDialog(
            onDismissRequest = { panel = null },
            title = { Text("Contactos de confianza") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { PhoneContacts(onSelect = null) } },
            confirmButton = { TextButton({ panel = null }) { Text("Cerrar") } },
        )
        CallPanel.Help -> CallPanelMessage(
            "Ayuda durante la llamada",
            "Usa Silenciar para apagar tu micrófono. Si te piden claves o códigos, cuelga y contacta por un canal oficial.",
            { panel = null },
        )
        null -> Unit
    }
}

@Composable
private fun CallPanelMessage(title: String, message: String, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text(title) },
        text = { Text(message, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClose) { Text("Entendido") } })
}
