# ETAPA 5 · Familia y contactos de confianza

Familia permite elegir a quién pedir ayuda cuando una llamada o un correo generan dudas. Hestia prepara un mensaje que la persona puede revisar y editar. La llamada o el envío se confirma en la aplicación que la persona abre.

## Contactos y flujo de ayuda

- **Familia.** En **Administrar contactos** se puede agregar, editar y eliminar, marcar una persona como principal y subir la prioridad de las demás. Nombre y teléfono son obligatorios y la relación es opcional. Se puede escribir el contacto o elegir un número en el selector de Android. El contacto principal encabeza la lista. Al pedir ayuda desde la portada, la persona elige el destinatario. Al hacerlo desde una tarjeta, se muestra el contacto de esa tarjeta y se puede cambiar.
- **Llamadas.** **Ayuda** y **Ver por qué** muestran recomendaciones y permiten preparar una solicitud. Mientras Telecom gestiona una llamada en la actividad de Hestia, el flujo bloquea el acceso al marcador, Mensajes y el selector. Se puede volver a la llamada y colgar desde su propio control. La advertencia flotante de Google Teléfono permite preparar una solicitud mientras está visible. Después de la llamada se puede volver a Familia para comunicarse.
- **Después de la llamada y Recientes.** El formulario contextual de reporte conserva su flujo y ofrece **Pedir ayuda** y **Volver a Recientes**. Si el reporte no está disponible, la pantalla visible de finalización ofrece ayuda y acceso a Recientes. **Recientes → Ver opciones → Pedir ayuda** usa el contexto de esa entrada. El mensaje incluye una fecha aproximada cuando está disponible. El número solo se agrega al activar **Incluir número de la llamada**. Hestia ofrece plantillas diferentes para llamadas con y sin advertencia y excluye números ocultos.
- **Correos.** **Pedir ayuda con este correo** aparece en las alertas HIGH de revisión automática, DEMO LOCAL y revisión manual. La solicitud usa una plantilla general que omite cuerpo, asunto, remitente, enlaces y motivos del mensaje original. Los correos sintéticos llevan el encabezado **DEMO LOCAL · Aviso de prueba**. Al cerrar la solicitud vuelve a mostrarse el detalle o la tarjeta del correo.

## Datos y arquitectura

`DataStoreSettingsRepository` conserva el mismo archivo y clave de contactos. El registro `EmergencyContact` añade `relationship` y `primary`, con valores opcionales para leer las listas anteriores. Las operaciones de agregar, editar y priorizar se hacen dentro de una escritura de DataStore. La app mantiene como máximo un principal y rechaza duplicados al guardar, incluso al editar. `HestiaViewModel` coordina la gestión en Familia y Teléfono reutiliza el repositorio y el editor. En DEBUG, los registros de Recientes conservan si la llamada tuvo una advertencia, con un valor por defecto para las entradas anteriores.

`HelpRequest` define las plantillas y los datos que se pueden mostrar. `TrustHelpDialog` presenta selección de persona, canal, edición y confirmación. `FamilyAndroidActions` abre aplicaciones de Android. Los borradores viven en memoria y su estado se limpia al cerrar la solicitud. Los contactos se guardan en el dispositivo y Teléfono comparte esa lista. Las identidades sintéticas Ana y Bruno siguen separando reputación y correos de demostración. Cambiar de identidad no cambia la lista local del dispositivo. Los contactos y borradores no pasan por esos adaptadores ni se registran en logs.

