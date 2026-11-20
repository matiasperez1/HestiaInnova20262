package cl.grupo28.hestia.features.calls.dialer

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.OutcomeReceiver
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.telephony.PhoneNumberUtils
import cl.grupo28.hestia.data.localCallReputation
import cl.grupo28.hestia.data.PersonalReports
import cl.grupo28.hestia.data.calls.PhoneRecents
import cl.grupo28.hestia.data.calls.RecentCall
import cl.grupo28.hestia.data.calls.RecentKind
import cl.grupo28.hestia.data.calls.demoCallNumber
import cl.grupo28.hestia.domain.normalizeCallNumber
import cl.grupo28.hestia.domain.PersonalReportReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.util.UUID

@Suppress("DEPRECATION") // Android 10–13 use CallAudioState; 14+ use CallEndpoint.
class HestiaInCallService : InCallService() {
    private data class TrackedCall(
        val id: String,
        val callback: Call.Callback,
        var connected: Boolean,
        var rejected: Boolean = false,
        var reported: Boolean = false,
        var recorded: Boolean = false,
    )
    private val managed = linkedMapOf<Call, TrackedCall>()
    private var shownId: String? = null
    private var shownState: Int? = null
    private var shownPersonal: PersonalReportReason? = null
    private val personalReports by lazy { PersonalReports.from(this) }
    private val observations = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var audio: CallAudioState? = null
    private var endpoint: CallEndpoint? = null
    private var endpoints = emptyList<CallEndpoint>()
    private var muted = false
    private val toneHandler = Handler(Looper.getMainLooper())
    private var toneCall: Call? = null
    private val stopTone = Runnable {
        toneCall?.stopDtmfTone()
        toneCall = null
    }

    override fun onCreate() {
        super.onCreate()
        observations.launch { personalReports.state.drop(1).collect { update() } }
    }

