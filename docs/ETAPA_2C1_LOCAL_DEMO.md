# Hestia — ETAPA 2C.1: DEMO LOCAL sin servicios remotos

## Alcance y estado

Esta demostración complementa 2C y conserva su implementación de producción. Utiliza datos **exclusivamente sintéticos**, identificados como DEMO LOCAL; no prueba acceso a Gmail real ni eficacia frente a estafas reales. No hay despliegue, facturación habilitada ni recursos creados en Google Cloud. No se ejecutaron scripts de deploy ni parámetros -Execute. No se modificaron OAuth, secrets.properties, la carpeta del proyecto o el repositorio.

No necesita cuentas, claves Google, Supabase, Firebase, Docker ni software nuevo. Esta computadora ya dispone de Node 24 y PostgreSQL 18; se reutilizan. El launcher exige PostgreSQL en el PATH de la terminal y se detiene con una explicación si no lo encuentra; no instala nada automáticamente. Se eligió PostgreSQL local tras inspeccionar el equipo, en vez de añadir otra base de datos o un adaptador JSON innecesario para este entorno.

## Arquitectura local y diferencias con producción

```text
UI debug / CLI demo:emit
  → API en 127.0.0.1
  → identidad demo emitida por el servidor
  → fixture MIME y sobre con formato Gmail Push
  → adaptador LocalPubSub / cola PostgreSQL persistente
  → Worker Node separado
  → MISMO GmailService de producción
  → LocalDemoMailbox → MISMO parser MIME y motor determinista
  → MISMO PgStore, deduplicación y cursor
  → LocalDemoNotificationProvider → bandeja simulada
  → AutomaticApi / AutomaticGateway → mismo AutomaticViewModel
  → resumen y detalle de alerta en Hestia
```

| Función | LOCAL_DEMO | PRODUCTION conservada |
| --- | --- | --- |
| Identidad | Dos personas sintéticas elegibles localmente, bearer opaco con hash en PostgreSQL | JWT verificado por Supabase Auth |
| Correo | Fixtures predefinidos, sin aceptar cuerpos arbitrarios | Gmail readonly + perfil verificado |
| Entrega | Cola SQL local, claims, lease y reintentos | Pub/Sub OIDC + Cloud Run privado |
| Acceso | Sin OAuth, access tokens Google ni refresh tokens | Código offline de un uso, refresh cifrado KMS |
| Cifrado | No hay credenciales que cifrar; noDemoCipher rechaza su uso | Cloud KMS AES-256-GCM/AAD del propietario |
| Datos | Clúster PostgreSQL aislado y datos sintéticos en disco | Supabase PostgreSQL con RLS |
| Aviso HIGH | Registro único en bandeja local, consultado en primer plano | Outbox y FCM genérico |
| UI | Controles y transporte HTTP solo en source set debug | Source set release sin controles ni HTTP local |

La única extensión del servicio de negocio es un proveedor opcional de acceso para procesar el buzón local **sin credenciales**. Producción conserva su comportamiento predeterminado de refresh/KMS; no se reemplaza GoogleRest ni los adaptadores Cloud. El motor no se duplica ni se ajusta artificialmente para los escenarios.

No se implementó cifrado AES local porque la demo no genera ni almacena credenciales. Sus mensajes MIME son fixtures sintéticos locales y no son contenido privado del usuario. El almacenamiento local de esta demo **no equivale** a la gestión de claves/seguridad de producción y no debe usarse con correos o tokens reales.

## Configuración y ejecución en Windows PowerShell

Desde una terminal en la raíz del proyecto:

```powershell
node --version
psql --version
Get-Command pg_ctl
Set-Location backend
$env:LOCAL_DEMO = 'true'
npm.cmd ci --ignore-scripts
npm.cmd run demo:start
```

`npm ci` instala únicamente las dependencias ya fijadas por package-lock.json; en esta entrega no se añadieron bibliotecas. Si ya están instaladas, omitir ese paso. Usar npm.cmd evita depender de la política de ejecución de npm.ps1 en Windows.

