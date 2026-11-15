# MVP_SPEC.md — Hestia

## 1. Propósito e identidad

Tres objetivos: identificar llamadas y correos sospechosos, mostrar un resumen semanal real y enseñar prevención de estafas a adultos mayores. Configuración una vez y uso cotidiano silencioso y sencillo.

Hestia (`cl.grupo28.hestia`) representa protección, hogar, familia y tranquilidad. Debe transmitir seguridad, confianza, cercanía, estabilidad y protección familiar. Inspiración mitológica sutil y contemporánea, con símbolos abstractos de hogar o llama protegida; sin diosas ni templos literales.

Mantener la paleta de AGENTS.md, fondos claros, números grandes y botones de 56 dp. Explicaciones de dos o tres líneas cuando sea posible, sin recortar fuentes ampliadas. Una acción principal, sin párrafos o advertencias técnicas en el resumen.

Android 10/API 29+, Kotlin, Compose, Material 3, MVVM/StateFlow, Navigation Compose y DataStore. Mantener las versiones técnicas validadas.

## 2. Navegación

Solo tres pestañas: **Resumen, Aprender, Familia**. Ajustes desde Resumen. Sin sección independiente de Protección. Sin integración de mensajes; WhatsApp solo como educación.

## 3. Primer inicio

Guardar el avance y finalización del onboarding en DataStore. Tras completarlo, el siguiente inicio abre Resumen. Si se interrumpe, volver al último paso. Indicador de progreso y una pantalla por paso:

1. «Bienvenido a Hestia»: «Te ayudamos a identificar llamadas y correos sospechosos». Botón Comenzar.
2. Google: autenticación futura mediante Supabase Auth para identificar al usuario. No equivale a permiso Gmail.
3. Gmail: «Hestia revisará tus correos para identificar posibles estafas». OAuth independiente y solo lectura.
4. Llamadas: «Hestia puede identificar números sospechosos antes de que contestes». Rol ROLE_CALL_SCREENING mediante RoleManager junto con servicio real; el marcador habitual sigue atendiendo llamadas.
5. Agenda: READ_CONTACTS opcional solo para incluir llamadas de números guardados; no para gestionar contactos de emergencia. Sin READ_CALL_LOG.
6. Notificaciones: «Te avisaremos si detectamos algo sospechoso». POST_NOTIFICATIONS desde Android 13 y comprobación del estado en versiones anteriores.
7. Finalización: mostrar únicamente estados reales. Botón Entrar a Hestia.

**ETAPA 1.1:** no implementar aún Supabase Auth, Gmail OAuth, CallScreeningService ni permisos reales. Los pasos permanecen pendientes y permiten continuar sin concederlos. Completar el asistente registra que fue recorrido, no activa una protección. Las funciones pendientes se podrán configurar en una etapa posterior desde Ajustes.

## 4. Resumen semanal

Pantalla principal: semana actual y tres tarjetas simples, sin gráficos innecesarios:

- Estado general: solo conexiones reales.
- Llamadas registradas por Hestia y sospechosas; correos analizados y sospechosos.
- Alertas revisadas por el usuario.

Calcular exclusivamente desde eventos registrados, con semana de lunes a domingo y zona horaria del dispositivo. Evitar duplicados. Una detección no es un rechazo ni una revisión. Sin datos conectados mostrar «Sin datos»; cero solo cuando exista una fuente real consultada y el resultado sea cero.

CallScreeningService no recibe necesariamente todas las llamadas: contactos necesitan READ_CONTACTS y números privados/restringidos pueden no estar disponibles. Las cifras no representan el historial telefónico completo. No pedir READ_CALL_LOG ni calcular supuestas estafas evitadas.

## 5. Familia

Lista de contactos de emergencia: nombre, número, iniciales o foto, Llamar y Eliminar. Botón principal Agregar contacto. Entrada manual o selector del sistema, sin permiso de agenda innecesario. Guardar varios localmente; confirmar la eliminación. Llamar abre ACTION_DIAL, sin CALL_PHONE.

