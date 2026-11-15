# CODEX_PROMPTS.md — Plan de implementación

## Instrucciones generales

Leer `AGENTS.md` y `MVP_SPEC.md` antes de cada etapa.
Trabajar en una etapa a la vez. No avanzar automáticamente.
Después de cada etapa, compilar y ejecutar las pruebas disponibles.
No inventar credenciales ni presentar datos simulados como reales.

---

## ETAPA 0 — Inspección y plan

Usar `/plan` con este prompt:

> Lee AGENTS.md, MVP_SPEC.md y CODEX_PROMPTS.md. Inspecciona el repositorio sin modificarlo. Revisa Gradle, JDK, SDK, package name, dependencias y estructura. Prepara un plan por etapas para implementar el MVP. Identifica riesgos técnicos y credenciales externas necesarias, especialmente Gmail OAuth, SMS y CallScreeningService. No escribas código todavía.

## ETAPA 1 — Diseño visual y navegación

> Implementa únicamente la etapa de diseño. Configura Jetpack Compose y Material 3 con una identidad visual propia basada en AGENTS.md. Construye navegación principal, dashboard, pantallas vacías/placeholder claramente identificadas, componentes reutilizables y menú de accesibilidad inicial. Prioriza una interfaz moderna, cálida, despejada y apta para adultos mayores. No implementes integraciones externas ni simules datos reales. Compila y reporta archivos modificados.

Pantallas:
- Bienvenida.
- Inicio.
- Protección.
- Educación.
- Contacto de confianza.
- Ajustes y accesibilidad.

Componentes:
- PrimaryButton.
- SecondaryButton.
- RiskBadge con texto además de color.
- ProtectionStatusCard.
- SecurityAlertCard.
- SectionHeader.
- EmptyState.
- ErrorState.
- LoadingState.

## ETAPA 2 — Onboarding y accesibilidad

> Implementa el onboarding como un asistente paso a paso. Incluye login Google (si las credenciales aún no existen, crea la interfaz y documenta la configuración, sin simular autenticación real), autorización Gmail separada, rol de identificación de llamadas, permiso de notificaciones y permiso SMS opcional. Cada pantalla explica el propósito antes de abrir el flujo oficial de autorización. Verifica el estado real al volver. El usuario puede continuar sin permisos opcionales. Implementa preferencias DataStore: tamaño de texto, alto contraste, lectura en voz alta, interfaz simplificada y reducción de animaciones. No solicites permisos de contactos, micrófono, ubicación, READ_SMS ni accesibilidad del sistema. Añade pruebas de estados de permisos y compila.

## ETAPA 3 — Motor antifraude y educación

> Implementa analizadores deterministas separados para email, SMS, mensajes compartidos y números telefónicos. Define modelos de riesgo, códigos de motivos y recomendaciones. La puntuación es heurística, no una probabilidad calibrada. Crea pruebas unitarias con casos legítimos y fraudulentos. Implementa seis lecciones educativas con explicaciones y preguntas interactivas. Incluye el flujo “Creo que estoy siendo víctima de una estafa”. No utilices un LLM como dependencia obligatoria. Compila y reporta resultados de pruebas.

## ETAPA 4 — SMS Android

> Implementa el receptor de SMS entrantes mediante Telephony.Sms.Intents.SMS_RECEIVED_ACTION. Declara RECEIVE_SMS y solicita el permiso en runtime. Procesa solo mensajes nuevos, reconstruye multipartes, analiza localmente con SmsRiskAnalyzer y registra metadatos mínimos. No solicites READ_SMS, no leas historial, no envíes el cuerpo al backend y no hagas red desde BroadcastReceiver. El análisis debe ser breve; usa WorkManager solo para trabajo diferido no crítico. Mantén análisis manual si se deniega el permiso. Documenta que APK sideload no equivale a aprobación de Google Play. Añade pruebas y pasos de prueba física.

## ETAPA 5 — Identificación de llamadas

> Implementa CallScreeningService y solicitud de ROLE_CALL_SCREENING. Consulta exclusivamente una caché local de números normalizados. Responde dentro del plazo de Android. No hagas llamadas de red en el servicio. No grabes, escuches ni transcribas llamadas. No bloquees automáticamente. Registra eventos mínimos y muestra información al usuario mediante mecanismos compatibles (por ejemplo, notificación cuando corresponda), sin asumir que se puede dibujar sobre cualquier dialer. Documenta la limitación de números guardados en contactos cuando no se solicita READ_CONTACTS. Prueba en dispositivo físico.

## ETAPA 6 — Mensajes compartidos y Gmail

> Implementa recepción de texto compartido mediante ACTION_SEND/text/plain para SMS y WhatsApp. El usuario debe iniciar el compartir; no leer WhatsApp automáticamente. Implementa GmailRepository y OAuth de Gmail separado del login Google. Consulta correos recientes autorizados, extrae encabezados y enlaces disponibles y ejecuta EmailRiskAnalyzer. Empieza con sincronización manual. No guardes cuerpos completos en backend. Si faltan credenciales Google Cloud, deja interfaces y documentación de configuración, no datos falsos. Documenta el scope restringido gmail.readonly y requisitos de verificación. Compila y prueba con cuenta autorizada.

## ETAPA 7 — Supabase y contactos de confianza

> Implementa esquema/migraciones y repositorios Supabase para perfiles, invitaciones, relaciones, dominios oficiales, reportes y eventos. Implementa RLS. Crea flujo de invitación mediante código con expiración y aceptación explícita. El adulto mayor elige qué eventos comparte. No pidas acceso a agenda/contactos. Nunca incluyas service_role en APK. Si falta configuración, documenta variables necesarias y no simules sincronización real.

## ETAPA 8 — Notificaciones y resumen

> Implementa primero notificaciones locales y bandeja de incidentes. Si Firebase está configurado, integra FCM para avisos autorizados al contacto de confianza. Calcula resumen semanal desde eventos reales. Diferencia amenazas detectadas de estafas evitadas. Maneja ausencia de conexión y permisos denegados. Compila y prueba.

## ETAPA 9 — QA y APK

> Revisa MVP_SPEC.md y realiza auditoría funcional. Ejecuta pruebas unitarias, compila, revisa permisos del manifest, estados sin conexión, accesibilidad, navegación y privacidad. Comprueba que no existan llamadas de audio, READ_SMS, lectura automática de WhatsApp ni secretos en el APK. Genera APK debug, entrega instrucciones de instalación y un informe que separe: implementado y probado, implementado pero no probado, pendiente por credenciales y fuera de alcance. No declares terminada una integración que no hayas verificado.

---

## Prompt inicial recomendado

Después de abrir el proyecto Android en VS Code:

> Lee AGENTS.md, MVP_SPEC.md y CODEX_PROMPTS.md. Inspecciona el proyecto y utiliza la ETAPA 0 para proponer el plan. No modifiques archivos todavía. Quiero revisar el plan y los riesgos antes de comenzar a implementar.
