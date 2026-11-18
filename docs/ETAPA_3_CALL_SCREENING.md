# ETAPA 3 — Identificación de llamadas

Hestia identifica llamadas entrantes que Android Telecom entrega a la aplicación, consulta una fuente local de reputación y avisa si el número figura como reportado. La llamada sigue su curso normal en la aplicación de teléfono habitual. Resumen muestra las detecciones de la semana. La etapa está implementada y validada en un emulador Android API 37. La validación en teléfono físico sigue pendiente.

## Servicio y activación

1. **Identificar llamadas** en el onboarding y **Ajustes → Identificación de llamadas** ofrecen el diálogo oficial de Android. `CallSetup` consulta `isRoleAvailable` e `isRoleHeld` para `ROLE_CALL_SCREENING` y actualiza el estado al volver del diálogo o al reanudar la pantalla. Se puede continuar sin conceder el rol.
2. El manifiesto protege el enlace a `HestiaCallScreeningService` con `android.permission.BIND_SCREENING_SERVICE`. El servicio ignora las llamadas salientes y responde de inmediato con una respuesta permisiva para las entrantes, antes de consultar la reputación o guardar datos. El plazo de respuesta de Android es de cinco segundos.
3. Si la llamada tiene un número `tel:` válido, el servicio lo normaliza, consulta `CallReputation.lookup` y genera un evento. El aviso y la escritura se ejecutan de forma asíncrona en el proceso de la aplicación, independientemente de la pantalla abierta.

## Reputación local

La consulta es local y síncrona. La normalización admite separadores comunes, prefijo internacional `00` y el formato móvil chileno de nueve dígitos. Se descartan valores que no representan un número compatible.

| Variante | Fuente local |
| --- | --- |
| DEBUG | Lista fija con `2025550147` reportado y `2025550148` sin reporte, incluidas sus formas internacionales. Ambos casos se marcan como sintéticos. Los demás números devuelven «sin coincidencia». |
| RELEASE | La implementación devuelve «sin coincidencia» para todos los números. Registra las llamadas elegibles, pero en esta etapa carece de una lista real que permita generar un aviso por reputación. |

«Sin coincidencia» solo indica el resultado de la búsqueda local. El número mostrado por la red puede ser suplantado.

## Avisos, registro y privacidad

- Para una coincidencia reportada, Android muestra un aviso local de importancia alta en el canal **Avisos de llamadas**. El aviso de depuración se titula **Prueba · Posible llamada fraudulenta**. Los números sin reportes se registran sin generar un aviso. Android decide si la notificación se muestra encima de la pantalla de llamada. Los avisos usan visibilidad privada y omiten el número. Desde Android 13 se requiere `POST_NOTIFICATIONS`. Deshabilitar los avisos conserva el registro en Resumen.
- DataStore guarda un identificador aleatorio, fecha y hora, resultado y marca sintética en el espacio privado excluido de copias de seguridad. Las entradas anteriores a 30 días se depuran al registrar una detección nueva. El número se usa en memoria para la consulta. Esta etapa no usa red, agenda, audio ni historial telefónico del sistema.
- Resumen muestra las llamadas registradas y sospechosas de la semana local, de lunes a domingo. Las detecciones sintéticas aparecen en la tarjeta **Pruebas de llamadas**, con el total de simulaciones y el desglose entre sospechosas y sin reportes. Un evento indica una detección, con independencia de si se mostró o se revisó un aviso.

## Archivos principales

| Ubicación | Responsabilidad |
| --- | --- |
| `app/src/main/AndroidManifest.xml` y `services/HestiaCallScreeningService.kt` | Registro del servicio protegido, respuesta a Telecom, clasificación y aviso. |
| `features/calls/CallSetup.kt`, `core/navigation/HestiaApp.kt`, `OnboardingScreen.kt` y `SettingsScreen.kt` | Solicitud del rol, estado real y acceso al permiso de avisos. |
| `domain/CallReputation.kt` y `data/CallReputationSource.kt` en debug y release | Contrato de consulta, normalización y fuentes por variante. |
| `data/CallEvents.kt` y `features/summary/SummaryScreen.kt` | Registro privado y conteo semanal. |
| `CallReputationTest.kt` y `HestiaNavigationTest.kt` | Pruebas unitarias de normalización y reputación sintética y regresión instrumental de navegación. |

