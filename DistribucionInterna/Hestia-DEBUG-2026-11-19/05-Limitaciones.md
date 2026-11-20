# Estado y limitaciones del paquete

| Función | Estado y evidencia |
| --- | --- |
| Instalar en Android 15 | Instalación física comprobada en un Moto G06 con API 35. Consulta [Verificación](VERIFICACION.md) para las pruebas de este APK. |
| Teléfono y Contactos | Implementados en DEBUG. En el Moto G06 se revisaron el rol, la agenda real, la búsqueda y el selector de número. También hay evidencia de recepción SIM con la pantalla de Hestia. El comportamiento depende de los permisos, el fabricante, la SIM y el operador. |
| Recientes | Implementado y probado con llamadas gestionadas por Hestia. Conserva un registro acotado de hasta 30 llamadas de los últimos 30 días. El usuario debe activarlo. |
| Reportes personales reales | Implementados y verificados en el Moto G06. Se guardó localmente un reporte procedente de una llamada SIM real, persistió tras reabrir la app y se retiró offline. En una llamada posterior se observó **Número marcado por ti** con Wi-Fi y datos móviles apagados. La persistencia después de reiniciar todo el teléfono sigue pendiente de validación física. |
| Protección y aviso rojo | El aviso personal es independiente de la reputación comunitaria. Se comprobó la interfaz en el Moto y con texto del sistema al 200 % en emulador. La presentación puede depender de los avisos de Android y el bloqueo de pantalla. |
| Familia y Aprender | Implementados con almacenamiento local. Pruebas automáticas verificaron navegación, estado local y flujos asociados. Los vínculos educativos externos requieren conexión. |
| Gmail real y cuenta Google | La compilación interna omite la configuración de OAuth, Supabase, API y Firebase. La conexión de Gmail real, la sincronización automática y los avisos remotos carecen de validación de extremo a extremo para esta entrega. |
| Sincronización comunitaria | Implementada para **DEMO LOCAL** con números ficticios e identidades de prueba. Tuvo validación anterior contra un backend local de prueba. Este paquete no incluye backend ni despliegue para teléfonos de compañeros. La sincronización comunitaria en uso real sigue pendiente. |

Los reportes personales se guardan cifrados en el equipo. Un reporte personal no se envía a la comunidad. La marca sirve como señal de precaución, porque el número que presenta la red puede ser suplantado. Hestia no bloquea llamadas automáticamente.

Los números privados y algunas llamadas que Android no entrega pueden quedar fuera de la identificación. La agenda es opcional y las emergencias quedan a cargo del sistema. **Resumen** distingue fuentes consultadas de las que figuran **Sin datos**. Esta edición DEBUG se distribuye solo dentro del equipo.
