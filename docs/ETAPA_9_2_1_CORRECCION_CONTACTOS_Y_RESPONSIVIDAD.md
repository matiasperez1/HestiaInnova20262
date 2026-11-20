# ETAPA 9.2.1 · Contactos, llamadas y diseño adaptable

## Causas raíz

- **Onboarding.** El paso 5 estaba excluido de los botones de permisos y solo ofrecía avanzar. El manifiesto DEBUG tampoco declaraba `READ_CONTACTS`. Familia podía elegir un número con el selector puntual de Android y guardarlo en su lista local. Ese flujo carecía de acceso de lectura a la agenda completa.
- **Llamadas desde Contactos.** Teléfono mostraba solo la lista local de Familia. El icono preparaba un número en Teclado. El marcador y Telecom ya funcionaban como vías de llamada, pero faltaban la consulta al proveedor Android y la selección entre números de una misma persona.
- **Pestañas.** Se reprodujo la columna de cuatro botones en el Moto G06 con 720 px de ancho, densidad 280 y texto al 100 %. El `FlowRow` exigía 380 dp de ancho interior para habilitar cuatro columnas. Tras el espacio lateral quedaban unos 379,4 dp. La fórmula de dos columnas dividía 643 px en dos anchos de 321,5 px. Al redondearlos a 322 px, las dos pestañas más los 21 px de separación requerían 665 px frente a los 664 px disponibles. La segunda pestaña saltaba de línea.

## Cambios

