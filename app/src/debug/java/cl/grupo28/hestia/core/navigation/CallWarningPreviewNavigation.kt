package cl.grupo28.hestia.core.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import cl.grupo28.hestia.features.calls.CallWarningPreviewScreen
import cl.grupo28.hestia.features.calls.CallSetup
import cl.grupo28.hestia.features.calls.PhoneScreen

private const val Route = "call-warning-preview"
internal val callWarningPreviewRoute: String? = Route
private const val PhoneRoute = "phone"
internal val phoneRoute: String? = PhoneRoute

internal fun NavGraphBuilder.addPhoneDestination(calls: CallSetup, onPreview: () -> Unit) {
    composable(PhoneRoute) { PhoneScreen(calls, onPreview) }
}

internal fun NavGraphBuilder.addCallWarningPreview(onClose: () -> Unit) {
    composable(Route) {
        CallWarningPreviewScreen(onClose)
    }
}
