package cl.grupo28.hestia.domain.gmail

import java.net.URI
import java.text.Normalizer
import java.util.Locale

/** Explainable heuristic signals, never a calibrated probability or proof of fraud. */
class EmailRiskEngine {
    fun analyze(subject: String, body: String, sender: String = "", replyTo: String = ""): EmailAnalysis {
        val text = Normalizer.normalize("$subject $body", Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT)
        val reasons = mutableListOf<String>()
        var score = 0
        fun add(points: Int, reason: String) { score += points; reasons += reason }
        val sentences = text.split(Regex("[.!?;\n]"))
        val credentials = sentences.any { sentence ->
            !Regex("(?:no|nunca|jamas)\\s+(?:\\w+\\s+){0,3}(?:compart|entreg|envi|ingres)").containsMatchIn(sentence) &&
                Regex("(?:ingresa|envia|comparte|entrega|confirma|valida|actualiza).{0,80}(?:clave|contrasena|codigo|\\bpin\\b|token)").containsMatchIn(sentence)
        }
        if (credentials) add(35, "Solicita claves o códigos personales.")
        if (Regex("urgente|hoy mismo|inmediatamente|ultimo plazo|en 24 horas|bloquearemos|suspenderemos").containsMatchIn(text)) add(15, "Presiona para actuar con urgencia.")
        if (Regex("(?:transfiere|deposita|paga).{0,100}(?:activar|liberar|premio|credito|adelantado)").containsMatchIn(text)) add(30, "Pide un pago para liberar un beneficio.")
        val urls = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE).findAll("$subject $body").map { it.value }.take(100).toList()
        val hosts = urls.mapNotNull { url -> runCatching {
            val uri = URI(url)
            (uri.host ?: uri.rawAuthority?.substringAfterLast('@')?.substringBefore(':'))?.lowercase(Locale.ROOT)
        }.getOrNull() }
        if (hosts.any { it in setOf("bit.ly", "tinyurl.com", "t.co", "cutt.ly", "shorturl.at", "is.gd") }) add(15, "Incluye un enlace acortado que oculta el destino.")
        if (urls.any { it.startsWith("http://", true) }) add(15, "Incluye un enlace sin conexión HTTPS.")
        if (hosts.any { it.matches(Regex("[0-9]+(?:\\.[0-9]+){3}")) || it.startsWith("[") }) add(20, "Un enlace utiliza una dirección numérica.")
        if (hosts.any { it.contains("xn--") || it.any { c -> c.code > 127 } }) add(15, "Un dominio requiere revisar cuidadosamente sus caracteres.")
        fun domain(value: String) = Regex("@([a-zA-Z0-9.-]+)").find(value)?.groupValues?.get(1)?.trimEnd('.')?.lowercase(Locale.ROOT)
        if (domain(sender) != null && domain(replyTo) != null && domain(sender) != domain(replyTo)) add(15, "La dirección de respuesta usa otro dominio.")
        val claimsInstitution = Regex("\\b(?:banco|bancoestado|santander|bci|tesoreria|sii|chileatiende|sernac|institucion)\\b").containsMatchIn(text)
        val unsafeChannel = urls.any { it.startsWith("http://", true) } ||
            hosts.any { it in setOf("bit.ly", "tinyurl.com", "t.co", "cutt.ly", "shorturl.at", "is.gd") } ||
            (domain(sender) != null && domain(replyTo) != null && domain(sender) != domain(replyTo))
        if (claimsInstitution && credentials && unsafeChannel) add(20, "Se presenta como una institución y pide datos mediante un canal que requiere verificar su identidad.")
        score = score.coerceAtMost(100)
        return EmailAnalysis(score, when { score >= 60 -> EmailRisk.HIGH; score >= 25 -> EmailRisk.MEDIUM; else -> EmailRisk.LOW }, reasons)
    }
}
