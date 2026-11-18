# Hestia — Entrega ETAPA 2C

## Resultado y límites

Implementación local preparada: Android conserva las tres pestañas y revisión manual; autorización offline independiente, API pública que verifica Supabase JWT y worker privado IAM/OIDC, Gmail watch + Pub/Sub, KMS, motor determinista, SQL/RLS, alertas/resumen y FCM opcional. **No desplegado y no validado de extremo a extremo con Gmail real.** No iniciar ETAPA 3.

Compilación Android aprobada; 75 pruebas JVM, 15 instrumentales y 40 backend aprobadas, sin fallos. La suite backend incluyó la migración sobre PostgreSQL local aislado, sin tests omitidos. Se ejecutaron clean, assembleDebug, testDebugUnitTest, connectedDebugAndroidTest y lintDebug. La última validación completa y los reportes están en los directorios ignorados build/ y app/build/reports/. Lint: cero errores, 27 advertencias (12 versiones Gradle, 6 nuevas versiones disponibles, 7 recursos heredados sin uso, 1 comprobación Lifecycle obsoleta y 1 etiqueta redundante). No actualizar versiones únicamente por esas advertencias.

JDK real de Gradle: JetBrains 25.0.4. Se mantienen AGP 9.5.1, Gradle 9.6.0, configuración Kotlin/Compose existente, compile/target 37 y minSdk 29. Identidad definitiva cl.grupo28.hestia. La APK original se actualizó con install -r; no se desinstaló ni borró la app. Sesión Supabase cifrada presente y restauración verificada en Ajustes, vuelta posterior a Resumen.

## Dependencias incorporadas

Android: firebase-messaging 25.0.1 y kotlinx-coroutines-play-services 1.8.1, registradas en el catálogo. No se añade Google Services Gradle plugin: FirebaseOptions usa configuración pública auténtica opcional y consentimiento previo; sin archivo la app compila. Backend Node 24 LTS con lockfile:

- `@google-cloud/kms`: 6.3.0
- `@google-cloud/pubsub`: 6.4.1
- `firebase-admin`: 14.5.1
- `google-auth-library`: 11.3.4
- `luxon`: 3.7.2
- `pg`: 8.23.1

Herramientas de desarrollo backend: TypeScript y tipos Node/pg/Luxon, versiones exactas en package.json y lockfile. npm audit de la instalación informó cero vulnerabilidades. Las imágenes Docker no se construyeron aquí; validación real de Google/KMS/FCM pendiente.

## Migración y configuración pendiente

`supabase/migrations/202611160001_gmail_automatic.sql`: nuevas tablas de conexión privada, eventos públicos con SELECT propietario por RLS, dispositivos privados y outbox. Sin modificar objetos Gmail preexistentes o tablas de autenticación. Aplicada solo en un clúster local sintético. Revisar esquema remoto y crear login de ejecución dedicado antes de aplicarla en Supabase real.

Pendiente con autorización: habilitar APIs y facturación/cuotas del proyecto existente hestia-514901, topic y publisher Gmail, identidades IAM/OIDC, KMS, versiones Secret Manager, imágenes/Cloud Run, suscripción Pub/Sub y jobs Scheduler; asociar Firebase al proyecto existente y aportar google-services.json auténtico; compilar con HESTIA_API_URL de la API. Los scripts sin -Execute solo muestran plan. No se cambiaron clientes OAuth ni secrets.properties. No se creó configuración ficticia.

Testing OAuth puede exigir reconexión a los siete días; no se cambió a Production. Falta refresh → renovación explícita del permiso por revocación confirmada y nueva autorización, compatible con play-services-auth 21.4.0; no se llama al overload deprecated. Verificación de scopes restringidos y posible CASA deben evaluarse antes de distribuir; no se garantiza aprobación.

## Procedimiento de prueba real

