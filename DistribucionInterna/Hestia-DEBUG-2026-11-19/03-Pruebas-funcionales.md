# Guía de pruebas funcionales

Usa dos teléfonos de prueba con permiso de sus propietarios. Para revisar reportes, elige un número controlado por el equipo. Registra modelo, versión de Android, paso y resultado en [07 · Reportar errores](07-Reportar-errores.md).

## Llamadas y Teclado

1. Confirma **Hestia** como teléfono predeterminado en **Teléfono → Protección** y activa **Recientes**.
2. Marca el segundo teléfono desde **Teclado**. Borra o corrige dígitos antes de tocar **Llamar**. Si aparece el diálogo del permiso de teléfono, concédelo y pulsa **Llamar** otra vez.
3. Desde el segundo teléfono, llama al equipo de prueba. En intentos separados, contesta, rechaza y deja que el segundo teléfono cuelgue antes de contestar. Comprueba número o nombre disponible, duración cuando corresponda y acceso a **Contestar**, **Rechazar** y **Colgar**.
4. Si Android muestra solo una notificación, registra ese resultado y comprueba si al tocarla se abre la llamada. Los avisos a pantalla completa dependen de Android y sus permisos.

## Contactos

1. En **Teléfono → Contactos**, concede el permiso de Contactos cuando Android lo pida.
2. Busca una persona de prueba por nombre y por los últimos dígitos. Si la persona tiene dos números, abre su ficha y comprueba que puedes escoger el número del segundo teléfono.
3. Revisa la miniatura si el contacto tiene foto. Verifica que **Contactos de confianza** abre la selección de Familia, independiente de la agenda general.

## Recientes y reporte personal

1. Comprueba que el registro de Hestia distingue **Recibida**, **Saliente**, **Perdida** y **Rechazada** cuando esos casos están presentes.
2. Abre las opciones de una llamada del segundo teléfono. Pulsa **Reportar número**, elige un motivo y usa **Revisar reporte → Guardar reporte**. Verifica **Número marcado por ti** en rojo y que aparece **Retirar mi reporte** al reabrir las opciones.
3. Cierra y vuelve a abrir Hestia. Comprueba que el reporte permanece activo. Puedes apagar Wi-Fi y datos móviles para repetir esta revisión. Mantén el modo avión apagado si vas a probar la recepción SIM.
4. Mientras el reporte sigue **Activo**, llama de nuevo desde el mismo número. Observa **Número marcado por ti** en la pantalla de llamada de Hestia. Retira el reporte **después** de terminar la segunda llamada.
5. Comprueba que la entrada vuelve a ofrecer **Reportar número** y que Protección muestra **Retirado**. Si el operador no admite una llamada en las condiciones de red elegidas, registra esa limitación y restablece los ajustes de conectividad iniciales.

## Protección

1. Revisa el marcador predeterminado, la identificación de llamadas y el estado de avisos. Comprueba que cada texto refleja el permiso o rol que elegiste.
2. Abre **Números marcados por ti → Ver reportes personales**. Busca el número de prueba y revisa motivo, fecha y estado. Edita el motivo de un reporte activo y comprueba el cambio desde las opciones del número en **Recientes → Consultar reputación**.
3. **Reputación de demostración** corresponde al entorno **DEMO LOCAL**. La sincronización comunitaria no forma parte de la prueba de dos teléfonos con este paquete.

## Familia

1. Agrega una persona de confianza de prueba con entrada manual o selector de Android. Prueba editar, ordenar y elegir un contacto principal.
2. Reinicia la app y comprueba que el contacto permanece. Si pruebas **Llamar**, confirma la persona y el número antes de continuar en el marcador. Puedes cancelar ahí para probar la navegación sin iniciar otra llamada.

## Aprender y apariencia

1. Abre **Aprender** y elige un tema. Completa un ejercicio y vuelve a abrirlo para comprobar que conserva el progreso.
2. Repite una lección sin Internet. Los enlaces a fuentes oficiales requieren conexión al abrirse.
3. Aumenta el tamaño del texto en **Resumen → Ajustes** o en el sistema. Comprueba que el aviso de llamada, las etiquetas y las acciones se pueden leer y alcanzar, incluido el desplazamiento si hace falta.
