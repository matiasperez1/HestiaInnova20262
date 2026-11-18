# ETAPA 4.1 · Google Teléfono y protección de Hestia

En DEBUG, Google Teléfono conserva el marcador, los contactos, el historial, los favoritos y los controles de llamadas. Hestia puede tomar el rol independiente de identificación y filtro para advertir de posibles fraudes. La reputación comunitaria sigue siendo DEMO LOCAL con dos reportes independientes para advertir. La notificación previa de RELEASE se conserva.

## Activación

1. Compila con `./gradlew.bat assembleDebug` e instala con `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`. Sustituye el identificador por el que aparece en `adb devices -l`.
2. En **Teléfono → Aplicación de teléfono**, confirma **Google Teléfono**. Si aparece Hestia, pulsa **Cambiar aplicación de teléfono → Aplicación de teléfono → Teléfono** en los ajustes de Android.
3. En **Teléfono → Identificación de llamadas**, pulsa **Activar identificación**, selecciona Hestia y confirma. Permite las notificaciones para el aviso de respaldo.
4. Para reputación compartida, prepara la API local y crea los reportes con Ana y Bruno siguiendo la [guía de ETAPA 4](ETAPA_4_REPUTACION_LLAMADAS.md). Vuelve a Teléfono o pulsa **Sincronizar reputación** antes de llamar.

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$emulador = 'emulator-5554'
& $adb -s $emulador shell cmd role get-role-holders android.app.role.DIALER
& $adb -s $emulador shell cmd role get-role-holders android.app.role.CALL_SCREENING
# Ejecuta cada llamada por separado y termínala antes de continuar.
& $adb -s $emulador emu gsm call 2025550147 # Sospechoso fijo
& $adb -s $emulador emu gsm call 2025550148 # Sin reportes
& $adb -s $emulador emu gsm call 2025550199 # Sospechoso tras dos reportes y sincronización
& $adb -s $emulador emu gsm call 2025550196 # Pendiente si se guarda solo un reporte
```

Repite `0147` con Hestia abierta, desde Inicio y con la pantalla bloqueada. Durante la llamada, abre **Ver por qué** o pulsa **Cerrar aviso**. Contesta y rechaza desde Google Teléfono. En bloqueo el aviso de respaldo se entrega al sistema, que decide si se muestra según sus ajustes. Para comprobar la caché, detén la API después de sincronizar y repite `0199`. Puedes quitar y volver a conceder el rol desde **Ajustes de Android → Aplicaciones → Aplicaciones predeterminadas → Identificador de llamadas y spam**.

## Presentación y privacidad

- La actividad flotante reutiliza la tarjeta de riesgo y deja libres las zonas externas de la ventana. Android puede rechazar el inicio en segundo plano o priorizar su propia pantalla de llamada. En ese caso se publica un único aviso de respaldo, sin el número. Al recibir otra llamada se cierra el aviso anterior. La tarjeta tiene un tiempo máximo de 25 segundos porque `CallScreeningService` no informa cuándo termina la llamada. [Referencia oficial de CallScreeningService](https://developer.android.com/reference/android/telecom/CallScreeningService).
- No se pide acceso a contactos ni al historial. Android omite las llamadas de números guardados para un servicio de identificación sin `READ_CONTACTS`. Para reproducirlo, prueba `0147` tras guardarlo en un contacto local de prueba y borra ese contacto al terminar. [Documentación oficial de Android](https://developer.android.com/reference/android/telecom/CallScreeningService#onScreenCall(android.telecom.Call.Details)).
- Telecom recibe siempre una respuesta de permitir la llamada. La consulta se hace sobre datos locales. El número de la tarjeta vive en memoria y no se escribe en logs de Hestia ni en el registro semanal. Los números comunitarios persistidos corresponden a reportes explícitos.

## Volver a la modalidad anterior

En **Teléfono → Herramientas DEBUG**, pulsa **Usar Hestia como teléfono** y acepta el diálogo de Android. Aparecerá su marcador de prueba y `InCallService` presenta la pantalla de la ETAPA 3.2. Para regresar a Google Teléfono, usa **Cambiar aplicación de teléfono**. El rol de identificación se gestiona por separado.

## Validación ejecutada en Pixel 10, API 37

| Caso | Resultado |
| --- | --- |
| `0147` fijo y `0199` con dos reportes | Con Hestia abierta se vio la tarjeta y, simultáneamente, el aviso entrante con controles de Google Teléfono. Se abrió **Ver por qué** y se cerró el aviso. |
| `0148` y `0196` con un reporte | Se recibió la llamada sin tarjeta ni notificación de fraude de Hestia. |
| Hestia en segundo plano | Android bloqueó el inicio de la actividad en esta imagen. El aviso de respaldo quedó registrado y Google Teléfono presentó los controles entrantes. |
| Bloqueo | Google Teléfono mostró su pantalla completa. Hestia publicó el aviso de respaldo. Se verificaron las acciones de contestar y rechazar por los eventos de Telecom. |
| Contacto local | Google recibió la llamada de `0147` guardado como contacto local. Hestia no mostró tarjeta ni aviso. El contacto de QA se retiró al terminar. |
| API detenida y Hestia reiniciada | La actualización falló y `0199` conservó la tarjeta desde la caché persistida. Se restauró la API. |
| Retiro y recuperación del rol | Sin el rol, `0147` quedó sin aviso de Hestia. Tras devolver el rol, reapareció la tarjeta. Google Teléfono conservó el rol de marcador en ambos casos. |
| Acceso a Google Teléfono | El botón abrió Google Teléfono. Se comprobó la vista del teclado nativo y la presencia del acceso al historial. La prueba de agenda usó exclusivamente un contacto local sintético. |

Gradle aprobó `assembleDebug`, `assembleRelease`, `testDebugUnitTest` (93 pruebas) y `lintDebug`. El backend DEMO LOCAL aprobó 51 pruebas; la prueba separada que depende de `TEST_DATABASE_URL` quedó omitida. Quedan pendientes las pruebas en teléfono físico y con TalkBack. No se verificó que la tarjeta flote encima de la actividad a pantalla completa de Google Teléfono. La convivencia observada fue con sus controles entrantes en el aviso de Android.

## Archivos modificados

- `app/src/main/java/cl/grupo28/hestia/services/HestiaCallScreeningService.kt` y `CallerIdentification.kt` en DEBUG y RELEASE conectan la presentación de la identificación con el servicio y aíslan el comportamiento nuevo en DEBUG.
- `app/src/debug/AndroidManifest.xml` y `app/src/debug/res/values/caller_identification.xml` registran la actividad privada y su ventana flotante.
- `app/src/debug/java/cl/grupo28/hestia/features/calls/PhoneScreen.kt` reorganiza Teléfono y separa las herramientas de recuperación.
- `AGENTS.md` registra el alcance de la modalidad.
