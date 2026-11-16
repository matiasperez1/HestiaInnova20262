# ETAPA 2A — Cuenta Google y Supabase Auth

## Estado y alcance

Implementación de cuenta separada de Gmail: Credential Manager obtiene un ID token Google; Supabase Auth lo intercambia por una sesión. Se usa su API REST documentada con OkHttp, sin incorporar módulos de base de datos, SDK de Gmail ni actualizar Kotlin. No hay sincronización de contactos/lecciones, tablas propias, permisos de ejecución ni protección activa por iniciar sesión.

Dependencias de ejecución añadidas: Credential Manager y proveedor Play Services 1.5.0, Google ID 1.1.1 y OkHttp 4.12.0. Dependencias exclusivas de pruebas: coroutines-test 1.8.1, MockWebServer 4.12.0 y JSON 20240303. Se conservan AGP 9.5.1, Gradle 9.6.0, JDK 25, Kotlin, compileSdk/targetSdk 37 y minSdk 29.

La compilación sin configuración es intencional: permite usar Hestia sin cuenta y muestra el acceso no disponible. No contiene credenciales ficticias. La autenticación real solo puede validarse después de configurar los proyectos y utilizar una cuenta autorizada; las pruebas locales no la acreditan.

## 1. Crear Supabase (propietario)

1. Entrar en https://supabase.com/dashboard con la cuenta del responsable del equipo. Crear un proyecto de desarrollo llamado Hestia en una organización propia, sin contratar un plan de pago. Elegir una región disponible próxima a Chile y guardar la contraseña de base de datos en el gestor privado del equipo.
2. Esperar a que el proyecto esté disponible. Anotar la URL HTTPS del proyecto y su **publishable key** (`sb_publishable_…`) desde la configuración de API. Son identificadores públicos de cliente; el APK los contiene y no son una barrera de autorización.
3. No copiar claves `service_role`, `sb_secret_…`, contraseñas ni tokens administrativos al repositorio o chat. Esta implementación solo admite la nueva publishable key; no utiliza la clave anon heredada.
4. No crear tablas públicas para esta entrega. Los usuarios se gestionan en Authentication. Cuando se añadan tablas propias, habilitar RLS y políticas antes de exponerlas.

## 2. Crear Google Cloud (propietario)

1. Entrar en https://console.cloud.google.com y crear un proyecto de desarrollo Hestia con una cuenta Gmail personal del responsable. Registrar quién administra ambos proyectos y añadir miembros del equipo solo si corresponde.
2. Configurar Google Auth Platform / pantalla de consentimiento: nombre Hestia, correo de soporte del equipo, audiencia externa y publicación en modo de prueba. Registrar las cuentas Gmail autorizadas cuando la consola permita configurar usuarios de prueba. No publicar la aplicación.
3. Solicitar únicamente identidad básica (`openid`, correo y perfil); **no habilitar Gmail API ni pedir scopes Gmail**. No se requiere Firebase ni `google-services.json`.
4. Crear un cliente OAuth de tipo **Aplicación web** para el intercambio con Supabase. Guardar su client ID; su client secret debe configurarse únicamente en el proveedor Google de Supabase, nunca en Android. Registrar como URI de redirección autorizada la URL de callback que muestra Supabase, normalmente `https://<project-ref>.supabase.co/auth/v1/callback`. El flujo Android de esta etapa intercambia ID token directamente, sin deep link.
5. Obtener la huella de la firma de depuración del equipo:

```powershell
.\gradlew.bat signingReport
```

6. Crear un cliente OAuth de tipo **Android** en el mismo proyecto Google, con package `cl.grupo28.hestia` y SHA-1 de esa firma. Registrar otro cliente Android para cada firma de depuración distinta que vaya a probarse. La firma de distribución requerirá su configuración propia en una etapa posterior.
7. En Supabase, Authentication → Sign In / Providers → Google, habilitar el proveedor y registrar el client ID web y su client secret. Mantener activada la validación de nonce; no usar la opción de omitirla. Guardar los cambios. No activar otros proveedores para esta entrega.

