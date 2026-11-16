# Hestia — ETAPA 2B: revisión manual de Gmail

## Configurar Google Cloud antes de la prueba real

Usar el mismo proyecto Google Cloud que ya autentica Hestia; no crear otro cliente ni modificar los valores actuales de Supabase/Google en secrets.properties.

1. En Google Cloud, APIs y servicios → Biblioteca, buscar **Gmail API** (`gmail.googleapis.com`) y habilitarla.
2. En Google Auth Platform → Acceso a los datos / pantalla de consentimiento, añadir exclusivamente `https://www.googleapis.com/auth/gmail.readonly` para esta función. No añadir gmail.modify, gmail.send, acceso completo mail.google.com ni scopes de borrado.
3. Mantener audiencia externa y aplicación en **Testing / Pruebas**. Incluir la cuenta Gmail autorizada del equipo en Usuarios de prueba. Las autorizaciones restringidas pueden mostrar advertencias o caducar en el entorno de pruebas; no publicar ni prometer aprobación de Google.
4. Conservar el cliente Android con package `cl.grupo28.hestia` y SHA-1 de la firma que instala el APK. Conservar el client ID web de la cuenta Google ya configurado. No colocar client secret ni credenciales administrativas en Android.
5. Instalar el APK actualizado mediante `adb install -r`; esto conserva los datos y la sesión. **No desinstalar ni borrar datos para validar esta etapa con tu cuenta real.**
6. En Hestia, iniciar/verificar la cuenta y pulsar Conectar Gmail desde onboarding, Ajustes o Revisar correos. Leer y aceptar el consentimiento nativo con la cuenta de prueba. Conectar Gmail no inicia un análisis: pulsar Revisar correos ahora para leer y analizar correos de la semana actual.
7. Comprobar resultados reales y revisar errores/cancelaciones. No enviar correos de prueba desde la app; no se implementan APIs de envío.

Si aparece acceso bloqueado, revisar cuenta de prueba, proyecto y scope. Si Hestia informa que Gmail no está habilitado, comprobar Gmail API en el proyecto del cliente Android y esperar la propagación. Un error no equivale a una bandeja vacía y no convierte resultados previos en una revisión nueva.

## Separación de autenticación y autorización

Credential Manager y Supabase siguen identificando al usuario (2A). La autorización Gmail usa **AuthorizationClient** con la cuenta fijada al correo de Hestia y solo gmail.readonly. Antes de analizar se verifica `users.getProfile`; una cuenta distinta se rechaza antes de leer mensajes. No se pide acceso offline ni server auth code: Google Play Services gestiona sus tokens y la app solo recibe un access token transitorio en memoria para cada acción. No se guardan tokens Gmail en el archivo de sesión Supabase ni se envían a Supabase.

En cada inicio se muestran resultados históricos guardados, pero Gmail no aparece verificado hasta una nueva comprobación autorizada. La revocación externa se detecta al comprobar/usar la API; no se promete detección instantánea ni protección automática.

## Alcance de la revisión

- Solo la bandeja INBOX y la semana actual, de lunes a domingo en la zona horaria del dispositivo. Se excluyen borradores y no se incluyen spam/papelera. Se vuelve a filtrar internalDate localmente por los límites exactos de semana y por fecha actual.
- Paginación con máximo de 200 identificadores únicos y 10 páginas por ejecución. Al alcanzar un límite sin agotar resultados se informa **resultado parcial**; las cifras nunca se presentan como todo el buzón.
- Listar y obtener mensajes son operaciones GET. Se omiten mensajes eliminados entre listado y lectura (404); otros errores abortan la revisión y conservan el último resultado completo.
- No se consulta el endpoint de descarga de adjuntos ni se abren o analizan correos adjuntos. La respuesta MIME completa puede incluir datos adjuntos inline; esas partes se descartan sin procesarlas ni guardarlas. Texto MIME plano y HTML se inspeccionan como texto inerte, sin renderizar HTML, ejecutar scripts, abrir enlaces ni cargar imágenes remotas. Se limita MIME a profundidad 15 y 200 partes, respuesta a 2 MB y texto a 65.536 caracteres; el análisis limitado se indica en cada resultado.
- Los cuerpos se procesan en memoria y se descartan. Solo se guarda un snapshot local de la última revisión: propietario, semana/zona, fecha de revisión, ID SHA-256, fecha del correo, remitente (máximo 160 caracteres), asunto (máximo 120), nivel, score interno, motivos e indicador de contenido limitado. Son metadatos para reconocer el correo, no un archivo del buzón.
- Snapshot cifrado AES-GCM con Android Keystore, escritura atómica en noBackupFilesDir. Sin backups de estos resultados. Cambio/cierre de cuenta elimina los resultados del propietario anterior; ningún resultado se muestra a otra cuenta. Sin cuerpos completos, metadatos ni tokens en backend o logs.
- Las métricas de correo describen **la última revisión manual de esta semana**, no un contador acumulativo de correos históricos ni cobertura continua. Cero solo tras una consulta real que termina sin correos; llamadas y alertas revisadas permanecen Sin datos. No se implementa el almacén general de eventos/notificaciones de la ETAPA 4.

