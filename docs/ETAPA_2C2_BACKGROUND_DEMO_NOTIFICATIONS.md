# Hestia — ETAPA 2C.2: avisos de DEMO LOCAL en segundo plano

## Estado

Implementación en validación: solo correos sintéticos y exclusivamente debug. El backend local procesa el correo sin Android; el servicio foreground consulta avisos HIGH y crea notificaciones Android locales. No utiliza FCM, Supabase, OAuth ni Gmail real. La validación manual con pantalla bloqueada, Doze profundo, dispositivos físicos, TalkBack y varios casos de permisos/navegación sigue pendiente: no declarar terminada esta etapa hasta completar los criterios solicitados. La prueba instrumental de pantalla apagada no equivale a una prueba manual ni de Doze profundo.

## Arquitectura y cursor

PowerShell `demo:emit` → API/cola PostgreSQL local → worker y motor compartido → `demo.notification` → endpoint incremental → `LocalDemoGateway` → foreground service → NotificationManager. Las identidades y datos de demo son separados del modo real. La UI consulta estado de resumen mientras está visible; el servicio mantiene su consulta de avisos independientemente de la actividad.

`GET /demo/notifications?after=<cursor>&limit=50` requiere sesión demo autenticada. Sin `after` devuelve referencia inicial sin históricos; con cursor devuelve `identity`, `items` (event_id y cursor individual), `nextCursor` y `hasMore`. Cursor opaco ligado al propietario autenticado. Se procesan páginas de hasta 50, guardando el cursor tras cada aviso; reintento desde el último guardado. Las inserciones de aviso y la consulta de la referencia se serializan con advisory lock hasta commit, y se ordenan por secuencia. El launcher añade de forma idempotente `delivery_seq` y su índice a una base **demo local existente** sin borrar datos; una base nueva ya lo incorpora. No se modifica el esquema de Supabase.

Al activar se obtiene una referencia inicial; los eventos previos a esa referencia no producen notificaciones. La activación figura como «Activos» una vez establecida. Una interrupción inesperada conserva el cursor; desactivar y reactivar inicia una nueva referencia y omite el período desactivado. Identificadores estables por identidad/evento actualizan el mismo aviso mientras permanezca visible. NotificationManager y el archivo AtomicFile no comparten transacción: una caída entre notificar y guardar podría volver a alertar si el aviso ya se descartó; no se garantiza exactamente una señal sonora ante ese fallo.

El PendingIntent lleva solo tipo de identidad demo e ID opaco. La navegación exige modo demo y persona coincidente; el gateway valida el detalle mediante la sesión autenticada del backend. El flujo de producción conserva su validación Supabase. Los dos canales de demo son independientes: estado de importancia baja y alertas HIGH de importancia alta. Los avisos no contienen asunto, remitente, cuerpo, claves ni tokens.

## Ejecución

Con Node 24 y PostgreSQL local ya instalados, en PowerShell desde la raíz:

```powershell
Set-Location backend
$env:LOCAL_DEMO = 'true'
npm.cmd run demo:start
```

En otra terminal, con JDK 25 de Android Studio en JAVA_HOME:

```powershell
$env:LOCAL_DEMO = 'true'
.\gradlew.bat assembleDebug
```

Instalar por actualización en el emulador seleccionado, abrir Resumen → Revisar correos → DEMO LOCAL y activar **Avisos en segundo plano**. Conceder notificaciones y, en API 37, conexión local. Esperar estado **Activos**. Desde otra terminal en `backend`:

```powershell
$env:LOCAL_DEMO = 'true'
npm.cmd run demo:emit -- --scenario high-risk
npm.cmd run demo:emit -- --repeat
npm.cmd run demo:emit -- --scenario urgent-payment
npm.cmd run demo:emit -- --scenario legitimate
```

El escenario HIGH genera una notificación del sistema; MEDIUM permanece en la app y LOW solo aumenta el conteo. La interfaz ya no genera correos: se usa el CLI. Detener desde la app con **Detener avisos en segundo plano**. Las simulaciones no equivalen a protección real.

## Permisos y límites

Debug declara FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC y ACCESS_LOCAL_NETWORK. POST_NOTIFICATIONS ya estaba en main y se solicita en Android 13+. Sin permiso o con avisos deshabilitados no se inicia; la interfaz ofrece ajustes. Android 17 necesita permiso de red local. Release no declara el servicio ni los permisos foreground de esta demo.

Android restringe el inicio de foreground services desde segundo plano: activar solo con la app visible. En target 35+ dataSync tiene un máximo acumulado de seis horas por 24 horas; `onTimeout()` detiene el servicio. La pantalla apagada no implica Doze profundo; Doze y ahorro energético pueden aplazar las consultas. El usuario puede detener el servicio desde el administrador de tareas de Android o forzar detención; no se reinicia tras forzar detención ni tras reiniciar el dispositivo. No se solicita exclusión de batería ni se usa specialUse. No hay garantía de recepción inmediata o permanente.

## Validación

Backend: prueba local de 125 avisos con timestamps iguales, tres páginas, reanudación y aislamiento de cursor; suite con PostgreSQL demo ejecutada. La prueba separada PostgreSQL de producción se omite sin TEST_DATABASE_URL. Android: la prueba instrumental opt-in usa exclusivamente Hestia_2B_QA y backend local. Una ejecución comprobó estado foreground, segundo plano, pantalla apagada por comando, llegada HIGH, apertura del detalle y repetición sin segundo registro visible. No constituye prueba manual de pantalla bloqueada, ni validación de Doze o de dispositivo físico. La prueba usa `localDemoIntegration=true`; nunca ejecutar pruebas contra el emulador con sesión real.
