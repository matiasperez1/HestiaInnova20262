package cl.grupo28.hestia.domain

import java.time.Instant

internal enum class PersonalReportReason(val label: String) {
    POSSIBLE_FRAUD("Posible intento de estafa"),
    IMPERSONATION("Suplantación de identidad"),
    REQUEST_FOR_MONEY("Solicitud sospechosa de dinero"),
    REQUEST_FOR_DATA("Solicitud de datos personales"),
    INTIMIDATION("Amenazas o intimidación"),
    UNWANTED_ADS("Llamada comercial no deseada"),
    OTHER("Otro comportamiento sospechoso"),
}

internal data class PersonalCallReport(
    val id: String,
    val normalizedNumber: String,
    val originalNumber: String,
    val reason: PersonalReportReason,
    val description: String?,
    val createdAt: Instant,
    val active: Boolean,
    val modifiedAt: Instant,
)

internal data class CallSignals(val personal: PersonalReportReason?, val community: CallVerdict) {
    val shouldWarn: Boolean get() = personal != null || community.reported
}

internal const val PERSONAL_REPORT_DESCRIPTION_LIMIT = 300
private val phoneCharacters = Regex("\\+?[0-9]+")
private val e164 = Regex("\\+[1-9][0-9]{6,14}")

internal fun normalizePersonalNumber(input: String, region: String, formatE164: (String, String) -> String?): String? {
    val cleaned = input.filterNot { it.isWhitespace() || it in "().-" }
    if (!phoneCharacters.matches(cleaned)) return null
    val candidate = if (cleaned.startsWith("00")) "+${cleaned.drop(2)}" else cleaned
    if (candidate.removePrefix("+").length !in 7..15) return null
    return formatE164(candidate, region)?.takeIf(e164::matches)
}