`demo:start` compila TypeScript, prepara un clúster PostgreSQL aislado, crea la base **hestia_demo** solo en ese clúster y arranca API + Worker como procesos separados. Mensajes esperados: `DEMO LOCAL API: http://127.0.0.1:8787` y `DEMO LOCAL Worker: ready`. El Worker muestra IDs sintéticos procesados, duplicados e intentos; no contenidos ni tokens. Mantener esa terminal abierta; Ctrl+C detiene los servicios. Los datos permanecen. Un PostgreSQL preparado previamente puede continuar activo en el computador; no es un servicio externo ni contratado.

La configuración compartida es `backend/demo.config.json`: apiPort **8787**, databasePort **55433**, workerIntervalMs **1000**. Android obtiene el puerto de ese archivo durante la compilación; el endpoint debug generado es `http://10.0.2.2:8787`. Si se cambia apiPort, reiniciar backend y recompilar Android. No editar direcciones en el motor o ViewModel.

Si PostgreSQL no está en PATH pero ya está instalado, agregar su carpeta bin **solo a esa terminal**, sin modificar variables globales:

```powershell
$pgBin = Read-Host 'Carpeta bin de tu PostgreSQL local instalado'
$env:PATH = "$pgBin;$env:PATH"
```

No se usa DATABASE_URL de producción, ni se lee .env de servicios remotos. El launcher verifica que el puerto PostgreSQL pertenece exactamente al directorio local esperado antes de crear/modificar su base; rechaza un clúster ajeno.

Para procesos separados, desde backend en terminales con LOCAL_DEMO=true:

```powershell
npm.cmd run demo:prepare
# Terminal 2, después de prepare:
npm.cmd run demo:api
# Terminal 3, después de prepare:
npm.cmd run demo:worker
```

No ejecutar demo:start simultáneamente con esos procesos en el mismo puerto. Se pueden ejecutar varios workers para probar claims, pero no es necesario para la presentación.

## Android y permiso local

En otra terminal, desde la raíz del proyecto:

```powershell
$env:JAVA_HOME = Read-Host 'Carpeta jbr de Android Studio (JDK 25)'
$env:LOCAL_DEMO = 'true'
.\gradlew.bat assembleDebug
```

LOCAL_DEMO está desactivado por defecto. La variable de compilación debug true permite arrancar directamente sin verificar la cuenta real por internet; también puede activarse desde Resumen → Revisar correos → Activar DEMO LOCAL. La selección de modo/persona se guarda en un archivo propio noBackupFilesDir, separado de la sesión Supabase y Gmail 2B. Una compilación con LOCAL_DEMO=true vuelve a iniciar offline al arrancar un proceso nuevo, aunque se haya elegido temporalmente el modo real.

Actualizar la app con Android Studio o `adb install -r app/build/outputs/apk/debug/app-debug.apk`, seleccionando siempre el emulador correcto. **No desinstalar, borrar datos ni ejecutar los tests contra el dispositivo con sesión real.** Completar el onboarding si el emulador de QA es nuevo; los pasos reales pueden omitirse y no simulan permisos.