Las reglas de copia de seguridad excluyen de la nube el directorio de DataStore donde se guardan los contactos. El archivo compartido contiene también el avance del inicio, las lecciones y las preferencias de apariencia. En Android 12 y posteriores, el traslado directo entre dispositivos conserva las reglas existentes del sistema. [Copias de seguridad de Android](https://developer.android.com/identity/data/autobackup).

| Acción | Qué ocurre |
| --- | --- |
| Llamar | `ACTION_DIAL` abre el marcador con el número. |
| Enviar mensaje | `ACTION_SENDTO` y `smsto:` abren Mensajes con el contacto y el texto revisado. Si falla, se puede volver para elegir otra opción. |
| Compartir advertencia | `ACTION_SEND` abre el selector de Android con texto. La persona elige la app y después el destinatario. |
| Elegir contacto del sistema | `ACTION_PICK` concede lectura temporal solo del número elegido. Se puede cancelar y escribirlo manualmente. |

La etapa usa los permisos existentes. Android maneja la confirmación de la llamada o del mensaje. Hestia solo informa que solicitó abrir la aplicación. Los operadores pueden aplicar tarifas de llamada o SMS. En RELEASE están disponibles Familia, las acciones Android y la ayuda de correos. El teléfono de Hestia, Recientes y DEMO LOCAL siguen en DEBUG. [Intents oficiales de Android](https://developer.android.com/guide/components/intents-common), [selector de contactos de Android 17](https://developer.android.com/about/versions/17/features/contact-picker).

## Accesibilidad

Se mantienen la tipografía escalable y la paleta de Hestia. Los botones muestran su acción en texto y los diálogos se desplazan al ampliar la fuente. Mostrar pocos controles por paso y conservar **Cerrar** o **Volver** aplica las recomendaciones de simplificación y autonomía. La paleta cálida se conserva como decisión del proyecto. [Darvishy y Hutter](https://pubmed.ncbi.nlm.nih.gov/28873871/), [Gomez-Hernandez et al.](https://mhealth.jmir.org/2023/1/e43186/), [Liu y Wang](https://doi.org/10.1080/10447318.2024.2338659), [Zhang et al.](https://doi.org/10.1080/10447318.2022.2131264).

## Repetir en Pixel 10 API 37

1. Usa un emulador de QA con JDK 25. Ejecuta `.\gradlew.bat assembleDebug assembleRelease testDebugUnitTest lintDebug assembleDebugAndroidTest` desde la raíz. Instala el APK DEBUG con `adb -s <emulador> install -r app/build/outputs/apk/debug/app-debug.apk`. Completa el inicio sin conectar cuentas reales.
2. En **Familia**, agrega dos contactos ficticios, por ejemplo **Elena de prueba**, `2025550196`, y **Luis de prueba**, `2025550197`. En **Administrar contactos** edita uno, marca y cambia el principal, ordena y verifica la lista después de reabrir la app.
3. Pulsa **Pedir ayuda**, selecciona y cambia de contacto. Elige **Enviar mensaje**, modifica el texto y pulsa **Abrir Mensajes**. Comprueba el destinatario y cancela sin enviar. Repite con **Compartir advertencia → Abrir selector de aplicaciones** y vuelve a Hestia sin compartir.
4. En **Teléfono**, selecciona Hestia como aplicación predeterminada y activa **Recientes**. En el notebook, ejecuta `adb -s <emulador> emu gsm call 2025550147`. Contesta, abre **Ayuda** y prepara el mensaje. Verifica que el acceso externo está deshabilitado durante la llamada. Vuelve, cuelga y abre **Pedir ayuda** desde el contexto posterior o desde Recientes. Comprueba que el número aparece en el mensaje solo si lo eliges.
5. Inicia el backend de pruebas con `$env:LOCAL_DEMO='true'` y `npm.cmd run demo:start` desde `backend`. En otra terminal con la misma variable, ejecuta `npm.cmd run demo:emit -- --persona bob --scenario high-risk`. En **Resumen → Revisar correos** activa DEMO LOCAL, concede red local y elige Bruno. Abre la alerta HIGH y pulsa **Pedir ayuda con este correo**. Selecciona el contacto y revisa el mensaje. Ciérralo y comprueba que vuelve el detalle.
6. Repite el borrador con `adb -s <emulador> shell settings put system font_scale 1.3` y `2.0`. Desplaza el contenido y revisa el acceso a **Volver** y a los botones. Restaura `font_scale` a su valor anterior al terminar.

Para los ensayos de contactos y comunicación, instala `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` con `adb install -r` y usa `adb -s <emulador> shell am instrument -w -e class cl.grupo28.hestia.LocalSettingsRepositoryTest,cl.grupo28.hestia.FamilyCommunicationTest cl.grupo28.hestia.test/androidx.test.runner.AndroidJUnitRunner`.

## Archivos de la etapa

| Área | Archivos |
| --- | --- |
| Contactos y estado | `LocalSettings.kt`, `LocalSettingsRepository.kt`, `HestiaViewModel.kt`, `FamilyScreen.kt` |
| Solicitud y canales Android | `HelpRequest.kt`, `TrustHelpDialog.kt`, `FamilyAndroidActions.kt` |
| Llamadas y navegación | `MainActivity.kt`, `HestiaApp.kt`, `ExternalActions.kt`, `CallWarningPreviewNavigation.kt` en ambas variantes, `PhoneScreen.kt`, `PhoneDirectory.kt`, `PhoneRecents.kt`, `TelecomCallActivity.kt`, `HestiaInCallService.kt`, `InCallSession.kt`, `CallWarningPreviewScreen.kt`, `CallerIdentification.kt`, `ContextualCallReport.kt`, `DemoCallReportsCard.kt` |
| Correos | `AutomaticPanel.kt`, `GmailScreen.kt` |
| Privacidad | `backup_rules.xml`, `data_extraction_rules.xml` |
| Pruebas | `HelpRequestTest.kt`, `FamilyCommunicationTest.kt`, `LocalSettingsRepositoryTest.kt`, `HestiaNavigationTest.kt`, `LocalDemoIntegrationTest.kt` |
| Documentación | `AGENTS.md`, `README.md`, esta guía y las capturas del Pixel 10 |

## Resultados en Pixel 10 API 37

Pasaron `assembleDebug`, `assembleRelease`, `lintDebug` y las **97 pruebas unitarias** de `testDebugUnitTest`. Pasaron **11 pruebas instrumentales** de navegación, persistencia, prioridad, duplicados, Intents, ausencia de una app SMS, Gmail manual, simulación local y avisos HIGH en segundo plano. La aserción anterior que buscaba la métrica de llamadas en Resumen se actualizó para verificar su separación en DEBUG. El manifiesto fusionado RELEASE mantiene `usesCleartextTraffic=false` y excluye `InCallService`, los permisos de red local de DEBUG, `READ_CONTACTS` y `SEND_SMS`.

| Recorrido | Resultado observado |
| --- | --- |
| Familia | Se verificó la lista vacía, se agregaron dos personas ficticias, se cambió el contacto principal y se editó la relación. El selector del sistema abrió y volvió al editor al cancelarlo. La prueba de persistencia reabrió DataStore, verificó la edición del número, rechazó un duplicado y eliminó el contacto principal. |
| Mensajes y selector | Se eligió el principal y luego otro contacto. Google Mensajes abrió el número correcto y mostró el texto editado como borrador. Se volvió a Hestia sin pulsar Enviar. El selector de Android se abrió y se canceló sin elegir una aplicación. |
| Llamada activa y postllamada | `2025550147` mostró SOSPECHOSO. Ayuda preparó un borrador con Mensajes deshabilitado. Al cerrarlo la llamada seguía conectada. Tras colgar se abrió ayuda desde el contexto posterior y se comprobó que la casilla del número empezaba desmarcada. |
| Recientes y reporte | **Volver a Recientes** abrió la lista. Se abrió una solicitud para `0147`, se guardó un reporte sintético en la base aislada y **Volver a llamar** abrió el marcador con ese número. |
| Google Teléfono | Android mantuvo Google Teléfono como marcador y Hestia como identificador. `CallScreeningService` mostró la advertencia local y **Ver por qué → Pedir ayuda** abrió la lista de confianza. |
| Correo HIGH | El worker local procesó un correo sintético de Bruno. El borrador incluyó el rótulo DEMO LOCAL y la plantilla sin asunto, remitente, enlaces o credenciales. Al cerrarlo volvió la alerta. Se comprobó el aviso HIGH local en segundo plano y su apertura en el detalle. |
| Accesibilidad | Se comprobó el formulario con fuente estándar, 130 % y 200 %. Con TalkBack se abrió **Pedir ayuda** por doble toque y se observó el foco de accesibilidad en el diálogo. |

QA usó un AVD y una base de datos separados con datos sintéticos. No se enviaron mensajes ni se marcaron llamadas reales.

## Límites de la verificación

Quedan pendientes la prueba en un teléfono físico, la importación con contactos reales del sistema y una revisión más amplia de TalkBack con audio. Las pruebas de correo usaron el worker y PostgreSQL locales con datos sintéticos. Los correos reales y los servicios externos quedan fuera de esta validación.

## Capturas del emulador

- [Familia vacía](screenshots/etapa-5-familia/familia-vacia.png), [contacto principal](screenshots/etapa-5-familia/familia-contacto-principal.png) y [revisión del mensaje](screenshots/etapa-5-familia/solicitud-desde-familia.png).
- [Borrador abierto en Mensajes](screenshots/etapa-5-familia/mensajes-borrador.png) y [selector de Android](screenshots/etapa-5-familia/selector-android.png).
- [Ayuda durante una llamada](screenshots/etapa-5-familia/ayuda-durante-llamada.png), [después de colgar](screenshots/etapa-5-familia/ayuda-postllamada.png), [aviso con Google Teléfono](screenshots/etapa-5-familia/ayuda-aviso-google.png) y [solicitud desde correo HIGH](screenshots/etapa-5-familia/ayuda-correo-high.png).
- [Texto al 130 %](screenshots/etapa-5-familia/texto-130.png), [texto al 200 %](screenshots/etapa-5-familia/texto-200.png) y [foco de TalkBack](screenshots/etapa-5-familia/talkback-ayuda.png).
