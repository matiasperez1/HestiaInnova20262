# ETAPA 3.2 — Teléfono de Hestia en DEBUG

## Instalar y activar

1. Compila `assembleDebug` con el entorno habitual de Android Studio. Instala o actualiza el APK **en el emulador de QA**, sin borrar los datos.
2. En la barra inferior de Hestia entra a **Teléfono → Usar Hestia como teléfono**. En el diálogo de Android selecciona Hestia y confirma. La pestaña verifica el titular real de `ROLE_DIALER` al volver.
3. Para usar el aviso de respaldo, permite las notificaciones de Hestia. Desde Android 14, si aparece **Permitir avisos a pantalla completa**, ese botón abre la configuración de Android para el permiso especial.
4. Para recuperar el teléfono original abre **Teléfono de Hestia → Cambiar aplicación de teléfono → Aplicación de teléfono → Teléfono**. También se puede hacer desde **Ajustes de Android → Aplicaciones → Aplicaciones predeterminadas**. El rol de identificación de llamadas es independiente.

```powershell
adb devices -l
adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 emu gsm call 2025550147
# En Hestia, pulsa Contestar y luego Colgar, o pulsa Rechazar.
adb -s emulator-5556 emu gsm call 2025550148
# En Hestia, prueba Contestar o Rechazar con la pantalla sin tarjeta de fraude.
# Solo para limpiar una llamada de QA que haya quedado pendiente:
adb -s emulator-5556 emu gsm cancel 2025550147
adb -s emulator-5556 emu gsm cancel 2025550148
```

Sustituye el identificador por el de tu emulador. **Probar alerta de llamada** sigue siendo el prototipo sin Telecom. Para probar la integración usa los comandos GSM y los controles de la pantalla entrante.

La pestaña **Teléfono** de DEBUG reúne el marcador, el rol, la identificación y las detecciones semanales. Durante la llamada, la advertencia sigue visible para los números reportados. El teclado en curso envía tonos DTMF a Telecom y **Agregar** está deshabilitado hasta implementar llamadas simultáneas. RELEASE conserva las tres pestañas y el flujo de identificación de la ETAPA 3.1.

## Comportamiento y límites

- El manifiesto DEBUG declara el servicio protegido por `BIND_INCALL_SERVICE`, la actividad privada de llamada y el marcador `ACTION_DIAL`, con y sin URI `tel:`. RELEASE conserva el comportamiento de la ETAPA 3.1.
- `HestiaInCallService` recibe objetos `Call`, observa su estado y comparte datos transitorios con la actividad. La pantalla entrante reutiliza el composable aprobado y la consulta local de reputación. Las acciones envían `answer`, `reject` o `disconnect` a la llamada seleccionada. La pantalla en curso muestra el estado que informa Telecom. El micrófono usa `setMuted` y el altavoz usa `CallEndpoint` desde Android 14 o el enrutamiento anterior en versiones compatibles.
- El servicio deja el timbre a Telecom. Para abrir la pantalla usa el enlace de Telecom y publica una notificación de llamada con acceso a la actividad y a las acciones. Android controla si presenta automáticamente la interfaz o un aviso y si puede cubrir la pantalla bloqueada. Cuando termina la llamada, el servicio cancela el aviso y la actividad se cierra.
- El aviso de reputación de `CallScreeningService` se omite en DEBUG mientras Hestia posee `ROLE_DIALER`; el resto del flujo de identificación se conserva. Al devolver el rol de teléfono, vuelve el aviso independiente si Hestia sigue siendo la aplicación de identificación.
- El marcador confirma la acción del usuario y utiliza `TelecomManager.placeCall` con el permiso de llamadas. Android reserva las llamadas de emergencia al teléfono preinstalado. No se probaron números de emergencia. La implementación mínima todavía requiere validación con doble SIM, llamadas en espera, vídeo, Bluetooth, DND, pantalla con PIN y teléfono físico.
- El rol se activa por consentimiento explícito. La app no graba audio ni guarda números de esta pantalla. No requiere cuentas, recursos pagos ni permisos de superposición.

## Validación en Pixel 10, API 37

| Prueba | Resultado observado |
| --- | --- |
| `2025550147` | Hestia abrió su pantalla con la advertencia. En llamadas separadas se comprobó contestar y colgar, y rechazar antes de contestar. Telecom registró `ACTIVE`, desconexión `LOCAL` y rechazo `REJECTED`. |
| `2025550148` | Hestia abrió la pantalla sin tarjeta de fraude. Se comprobó contestar, colgar y rechazar mediante llamadas GSM separadas. |
| Micrófono y altavoz | El estado cambió de micrófono activo a silenciado y volvió a activo. Telecom informó altavoz activo y el control quedó deshabilitado al no ofrecer una transición a otra salida. El cambio efectivo de salida queda pendiente. |
| Segundo plano y bloqueo | La pantalla se abrió desde Inicio, con el emulador dormido y con el bloqueo por deslizamiento activo. En la prueba de bloqueo, Android informó `showing=true` y `occluded=true` al mostrar la llamada. |
| Marcador | `ACTION_DIAL` precargó un número sintético. `Llamar` produjo `DIALING` y `Colgar` produjo desconexión `LOCAL`. Esta prueba acredita el inicio y la cancelación de la llamada saliente en el emulador. |
| Restauración y ETAPA 3.1 | Se devolvió `ROLE_DIALER` a Teléfono y se verificó que su actividad entrante volvió a mostrarse. Hestia conservó el rol de identificación: ningún aviso independiente nuevo mientras era teléfono predeterminado, un aviso para `0147` tras restaurar Teléfono y ninguno para `0148`. |
| Gradle y alcance | `assembleDebug`, `testDebugUnitTest` (91 pruebas), `lintDebug` y `assembleRelease` aprobaron. El manifiesto fusionado de RELEASE excluye `InCallService`, el marcador y los permisos de teléfono y pantalla completa introducidos en DEBUG. |

Documentación de Android sobre el [teléfono predeterminado](https://developer.android.com/develop/connectivity/telecom/dialer-app) e [InCallService](https://developer.android.com/reference/android/telecom/InCallService).
