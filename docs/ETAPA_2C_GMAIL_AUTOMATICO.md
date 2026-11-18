# Hestia — ETAPA 2C: Gmail automático

## Estado de esta entrega

Código Android y backend preparado localmente. **No se desplegó infraestructura, no se aplicó SQL en Supabase remoto, no se intercambió un código OAuth real y no se validó Gmail → Pub/Sub → Cloud Run → FCM con un buzón real.** No confundir pruebas sintéticas con una integración operativa. Se conserva la revisión manual 2B y la sesión Supabase existente. ETAPA 3 no iniciada.

Esta etapa autoriza, con consentimiento independiente, procesamiento de correo en servidor y autorización offline. Reemplaza para el modo automático las restricciones locales de 2B; ese modo manual mantiene sus límites originales. No se modifica ningún cliente OAuth existente ni secrets.properties.

## Arquitectura y datos

- Android: AuthorizationClient con cuenta fijada, gmail.readonly y requestOfflineAccess del **mismo cliente web** de 2A. El serverAuthCode de un solo uso viaja directamente por HTTPS con el JWT Supabase; nunca se guarda en estado, preferencias o logs.
- API Cloud Run pública a nivel de transporte; cada ruta /v1 verifica el JWT consultando Supabase Auth /user, exige identidad Google y obtiene UUID/correo del servidor. No acepta propietario ni correo del cuerpo como identidad. Solo /health es público.
- Worker Cloud Run privado: IAM run.invoker y verificación adicional de firma, emisor, audiencia, email_verified e identidad de servicio OIDC. Pub/Sub y Scheduler usan cuentas distintas y rutas separadas.
- El backend intercambia el código con el secreto del cliente web desde Secret Manager, compara users.getProfile con el correo verificado y exige refresh token. Access tokens solo viven en memoria. Refresh token cifrado por Cloud KMS GOOGLE_SYMMETRIC_ENCRYPTION (AES-256-GCM) con AAD `hestia:gmail:<UUID>`; solo ciphertext en la tabla privada. Identidades de ejecución y ADC; sin claves JSON de service account.
- users.watch usa INBOX e INCLUDE. La activación registra cursor y hora; **no analiza el historial previo**. Pub/Sub dispara history.list de todas las páginas; se consumen messagesAdded y labelsAdded INBOX. Antes del análisis se comprueba el estado actual INBOX y se excluyen SPAM, TRASH, DRAFT y mensajes previos al inicio del seguimiento.
- Identidad única del evento: UUID propietario + SHA-256 del messageId. Un lease renovable por conexión y escrituras cercadas por su token protegen la concurrencia; pérdida del lease impide confirmar el cursor. El cursor solo avanza tras terminar todas las páginas. Los resultados ya guardados durante un fallo parcial se conservan y se deduplican al reintentar.
- Presupuesto de 450 segundos/500 lecturas nuevas y hasta 1000 páginas por ejecución; errores y límites retornan fallo reintentable. Una nueva ejecución omite hashes ya procesados y progresa. No se confirma cobertura completa al alcanzar un límite.
- Si history.list devuelve 404, se captura primero un cursor de perfil como barrera, se revisa únicamente la semana local actual desde la activación, y se confirma esa barrera al terminar. Así los mensajes recibidos durante el barrido quedan disponibles para history posterior. No es una recuperación de todo el período perdido; los mensajes de semanas anteriores fuera del historial disponible pueden quedar excluidos.
- Renovación diaria del watch **sin adelantar el cursor procesado**; reconciliación cada seis horas para recuperar notificaciones perdidas. Gmail puede retrasar/omitir notificaciones; no se promete detección inmediata ni cobertura absoluta.
- Motor TypeScript determinista portado desde Kotlin. `shared/email-risk-fixtures.json` se prueba en ambos entornos. Score interno, categorías LOW/MEDIUM/HIGH y razones, sin porcentajes ni garantía de autenticidad. Límites MIME: 2 MB de respuesta, profundidad 15, 200 partes, 65.536 caracteres de texto. Respuestas demasiado grandes usan solo metadatos y quedan marcadas limitadas. No se consultan adjuntos ni se ejecuta HTML o abren enlaces.

## Persistencia y privacidad

Migración nueva: `supabase/migrations/202611160001_gmail_automatic.sql`. Revisarla contra el esquema real antes de ejecutarla. Se detiene si detecta objetos Gmail preexistentes; no modifica tablas de autenticación ni esquema desconocido.

