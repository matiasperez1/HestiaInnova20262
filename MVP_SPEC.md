# MVP_SPEC.md — Especificación funcional Heldia

## 1. Resumen

Heldia es una aplicación Android orientada a adultos mayores. Busca prevenir estafas bancarias y digitales mediante detección de comunicaciones sospechosas, educación práctica y acompañamiento de contactos de confianza.

La aplicación no reemplaza al banco, a Gmail ni a la aplicación telefónica. Funciona como una capa de orientación y alerta.

## 2. Plataforma y stack

- Android 10 o superior (API 29+).
- Kotlin y Jetpack Compose.
- Material 3 personalizado.
- MVVM, ViewModel y StateFlow.
- Room y DataStore.
- Supabase Auth y PostgreSQL.
- Gmail API.
- `CallScreeningService`.
- `BroadcastReceiver` para SMS.
- FCM opcional para notificaciones remotas.

Nombre oficial del producto y del proyecto Gradle: **Heldia**.
Namespace, application ID y paquete Kotlin definitivos: `cl.grupo28.heldia`.

Distribución inicial: APK para pruebas. La distribución por APK no exime de restricciones técnicas de Android ni de políticas de Google Play.

## 3. Usuarios

### Adulto mayor
Puede iniciar sesión, configurar protecciones, consultar alertas, analizar mensajes, aprender a reconocer estafas y vincular contactos de confianza.

### Contacto de confianza
Puede aceptar una invitación y recibir únicamente los incidentes y resúmenes que el adulto mayor haya autorizado compartir.

No existe configuración remota del teléfono. El adulto mayor concede personalmente los permisos en su dispositivo.

## 4. Navegación principal

Cuatro secciones:
1. Inicio.
2. Protección.
3. Aprende a protegerte.
4. Mi persona de confianza.

Acceso visible a accesibilidad y ajustes desde las pantallas principales.

## 5. Onboarding

Asistente de configuración:
1. Bienvenida.
2. Iniciar sesión con Google.
3. Conectar Gmail (autorización independiente).
4. Activar identificación de llamadas.
5. Activar notificaciones.
6. Ofrecer activar detección automática de SMS (opcional).
7. Mostrar resumen de protecciones activas y pendientes.

Cada pantalla debe explicar para qué sirve el permiso antes de abrir el diálogo oficial de Android o Google. Al volver, verificar el estado real. El usuario puede continuar sin permisos opcionales.

No pedir permisos de contactos, micrófono, ubicación, `READ_SMS` ni accesibilidad del sistema.

## 6. Dashboard

Mostrar:
- Saludo.
- Estado general de protección.
- Estado de Gmail.
- Estado de identificación de llamadas.
- Estado de SMS (si se habilitó).
- Alertas recientes.
- Acceso a educación.
- Contacto de confianza.
- Resumen semanal.

Mantener la pantalla despejada, con pocas acciones principales y botones grandes.

## 7. Gmail

### Objetivo
Analizar correos para detectar señales de phishing y suplantación bancaria.

### Alcance inicial
- Autorización OAuth independiente del login.
- Sincronización manual de correos recientes.
- Extraer remitente, asunto, encabezados y enlaces cuando estén disponibles.
- Revisar `From` y `Reply-To`.
- Comparar dominios con una lista mantenida de dominios oficiales.
- Revisar resultados SPF, DKIM y DMARC solo si aparecen en encabezados disponibles.
- Detectar enlaces cuyo dominio no coincida con el dominio institucional esperado.
- Mostrar señales encontradas y recomendación.

### Restricciones
- `gmail.readonly` es un scope restringido de Google.
- La app debe configurarse en Google Cloud con consentimiento OAuth y usuarios de prueba.
- La publicación pública requiere cumplir los procesos de verificación aplicables.
- No almacenar el cuerpo completo de correos en Supabase.
- No afirmar que un correo es seguro solo porque el dominio coincide.
- No abrir enlaces automáticamente.

Empezar con sincronización manual. La sincronización periódica es una mejora posterior.

## 8. Identificación de llamadas

### Objetivo
Identificar llamadas entrantes cuyo número haya sido reportado o clasificado como sospechoso.

### Implementación
- `CallScreeningService`.
- Solicitar `ROLE_CALL_SCREENING`.
- Leer número disponible en los detalles de la llamada.
- Normalizar a E.164 cuando sea posible.
- Consultar caché local sincronizada desde backend.
- Clasificar según reportes y estado de verificación.
- Registrar evento mínimo.
- Responder dentro del plazo de Android (cinco segundos).

