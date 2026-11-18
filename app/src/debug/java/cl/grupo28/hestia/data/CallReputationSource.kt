package cl.grupo28.hestia.data

import cl.grupo28.hestia.domain.CallReputation
import cl.grupo28.hestia.domain.CallVerdict

internal val localCallReputation = CallReputation { number ->
    when (number) {
        "+12025550147", "12025550147", "2025550147" -> CallVerdict.DEBUG_REPORTED
        "+12025550148", "12025550148", "2025550148" -> CallVerdict.DEBUG_NO_MATCH
        else -> CallVerdict.NO_MATCH
    }
}