Las rutas Kotlin son relativas a `app/src/<variante>/java/cl/grupo28/hestia/`, según el conjunto de fuentes.

## Validación disponible

| Comprobación | Resultado y evidencia |
| --- | --- |
| DEBUG y RELEASE | `assembleDebug` y `assembleRelease` aprobados. La ejecución final reutilizó el resultado de RELEASE de una compilación previa exitosa. El registro `build/stage3-validation.log` termina con `BUILD SUCCESSFUL`. |
| Pruebas unitarias | **91 aprobadas**, 0 fallos, 0 errores y 0 omitidas, según los XML de `app/build/test-results/testDebugUnitTest`. Incluyen los dos casos de `CallReputationTest`. |
| Lint | `lintDebug` aprobado. El informe local registra **0 errores y 20 advertencias**. |
| Instrumentación | `assembleDebugAndroidTest` completado. La salida de ADB de la ejecución final de `HestiaNavigationTest` en el emulador de QA indica `OK (1 test)`. |
| Rol, llamadas y Resumen | La sesión de QA en API 37 confirmó el diálogo oficial y el rol concedido. Las llamadas sintéticas se verificaron con la consola del emulador y con `adb emu gsm call`. `dumpsys telecom` mostró `SCREENING_COMPLETED` con `Allow` y el servicio de Hestia. Android registró dos avisos de llamadas y Resumen mostró **3 detecciones de prueba, 2 reportadas**. El comando ADB funcionó al ejecutarlo con acceso a la consola local. |

Los informes de compilación y pruebas son artefactos locales de Gradle. Los resultados del rol y la simulación proceden de la sesión de QA. Estas comprobaciones corresponden al emulador.

## Reproducir en el emulador

1. Instala la variante DEBUG en un emulador de prueba. Usa `adb devices` para confirmar su identificador y sustituye `emulator-5556` si corresponde.
2. En **Ajustes → Identificación de llamadas**, pulsa **Activar identificación**, elige Hestia en el diálogo oficial y comprueba que la app muestre la identificación activada. Habilita los avisos en el sitio indicado por Hestia.
3. Ejecuta cada `call` y espera a que aparezca la llamada antes de ejecutar su `cancel`. El primer número debe generar un aviso y aumentar ambos conteos de prueba. El segundo debe aumentar solo el de registradas.

```powershell
adb -s emulator-5556 emu gsm call 2025550147
adb -s emulator-5556 emu gsm cancel 2025550147
adb -s emulator-5556 emu gsm call 2025550148
adb -s emulator-5556 emu gsm cancel 2025550148
```

Comprueba **Resumen → Pruebas de llamadas**. Los valores se acumulan durante la semana. Para diagnosticar el recorrido, usa `adb -s emulator-5556 shell dumpsys telecom` y busca el resultado `Allow` del servicio de Hestia.

## Límites y ETAPA 4

Android decide qué llamadas entrega. Hestia carece de permiso de agenda en esta etapa, por lo que las llamadas de contactos guardados quedan fuera. Los números privados, restringidos o no disponibles también pueden quedar excluidos. Los conteos reflejan únicamente llamadas entrantes con número utilizable recibidas por el servicio.

En teléfono físico queda pendiente validar una llamada entrante autorizada, la concesión y revocación del rol, el comportamiento con avisos denegados, la entrega con pantalla bloqueada y la accesibilidad con TalkBack. Para validar un aviso de reputación en hardware se necesita una fuente de prueba autorizada.

La implementación usa Android Telecom y RoleManager, disponibles para el mínimo del proyecto, Android 10 (API 29). Reutiliza Compose, Activity Result, AndroidX Core, corrutinas y DataStore sin sumar dependencias, credenciales ni servicios externos. El contrato `CallReputation` permite que la ETAPA 4 incorpore una caché alimentada por reputación compartida. Su eventual sincronización debe ocurrir fuera del flujo de llamada, de modo que la respuesta a Telecom solo dependa del dispositivo.
