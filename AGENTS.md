# AGENTS.md — Antifraude

## 1. Contexto del producto

Antifraude es una aplicación Android del Grupo 28 para ayudar a adultos mayores a prevenir estafas bancarias y digitales.

La aplicación combina:
- Análisis de correos de Gmail.
- Identificación de llamadas mediante números reportados.
- Detección opcional de SMS entrantes.
- Análisis manual de mensajes compartidos desde SMS o WhatsApp.
- Educación preventiva.
- Contactos de confianza y alertas autorizadas.
- Resumen semanal de actividad.

El usuario principal puede tener poca experiencia tecnológica. La facilidad de uso, la accesibilidad y la configuración guiada son requisitos centrales.

## 2. Reglas no negociables

1. Leer `AGENTS.md`, `MVP_SPEC.md` y `CODEX_PROMPTS.md` antes de empezar una tarea.
2. No presentar pantallas de demostración como integraciones reales.
3. No afirmar que una funcionalidad está terminada sin compilarla y probarla.
4. No implementar análisis, grabación ni transcripción de llamadas celulares en vivo.
5. No intentar acceder automáticamente a conversaciones de WhatsApp.
6. No leer el historial completo de SMS. El MVP solo contempla SMS entrantes nuevos, si el usuario habilita el permiso.
7. No solicitar permisos que no sean necesarios.
8. No guardar cuerpos completos de correos o SMS en el backend.
9. No incluir secretos de servidor en el APK.
10. Trabajar en etapas pequeñas y compilar después de cada etapa.

## 3. Stack preferido

- Kotlin.
- Jetpack Compose.
- Material 3 personalizado.
- MVVM con ViewModel y StateFlow.
- Navigation Compose.
- Room para datos locales necesarios.
- DataStore para preferencias.
- WorkManager para sincronizaciones diferidas.
- Supabase Auth y PostgreSQL para backend.
- Gmail API para correos.
- Android `CallScreeningService` para identificación de llamadas.
- Android `BroadcastReceiver` para SMS entrantes.
- Firebase Cloud Messaging (FCM) para notificaciones remotas, si el tiempo y la configuración lo permiten.

Usar versiones estables y compatibles con el proyecto existente. No actualizar masivamente dependencias sin motivo.

## 4. Arquitectura

Separar:
- `core`: tema, navegación, componentes comunes, permisos y accesibilidad.
- `domain`: modelos, reglas y casos de uso.
- `data`: repositorios, fuentes locales y remotas.
- `features`: pantallas y ViewModels por funcionalidad.
- `services`: servicios Android como `CallScreeningService` y `BroadcastReceiver`.
- `workers`: trabajos diferidos.

No colocar lógica de negocio en `MainActivity` ni en composables.

Usar interfaces de repositorio para poder probar funcionalidades sin credenciales externas.

No hacer llamadas de red dentro de `CallScreeningService` ni dentro de `BroadcastReceiver`. La identificación de llamadas debe consultar una caché local. El receptor SMS debe realizar un procesamiento breve y local; el trabajo diferido puede delegarse a WorkManager.

## 5. Diseño visual y accesibilidad

La interfaz debe parecer un producto comercial moderno, confiable y cálido. No usar una plantilla genérica de dashboard.

Paleta inicial:
- Azul profundo: `#172B3A`.
- Verde petróleo: `#347C78`.
- Marfil: `#F5F1E8`.
- Ámbar: `#E9B95D`.
- Coral: `#C96C63`.

Se pueden ajustar tonos para asegurar contraste WCAG adecuado.

### Reglas de interfaz
- Una acción principal clara por pantalla.
- Pocas opciones visibles simultáneamente.
- Botones principales de aproximadamente 56 dp de alto.
- Áreas táctiles de al menos 48 dp.
- Texto principal cómodo de leer; diseñar para escalar, no fijar tamaños rígidos.
- Iconos acompañados de texto.
- Espacios generosos y jerarquía visual consistente.
- Lenguaje cotidiano en español de Chile, sin tecnicismos innecesarios.
- No depender exclusivamente del color para comunicar riesgo.
- Estados vacíos, errores, carga y permisos denegados bien explicados.

### Menú de accesibilidad
Debe estar disponible desde las pantallas principales e incluir:
- Tamaño de texto: estándar, grande y muy grande.
- Alto contraste.
- Lectura en voz alta mediante Android TextToSpeech.
- Interfaz simplificada.
- Reducir animaciones.
- Restablecer preferencias.

Persistir preferencias con DataStore. Respetar el escalado de fuente del sistema, TalkBack y la semántica de Jetpack Compose. No crear un `AccessibilityService` propio ni pedir permiso de accesibilidad del sistema.

## 6. Onboarding y permisos

El onboarding debe ser guiado, breve y comprensible.

Secuencia sugerida:
1. Bienvenida.
2. Inicio de sesión con Google.
3. Conexión independiente de Gmail.
4. Activar identificación de llamadas.
5. Activar notificaciones.
6. Ofrecer protección SMS opcional.
7. Resumen de configuración.

