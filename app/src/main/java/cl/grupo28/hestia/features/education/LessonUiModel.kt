package cl.grupo28.hestia.features.education

import cl.grupo28.hestia.core.components.HestiaSymbol

data class OfficialSource(val title: String, val url: String)
data class LessonUiModel(
    val id: Int, val title: String, val symbol: HestiaSymbol,
    val situation: String, val explanation: String, val recommendation: String,
    val question: String, val correctAnswer: String, val otherAnswer: String, val feedback: String,
    val sources: List<OfficialSource>, val legalNote: String? = null,
)

val cmfPrevention = OfficialSource("CMF · Prevención", "https://www.cmfchile.cl/educa/621/w3-article-52008.html")
val cmfAlerts = OfficialSource("CMF · Alertas", "https://www.cmfchile.cl/educa/621/w3-propertyvalue-49625.html")
val sernacPhishing = OfficialSource("SERNAC · Correos falsos", "https://www.sernac.cl/portal/607/w3-article-1612.html")
val sernacGuide = OfficialSource("SERNAC · Guía de prevención", "https://www.sernac.cl/portal/607/w3-article-86575.html")
val chileAtiende = OfficialSource("ChileAtiende · Más Vale Prevenir", "https://portal.chileatiende.gob.cl/fichas/81544-campana-mas-vale-prevenir")
val fraudLaw = OfficialSource("BCN · Ley 20.009", "https://www.bcn.cl/leychile/navegar?idLey=20009")

/** Own educational scenarios, based on the linked publications; never presented as official courses. */
val lessons = listOf(
    LessonUiModel(1, "¿Te llaman del banco?", HestiaSymbol.PHONE,
        "Una persona dice ser de tu banco y te pide un código para detener un supuesto fraude.",
        "La urgencia puede ser una forma de presionarte. Conocer tu nombre no demuestra quién llama.",
        "No compartas claves ni dictes códigos. Corta y llama al banco por un canal oficial.",
        "¿Qué harías antes de seguir?", "Cortar y verificar con el banco", "Dictar el código para terminar rápido",
        "Verificar por otro canal te permite decidir con calma, sin entregar tus claves.", listOf(cmfPrevention, chileAtiende)),
    LessonUiModel(2, "Correos electrónicos falsos", HestiaSymbol.MAIL,
        "Recibes un correo que amenaza con bloquear tu cuenta si abres un enlace.",
        "El nombre y el logo pueden ser copiados. Revisa la dirección del remitente y desconfía de enlaces inesperados.",
        "No abras adjuntos sospechosos ni entregues datos. Entra a la aplicación oficial por tu cuenta.",
        "¿Cómo revisarías tu cuenta?", "Abrir mi aplicación habitual", "Abrir el enlace del correo",
        "La aplicación que ya usas evita depender del enlace de quien te escribió.", listOf(sernacPhishing, sernacGuide)),
    LessonUiModel(3, "Estafas por WhatsApp", HestiaSymbol.MESSAGE,
        "Un número nuevo dice ser un familiar y pide una transferencia urgente.",
        "Pueden hacerse pasar por otra persona. Los códigos de verificación también pueden usarse para quitarte una cuenta.",
        "No compartas códigos. Antes de transferir, llama al número que ya conocías o verifica por otro medio.",
        "¿Qué harías primero?", "Confirmar por otro medio", "Transferir para ayudar de inmediato",
        "Una foto o un nombre de perfil no bastan para confirmar quién escribe.", listOf(chileAtiende)),
    LessonUiModel(4, "Créditos e inversiones fraudulentas", HestiaSymbol.SHIELD,
        "Te ofrecen un crédito inmediato, pero debes pagar antes de recibir el dinero.",
        "Los pagos anticipados y las ganancias supuestamente garantizadas son señales de alerta.",
        "Consulta las alertas y el registro de instituciones de la CMF antes de entregar dinero.",
        "¿Qué conviene revisar?", "La institución en el sitio de la CMF", "Solo los comentarios del anuncio",
        "Los comentarios no reemplazan la consulta oficial. Una entidad no regulada no tiene la misma supervisión.", listOf(cmfPrevention, cmfAlerts)),
    LessonUiModel(5, "¿Qué hago si fui víctima?", HestiaSymbol.ALERT,
        "Ves un movimiento que no reconoces o ya entregaste datos a alguien sospechoso.",
        "Actúa cuanto antes: avisa al banco y solicita el bloqueo del medio de pago afectado.",
        "Guarda el identificador del aviso y los antecedentes. Denuncia ante Fiscalía, Carabineros o PDI y entrega el respaldo al banco.",
        "¿Cuál es el primer paso?", "Avisar al banco por su canal oficial", "Esperar a ver si el movimiento desaparece",
        "El aviso permite pedir el bloqueo y dejar constancia. La devolución depende del procedimiento y los requisitos legales.", listOf(fraudLaw, chileAtiende),
        "Artículo 2: el emisor debe ofrecer canales gratuitos y permanentes, disponibles las 24 horas, registrar el aviso, entregar su identificador y bloquear el medio de pago.\n\nArtículo 4 bis: los usuarios deben prevenir el uso indebido; las entidades deben informar medidas de seguridad de forma clara, accesible y actualizada."),
)