## Reglas antifraude y límites

Reglas deterministas para solicitudes de claves/códigos, urgencia, pagos para liberar beneficios, enlaces acortados, HTTP, direcciones numéricas, dominios con caracteres que requieren revisión y diferencias entre dominio From y Reply-To. Se añade una señal de posible suplantación cuando el mensaje se presenta como una institución, solicita claves y usa un canal sospechoso; nombrar un banco por sí solo no acusa fraude. Señales repetidas no multiplican puntos. Score heurístico interno entre 0 y 100: LOW <25, MEDIUM 25–59, HIGH >=60. La interfaz muestra señales y recomendaciones, nunca porcentajes de certeza ni fraude confirmado.

Un mensaje legítimo puede producir señales y uno fraudulento puede no producirlas. HTTPS, un remitente conocido o un nivel bajo no prueban autenticidad. El motor inicial se centra en expresiones en español, no comprueba reputación de dominios ni autenticidad SPF/DKIM/DMARC, no usa un LLM y no interpreta imágenes, adjuntos u ofuscaciones arbitrarias. Las reglas necesitan evaluación con corpus autorizado antes de afirmar precisión o cobertura.

## Desconexión y consentimiento

Desconectar borra primero la revisión local e intenta `AuthorizationClient.revokeAccess`. **Google revoca todos los scopes concedidos a Hestia**, no solo los indicados en la petición; la confirmación lo explica. La sesión Supabase existente no se cierra automáticamente, pero Google puede pedir consentimiento al entrar de nuevo. Si la red falla se informa desconexión local con revocación remota no confirmada. No se afirma que el permiso remoto desapareció si Google no lo confirmó. Dentro de la operación de desconexión se intenta obtener silenciosamente un token ya disponible, sin lanzar consentimiento; tras revokeAccess se solicita clearToken para ese token. La revocación exitosa también limpia la caché según el contrato del SDK. No se conserva ese token entre operaciones.

No se añaden permisos Android de ejecución, backend, tablas/RLS, Gmail push, WorkManager, avisos, acciones de envío/modificación ni identificación de llamadas. Solo se añade play-services-auth 21.4.0, versión mínima documentada que incorpora revocación y limpieza del token cache; se mantienen las demás versiones.

## Fuentes oficiales

- https://developer.android.com/identity/authorization
- https://developers.google.com/android/guides/releases (23 de julio de 2025: APIs revokeAccess/clearToken)
- https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/list
- https://developers.google.com/workspace/gmail/api/reference/rest/v1/users.messages/get
- https://developers.google.com/workspace/gmail/api/auth/scopes

## Estado de validación

Implementación en revisión técnica. La persona responsable indicó que Gmail API y el scope todavía no están configurados; conexión y análisis con cuenta real permanecen pendientes. No se presenta una prueba con dobles como acceso real al buzón.


## Arquitectura y archivos

| Responsabilidad | Implementación |
|---|---|
| Contratos y datos sin cuerpo/token persistente | `domain/gmail/GmailModels.kt` |
| Reglas deterministas | `domain/gmail/EmailRiskEngine.kt` |
| Transporte REST con OkHttp existente | `data/gmail/GmailApi.kt` (`GmailApi`) |
| Interpretación del mensaje y MIME | `data/gmail/GmailApi.kt` (`GmailMimeParser`) |
| Identidad, paginación, límites y commit del snapshot | `data/gmail/GmailRepository.kt` |
| AES-GCM, Keystore y archivo atómico sin backup | `data/gmail/EncryptedGmailStore.kt` |
| Autorización/resolución nativa y revocación/cache | `features/gmail/GmailAuthorization.kt` |
| Estado y coordinación de operaciones | `features/gmail/GmailViewModel.kt` |
| Panel reutilizado y lista de resultados | `features/gmail/GmailScreen.kt` |

Rutas Kotlin bajo `app/src/main/java/cl/grupo28/hestia/`. MainActivity conecta los ViewModels y el launcher; navegación, onboarding, Ajustes y Resumen conectan sus acciones. Se mantiene una única arquitectura y las tres pestañas. El detalle de revisión pertenece a Resumen. Las responsabilidades de message parser/MIME se agrupan en una implementación, sin capas vacías ni paquetes duplicados.