## 3. Configurar Android

Copiar `secrets.properties.example` a `secrets.properties` en la raíz. Este último está ignorado por Git. Completar únicamente:

```properties
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_PUBLISHABLE_KEY=<publishable key del proyecto>
GOOGLE_WEB_CLIENT_ID=<client ID web>.apps.googleusercontent.com
```

Los valores también pueden suministrarse como variables de entorno del proceso de compilación, que tienen prioridad. No modificar variables globales de Windows. Gradle rechaza claves administrativas y destinos que no sean HTTPS antes de generar el APK. Reconstruir e instalar el APK después de cambiar la configuración; no es una opción editable desde la app. No enviar client secret ni contraseñas para realizar esta configuración.

Usar JDK 25 de Android Studio como en README. Si el entorno encuentra un Java antiguo, cambiar JAVA_HOME solo en la terminal actual. No guardar rutas absolutas de la computadora en archivos compartidos.

## 4. Comportamiento y seguridad

- Onboarding permite omitir la cuenta; Ajustes permite iniciar sesión después. No se repite el onboarding al cerrar sesión.
- Selector explícito de Google mediante Credential Manager; nonce aleatorio por intento, hash SHA-256 para Google y valor original para Supabase. Solo Supabase valida y publica la identidad; no se confía en decodificar localmente el ID token.
- La sesión completa se cifra mediante AES-GCM y una clave no exportable de Android Keystore. El archivo se escribe atómicamente en `noBackupFilesDir`, excluido por Android de copias de seguridad y transferencia; no se usa DataStore para tokens. Un archivo alterado o una clave perdida descarta la sesión y exige autenticar nuevamente.
- Restauración y verificación mientras la app está en primer plano; renovación al aproximarse la expiración. No se ejecuta un servicio en segundo plano. Las operaciones de sesión se serializan para no reutilizar simultáneamente un refresh token rotado.
- Sin conexión puede mostrarse la identidad previamente guardada como **pendiente de verificar**, nunca como nueva conexión confirmada. El botón Verificar cuenta permite reintentar explícitamente. Una revocación se detecta cuando el servidor rechaza la validación o renovación; no se promete detección instantánea de un JWT ya emitido.
- Cerrar sesión borra primero las credenciales locales e intenta revocar la sesión de este dispositivo (`scope=local`), sin cerrar otros teléfonos. Si falla la red se informa que el cierre remoto no pudo confirmarse. Los access tokens emitidos pueden seguir válidos hasta su expiración; no se promete revocación instantánea.
- Cerrar sesión intenta limpiar el estado del selector Google para volver a elegir cuenta. No elimina la cuenta Google del teléfono ni revoca automáticamente su consentimiento.
- Contactos, preferencias y progreso permanecen asociados al teléfono, no a una cuenta. Se informa en la confirmación; no se suben ni borran al cambiar de cuenta.
- Sin logs HTTP, tokens en UI ni mensajes crudos de errores del proveedor. Solo se admiten destinos HTTPS y publishable keys. No hay tablas de aplicación ni políticas RLS ficticias.

## 5. Validación real pendiente del propietario

Con las consolas configuradas, instalar el APK reconstruido en un dispositivo/emulador con Google Play Services y una cuenta Gmail de prueba. Iniciar sesión desde onboarding y Ajustes, confirmar el usuario en Supabase Authentication, cerrar/abrir la app y verificar restauración. Cancelar el selector y probar ausencia de cuenta, red caída, cierre local sin red, cambio de cuenta, expiración y revocación de sesión desde Supabase. Registrar resultados sin capturar tokens ni correos personales en artefactos compartidos.

Verificar texto muy grande y TalkBack: etiqueta del botón, correo anunciado, feedback de éxito/error y confirmación de cierre. La apariencia conserva el tema cálido; el selector nativo de Google pertenece al sistema. No declarar autenticación real ni accesibilidad auditiva validadas sin efectuar estas comprobaciones.

## Fuentes técnicas

