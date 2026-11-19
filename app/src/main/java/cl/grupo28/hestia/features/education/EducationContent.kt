package cl.grupo28.hestia.features.education

import cl.grupo28.hestia.core.components.HestiaSymbol
import cl.grupo28.hestia.domain.EducationIds

internal data class OfficialSource(val title: String, val url: String)

internal enum class ExerciseKind { CHOOSE_ACTION, FIND_SIGNAL, NEXT_STEP }

internal data class ExerciseOption(val id: String, val label: String)

internal data class EducationExercise(
    val kind: ExerciseKind,
    val question: String,
    val options: List<ExerciseOption>,
    val correctId: String,
    val explanation: String,
) {
    fun evaluate(answer: String?): Boolean? = options.firstOrNull { it.id == answer }?.let { it.id == correctId }
}

internal data class EducationLesson(
    val id: String,
    val title: String,
    val situation: String,
    val signals: List<String>,
    val actions: List<String>,
    val exercise: EducationExercise,
    val followUpExercise: EducationExercise,
    val takeaway: String,
    val sources: List<OfficialSource>,
    val legalNote: String? = null,
)

internal data class EducationModule(
    val id: String,
    val title: String,
    val description: String,
    val icon: HestiaSymbol,
    val lessons: List<EducationLesson>,
)

internal object EducationContent {
    const val REVIEWED_ON = "18 de noviembre de 2026"
    const val RESPONSE_MODULE = "que-hacer"
    const val REPORT_LESSON = "advertencia-hestia"

    private val phoneGuide = OfficialSource("SERNAC · Guía sobre llamadas sospechosas", "https://www.sernac.cl/portal/607/w3-article-86576.html")
    private val emailGuide = OfficialSource("SERNAC · Guía sobre correos y mensajes falsos", "https://www.sernac.cl/portal/607/w3-article-86575.html")
    private val financialGuide = OfficialSource("CMF Educa · Protégete de los fraudes financieros", "https://www.cmfchile.cl/educa/621/w3-article-52008.html")
    private val marketAlerts = OfficialSource("CMF Educa · Alertas del mercado financiero", "https://www.cmfchile.cl/educa/621/w3-propertyvalue-45615.html")
    private val accountGuide = OfficialSource("ChileAtiende · Campaña Más Vale Prevenir", "https://www.chileatiende.gob.cl/fichas/81544/1/pdf")
    private val messageGuide = OfficialSource("CSIRT · Los peligros de los mensajes de texto falsos", "https://csirt.gob.cl/documents/4565/Los-peligros-del-Smishing.pdf")
    private val verificationGuide = OfficialSource("CSIRT · Qué hacer si te roban la cuenta de WhatsApp", "https://csirt.gob.cl/documents/1763/Ciberconsejos-Qu%C3%A9-hacer-si-te-roban-la-cuenta-de-whatsapp.pdf")
    private val fraudLaw = OfficialSource("Biblioteca del Congreso Nacional · Ley 20.009", "https://www.bcn.cl/leychile/navegar?idNorma=236736")
    private val reportingGuide = OfficialSource("ChileAtiende · Denunciar un delito", "https://www.chileatiende.gob.cl/fichas/1795/1/pdf")

