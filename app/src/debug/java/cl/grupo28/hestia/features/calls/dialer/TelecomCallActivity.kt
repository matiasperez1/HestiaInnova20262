package cl.grupo28.hestia.features.calls.dialer

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.core.content.ContextCompat
import cl.grupo28.hestia.core.accessibility.DisplayPreferences
import cl.grupo28.hestia.core.components.*
import cl.grupo28.hestia.core.navigation.returnToPhoneRecents
import cl.grupo28.hestia.core.theme.HestiaTheme
import cl.grupo28.hestia.data.DataStoreSettingsRepository
import cl.grupo28.hestia.data.calls.DemoCallReports
import cl.grupo28.hestia.data.calls.PhoneRecents
import cl.grupo28.hestia.data.calls.RecentCall
import cl.grupo28.hestia.data.calls.demoCallNumber
import cl.grupo28.hestia.domain.HelpRequest
import cl.grupo28.hestia.domain.HelpTopic
import cl.grupo28.hestia.features.calls.HestiaIncomingCall
import cl.grupo28.hestia.features.calls.PersonalCallWarning
import cl.grupo28.hestia.features.calls.ContextualCallReport
import cl.grupo28.hestia.features.calls.recentDate
import cl.grupo28.hestia.features.calls.CallRiskCard
import cl.grupo28.hestia.features.calls.CallRiskDetails
import cl.grupo28.hestia.features.calls.CallAction
import cl.grupo28.hestia.features.calls.PhoneContacts
import cl.grupo28.hestia.features.calls.findTrustedContact
import cl.grupo28.hestia.features.family.TrustHelpDialog
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale

