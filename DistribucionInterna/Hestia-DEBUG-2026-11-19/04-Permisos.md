# Permisos y roles en Hestia DEBUG

Concede solo los permisos que correspondan a la función que quieras probar. Puedes revisarlos después en **Ajustes de Android → Aplicaciones → Hestia → Permisos**.

| Permiso o rol | Motivo | Cuándo se necesita |
| --- | --- | --- |
| Contactos (`READ_CONTACTS`) | Buscar la agenda y mostrar foto y números disponibles. También permite que Android considere números guardados para identificación. | Opcional. El teclado manual y la entrada manual de Familia siguen disponibles. |
| Teléfono predeterminado (`ROLE_DIALER`) | Dar a Hestia la pantalla de llamadas, el marcador y el registro propio. | Para probar llamadas gestionadas por Hestia y Recientes con datos reales. Android pide elegir la app. |
| Teléfono (`CALL_PHONE`) | Iniciar desde Hestia la llamada que tú confirmas en el teclado. | Según el estado del permiso al usar **Llamar**. |
| Identificación de llamadas (`ROLE_CALL_SCREENING`) | Recibir datos de llamadas elegibles y mostrar avisos basados en una consulta local. | Para probar identificación como rol independiente, incluso si Google Teléfono sigue como marcador. |
| Notificaciones (`POST_NOTIFICATIONS`) | Mostrar avisos de llamadas y acceso a la pantalla de llamada cuando corresponda. | Opcional. Android pide el permiso desde Android 13. |
| Avisos en pantalla completa (`USE_FULL_SCREEN_INTENT`) | Permitir a Android presentar una llamada entrante aunque la app esté en segundo plano. | Configuración especial dependiente de Android. Pulsa **Permitir avisos a pantalla completa** en Protección si aparece. |
| Internet y estado de red (`INTERNET`, `ACCESS_NETWORK_STATE`) | Permisos de red declarados por la app y sus bibliotecas. | Android los concede al instalar. Los reportes personales y la consulta local de llamadas funcionan sin Internet. |
| Red local (`ACCESS_LOCAL_NETWORK`) | Conectar con un backend de demostración local. | Solo para ensayos con un entorno DEMO LOCAL configurado. Android 17 puede pedir este permiso. |
| Servicio en primer plano (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`) | Mantener la prueba de sincronización DEMO LOCAL mientras usa una notificación visible. | Solo para el diagnóstico de demostración. |

El manifiesto incluye permisos técnicos de dependencias como `WAKE_LOCK` para tareas de recepción, `com.google.android.c2dm.permission.RECEIVE` para el registro de Firebase Messaging y `USE_BIOMETRIC` y `USE_FINGERPRINT` para bibliotecas de credenciales. La app declara un permiso interno de firma para sus receptores dinámicos. Android utiliza además `BIND_SCREENING_SERVICE` y `BIND_INCALL_SERVICE` para controlar quién puede enlazar sus servicios de Telecom. Los flujos cotidianos de este paquete se pueden evaluar sin cuenta Google ni autorización Gmail.

Hestia no solicita micrófono para analizar llamadas ni permiso para leer el historial telefónico de Android. Recientes guarda solo llamadas gestionadas por Hestia cuando lo activas.
