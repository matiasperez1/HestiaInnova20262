package cl.grupo28.hestia.features.calls.dialer

import android.app.role.RoleManager
import android.content.Context
import cl.grupo28.hestia.data.calls.RecentCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun Context.holdsDialerRole(): Boolean =
    getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)

internal data class CallUi(
    val id: String,
    val number: String,
    val state: Int,
    val reported: Boolean,
    val synthetic: Boolean,
    val muted: Boolean,
    val speaker: Boolean,
    val canSwitchSpeaker: Boolean,
    val reportNumber: String?,
)

internal enum class CallCommand { Answer, Reject, HangUp, Mute, Speaker }

internal object InCallSession {
    private val mutable = MutableStateFlow<CallUi?>(null)
    val current = mutable.asStateFlow()
    private var ended: RecentCall? = null
    var service: HestiaInCallService? = null
        private set

    fun publish(source: HestiaInCallService, ui: CallUi?) {
        service = source
        if (ui != null && ended?.id != ui.id) ended = null
        mutable.value = ui
    }

    fun complete(call: RecentCall) {
        if (mutable.value?.id == call.id) ended = call
    }

    fun takeCompleted(id: String?): RecentCall? = ended?.takeIf { it.id == id }?.also { ended = null }

    fun clear(source: HestiaInCallService) {
        if (service === source) {
            mutable.value = null
            service = null
        }
    }
}
