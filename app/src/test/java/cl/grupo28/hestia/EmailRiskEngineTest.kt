package cl.grupo28.hestia

import cl.grupo28.hestia.domain.gmail.*
import org.junit.Assert.*
import org.junit.Test

class EmailRiskEngineTest {
    private val engine = EmailRiskEngine()
    @Test fun ordinaryMessageDoesNotManufactureFraud() {
        val result = engine.analyze("Reunión familiar", "Nos vemos el sábado a almorzar.")
        assertEquals(EmailRisk.LOW, result.level); assertTrue(result.reasons.isEmpty())
    }
    @Test fun credentialsUrgencyAndShortLinkProduceExplainedHighRisk() {
        val result = engine.analyze("URGENTE", "Ingresa tu CÓDIGO de verificación inmediatamente en https://bit.ly/test")
        assertEquals(EmailRisk.HIGH, result.level); assertEquals(3, result.reasons.size)
        assertTrue(result.score in 60..100)
    }
    @Test fun preventionAdviceIsNotCredentialRequest() {
        val result = engine.analyze("Consejo", "Nunca compartas tu clave ni códigos. Verifica por un canal oficial.")
        assertEquals(0, result.score)
    }
    @Test fun httpsIsNotProofOfLegitimacy() {
        val result = engine.analyze("Tu cuenta", "Confirma tu contraseña en https://example.invalid")
        assertEquals(EmailRisk.MEDIUM, result.level)
    }
    @Test fun numericalInsecureLinkAddsIndependentSignals() {
        val result = engine.analyze("Aviso", "Visita http://192.0.2.10/login")
        assertEquals(EmailRisk.MEDIUM, result.level); assertEquals(2, result.reasons.size)
    }
    @Test fun replyDomainMismatchAloneDoesNotConfirmFraud() {
        val result = engine.analyze("Boletín", "Noticias", "Entidad <a@example.invalid>", "b@another.invalid")
        assertEquals(EmailRisk.LOW, result.level); assertEquals(1, result.reasons.size)
    }
    @Test fun duplicateSignalsDoNotInflateScore() {
        val result = engine.analyze("URGENTE urgente", "Inmediatamente hoy mismo urgente")
        assertEquals(15, result.score)
    }
    @Test fun institutionalClaimWithCredentialRequestAndHiddenLinkAddsImpersonationSignal() {
        val result=engine.analyze("Banco: verifica tu cuenta", "Ingresa tu clave en https://bit.ly/test")
        assertEquals(EmailRisk.HIGH,result.level)
        assertTrue(result.reasons.any { it.contains("institución") })
        assertFalse(engine.analyze("Banco: consejo de seguridad", "Nunca compartas tu clave").reasons.any { it.contains("institución") })
    }
    @Test fun unicodeDomainRequiresReviewWithoutClaimingFraud() {
        val result=engine.analyze("Aviso", "https://bánco.example.invalid")
        assertTrue(result.reasons.any { it.contains("caracteres") })
        assertEquals(EmailRisk.LOW,result.level)
    }

}
