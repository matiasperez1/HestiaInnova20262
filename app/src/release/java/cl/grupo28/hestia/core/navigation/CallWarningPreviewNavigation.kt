package cl.grupo28.hestia.core.navigation

import androidx.navigation.NavGraphBuilder
import cl.grupo28.hestia.features.calls.CallSetup

internal val callWarningPreviewRoute: String? = null
internal val phoneRoute: String? = null

internal fun NavGraphBuilder.addPhoneDestination(calls: CallSetup, onPreview: () -> Unit) = Unit

internal fun NavGraphBuilder.addCallWarningPreview(onClose: () -> Unit) = Unit
