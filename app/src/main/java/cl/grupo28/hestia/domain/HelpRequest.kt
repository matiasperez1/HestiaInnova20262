package cl.grupo28.hestia.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class HelpTopic { GENERAL, CALL, CALL_WARNING, EMAIL_WARNING }

data class HelpRequest(
    val topic: HelpTopic = HelpTopic.GENERAL,
    val demonstration: Boolean = false,
    val callNumber: String? = null,
    val callTime: Instant? = null,
) {
    val demonstrationLabel: String? get() = "DEMO LOCAL · Aviso de prueba".takeIf { demonstration }
    val shareableNumber: String? get() = if (topic == HelpTopic.CALL || topic == HelpTopic.CALL_WARNING) {
        callNumber?.let(::normalizeCallNumber)
    } else null

    fun suggestedMessage(): String = when (topic) {
        HelpTopic.GENERAL -> "Hola, necesito que me ayudes. Recibí algo que me pareció sospechoso y quisiera revisarlo contigo antes de hacer cualquier cosa."
        HelpTopic.CALL -> "Hola, tengo dudas sobre una llamada y me gustaría revisarla contigo. Por ahora no compartiré claves ni códigos."
        HelpTopic.CALL_WARNING -> "Hola, Hestia me advirtió de una llamada posiblemente sospechosa. ¿Me ayudas a revisarla? Por ahora no compartiré claves ni códigos."
        HelpTopic.EMAIL_WARNING -> "Hola, Hestia detectó señales de riesgo en un correo que recibí. ¿Me ayudas a revisarlo? Por ahora no abriré enlaces ni compartiré claves."
    }

    fun dateLabel(zone: ZoneId = ZoneId.systemDefault()): String? =
        callTime?.takeIf { topic == HelpTopic.CALL || topic == HelpTopic.CALL_WARNING }?.let {
            "Fecha aproximada: ${DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(zone).format(it)}"
        }

    fun message(text: String, includeNumber: Boolean, zone: ZoneId = ZoneId.systemDefault()): String = listOfNotNull(
        demonstrationLabel, text.trim(), dateLabel(zone),
        shareableNumber?.takeIf { includeNumber }?.let { "Número de la llamada: $it" },
    ).joinToString("\n")
}
