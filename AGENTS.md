# AGENTS.md — Hestia

## Producto y alcance vigente

Hestia ayuda a adultos mayores a identificar llamadas y correos sospechosos, revisar su resumen semanal y aprender a prevenir estafas. Este alcance de la ETAPA 1.1 reemplaza la navegación y los requisitos anteriores.

Nombre comercial, visible y Gradle: **Hestia**. Namespace, application ID y paquetes: `cl.grupo28.hestia`. No cambiar la carpeta física ni el repositorio.

Solo tres secciones principales: **Resumen, Aprender y Familia**. Resumen es el destino de inicio después del onboarding. Ajustes es un acceso pequeño desde Resumen; no es una cuarta sección. Familia gestiona contactos de emergencia locales, sin cuentas familiares ni invitaciones.

## Identidad y accesibilidad

Hestia representa protección, hogar, familia y tranquilidad: seguridad, confianza, cercanía, estabilidad y protección familiar. Inspiración mitológica sutil y contemporánea; símbolos abstractos de hogar, llama protegida, estructura arquitectónica o círculo. Sin diosas ni templos literales.

Sistema visual vigente ETAPA 1.2: fondo `#F7F2E9`, tarjetas `#FFF9F0`, texto `#24211F`, texto secundario `#514A44`, acciones terracota `#8A3F20`, acento ámbar `#D88A2D`, destacados `#F4DDB8`, positivo `#435A3B` sobre `#E7EBDD`, riesgo `#963B32` sobre `#F4DEDA`, bordes `#CFC2B2`. Usar MaterialTheme, sin colores dinámicos ni fondos blancos puros. Ámbar solo como acento, nunca texto pequeño sobre claro.

Sans serif nativa: pantalla 28–32 sp, tarjetas 20–22 sp, cuerpo 18 sp, secundario 16 sp, botones 18 sp, navegación 16 sp y métricas 36 sp. Sin texto de 12 sp ni alturas que recorten fuentes ampliadas. Separar acciones independientes al menos 12 dp. Selección de pestañas mediante fondo y peso tipográfico, con etiquetas visibles. Sin datos reales mostrar «Sin datos». Ver [decisiones y límites](docs/DESIGN_DECISIONS.md).

- Una acción principal por pantalla; reducir texto, opciones y tarjetas.
- Preferir dos o tres líneas por explicación, sin cortar texto al ampliar fuentes.
- Acciones de 56 dp y áreas táctiles de al menos 48 dp.
- Iconos con texto, estados expresados en palabras y semántica Compose para TalkBack.
- Respetar el tamaño del sistema. Apariencia accesible en Ajustes: texto, contraste, reducción de animaciones y restablecer. TextToSpeech continúa pendiente hasta su etapa autorizada.
- No mostrar detalles de implementación ni advertencias técnicas en el uso cotidiano.

## Reglas de trabajo

1. Leer completos AGENTS.md, MVP_SPEC.md y CODEX_PROMPTS.md; revisar Git antes de trabajar.
2. Proponer un plan breve e identificar dependencias, permisos y credenciales.
3. Implementar solo la etapa solicitada, en cambios pequeños, y compilar.
4. Ejecutar pruebas pertinentes; informar archivos, resultados y límites.
5. No declarar terminada una integración sin probarla con datos reales autorizados.
6. No hacer commits ni push sin autorización.
7. No presentar ejemplos, permisos concedidos o protecciones activas ficticias.

## Stack y organización

Conservar AGP 9.5.1, Gradle 9.6.0, JDK 25, Kotlin integrado de AGP 9, compileSdk/targetSdk 37 y minSdk 29. Sin actualizaciones masivas ni rutas locales compartidas.

