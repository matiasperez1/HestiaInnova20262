# ETAPA 7 · Integración y consolidación del MVP

## Estado inicial

Hestia compartía Resumen, Aprender y Familia entre variantes. DEBUG incorporaba Teléfono y DEMO LOCAL. Las rutas contextuales podían abrir lecciones desde Protección, Recientes y un correo, pero un cambio de pestaña descartaba el recorrido. Resumen mezclaba la disponibilidad de fuentes en una sola frase y omitía la actividad telefónica en DEBUG. La pantalla de correos daba precedencia a la revisión automática aun cuando no había un servicio configurado. La simulación mostraba las herramientas de preparación junto con las alertas.

El repositorio ya contenía cambios de Familia, Aprender y la corrección de R8, además de trabajo local previo del backend. El código educativo vigente tiene 24 preguntas, dos en cada una de 12 lecciones. Los cambios de esta etapa se concentran en la integración, la navegación y la presentación del estado.

## Resumen y accesos

- **Estado de protección** muestra por separado el rol de identificación de llamadas y la disponibilidad de correos. Revisar correos abre el flujo existente. En DEBUG, Consultar Teléfono recupera el recorrido de esa sección.
- **Actividad de esta semana** utiliza el registro local de detecciones y la revisión de correos que corresponda. Las llamadas de prueba quedan en Protección de DEBUG. Un correo HIGH de la semana puede abrirse desde Resumen cuando el servicio está confirmado. El acceso transmite el identificador al detalle y su consulta vuelve a comprobar la identidad.
- Los correos de demostración tienen etiquetas propias. Las cifras de la revisión manual y de la automática se eligen como fuentes alternativas. Los resultados de otra semana quedan en su sección histórica. Si falta una métrica, Resumen muestra el estado de la fuente. El historial de reportes queda disponible en **Mis reportes** de Teléfono, donde se conoce la identidad que lo realizó.
- **Ayuda y prevención** abre la solicitud compartida con Familia o la sección Aprender. La solicitud se prepara localmente y el envío se confirma en la aplicación de destino.

## Navegación, estados y datos

Las pestañas conservan su estado de navegación. Un aviso de correo vuelve a la entrada existente de Revisar correos si ya está en la pila. La lección contextual vuelve al detalle que la abrió. La revisión manual es el punto de entrada cuando la automática carece de configuración. Las alertas automáticas y DEMO LOCAL muestran el flujo correspondiente al abrirse.

Durante una consulta se indica que está en curso. El fallo de red se distingue de un error de consulta y del permiso pendiente. Un fallo conserva las últimas cifras confirmadas en la sesión abierta y las identifica como guardadas. Actualizar estado vuelve a consultar. Si falla la lectura de llamadas, se informa el error de almacenamiento. La reputación sigue consultando la última caché local y se sincroniza desde Teléfono. Los borradores de ayuda pueden recuperar sus campos cuando Android conserva el estado del formulario.

Los contactos, preferencias y avance educativo siguen en el DataStore del dispositivo. Las detecciones telefónicas se conservan en el registro privado de Hestia. La consulta manual usa la copia local de resultados cifrada. El backend de demostración guarda correos sintéticos y votos de Ana y Bruno. Se utilizaron los repositorios, los formatos guardados y la política de copias existentes.

## DEBUG y RELEASE

DEBUG conserva Teléfono y agrupa la preparación de correos en **Herramientas de demostración**. Las identidades, limpieza, estados técnicos y activación de avisos quedan dentro de ese grupo. La reputación tiene un grupo propio en Protección. La demostración usa datos sintéticos y puede presentarse con una modalidad local sin servidor o con API, Worker y PostgreSQL.

RELEASE ofrece Resumen, Aprender y Familia con Ajustes y Revisar correos como destinos secundarios. El servicio de identificación conserva su alcance de producción. El backend de correo automático real sigue pendiente de despliegue y validación. Se conserva la configuración R8 de la ETAPA 6.

## Validación reproducible

