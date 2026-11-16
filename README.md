# Hestia

Aplicación Android del Grupo 28 para adultos mayores: llamadas y correos sospechosos, resumen semanal y educación antifraude. Identidad: **Hestia**, `cl.grupo28.hestia`.

## Experiencia actual — ETAPA 2B

Solo tres secciones: **Resumen, Aprender y Familia**. Configuración inicial de siete pasos, persistida con DataStore y reanudable. Tras finalizar el recorrido se abre Resumen en los siguientes inicios.

Cuenta Google mediante Credential Manager y Supabase Auth implementada para configuración y pruebas. Supabase y Google Auth ya están configurados y se observó una sesión real restaurada y verificada. Sin configuración pública válida el acceso permanece deshabilitado; siempre se puede continuar sin cuenta. Ver [guía ETAPA 2A](docs/ETAPA_2A_AUTH.md) y `secrets.properties.example`.

Gmail OAuth de solo lectura, revisión manual de INBOX semanal y análisis local ya tienen implementación y pruebas locales; falta configurar Gmail API/scope y validar con cuenta real según [guía ETAPA 2B](docs/ETAPA_2B_GMAIL.md). Identificación de llamadas y avisos siguen pendientes. Completar el asistente o iniciar sesión no activa protecciones. Solo se agrega INTERNET, sin permisos Android de ejecución. La sesión se cifra con Android Keystore y se excluye de backups. Al cerrar sesión se conservan los datos locales del teléfono.

Resumen muestra la semana y «Sin datos» para fuentes no consultadas; después de una revisión Gmail real muestra los correos analizados y sospechosos de esa última revisión, con límites parciales explícitos. Incluye un cálculo de dominio probado para eventos futuros, sin generar eventos de demostración. El conteo futuro será de llamadas registradas por Hestia, no de todo el historial telefónico: la recepción de contactos requiere READ_CONTACTS y números privados/restringidos pueden quedar fuera. No se solicita READ_CALL_LOG.

Familia guarda contactos de emergencia en el teléfono: entrada manual, iniciales, eliminación confirmada y apertura del marcador mediante ACTION_DIAL. No necesita permisos de agenda ni CALL_PHONE. No hay backend, invitaciones ni avisos a terceros. Aprender contiene cinco lecciones propias con preguntas, progreso local y enlaces de CMF, SERNAC, ChileAtiende y BCN.

WhatsApp es solo temática educativa. No hay funciones de análisis de mensajes ni sección independiente de Protección.

## Arquitectura y dependencias

Kotlin, Compose/Material 3, Navigation Compose, ViewModel/StateFlow y repositorio DataStore. `features/summary`, `education` y `family`; `onboarding` y `settings` para configuración; `core`, `domain` y `data` para lógica compartida.

Dependencia directa incorporada en ETAPA 1.1: DataStore Preferences **1.1.7**. Se mantienen Navigation Compose 2.9.4, Espresso 3.7.0 (pruebas), AGP 9.5.1, Gradle 9.6.0, Kotlin/Compose plugin 2.2.10, compileSdk/targetSdk 37 y minSdk 29.

Autenticación 2A agrega Credential Manager 1.5.0, su proveedor Google, Google ID 1.1.1 y OkHttp 4.12.0. ETAPA 2B añade play-services-auth 21.4.0 para autorización incremental y revocación, sin actualizar las versiones de AGP/Gradle/Kotlin/SDK. Las pruebas usan coroutines-test 1.8.1, MockWebServer 4.12.0 y JSON 20240303. Supabase se consume mediante su API Auth REST, sin módulos de datos adicionales ni cambios de Kotlin. La autorización Gmail solicita únicamente gmail.readonly, de forma separada del inicio de sesión. No se envían cuerpos ni resultados a Supabase.

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

- [Diseño ETAPA 1.2](docs/DESIGN_DECISIONS.md): paleta, accesibilidad, investigación y límites.
- AGENTS.md: reglas vigentes.
- MVP_SPEC.md: alcance y aceptación actuales.
- CODEX_PROMPTS.md: etapas futuras, sin autorización implícita para ejecutarlas.
- [Informe ETAPA 1.1](docs/ETAPA_1_1.md): cambios, fuentes, pruebas y capturas.
- docs/ETAPA_1.md: histórico, reemplazado por ETAPA 1.1.

Los contactos, preferencias y progreso se almacenan en el espacio privado local de la app mediante DataStore; no se transmiten ni se registran en logs. La lectura en voz alta sigue pendiente. Falta auditoría completa con TalkBack, Android 10 y dispositivos físicos.

Se conserva la limitación de lint de Lifecycle: algunas comprobaciones usan APIs obsoletas y se omiten; no impide compilar. No se actualizan dependencias solo para ocultar advertencias. No se realizaron commits ni push.