    val modules = listOf(
        EducationModule("llamadas", "Estafas telefónicas", "Descubre qué hacer si te piden claves o te apuran por teléfono.", HestiaSymbol.PHONE, listOf(
            EducationLesson(
                EducationIds.BANK_CALL, "¿Te llaman del banco?",
                "Imagina que suena tu teléfono. Una persona dice que llama del banco y que tu cuenta corre peligro. Para protegerla, te pide tu usuario, tu contraseña y un código que acaba de llegar por mensaje.",
                listOf("Te piden la contraseña o un código que tú no solicitaste.", "Te apuran para que entregues los datos antes de verificar la llamada."),
                listOf("Puedes colgar sin compartir tus claves ni el código.", "Busca el número del banco en tu tarjeta o en la aplicación que ya usas."),
                EducationExercise(ExerciseKind.CHOOSE_ACTION, "¿Qué es lo más seguro?", listOf(
                    ExerciseOption("dictar", "Dictar el código"),
                    ExerciseOption("verificar", "Cortar y verificar con el banco"),
                ), "verificar", "Si tú llamas por un canal oficial, puedes revisar la situación con calma sin entregar el código."),
                EducationExercise(ExerciseKind.FIND_SIGNAL, "Además del código, ¿qué pedido debe ponerte en alerta?", listOf(
                    ExerciseOption("clave", "Que te pidan tu contraseña por teléfono"),
                    ExerciseOption("saludo", "Que te saluden antes de hablar"),
                ), "clave", "Tu contraseña es privada. Si te la piden por teléfono, puedes colgar y llamar tú al banco."),
                "Guarda tus códigos para las operaciones que tú iniciaste.", listOf(phoneGuide, financialGuide),
            ),
            EducationLesson(
                "volver-a-llamar", "Antes de devolver la llamada",
                "Al mirar tu teléfono, ves una llamada perdida de un número que te suena conocido. En el mensaje de voz dicen que bloquearán tu tarjeta si no devuelves la llamada pronto.",
                listOf("Un número puede parecer conocido y la identidad de quien llama seguir sin confirmar.", "Te ponen un plazo muy corto para devolver la llamada."),
                listOf("Si necesitas verificarlo, usa un número oficial que encuentres tú.", "Revisa los movimientos con la aplicación habitual del banco."),
                EducationExercise(ExerciseKind.NEXT_STEP, "Terminó la llamada. ¿Qué haces ahora?", listOf(
                    ExerciseOption("oficial", "Buscar el número oficial"),
                    ExerciseOption("recientes", "Marcar el número que apareció"),
                ), "oficial", "El número de la llamada no basta para verificar la identidad. Busca el contacto por tu cuenta."),
                EducationExercise(ExerciseKind.FIND_SIGNAL, "¿Qué te indica que conviene hacer una pausa?", listOf(
                    ExerciseOption("voz", "Que hayan dejado un mensaje de voz"),
                    ExerciseOption("presion", "Que te den muy poco tiempo para devolver la llamada"),
                ), "presion", "La urgencia puede llevarte a marcar sin verificar. Puedes tomarte un momento y buscar el contacto oficial."),
                "Para verificar, elige tú el número al que llamas.", listOf(phoneGuide),
            ),
        )),
        EducationModule("correos", "Correos sospechosos", "Aprende qué revisar si te llega un correo que no esperabas.", HestiaSymbol.MAIL, listOf(
            EducationLesson(
                EducationIds.FAKE_EMAIL, "Correos electrónicos falsos",
                "Te llega un correo con el logo de tu banco. Dice que hoy bloquearán tu cuenta y te pide abrir un enlace para escribir tu contraseña.",
                listOf("Amenazan con un bloqueo para que respondas rápido.", "El correo te dirige a una página para pedirte tu contraseña."),
                listOf("Deja el enlace sin abrir.", "Entra a la aplicación habitual del banco o consulta por un canal oficial."),
                EducationExercise(ExerciseKind.FIND_SIGNAL, "¿Qué parte del correo debe hacerte dudar?", listOf(
                    ExerciseOption("fecha", "Que el correo tenga fecha"),
                    ExerciseOption("clave", "Que te pida la clave desde un enlace"),
                    ExerciseOption("saludo", "Que comience con un saludo"),
                ), "clave", "El logo y el saludo se pueden copiar. Si el correo te pide una clave desde un enlace, verifica desde tu aplicación habitual."),
                EducationExercise(ExerciseKind.NEXT_STEP, "¿Cómo puedes saber si tu cuenta tiene un problema?", listOf(
                    ExerciseOption("app", "Abrir la aplicación que ya uso para el banco"),
                    ExerciseOption("enlace", "Entrar al banco desde el enlace del correo"),
                ), "app", "Al abrir la aplicación por tu cuenta puedes revisar si hay algún aviso sin pasar por la página del correo."),
                "Entra a tu cuenta desde la aplicación que conoces.", listOf(emailGuide),
            ),
            EducationLesson(
                "archivo-inesperado", "Una boleta que no esperabas",
                "Recibes una supuesta boleta de una compra que no reconoces. Te piden abrir un archivo para ver lo que debes pagar.",
                listOf("El archivo llegó sin que lo hayas pedido.", "El nombre de la tienda suena conocido. El correo viene de una dirección distinta."),
                listOf("Deja el archivo cerrado.", "Consulta tus compras en el sitio o la aplicación habitual de la tienda."),
                EducationExercise(ExerciseKind.CHOOSE_ACTION, "¿Cómo compruebas la boleta?", listOf(
                    ExerciseOption("archivo", "Descargar el archivo para ver el monto"),
                    ExerciseOption("tienda", "Revisar mis compras en la tienda"),
                ), "tienda", "Ver tus compras por tu cuenta permite verificar la boleta sin abrir un archivo inesperado."),
                EducationExercise(ExerciseKind.FIND_SIGNAL, "El mensaje usa el nombre de una tienda conocida. ¿Qué detalle conviene revisar?", listOf(
                    ExerciseOption("logo", "Si el logo tiene los colores habituales"),
                    ExerciseOption("remitente", "Si la dirección de quien envía el correo coincide con la habitual"),
                ), "remitente", "El logo se puede copiar. Una dirección distinta es un motivo para verificar la compra por tu cuenta."),
                "Si una boleta te sorprende, verifica la compra antes de abrir archivos.", listOf(emailGuide),
            ),
        )),
        EducationModule("mensajes", "SMS y mensajería", "Aprende a confirmar quién te escribe antes de enviar dinero.", HestiaSymbol.MESSAGE, listOf(
            EducationLesson(
                EducationIds.FAMILY_MESSAGE, "Estafas por WhatsApp",
                "Desde un número nuevo te escriben: «Soy tu hijo. Cambié de número y necesito que me transfieras $30.000 ahora».",
                listOf("El número cambió sin aviso.", "Te piden dinero con urgencia. Una foto de perfil puede copiarse."),
                listOf("Llama al número que ya tenías guardado o consulta con otro familiar.", "Espera a confirmar quién escribe antes de transferir."),
                EducationExercise(ExerciseKind.CHOOSE_ACTION, "¿Qué haces antes de transferir?", listOf(
                    ExerciseOption("familiar", "Confirmar por otro medio"),
                    ExerciseOption("transferir", "Transferir para ayudar de inmediato"),
                ), "familiar", "Tienes una forma más segura de ayudar: verifica con el número que conocías o con otra persona de confianza."),
                EducationExercise(ExerciseKind.NEXT_STEP, "Llamas al número que tenías guardado y nadie contesta. ¿Qué haces?", listOf(
                    ExerciseOption("consulta", "Consultar a otra persona de la familia antes de transferir"),
                    ExerciseOption("foto", "Transferir porque reconoces la foto del perfil"),
                ), "consulta", "Una foto se puede copiar. Otra persona de la familia puede ayudarte a confirmar quién te escribió."),
                "Un mensaje urgente puede esperar mientras confirmas con tu familia.", listOf(accountGuide, verificationGuide),
            ),
            EducationLesson(
                "mensaje-encomienda", "Un mensaje sobre una encomienda",
                "Un SMS dice que tu encomienda está retenida y que debes pagar $990 por un enlace. Después llega un código que el mensaje te pide compartir.",
                listOf("Te piden pagar desde un enlace inesperado.", "Quieren que compartas un código que tú no solicitaste."),
                listOf("Deja el enlace cerrado y mantén el código en privado.", "Busca el seguimiento desde la tienda o el servicio de reparto que usaste."),
                EducationExercise(ExerciseKind.FIND_SIGNAL, "¿Qué señal te indica que debes detenerte?", listOf(
                    ExerciseOption("paquete", "Que se mencione una encomienda"),
                    ExerciseOption("codigo", "Que pidan el código que acaba de llegar"),
                ), "codigo", "El código puede servir para entrar a tu cuenta o confirmar una acción. Revisa el envío por tu cuenta, sin compartirlo."),
                EducationExercise(ExerciseKind.NEXT_STEP, "Si estás esperando una encomienda, ¿dónde puedes revisar su estado?", listOf(
                    ExerciseOption("sms", "En el enlace de pago que llegó por mensaje"),
                    ExerciseOption("seguimiento", "En el seguimiento de la tienda o del reparto que usaste"),
                ), "seguimiento", "Puedes comprobar si hay un problema con el envío desde el canal que ya conoces."),
                "Revisa el seguimiento por tu cuenta. Los códigos son personales.", listOf(messageGuide, emailGuide),
            ),
        )),
        EducationModule("identidad", "Suplantación de identidad", "¿Alguien se hace pasar por otra persona? Aprende a comprobarlo.", HestiaSymbol.FAMILY, listOf(
            EducationLesson(
                "supuesto-funcionario", "¿Es un funcionario de verdad?",
                "Alguien dice llamar de una oficina pública por un beneficio. Sabe tu nombre y parte de tu RUT, y pide que confirmes más datos en ese momento.",
                listOf("Saber datos tuyos no prueba la identidad de otra persona.", "Te piden datos antes de dejarte confirmar el trámite."),
                listOf("Detén la conversación y consulta directamente en la institución.", "Si el trámite existe, pregunta por los pasos usando el contacto que tú encontraste."),
                EducationExercise(ExerciseKind.FIND_SIGNAL, "¿Por qué conviene verificar por tu cuenta?", listOf(
                    ExerciseOption("desconocido", "Todas las llamadas desconocidas son fraudes"),
                    ExerciseOption("rut", "Conocer mi RUT no demuestra quién llama"),
                ), "rut", "Una llamada desconocida puede ser legítima. Los datos que conoce la otra persona no comprueban su identidad."),
                EducationExercise(ExerciseKind.NEXT_STEP, "¿Cómo averiguas si existe el beneficio que te mencionaron?", listOf(
                    ExerciseOption("institucion", "Buscar el contacto oficial de la institución y consultar"),
                    ExerciseOption("datos", "Dar más datos para que te expliquen el trámite"),
                ), "institucion", "Puedes preguntar directamente a la institución por el trámite sin entregar datos en esa llamada."),
                "Puedes verificar un trámite sin entregar tus datos en una llamada inesperada.", listOf(accountGuide, phoneGuide),
            ),
            EducationLesson(
                "amenaza-familiar", "Cuando te piden guardar silencio",
                "Una persona dice que un familiar tiene un problema y que solo puedes ayudar si transfieres ahora. Te pide no colgar ni avisar a nadie.",
                listOf("Quieren que decidas bajo presión.", "Te piden mantener la conversación en secreto para que no puedas verificarla."),
                listOf("Cuelga y habla con tu familiar o con alguien que pueda localizarlo.", "Tómate un momento antes de entregar dinero o información."),
                EducationExercise(ExerciseKind.NEXT_STEP, "¿Qué es lo más seguro si te piden que no cuelgues?", listOf(
                    ExerciseOption("comprobar", "Colgar y comprobar con una persona de confianza"),
                    ExerciseOption("seguir", "Seguir las instrucciones para ganar tiempo"),
                ), "comprobar", "Hablar por un canal que conoces te permite confirmar lo que pasa y pedir apoyo."),
                EducationExercise(ExerciseKind.FIND_SIGNAL, "¿Qué detalle de esta llamada dificulta que compruebes la historia?", listOf(
                    ExerciseOption("mencionar", "Que mencionen a alguien de tu familia"),
                    ExerciseOption("secreto", "Que te pidan mantener todo en secreto"),
                ), "secreto", "Si te piden guardar silencio, es más difícil que alguien te ayude a confirmar lo que está pasando."),
                "Puedes colgar aunque la otra persona te insista en seguir hablando.", listOf(phoneGuide, accountGuide),
            ),
        )),
        EducationModule("cuentas", "Protección de cuentas y dinero", "Cuida tus claves y aprende a revisar un pago que no reconoces.", HestiaSymbol.LOCK, listOf(
            EducationLesson(
                "confirmar-pago", "Un pago que tú no iniciaste",
                "Tu aplicación bancaria te pide confirmar una transferencia que no iniciaste. Al mismo tiempo, te envían un enlace para supuestamente cancelarla.",
                listOf("Te llega una confirmación para una operación que no reconoces.", "Un mensaje externo te pide usar un enlace para resolverlo."),
                listOf("No apruebes la operación. Revisa los movimientos desde la aplicación oficial y avisa al banco si no los reconoces.", "Usa contraseñas distintas y activa la verificación en dos pasos cuando esté disponible. Es una segunda comprobación además de tu clave."),
                EducationExercise(ExerciseKind.CHOOSE_ACTION, "¿Qué haces con esa confirmación?", listOf(
                    ExerciseOption("abrir", "Abrir el enlace que llegó por mensaje"),
                    ExerciseOption("revisar", "Dejarla sin aprobar y revisar en el banco"),
                ), "revisar", "Al entrar por tu cuenta puedes comprobar si existe la operación, sin aprobarla ni dar tus claves en otra página."),
                EducationExercise(ExerciseKind.NEXT_STEP, "Ves un pago desconocido en tu aplicación del banco. ¿Qué haces después?", listOf(
                    ExerciseOption("banco", "Avisar al banco por su canal oficial"),
                    ExerciseOption("mensaje", "Responder el mensaje que ofrecía cancelar el pago"),
                ), "banco", "El canal oficial te permite avisar de la operación y consultar qué hacer."),
                "Aprueba solo operaciones que reconoces y que tú iniciaste.", listOf(financialGuide, emailGuide, verificationGuide),
            ),
            EducationLesson(
                EducationIds.LOAN_OFFER, "Créditos e inversiones fraudulentas",
                "Un anuncio ofrece un crédito inmediato si transfieres $25.000 antes. En la misma página prometen ganancias rápidas y garantizadas.",
                listOf("Piden dinero por adelantado para entregar el crédito.", "Prometen ganancias garantizadas y te apuran para pagar."),
                listOf("Detén el pago y verifica la entidad en el registro y las alertas de la CMF.", "Si vas a pagar una compra, confirma el monto y el destinatario en tu aplicación bancaria antes de aprobar."),
                EducationExercise(ExerciseKind.NEXT_STEP, "¿Qué conviene revisar antes de pagar?", listOf(
                    ExerciseOption("opiniones", "Solo los comentarios del anuncio"),
                    ExerciseOption("cmf", "El registro y las alertas de la CMF"),
                ), "cmf", "La CMF te permite saber si la entidad está fiscalizada y consultar alertas. Que una entidad no figure en alertas no garantiza una oferta."),
                EducationExercise(ExerciseKind.FIND_SIGNAL, "¿Qué detalle del anuncio de crédito es una señal de alerta?", listOf(
                    ExerciseOption("monto", "Que el anuncio diga de cuánto es el crédito"),
                    ExerciseOption("anticipo", "Que te pidan transferir antes de entregarte el crédito"),
                ), "anticipo", "Si te piden dinero por adelantado para entregarte un crédito, detén el pago y verifica la entidad."),
                "Haz una pausa si te piden pagar para recibir un crédito.", listOf(financialGuide, marketAlerts),
            ),
        )),
        EducationModule(RESPONSE_MODULE, "Qué hacer ante una posible estafa", "Encuentra los primeros pasos y cómo pedir ayuda si algo te preocupa.", HestiaSymbol.ALERT, listOf(
            EducationLesson(
                EducationIds.UNKNOWN_PAYMENT, "¿Qué hago si fui víctima?",
                "Ves un movimiento bancario que no reconoces o ya entregaste un código durante una llamada sospechosa.",
                listOf("La operación aparece en tu aplicación oficial y tú no la recuerdas.", "La otra persona insiste en que sigas entregando información."),
                listOf("Termina la conversación. Avisa cuanto antes al emisor por su canal oficial y solicita el bloqueo del medio de pago afectado.", "Guarda el identificador del aviso y los antecedentes. Consulta el procedimiento oficial para denunciar y cómo entregar el respaldo al emisor."),
                EducationExercise(ExerciseKind.NEXT_STEP, "Si no reconoces el movimiento, ¿cuál es el primer paso?", listOf(
                    ExerciseOption("avisar", "Avisar al banco por su canal oficial"),
                    ExerciseOption("esperar", "Esperar a que desaparezca el movimiento"),
                ), "avisar", "Dar aviso permite pedir el bloqueo y dejar constancia. El resultado de un reclamo depende del procedimiento y de sus requisitos."),
                EducationExercise(ExerciseKind.NEXT_STEP, "Después de dar aviso, ¿qué te conviene guardar?", listOf(
                    ExerciseOption("identificador", "El identificador del aviso y los antecedentes de la operación"),
                    ExerciseOption("clave", "La contraseña del banco en un mensaje para compartirla"),
                ), "identificador", "El identificador te permite consultar por tu aviso. Guarda los antecedentes y mantén tus claves en privado."),
                "Avisa, pide el bloqueo y guarda el identificador del aviso.", listOf(financialGuide, fraudLaw, reportingGuide),
                "El artículo 2 de la Ley 20.009 dispone canales gratuitos de aviso las 24 horas, un identificador de seguimiento y el bloqueo del medio de pago. El artículo 4 bis trata la prevención y la información de seguridad que deben entregar los emisores.",
            ),
            EducationLesson(
                REPORT_LESSON, "Una advertencia y cómo pedir ayuda",
                "Hestia mostró un aviso durante una llamada que te dejó con dudas. Quieres revisar lo ocurrido con alguien de confianza.",
                listOf("El aviso señala un posible riesgo. La identidad de quien llama aún necesita verificación.", "Si te presionaron para actuar o te pidieron claves, puedes detenerte y buscar apoyo."),
                listOf("Verifica con la institución por un canal que tú ya conozcas.", "Puedes usar Pedir ayuda para preparar un mensaje a una persona de confianza. Si corresponde denunciar, consulta el canal oficial."),
                EducationExercise(ExerciseKind.CHOOSE_ACTION, "¿Qué puedes hacer después de ver el aviso?", listOf(
                    ExerciseOption("apoyo", "Verificar por otro canal y pedir apoyo"),
                    ExerciseOption("seguro", "Dar por confirmada la estafa y reenviar el número a todos"),
                ), "apoyo", "Una advertencia orienta tu decisión. Verificar por tu cuenta y hablar con una persona de confianza te ayuda a decidir qué hacer."),
                EducationExercise(ExerciseKind.CHOOSE_ACTION, "Si preparas un mensaje para pedir ayuda, ¿qué puedes contar?", listOf(
                    ExerciseOption("datos", "Tu contraseña para que la otra persona revise tu cuenta"),
                    ExerciseOption("situacion", "Lo que te pasó y en qué necesitas apoyo"),
                ), "situacion", "Puedes pedir que te acompañen a verificar lo ocurrido sin enviar claves ni códigos."),
                "Pide ayuda a alguien de confianza y comparte solo lo que tú decidas.", listOf(phoneGuide, reportingGuide),
            ),
        )),
    )

    val lessonsById = modules.flatMap { it.lessons }.associateBy { it.id }
    val modulesById = modules.associateBy { it.id }
}
