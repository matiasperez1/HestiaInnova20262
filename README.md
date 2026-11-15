# Antifraude — Base documental para Codex

Esta carpeta contiene la base de especificación e instrucciones para desarrollar el MVP Android de Antifraude con Codex en VS Code.

## Contenido

- `AGENTS.md`: reglas permanentes para Codex.
- `MVP_SPEC.md`: requisitos funcionales, alcance y criterios de aceptación.
- `CODEX_PROMPTS.md`: prompts ordenados por etapas.
- `.gitignore`: exclusiones recomendadas para Android y secretos.

## Cómo usar

1. Crear un proyecto Android nativo desde Android Studio usando Empty Activity con Jetpack Compose.
2. Usar el package sugerido `cl.grupo28.antifraude`.
3. Abrir la carpeta raíz del proyecto en VS Code.
4. Copiar estos archivos a la raíz del repositorio.
5. Abrir Codex.
6. Usar el prompt inicial de `CODEX_PROMPTS.md`.
7. Revisar el plan antes de permitir modificaciones.
8. Ejecutar una etapa a la vez y compilar después de cada etapa.

## Requisitos sugeridos

- Android Studio y Android SDK.
- JDK compatible con la versión de Gradle del proyecto (usar JDK 17 si la plantilla y Gradle lo requieren).
- VS Code y Codex.
- Dispositivo Android físico para pruebas de llamadas y SMS.

## Generar APK

Desde PowerShell, en la raíz del proyecto Android:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
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

## Nota

Esta es una base documental, no un proyecto Android compilable por sí solo. El proyecto Android debe crearse desde Android Studio y luego recibir estos archivos en su raíz.