Sin invitaciones, códigos, cuentas familiares, backend de contactos ni alertas automáticas. Nunca enviar información a terceros sin autorización explícita.

## 6. Aprender

Ruta simple con cinco lecciones de aproximadamente 2 a 3 minutos, incluido el ejercicio. Cada una tiene situación concreta, explicación, recomendación, pregunta, respuesta explicada y acceso a fuentes oficiales. Progreso local persistido, separado de métricas de protección.

1. **¿Te llaman del banco?** No compartir claves/códigos, desconfiar de urgencia, cortar y verificar por canal oficial. CMF y ChileAtiende.
2. **Correos electrónicos falsos.** Remitente, enlaces, adjuntos y datos personales. SERNAC.
3. **Estafas por WhatsApp.** Suplantación familiar, presión para transferir y códigos; verificar por otro medio. ChileAtiende.
4. **Créditos e inversiones fraudulentas.** Ofertas, pagos anticipados y entidades no reguladas; consultar CMF.
5. **¿Qué hago si fui víctima?** Avisar al banco, bloquear, guardar identificador y antecedentes, denunciar y entregar respaldo. Ley 20.009 y ChileAtiende.

Artículo 2 de la Ley 20.009: canales gratuitos, permanentes, 24 horas; registro del aviso, identificador y bloqueo. Artículo 4 bis: deber del usuario de prevenir uso indebido e información clara, accesible y actualizada por las entidades. No afirmar que prohíbe expresamente llamadas para pedir claves ni prometer devolución automática. Atribuir el consejo sobre claves a las recomendaciones de seguridad.

Fuentes obligatorias:

- [CMF: prevención](https://www.cmfchile.cl/educa/621/w3-article-52008.html).
- [CMF: alertas](https://www.cmfchile.cl/educa/621/w3-propertyvalue-49625.html).
- [SERNAC: phishing](https://www.sernac.cl/portal/607/w3-article-1612.html).
- [ChileAtiende: Más Vale Prevenir](https://portal.chileatiende.gob.cl/fichas/81544-campana-mas-vale-prevenir).
- [BCN: Ley 20.009](https://www.bcn.cl/leychile/navegar?idLey=20009).

Material original de Hestia; no son cursos publicados ni avalados por esos organismos. Se complementa SERNAC con su guía reciente de prevención: https://www.sernac.cl/portal/607/w3-article-86575.html. No presentar el candado del navegador como prueba de autenticidad.

## 7. Integraciones posteriores

Google/Supabase Auth separado de Gmail OAuth gmail.readonly. No permisos de envío, borrado o modificación. Pruebas con cuentas autorizadas y configuración Google Cloud; scope restringido y requisitos de verificación aplicables.

Llamadas: rol, número entrante disponible, normalización, caché local y respuesta dentro de cinco segundos. Sin red en el servicio, audio ni bloqueo automático. El número puede ser suplantado.

Motor determinista y explicable; score heurístico, no probabilidad calibrada. Eventos mínimos sin contenido completo del correo. Backend futuro con RLS, claves públicas de cliente y sin secretos en APK. Notificaciones locales antes de cualquier push, sin avisos a contactos no autorizados.

## 8. Aceptación de ETAPA 1.1

- Compilación y pruebas unitarias/instrumentales pertinentes aprobadas.
- Onboarding persistente, reanudable y ausente en el siguiente inicio completado.
- Solo tres secciones, sin tarjetas de canales eliminados.
- Resumen sin métricas fabricadas ni protección ficticia.
- Contactos locales múltiples con validación, eliminación y apertura del marcador.
- Cinco lecciones propias con enlaces oficiales y progreso local.
- Fuente escalable, contraste, controles accesibles y errores de guardado manejados.
- Documentar límites de llamadas, ausencia de integraciones, pruebas físicas y TalkBack pendientes.

No iniciar las integraciones siguientes, hacer commits ni push durante esta etapa.
