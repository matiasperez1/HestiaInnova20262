# ETAPA 4.2 · Teléfono inclusivo

En DEBUG, Hestia vuelve a ser la aplicación telefónica predeterminada si la persona la elige en Android. `InCallService` presenta la llamada y ejecuta los comandos reales para contestar, rechazar, colgar, silenciar, enviar tonos y cambiar la salida de audio cuando el dispositivo ofrece más de una. El modo de Google Teléfono y aviso de Hestia de ETAPA 4.1 queda en **Protección → Herramientas DEBUG**. Las funciones de RELEASE y de Gmail conservan su alcance.

## Decisiones de diseño

- Teléfono concentra cuatro accesos. Marcador es la acción principal. Recientes y Contactos llevan a listas separadas. Protección reúne el estado del servicio, la reputación, los reportes compartidos y las detecciones semanales. La vuelta desde las subáreas es explícita. [Revisión de Gomez-Hernandez et al. (2023)](https://mhealth.jmir.org/2023/1/e43186).
- La llamada entrante tiene dos acciones separadas de 88 dp. La llamada activa muestra número y estado, seis acciones circulares de hasta 80 dp y Colgar de 96 dp. El teclado mantiene las teclas de hasta 100 dp. Las etiquetas y la posición de los controles permiten reconocer las acciones con independencia del color. [Referencia de Darvishy y Hutter](https://doi.org/10.1007/978-3-319-60597-5_22), [revisión de Gomez-Hernandez et al.](https://mhealth.jmir.org/2023/1/e43186).
- La tarjeta existente conserva el contraste de la paleta cálida de Hestia y reduce el mensaje central a **SOSPECHOSO** y **No compartas claves ni códigos**. La explicación está en **Ver por qué**. La tarjeta se mantiene tras contestar y no se muestra para números normales ni pendientes. [Liu y Wang](https://www.tandfonline.com/doi/abs/10.1080/10447318.2024.2338659).
- Los contactos de confianza están disponibles con una búsqueda por nombre o número. Seleccionar un contacto o un reciente rellena el marcador y la persona confirma con **Llamar**. Durante la llamada, Contactos abre la lista para consultarla, con protección cuando el dispositivo está bloqueado con credencial. [Zhang et al. (2024)](https://www.tandfonline.com/doi/full/10.1080/10447318.2022.2131264).

Las referencias orientan decisiones de diseño. Esta etapa no incluye una evaluación de usabilidad con participantes. El capítulo de Darvishy y Hutter se usó como referencia bibliográfica junto con la síntesis previa del proyecto.

## Datos y permisos

- Recientes está apagado al inicio. Al activarlo, guarda hasta 30 llamadas manejadas por Hestia como teléfono predeterminado, con número disponible, fecha y tipo. Se muestran entradas de hasta 30 días y se purgan las caducadas en la siguiente escritura. Se puede desactivar y borrar desde la propia vista. Se excluye de copias de seguridad. La lista no se envía al backend, no importa el historial de Android y es independiente de los contadores de Protección.
- Contactos reutiliza la lista local de Familia para tener una sola lista de confianza. Se puede agregar desde ambos lugares y eliminar desde Familia. No se solicita acceso a la agenda ni al historial de llamadas de Android.
- La reputación se actualiza al abrir **Protección** o desde sus controles. Se conservan los fixtures de ETAPA 3 y la caché de ETAPA 4. El umbral del demo sigue en dos identidades. Los servicios consultan solo datos locales mientras entra la llamada.

## Probar en Pixel 10, API 37

```powershell
.\gradlew.bat assembleDebug assembleRelease testDebugUnitTest lintDebug
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
$emulador = 'emulator-5554'
& $adb -s $emulador install -r app/build/outputs/apk/debug/app-debug.apk
```

1. En **Teléfono**, pulsa **Usar Hestia como teléfono** y confirma en Android. En **Protección** comprueba el estado y los avisos. Android decide si puede presentar la llamada a pantalla completa según sus ajustes. [Documentación de `InCallService`](https://developer.android.com/reference/android/telecom/InCallService).
2. En **Teléfono → Recientes**, activa el registro si quieres comprobarlo. En **Contactos**, agrega un contacto sintético y búscalo. **Marcar a** debe abrir el marcador con su número. Comprueba los accesos Recientes y Contactos del marcador.
3. Para la prueba comunitaria, prepara la [API de ETAPA 4](ETAPA_4_REPUTACION_LLAMADAS.md), reporta `2025550199` con Ana y Bruno, y `2025550196` solo con Ana. Sincroniza en **Protección**.
4. Ejecuta las llamadas de una en una. Prueba contestar, colgar y rechazar desde Hestia. Repite desde Inicio y con la pantalla bloqueada.

```powershell
& $adb -s $emulador emu gsm call 2025550147 # Sospechoso fijo
& $adb -s $emulador emu gsm call 2025550148 # Sin reportes
& $adb -s $emulador emu gsm call 2025550199 # Con dos reportes
& $adb -s $emulador emu gsm call 2025550196 # Pendiente con un reporte
```

5. Amplía la fuente en Android o en **Resumen → Ajustes → Apariencia**. Verifica que se puede leer el aviso, desplazarse cuando falta altura y alcanzar Colgar. Puedes retirar los reportes sintéticos después de probar y volver a sincronizar.

Para volver a Google Teléfono, abre **Teléfono → Protección → Cambiar aplicación de teléfono** y selecciona Google Teléfono en Android. Conserva a Hestia en **Identificación de llamadas y spam** si quieres probar la modalidad B. Puedes volver a habilitar Hestia como teléfono desde la portada de Teléfono.

## Archivos principales

| Área | Archivos |
| --- | --- |
| Activación y navegación | `PhoneScreen.kt`, `DialerSetup.kt`, `HestiaNavigationTest.kt` |
| Telecom, advertencia y marcador | `HestiaInCallService.kt`, `TelecomCallActivity.kt`, `CallWarningPreviewScreen.kt`, `HestiaDialerActivity.kt`, `PhoneControls.kt` |
| Recientes y contactos de confianza | `PhoneRecents.kt`, `PhoneDirectory.kt`, `FamilyScreen.kt`, `PhoneRecentsTest.kt` |

## Verificación

Compilaron DEBUG y RELEASE. Pasaron `lintDebug` y 94 pruebas unitarias Android. El backend local aprobó 51 pruebas y omitió la prueba que requiere `TEST_DATABASE_URL`.

| Pixel 10, API 37 | Resultado |
| --- | --- |
| Activación | El diálogo oficial asignó `ROLE_DIALER` a Hestia. Tras devolverlo a Google, Android abrió su marcador mediante `ACTION_DIAL`. El rol volvió a Hestia para dejar preparada la modalidad principal. |
| `0147` y `0199` | Se mostró **SOSPECHOSO** con el fijo y el número con dos reportes. Se contestó `0147` y se verificó que el aviso seguía durante la llamada. |
| `0148` y `0196` | Se mostraron sin advertencia. Se contestó y rechazó `0148` en llamadas separadas. Se rechazó también `0196`, con un solo reporte. |
| Segundo plano y bloqueo | Hestia presentó su actividad desde Inicio. Con bloqueo por deslizamiento, Android informó `showing=true` y `occluded=true` mientras Hestia mostraba la llamada. |
| Llamada activa | Se verificó el estado Conectada, el cambio de etiqueta al silenciar, el envío de un tono DTMF y la desconexión en Telecom. |
| Recientes, Contactos y Marcador | La activación empezó con una lista de recientes vacía. Tras recibir llamadas se pudo marcar una entrada. Se agregó y buscó un contacto sintético, se abrió su número en el marcador y se inició y terminó la llamada saliente. El contacto se retiró al terminar. |
| Texto al 130 % | Se comprobó la llamada entrante y activa, con la tarjeta y los controles completos. Se pudo contestar, silenciar y colgar. |

Capturas del emulador: [Teléfono](../build/stage42-telefono.png), [Marcador](../build/stage42-marcador-contacto.png), [Recientes](../build/stage42-recientes.png), [entrante normal](../build/stage42-entrante-normal.png), [entrante con reputación comunitaria](../build/stage42-reputacion.png), [llamada activa al 130 %](../build/stage42-texto-130.png), [llamada con bloqueo](../build/stage42-bloqueada.png).

Para el esquema estándar, el contraste calculado de los tokens es 14,35:1 para texto principal sobre el fondo, 5,49:1 para **SOSPECHOSO** sobre la tarjeta y 7,26:1 y 6,76:1 para el contenido claro sobre los botones verde y rojo. Es una comprobación de esos pares de colores y no una auditoría completa de accesibilidad.

Las llamadas simultáneas, doble SIM, dispositivos Bluetooth, equipos físicos y TalkBack requieren validación adicional. La presentación automática depende de las reglas de Telecom y de la configuración de avisos de Android. En un emulador con una sola salida de audio, cambiar altavoz puede no estar disponible. Recientes omite números privados y solo refleja llamadas recibidas por el servicio de Hestia desde que se activa.
