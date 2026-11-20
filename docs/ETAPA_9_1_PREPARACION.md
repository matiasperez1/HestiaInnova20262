# ETAPA 9.1 · Preparación del Moto G06

19 de noviembre de 2026. Línea base `4d386a6`. Esta preparación verifica instalación y compatibilidad antes del rediseño, la integración de agenda, el transporte LAN y Gmail en segundo plano. El costo de infraestructura fue **CLP $0**.

## Resultados comprobados

| Comprobación | Resultado |
| --- | --- |
| ADB y equipo | Motorola `moto g06`, Android 15, API 35, `arm64-v8a`. ADB autorizado por USB. Google Play Services presente y SIM en estado `LOADED`. El segundo teléfono queda para la prueba manual de SIM. |
| SDK y herramientas | Platform Tools 37.0.1. Plataformas 35 y 37 instaladas. Build Tools 35.0.1 y 36.0.0. Disponibles `adb`, `aapt`, `apksigner`, emulador, JDK 25.0.4 de Android Studio, Gradle 9.6.0, AGP 9.5.1, Node 24.22.1 y PostgreSQL 18.3. La ruta habitual de `sdkmanager.bat` bajo `cmdline-tools/latest` está ausente. El SDK instalado permitió compilar sin conexión. |
| DEBUG en el Motorola | La primera instalación, el arranque en frío, la reinstalación con `adb install -r` y el segundo arranque pasaron. Se conservó el registro de primera instalación. El APK usa `cl.grupo28.hestia`, versión 1.0, `versionCode=1`, `minSdk=29` y `targetSdk=37`, e incluye `arm64-v8a`. El ensayo comienza con una instalación vacía y comprueba el mecanismo de actualización. |
| Roles y permisos consultados en el equipo | Google Teléfono conserva `ROLE_DIALER`. `ROLE_CALL_SCREENING` aparece sin titular seleccionado. El gestor de paquetes resuelve la actividad `ACTION_DIAL`, `HestiaInCallService` y `HestiaCallScreeningService`. `CALL_PHONE` y `POST_NOTIFICATIONS` están sin conceder. La declaración de `USE_FULL_SCREEN_INTENT` aparece concedida en el paquete. La presentación efectiva con pantalla bloqueada requiere una llamada de prueba. |
| Compilación y pruebas | `assembleDebug`, `assembleRelease` y `lintDebug` pasaron. Se repitió `testDebugUnitTest` y pasaron 103 casos sin fallos ni omisiones. Lint informó 0 errores y 31 advertencias. |
| RELEASE | El APK compilado conserva R8, tráfico HTTP deshabilitado y el servicio de filtrado. Carece de marca depurable, `InCallService` propio y permiso de red local de DEBUG. El artefacto de la compilación es `app-release-unsigned.apk`. La instalación física de esta subetapa se hizo con DEBUG. |