- https://developer.android.com/identity/sign-in/credential-manager-siwg
- https://supabase.com/docs/guides/auth/social-sign-in/auth-google?platform=android
- https://github.com/supabase/auth/blob/master/openapi.yaml
- https://supabase.com/docs/guides/auth/sessions
- https://developer.android.com/identity/data/autobackup


## Archivos y pruebas

- `app/build.gradle.kts`, `gradle/libs.versions.toml`, `secrets.properties.example`: configuración pública y dependencias mínimas de autenticación/pruebas.
- `AndroidManifest.xml`: únicamente permiso INTERNET adicional.
- `domain/auth` y `data/auth`: contratos, API Auth, coordinación serializada y almacenamiento cifrado.
- `features/account`: selector Google, estado de cuenta, confirmación y recuperación.
- MainActivity, navegación, onboarding, Ajustes y SecondaryButton: conexión del flujo y estados habilitados sin cambiar las tres secciones.
- Pruebas de repositorio, API HTTP y ViewModel en JVM; cifrado con Keystore y controles de cuenta en Android. Los datos de prueba usan dominios `.invalid` y no se incluyen en producción.
- AGENTS.md, MVP_SPEC.md, CODEX_PROMPTS.md, README.md y este documento: alcance 2A y separación de 2B.

Comandos de validación: `assembleDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest`, `lintDebug`. Además de las pruebas nuevas, se conserva el recorrido instrumental de onboarding, lecciones, ajustes y contactos. Las pruebas de cancelación y ausencia de cuenta utilizan el límite inyectable del selector; no equivalen a probar Google Play Services con una cuenta real.


## Resultado de validación local

- `assembleDebug`: aprobado; APK en `app/build/outputs/apk/debug/app-debug.apk`, sin configuración externa incluida.
- `testDebugUnitTest`: 26 pruebas aprobadas, cero fallos.
- `connectedDebugAndroidTest`: 6 pruebas aprobadas, cero fallos, Pixel 10 emulado API 37. Incluye las tres pruebas de regresión anteriores, dos de controles de cuenta y una de cifrado/tamper con Keystore real del emulador.
- `lintDebug`: cero errores y 24 advertencias: 10 GradleDependency, 5 NewerVersionAvailable, 7 UnusedResources, 1 ObsoleteLintCustomCheck de Lifecycle y 1 RedundantLabel. No se actualizan versiones para ocultarlas. El ejecutor también advierte de `sun.misc.Unsafe` en protobuf y del empaquetado de librerías nativas sin stripping; no son errores de compilación.
- Guardia de configuración Gradle: se suministró una clave administrativa ficticia únicamente a la sesión de prueba; Gradle la rechazó antes de generar un APK. No se utilizaron credenciales reales ni se alteró el APK validado.
- `git diff --check`: sin errores de espacios.

Las primeras ejecuciones detectaron una referencia URI del script Gradle y un selector de prueba de Compose incompatibles; ambos se corrigieron y la ejecución final completa pasó. Los logs locales están en `build/stage2a-final.log`; los informes de JUnit, instrumentación y lint permanecen en `app/build`.

Pendiente: crear/configurar los dos proyectos externos, probar una cuenta Google autorizada y corroborar su usuario/sesión en Supabase; cancelación real del selector, ausencia real de cuentas y revocación real; auditoría auditiva con TalkBack y dispositivos físicos Android 10+. La ETAPA 2A no se declara validada de extremo a extremo mientras falten esas pruebas. No se realizaron commits, push ni publicación.


## Actualización al iniciar 2B

Los proyectos de Supabase y Google Auth ya se configuraron. Se comprobó que Supabase responde con Google habilitado, que la configuración compilada coincide con la local y que Hestia muestra una sesión restaurada/verificada tras apertura en frío. Esta evidencia reemplaza el pendiente de crear proyectos de las notas históricas. Cierre, revocación, cancelación reales y auditoría completa siguen separados de esa comprobación. Gmail tiene autorización y configuración independientes, documentadas en ETAPA_2B_GMAIL.md.
