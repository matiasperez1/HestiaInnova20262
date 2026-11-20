package cl.grupo28.hestia.services

import android.content.Context
import cl.grupo28.hestia.domain.CallSignals

@Suppress("UNUSED_PARAMETER")
internal fun Context.showCallerIdentification(number: String, signals: CallSignals, fallback: () -> Unit) = Unit

internal fun clearCallerIdentification() = Unit
