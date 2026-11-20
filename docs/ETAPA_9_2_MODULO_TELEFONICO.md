# ETAPA 9.2 · Módulo telefónico de Hestia

Esta etapa amplía el teléfono propio en DEBUG a partir de la integración de ETAPA 4.2. El piloto se prepara para el Motorola Moto G06 con Android 15. La prueba física de llamadas SIM requiere intervención del usuario y del segundo teléfono. [Preparación del Motorola](ETAPA_9_1_PREPARACION.md)

## Arquitectura y roles

En DEBUG, **Teléfono → Protección → Usar Hestia como teléfono** abre el diálogo oficial de `ROLE_DIALER`. La pantalla comprueba disponibilidad y titular al volver de Android. **Cambiar aplicación de teléfono** abre los ajustes para volver a Google Teléfono. El rol `ROLE_CALL_SCREENING` se concede por separado. El servicio de identificación conserva su consulta a la caché local y deja pasar las llamadas. El flujo de protección ofrece su aviso visual cuando Android permite presentarlo y la notificación de respaldo según los permisos. [Contrato de marcador de Android](https://developer.android.com/develop/connectivity/telecom/dialer-app), [servicio de identificación](https://developer.android.com/reference/android/telecom/CallScreeningService)

La actividad pública atiende `ACTION_DIAL` y comparte el teclado con la página interna. El botón **Llamar** verifica el rol y `CALL_PHONE` antes de usar `TelecomManager.placeCall`. El usuario vuelve a pulsar Llamar después de conceder el permiso. Android gestiona las emergencias con el marcador precargado. `InCallService` recibe los estados de Telecom y envía las acciones para contestar, rechazar, silenciar y colgar. La actividad privada muestra el estado y la duración calculada desde la conexión reportada por Telecom. Al bloquear el dispositivo se ocultan el número y el nombre. Solo se usa el nombre de una coincidencia con un contacto de confianza guardado en Familia.

La política de red, los manifiestos, los archivos de firma y las reglas de reputación se conservan. RELEASE mantiene el alcance de protección, incluyendo sus tres pestañas y el servicio de identificación. La nueva ubicación de Pedir ayuda aplica también a las pantallas comunes de RELEASE.

## Pantallas

- **Recientes** es el primer destino. Sus filas muestran el nombre de confianza o número, icono, tipo escrito, fecha y hora, y señal de riesgo cuando corresponde. El icono de teléfono prepara el número en Teclado. El menú de la fila incluye volver a llamar, ver detalle y consultar reputación. Reportar o retirar un reporte propio usa las identidades y el rango sintético existentes de DEMO LOCAL. Si el número corresponde a una llamada real y carece de coincidencia en Familia, **Agregar a contactos** abre el editor del sistema, donde el usuario puede elegir crear o editar.
- **Historial** se activa por decisión del usuario. Se guardan hasta 30 registros de los últimos 30 días gestionados con Hestia como marcador, con su número permitido, fecha y hora de cierre y tipo. El registro queda local y excluido de copias. El menú **Administrar Recientes** separa **Borrar historial** de **Desactivar Recientes**. Las dos acciones requieren confirmación. Borrar conserva el registro habilitado para futuras llamadas.
- **Contactos** muestra la lista local de confianza con avatares de iniciales, búsqueda por nombre o dígitos y acceso al teclado. Los contactos siguen gestionándose también en Familia. La agenda completa del dispositivo, fotos y `READ_CONTACTS` corresponden a 9.3. El acceso al editor Android usa un intent y queda bajo control del usuario.
- **Teclado** admite entrada numérica, borrar y confirmación de llamada. Conserva el número preparado al cambiar de destino dentro de Teléfono. El acceso externo como marcador ofrece selección de Recientes o Contactos.
- **Protección** reúne los roles, reputación, avisos y herramientas DEBUG. **Pedir ayuda** se abre desde correos de riesgo alto y desde la interfaz de una llamada sospechosa activa de Hestia. Con Google Teléfono, la alerta de identificación informa el riesgo. Ese servicio carece de un evento de fin fiable para mantener abierto un compositor únicamente durante la llamada. Familia conserva contactos, controles y las plantillas que usa la ayuda contextual.

El selector interno muestra texto e iconos, indica su selección y se adapta al ancho y a la escala tipográfica. Los controles de fila exponen un nombre accesible y un área de 48 dp. El historial y los avisos de riesgo expresan el tipo en texto.

## Archivos principales

| Área | Archivos |
| --- | --- |
| Navegación y vistas DEBUG | `PhoneScreen.kt`, `PhoneDirectory.kt`, `PhoneControls.kt`, `CallWarningPreviewNavigation.kt`, `HestiaDialerActivity.kt` |
| Roles, llamadas y registro | `DialerSetup.kt`, `HestiaInCallService.kt`, `InCallSession.kt`, `TelecomCallActivity.kt`, `CallWarningPreviewScreen.kt`, `CallerIdentification.kt`, `PhoneRecents.kt` |
| Ayuda contextual común | `HestiaApp.kt`, `SummaryScreen.kt`, `FamilyScreen.kt`, `EducationScreen.kt`, `EducationContent.kt` |
| Instrumentación | `PhoneModuleTest.kt`, `PrivacyBoundaryTest.kt`, `HestiaNavigationTest.kt` |

## Validación de la etapa

- Gradle completó `assembleDebug`, `assembleRelease`, `testDebugUnitTest`, `lintDebug` y `assembleDebugAndroidTest`. Pasaron 103 pruebas unitarias. Lint informó 0 errores y 31 advertencias.
- En un perfil aislado del emulador pasaron 13 pruebas instrumentales de Teléfono, privacidad, Gmail, Familia y accesibilidad textual. Se repitieron dos pruebas de Teléfono al 130 %, 150 % y 200 %, con seis ejecuciones aprobadas. TalkBack queda pendiente de verificación manual.
- Se inspeccionó el APK RELEASE. Conserva el servicio de identificación, usa HTTPS para su transporte y carece del marcador e `InCallService` de DEBUG y de permisos para red local, agenda o historial telefónico.
- El Moto G06 con Android 15 recibió el APK DEBUG con instalación sobre la existente y firma coincidente. Se conservó la fecha de primera instalación y se verificó el arranque en frío. Google Teléfono seguía como predeterminado y el rol de identificación estaba sin asignar. Los ensayos de llamadas SIM quedan pendientes de la confirmación del usuario.

## Guía de pruebas SIM en el Moto G06

Usa dos números controlados, con consentimiento de quienes operan ambos equipos. Deja ADB conectado si se requiere diagnóstico. Hestia DEBUG ya usa `cl.grupo28.hestia` y el mismo certificado que en 9.1. Los preparativos de roles se hacen desde el teléfono. [Instalación y certificado](ETAPA_9_1_PREPARACION.md)

| Paso | Qué hacer | Qué observar |
| --- | --- | --- |
| 1. Seleccionar el rol | En **Teléfono → Protección → Usar Hestia como teléfono**, elige Hestia en el diálogo Android. | Protección muestra **Hestia** bajo Teléfono predeterminado. Si se rechaza, muestra el titular anterior. |
| 2. Preparar el historial | Abre **Teléfono → Recientes → Activar Recientes**. Otorga el permiso de notificaciones desde Protección si probarás avisos. | Recientes muestra que está habilitado. Los avisos quedan bajo los ajustes de Android. |
| 3. Entrante y contestar | Llama desde el segundo teléfono con el Motorola desbloqueado. Pulsa **Contestar** y luego **Colgar**. | Pantalla entrante, número o contacto de confianza, estado conectado, contador de duración y entrada **Recibida**. |
| 4. Rechazar y perder | Haz dos llamadas más desde el segundo teléfono. Rechaza una en Hestia y deja que el segundo teléfono termine la otra antes de contestar. | Dos entradas distintas **Rechazada** y **Perdida**. |
| 5. Saliente y silenciar | En **Teclado**, marca el número del segundo teléfono y pulsa **Llamar**. Si Android pide permiso, concédelo y confirma Llamar otra vez. Una vez conectada, pulsa **Silenciar** y luego **Colgar**. | El estado del micrófono cambia en la pantalla y aparece una entrada **Saliente**. |
| 6. Bloqueo | Recibe otra llamada con la pantalla bloqueada. | Nombre y número ocultos en la pantalla de Hestia. Los controles quedan accesibles cuando Android la presenta. Si Android usa una notificación, registra esa modalidad. |
| 7. Opciones y contacto | Abre las opciones de la entrada más reciente. Abre Ver detalle y luego vuelve a marcar. | El número se prepara en Teclado. La hoja de llamada finalizada permite consultar la reputación y carece de accesos a Pedir ayuda y Aprender. |
| 8. Restaurar | En **Teléfono → Protección → Cambiar aplicación de teléfono**, elige **Google Teléfono**. | Protección muestra Google Teléfono. La configuración de identificación se controla en el rol separado. |

Registra el paso, la modalidad y el resultado en texto. Usa referencias como **número controlado A** y omite nombres, números completos y contenido privado. La advertencia sobre un número SIM reportado requiere una fuente autorizada de reputación real, pendiente en esta etapa. Los estados de reputación sintéticos se ensayan exclusivamente con sus fixtures DEBUG.
