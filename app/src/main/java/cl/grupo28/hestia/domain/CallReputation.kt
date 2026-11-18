package cl.grupo28.hestia.domain

enum class CallVerdict(val reported: Boolean, val synthetic: Boolean) {
    NO_MATCH(false, false), REPORTED(true, false),
    DEBUG_NO_MATCH(false, true), DEBUG_REPORTED(true, true),
}

/** Implementations only read local data. A future sync can populate that cache independently. */
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