class TelecomCallActivity : ComponentActivity() {
    private var locked by mutableStateOf(true)
    private var observingLock = false
    private val lockEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            locked = intent.action == Intent.ACTION_SCREEN_OFF || readLocked()
        }
    }

    private fun readLocked() = getSystemService(KeyguardManager::class.java).isKeyguardLocked

    override fun onStart() {
        super.onStart()
        locked = readLocked()
        ContextCompat.registerReceiver(this, lockEvents, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        observingLock = true
    }

    override fun onStop() {
        if (observingLock) {
            unregisterReceiver(lockEvents)
            observingLock = false
        }
        locked = true
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!holdsDialerRole() || InCallSession.current.value == null) {
            finish()
            return
        }
        locked = readLocked()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        val settings = DataStoreSettingsRepository(applicationContext).settings
        val availableSettings = settings.catch { if (it !is IOException) throw it }
        val reports = DemoCallReports.from(this)
        val keyguard = getSystemService(KeyguardManager::class.java)
        setContent {
            val preferences by availableSettings.collectAsState(initial = null)
            val ui by InCallSession.current.collectAsState()
            var shownCall by remember { mutableStateOf(InCallSession.current.value?.id) }
            var postCall by remember { mutableStateOf<RecentCall?>(null) }
            var canReportPostCall by remember { mutableStateOf(false) }
            var activeReport by remember { mutableStateOf<ActiveCallReport?>(null) }
            var help by remember { mutableStateOf<HelpRequest?>(null) }
            LaunchedEffect(help != null, activeReport != null, postCall != null) {
                setShowWhenLocked(help == null && activeReport == null && postCall == null)
            }
            LaunchedEffect(ui?.id) {
                val callId = ui?.id
                if (callId != null) {
                    if (shownCall != callId) help = null
                    shownCall = callId
                    postCall = null
                    canReportPostCall = false
                    if (activeReport?.callId != callId) activeReport = null
                    return@LaunchedEffect
                }
                help = null
                val ended = InCallSession.takeCompleted(shownCall)
                // Keep the existing report dialog alive until the send result is shown.
                if (activeReport?.let { it.callId == shownCall } == true) return@LaunchedEffect
                val canOffer = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !keyguard.isKeyguardLocked
                if (!canOffer || ended == null) {
                    finish()
                    return@LaunchedEffect
                }
                postCall = ended
                val number = demoCallNumber(ended.number) ?: return@LaunchedEffect
                canReportPostCall = try {
                    withTimeoutOrNull(2_800) {
                        val contacts = settings.first().contacts
                        if (contacts.any { demoCallNumber(it.phone) == number }) return@withTimeoutOrNull false
                        val identity = reports.persona.value
                        number !in reports.sync(identity).own
                    } == true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) { false }
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || keyguard.isKeyguardLocked) finish()
            }
            HestiaTheme(preferences?.display ?: DisplayPreferences()) {
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
                                val contactName = if (locked) null else preferences?.contacts.orEmpty()
                                    .findTrustedContact(call.shareableNumber)?.name
                                val send: (CallCommand) -> Unit = { InCallSession.service?.perform(call.id, it) }
                                if (call.state == Call.STATE_RINGING) {
                                    HestiaIncomingCall(
                                        number = call.number,
                                        reported = call.reported,
                                        testLabel = "Número de prueba".takeIf { call.synthetic },
                                        onReject = { send(CallCommand.Reject) },
                                        onAnswer = { send(CallCommand.Answer) },
                                        onHelp = { if (call.reported && !keyguard.isKeyguardLocked) help = call.helpRequest() },
                                        showPrivateDetails = !locked,
                                        contactName = contactName,
                                        personalReason = call.personalReason,
                                    )
                                } else OngoingCallScreen(call, contactName, locked, send,
                                    { number -> if (!keyguard.isKeyguardLocked) activeReport = ActiveCallReport(call.id, number) },
                                    { if (call.reported && !keyguard.isKeyguardLocked) help = call.helpRequest() })
                            }
                        }
                        if (ui == null && !locked) {
                            val goToRecents = {
                                returnToPhoneRecents(this@TelecomCallActivity)
                                finish()
                            }
                            Column(Modifier.align(Alignment.Center).padding(22.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Llamada finalizada", style = MaterialTheme.typography.titleLarge)
                                if (postCall != null) SecondaryButton("Volver a Recientes", goToRecents)
                            }
                            postCall?.let { ended ->
                                if (canReportPostCall) ContextualCallReport(demoCallNumber(ended.number) ?: return@let,
                                    onClose = { finish() }, postCall = true, contextLabel = recentDate(ended.at),
                                    onAnswered = { persona ->
                                        PhoneRecents(applicationContext).markAnswered(ended.id, persona)
                                    }, afterCallActions = {
                                        SecondaryButton("Volver a Recientes", goToRecents)
                                    })
                            }
                        }
                        activeReport?.takeUnless { locked }?.let { report ->
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
                        if (!locked && ui?.reported == true && ui?.state != Call.STATE_DISCONNECTING) {
                            help?.let { request ->
                                TrustHelpDialog(request, onClose = { help = null }, callInProgress = true,
                                    canOpenNow = { false })
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        locked = readLocked()
        if (!holdsDialerRole()) finish()
    }

    companion object {
        internal fun intent(context: Context) = Intent(context, TelecomCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}

private data class ActiveCallReport(val callId: String, val number: String)

private fun CallUi.helpRequest() = HelpRequest(
    HelpTopic.CALL_WARNING, synthetic, shareableNumber,
)

private enum class CallPanel { Keypad, Contacts, Help, Info }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OngoingCallScreen(call: CallUi, contactName: String?, locked: Boolean, send: (CallCommand) -> Unit, onReport: (String) -> Unit, onHelp: () -> Unit) {
    var panel by rememberSaveable { mutableStateOf<CallPanel?>(null) }
    LaunchedEffect(locked) { if (locked) panel = null }
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
    val reportAction = call.reportNumber?.takeIf { connected && !locked }?.let { number ->
        { onReport(number) }
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val screenHeight = maxHeight
        val columns = if (maxWidth >= (260 * LocalDensity.current.fontScale).dp) 3 else 2
        val controlWidth = (minOf(maxWidth, 464.dp) - 44.dp - 12.dp * (columns - 1)) / columns
        Column(
            Modifier.widthIn(max = 464.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = screenHeight).padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (locked) "Número oculto" else contactName ?: call.number, style = type.displaySmall, textAlign = TextAlign.Center)
                if (!locked && contactName != null) Text(call.number, style = type.bodyLarge, textAlign = TextAlign.Center)
                StatusPill(state, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                call.connectedAtMillis?.let { CallDuration(it) }
            }
            if (call.reported) CallRiskCard(if (locked) null else { { panel = CallPanel.Info } }, Modifier.padding(bottom = 12.dp), inclusive = true,
                onReport = reportAction)
            call.personalReason?.let { PersonalCallWarning(it, showReason = !locked) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = columns,
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhoneControl(
                        if (call.muted) "Micrófono apagado" else "Silenciar", if (call.muted) PhoneGlyph.Muted else PhoneGlyph.Mic,
                        Modifier.width(controlWidth).semantics { stateDescription = if (call.muted) "Micrófono silenciado" else "Micrófono activo" },
                        enabled = connected, selected = call.muted,
                    ) { send(CallCommand.Mute) }
                    PhoneControl("Teclado", PhoneGlyph.Digits, Modifier.width(controlWidth), enabled = connected) { panel = CallPanel.Keypad }
                    PhoneControl(
                        "Altavoz", PhoneGlyph.Speaker,
                        Modifier.width(controlWidth).semantics { stateDescription = if (call.speaker) "Activo" else "Inactivo" },
                        enabled = connected && call.canSwitchSpeaker, selected = call.speaker,
                    ) { send(CallCommand.Speaker) }
                    PhoneControl("Contactos", PhoneGlyph.Contacts, Modifier.width(controlWidth), enabled = !locked) { panel = CallPanel.Contacts }
                    if (call.reported) PhoneControl("Ayuda", PhoneGlyph.Help, Modifier.width(controlWidth)) { panel = CallPanel.Help }
                    PhoneControl("Reportar", PhoneGlyph.Report, Modifier.width(controlWidth), enabled = reportAction != null) { reportAction?.invoke() }
                }
                CallAction("Colgar", colors.error, colors.onError, rotation = 135f, diameter = 96.dp) {
                    send(CallCommand.HangUp)
                }
            }
        }
    }

    when (panel.takeUnless { locked && it != CallPanel.Keypad && it != CallPanel.Help }) {
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
            CallRiskDetails(call.number, call.synthetic, { panel = null },
                if (locked) null else { { panel = null; onHelp() } })
        }
        CallPanel.Contacts -> AlertDialog(
            onDismissRequest = { panel = null },
            title = { Text("Contactos de confianza") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { PhoneContacts(onSelect = null) } },
            confirmButton = { TextButton({ panel = null }) { Text("Cerrar") } },
        )
        CallPanel.Help -> AlertDialog(
            onDismissRequest = { panel = null },
            title = { Text("¿Necesitas ayuda con esta llamada?") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No compartas claves ni códigos. Puedes colgar si tienes dudas y verificar por un canal oficial.")
                if (!locked && call.reported) SecondaryButton("Pedir ayuda a una persona de confianza", { panel = null; onHelp() })
            } },
            confirmButton = { TextButton({ panel = null }, Modifier.heightIn(min = 48.dp)) { Text("Volver a la llamada") } },
        )
        null -> Unit
    }
}


@Composable
private fun CallDuration(connectedAtMillis: Long) {
    val elapsed by produceState(0L, connectedAtMillis) {
        while (true) {
            value = ((System.currentTimeMillis() - connectedAtMillis) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    val hours = elapsed / 3600
    val minutes = elapsed / 60 % 60
    val seconds = elapsed % 60
    val time = if (hours == 0L) String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        else String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    Text("Duración $time", style = MaterialTheme.typography.bodyMedium)
}
