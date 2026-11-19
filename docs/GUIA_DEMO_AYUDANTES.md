# Demostración de Hestia para ayudantes

## Antes de presentar

Usa un Pixel 10 API 37 de demostración con Hestia DEBUG. Completa el inicio sin cuenta Google ni Gmail. En **Teléfono**, pulsa **Usar Hestia como teléfono** y confirma la solicitud de Android. Abre **Recientes** y elige **Activar Recientes**. En **Familia** crea un contacto ficticio, por ejemplo **Ana de prueba** con `+12025550196`, y márcalo como principal. Usa el emulador para todas las llamadas y confirma cada permiso en Android.

```powershell
.\gradlew.bat assembleDebug
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
$emulador = "emulator-5560"  # Sustituir por el identificador del Pixel 10 de demostración.
& $adb -s $emulador install -r app/build/outputs/apk/debug/app-debug.apk
```

### A. Básica sin backend

Deja Hestia en modo real, sin vincular una cuenta. Mantén el modo avión desactivado para que el emulador reciba la llamada GSM simulada y desactiva Wi-Fi y datos. Mantén abierta la terminal para simular la llamada entrante. Con esta modalidad se pueden mostrar el aviso telefónico de prueba, Recientes, los borradores de Familia y Aprender. Al abrir **Revisar correos** aparece el estado de la revisión manual. El informe comunitario y el correo sintético se explican como parte de la modalidad completa.

```powershell
& $adb -s $emulador shell cmd connectivity airplane-mode disable
& $adb -s $emulador shell svc wifi disable
& $adb -s $emulador shell svc data disable
```

### B. Completa con PostgreSQL, API y Worker locales

Usa Node.js y PostgreSQL ya instalados, y conserva el puerto del archivo `backend/demo.config.json`. Usa terminales de PowerShell separadas. Si las dependencias fijadas ya están instaladas, puedes arrancar directamente. El comando inicia API y Worker por separado, prepara la base local y escucha en localhost. Espera los mensajes **DEMO LOCAL API** y **DEMO LOCAL Worker: ready**.

```powershell
Set-Location backend
$env:LOCAL_DEMO = "true"
npm.cmd run demo:start
```

En el emulador desactiva modo avión y vuelve a habilitar Wi-Fi. En Hestia entra a **Resumen → Revisar correos → Opciones de revisión automática → Activar DEMO LOCAL** si la demo aún está desactivada. Concede el permiso de conexión local cuando se solicite. En **Herramientas de demostración**, comprueba que la identidad sea **Ana**, habilita **Activar avisos en segundo plano** y concede el permiso de avisos. Verifica que el estado de conexión indique **Conectada al computador**. Si partes con una compilación que ya abre DEMO LOCAL, entra directamente a sus herramientas.

```powershell
& $adb -s $emulador shell cmd connectivity airplane-mode disable
& $adb -s $emulador shell svc wifi enable
```

**Ana (alice)** y **Bruno (bob)** son dos identidades sintéticas. La selección de Teléfono es independiente de la de correos. Confirma **Ana** también en **Teléfono → Protección → Reputación de demostración**. Para dejar lista la corroboración, verifica que el número `2025550147` tenga cero reportes comunitarios antes de mostrarlo. Su alerta fija de llamada sigue disponible en la demostración básica. Los números `2025550147` y `2025550196` pertenecen a la lista de prueba usada por Hestia.

## Guion de 6 minutos