### Limitaciones
- No escuchar ni grabar audio.
- No transcribir llamadas.
- No analizar la conversación.
- No bloquear automáticamente llamadas.
- No hacer consultas de red en tiempo real.
- No garantizar una interfaz personalizada sobre todas las aplicaciones telefónicas.
- El número puede ser suplantado.
- Sin `READ_CONTACTS`, los números guardados en contactos pueden no entregarse al servicio.

## 9. SMS entrantes

### Objetivo
Detectar señales de fraude en nuevos SMS recibidos, si el usuario concede el permiso.

### Implementación
- `BroadcastReceiver` para `Telephony.Sms.Intents.SMS_RECEIVED_ACTION`.
- Declarar y solicitar `RECEIVE_SMS`.
- Extraer remitente, cuerpo y fecha del SMS entrante.
- Reconstruir mensajes multipartes.
- Ejecutar análisis local y rápido.
- Registrar un evento mínimo.
- Mostrar notificación si el riesgo lo justifica.

### Restricciones
- No solicitar `READ_SMS`.
- No leer historial de mensajes.
- No convertirse en la app SMS predeterminada.
- No enviar el cuerpo completo al backend.
- No hacer llamadas de red dentro del receiver.
- Mantener alternativa de análisis manual si el permiso se rechaza.

El permiso SMS está restringido por Google Play. La app puede probarse por APK con el permiso concedido, pero eso no garantiza que Google Play autorice su publicación. Revisar la política vigente antes de publicar.

## 10. Análisis manual de SMS y WhatsApp

Aceptar texto compartido por el usuario mediante `ACTION_SEND` y `text/plain`.

Flujo:
1. El usuario elige Compartir desde su aplicación de mensajes.
2. Selecciona Heldia.
3. Heldia recibe el texto.
4. Ejecuta el analizador.
5. Muestra riesgo, indicadores y recomendación.
6. Ofrece compartir el incidente con el contacto de confianza.

No acceder automáticamente a WhatsApp ni a la bandeja SMS.

## 11. Motor antifraude

Implementar reglas deterministas y separadas por canal.

### Correo
- Dominio no incluido en lista oficial.
- Dominio parecido a uno oficial (typosquatting básico).
- Diferencia entre From y Reply-To.
- Fallos de autenticación disponibles en encabezados.
- Enlaces externos o dominios inesperados.
- Solicitud de claves/códigos.
- Reportes previos.

### SMS y mensajes compartidos
- Solicitud de códigos de verificación.
- Solicitud de contraseñas o datos bancarios.
- Enlaces sospechosos.
- Suplantación de banco o familiar.
- Amenazas de bloqueo.
- Urgencia o presión para transferir.
- Solicitud de instalar aplicaciones o compartir pantalla.

### Llamadas
- Número reportado.
- Cantidad de reportes.
- Estado de revisión.
- Antigüedad de reportes.

Cada análisis devuelve `riskScore`, `riskLevel`, `reasonCodes`, `explanation` y `recommendation`.

La puntuación es heurística y debe explicarse como indicador, no como probabilidad matemática de fraude.

## 12. Reportes comunitarios

Permitir reportar:
- Números telefónicos.
- Direcciones de correo.
- Dominios.

Estados:
- `REPORTED`: recibido, aún no verificado.
- `SUSPICIOUS`: señales suficientes para advertencia.
- `VERIFIED`: revisado por administrador mediante criterios documentados.

Una denuncia aislada no debe marcar automáticamente un elemento como fraude confirmado. Incluir controles para abuso y denuncias falsas.

## 13. Contactos de confianza

Vinculación por código:
1. Adulto mayor genera invitación.
2. Comparte código manualmente.
3. Contacto inicia sesión.
4. Ingresa el código.
5. Acepta la vinculación.
6. Se crea relación.

El adulto mayor controla qué eventos se comparten. No solicitar acceso a la agenda del dispositivo.

## 14. Educación antifraude

Crear seis lecciones iniciales:
1. Falsos ejecutivos bancarios.
2. Solicitudes de claves y códigos.
3. Enlaces bancarios falsos.
4. Estafas por WhatsApp y suplantación familiar.
5. Aplicaciones maliciosas y acceso remoto.
6. Transferencias sospechosas.

Cada lección incluye:
- Cómo funciona.
- Señales de alerta.
- Qué hacer.
- Qué no hacer.
- Una pregunta o escenario interactivo.

