# Hestia

Aplicación Android del Grupo 28 para adultos mayores: llamadas y correos sospechosos, resumen semanal y educación antifraude. Identidad: **Hestia**, `cl.grupo28.hestia`.

## Experiencia actual — ETAPA 1.2

Solo tres secciones: **Resumen, Aprender y Familia**. Configuración inicial de siete pasos, persistida con DataStore y reanudable. Tras finalizar el recorrido se abre Resumen en los siguientes inicios.

Google/Supabase Auth, Gmail OAuth, identificación de llamadas y avisos siguen pendientes. Sus acciones aparecen no disponibles y el usuario puede continuar sin activarlas. Completar el asistente no concede permisos ni conecta protecciones. No se solicitan permisos Android en esta etapa.

Resumen muestra la semana y campos sin datos («Sin datos»); no hay fuentes reales conectadas. Incluye un cálculo de dominio probado para eventos futuros, sin generar ni sincronizar eventos de demostración. El conteo futuro será de llamadas registradas por Hestia, no de todo el historial telefónico: la recepción de contactos requiere READ_CONTACTS y números privados/restringidos pueden quedar fuera. No se solicita READ_CALL_LOG.

Familia guarda contactos de emergencia en el teléfono: entrada manual, iniciales, eliminación confirmada y apertura del marcador mediante ACTION_DIAL. No necesita permisos de agenda ni CALL_PHONE. No hay backend, invitaciones ni avisos a terceros. Aprender contiene cinco lecciones propias con preguntas, progreso local y enlaces de CMF, SERNAC, ChileAtiende y BCN.

WhatsApp es solo temática educativa. No hay funciones de análisis de mensajes ni sección independiente de Protección.

## Arquitectura y dependencias

Kotlin, Compose/Material 3, Navigation Compose, ViewModel/StateFlow y repositorio DataStore. `features/summary`, `education` y `family`; `onboarding` y `settings` para configuración; `core`, `domain` y `data` para lógica compartida.

Única dependencia directa nueva de ETAPA 1.1: DataStore Preferences **1.1.7**. Se mantienen Navigation Compose 2.9.4, Espresso 3.7.0 (pruebas), AGP 9.5.1, Gradle 9.6.0, Kotlin/Compose plugin 2.2.10, compileSdk/targetSdk 37 y minSdk 29.

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