| Tiempo | En pantalla | Qué mostrar o decir |
| --- | --- | --- |
| 0:00–0:45 | **Resumen** | «Hestia ayuda a reconocer señales de estafa y decidir qué hacer». Recorre **Estado de protección** y **Actividad de esta semana**. Explica si llamadas está activa y si los correos son de prueba. |
| 0:45–1:45 | Llamada entrante y **Recientes** | Ejecuta el comando GSM. Muestra **SOSPECHOSO**, **Número de prueba** y **Ver por qué**. Cierra la explicación y pulsa **Rechazar**. En la completa, abre **Recientes → Ver opciones → Reportar número**, elige **Posible estafa** y envía el reporte con Ana. Si se abre el formulario al colgar puedes enviarlo allí. En la básica, muestra **Recientes**. |
| 1:45–2:45 | **Protección → Reputación de demostración** | En la completa, cambia a Bruno, sincroniza y consulta `2025550147`. Con un único reporte se lee **Pendiente de corroboración**. Bruno reporta **Suplantación de identidad** y vuelve a consultar. Muestra los dos reportes de fraude independientes. En la básica, abre la explicación o el acceso educativo desde Protección y explica cómo funciona la corroboración. |
| 2:45–3:45 | **Revisar correos** y notificación | En la completa, ejecuta el comando HIGH desde la segunda terminal. Abre el aviso Android, muestra el detalle marcado **DEMO LOCAL · correo sintético** y entra a **Aprender a reconocer correos falsos**. Usa **Volver** y muestra el mismo correo. En la básica, muestra **Revisión manual en este teléfono** y explica que la configuración y evaluación de un buzón real quedan separadas. |
| 3:45–4:40 | **Familia → Pedir ayuda** | Muestra el contacto principal. Elige **Enviar mensaje** y deja visible el borrador editable. Vuelve sin enviar. Explica que Android abre el compositor solo por acción de la persona y que el envío se confirma allí. |
| 4:40–5:35 | **Aprender → Estafas telefónicas** | Abre **¿Te llaman del banco?**, muestra **¿Y qué debería hacer?** y elige **Ir a las preguntas**. Responde, lee la explicación y muestra que se puede reintentar. |
| 5:35–6:00 | **Resumen** | «La advertencia muestra señales, Familia ofrece apoyo y Aprender ayuda a decidir». Recuerda que Android decide qué llamadas entrega y que la automatización Gmail real tiene despliegue y validación pendientes. |

```powershell
# Llamada sintética. Ejecutar desde la terminal con $adb y $emulador.
& $adb -s $emulador emu gsm call 2025550147
```

```powershell
# Solo modalidad completa. Ejecutar desde backend en la segunda terminal.
$env:LOCAL_DEMO = "true"
npm.cmd run demo:emit -- --persona alice --scenario high-risk
```

## Recuperar el escenario

- Si la llamada sigue sonando, ejecuta `& $adb -s $emulador emu gsm cancel 2025550147`.
- Si Telecom no entrega la llamada durante la presentación, en **Teléfono → Protección → Herramientas DEBUG → Probar alerta de llamada** puedes mostrar una vista de prueba. Identifícala como una vista previa.
- En **Teléfono → Protección → Reputación de demostración**, alterna entre Ana y Bruno. Para cada una, abre **Mis reportes** y retira solo el reporte de `2025550147`. Luego pulsa **Sincronizar reputación**. El aviso fijo de la llamada de prueba se conserva.
- En correos, abre **Herramientas de demostración → Limpiar datos demo → Limpiar simulaciones** para la identidad sintética usada. Eso reinicia sus correos, eventos y avisos de prueba. Puedes detener los avisos en segundo plano desde las mismas herramientas.
- En **Aprender**, termina la lección si quedó a medias y usa **Repetir lección** para volver a la situación inicial. La lección permanece marcada como completada. Los contactos y ajustes del emulador permanecen disponibles.
- Si la API no responde, mantén la presentación en modalidad básica. Para reactivar la completa, reinicia los servicios locales, vuelve a **Revisar correos** y pulsa **Actualizar estado**. Para cerrar la sesión de presentación, detén los servicios en su terminal y restaura el modo de red que usaba ese emulador.

El flujo de Android, la edición de contactos, los borradores y el progreso educativo son reales en el emulador. Las llamadas, correos, identidades y reportes que se presentan son de prueba. El Worker procesa el correo sintético y Hestia puede emitir un aviso Android local para HIGH. Su entrega depende del estado del emulador y de los permisos. La demostración completa usa la red local del emulador y del computador.
