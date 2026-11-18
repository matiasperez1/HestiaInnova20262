# ETAPA 4.3 · Reportes contextuales de llamadas

La función está disponible en DEBUG con Hestia como teléfono predeterminado y el backend DEMO LOCAL. Usa la identidad sintética elegida en **Teléfono → Protección → Reportes compartidos**. Ana y Bruno conservan reportes independientes y **Mis reportes** permite corregir su categoría o retirarlos.

## Flujos

- **Durante la llamada.** Pulsa **Reportar** en los controles de la llamada. La tarjeta **SOSPECHOSO** también ofrece **Reportar este número**. El formulario muestra el número entregado por Telecom y pide una categoría. Al confirmar **Reportar y colgar**, Hestia termina la llamada e inicia el envío. El formulario queda abierto para mostrar el resultado y permitir reintentar si falla la conexión. **Cancelar** deja la llamada conectada.
- **Después de colgar.** Hestia ofrece el formulario automático para una llamada entrante contestada que mostró la advertencia **SOSPECHOSO**, si la pantalla sigue visible, el equipo está desbloqueado, el número no está en los contactos de confianza y la API confirma que la identidad vigente aún no lo reportó. **Ahora no** cierra la oferta. Las llamadas sin alerta se pueden reportar manualmente desde Recientes si el registro está activado. El servicio no inicia una actividad nueva al finalizar la llamada. [Restricciones de Android para iniciar actividades](https://developer.android.com/guide/components/activities/secure-bal).
- **Desde Recientes.** Cada entrada tiene **Ver opciones**. **Volver a llamar** abre el marcador con el número y el usuario confirma allí. **Reportar número** abre un formulario aparte que nunca realiza una llamada. **Consultar reputación** muestra el estado comunitario y sus categorías. Las llamadas perdidas y rechazadas pueden reportarse aquí. Se muestra el nombre cuando coincide con un contacto de confianza local.

El formulario comparte lenguaje, tipografía y colores con las pantallas de Hestia. Se muestran cinco categorías, el número en solo lectura y botones con texto. El formulario se desplaza si la fuente ampliada necesita más espacio. [Gomez-Hernandez et al. (2023)](https://mhealth.jmir.org/2023/1/e43186), [decisiones y referencias de la etapa anterior](ETAPA_4_2_DIALER_INCLUSIVO.md#decisiones-de-diseño).

## Categorías y umbral

| Categoría | Cuenta para alerta comunitaria de posible fraude |
| --- | --- |
| Posible estafa | Sí |
| Suplantación de identidad | Sí |
| Publicidad no deseada | No |
| Acoso o llamadas insistentes | No |
| Otro | No |

Con un reporte de fraude el número queda **Pendiente de corroboración**. Con dos identidades distintas queda **Sospechoso**. Si solo hay otras categorías, se muestra **Sin reportes de fraude** y su desglose. La migración amplía las categorías y conserva las filas previas, incluidas las categorías históricas de fraude. `2025550147` y `2025550148` admiten reportes de demostración, y conservan sus respectivos veredictos fijos de ETAPA 3 en las pantallas de llamada. Los estados de consulta describen los reportes comunitarios. Un reintento idéntico no agrega un segundo reporte. Cambiar la categoría requiere corregir el reporte propio.

## Privacidad y conexión

Solo los números ficticios del DEMO LOCAL con identificación disponible pueden reportarse. Nunca se envía automáticamente un voto ni se escribe el número en logs de Hestia. La red se usa para consultas y reportes explícitos, actualizar vistas de reputación y comprobar elegibilidad al finalizar. La identificación de la llamada sigue leyendo la caché. El formulario informa si no pudo confirmar el envío, conserva la selección mientras está abierto y permite reintentar. No existe una cola offline. Los números de llamadas se guardan en Recientes únicamente si el usuario activó ese registro. El estado de «Ahora no» se asocia a la entrada y a la identidad seleccionada. Sin Recientes, el número de la oferta queda solo en memoria. La comparación para suprimir la oferta usa los contactos locales de confianza de Hestia. No se añadió acceso a la agenda del sistema.

## Preparar y probar

Con el PostgreSQL local en `PATH`, detén la instancia anterior de la API si está ejecutándose. Desde la raíz del repositorio, en PowerShell:

```powershell
Set-Location backend
$env:LOCAL_DEMO = 'true'
npm.cmd run demo:prepare
npm.cmd test
npm.cmd run demo:api
```

En una segunda terminal, desde la raíz:

```powershell
.\gradlew.bat assembleDebug assembleRelease testDebugUnitTest lintDebug
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
$emulador = 'emulator-5554'
& $adb -s $emulador install -r app/build/outputs/apk/debug/app-debug.apk
& $adb -s $emulador emu gsm call 2025550147
& $adb -s $emulador emu gsm call 2025550148
# Ejecutar de una en una. Para una perdida, cancelar sin contestar.
& $adb -s $emulador emu gsm call 2025550151
& $adb -s $emulador emu gsm cancel 2025550151
& $adb -s $emulador emu gsm call 2025550152
```

Selecciona Hestia como teléfono predeterminado, permite la conexión local en Protección y, para los casos perdidos o rechazados, activa Recientes. Para probar el fallo de red, detén la API con Ctrl+C y usa **Reportar número** desde Recientes o **Reportar este número** durante una llamada. Reinicia la API y reintenta si deseas enviar el reporte.

## Resultados ejecutados

| Prueba en Pixel 10, API 37 | Resultado |
| --- | --- |
| `2025550147` en llamada | Telecom completó el número. Ana envió «Posible estafa». El siguiente intento indicó «Ya reportado por ti» y al colgar no volvió a preguntar. |
| `2025550148` y publicidad | En la validación inicial se envió «Publicidad no deseada» y PostgreSQL registró cero votos de fraude para ese número. El comportamiento vigente al colgar una llamada sin alerta es volver sin abrir el formulario. |
| Perdida `2025550151` y rechazada `2025550152` | Se abrieron por separado desde Recientes, con el estado correcto y el número correspondiente. Se enviaron «Acoso o llamadas insistentes» y «Otro». |
| Ana y Bruno | Bruno envió «Suplantación de identidad» sobre `0147`. PostgreSQL registró dos votos de fraude de autores distintos. Editar el de Bruno a publicidad dejó el total en dos y el conteo de fraude en uno. Retirarlo conservó el de Ana. |
| Contacto conocido, descarte y segundo plano | El contacto de confianza se mostró en Recientes por nombre y no abrió el formulario al colgar. Para un número sin guardar se usó «Ahora no». Al finalizar una llamada desde Inicio no se abrió otra actividad y se encontró la sugerencia en Recientes. |
| Fuente al 130 % | Se mostraron el número y las cinco categorías. Se seleccionó Suplantación de identidad, se confirmó el envío y se colgó desde Hestia. |
| API detenida | El formulario mostró que no pudo confirmar el envío. En PostgreSQL se comprobó que no había un reporte para ese intento tras reiniciar la API. |

Pasaron `assembleDebug`, `assembleRelease`, `testDebugUnitTest` con 95 pruebas y `lintDebug`. La suite backend tuvo 51 pruebas aprobadas y una omitida que requiere `TEST_DATABASE_URL`. La suite verificó también el umbral con categorías separadas, la duplicación concurrente y que reaplicar la migración conserva un reporte histórico. Se retiraron los reportes y el contacto creados para QA.

Pendientes de validación en un teléfono físico y con TalkBack. El alcance de los reportes sigue limitado a números ficticios. Para contactos conocidos, se consultó únicamente la lista local de confianza de Hestia.

### Capturas reales

[Durante una llamada](../build/stage43-durante-llamada.png) · [Después de colgar](../build/stage43-postllamada.png) · [Opciones en Recientes](../build/stage43-recientes-opciones.png) · [Sugerencia persistente](../build/stage43-sugerencia-recientes.png) · [Texto al 130 %](../build/stage43-texto-130.png) · [API detenida](../build/stage43-sin-backend.png)