También se actualizaron AGENTS.md, MVP_SPEC.md, CODEX_PROMPTS.md, README.md y el estado histórico de ETAPA_2A_AUTH.md. En Gradle solo se incorporó la dependencia requerida play-services-auth 21.4.0 mediante catálogo y app/build.gradle.kts: no cambian Gradle/AGP/Kotlin/SDK. No se modificaron secrets.properties, OAuth clients, namespace, application ID ni el flujo de autenticación 2A.

Pruebas añadidas: EmailRiskEngineTest, GmailApiTest, GmailRepositoryTest, GmailViewModelTest (JVM), GmailStoreTest y GmailUiTest (Android). Respuestas sintéticas y MockWebServer exclusivamente en pruebas; no hay datos sembrados en producción.

## Validación final de código

Ejecutados `assembleDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest` y `lintDebug` en una invocación Gradle: BUILD SUCCESSFUL. **63 pruebas JVM y 13 instrumentales aprobadas, sin fallos.** Las instrumentales incluyen navegación completa, regresiones de 2A, cifrado/tamper y separación de propietarios, estados manuales, resultados parciales y métricas sin inventar llamadas.

Para cumplir la prohibición de desinstalar/borrar datos del dispositivo real, connectedDebugAndroidTest se ejecutó exclusivamente en **Hestia_2B_QA**, API 37, creado en build/qa-avd a partir de metadatos de hardware/imagen del SDK. No se copiaron datos de usuario, sesiones ni snapshots del AVD original. Se empleó la propiedad de conservar APK tras el test. El AVD original se detuvo temporalmente conservando sus datos y se restaura al finalizar; las APK del dispositivo real se actualizan únicamente con `adb install -r`.

Logs: `build/stage2b-final-validation.log`; informes JUnit, instrumentación y lint en app/build. La primera selección del argumento Gradle para conservar APK falló por el tratamiento de puntos de PowerShell; se corrigió citando el argumento y la ejecución completa posterior pasó. La limitación inicial de revocación con play-services-auth 21.3.0 se resolvió usando 21.4.0 exigido y documentado; no se sustituyeron APIs por simulaciones.

La prueba real del buzón se hace manualmente por la cuenta autorizada: configurar Google Cloud con los pasos iniciales, conectar, comprobar identidad, pulsar **Revisar correos ahora**, contrastar mensajes recibidos esta semana en INBOX, repetir la revisión sin duplicados y verificar fecha/alcance/parcialidad. Después comprobar cancelación, red caída, revocación y desconexión sabiendo que Google revoca todos los scopes. No distribuir capturas con contenido privado ni registrar tokens. La sesión Supabase, las llamadas sin configurar y los contactos deben seguir independientes.

No se declara Gmail conectado/revisado: Gmail API, scope y consentimiento de la cuenta real todavía deben confirmarse por el propietario. Las pruebas sintéticas no demuestran acceso real ni precisión antifraude. Auditoría con TalkBack y equipos físicos Android 10+ continúa pendiente. No se realizaron commits/push ni se comenzó 2C/3.


### Comprobación posterior en el dispositivo original

Se restauró Pixel_10 y se instaló la APK final únicamente mediante `adb install -r`, sin desinstalar ni borrar datos. Tras estabilizar la apertura, el paso Gmail mostró Conectar Gmail habilitado: ese estado exige cuenta Supabase existente y verificada en la implementación. Se confirmó simultáneamente **Sin conexión verificada** para Gmail, sin pulsar autorización ni leer correos. La sesión de 2A se conservó. Captura sin datos personales: `build/hestia-stage2b-final-ready.png`.

Lint final: **0 errores y 25 advertencias**: 11 GradleDependency, 5 NewerVersionAvailable, 7 UnusedResources, 1 ObsoleteLintCustomCheck (Lifecycle) y 1 RedundantLabel. La comprobación obsoleta de Lifecycle mantiene su limitación previa; no se actualizaron dependencias para ocultar advertencias. El ejecutor también avisa de Unsafe/protobuf; no es código de Hestia. `git diff --check` no presenta errores de espacios.

La interfaz distingue comprobación de permiso, **Revisando correos**, desconexión, revisión completada/parcial y errores. No se guarda esta fase transitoria ni un token en el snapshot. No se han probado aún la autorización, consultas, revocación o limpieza del caché Google con la cuenta real: las firmas de las APIs 21.4.0 están verificadas por compilación; las pruebas instrumentales validan UI, cifrado y flujos aislados.