- **Agenda y permisos.** DEBUG declara `READ_CONTACTS` y lo solicita con el diálogo oficial. El paso 5 y Contactos explican el uso antes de solicitarlo. El resultado y el retorno de Ajustes actualizan el estado. Un rechazo permite reintentar cuando Android ofrece otro diálogo y abrir Ajustes. La revocación oculta la agenda y conserva las selecciones guardadas en Familia. **Teléfono → Protección → Herramientas DEBUG → Probar paso de agenda** repite el paso opcional sin escribir el progreso del asistente. [Permisos de ejecución](https://developer.android.com/training/permissions/requesting)
- **Lectura local.** `DeviceAgenda` consulta `ContactsContract.CommonDataKinds.Phone` en segundo plano, agrupa por contacto, deduplica números equivalentes y obtiene la miniatura del proveedor cuando existe. La lista se consulta mientras está en uso y se mantiene en memoria. La búsqueda admite nombre sin distinción de tildes y fragmentos de cualquiera de los números. Solo **Agregar a Familia** guarda el nombre y el número elegidos en el DataStore existente. [Proveedor de teléfonos](https://developer.android.com/reference/android/provider/ContactsContract.CommonDataKinds.Phone)
- **Llamadas.** Contactos muestra un icono para abrir los números de cada persona. Cada número tiene su propia acción **Llamar**. Teclado permite editar el número con el borrador, muestra coincidencias desde tres dígitos y deja elegir el número que se quiere preparar. Las dos entradas comparten la llamada explícita a `TelecomManager.placeCall` y comprueban por separado `ROLE_DIALER` y `CALL_PHONE`. El teclado funciona aunque no se conceda acceso a la agenda. Se conserva `InCallService`, el registro propio y la caché de `CallScreeningService`. [Contrato de marcador de Android](https://developer.android.com/develop/connectivity/telecom/dialer-app)
- **Navegación.** Una sola `Row` distribuye las cuatro pestañas. Usa anchos iguales cuando caben y reparte el espacio restante según el ancho de cada etiqueta en ventanas más estrechas. Si la escala de texto hace que el conjunto exceda el ancho, la fila se desplaza horizontalmente. Cada pestaña conserva una etiqueta, selección accesible y superficie táctil de 48 dp o más.
- **RELEASE.** Conserva el asistente anterior y sus permisos. La consulta integral y las herramientas de repetición se compilan solo en DEBUG. Los datos de la agenda permanecen en el dispositivo. No se agregó permiso de escritura ni lectura del registro de llamadas.

## Archivos

| Área | Archivos |
| --- | --- |
| Permiso DEBUG | `app/src/debug/AndroidManifest.xml` |
| Agenda | `DeviceAgenda.kt`, `AgendaAccess.kt`, `AgendaScreen.kt` |
| Onboarding | `OnboardingScreen.kt`, `AgendaOnboardingContent.kt` en DEBUG y RELEASE |
| Teléfono | `PhoneScreen.kt`, `HestiaDialerActivity.kt`, `PhoneControls.kt` |
| Regresión | `AgendaModuleTest.kt`, `PhoneModuleTest.kt`, `PrivacyBoundaryTest.kt` |

## Instalación y comprobación en el Moto G06

- Motorola Moto G06 con Android 15 y API 35. ADB confirmó el dispositivo, el paquete `cl.grupo28.hestia` y la coincidencia SHA-256 entre el certificado instalado y el nuevo. La instalación `-r` conservó la fecha de primera instalación. El arranque en frío llegó a `MainActivity`.
- La aplicación ya tenía el rol de teléfono en este equipo. Android informó `READ_CONTACTS` concedido por ese rol. Se conservó esa elección. La agenda cargó y el selector de un contacto real mostró la acción Llamar. **Probar paso de agenda** abrió el paso 5, mostró la agenda y permitió encontrar un contacto por los últimos dígitos de su número. El guardado de una persona real en Familia y las llamadas SIM quedan para la prueba manual.
- Al 100 % del tamaño de texto del sistema, las cuatro etiquetas estaban completas en la misma fila. ADB las midió en el intervalo vertical común de 221 a 260 px. Las capturas de Recientes y Contactos ocultan el contenido privado. Con texto ampliado se observó desplazamiento horizontal en el Moto. El recorrido completo en las cuatro escalas se verificó en el emulador.

[Recientes](images/etapa_9_2_1/recientes.png) · [Contactos](images/etapa_9_2_1/contactos.png) · [Teclado](images/etapa_9_2_1/teclado.png) · [Protección](images/etapa_9_2_1/proteccion.png)

## Pruebas automáticas

- Gradle completó `assembleDebug`, `assembleRelease`, `testDebugUnitTest`, `lintDebug` y `assembleDebugAndroidTest`. Pasaron 103 pruebas unitarias. Lint registró 0 errores y 31 advertencias.
- Pasaron 19 pruebas instrumentales en el perfil aislado del emulador. Incluyen agenda vacía, permiso concedido, rechazo y revocación, búsqueda, selección e inicio de llamada mediante un doble de prueba, importación explícita y proveedor real de Android con varios números, duplicados y miniatura. También pasaron las pruebas de privacidad, Familia, Gmail y accesibilidad textual.
- En el mismo perfil se comprobó además el diálogo oficial con ADB. Se rechazó el acceso, se volvió a solicitar, se concedió y se observó la agenda. Tras revocar el permiso, volvió a mostrarse la opción para recuperar el acceso.
- La navegación pasó a densidad 420 y se repitió a densidad 280 con texto al 100 %, 130 %, 150 % y 200 %. Las pruebas comprobaron las cuatro pestañas en la misma fila, su alcance por desplazamiento y objetivos táctiles de al menos 48 dp.

## Prueba SIM en el Moto G06

1. Abre **Teléfono → Protección → Usar Hestia como teléfono** y elige Hestia en el diálogo de Android. Activa **Recientes** para comprobar el registro propio.
2. En **Contactos**, pulsa **Permitir acceso a mis contactos** y autoriza en Android. Busca el nombre o parte del número de una persona de prueba. Abre la persona, elige el número que corresponde al segundo teléfono y pulsa **Llamar**. Comprueba que solo suene el número escogido. Si faltara el permiso telefónico, concédelo y vuelve a pulsar Llamar.
3. En **Teclado**, escribe y corrige un número. Si aparece una coincidencia, selecciona el número y pulsa Llamar. Con la llamada conectada, comprueba Silenciar y pulsa Colgar. Desde el segundo equipo, llama al Moto, contesta una vez y rechaza otra. Revisa los tipos en Recientes.
4. Para comprobar el permiso por separado, usa **Probar paso de agenda**. Elige una persona y un número de prueba, pulsa **Agregar a Familia** y revisa la lista de confianza. Para revocar, abre **Ajustes de Android → Apps → Hestia → Permisos → Contactos** y elige **No permitir**. Al regresar, comprueba que la agenda queda oculta y el teclado disponible. **Continuar sin agenda** debe cerrar la prueba o avanzar al paso siguiente del asistente real.
5. Para restaurar el marcador anterior, abre **Teléfono → Protección → Cambiar aplicación de teléfono** y elige **Google Teléfono**.