Kotlin, Compose, Material 3, Navigation Compose, ViewModel/StateFlow y DataStore. Room/WorkManager solo cuando una función real lo requiera. ETAPA 2A incorpora cuenta Google mediante Credential Manager y Supabase Auth; se verificó una sesión real restaurada. ETAPA 2B incorpora autorización Gmail y revisión manual local, pendiente de configurar Gmail API/scope y probar el buzón real. La ETAPA 2C prepara PostgreSQL de aplicación y Gmail automático; despliegue y prueba real pendientes. Llamadas siguen siendo futuras.

- `core`: tema, componentes, accesibilidad, navegación y estado compartido.
- `features/summary`, `education`, `family`: las tres funciones principales.
- `features/onboarding` y `settings`: configuración.
- `domain`: modelos y reglas; `data`: repositorios locales y futuros adaptadores.
- `services`/`workers` únicamente al autorizar integraciones reales.

No poner lógica de negocio en MainActivity o composables. Usar interfaces de repositorio para pruebas sin credenciales. No crear capas vacías.

## Onboarding y permisos

Guardar con DataStore el último paso y su finalización. No repetirlo al abrir de nuevo; reanudar el paso pendiente. Completar el recorrido no significa que las protecciones estén activas.

Pasos: bienvenida, Google mediante Supabase Auth, autorización Gmail independiente, identificación de llamadas, agenda opcional para llamadas, notificaciones y finalización.

En ETAPA 2B Google y Gmail son conexiones independientes. Se puede continuar sin cuenta o sin Gmail. Gmail requiere consentimiento nativo gmail.readonly y comprobación real del perfil antes de mostrar conexión verificada; llamadas, agenda y notificaciones continúan pendientes. En etapas posteriores:

- Separar autenticación de Google y OAuth Gmail de solo lectura. Nunca pedir acceso para modificar, eliminar o enviar correos.
- Solicitar `ROLE_CALL_SCREENING` con RoleManager solo junto con un servicio funcional. No sustituir la aplicación telefónica habitual.
- `READ_CONTACTS` será opcional, justificado solo si se decide incluir llamadas de números guardados. No se necesita para la lista manual de emergencia.
- No solicitar `READ_CALL_LOG`, micrófono, ubicación ni accesibilidad del sistema.
- `POST_NOTIFICATIONS` desde Android 13; antes comprobar notificaciones habilitadas.
- Explicar antes del diálogo oficial, verificar el estado real al volver y tolerar rechazos.

## Llamadas, correos y resumen

CallScreeningService consultará exclusivamente caché local y responderá dentro de cinco segundos. Sin red en tiempo real, acceso al audio ni bloqueo automático. El número puede ser suplantado. Los contactos requieren READ_CONTACTS para ser entregados al servicio; privados/restringidos pueden quedar excluidos. Nunca presentar las cifras como todo el historial telefónico.

Gmail empezará con sincronización manual autorizada y reglas deterministas explicables. `gmail.readonly` es restringido y requiere configurar consentimiento y pruebas en Google Cloud. No abrir enlaces automáticamente ni afirmar autenticidad absoluta.

Resumen: semana actual, llamadas registradas/sospechosas, correos analizados/sospechosos y alertas revisadas. Solo eventos reales; no confundir detección con revisión o rechazo. Sin fuente conectada usar ausencia de datos, nunca cifras de demostración. No afirmar estafas o pérdidas evitadas.

## Familia, educación y privacidad

Contactos de emergencia con nombre, teléfono e iniciales. Agregar/eliminar varios, con selector del sistema o entrada manual. Llamar abre el marcador sin realizar la llamada automáticamente. Sin backend de contactos ni avisos a terceros no autorizados.

Cinco lecciones propias y breves basadas en CMF, SERNAC, ChileAtiende y BCN; identificar la fuente y enlazarla. Ley 20.009: artículo 2, aviso, identificador y bloqueo; artículo 4 bis, prevención e información de seguridad. No atribuir a esa ley una prohibición expresa de llamadas para pedir claves. No presentar material de Hestia como cursos de organismos oficiales ni prometer restituciones automáticas.