La instalación y el arranque se comprobaron mediante ADB. Quedan para las subetapas de validación las llamadas SIM, el consentimiento de roles, Gmail real y las pruebas con agenda poblada. [Instalación con ADB](https://developer.android.com/tools/adb)

## Firma e instalación reproducible

- El certificado de `app-debug.apk` coincide con el almacén estándar de depuración de Windows. Su huella pública SHA-1 es `1A:E5:9A:FF:D0:D2:14:54:42:52:0E:6A:D4:E4:9E:FF:22:92:74:E3`. Esta es la huella que corresponde comprobar en el cliente Android de OAuth.
- SHA-256 del APK DEBUG instalado `d122f1db58b6743ae67cc3dd36bfacf5f1ef35d45c34e216ae16a4fa44e33a03`. SHA-256 de RELEASE sin firma `7eb5563455930d29cd270875f5109680bf9d79cceaebd5c1873f41048f4fb8bc`.
- Para futuras actualizaciones sobre el Motorola, mantener paquete, certificado de firma y un `versionCode` igual o superior al instalado. Si Android indica firma incompatible, conservar la instalación y revisar qué certificado firmó cada APK. La firma de distribución de RELEASE requiere su propio registro OAuth y un plan de actualización antes de instalarla sobre el perfil DEBUG. [Firma de aplicaciones Android](https://developer.android.com/studio/publish/app-signing)

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $adb devices -l
$motorola = '<identificador del moto g06 en adb devices -l>'
& $adb -s $motorola shell getprop ro.product.model
.\gradlew.bat --offline assembleDebug
& $adb -s $motorola install -r app/build/outputs/apk/debug/app-debug.apk
& $adb -s $motorola shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n cl.grupo28.hestia/.HestiaLauncherActivity
```

La salida esperada de la última línea incluye `Status: ok` y `cl.grupo28.hestia/.MainActivity`. El identificador selecciona el Motorola aunque haya emuladores activos. Android Studio también puede ejecutar la variante `debug` seleccionando el equipo físico. [Ejecutar en un dispositivo físico](https://developer.android.com/studio/run/device)

## Archivo de distribución

`HeldiaInnova20262.rar` tenía una eliminación local preexistente al comenzar. Se conservó esa eliminación y se leyó únicamente la lista de entradas de la copia en `HEAD`. La copia versionada contiene **16.698 entradas**, incluida una llamada `secrets.properties`. El listado dio cero coincidencias para `google-services.json`, los formatos de claves buscados y archivos `.env` distintos del ejemplo. La revisión se limita a nombres de entradas. La referencia local `origin/main` carece del RAR y `main` está un commit por delante. La publicación del commit requiere revisar el historial a compartir, porque excluir el archivo del árbol de trabajo deja disponible la copia en el commit que lo introdujo.

## Gmail real y OAuth

La configuración local declara la URL y clave pública de Supabase y el identificador web de Google. Quedan por verificar en el proyecto existente la **Gmail API habilitada**, el scope **`gmail.readonly`**, el consentimiento y la cuenta admitida para Testing, y el cliente Android `cl.grupo28.hestia` con la SHA-1 anterior. `HESTIA_API_URL` y la configuración opcional de Firebase están ausentes. La revisión manual y la propuesta con WorkManager usan Gmail API directamente desde Android. [Configuración de revisión manual](ETAPA_2B_GMAIL.md), [Scopes de Gmail](https://developers.google.com/workspace/gmail/api/auth/scopes)

En la prueba manual de 9.5, el usuario abrirá **Resumen → Revisar correos**, iniciará sesión con Google y autorizará Gmail en el flujo oficial del Motorola. La evidencia esperada es perfil verificado y resultado de una revisión de solo lectura. Para transmitir el resultado basta indicar el estado y un conteo, omitiendo direcciones y contenido. La prueba de 9.6 deberá obtener un token nuevo sin actividad visible después de que caduque el anterior. Si Google requiere interacción, el trabajador conservará el cursor hasta que la persona vuelva a autorizar. [Autorización de Google en Android](https://developer.android.com/identity/authorization), [Trabajo periódico Android](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)

## Preparación LAN

Se observó una IPv4 Wi-Fi del Moto G06 y al menos una interfaz activa del notebook en la misma subred. El puerto TCP 8787 observado escucha en `127.0.0.1`. La instancia PostgreSQL de QA observada escucha en loopback en el puerto 55438. El archivo `backend/demo.config.json` configura 8787 para la API y 55433 para PostgreSQL. La coincidencia de subred prepara la prueba. El acceso HTTP de extremo a extremo queda para 9.4.

Para 9.4, elegir explícitamente la IPv4 privada del notebook con `LOCAL_DEMO_LAN_IP`, verificar la instancia PostgreSQL de QA, mantener PostgreSQL en loopback y limitar cualquier regla de Windows para TCP 8787 a la interfaz privada y la subred de ensayo. La app DEBUG requiere adaptar la URL actual `10.0.2.2` y su política de tráfico a ese host explícito. `ACCESS_LOCAL_NETWORK` aplica en equipos Android 17 con `targetSdk` 37. En este Motorola API 35, la ruta LAN usa `INTERNET` y la política de red de la app. El HTTP de demo queda limitado a datos sintéticos. Los números reales controlados requieren un ámbito QA separado, autenticación y transporte protegido. [Permiso Android de red local](https://developer.android.com/privacy-and-security/local-network-permission), [Guía de DEMO LOCAL](GUIA_DEMO_AYUDANTES.md)

## Arquitectura telefónica en Android 15

| Aspecto | Google Teléfono con protección de Hestia | Hestia como teléfono predeterminado |
| --- | --- | --- |
| Rol y contrato | Google Teléfono ocupa `ROLE_DIALER`. El usuario puede conceder `ROLE_CALL_SCREENING` a Hestia para identificación. | El usuario elige Hestia para `ROLE_DIALER`. Android exige `ACTION_DIAL`, teclado y `InCallService` con pantallas entrante y en curso. `ROLE_CALL_SCREENING` se elige por separado. |
| Entrantes y salientes | Google Teléfono presenta la llamada, inicia llamadas y ofrece los controles. Hestia permite las entrantes que recibe para identificación y puede emitir el aviso de riesgo. | Hestia inicia llamadas por Telecom y presenta los estados y controles de las llamadas que el sistema le entrega. Los servicios de telefonía siguen en Android. Android conserva el marcador precargado para llamadas de emergencia. |
| Recientes | El historial está en Google Teléfono. El registro de detecciones de Hestia cubre solo eventos que recibe su servicio. | Hestia puede guardar Recientes propios de manera opcional y acotada para las llamadas que gestiona. La prueba no necesita importar el historial del sistema. |
| Contactos | Hestia puede leerlos después de conceder `READ_CONTACTS`. El marcador mantiene su propia experiencia de agenda. | Misma agenda del sistema para búsqueda y nombres, con permiso opcional. Familia mantiene un registro local separado y una acción explícita para añadir una persona. |
| Android 15 | La presentación de una actividad desde segundo plano depende de Telecom y de las restricciones del sistema. El aviso Android genérico sirve como respaldo. | Hay que comprobar `InCallService` con bloqueo y segundo plano y la disponibilidad de pantalla completa. El teclado y los controles deben respetar los insets de Android 15. |
| RELEASE y presentación | El alcance actual de RELEASE corresponde a protección. La presentación puede mostrar cómo convive con el marcador habitual. | Para ofrecer el reemplazo en RELEASE se deben incorporar los componentes de producto del marcador y validar roles, pérdida de rol, emergencias, permisos, Recientes y privacidad. DEBUG ya ofrece un punto de partida para demostrar el recorrido completo con SIM en el Motorola. |

**Recomendación.** Diseñar Teléfono como una experiencia completa y probar primero el reemplazo mediante el `InCallService` que ya existe en DEBUG. Usar ambos modos en la prueba física. Promover a RELEASE el marcador propio como cambio de producto cuando los controles, el retorno a Google Teléfono, los permisos y la privacidad con bloqueo tengan evidencia física. Las simulaciones y herramientas de LAN quedan en DEBUG. [Contrato del marcador Android](https://developer.android.com/develop/connectivity/telecom/dialer-app), [Servicio de identificación](https://developer.android.com/reference/android/telecom/CallScreeningService), [Restricciones de Android 15](https://developer.android.com/about/versions/15/behavior-changes-15)

### Permisos y acciones manuales del piloto

1. En **Ajustes → Aplicaciones → Aplicaciones predeterminadas**, confirmar Google Teléfono para el escenario de protección y elegir Hestia en **Identificación de llamadas y spam** si Android lo ofrece. Para el escenario de marcador propio, elegir Hestia en **Aplicación de teléfono**. Registrar qué rol aparece y si el sistema muestra el aviso al llamar desde el segundo teléfono. Volver a Google Teléfono desde el mismo menú al terminar ese escenario.
2. Aceptar `POST_NOTIFICATIONS` desde Hestia si se probarán avisos. En el marcador DEBUG, conceder `CALL_PHONE` al probar la acción de llamada propia. La futura vista de agenda solicitará `READ_CONTACTS` desde Contactos, bajo consentimiento independiente. Sin ese permiso, Telecom puede omitir llamadas guardadas para identificación. Las llamadas que ocultan el número también pueden quedar fuera. [Cobertura del filtrado](https://developer.android.com/reference/android/telecom/CallScreeningService)
3. Registrar el escenario por tipo, por ejemplo **número controlado A**, **guardado**, **bloqueado**, **saliente** o **sin Internet**, y qué control o aviso funcionó. Los números y los datos privados quedan en el teléfono.

## Estado de cierre

El teléfono está preparado para desarrollar y probar el rediseño. Se preservaron los roles, credenciales y cambios de trabajo existentes. La confirmación del proyecto OAuth, el transporte LAN desde Android y los escenarios con SIM quedan en sus subetapas. La comparación de arquitectura queda entregada antes de cualquier cambio de rol en RELEASE. No hubo commits, push ni gastos.
