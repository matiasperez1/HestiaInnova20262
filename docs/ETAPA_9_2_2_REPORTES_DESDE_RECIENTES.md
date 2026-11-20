# ETAPA 9.2.2 · Reportes desde Recientes

## Causa y alcance

El menú de llamadas finalizadas exigía un número del rango ficticio y ocultaba **Reportar número** si el número coincidía con un contacto de confianza. La lista mantenía también una copia de la reputación por separado. Esa copia podía quedar desfasada después de enviar o retirar un reporte en el menú.

El único servicio disponible para reportes telefónicos es DEMO LOCAL. Utiliza dos identidades de prueba fijas y los endpoints existentes `/demo/calls/reputation`, `/demo/calls/report` y `/demo/calls/withdraw`. Tanto el servidor como `demo.call_report` limitan los números al rango ficticio `2025550100`–`2025550199`. RELEASE conserva su alcance de identificación y carece de Recientes y de un servicio de reportes telefónicos para cuentas reales.

Para un número real válido, **Reportar número** abre su ficha y muestra que el envío requiere un servicio habilitado para cuentas reales. El número se toma del registro y permanece en el dispositivo. Para un número privado, oculto o inválido, el menú solo ofrece las acciones aplicables, como ver detalles. La integración comunitaria de números reales queda pendiente de un servicio que admita identidades reales. El motor, los endpoints y la base de datos de demostración conservan su alcance.

## Flujo de prueba

1. Abre las opciones de una llamada de prueba en **Teléfono → Recientes**. El menú ofrece llamar, consultar reputación, reportar o retirar, agregar a contactos cuando corresponde y ver detalles.
2. Pulsa **Reportar número**, elige una categoría y pulsa **Revisar reporte**. La confirmación muestra el número que figura en la llamada y la categoría. Explica que el reporte describe un comportamiento observado y se guarda con la identidad de prueba indicada.
3. Pulsa **Confirmar envío**. El repositorio utiliza la categoría existente y el identificador canónico del mismo número. Tras la respuesta del servidor, **Entendido** vuelve a Recientes. La lista observa la actualización de la caché compartida.
4. Abre las opciones y selecciona **Retirar mi reporte**. El diálogo confirma qué identidad retira su reporte. Si otro usuario mantiene el suyo, ese reporte sigue contando.

La condición de contacto guardado y el tipo de llamada carecen de efecto sobre el permiso de reportar. Las categorías son las que ya admite DEMO LOCAL. **Consultar reputación** solo lee. Otra identidad corrobora al confirmar su propio reporte cuando la categoría cuenta para la alerta. Un voto de fraude queda **Pendiente de corroboración**. Dos identidades con categorías de fraude alcanzan el umbral configurado de **Sospechoso**. Las otras categorías se contabilizan aparte. Al quedar sin votos, la etiqueta vuelve a **Sin reportes conocidos**.

Una pérdida de conexión deja el resultado como **No pudimos confirmar el envío**. Se conserva el motivo mientras el formulario sigue abierto. El reintento utiliza el mismo endpoint y el servidor aplica la unicidad por identidad y número. El envío y el retiro carecen de cola offline. Si el servidor confirma el cambio y falla la lectura posterior, la interfaz señala el cambio confirmado y conserva la última reputación sincronizada.

## Archivos

| Área | Archivos |
| --- | --- |
| Menú y lista | `PhoneDirectory.kt`, `PhoneControls.kt` |
| Formulario y confirmación | `ContextualCallReport.kt`, `DemoCallReportsCard.kt` |
| Repositorio y caché observada | `DemoCallReputation.kt` |
| Pruebas | `RecentCallReportTest.kt`, `PhoneModuleTest.kt` |
| Transporte USB opcional de prueba | `app/build.gradle.kts` |

## Preparación de la prueba física

La verificación usa una entrada ficticia controlada y el servidor local. DEBUG acepta el argumento `-PlocalDemoUsb=true` para dirigir el transporte de demostración a `127.0.0.1`. El reenvío USB usa `adb reverse tcp:8787 tcp:8787`. El APK habitual conserva la dirección del emulador. Estos parámetros se limitan al transporte de DEMO LOCAL.

## Resultados de compilación y servicio

- Gradle completó `assembleDebug`, `assembleRelease`, `testDebugUnitTest`, `lintDebug` y `assembleDebugAndroidTest`. Pasaron las 103 pruebas unitarias. Lint informó 0 errores y 31 advertencias.
- Pasaron 10 pruebas instrumentales en el emulador. Cubrieron los cuatro tipos de llamada, contactos de confianza, números privados e inválidos, la ficha local para números reales, la confirmación, el envío, la actualización de reputación, el retiro y el reintento tras una pérdida de conexión simulada. Se incluyó el recorrido contra la API local.
- Una prueba directa contra la API local confirmó que dos envíos idénticos del mismo autor cuentan una vez, que otro autor puede corroborar y que retirar el propio reporte conserva el ajeno. Se retiraron los reportes de prueba al terminar.
- En el Moto G06 con Android 15, la actualización DEBUG mantuvo paquete, certificado y fecha de primera instalación. Se comprobó el arranque en frío y la entrada **Recientes → Reportar número** con una llamada real ya presente. La ficha mostró el estado del servicio para números reales. Los datos del contacto y del historial permanecieron en el equipo.
- En el mismo Moto pasó una prueba instrumental sobre el menú con el número ficticio `2025550196`. Usó el repositorio, el transporte USB y la API local reales. Se verificaron el motivo, la confirmación, un voto por identidad pese a repetir el envío, la corroboración y el retiro con actualización de reputación. La llamada se representó con una entrada de prueba en memoria. El historial personal permaneció intacto. Al cerrar la prueba se retiraron ambos reportes ficticios y se restauraron DEBUG habitual y la configuración USB anterior.