| Objeto | Acceso y contenido |
| --- | --- |
| private.gmail_connections | Backend: propietario, correo, refresh cifrado, cursor, watch, zona local, salud y lease |
| public.gmail_events | SELECT solo del propietario autenticado por RLS. Escrituras únicamente backend. Hash, fechas, categoría y resultado limitado |
| private.gmail_devices | Backend: instalación, FCM token, permiso y vencimiento; ningún SELECT de cliente |
| private.gmail_notification_outbox | Backend: entrega de alertas HIGH deduplicada por evento |

LOW guarda hash, propietario, fechas, categoría y limitación; **sin remitente, asunto, score ni razones**. MEDIUM/HIGH guardan remitente máximo 160 caracteres, asunto máximo 120, score interno y razones. Nunca cuerpos completos, adjuntos ni tokens de acceso. Los endpoints de UI no entregan score ni ciphertext. Las respuestas no se cachean.

Eventos se eliminan tras 30 días por las tareas programadas; dispositivos vencen a los 30 días y se renuevan al registrar el teléfono. El refresh cifrado permanece mientras se mantenga la conexión, porque es necesario para el seguimiento. Se elimina al desconectar o eliminar la cuenta. Las claves foráneas a auth.users hacen limpieza en cascada de conexiones, eventos, dispositivos y outbox. **Borrar una fila de cuenta no revoca automáticamente el permiso externo de Google**: antes de una eliminación administrativa, desconectar Gmail mediante la API con esa sesión; si ya se borró, el propietario debe revocar Hestia en su cuenta Google. El watch remoto puede persistir hasta su vencimiento, sin conexión local que pueda procesarlo.

Desconectar intenta users.stop y revocación OAuth, elimina ciphertext, cursor, watch, dispositivos y avisos pendientes; los eventos se borran solo si se confirma esa opción. Una revocación no confirmada queda como DISCONNECTED_REVOCATION_PENDING, sin conservar la credencial para reintentos. El usuario puede completar la revocación desde Seguridad → Conexiones con terceros de Google. La revocación afecta todos los scopes de Hestia, incluida la revisión manual. Desconectar desde el modo manual también intenta detener el seguimiento del servidor, conservando los eventos remotos porque ese diálogo no autoriza borrarlos. La sesión Supabase permanece.

Cerrar sesión desvincula este dispositivo, borra/deshabilita su token Firebase y cancela avisos locales; si no hay red se informa que no se confirmó la desvinculación remota. Los tokens remotos inválidos se eliminan y los registros caducan. El receptor Android además comprueba consentimiento, sesión actual y SHA-256 del propietario antes de mostrar un aviso. **Cerrar sesión no detiene el seguimiento del servidor**: se explica antes de activarlo; para detenerlo usar Desconectar.

FCM solo HIGH. Mensaje genérico generado localmente, sin correo, remitente, asunto, cuerpo, banco, enlaces ni credenciales. Datos: `type`, UUID opaco `eventId`, `accountTag` derivado del UUID. Este último es un identificador seudónimo, no anónimo. Navegación directa mediante PendingIntent explícito/inmutable; la API comprueba de nuevo propietario al obtener detalles. No se confía en el contenido del intent. MEDIUM aparece en Hestia.

Entrega al menos una vez: outbox con bloqueo y reintentos, collapseKey por evento, notificación con tag y deduplicación local acotada a 500 IDs. No se promete exactly-once ante fallos entre FCM y la confirmación de base de datos. Los contadores/eventos sí tienen restricción única. Una notificación perdida siempre puede consultarse dentro de Hestia.

## Compatibilidad OAuth y modo Testing

Se conserva play-services-auth **21.4.0**. `requestOfflineAccess(String)` y `getServerAuthCode()` están disponibles y se compilan. La documentación actual recomienda setPrompt(CONSENT) para reconsentimiento; esa API no existe en 21.4.0 (se incorporó en 21.5.0). No se usa el overload booleano deprecated ni se actualiza la biblioteca silenciosamente.

Alternativa conservadora implementada: si no llega refresh token, el servidor responde 428 REFRESH_TOKEN_REQUIRED y la interfaz ofrece **Renovar permiso de Google**. Solo después de confirmar el alcance global se ejecuta revokeAccess y se solicita nuevamente autorización offline. La revocación puede afectar 2B; no se fuerza consentimiento en cada revisión. Este camino se compila pero aún requiere prueba real con la cuenta autorizada.