    override fun onCallAdded(call: Call) {
        if (call in managed) return
        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_ACTIVE) managed[call]?.connected = true
                update()
            }
            override fun onDetailsChanged(call: Call, details: Call.Details) = update()
        }
        managed[call] = TrackedCall(UUID.randomUUID().toString(), callback, state(call) == Call.STATE_ACTIVE)
        call.registerCallback(callback)
        update()
        observations.launch { personalReports.load() }
    }

    override fun onCallRemoved(call: Call) {
        if (toneCall === call) finishTone()
        managed.remove(call)?.let {
            call.unregisterCallback(it.callback)
            recordRecent(call.details, it)
        }
        update()
    }

    override fun onBringToForeground(showDialpad: Boolean) = openCallUi()

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        if (Build.VERSION.SDK_INT < 34) {
            audio = audioState
            muted = audioState.isMuted
            update()
        }
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        endpoint = callEndpoint
        update()
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        endpoints = availableEndpoints.toList()
        update()
    }

    override fun onMuteStateChanged(isMuted: Boolean) {
        muted = isMuted
        update()
    }

    private fun state(call: Call): Int =
        if (Build.VERSION.SDK_INT >= 31) call.details.state else call.state

    private fun recordRecent(details: Call.Details, tracked: TrackedCall) {
        if (tracked.recorded) return
        tracked.recorded = true
        if (!holdsDialerRole() || details.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE) ||
            details.handlePresentation != android.telecom.TelecomManager.PRESENTATION_ALLOWED) return
        val original = details.handle?.takeIf { it.scheme == "tel" }?.schemeSpecificPart ?: return
        val number = normalizeCallNumber(original) ?: return
        val kind = when (details.callDirection) {
            Call.Details.DIRECTION_OUTGOING -> RecentKind.OUTGOING
            Call.Details.DIRECTION_INCOMING -> when {
                tracked.connected || details.connectTimeMillis > 0 -> RecentKind.RECEIVED
                tracked.rejected || details.disconnectCause.code == DisconnectCause.REJECTED -> RecentKind.DECLINED
                else -> RecentKind.MISSED
            }
            else -> return
        }
        val entry = RecentCall(tracked.id, number, Instant.now(), kind, demoCallNumber(number) != null,
            warned = tracked.reported, originalNumber = original)
        if (kind == RecentKind.RECEIVED && entry.warned) InCallSession.complete(entry)
        val repository = PhoneRecents(applicationContext)
        recentWrites.launch {
            try { repository.record(entry) } catch (_: IOException) { /* Calls remain available if storage fails. */ }
        }
    }

    private fun update() {
        managed.forEach { (call, tracked) ->
            // Incoming calls without a final cause can still be classified in onCallRemoved.
            if (state(call) == Call.STATE_DISCONNECTED && (tracked.connected || tracked.rejected ||
                    call.details.disconnectCause.code != DisconnectCause.UNKNOWN)) {
                recordRecent(call.details, tracked)
            }
        }
        val live = managed.keys.filter { state(it) != Call.STATE_DISCONNECTED }
        val selected = if (!holdsDialerRole()) null else
            live.firstOrNull { state(it) == Call.STATE_RINGING }
                ?: live.firstOrNull { state(it) == Call.STATE_ACTIVE }
                ?: live.firstOrNull()

        if (selected == null) {
            InCallSession.clear(this)
            shownId = null
            shownState = null
            shownPersonal = null
            getSystemService(NotificationManager::class.java).cancel(CallNotices.ID)
            return
        }

        val details = selected.details
        val visibleHandle = details.handle?.takeIf {
            details.handlePresentation == android.telecom.TelecomManager.PRESENTATION_ALLOWED
        }
        val handle = visibleHandle?.takeIf { it.scheme == "tel" }
        val number = handle?.schemeSpecificPart
        val normalized = number?.let(::normalizeCallNumber)
        val verdict = normalized?.let { localCallReputation(this).lookup(it) }
        val personalReason = number?.takeIf {
            details.callDirection == Call.Details.DIRECTION_INCOMING && verdict?.synthetic != true &&
                !details.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE)
        }?.let { personalReports.state.value.active(personalReports.normalize(it))?.reason }
        val tracked = managed.getValue(selected)
        if (verdict?.reported == true) tracked.reported = true
        val display = if (verdict?.synthetic == true) {
            val digits = normalized.takeLast(10)
            "${digits.take(3)} ${digits.substring(3, 6)} ${digits.takeLast(4)}"
        } else {
            normalized?.let { PhoneNumberUtils.formatNumber(it, java.util.Locale.getDefault().country.ifEmpty { "CL" }) ?: it }
                ?: visibleHandle?.takeIf { it.scheme == "sip" }?.schemeSpecificPart
                ?: "Número no disponible"
        }
        val speaker = if (Build.VERSION.SDK_INT >= 34) endpoint?.endpointType == CallEndpoint.TYPE_SPEAKER
            else audio?.route == CallAudioState.ROUTE_SPEAKER
        val canSwitchSpeaker = if (Build.VERSION.SDK_INT >= 34) endpoints.any { it.endpointType == CallEndpoint.TYPE_SPEAKER }
            && endpoints.any { it.endpointType != CallEndpoint.TYPE_SPEAKER }
            else audio?.supportedRouteMask?.and(CallAudioState.ROUTE_SPEAKER) != 0 && audio != null
        val shareableNumber = normalized?.takeIf {
            selected.details.handlePresentation == android.telecom.TelecomManager.PRESENTATION_ALLOWED &&
                !selected.details.hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE)
        }
        val ui = CallUi(tracked.id, display, state(selected), tracked.reported,
            verdict?.synthetic == true, muted, speaker, canSwitchSpeaker, shareableNumber?.let(::demoCallNumber),
            shareableNumber, details.connectTimeMillis.takeIf { it > 0 }, personalReason)
        InCallSession.publish(this, ui)

        val switched = shownId != ui.id
        val stateChanged = shownState != ui.state
        if (switched || stateChanged || shownPersonal != ui.personalReason) {
            shownId = ui.id
            shownState = ui.state
            shownPersonal = ui.personalReason
            CallNotices.show(this, ui, replace = switched || stateChanged)
            if (switched) openCallUi()
        }
    }

    private fun openCallUi() {
        if (!holdsDialerRole() || InCallSession.current.value == null) return
        // Telecom's binding can grant a background activity start. The call notification
        // is also posted so Android can present its fallback on devices that deny it.
        runCatching { startActivity(TelecomCallActivity.intent(this)) }
    }

    internal fun perform(id: String, command: CallCommand) {
        if (!holdsDialerRole()) return
        val call = managed.entries.firstOrNull { it.value.id == id }?.key ?: return
        val state = state(call)
        when (command) {
            CallCommand.Answer -> if (state == Call.STATE_RINGING) call.answer(VideoProfile.STATE_AUDIO_ONLY)
            CallCommand.Reject -> if (state == Call.STATE_RINGING) {
                managed.getValue(call).rejected = true
                call.reject(false, null)
            }
            CallCommand.HangUp -> if (state != Call.STATE_RINGING && state != Call.STATE_DISCONNECTED) call.disconnect()
            CallCommand.Mute -> if (state == Call.STATE_ACTIVE) setMuted(!muted)
            CallCommand.Speaker -> if (state == Call.STATE_ACTIVE) switchSpeaker()
        }
    }

    internal fun sendDigit(id: String, digit: Char) {
        if (!holdsDialerRole() || digit !in "0123456789*#") return
        val call = managed.entries.firstOrNull { it.value.id == id }?.key ?: return
        if (state(call) != Call.STATE_ACTIVE) return
        finishTone()
        call.playDtmfTone(digit)
        toneCall = call
        toneHandler.postDelayed(stopTone, 150L)
    }

    private fun finishTone() {
        toneHandler.removeCallbacks(stopTone)
        stopTone.run()
    }

    private fun switchSpeaker() {
        if (Build.VERSION.SDK_INT >= 34) {
            val next = if (endpoint?.endpointType == CallEndpoint.TYPE_SPEAKER) {
                endpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_EARPIECE }
                    ?: endpoints.firstOrNull { it.endpointType != CallEndpoint.TYPE_SPEAKER }
            } else endpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_SPEAKER }
            if (next != null) requestCallEndpointChange(next, mainExecutor, object : OutcomeReceiver<Void?, CallEndpointException> {
                override fun onResult(result: Void?) = Unit
                override fun onError(error: CallEndpointException) {
                    android.widget.Toast.makeText(this@HestiaInCallService, "No se pudo cambiar la salida de audio.", android.widget.Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            val current = audio ?: return
            val next = if (current.route == CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_WIRED_OR_EARPIECE
                else CallAudioState.ROUTE_SPEAKER
            setAudioRoute(next)
        }
    }

    override fun onDestroy() {
        observations.cancel()
        finishTone()
        managed.forEach { (call, tracked) -> call.unregisterCallback(tracked.callback) }
        managed.clear()
        InCallSession.clear(this)
        getSystemService(NotificationManager::class.java).cancel(CallNotices.ID)
        super.onDestroy()
    }

    private companion object {
        val recentWrites = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
