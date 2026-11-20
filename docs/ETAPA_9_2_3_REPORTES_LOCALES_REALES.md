# ETAPA 9.2.3 · Reportes personales de llamadas

## Arquitectura

`PersonalReports` mantiene un índice local por número E.164. El mismo repositorio alimenta Recientes, Protección, Ajustes y las advertencias telefónicas. Recientes ofrece el reporte personal para un número real válido de cualquier tipo de llamada, incluidos contactos de confianza. Las entradas de prueba DEBUG siguen el formulario, las identidades y las reglas de DEMO LOCAL. La consulta muestra el estado de cada fuente por separado. Un reporte personal no cambia el umbral comunitario ni la métrica de llamadas sospechosas.

## Persistencia y privacidad

Cada registro guarda un UUID, número normalizado, número mostrado en el registro, motivo, descripción de hasta 300 caracteres, creación, estado y última modificación. Las fechas se almacenan como instantes y se muestran en la zona horaria del dispositivo. El registro se asocia al número. El repositorio serializa las escrituras y guarda un único registro por número. Editar conserva la fecha de creación y retirar desactiva el aviso mientras conserva el historial en Protección. Volver a marcar abre un nuevo periodo activo en el mismo registro.

El archivo JSON versionado se escribe atómicamente en `noBackupFilesDir`, cifrado mediante AES-GCM con una clave propia de Android Keystore. La utilidad de cifrado conserva el formato y el alias que ya usa la sesión de Hestia. Una lectura fallida deja el archivo intacto y muestra un estado de error. Los números, notas y motivos quedan en el equipo. Las notificaciones genéricas omiten el número y la descripción. Android excluye esta ubicación de las copias de seguridad y transferencias. [Android Auto Backup](https://developer.android.com/identity/data/autobackup)

## Normalización

La normalización personal usa `PhoneNumberUtils.formatNumberToE164`. Los prefijos `+` y `00` se interpretan como internacionales. Para números nacionales se usa el país de la SIM disponible o la región del dispositivo. Se aceptan espacios, guiones y paréntesis, y se descartan identificadores privados o que Android no puede validar. El registro de nuevas llamadas conserva el número de Telecom. Las entradas antiguas leen el número ya guardado si carecen del campo original. [PhoneNumberUtils](https://developer.android.com/reference/android/telephony/PhoneNumberUtils#formatNumberToE164(java.lang.String,%20java.lang.String))

## Formulario y gestión

- En **Teléfono → Recientes → Más opciones → Reportar número**, elige el motivo, añade una descripción opcional y pulsa **Revisar reporte → Guardar reporte**. Recientes muestra **Número marcado por ti** y ofrece **Editar reporte** y **Retirar mi reporte**. Los reportes locales no requieren autenticación ni conexión.
- En **Teléfono → Protección → Números marcados por ti** puedes buscar, revisar motivo, fechas y estado, editar o retirar. RELEASE usa **Resumen → Ajustes → Números marcados por ti**, con ingreso manual del número porque su configuración conserva el marcador de Android.
- **Consultar reputación** muestra por separado los estados disponibles. La consulta comunitaria sigue usando su caché y sus reglas. El reporte personal se guarda únicamente mediante el repositorio cifrado local.

## Llamadas entrantes

`CallScreeningService` permite la llamada antes de consultar cualquier archivo. Después obtiene la señal personal y la señal comunitaria en segundo plano, sin red. En DEBUG con Hestia como marcador, `InCallService` observa el estado local y presenta el aviso con el motivo cuando el teléfono está desbloqueado. En el modo de identificación DEBUG se usa el aviso flotante existente o su respaldo por notificación. En RELEASE se usa una notificación local. En **Ajustes**, **Incluir llamadas de mis contactos** solicita el permiso opcional necesario para que Android entregue también llamadas de la agenda. RELEASE usa el permiso para esta cobertura y prescinde de consultar la agenda. Los roles y avisos dependen de la configuración de Android. El servicio nunca bloquea ni silencia una llamada. Android puede omitir números privados o contactos según el modo y los permisos. El identificador presentado por la red puede estar suplantado. [Contrato de CallScreeningService](https://developer.android.com/reference/android/telecom/CallScreeningService)

## Prueba SIM y funcionamiento sin Internet

1. En el Moto activa **Recientes** y comprueba Hestia como marcador predeterminado en **Protección**. Para la modalidad RELEASE, activa el rol de identificación y permite notificaciones.
2. Apaga Wi-Fi y datos móviles, deja el modo avión apagado y conserva la SIM registrada. Usa exclusivamente un segundo teléfono de prueba autorizado.
3. Haz una llamada entrante, cuelga y abre la nueva entrada. Guarda un reporte. Revisa el estado en Recientes y Protección. Cierra Hestia y vuelve a abrirla para verificar la persistencia.
4. Llama de nuevo desde el mismo número y comprueba **Número marcado por ti**. Retira el reporte y verifica que desapareció la señal personal. Si el operador depende de una conexión que no esté disponible, registra la limitación por separado.

## Archivos principales

| Área | Archivos |
| --- | --- |
| Modelo, normalización y persistencia | `PersonalCallReport.kt`, `PersonalReports.kt`, `EncryptedDeviceFile.kt`, `KeystoreSessionStore.kt` |
| Formulario, consulta y gestión | `PersonalReportsPanel.kt`, `PhoneScreen.kt`, `PhoneDirectory.kt`, `PhoneRecents.kt`, `SettingsScreen.kt` |
| Screening y presentación | `HestiaCallScreeningService.kt`, `CallerIdentification.kt` en DEBUG y RELEASE, `HestiaInCallService.kt`, `InCallSession.kt`, `CallNotices.kt`, `TelecomCallActivity.kt`, `CallWarningPreviewScreen.kt` |
| Cobertura opcional de agenda | `CallSetup.kt`, los manifiestos Android y el texto de `AgendaOnboardingContent.kt` en RELEASE |
| Regresión | `PersonalReportsTest.kt`, `PersonalCallReportUiTest.kt`, `RecentCallReportTest.kt` |

## Resultados verificados

- Compilaron los APK DEBUG y RELEASE. Pasaron 108 pruebas unitarias, incluidas cinco pruebas nuevas de persistencia, normalización, unicidad, errores de almacenamiento y orden de la respuesta a Telecom. Lint registró 0 errores y 31 advertencias.
- Pasaron 11 pruebas instrumentales en el emulador sin Wi-Fi ni datos. Cubrieron el formulario local, la privacidad en bloqueo, la navegación y la regresión de la interfaz comunitaria con repositorios de prueba. La API comunitaria quedó fuera de esta validación offline.
- Se instaló DEBUG sobre la versión del Moto G06, con el mismo `applicationId`, certificado SHA-256 y fecha de primera instalación. Se verificó el arranque en frío. Dos pruebas instrumentales pasaron en el equipo con Wi-Fi y datos móviles apagados y modo avión apagado. Usaron un archivo y una clave de prueba aislados para verificar guardado cifrado, reapertura, consulta, retiro y presentación del aviso. Al terminar se retiraron los datos de prueba instrumentales.
- El usuario realizó una llamada desde su segundo teléfono. En la entrada perdida resultante se guardó un reporte personal real con Wi-Fi y datos móviles apagados. Recientes mostró la marca y la volvió a mostrar tras detener y reabrir Hestia. La prueba de persistencia se hizo reiniciando el proceso de la aplicación.
- Se retiró el reporte de esa llamada sin conexión. Recientes volvió a ofrecer **Reportar número** y Protección mostró **Retirado**. Se restauraron Wi-Fi y la configuración de pantalla previa. La advertencia también se comprobó en instrumentación.

La primera repetición SIM que el usuario informó como «sin aviso» llegó con el reporte de prueba retirado. El registro de Android sitúa esa llamada a las 22:11, después del retiro y la restauración de Wi-Fi. Se volvió a marcar el número desde esa entrada y se verificó la marca activa antes de coordinar otra llamada.

En la repetición con el reporte activo, el usuario confirmó **Número marcado por ti**. ADB observó el mismo texto en la pantalla de llamada entrante con Wi-Fi y datos móviles apagados y modo avión apagado. Después se retiró el reporte sin conexión, Protección mostró **Retirado** y se restauró la configuración original del Moto. Queda pendiente comprobar la persistencia tras reiniciar todo el dispositivo.

La etapa usa el SDK y el equipo existentes. El costo de infraestructura fue **CLP $0**.
