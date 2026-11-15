# CODEX_PROMPTS.md — Hestia

Leer AGENTS.md y MVP_SPEC.md completos antes de cada etapa. Trabajar en una etapa a la vez, compilar y probar. No avanzar automáticamente, inventar credenciales, simular permisos o hacer commits/push sin autorización.

Identidad definitiva: **Hestia**, `cl.grupo28.hestia`. Conservar configuración técnica. Las etapas 0, 0.5 y 0.6 establecieron la base. El diseño inicial de ETAPA 1 queda reemplazado por la simplificación de ETAPA 1.1; no restaurar la navegación anterior.

## ETAPA 1.1 — Simplificación

> Lee los documentos y revisa el estado del repositorio. Deja solo Resumen, Aprender y Familia. Resumen será el inicio tras un onboarding persistido y reanudable con DataStore. Prepara siete pasos breves de bienvenida, cuenta Google, Gmail, llamadas, agenda opcional, notificaciones y finalización. No implementes aún autenticación, OAuth, servicio de llamadas, permisos ni eventos ficticios; deja acciones pendientes y permite continuar. El resumen utiliza solo eventos reales y muestra ausencia de datos si no hay fuente. Familia permite agregar/eliminar varios contactos locales y abrir el marcador sin acceso a agenda. Crea cinco lecciones propias basadas en CMF, SERNAC, ChileAtiende y Ley 20.009, con preguntas y fuentes. Reduce texto y pantallas. Actualiza documentación, compila y prueba persistencia, navegación y accesibilidad. Entrega capturas de las tres secciones. No comiences integraciones ni hagas commits/push.

## ETAPA 2 — Cuenta y Gmail (solo con autorización posterior)

> Conecta Supabase Auth con Google y OAuth Gmail independiente de solo lectura. Verifica credenciales, errores, cancelaciones y revocación con cuentas autorizadas. Configura RLS y almacenamiento seguro de tokens. Sin cuerpos de correo en backend ni secretos administrativos en APK. Sincronización manual y análisis local determinista. Activar estados únicamente tras comprobar la conexión real.

## ETAPA 3 — Identificación de llamadas (solo con autorización posterior)

> Implementa CallScreeningService y ROLE_CALL_SCREENING mediante RoleManager. Mantén el marcador habitual. Caché local y respuesta dentro del plazo Android, sin red ni audio ni bloqueo automático. READ_CONTACTS solo opcional si se necesita incluir números guardados; nunca READ_CALL_LOG. Documenta números privados/restringidos y que el registro no es el historial completo. Prueba en dispositivo físico.

## ETAPA 4 — Eventos y notificaciones

> Conecta fuentes reales al resumen semanal y sus estados de disponibilidad. Distingue registro, detección y revisión; deduplica y respeta límites de semana/zona horaria. Implementa avisos locales y permisos reales. Push y compartir con terceros solo con autorización separada y consentimiento explícito. No afirmar estafas evitadas.

## ETAPA 5 — QA y distribución

> Comprueba onboarding, permisos reales y denegados, sincronización, resumen, contactos, fuentes educativas, desconexión, accesibilidad y privacidad. Ejecuta pruebas, genera APK y separa probado, pendiente de credenciales y no probado físicamente. No publicar ni hacer commit/push sin autorización.

## Fuera del plan

No implementar SMS, integración de conversaciones de WhatsApp, pantalla independiente de Protección, acceso al audio celular, bloqueo automático ni control remoto. WhatsApp queda únicamente como temática educativa.
