# CODEX_PROMPTS.md — Hestia

Leer AGENTS.md y MVP_SPEC.md completos antes de cada etapa. Trabajar en una etapa a la vez, compilar y probar. No avanzar automáticamente, inventar credenciales, simular permisos o hacer commits/push sin autorización.

Identidad definitiva: **Hestia**, `cl.grupo28.hestia`. Conservar configuración técnica. Las etapas 0, 0.5 y 0.6 establecieron la base. El diseño inicial de ETAPA 1 queda reemplazado por la simplificación de ETAPA 1.1; no restaurar la navegación anterior.

## ETAPA 1.1 — Simplificación

> Lee los documentos y revisa el estado del repositorio. Deja solo Resumen, Aprender y Familia. Resumen será el inicio tras un onboarding persistido y reanudable con DataStore. Prepara siete pasos breves de bienvenida, cuenta Google, Gmail, llamadas, agenda opcional, notificaciones y finalización. No implementes aún autenticación, OAuth, servicio de llamadas, permisos ni eventos ficticios; deja acciones pendientes y permite continuar. El resumen utiliza solo eventos reales y muestra ausencia de datos si no hay fuente. Familia permite agregar/eliminar varios contactos locales y abrir el marcador sin acceso a agenda. Crea cinco lecciones propias basadas en CMF, SERNAC, ChileAtiende y Ley 20.009, con preguntas y fuentes. Reduce texto y pantallas. Actualiza documentación, compila y prueba persistencia, navegación y accesibilidad. Entrega capturas de las tres secciones. No comiences integraciones ni hagas commits/push.

## ETAPA 2A — Cuenta Google (etapa autorizada)

> Implementar cuenta opcional mediante Credential Manager y Supabase Auth con ID token y nonce, sesión cifrada con Keystore, restauración/renovación y cierre de sesión. Configuración local pública sin secretos administrativos en APK. Conservar datos locales al cerrar sesión. Guiar creación de proyectos de prueba con Gmail personales, sin publicación. Verificar errores, cancelaciones, revocación y cuenta real en Supabase. No declarar terminada la conexión sin credenciales y pruebas reales. No conectar Gmail ni crear tablas propias en esta entrega. Ver docs/ETAPA_2A_AUTH.md.

## ETAPA 2B — Gmail (etapa autorizada)

> Implementar OAuth Gmail independiente de solo lectura. Configurar cuentas autorizadas y scopes restringidos, verificar errores, cancelaciones y revocación. Sin cuerpos de correo en backend ni secretos administrativos en APK. Sincronización manual y análisis local determinista. Activar estados únicamente tras comprobar la conexión real. RLS obligatorio si se incorporan tablas expuestas.

La implementación y configuración de pruebas de 2B se documentan en docs/ETAPA_2B_GMAIL.md. No ejecutar la ETAPA 3 ni el sistema general de eventos/notificaciones de 4 por esta autorización.

## ETAPA 2C — Gmail automático (etapa autorizada)

> Implementar únicamente Gmail automático con backend TypeScript Node LTS, API Supabase JWT y worker Cloud Run privado IAM/OIDC, AuthorizationClient offline del cliente existente, refresh cifrado KMS/AAD, watch INBOX + Pub/Sub y recuperación incremental sin duplicados. SQL nuevo con RLS y mínimos datos, retención 30 días, FCM HIGH genérico y permiso Android independiente, resumen/alertas reales y desconexión honesta. Conservar revisión manual 2B y la sesión real. Sin despliegue, SQL remoto ni recursos facturables hasta autorización. Preparar scripts/configuración y pruebas locales; no confundirlas con integración real. Ver docs/ETAPA_2C_GMAIL_AUTOMATICO.md y el alcance vigente de AGENTS.md. Las prohibiciones históricas de offline/backend/push de 2B quedan reemplazadas solo para este modo con consentimiento explícito. No actualizar play-services-auth para usar setPrompt: renovar permiso explícitamente mediante revocación confirmada si falta refresh. No iniciar ETAPA 3.

## ETAPA 3 — Identificación de llamadas (solo con autorización posterior)

> Implementa CallScreeningService y ROLE_CALL_SCREENING mediante RoleManager. Mantén el marcador habitual. Caché local y respuesta dentro del plazo Android, sin red ni audio ni bloqueo automático. READ_CONTACTS solo opcional si se necesita incluir números guardados; nunca READ_CALL_LOG. Documenta números privados/restringidos y que el registro no es el historial completo. Prueba en dispositivo físico.

## ETAPA 4 — Eventos y notificaciones

> Conecta fuentes reales al resumen semanal y sus estados de disponibilidad. Distingue registro, detección y revisión; deduplica y respeta límites de semana/zona horaria. Implementa avisos locales y permisos reales. Push y compartir con terceros solo con autorización separada y consentimiento explícito. No afirmar estafas evitadas.

## ETAPA 5 — QA y distribución

> Comprueba onboarding, permisos reales y denegados, sincronización, resumen, contactos, fuentes educativas, desconexión, accesibilidad y privacidad. Ejecuta pruebas, genera APK y separa probado, pendiente de credenciales y no probado físicamente. No publicar ni hacer commit/push sin autorización.

## Fuera del plan

No implementar SMS, integración de conversaciones de WhatsApp, pantalla independiente de Protección, acceso al audio celular, bloqueo automático ni control remoto. WhatsApp queda únicamente como temática educativa.


## ETAPA 2C.1 — LOCAL_DEMO (alcance vigente, sin costos)

Se autoriza una demostración local explícita con datos sintéticos identificados como DEMO LOCAL, sin reemplazar la implementación 2C. PostgreSQL local ya instalado, API y Worker Node separados, cola persistente y adaptadores locales que reutilizan GmailService, parser MIME, motor y almacenamiento/contadores. Identidades demo del servidor, separadas de Supabase; nunca OAuth ni refresh tokens reales. No se inicializa Firebase ni se consulta Google/Supabase en la demo.

Controles/transporte HTTP y permiso ACCESS_LOCAL_NETWORK para Android 17 exclusivamente debug. Release sin controles ni acceso HTTP local. AutomaticGateway/AutomaticApi y el mismo ViewModel; cifras sintéticas nunca se suman a Gmail real. Revisión manual 2B y sesión cifrada se conservan; se vuelve a ellas eligiendo modo real explícitamente. Avisos HIGH en bandeja simulada, MEDIUM solo alerta en app, LOW solo conteo. No prometer recepción con app cerrada ni representar simulaciones como protecciones reales.

Prohibido habilitar facturación, contratar servicios, crear recursos Cloud, ejecutar scripts de despliegue o -Execute durante esta etapa. Sin software adicional, modificaciones de secrets.properties/OAuth, commits, push ni ETAPA 3. Ver docs/ETAPA_2C1_LOCAL_DEMO.md. Las prohibiciones históricas de datos ficticios no impiden esta demo explícita y rotulada; siguen vigentes para el producto real.
