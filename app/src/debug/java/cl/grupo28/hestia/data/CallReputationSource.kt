package cl.grupo28.hestia.data

import android.content.Context
import cl.grupo28.hestia.data.calls.DemoReputationCache
import cl.grupo28.hestia.data.calls.DemoReputationSnapshot
import cl.grupo28.hestia.data.calls.demoCallNumber
import cl.grupo28.hestia.domain.CallReputation
import cl.grupo28.hestia.domain.CallVerdict

internal fun localCallReputation(context: Context): CallReputation =
    demoCallReputation(DemoReputationCache.from(context)::read)

internal fun demoCallReputation(snapshot: () -> DemoReputationSnapshot? = { null }) = CallReputation { number ->
    when (val canonical = demoCallNumber(number)) {
        null -> CallVerdict.NO_MATCH
        "2025550147" -> CallVerdict.DEBUG_REPORTED
        "2025550148" -> CallVerdict.DEBUG_NO_MATCH
        else -> snapshot()?.verdict(canonical) ?: CallVerdict.DEBUG_NO_MATCH
    }
}
