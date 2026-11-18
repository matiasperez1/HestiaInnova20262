package cl.grupo28.hestia.data

import android.content.Context
import cl.grupo28.hestia.domain.CallReputation
import cl.grupo28.hestia.domain.CallVerdict

private val emptyReputation = CallReputation { CallVerdict.NO_MATCH }

internal fun localCallReputation(@Suppress("UNUSED_PARAMETER") context: Context): CallReputation = emptyReputation
