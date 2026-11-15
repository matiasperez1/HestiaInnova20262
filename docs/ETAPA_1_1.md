# Hestia — ETAPA 1.1

## Cambios

La nueva experiencia reemplaza cuatro secciones y sus detalles por **Resumen, Aprender y Familia**, con Ajustes desde Resumen. Se retiran Inicio, Protección, sus cuatro detalles, orientación separada, invitaciones familiares y bienvenida repetida. No hay integración de mensajes.

El asistente tiene los siete pasos solicitados y DataStore conserva el último paso o su finalización. En esta etapa las acciones de Google, Gmail, llamadas, agenda y notificaciones están deshabilitadas, con la indicación breve «Disponible próximamente» y una acción para continuar. No se concede ningún permiso ni se simula una conexión. Completar el recorrido no activa protecciones.

Resumen tiene semana actual, estado pendiente, métricas de llamadas/correos y alertas revisadas. Sus valores son «—» porque no hay fuente conectada. El modelo de cálculo recibe exclusivamente eventos, deduplica por canal/referencia y filtra lunes a domingo según la zona del dispositivo; está preparado y probado sin generar datos de demostración. No es un registro completo del historial telefónico. Faltan las integraciones que aportarán eventos reales.

Familia usa un diálogo de nombre/teléfono, iniciales, una lista local y eliminación confirmada. Valida números y evita duplicados. Llamar abre ACTION_DIAL para que la aplicación telefónica gestione la llamada; no requiere CALL_PHONE ni acceso a agenda. No envía avisos ni tiene backend.

Aprender tiene cinco lecciones y progreso local persistente. Cada lección separa una situación breve y recomendación del ejercicio interactivo; ofrece respuesta explicada y fuentes. Los detalles legales están desplegables para no saturar la pantalla inicial.

## Fuentes y revisión editorial

Revisadas el 15 de noviembre de 2026:

| Lección | Publicaciones utilizadas |
| --- | --- |
| ¿Te llaman del banco? | [CMF prevención](https://www.cmfchile.cl/educa/621/w3-article-52008.html), [ChileAtiende](https://portal.chileatiende.gob.cl/fichas/81544-campana-mas-vale-prevenir) |
| Correos falsos | [SERNAC phishing](https://www.sernac.cl/portal/607/w3-article-1612.html), [guía SERNAC](https://www.sernac.cl/portal/607/w3-article-86575.html) |
| WhatsApp | [ChileAtiende: Más Vale Prevenir](https://portal.chileatiende.gob.cl/fichas/81544-campana-mas-vale-prevenir) |
| Créditos e inversiones | [CMF prevención](https://www.cmfchile.cl/educa/621/w3-article-52008.html), [CMF alertas](https://www.cmfchile.cl/educa/621/w3-propertyvalue-49625.html) |
| Si fui víctima | [Ley 20.009 en BCN](https://www.bcn.cl/leychile/navegar?idLey=20009), [ChileAtiende](https://portal.chileatiende.gob.cl/fichas/81544-campana-mas-vale-prevenir) |

ChileAtiende y la guía reciente de SERNAC presentaron restricciones de acceso directo en el entorno; se consultó su contenido indexado bajo los mismos dominios oficiales. Los enlaces originales se mantienen en la app. Una apertura puede depender de conexión, navegador y disponibilidad del sitio.

Material educativo redactado por Hestia, no cursos atribuidos a organismos oficiales. Se verificaron los artículos 2 y 4 bis de la Ley 20.009. No se atribuye a la ley una prohibición expresa de llamadas para pedir claves ni se prometen restituciones automáticas. Se evita el consejo antiguo que trataba un candado como evidencia suficiente de autenticidad.

## Arquitectura y archivos

- `core/HestiaViewModel.kt`: estado, lectura/guardado y errores.
- `data/LocalSettingsRepository.kt`: contrato y DataStore para onboarding, contactos, progreso y apariencia.
- `domain/LocalSettings.kt`, `WeeklySummary.kt`: validación de contactos, modelos y cálculo semanal.
- `core/navigation/HestiaApp.kt`, `ExternalActions.kt`: tres destinos, onboarding y apertura explícita de fuentes/marcador.
- `features/onboarding`, `summary`, `family`, `education`, `settings`: pantallas vigentes.
- Componentes comunes y preferencias de accesibilidad simplificados; MainActivity solo conecta el estado con la UI.
- Eliminados `features/home`, `protection`, `trustedcontact` y la navegación anterior.
- Actualizados AGENTS.md, MVP_SPEC.md, CODEX_PROMPTS.md y README.md. ETAPA_1.md queda histórico.

Única dependencia nueva de esta etapa: DataStore Preferences 1.1.7, estable y compatible con la base existente. No se cambian AGP, Gradle, Kotlin, SDK, identidad o permisos del manifest.

## Pruebas y límites

Las pruebas de dominio cubren teléfono/nombre, límites semanales y separación/deduplicación de métricas. La instrumentación usa un archivo DataStore aislado para comprobar reapertura, contactos múltiples, duplicados, progreso y apariencia; el recorrido de la aplicación comprueba reanudación, finalización, tres pestañas, cinco lecciones, fuentes y operaciones de contactos.

No hay integración real de autenticación, correo, llamadas, sincronización o push. No se solicitan permisos en esta etapa. La lectura en voz alta y una auditoría completa con TalkBack/dispositivos físicos permanecen pendientes. Los contactos residen en el espacio privado local de la app; no se transmiten ni se imprimen en logs.

## Resultado final

- `assembleDebug`: aprobado.
- `testDebugUnitTest`: 7 pruebas aprobadas (incluida la prueba de plantilla).
- `connectedDebugAndroidTest`: 3 pruebas aprobadas en emulador API 37; repetidas sobre el APK final mediante instrumentación directa, también aprobadas.
- `lintDebug`: 0 errores y 17 advertencias: Lifecycle/lint, versiones disponibles, recursos de plantilla sin uso y etiqueta redundante.
- Se comprobó un cierre completo del proceso con `am force-stop` y nueva apertura: muestra Resumen y no la bienvenida.
- Fuentes y marcador probados con monitores de intents: no se realizaron llamadas ni se abrieron sitios durante la prueba automatizada.
- Los contactos de prueba fueron eliminados; la lista quedó vacía. El progreso educativo del emulador refleja las cinco lecciones recorridas en las pruebas.
- Capturas de las tres secciones en `build/review-stage11`, fuera del control de versiones; APK en `app/build/outputs/apk/debug/app-debug.apk`.

No se comienza la siguiente etapa ni se realizan commits o push.