Crear una sección destacada: “Creo que estoy siendo víctima de una estafa”.

Instrucciones:
1. Detener la comunicación.
2. No entregar claves ni códigos.
3. No transferir dinero ni instalar aplicaciones indicadas por desconocidos.
4. Contactar al banco por canales oficiales.
5. Avisar al contacto de confianza.

Si ya hubo transferencia o entrega de datos, indicar que contacte inmediatamente al banco por canales oficiales para bloquear o proteger productos.

## 15. Resumen semanal

Mostrar métricas derivadas de eventos registrados:
- Correos analizados.
- Correos con señales sospechosas.
- Llamadas identificadas como sospechosas.
- SMS analizados.
- Mensajes compartidos analizados.
- Alertas compartidas.
- Lecciones completadas.

Distinguir “amenazas detectadas” de “estafas evitadas”. No afirmar pérdidas económicas evitadas sin evidencia.

## 16. Accesibilidad

Menú accesible desde pantallas principales:
- Texto estándar, grande y muy grande.
- Alto contraste.
- Lectura en voz alta con TextToSpeech.
- Interfaz simplificada.
- Reducir animaciones.
- Restablecer preferencias.

Persistir con DataStore. Respetar TalkBack, escalado de fuente del sistema y semántica Compose.

## 17. Modelo de datos inicial sugerido

### profiles
- `id` UUID vinculado a `auth.users`.
- `full_name`.
- `email`.
- `role` (`PROTECTED_USER` o `TRUSTED_CONTACT`).
- `created_at`.

### trusted_relationships
- `id`.
- `protected_user_id`.
- `trusted_contact_id`.
- `status`.
- `created_at`.

### invitations
- `id`.
- `owner_id`.
- `code_hash`.
- `expires_at`.
- `used_by`.

### official_domains
- `id`.
- `domain`.
- `institution_name`.
- `verification_status`.
- `updated_at`.

### reported_phone_numbers
- `id`.
- `phone_e164`.
- `report_count`.
- `risk_level`.
- `status`.
- `last_reported_at`.

### reported_email_senders
- `id`.
- `sender_identifier`.
- `report_count`.
- `risk_level`.
- `status`.

### security_events
- `id`.
- `user_id`.
- `channel`.
- `risk_level`.
- `reason_codes` JSONB.
- `family_visible`.
- `created_at`.

No guardar cuerpos completos de correos o SMS en estas tablas.

### education_progress
- `id`.
- `user_id`.
- `lesson_id`.
- `completed_at`.

### device_tokens
- `id`.
- `user_id`.
- `fcm_token`.
- `created_at`.

Implementar RLS y políticas mínimas necesarias. Los contactos solo pueden leer eventos expresamente compartidos con ellos.

## 18. Fuera de alcance

- Captura, grabación o transcripción de audio de llamadas celulares.
- Análisis de llamadas en vivo.
- Lectura automática de WhatsApp.
- Lectura histórica de SMS.
- Bloqueo automático de llamadas.
- Aplicación iOS.
- Entrenamiento de un modelo de IA propio.
- Acceso remoto al teléfono del adulto mayor.

## 19. Criterios de aceptación

- APK instalable en Android 10 o superior.
- Login funcional o integración configurada y probada.
- Onboarding verifica permisos reales.
- Preferencias de accesibilidad persisten.
- Gmail analiza correos reales de una cuenta de prueba autorizada.
- CallScreeningService puede activarse y consultar caché local en dispositivo compatible.
- SMS entrante se analiza si `RECEIVE_SMS` fue concedido y el dispositivo permite la prueba.
- Mensaje compartido desde WhatsApp se analiza manualmente.
- Lecciones educativas funcionan.
- Vinculación familiar funciona con dos cuentas de prueba.
- Estadísticas se calculan a partir de eventos reales.
- Los permisos denegados no hacen fallar el resto de la app.

## 20. Prioridad de una semana

1. Día 1: estructura, diseño, navegación.
2. Día 2: onboarding y accesibilidad.
3. Día 3: motor antifraude y educación.
4. Día 4: SMS y llamadas.
5. Día 5: Gmail.
6. Día 6: Supabase, contactos y eventos.
7. Día 7: pruebas, correcciones y APK.

Ajustar el orden según bloqueos de credenciales y pruebas físicas. Es preferible entregar menos funcionalidades probadas que muchas pantallas incompletas.
