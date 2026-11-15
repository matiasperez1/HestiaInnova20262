# Hestia

Proyecto Android nativo de Hestia, aplicación antifraude del Grupo 28 orientada a adultos mayores. Utiliza Kotlin, Jetpack Compose y Material 3. Actualmente conserva la pantalla de plantilla; las funcionalidades del MVP se implementarán por etapas.

Nombre visible y nombre del proyecto Gradle: **Hestia**.
Namespace, application ID y paquetes Kotlin: `cl.grupo28.hestia`.

## Contenido

- `AGENTS.md`: reglas permanentes para Codex.
- `MVP_SPEC.md`: requisitos funcionales, alcance y criterios de aceptación.
- `CODEX_PROMPTS.md`: prompts ordenados por etapas.
- `.gitignore`: exclusiones recomendadas para Android y secretos.

## Cómo usar

1. Crear un proyecto Android nativo desde Android Studio usando Empty Activity con Jetpack Compose.
2. Mantener el identificador definitivo `cl.grupo28.hestia`.
3. Abrir la carpeta raíz del proyecto en VS Code.
4. Copiar estos archivos a la raíz del repositorio.
5. Abrir Codex.
6. Usar el prompt inicial de `CODEX_PROMPTS.md`.
7. Revisar el plan antes de permitir modificaciones.
8. Ejecutar una etapa a la vez y compilar después de cada etapa.

## Requisitos sugeridos

- Android Studio y Android SDK.
- JDK 25, incluido en Android Studio. Los criterios del daemon están en `gradle/gradle-daemon-jvm.properties`.
- VS Code y Codex.
- Dispositivo Android físico para pruebas de llamadas y SMS.

## Java y Gradle en Windows

Se mantienen AGP 9.5.1, Gradle 9.6.0, Kotlin integrado de AGP 9 y plugin Compose 2.2.10. Android mínimo: API 29; compileSdk y targetSdk: 37.

Android Studio y la terminal integrada de VS Code pueden encontrar instalaciones distintas de Java. Comprobar desde la raíz:

```powershell
java -version
$env:JAVA_HOME
.\gradlew.bat --version
```

La salida de Gradle distingue Launcher JVM y Daemon JVM. Para usar el JDK incluido en Android Studio, localizar su carpeta `jbr` en la instalación local y asignarla a `JAVA_HOME` solo en la sesión actual. Este ejemplo solicita la ruta local y no modifica variables globales:

```powershell
$env:JAVA_HOME = Read-Host 'Ruta local a la carpeta jbr de Android Studio (sin comillas)'
& "$env:JAVA_HOME/bin/java.exe" -version
.\gradlew.bat --version
```

En Android Studio, revisar Gradle JDK en los ajustes de Gradle y usar el mismo JDK 25. En VS Code, abrir una terminal nueva si conserva un entorno antiguo. No guardar rutas locales en archivos compartidos. Java 11 en `compileOptions` define compatibilidad del código y no cambia el JDK 25 del daemon.

## Validar y generar APK

Desde PowerShell, en la raíz del proyecto Android:

```powershell
.\gradlew.bat clean
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
# Con un emulador conectado:
.\gradlew.bat connectedDebugAndroidTest
```

APK esperado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Instalar con ADB:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

## Servicios externos

### Supabase
Crear proyecto, configurar Auth, tablas y RLS. La clave `service_role` nunca debe incluirse en la app.

### Google Cloud / Gmail
Habilitar Gmail API, configurar OAuth consent screen y usuarios de prueba. El login Google y la autorización de Gmail son flujos distintos. `gmail.readonly` es un scope restringido y está sujeto a requisitos de Google.

### Firebase
FCM es opcional para la primera demo. Si se integra, configurar el proyecto y el archivo `google-services.json` para el package correcto. No subir secretos privados al repositorio.

## Alcance y límites importantes

- El servicio de llamadas identifica números; no accede al audio.
- No se implementa grabación ni transcripción de llamadas.
- SMS automático requiere `RECEIVE_SMS` y autorización del usuario. La política de Google Play restringe estos permisos; una APK instalada manualmente no garantiza publicación posterior.
- WhatsApp se analiza solo cuando el usuario comparte explícitamente un mensaje.
- No se accede al historial SMS.
- No se solicitan permisos de micrófono, contactos ni accesibilidad del sistema.

## Limitación conocida de lint

El informe anterior detectó `ObsoleteLintCustomCheck` en las comprobaciones incluidas en Lifecycle: `LifecycleWhenChecks` referencia `KtLightModifierList.getKotlinOrigin`, ausente en el lint actual. Se omiten `UnsafeLifecycleWhenUsage` y `RepeatOnLifecycleWrongUsage`; esto limita el análisis estático, aunque no impide compilar.

El [cambio oficial de AndroidX](https://android.googlesource.com/platform/frameworks/support/+/1606adf0448db3b94758f3734111cb3a26976236) indica que esas comprobaciones requieren Lifecycle 2.10.0 con AGP 9.5.0-alpha04 o posterior. La resolución anterior usa Lifecycle 2.9.4. No se actualizan dependencias ni se suprime la advertencia únicamente para eliminarla en esta etapa. Revisar una actualización compatible al incorporar las funciones de Lifecycle.
