# Hestia

Aplicación Android del Grupo 28 para adultos mayores: llamadas y correos sospechosos, resumen semanal y educación antifraude. Identidad: **Hestia**, `cl.grupo28.hestia`.

## Demostración local — ETAPA 2C.1

La arquitectura automática puede demostrarse sin servicios remotos con datos sintéticos, API/Worker Node y PostgreSQL ya instalado. Conserva la implementación 2C de producción, todavía no desplegada. No habilitar facturación ni ejecutar scripts de deploy.

Desde backend en PowerShell: `$env:LOCAL_DEMO='true'`, `npm.cmd run demo:start`. En otra terminal: `npm.cmd run demo:emit -- --scenario high-risk`. Desde la raíz, compilar debug con la misma variable y JDK 25: `.\gradlew.bat assembleDebug`. Abrir Resumen → Revisar correos → DEMO LOCAL; Android 17 solicita permiso de red local exclusivo de debug. No se usa Google Auth, Gmail, KMS, Pub/Sub o FCM remotos en este modo; no se borra la sesión real.

[Guía completa: comandos, escenarios, persistencia, pruebas y límites](docs/ETAPA_2C1_LOCAL_DEMO.md). Los avisos son una bandeja simulada consultada en primer plano; no son push reales. Release no incluye controles demo ni HTTP local.

## Identificación de llamadas — ETAPA 3

Hestia identifica las llamadas que Android Telecom le entrega, consulta la reputación local y muestra avisos para números reportados sin bloquear llamadas. Resumen separa las detecciones reales de los casos sintéticos, disponibles solo en debug. Release aún carece de una lista real de reputación. El flujo se validó en el emulador API 37. La prueba en teléfono físico sigue pendiente. Consulta la [guía de ETAPA 3](docs/ETAPA_3_CALL_SCREENING.md) para activar el rol y reproducir las pruebas con ADB.

## Experiencia de producción — ETAPA 2C preparada

Solo tres secciones: **Resumen, Aprender y Familia**. Configuración inicial de siete pasos, persistida con DataStore y reanudable. Tras finalizar el recorrido se abre Resumen en los siguientes inicios.

Cuenta Google mediante Credential Manager y Supabase Auth implementada para configuración y pruebas. Supabase y Google Auth ya están configurados y se observó una sesión real restaurada y verificada. Sin configuración pública válida el acceso permanece deshabilitado; siempre se puede continuar sin cuenta. Ver [guía ETAPA 2A](docs/ETAPA_2A_AUTH.md) y `secrets.properties.example`.

Gmail conserva la revisión manual 2B y añade la implementación de revisión automática 2C: autorización offline independiente, API/worker TypeScript para Cloud Run, Pub/Sub, Scheduler, KMS, SQL/RLS, resumen/alertas y FCM opcional. **La infraestructura no está desplegada ni se ha validado el flujo con Gmail/FCM reales.** Sin URL de API/Firebase auténticas, la aplicación ofrece la alternativa manual y no presenta protección automática activa. Ver [guía completa 2C](docs/ETAPA_2C_GMAIL_AUTOMATICO.md), que incluye pasos exactos para configurar y probar un correo de prueba autorizado.

Se mantienen Resumen, Aprender y Familia. Resumen utiliza datos reales, separa resultados automáticos de la última revisión manual y muestra «Sin datos» para fuentes no consultadas. La activación automática requiere consentimiento propio: comienza desde ese momento y no revisa todo el historial. Avisos HIGH genéricos y permiso POST_NOTIFICATIONS desde Android 13; rechazarlo no detiene las alertas dentro de la app. Cerrar sesión desvincula avisos del teléfono; Desconectar detiene el seguimiento del servidor.

Sesión Supabase cifrada con Keystore y excluida de backups, preservada en esta actualización. No se modificaron secrets.properties ni los clientes OAuth existentes. No se desplegaron recursos de pago, no se aplicaron migraciones remotas ni se hicieron commits/push.

Familia guarda personas de confianza en el teléfono. Permite editar sus datos, ordenar la lista, elegir un contacto principal y preparar una solicitud de ayuda que la persona revisa antes de abrir Mensajes o el selector de Android. El marcador se abre mediante `ACTION_DIAL`. El selector de contactos del sistema concede acceso al número elegido. [Guía de Familia](docs/ETAPA_5_FAMILIA_CONTACTOS_CONFIANZA.md).

Aprender ofrece seis temas, doce lecciones y ejercicios prácticos sin conexión. Guarda el paso y la respuesta de práctica en el teléfono y reconoce las lecciones terminadas en ediciones anteriores. Protección, Recientes y los correos con señales abren las lecciones relacionadas. [Guía de educación y prevención](docs/ETAPA_6_EDUCACION_PREVENCION.md).

WhatsApp es solo temática educativa. No hay funciones de análisis de mensajes ni sección independiente de Protección.

## Arquitectura y dependencias