1. Con JDK 25, ejecuta `./gradlew.bat assembleDebug assembleRelease testDebugUnitTest lintDebug assembleDebugAndroidTest`. Usa un Pixel 10 API 37 de QA. Instala `app/build/outputs/apk/debug/app-debug.apk` y `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` con `adb -s <emulador> install -r <apk>`.
2. En modo avión y con Wi-Fi y datos deshabilitados, ejecuta `adb -s <emulador> shell am instrument -w -e class cl.grupo28.hestia.HestiaNavigationTest,cl.grupo28.hestia.LocalSettingsRepositoryTest,cl.grupo28.hestia.FamilyCommunicationTest,cl.grupo28.hestia.GmailUiTest,cl.grupo28.hestia.AutomaticUiTest cl.grupo28.hestia.test/androidx.test.runner.AndroidJUnitRunner`. Usa una instalación de QA recién creada para la prueba de navegación.
3. Para las pruebas de correo sintético, inicia la modalidad completa de la guía y reactiva la red del emulador. Ejecuta `adb -s <emulador> shell am instrument -w -e localDemoIntegration true -e class cl.grupo28.hestia.LocalDemoIntegrationTest,cl.grupo28.hestia.DemoBackgroundIntegrationTest cl.grupo28.hestia.test/androidx.test.runner.AndroidJUnitRunner`. Las pruebas actúan sobre Bruno en el backend de QA.
4. Usa el guion para verificar Telecom, Recientes y la corroboración. Prueba después la conexión perdida deteniendo solo la API local de QA, con el Worker y PostgreSQL activos. Comprueba el estado en Resumen y la consulta de la caché desde Teléfono. Reinicia la API, actualiza y comprueba que vuelve la conexión.
5. Para una prueba de RELEASE, firma una copia de `app-release-unsigned.apk` con la clave de DEBUG usada en QA e instálala con `adb install -r` en ese mismo emulador. La firma de QA permite comprobar que la actualización conserva progreso, preferencias y contactos. Usa el paquete de RELEASE de la compilación para verificar que el manifiesto excluye los servicios y permisos de la demostración.

[Guion y comandos para la presentación](GUIA_DEMO_AYUDANTES.md).

## Archivos de trabajo

- Resumen y selección de fuentes en `SummaryScreen.kt` y `SummaryMail.kt`.
- Navegación en `HestiaApp.kt` y `GmailScreen.kt`.
- Estados y agrupación en `GmailViewModel.kt`, `AutomaticViewModel.kt`, `AutomaticPanel.kt` y `DemoPanel.kt`.
- Teléfono y ayuda en `PhoneScreen.kt`, `DemoCallReportsCard.kt` y `TrustHelpDialog.kt`.
- Pruebas de Resumen, navegación, configuración local y DEMO LOCAL en `app/src/test` y `app/src/androidTest`.

## Resultados

