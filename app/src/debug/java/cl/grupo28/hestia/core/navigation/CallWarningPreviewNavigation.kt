package cl.grupo28.hestia.core.navigation

import android.content.Context
import android.content.Intent
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cl.grupo28.hestia.MainActivity
import cl.grupo28.hestia.features.calls.CallWarningPreviewScreen
import cl.grupo28.hestia.features.calls.CallSetup
import cl.grupo28.hestia.features.calls.PhoneScreen

private const val Route = "call-warning-preview"
internal val callWarningPreviewRoute: String? = Route
private const val PhoneRoute = "phone"
private const val RecentsRoute = "phone/recents"
internal val phoneRoute: String? = PhoneRoute
internal val phoneRecentsRoute: String? = RecentsRoute

internal fun returnToPhoneRecents(context: Context) {
    context.startActivity(Intent(context, MainActivity::class.java)
        .setAction(OPEN_PHONE_RECENTS_ACTION)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
}

internal fun NavGraphBuilder.addPhoneDestination(calls: CallSetup, onPreview: () -> Unit, onLearnCalls: () -> Unit) {
    composable(PhoneRoute) { PhoneScreen(calls, onPreview, onLearnCalls) }
    composable(RecentsRoute) { PhoneScreen(calls, onPreview, onLearnCalls, startsInRecents = true) }
}

internal fun NavGraphBuilder.addCallWarningPreview(onClose: () -> Unit) {
    composable(Route) {
        CallWarningPreviewScreen(onClose)
    }
}