No implementar SMS ni análisis o recepción de conversaciones de WhatsApp; WhatsApp es solo temática educativa. Sin lectura del historial telefónico ni control remoto.

Nunca incluir secretos de servidor o service_role en el APK; no registrar tokens, contactos o contenido privado. No guardar cuerpos completos de correos en el backend. RLS y consentimiento explícito serán obligatorios cuando se autorice backend y cualquier intercambio de eventos.

## Definición de terminado

Compila, tiene pruebas adecuadas, errores y estados reales, interfaz accesible y limitaciones documentadas. Distinguir implementado y probado, pendiente por integración y no probado en dispositivo físico o TalkBack.


## Cuenta — ETAPA 2A

Mantener login Google separado de Gmail. Solicitar solo identidad básica con Credential Manager; intercambio de ID token y nonce validado en Supabase Auth. Nunca mostrar como autenticada una identidad obtenida solo del selector. Tokens cifrados con Android Keystore en noBackupFilesDir; sin logs ni secretos administrativos en APK. Cierre local incluso sin red, explicando si no se confirmó la revocación remota. Contactos, lecciones y preferencias siguen siendo locales al dispositivo y se conservan al cerrar/cambiar de cuenta. No crear tablas ni políticas RLS vacías. Ver docs/ETAPA_2A_AUTH.md para configurar y validar los servicios.


## Gmail — ETAPA 2B (modo manual conservado)

Autorización incremental con AuthorizationClient y gmail.readonly, ligada a la cuenta de Hestia y verificada con users.getProfile. Sin tokens Gmail persistidos, client secret, autorización offline ni backend de correos. Revisión manual de INBOX/semana local, límite 200 mensajes y 10 páginas explícito; deduplicar y preservar resultados anteriores ante errores. Cuerpos solo en memoria; sin endpoints de adjuntos ni apertura de enlaces. Metadatos y resultados mínimos cifrados con Keystore/noBackupFilesDir, aislados por propietario y eliminados al desconectar/cambiar/cerrar cuenta.

Mostrar resultados históricos como tales; nunca confundir cero con fuente no consultada, revisión parcial con cobertura completa ni Gmail con llamadas activas. Score heurístico interno, categorías y explicaciones sin porcentajes ni autenticidad garantizada. Google revokeAccess revoca todos los scopes de Hestia: explicarlo antes y distinguir desconexión local de revocación remota confirmada. Ver docs/ETAPA_2B_GMAIL.md. No ejecutar pruebas que desinstalen o borren datos del dispositivo con la sesión real sin un entorno de prueba separado.


## Gmail automático — ETAPA 2C (alcance vigente)

La ETAPA 2C autoriza backend Node 24 LTS/TypeScript, API Cloud Run con JWT Supabase, worker privado IAM/OIDC, Gmail watch + Pub/Sub, reconciliación/renovación Scheduler y FCM para HIGH. Este permiso reemplaza la prohibición de autorización offline y backend de correos de 2B **solo en el modo automático con consentimiento explícito**; la revisión manual local se conserva. No iniciar llamadas/ETAPA 3.

requestOfflineAccess usa el cliente web existente; serverAuthCode de un uso solo en memoria y HTTPS al backend. Identidad siempre desde Supabase Auth verificado y comparada con perfil Gmail. Refresh cifrado Cloud KMS AES-256-GCM con AAD del UUID, ciphertext privado en Supabase, secretos web/DB únicamente Secret Manager, identidades runtime ADC sin claves JSON. Access tokens solo memoria. Nada de cuerpos completos, adjuntos, tokens ni contenido privado en logs.

Watch INBOX comienza desde la activación, sin barrido histórico inicial. Cursor solo tras todas las páginas; leases cercados y unicidad propietario/hash. Renovar watch no adelanta el cursor. Historial 404: recuperación acotada a semana local actual y barrera previa. Retención eventos 30 días, RLS propietario SELECT y escrituras solo rol backend; conexiones/dispositivos/outbox privados. LOW sin asunto/remitente/score/razones. HIGH aviso genérico con identificadores opacos; MEDIUM en app. La selección de alerta comprueba nuevamente propiedad.