Explicar cada permiso antes de abrir el diálogo del sistema. Verificar el estado real al volver a la aplicación. Permitir continuar si se rechaza un permiso opcional. Nunca mostrar como activo un permiso no concedido.

Distinguir claramente:
- Login con Google: autentica al usuario.
- Autorización Gmail: concede acceso a los correos.
- Rol de identificación de llamadas: se concede desde el flujo de roles de Android.
- Notificaciones: permiso de Android 13 o superior.
- SMS: `RECEIVE_SMS`, permiso de ejecución para la función opcional.

No pedir micrófono, contactos, ubicación, `READ_SMS` ni permiso de accesibilidad.

## 7. Restricciones técnicas y de plataforma

### Llamadas
Implementar `CallScreeningService` y solicitar `ROLE_CALL_SCREENING`.

- Trabajar con el número entrante disponible en `Call.Details`.
- Normalizar números a E.164 cuando sea posible.
- Consultar una base local sincronizada.
- Responder dentro del plazo requerido por Android (cinco segundos).
- No realizar red en tiempo real.
- No grabar, escuchar ni transcribir llamadas.
- No bloquear automáticamente llamadas en el MVP.
- No asumir que se puede dibujar una ventana personalizada encima de cualquier aplicación telefónica. Usar notificaciones o una pantalla interna cuando corresponda y verificar el comportamiento en dispositivos físicos.
- Sin `READ_CONTACTS`, el servicio puede no recibir llamadas de números que ya estén en los contactos; documentar esta limitación.

### SMS
Implementar un `BroadcastReceiver` para `Telephony.Sms.Intents.SMS_RECEIVED_ACTION`.

- Declarar y solicitar `RECEIVE_SMS`.
- Procesar únicamente mensajes entrantes nuevos.
- Reconstruir correctamente SMS multipartes.
- No solicitar `READ_SMS`.
- No leer el historial.
- No convertirse en la aplicación SMS predeterminada.
- Procesar localmente y con rapidez.
- No enviar el texto completo al backend.
- Mantener disponible el análisis manual si el permiso se rechaza.

La instalación por APK no elimina las políticas de Google Play. El permiso SMS está restringido para publicación en Play Store y requiere que la aplicación califique bajo una categoría/excepción aceptada por Google. No prometer aprobación futura.

### WhatsApp
Aceptar únicamente texto que el usuario comparte explícitamente mediante `ACTION_SEND` y `text/plain`. No leer notificaciones ni acceder a la base de datos de WhatsApp.

### Gmail
Separar login Google de OAuth de Gmail. Solicitar solo scopes necesarios. `gmail.readonly` es un scope restringido. Para pruebas, configurar correctamente proyecto, pantalla de consentimiento y usuarios de prueba en Google Cloud. Para publicación pública, cumplir verificación y requisitos aplicables de Google.

No almacenar cuerpos completos de correo en Supabase. Preferir análisis en el dispositivo y persistir solo metadatos mínimos del evento, con consentimiento.

## 8. Seguridad y privacidad

- Nunca incluir `service_role` de Supabase en la aplicación.
- Nunca incluir secretos privados de servidor en el APK.
- Usar RLS en todas las tablas expuestas.
- No registrar tokens ni contenido privado en logs.
- No almacenar contraseñas Google.
- Guardar tokens usando mecanismos seguros y librerías oficiales.
- Compartir eventos con familiares solo con autorización explícita.
- No almacenar cuerpos completos de correos/SMS en el backend.
- No afirmar que un remitente o número es auténtico de forma absoluta.
- La suplantación de números telefónicos es posible.

## 9. Motor antifraude

Implementar primero reglas deterministas y explicables. No depender de un LLM para la funcionalidad esencial.

Cada análisis puede producir:
- `riskScore` interno entre 0 y 100.
- `riskLevel`: LOW, MEDIUM o HIGH.
- `reasonCodes`.
- Explicación en español sencillo.
- Recomendación de actuación.

La puntuación es un indicador heurístico, no una probabilidad estadística calibrada. No mostrar porcentajes de certeza como si fueran probabilidades reales.

Una denuncia aislada no debe convertir automáticamente un número o remitente en fraude confirmado. Distinguir `REPORTED`, `SUSPICIOUS` y `VERIFIED`.

## 10. Flujo de trabajo con Codex

Antes de programar:
1. Inspeccionar el estado del repositorio.
2. Leer los documentos del proyecto.
3. Proponer un plan breve.
4. Identificar permisos, dependencias y credenciales necesarias.
5. Implementar solo la etapa solicitada.

Después:
1. Compilar.
2. Ejecutar pruebas pertinentes.
3. Corregir errores.
4. Informar archivos modificados.
5. Documentar lo que no pudo probarse.

No reescribir módulos existentes sin justificación. No inventar nombres de APIs ni dependencias; consultar documentación oficial si hay dudas.

## 11. Definición de terminado

Una funcionalidad se considera terminada solo cuando:
- El código compila.
- Tiene manejo de errores y estados de permisos.
- Se probó en el nivel adecuado (unitario, integración o dispositivo).
- La UI es accesible.
- La documentación describe sus limitaciones.
- No se presenta una simulación como una integración real.
