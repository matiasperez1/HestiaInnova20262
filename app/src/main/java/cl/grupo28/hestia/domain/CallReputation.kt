package cl.grupo28.hestia.domain

enum class CallClassification { SUSPICIOUS, PENDING_CORROBORATION, NO_REPORTS }

enum class CallVerdict(val classification: CallClassification, val synthetic: Boolean) {
    NO_MATCH(CallClassification.NO_REPORTS, false),
    REPORTED(CallClassification.SUSPICIOUS, false),
    DEBUG_NO_MATCH(CallClassification.NO_REPORTS, true),
    DEBUG_PENDING(CallClassification.PENDING_CORROBORATION, true),
    DEBUG_REPORTED(CallClassification.SUSPICIOUS, true);

    val reported: Boolean get() = classification == CallClassification.SUSPICIOUS
}

/** Implementations only read local data. Synchronization runs independently. */
fun interface CallReputation {
    fun lookup(number: String): CallVerdict
}

fun normalizeCallNumber(number: String): String? {
    val input = number.trim()
    if (!input.matches(Regex("\\+?[0-9(). -]+"))) return null
    val digits = input.filter { it in '0'..'9' }
    if (digits.length !in 7..15) return null
    return when {
        input.startsWith("+") -> "+$digits"
        digits.startsWith("00") && digits.length >= 9 -> "+${digits.drop(2)}"
        digits.length == 9 && digits.startsWith("9") -> "+56$digits"
        else -> digits
    }
}