En OAuth External/Testing los grants con scope Gmail pueden caducar a los siete días. El backend conserva OAUTH_TESTING_MODE=true y exige reconexión al cumplirse siete días o ante invalid_grant. No cambia el proyecto a Production. Distribución pública de gmail.readonly y datos restringidos procesados en servidor puede requerir verificación, política de privacidad, demostración y evaluación de seguridad/CASA según Google. No se garantiza aprobación.

## Preparación manual de Google Cloud — todavía no ejecutada

Proyecto previsto: **hestia-514901**. Todos los identificadores se parametrizan. Se necesita una cuenta con permisos para desplegar y facturación habilitada, Google Cloud CLI y herramientas de contenedores/Cloud Build. No usar una clave JSON administrativa ni service_role en Android.

1. Revisar primero el plan sin efectos: `./deploy/Prepare-Gcp.ps1 -Region <REGION>`. Elegir región y revisar costos/cuotas, IAM y esquema existente. El script requiere `-Execute` para crear recursos; **no se ha ejecutado con esa opción**. No es un script de actualización idempotente: si un recurso existe se detiene, inspeccionar y reutilizar, nunca borrar para repetirlo.
2. Tras autorización explícita, habilitar las APIs listadas en ese script, crear identidades, topic hestia-gmail-updates, keyring y clave KMS de cifrado simétrico; no establecer fecha de destrucción de versiones en uso. El Gmail API debe estar habilitado en el mismo proyecto del cliente OAuth y del topic. Mantener la cuenta de prueba y scope gmail.readonly en el consentimiento existente.
3. Topic: conceder roles/pubsub.publisher a `gmail-api-push@system.gserviceaccount.com` **solo sobre ese topic**. API runtime recibe publisher sobre el mismo topic para solicitudes manuales. No conceder editor/owner a identidades de ejecución.
4. Runtime API y worker: roles/secretmanager.secretAccessor solo sobre los dos secretos usados y roles/cloudkms.cryptoKeyEncrypterDecrypter solo sobre la clave Gmail. Solo worker necesita roles/firebasecloudmessaging.admin en el proyecto Firebase. API y worker son cuentas separadas.
5. Crear contenedores vacíos Secret Manager `hestia-google-web-secret` y `hestia-database-url`. Añadir versiones desde archivos locales fuera del repositorio con `gcloud secrets versions add <NOMBRE> --data-file=<ARCHIVO_LOCAL> --project hestia-514901`. El primer secreto es **el secreto del cliente web existente**, no una nueva credencial OAuth. No enviarlo por chat ni ponerlo en .env compartido. Elegir versiones numéricas para el despliegue.
6. Inspeccionar el esquema Supabase y aplicar la migración mediante SQL Editor o psql con una credencial administrativa exclusivamente para migrar. Crear aparte un LOGIN de base de datos dedicado y concederle membresía en hestia_backend. Ejemplo SQL administrativo (contraseña introducida de forma segura, nunca pegada en Git): `CREATE ROLE hestia_runtime LOGIN PASSWORD '<GENERAR_LOCALMENTE>' NOBYPASSRLS; GRANT hestia_backend TO hestia_runtime;`. Verificar CONNECT del login y usar connection string TLS con hostname y CA válidos. No usar postgres/service_role como identidad de ejecución. El código hace SET LOCAL ROLE hestia_backend en cada transacción y no depende de las políticas de otras tablas.
7. Verificar que el pooler Supabase admite ese rol LOGIN; si no, usar conexión directa IPv6 o pooler dedicado compatible. Dimensionar un máximo de 5 conexiones por instancia y 3 instancias por servicio. TLS valida certificados; no desactivar rejectUnauthorized para sortear un fallo. Incluir sslmode=verify-full y CA de Supabase si procede; no usar sslmode=no-verify.
8. Construir imágenes desde `backend/` con `Dockerfile.api` y `Dockerfile.worker`, etiquetarlas y subirlas a un repositorio Artifact Registry autorizado. Por ejemplo `docker build -f backend/Dockerfile.api -t <REGION>-docker.pkg.dev/hestia-514901/<REPO>/gmail-api:<VERSION> backend`, repetir para worker. Para un repositorio nuevo autorizado: `gcloud artifacts repositories create hestia-gmail --repository-format=docker --location=<REGION> --project=hestia-514901`, luego `gcloud auth configure-docker <REGION>-docker.pkg.dev` y `docker push <IMAGEN>` para cada imagen. Con Cloud Build usar un archivo de configuración que especifique cada Dockerfile, no el Dockerfile implícito. Dockerfiles usan Node 24 LTS y npm ci con lockfile; npm install scripts están deshabilitados y se requiere validación real de las llamadas Google/FCM tras desplegar. No se ha ejecutado Docker aquí (no está instalado).
9. Obtener número de proyecto con `gcloud projects describe hestia-514901 --format='value(projectNumber)'`. URL determinista del worker: `https://hestia-gmail-worker-<PROJECT_NUMBER>.<REGION>.run.app`. Usar esa URL completa sin ruta final como WORKER_AUDIENCE y verificarla en Cloud Run después de desplegar.
10. Revisar `deploy/Deploy-Services.ps1`: pasar Region, ApiImage, WorkerImage, WorkerUrl, SupabaseUrl, PublishableKey, WebClientId y las dos versiones numéricas de secretos. Sin -Execute solo muestra el plan. Con autorización y -Execute crea las revisiones y recursos de entrega; no crea versiones de secretos ni aplica SQL. La API es pública a nivel IAM porque valida JWT Supabase; el worker debe conservar autenticación IAM. Inspeccionar IAM del worker y eliminar cualquier binding heredado allUsers/allAuthenticatedUsers si ya existía. No exponerlo públicamente para solucionar un 401.
11. Pub/Sub push usa hestia-pubsub-invoker y run.invoker **solo sobre el worker**. El service agent de Pub/Sub puede generar tokens solo de esa identidad (roles/iam.serviceAccountTokenCreator en esa cuenta). Audiencia exactamente WORKER_AUDIENCE, endpoint `/webhooks/pubsub/gmail`, subscription exactamente GMAIL_SUBSCRIPTION. Timeout/ack 600 s, reintentos de 10–600 s y retención 7 días. No hay descarte automático por número de intentos: investigar errores persistentes, cuota y mensajes pendientes. Monitorizar fallos 5xx/409, backlog y expiración de watches sin registrar payloads privados.
12. Scheduler usa hestia-scheduler-invoker, run.invoker solo worker y OIDC con la misma audiencia. Renovación diaria 03:00 UTC; reconciliación cada seis horas UTC. Conservar el rol oficial de service agent de Cloud Scheduler y verificar actAs del operador que crea los jobs. Para volúmenes grandes esta implementación secuencial requiere particionar jobs; no prometer escalabilidad ilimitada. Revisar duración, backlog y costos antes de ampliar usuarios.
13. La variable pública Android HESTIA_API_URL debe ser la URL HTTPS base de **la API**, sin rutas/query/credenciales. Establecerla solo en la sesión de terminal antes de compilar (`$env:HESTIA_API_URL='https://<API_URL>'`); no modificar variables globales. Android Studio necesita recibir esa variable al iniciarse o una configuración local equivalente; no compartir rutas absolutas. Los secretos del servidor no se leen desde Android.

