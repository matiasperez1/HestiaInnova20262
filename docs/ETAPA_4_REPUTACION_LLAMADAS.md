# ETAPA 4 · Reputación compartida de llamadas

En **Teléfono → Reportes compartidos**, Ana y Bruno pueden informar un posible fraude, consultar la reputación y corregir o retirar sus propios reportes. Son las dos identidades sintéticas del backend DEMO LOCAL. El selector de Teléfono es independiente de Gmail y de la cuenta Google.

| Reportes de identidades distintas | Reputación | Aviso de posible fraude |
| --- | --- | --- |
| 0 | Sin reportes | No |
| 1 hasta el umbral menos uno | Pendiente de corroboración | No |
| Umbral o más | Sospechoso | Sí |

El umbral inicial es 2. Para cambiarlo, edita `callReportThreshold` en `backend/demo.config.json`, reinicia la API y vuelve a sincronizar. Con dos identidades disponibles, un umbral superior a 2 deja todos los reportes pendientes. El total expresa cuántas identidades reportaron un número. «Sin reportes» describe la información de la demo y no certifica que una llamada sea segura.

## Datos y privacidad

- PostgreSQL guarda `número, identidad sintética y categoría` en `demo.call_report`. La clave primaria `(number, user_id)` impide votos duplicados, incluso si llegan en paralelo. Guardar otra categoría para el mismo número corrige el reporte existente. Retirar solo afecta a la identidad de la sesión. La API se enlaza a `127.0.0.1` y reutiliza la sesión opaca, la validación de origen local y el clúster existente.
- Se aceptan números de `2025550100` a `2025550199`, reservados como ficticios. Los números `2025550147` y `2025550148` siguen siendo pruebas fijas de la ETAPA 3 y se excluyen de la consulta y reportes comunitarios. Android admite también el prefijo `+1` y el formato con espacios o guiones. [Referencia de NANPA](https://www.nationalnanpa.com/reports/2020_NANPA_Annual_Report.pdf).
- El servidor expone los totales y únicamente la categoría del reporte propio. Hestia guarda en un archivo privado excluido de copias de seguridad solo el umbral, la fecha de sincronización y los totales de números reportados. La categoría propia se mantiene en memoria mientras está abierta la pestaña. Consultar usa la caché y no envía el número al servidor. Los servicios de llamadas no guardan el número recibido ni lo imprimen en logs de Hestia. Los registros semanales conservan solo sus indicadores y fechas.
- La caché se reemplaza completa tras sincronizar. Una falla conserva la versión previa, incluido el momento en que se obtuvo. El teléfono puede seguir mostrando un aviso anterior hasta que recupere la conexión y sincronice. `CallScreeningService` e `InCallService` consultan esa misma fuente local, que incluye los números fijos de la ETAPA 3. Con Hestia como teléfono predeterminado se usa la advertencia central existente. Si Hestia conserva solo el rol de identificación, usa su aviso habitual según los permisos de Android. Las llamadas siempre se permiten.
- La pestaña, el cliente local y la caché compartida se incluyen solo en DEBUG. RELEASE conserva su flujo anterior y su fuente de reputación vacía. Las sesiones sintéticas están diseñadas para localhost y la demo no incluye moderación ni identidades de producción.

## Preparar el Pixel 10 (API 37)

En una terminal PowerShell en la raíz del repositorio, prepara la base y deja la API abierta. Usa el PostgreSQL que ya está instalado y en `PATH`. Si la API demo anterior usa `8787`, detenla desde su terminal antes de iniciar esta versión.

```powershell
Set-Location backend
$env:LOCAL_DEMO = 'true'
npm.cmd run demo:prepare
npm.cmd run demo:api
```

`demo:prepare` compila y agrega la tabla de la etapa al clúster local existente. Para probar solo llamadas basta la API. En otra terminal, desde la raíz del repositorio, compila e instala DEBUG en el emulador de pruebas sin borrar sus datos. Elige el identificador correcto de `adb devices -l`.

```powershell
.\gradlew.bat assembleDebug
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
$emulador = 'emulator-5556'
& $adb -s $emulador install -r app/build/outputs/apk/debug/app-debug.apk
```

En Hestia, entra a **Teléfono → Usar Hestia como teléfono** y confirma en el diálogo de Android. En **Reportes compartidos**, pulsa **Permitir conexión local** si aparece. Android 17 requiere ese permiso y el emulador llega a la API a través de `10.0.2.2:8787`. [Permiso de red local](https://developer.android.com/privacy-and-security/local-network-permission), [Red del emulador](https://developer.android.com/studio/run/emulator-networking-address).

## Probar los reportes y las llamadas

1. En **Teléfono → Reportes compartidos**, selecciona **Ana**. Si el número `2025550199` ya se utilizó en una demostración, retira su reporte propio con Ana y con Bruno para empezar desde cero.
2. Con Ana, selecciona **Reportar un número**, ingresa `2025550199`, elige una categoría y pulsa **Guardar reporte** para confirmar el consentimiento. En **Consultar reputación**, escribe el mismo número. Debe decir **Pendiente de corroboración · 1 reporte**. Guardarlo de nuevo con Ana mantiene el total en uno.
3. Cambia a **Bruno**. Al actualizarse la pestaña, consulta el número y verifica que ve un reporte. En **Mis reportes**, Bruno no verá una entrada propia. Reporta `2025550199` con Bruno y vuelve a consultar. Debe decir **Sospechoso · 2 reportes independientes**. **Sincronizar reputación** actualiza también la fecha indicada.
4. Ejecuta cada comando por separado. Rechaza o termina la llamada antes de ejecutar el siguiente. `2025550199` debe mostrar **Posible llamada fraudulenta**. `2025550198` debe aparecer sin tarjeta si nadie lo ha reportado. Los números de la ETAPA 3 se pueden usar para comprobar que siguen funcionando de forma independiente.

```powershell
& $adb -s $emulador emu gsm call 2025550199
& $adb -s $emulador emu gsm call 2025550198
& $adb -s $emulador emu gsm call 2025550147
& $adb -s $emulador emu gsm call 2025550148
```

5. Para probar sin conexión, detén la API con Ctrl+C y selecciona **Sincronizar reputación**. Debe fallar y conservar la última fecha. Vuelve a llamar desde `2025550199`: la advertencia sigue visible con la caché anterior. Reinicia con `npm.cmd run demo:api` en la terminal del backend.
6. Con Bruno, abre **Mis reportes → Retirar reporte de 2025550199 → Retirar**. Después de la sincronización, `2025550199` vuelve a **Pendiente de corroboración · 1 reporte** y una nueva llamada no muestra tarjeta. Ana puede corregir su categoría desde **Mis reportes → Corregir reporte de 2025550199**, o retirar su reporte para volver a **Sin reportes**.
7. Para devolver el rol al marcador de Android, ve a **Teléfono → Cambiar aplicación de teléfono** y selecciona la aplicación original en Ajustes.

## Pruebas automatizadas

```powershell
# En backend, después de demo:prepare y con LOCAL_DEMO=true
npm.cmd test
# En la raíz del repositorio
.\gradlew.bat assembleDebug assembleRelease testDebugUnitTest lintDebug
```

## Validación ejecutada

- Backend con PostgreSQL DEMO LOCAL: 51 pruebas aprobadas. La prueba separada de RLS quedó omitida al faltar `TEST_DATABASE_URL`. La prueba de reportes ejercitó consentimiento, formato sintético, duplicados concurrentes, privacidad por identidad, corrección y retiro.
- Android: aprobaron las cuatro tareas de Gradle, con 93 pruebas unitarias aprobadas. El manifiesto fusionado de RELEASE conserva `CallScreeningService`, sin `InCallService` ni permiso de red local.
- Pixel 10, API 37: la interfaz mostró cero reportes, uno tras el envío de Ana y dos tras el de Bruno. Repetir el envío de Ana mantuvo el total en uno. Telecom mostró la tarjeta con `2025550199` al alcanzar el umbral y la omitió para `2025550198`. Los números fijos `2025550147` y `2025550148` conservaron su comportamiento.
- Con la API detenida y el proceso de Hestia reiniciado, falló la actualización, la consulta siguió mostrando dos reportes y la llamada mantuvo el aviso desde la caché. Tras reactivar la API y retirar el reporte de Bruno, una nueva llamada de `2025550199` quedó sin tarjeta. En Android también se corrigió y retiró el reporte de Ana. La consulta volvió a «Sin reportes» y quedó lista para repetir el flujo.

La prueba física y la revisión específica de esta etapa con TalkBack siguen pendientes.