Firebase opcional: compilar sin google-services.json, sin configuración ficticia ni auto-init previo al consentimiento. POST_NOTIFICATIONS independiente; rechazo mantiene alertas en app. Cerrar sesión desvincula avisos de este teléfono y conserva los datos locales según las reglas de 2A; no detiene seguimiento remoto hasta Desconectar. Desconectar elimina credenciales y dispositivos, permite confirmar borrado de eventos y distingue revocación remota no confirmada. Cascada de cuenta elimina datos locales del servidor, no revoca por sí sola Google.

Conservar play-services-auth 21.4.0: requestOfflineAccess(String) disponible, setPrompt no. Falta refresh → renovación explícita mediante revocación confirmada y nueva autorización; sin overload deprecated ni consentimiento forzado en cada revisión. Testing puede exigir reconexión a los siete días. No cambiar OAuth a Production ni prometer verificación/CASA.

No desplegar, crear recursos facturables, aplicar SQL remoto, modificar credenciales ignoradas ni hacer commit/push sin autorización. Scripts entregados muestran plan por defecto; no se ejecutaron contra la nube. Código local no equivale a integración real. Configuración y validación: [ETAPA 2C](docs/ETAPA_2C_GMAIL_AUTOMATICO.md).


## ETAPA 2C.1 — LOCAL_DEMO (alcance vigente, sin costos)

Se autoriza una demostración local explícita con datos sintéticos identificados como DEMO LOCAL, sin reemplazar la implementación 2C. PostgreSQL local ya instalado, API y Worker Node separados, cola persistente y adaptadores locales que reutilizan GmailService, parser MIME, motor y almacenamiento/contadores. Identidades demo del servidor, separadas de Supabase; nunca OAuth ni refresh tokens reales. No se inicializa Firebase ni se consulta Google/Supabase en la demo.

Controles/transporte HTTP y permiso ACCESS_LOCAL_NETWORK para Android 17 exclusivamente debug. Release sin controles ni acceso HTTP local. AutomaticGateway/AutomaticApi y el mismo ViewModel; cifras sintéticas nunca se suman a Gmail real. Revisión manual 2B y sesión cifrada se conservan; se vuelve a ellas eligiendo modo real explícitamente. Avisos HIGH en bandeja simulada, MEDIUM solo alerta en app, LOW solo conteo. No prometer recepción con app cerrada ni representar simulaciones como protecciones reales.

Prohibido habilitar facturación, contratar servicios, crear recursos Cloud, ejecutar scripts de despliegue o -Execute durante esta etapa. Sin software adicional, modificaciones de secrets.properties/OAuth, commits, push ni ETAPA 3. Ver docs/ETAPA_2C1_LOCAL_DEMO.md. Las prohibiciones históricas de datos ficticios no impiden esta demo explícita y rotulada; siguen vigentes para el producto real.


## ETAPA 2C.2 — Avisos locales en segundo plano (autorizada)

Extensión de LOCAL_DEMO exclusiva de debug: servicio foreground dataSync iniciado explícitamente, avisos Android locales HIGH sintéticos y cursor local por identidad. No usa FCM, Gmail, OAuth ni servicios externos; el flujo real se conserva. Sin BOOT_COMPLETED ni excepciones de batería. Android puede detener o retrasar el servicio, incluyendo Doze y el límite dataSync; no prometer seguimiento permanente. Ver docs/ETAPA_2C2_BACKGROUND_DEMO_NOTIFICATIONS.md. Preservar 2C.1 como documento histórico. No habilitar facturación, modificar credenciales, desplegar, iniciar ETAPA 3 ni hacer commit/push.