## Firebase Android — configuración pendiente

No existe configuración Firebase real en esta entrega. La APK compila sin ella y no inicializa Firebase ni solicita un token al abrirse.

1. Incorporar Firebase al **mismo proyecto GCP existente**, sin crear otro proyecto por defecto. Registrar la aplicación Android **cl.grupo28.hestia**; asociar certificados SHA-1/SHA-256 del entorno de pruebas cuando corresponda.
2. Descargar el google-services.json auténtico de esa app y colocarlo localmente en `app/google-services.json` (ignorado por Git). No crear un archivo ficticio. Restringir la API key pública según recomendaciones Firebase y revisar permisos/quota. No incluir secretos web ni credenciales de servicio en ese archivo.
3. Gradle lee únicamente app ID, API key pública, project ID y sender ID del cliente que coincide con cl.grupo28.hestia. Se usa FirebaseOptions para inicialización explícita: no es necesario añadir/aplicar Google Services Gradle plugin. Esta elección permite compilar sin archivo y evita auto-inicialización antes del consentimiento. HestiaApplication restaura la inicialización al arrancar un proceso nuevo solo si la configuración, la sesión propietaria y el consentimiento previo coinciden, para que FCM funcione también con la app cerrada. Messaging auto-init y Analytics collection permanecen deshabilitados inicialmente; no se añadió Analytics.
4. Habilitar FCM HTTP v1 y verificar ADC del worker. El permiso de notificaciones es independiente de la autorización Gmail: rechazo no detiene el análisis ni la consulta de alertas en Hestia. El usuario pulsa Activar avisos; Android 13+ muestra el diálogo oficial. En versiones anteriores se comprueba el estado del sistema. Si el usuario deshabilitó el canal/app en Ajustes Android, debe volver a habilitarlo allí.
5. Registrar instalación/token solo mediante API autenticada. onNewToken actualiza el registro si existe consentimiento y cuenta coincidente; un fallo reintenta en el siguiente refresco en primer plano. Nunca registrar tokens completos en logs. Tras recompilar, actualizar instalación conservando datos; no desinstalar la app con la sesión real.

