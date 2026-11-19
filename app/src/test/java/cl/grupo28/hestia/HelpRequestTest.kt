package cl.grupo28.hestia

import cl.grupo28.hestia.domain.HelpRequest
import cl.grupo28.hestia.domain.HelpTopic
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class HelpRequestTest {
    @Test fun callNumberRequiresExplicitChoiceAndTheWarningUsesTheSelectedCall() {
        val request = HelpRequest(HelpTopic.CALL_WARNING, true, "202 555 0147", Instant.parse("2026-11-18T17:00:00Z"))
        val normal = request.message("Texto editado", false, ZoneOffset.UTC)
        assertTrue(normal.startsWith("DEMO LOCAL · Aviso de prueba\nTexto editado"))
        assertTrue(normal.contains("Fecha aproximada:"))
        assertFalse(normal.contains("2025550147"))
        assertTrue(request.message("Texto editado", true).endsWith("Número de la llamada: 2025550147"))
        assertFalse(request.copy(callNumber = "Número oculto").message("Texto editado", true).contains("Número de la llamada"))
    }

    @Test fun highRiskEmailHasOnlyAnEditableGenericMessageEvenIfSuppliedWithACallNumber() {
        val request = HelpRequest(HelpTopic.EMAIL_WARNING, true, "2025550147", Instant.now())
        val message = request.message(request.suggestedMessage(), true)
        assertTrue(message.contains("señales de riesgo en un correo"))
        assertFalse(message.contains("2025550147"))
        assertFalse(message.contains("Fecha aproximada"))
        assertFalse(message.contains("https://"))
    }
}