Los pasos completos, comandos, IAM, secretos y recorrido están en [ETAPA_2C_GMAIL_AUTOMATICO.md](ETAPA_2C_GMAIL_AUTOMATICO.md). Después de configurar/desplegar: actualizar APK conservando datos; activar revisión automática y autorizar Gmail; confirmar watch activo; activar avisos; enviar a INBOX un correo de prueba propio **posterior** a la activación con las señales sintéticas indicadas en la guía; cerrar Hestia y verificar evento HIGH único, aviso genérico y detalle solo del propietario. Repetir notificación para comprobar deduplicación; verificar MEDIUM sin push, rechazo de avisos, reconexión y desconexión manteniendo Supabase. No abrir enlaces de la prueba ni usar Gmail real en la suite automatizada.

Código implementado y probado localmente. Infraestructura no desplegada; integración Gmail/FCM real no validada. La única comprobación real de esta entrega fue preservar/restaurar la sesión Supabase ya existente. No hubo commits, push ni ETAPA 3.

## Archivos modificados

- `.gitignore`
- `AGENTS.md`
- `CODEX_PROMPTS.md`
- `MVP_SPEC.md`
- `README.md`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/cl/grupo28/hestia/MainActivity.kt`
- `app/src/main/java/cl/grupo28/hestia/core/navigation/HestiaApp.kt`
- `app/src/main/java/cl/grupo28/hestia/features/account/AccountViewModel.kt`
- `app/src/main/java/cl/grupo28/hestia/features/gmail/GmailScreen.kt`
- `app/src/main/java/cl/grupo28/hestia/features/summary/SummaryScreen.kt`
- `docs/ETAPA_2A_AUTH.md`
- `docs/ETAPA_2B_GMAIL.md`
- `gradle/libs.versions.toml`

## Archivos nuevos

- `app/src/androidTest/java/cl/grupo28/hestia/AutomaticUiTest.kt`
- `app/src/main/java/cl/grupo28/hestia/HestiaApplication.kt`
- `app/src/main/java/cl/grupo28/hestia/data/automatic/AutomaticApi.kt`
- `app/src/main/java/cl/grupo28/hestia/domain/automatic/AutomaticGateway.kt`
- `app/src/main/java/cl/grupo28/hestia/features/automatic/AutomaticAuthorization.kt`
- `app/src/main/java/cl/grupo28/hestia/features/automatic/AutomaticNotifications.kt`
- `app/src/main/java/cl/grupo28/hestia/features/automatic/AutomaticPanel.kt`
- `app/src/main/java/cl/grupo28/hestia/features/automatic/AutomaticViewModel.kt`
- `app/src/main/res/drawable/ic_hestia_notification.xml`
- `app/src/test/java/cl/grupo28/hestia/AutomaticApiTest.kt`
- `app/src/test/java/cl/grupo28/hestia/AutomaticViewModelTest.kt`
- `app/src/test/java/cl/grupo28/hestia/SharedRiskFixturesTest.kt`
- `backend/.dockerignore`
- `backend/.env.example`
- `backend/Dockerfile.api`
- `backend/Dockerfile.worker`
- `backend/package-lock.json`
- `backend/package.json`
- `backend/src/adapters.ts`
- `backend/src/api-main.ts`
- `backend/src/config.ts`
- `backend/src/fraud.ts`
- `backend/src/google.ts`
- `backend/src/http.ts`
- `backend/src/mime.ts`
- `backend/src/runtime.ts`
- `backend/src/service.ts`
- `backend/src/store.ts`
- `backend/src/time.ts`
- `backend/src/types.ts`
- `backend/src/worker-main.ts`
- `backend/test/bootstrap.sql`
- `backend/test/domain.test.ts`
- `backend/test/postgres.test.ts`
- `backend/test/security.test.ts`
- `backend/test/service.test.ts`
- `backend/tsconfig.json`
- `deploy/Deploy-Services.ps1`
- `deploy/Prepare-Gcp.ps1`
- `docs/ETAPA_2C_ENTREGA.md`
- `docs/ETAPA_2C_GMAIL_AUTOMATICO.md`
- `shared/email-risk-fixtures.json`
- `supabase/migrations/202611160001_gmail_automatic.sql`