## Endpoints

Todas las rutas /v1 usan Authorization: Bearer del JWT Supabase. POST JSON; GET no cambia estado. Errores estáticos sin respuestas privadas del proveedor. Sin CORS abierto innecesario (cliente nativo).

| Método y ruta | Datos / resultado |
| --- | --- |
| POST /v1/gmail/connect | serverAuthCode, timeZone IANA → estado, watch y última revisión; no devuelve credenciales |
| GET /v1/gmail/status | estado, email propio, expiración, inicio, última revisión, automaticActive |
| POST /v1/gmail/sync | encola revisión incremental, 202; límite una solicitud/minuto |
| POST /v1/gmail/disconnect | deleteEvents boolean → remoteRevocationConfirmed |
| POST /v1/devices/fcm-token | installationId UUID, token, notificationsEnabled |
| DELETE /v1/devices/fcm-token | installationId propio |
| GET /v1/gmail/summary?timeZone=... | conteos de semana local actual, no suma revisión manual |
| GET /v1/gmail/alerts?before=... | página 50 alertas MEDIUM/HIGH, cursor opaco fecha/UUID |
| GET /v1/gmail/alerts/:id | detalle mínimo del propietario; 404 para otro usuario |

Worker: POST /webhooks/pubsub/gmail, /internal/gmail/renew-watches y /internal/gmail/reconcile. Rechaza identidades o suscripciones incorrectas antes de procesar. Las pruebas HTTP usan identidades sintéticas; la firma Google debe verificarse también en el entorno desplegado.

## Pruebas locales y repetición

Android: JDK 25 de Android Studio en JAVA_HOME de la sesión, assembleDebug, testDebugUnitTest, lintDebug y connectedDebugAndroidTest en AVD de QA separado. No borrar ni desinstalar Hestia del dispositivo con sesión real. Los recursos fixtures del test JVM provienen de shared/. Las pruebas no acceden a un buzón real.

Backend: Node 24 LTS, `npm ci --ignore-scripts` en backend y `npm test`. Incluye motor, MIME, semana/DST, OAuth invalid_grant, activación sin histórico, falta refresh, cuenta distinta, paginación/fallo parcial, deduplicación, cursor, lease, renovación, resync y seguridad HTTP/OIDC/payload. La suite PostgreSQL requiere TEST_DATABASE_URL de una base **de pruebas separada**; nunca Supabase real. Para crear un clúster local descartable, usar initdb/pg_ctl dentro de build/pgqa con puerto libre solo localhost; ejecutar test/bootstrap.sql y después la migración. El bootstrap solo se usa en ese entorno vacío; crea auth.users y roles sintéticos. La prueba comprueba RLS, denegación de escritura/tabla privada, LOW sin metadatos, outbox, fencing, unicidad y cascada. Elimina solo su usuario sintético. Para repetirla reinsertar el UUID indicado en bootstrap, sin recrear roles en un clúster ya inicializado.

## Recorrido exacto para validar correo real y aviso