Android 17/API 37 exige **ACCESS_LOCAL_NETWORK** para acceder al computador. Solo se declara en el manifiesto debug. En el panel DEMO LOCAL se explica y aparece **Permitir conexión local**, que abre el diálogo oficial. Si se rechaza/revoca, el cliente no abre sockets y pide el permiso; no inventa conexión. Android 10–16 no necesitan ese permiso para esta conexión. No se añadió a release, ni se cambió targetSdk/minSdk. [Documentación oficial](https://developer.android.com/privacy-and-security/local-network-permission).

El HTTP sin TLS se permite solo en debug y solo para 10.0.2.2/127.0.0.1/localhost. El cliente local rechaza otros hosts, HTTPS de otro entorno, proxies y redirecciones. Release mantiene usesCleartextTraffic=false y no incluye la configuración local ni LocalDemoGateway/los controles de simulación. No se cambian variables de Windows globales.

Mientras DEMO LOCAL está activo, se pausa la verificación de cuenta en primer plano, no se inicializa/restaura Firebase y no se muestran ni envían resultados reales. Los receptores FCM también rechazan este modo. Al volver al modo real se explica que puede reanudarse el acceso a internet. Empezar la presentación con la compilación offline indicada y con las revisiones reales detenidas. Las lecciones se pueden leer sin abrir sus fuentes externas durante la demo.

## Presentación desde la interfaz

1. Iniciar backend y emulador, abrir Hestia con la compilación offline y entrar a **Resumen → Revisar correos**.
2. Confirmar **DEMO LOCAL**, conceder el permiso local si corresponde y comprobar Backend conectado / Worker en ejecución. Esos estados proceden de respuestas reales locales y heartbeat del Worker.
3. Elegir Ana o Bruno, ambas identidades sintéticas. No son usuarios Supabase. Si se desea una presentación limpia, pulsar **Limpiar datos demo** y confirmar: solo borra la identidad seleccionada.
4. Pulsar **Simular llegada de correo**, seleccionar un escenario y **Simular escenario**. La API encola; el Worker procesa sin que Android ejecute el análisis. La UI consulta resultados cada dos segundos únicamente mientras está en primer plano.
5. Revisar eventos procesados/pendientes, última simulación, correos analizados/sospechosos y avisos generados. Abrir **Ver aviso simulado** o **Revisar alerta**: el detalle tiene remitente example.invalid, etiqueta DEMO LOCAL y razones del motor real.
6. Pulsar **Repetir último evento**: crece el registro de entregas/duplicados, pero no correos únicos ni avisos. MEDIUM aparece como alerta en app sin aviso inmediato; LOW incrementa solo el conteo.
7. Volver a Resumen: cifras exclusivamente demo; no se suman ni se sustituyen los archivos de revisión manual real. Aprender y Familia mantienen las mismas pestañas.
8. Apagar API/Worker con Ctrl+C, volver a consultar: Backend sin conexión confirmada y resultados anteriores identificados como guardados. Reiniciar demo:start y verificar que se recuperan los resultados.
9. Para demostrar reintentos, marcar **Provocar un fallo temporal y reintentar** al generar un correo: falla la lectura del mensaje dentro del mismo servicio de negocio, conserva cursor y resultado anterior, y la cola reintenta después de dos segundos.

No se implementa un servicio Android permanente ni recepción push. Con Hestia cerrada, el Worker del computador puede continuar, pero el teléfono no garantiza avisos: la bandeja se consulta al volver a abrirlo. No se requieren permisos de notificaciones para esta bandeja; no es FCM simulado como si fuera real.

## Comandos CLI y escenarios

En otra terminal situada en backend y con LOCAL_DEMO=true:

```powershell
npm.cmd run demo:emit -- --scenario high-risk
npm.cmd run demo:emit -- --scenario legitimate
npm.cmd run demo:emit -- --scenario urgent-payment --retry-once
npm.cmd run demo:emit -- --repeat
npm.cmd run demo:emit -- --persona bob --scenario reply-mismatch
npm.cmd run demo:emit -- --persona bob --clear --confirm
```

Sin --persona se usa **alice / Ana**. La segunda identidad es **bob / Bruno**. El servidor solo acepta esos nombres y emite un bearer demo opaco; no confía en user_id o correo enviados por el cliente. Es conveniencia de desarrollo en localhost, **no autenticación segura para usuarios reales**. Tokens demo solo en memoria Android/CLI, hash en PostgreSQL y vencimiento de doce horas. No son JWT Supabase ni tokens Google.

| scenario | Contenido sintético | Resultado del motor actual |
| --- | --- | --- |
| legitimate | Información institucional de prueba | LOW |
| high-risk | Solicita clave, urgencia, HTTP y banco de prueba | HIGH + aviso simulado |
| account-block | Amenaza de bloqueo y solicitud de código en HTTP | HIGH + aviso simulado |
| shortened-link | Alias corto reservado, urgencia y HTTP | MEDIUM, sin aviso |
| reply-mismatch | Solicitud de código y Reply-To de otro dominio reservado | MEDIUM, sin aviso |
| urgent-payment | Pago para liberar premio, con urgencia | MEDIUM, sin aviso |
| isolated-signal | Reunión familiar legítima con «urgente» | LOW: una señal aislada no supera el umbral |
| limited-content | Adjunto sintético sin texto descargable | LOW con contenido limitado, sin autenticidad garantizada |

Todos los remitentes y URLs de la demo usan example.invalid o subdominios reservados. No se abren enlaces. El escenario shortened-link no transforma el motor para reconocer el dominio ficticio como bit.ly: sus razones reales son urgencia/HTTP. Los acortadores públicos conocidos siguen cubiertos por los fixtures compartidos de producción; no se introduce un resultado ficticio para la presentación.

## Persistencia, aislamiento y limpieza

Datos y logs: `backend/.local-demo/`, ignorados por Git. El clúster se almacena en `.local-demo/postgres`; no es el servicio PostgreSQL global ni Supabase remoto. Usa loopback y autenticación trust exclusivamente para estos datos sintéticos, por lo que no debe exponerse por LAN, túneles o internet.

Además del esquema de aplicación reutilizado, las tablas del esquema local `demo` guardan identidades, sesiones demo con hash, fixtures MIME, cola y estados de entrega, heartbeat y bandeja de avisos. No se envían estas tablas/migraciones a Supabase. Una restricción única de evento evita doble resultado y la bandeja tiene evento único. LOW no guarda asunto/remitente/score/razones; bodies MIME sintéticos solo se conservan en el buzón de pruebas local para reproducir entregas.

La cola se confirma después del servicio compartido. Una entrega pendiente sobrevive al reinicio; un claim interrumpido puede esperar hasta 120 segundos antes de recuperarse. Errores reintentan cada dos segundos y no adelantan cursor. Los fallos persistentes permanecen visibles en pendientes/reintentos. Para volúmenes grandes no se promete throughput de producción.

La limpieza recomendada es la UI o --clear --confirm por persona. Borra únicamente sus mensajes/cola/eventos/avisos sintéticos. Conserva la otra persona, preferencias Android, contactos, lecciones, sesión real y revisión Gmail manual. No borrar carpetas globales PostgreSQL, datos de la app o snapshots del emulador. No se generan claves locales: si se desea reiniciar todo el clúster demo, primero detener sus procesos e inspeccionar el directorio exacto ignorado; no se proporciona un borrado recursivo automático.

## Pruebas y restricciones

```powershell
# backend, después de demo:prepare, LOCAL_DEMO=true
npm.cmd test
# Android, raíz del proyecto; usar emulador QA separado
$env:LOCAL_DEMO = 'false'
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' '-Pandroid.testInstrumentationRunnerArguments.localDemoIntegration=true'
.\gradlew.bat lintDebug
.\gradlew.bat assembleRelease
```

La prueba instrumental local necesita API/Worker encendidos; es opt-in mediante localDemoIntegration=true y usa Bruno, exclusivamente sintético. El harness concede ACCESS_LOCAL_NETWORK solo en el emulador QA. Restaurar selección real al terminar permite seguir ejecutando los tests anteriores sin cambiar sus expectativas. La compilación de presentación se obtiene después con LOCAL_DEMO=true.

Backend prueba HTTP real local, MIME, motor real, HIGH/MEDIUM/LOW, privacidad de LOW, cola, cursor, repetición, fallo/reintento, cierre y reapertura de conexiones, recuperación de claim expirado, cuentas separadas y limpieza. Crea y elimina una base test separada, verificando primero que pertenece al clúster demo. La prueba PostgreSQL original de producción requiere TEST_DATABASE_URL de otra base sintética inicializada con test/bootstrap.sql y la migración; si falta esa variable se omite explícitamente. No apuntarla a producción. Las pruebas de producción no se sustituyen.

El runtime demo bloquea fetch y sockets externos antes de conectar. Gmail/refresh/watch/KMS/FCM están prohibidos en sus adaptadores y se prueban como errores, no se silencian. El entrypoint de producción rechaza LOCAL_DEMO=true; los entrypoints demo rechazan NODE_ENV=production, K_SERVICE y CLOUD_RUN_JOB. Sin opt-in el demo no arranca. No se emplean credenciales reales, service_role ni llamadas a recursos facturables.

Pendiente para producción: toda la infraestructura Google Cloud, su despliegue y validación Gmail/FCM reales descritos en 2C. Esta entrega no habilita facturación ni comienza ETAPA 3. No hubo commits ni push.