Kotlin, Compose/Material 3, Navigation Compose, ViewModel/StateFlow y repositorio DataStore. `features/summary`, `education` y `family`; `onboarding` y `settings` para configuración; `core`, `domain` y `data` para lógica compartida.

Dependencia directa incorporada en ETAPA 1.1: DataStore Preferences **1.1.7**. Se mantienen Navigation Compose 2.9.4, Espresso 3.7.0 (pruebas), AGP 9.5.1, Gradle 9.6.0, Kotlin/Compose plugin 2.2.10, compileSdk/targetSdk 37 y minSdk 29.

Autenticación 2A agrega Credential Manager 1.5.0, su proveedor Google, Google ID 1.1.1 y OkHttp 4.12.0. ETAPA 2B añade play-services-auth 21.4.0 para autorización incremental y revocación, sin actualizar las versiones de AGP/Gradle/Kotlin/SDK. Las pruebas usan coroutines-test 1.8.1, MockWebServer 4.12.0 y JSON 20240303. Supabase se consume mediante su API Auth REST, sin módulos de datos adicionales ni cambios de Kotlin. La autorización Gmail solicita únicamente gmail.readonly, de forma separada del inicio de sesión. En 2B los resultados quedan locales. En 2C solo se guardan metadatos mínimos/resultados con RLS y consentimiento; nunca cuerpos completos o adjuntos. Firebase Messaging 25.0.1 y coroutines-play-services 1.8.1 son las dependencias nuevas Android; no se actualizaron las existentes.

## Java y validación

Usar JDK 25 incluido en Android Studio. Revisar `java -version`, `$env:JAVA_HOME` y `.\gradlew.bat --version`. Si VS Code encuentra un Java antiguo, asignar la carpeta local `jbr` solo a la sesión actual:

```powershell
$env:JAVA_HOME = Read-Host 'Ruta local a jbr de Android Studio, sin comillas'
& "$env:JAVA_HOME/bin/java.exe" -version
.\gradlew.bat --version
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
```

No guardar rutas locales ni modificar variables globales. Java 11 en compileOptions corresponde al código, no al JDK del daemon.

APK: `app/build/outputs/apk/debug/app-debug.apk`. Instalar con `adb install -r` y abrir Hestia. No hace falta configurar servicios externos para revisar esta etapa.

## Documentos y límites

[ETAPA 8 · Auditoría de accesibilidad, seguridad y validación](docs/ETAPA_8_AUDITORIA_ACCESIBILIDAD_SEGURIDAD.md). Incluye las matrices de QA, la inspección del APK RELEASE y los pasos pendientes para TalkBack y Gmail real.

- [ETAPA 7 · Integración del MVP](docs/ETAPA_7_INTEGRACION_CONSOLIDACION_MVP.md) y [guion de demostración para ayudantes](docs/GUIA_DEMO_AYUDANTES.md).
- [Diseño ETAPA 1.2](docs/DESIGN_DECISIONS.md): paleta, accesibilidad, investigación y límites.
- AGENTS.md: reglas vigentes.
- MVP_SPEC.md: alcance y aceptación actuales.
- CODEX_PROMPTS.md: etapas futuras, sin autorización implícita para ejecutarlas.
- [Informe ETAPA 1.1](docs/ETAPA_1_1.md): cambios, fuentes, pruebas y capturas.
- docs/ETAPA_1.md: histórico, reemplazado por ETAPA 1.1.

Los contactos, preferencias y progreso se almacenan en el espacio privado local de la app mediante DataStore; no se transmiten ni se registran en logs. La lectura en voz alta sigue pendiente. Falta auditoría completa con TalkBack, Android 10 y dispositivos físicos.

Se conserva la limitación de lint de Lifecycle: algunas comprobaciones usan APIs obsoletas y se omiten; no impide compilar. No se actualizan dependencias solo para ocultar advertencias. No se realizaron commits ni push.


## Backend 2C y despliegue pendiente

`backend/` contiene dos puntos de entrada y Dockerfiles separados, lockfile y pruebas. Node 24 LTS; instalar con `npm ci --ignore-scripts`, comprobar con `npm test`. La prueba SQL usa solo un PostgreSQL local aislado. `supabase/migrations/202611160001_gmail_automatic.sql` crea los objetos Gmail y protege el acceso por propietario; debe revisarse antes de aplicarla en Supabase real. `deploy/Prepare-Gcp.ps1` y `deploy/Deploy-Services.ps1` solo muestran un plan sin -Execute. No se han ejecutado contra Google Cloud.

Configurar HESTIA_API_URL pública en la sesión de compilación y aportar app/google-services.json auténtico del proyecto existente. El archivo es opcional, ignorado y no se fabricó uno; se inicializa FirebaseOptions explícitamente después del consentimiento. Secretos de servidor en Secret Manager y refresh cifrado por Cloud KMS; ninguno se añade al APK. Testing OAuth puede caducar a los siete días; producción requiere evaluar verificación de scope restringido/CASA. Ver la guía 2C para IAM, secretos, SQL, Firebase, validación y límites.