1. Completar los pasos de configuración y despliegue anteriores con autorización; comprobar /health API, IAM privado del worker y conexión SQL/KMS/secrets. Revisar logs solo de códigos y request IDs.
2. Compilar con HESTIA_API_URL y google-services.json reales. Actualizar APK sin desinstalar ni borrar datos. Abrir Hestia, verificar cuenta Supabase conservada y entrar Resumen → Revisar correos.
3. Pulsar Activar revisión automática, leer y aceptar el consentimiento independiente. Completar el diálogo Google. Si falta refresh, usar Renovar permiso de Google aceptando su alcance y repetir. Confirmar backend ACTIVE y watch futuro antes de considerar activo el seguimiento. No utilizar correo de otra persona.
4. Pulsar Activar avisos y conceder POST_NOTIFICATIONS si corresponde. Verificar registro de dispositivo privado sin copiar token a logs. Con rechazo, comprobar que las alertas en app siguen funcionando y no se promete aviso.
5. **Después de activar**, enviar desde otra cuenta de pruebas propia un correo a INBOX con asunto «Prueba Hestia» y texto «Banco: urgente. Ingresa tu clave en http://bit.ly/prueba». Es contenido sintético para esta comprobación, no abrir el enlace ni enviar claves. Usar exclusivamente el buzón autorizado. Cerrar Hestia y esperar la entrega; no pulsar revisión manual para demostrar el flujo por eventos.
6. Comprobar Pub/Sub push autenticado, worker y un evento HIGH único. Recibir aviso genérico sin asunto/remitente. Pulsarlo: abrir Hestia y consultar el detalle del UUID autenticado. El resumen debe aumentar una sola vez. No compartir capturas con correo privado.
7. Reentregar el mismo evento Pub/Sub desde una herramienta administrativa autorizada: no debe duplicar evento/conteo; los avisos usan deduplicación y las garantías indicadas arriba. Enviar un caso MEDIUM («Paga para liberar tu premio») y verificar alerta en app sin push; un LOW ordinario solo incrementa conteo con campos privados nulos.
8. Ejecutar manualmente los jobs con identidad autorizada y verificar renovación sin salto de cursor y reconciliación sin duplicados. Probar cuota/red caída y recuperación; historial vencido requiere entorno de prueba controlado, no alterar la conexión real solo para forzarlo.
9. Cambiar/cerrar sesión y comprobar que este teléfono deja de mostrar avisos del propietario anterior. Probar intent/evento de otra cuenta: el servidor debe devolver 404. No borrar datos del dispositivo.
10. Desconectar, elegir conservación o eliminación de resultados, comprobar ciphertext/dispositivos eliminados, watch detenido cuando Google lo confirma y sesión Supabase intacta. Revocar externamente y probar REAUTH_REQUIRED. En Testing repetir conexión tras caducar el grant. Registrar resultados reales y limitaciones sin afirmar que Gmail es infalible.

Fuentes oficiales consultadas: [Gmail push](https://developers.google.com/workspace/gmail/api/guides/push), [sync](https://developers.google.com/workspace/gmail/api/guides/sync), [AuthorizationRequest](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationRequest.Builder), [AuthorizationResult](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationResult), [OAuth offline](https://developers.google.com/identity/protocols/oauth2/web-server), [Pub/Sub OIDC](https://cloud.google.com/pubsub/docs/authenticate-push-subscriptions), [FCM Android](https://firebase.google.com/docs/cloud-messaging/android/get-started), [scopes restringidos](https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification), [KMS](https://cloud.google.com/kms/docs/algorithms), [URL Cloud Run](https://docs.cloud.google.com/run/docs/triggering/https-request).


## Validación registrada — 16 de noviembre de 2026

- Gradle clean y assembleDebug aprobados, con JDK JetBrains 25.0.4 y Gradle 9.6.0. AGP, Kotlin/Compose plugin y SDK conservados.
- Android: **75 pruebas JVM y 15 instrumentales aprobadas**, cero fallos. Las instrumentales corrieron en Hestia_2B_QA, separado del dispositivo con sesión real, conservando las APK instaladas.
- Backend: **40 pruebas aprobadas, cero fallos y cero omitidas**, incluida PostgreSQL 18 local con migración/RLS/aislamiento/cascada. No se consultó Gmail real en pruebas automatizadas.
- Lint: **0 errores y 27 advertencias**: 12 GradleDependency, 6 NewerVersionAvailable, 7 UnusedResources, 1 ObsoleteLintCustomCheck de Lifecycle y 1 RedundantLabel. Se mantienen versiones; no se actualizaron para ocultar avisos. El ejecutor instrumental también emite el aviso previo de Unsafe/protobuf.
- Emulador original Pixel_10 restaurado, APK actualizada mediante install -r sin desinstalar ni borrar datos. Archivo de sesión cifrada conservado y **Sesión iniciada en Hestia** comprobado en Ajustes, sin copiar credenciales o correo personal. Se volvió a Resumen.
- No se crearon recursos Cloud, aplicaron migraciones remotas, modificaron clientes OAuth/credenciales ignoradas, hicieron commits/push ni inició ETAPA 3. Docker/Google Cloud CLI no están instalados aquí: las imágenes y los scripts de despliegue no se ejecutaron. Autorización offline real, watch/PubSub, KMS remoto y FCM real siguen pendientes. TalkBack y dispositivo físico también pendientes.

Inventario de cambios y entrega: [ETAPA_2C_ENTREGA.md](ETAPA_2C_ENTREGA.md).
