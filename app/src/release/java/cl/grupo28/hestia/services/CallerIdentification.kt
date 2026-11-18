package cl.grupo28.hestia.services

import android.content.Context

@Suppress("UNUSED_PARAMETER")
internal fun Context.showCallerIdentification(number: String, synthetic: Boolean, fallback: () -> Unit) = Unit

internal fun clearCallerIdentification() = Unit
