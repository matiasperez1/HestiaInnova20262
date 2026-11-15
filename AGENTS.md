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

Kotlin, Compose, Material 3, Navigation Compose, ViewModel/StateFlow y DataStore. Room/WorkManager solo cuando una función real lo requiera. Supabase Auth/PostgreSQL, Gmail API y servicios Android son integraciones futuras, no parte de la ETAPA 1.1.

- `core`: tema, componentes, accesibilidad, navegación y estado compartido.
- `features/summary`, `education`, `family`: las tres funciones principales.
- `features/onboarding` y `settings`: configuración.
- `domain`: modelos y reglas; `data`: repositorios locales y futuros adaptadores.
- `services`/`workers` únicamente al autorizar integraciones reales.

No poner lógica de negocio en MainActivity o composables. Usar interfaces de repositorio para pruebas sin credenciales. No crear capas vacías.

## Onboarding y permisos

Guardar con DataStore el último paso y su finalización. No repetirlo al abrir de nuevo; reanudar el paso pendiente. Completar el recorrido no significa que las protecciones estén activas.

Pasos: bienvenida, Google mediante Supabase Auth, autorización Gmail independiente, identificación de llamadas, agenda opcional para llamadas, notificaciones y finalización.

En ETAPA 1.1 los pasos de integración son interfaces pendientes, permiten continuar y no abren autorizaciones reales. En etapas posteriores:

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