| Validación | Resultado |
| --- | --- |
| Android | `assembleDebug`, `assembleRelease`, `testDebugUnitTest`, `lintDebug` y `assembleDebugAndroidTest` correctos. Pasaron 103 pruebas unitarias. |
| DEBUG en Pixel 10 API 37 sin red | Once pruebas instrumentales aprobadas. Cubren navegación, rotación, ejercicio, salida y reanudación, contactos, preferencias y estados de correo. Se ejecutó por separado la última repetición de navegación después de corregir el arnés para la restauración de pestañas y los botones del encabezado. |
| DEMO LOCAL en Pixel 10 API 37 | Dos pruebas instrumentales aprobadas, con API, Worker y PostgreSQL ejecutándose en una instancia aislada. HIGH llegó a Resumen y al detalle con la etiqueta de simulación. Se abrió Aprender y se volvió al mismo aviso. Pedir ayuda regresó al aviso. La notificación Android abrió el detalle y la repetición del evento quedó deduplicada. |
| Android Telecom y Recientes | El emulador entregó `2025550147`. Se comprobó SOSPECHOSO y su explicación, se rechazó la llamada y se verificó su registro en Recientes. Ana reportó desde las opciones de la llamada rechazada. Abrieron Pedir ayuda y la lección contextual, y Volver recuperó las opciones de la misma llamada. |
| Demostración básica | Con Wi-Fi y datos deshabilitados, Android informó que no había red predeterminada. La llamada GSM de prueba volvió a mostrar SOSPECHOSO y se rechazó. |
| Reputación | Bruno consultó el único reporte de Ana. Su reporte propio elevó el total a dos autores independientes. Bruno retiró su reporte, sincronizó y se comprobó en PostgreSQL que permanecía únicamente el de Ana. |
| Familia y Mensajes | Se agregaron dos contactos ficticios y se cambió el principal de Ana a Luis. Tras cerrar y abrir Hestia, Luis seguía como principal. Desde Resumen se preparó una solicitud, se abrió el compositor de Google Mensajes con el número ficticio y se cerró la conversación sin enviar. Android conservó un borrador. |
| Caída y recuperación de la API | Se detuvo solo la API de QA. Resumen mostró «Sin conexión con el computador» y retuvo la última cifra con su etiqueta. Teléfono mostró el reporte de Ana desde la caché después del retiro de Bruno. Aprender abrió durante la caída. Tras reiniciar la API, el estado y la cifra se recuperaron y se confirmaron también al cerrar y abrir Hestia. |
| Arranque de RELEASE sin red | Antes de actualizar se vieron dos detecciones de prueba en DEBUG. Se instaló una copia firmada para QA sobre esos datos. RELEASE inició y abrió Resumen, Aprender, Familia y la revisión manual. Conservó la lección completada y el contacto principal. La pestaña Teléfono, los controles de demostración y las detecciones de prueba quedaron fuera de la experiencia. El manifiesto de RELEASE deshabilita HTTP y excluye el permiso de red local, el servicio de marcador de DEBUG y el monitor de demostración. |
| Backend | Se compiló el TypeScript existente para preparar la instancia local. Los flujos se ejercitaron con el servidor y Worker separados. Los cambios previos del backend permanecieron en el directorio de trabajo. |

[Resumen en modo real](screenshots/etapa-7-mvp/resumen-real.png) · [Resumen durante DEMO LOCAL](screenshots/etapa-7-mvp/resumen-demo.png) · [Detalle HIGH sintético](screenshots/etapa-7-mvp/correo-high.png).

[Aviso de llamada](screenshots/etapa-7-mvp/llamada-sospechosa.png) · [Llamada rechazada en Recientes](screenshots/etapa-7-mvp/recientes-rechazada.png) · [Reporte contextual](screenshots/etapa-7-mvp/reporte-contextual.png).

[Reputación pendiente](screenshots/etapa-7-mvp/reputacion-pendiente.png) · [Reputación corroborada](screenshots/etapa-7-mvp/reputacion-corroborada.png).

[Contacto principal](screenshots/etapa-7-mvp/familia-principal.png) · [Solicitud editable en Hestia](screenshots/etapa-7-mvp/familia-borrador.png).

[Llamada de prueba sin Internet](screenshots/etapa-7-mvp/llamada-sin-internet.png) · [Vista de correos con herramientas cerradas](screenshots/etapa-7-mvp/correos-herramientas-cerradas.png).

[Resumen sin API](screenshots/etapa-7-mvp/resumen-sin-api.png) · [Reputación desde la caché](screenshots/etapa-7-mvp/cache-sin-api.png) · [API recuperada](screenshots/etapa-7-mvp/api-recuperada.png).

[Resumen de RELEASE](screenshots/etapa-7-mvp/release-resumen.png) · [Progreso educativo heredado en RELEASE](screenshots/etapa-7-mvp/release-aprender.png).

## Límites

- La revisión manual de Gmail se comprobó con estados y resultados de prueba. Sigue pendiente validarla con un buzón real autorizado. La automatización real sigue pendiente de despliegue y verificación.
- La reputación compartida y los avisos automáticos que se demuestran usan datos sintéticos. El aviso fijo de `2025550147` es independiente de los votos de Ana y Bruno. Android decide qué llamadas entrega al servicio.
- La validación en esta etapa se hizo en el emulador API 37. La auditoría exhaustiva de accesibilidad y seguridad, y la prueba en un teléfono físico, tienen etapas propias.
- La instancia de QA usó el PostgreSQL instalado, un clúster distinto y API en localhost. No se contrataron servicios ni se hicieron despliegues, commits o push.
