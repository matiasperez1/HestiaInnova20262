# Hestia — Decisiones de diseño, ETAPA 1.2

## Alcance

Se conservan Resumen, Aprender y Familia, el onboarding, las cinco lecciones, el progreso local y los contactos. Cambio visual sin dependencias, permisos, integraciones, datos de demostración ni modificaciones de reglas o persistencia. Identidad técnica: `cl.grupo28.hestia`.

## Investigación y límites

- [Gomez-Hernandez et al. (2023)](https://mhealth.jmir.org/2023/1/e43186): revisión sistemática consultada en texto completo. Sintetiza 40 trabajos y 27 recomendaciones; simplificar y ampliar/separar controles son las recomendaciones con mayor respaldo. Fundamenta navegación consistente, controles legibles y menor carga visual. La heterogeneidad de los trabajos impide asumir que una interfaz funciona para todas las personas mayores.
- [Darvishy y Hutter (2017)](https://doi.org/10.1007/978-3-319-60597-5_22): referencia considerada; no se logró acceder al texto durante esta tarea. No se atribuyen resultados específicos no comprobados.
- [Liu y Wang](https://doi.org/10.1080/10447318.2024.2338659): acceso del editor bloqueado. Se utiliza la síntesis proporcionada en la solicitud sobre colores cálidos, luminosidad alta y saturación media. La muestra del norte de China no establece preferencias universales ni de personas chilenas.
- [Zhang et al.](https://doi.org/10.1080/10447318.2022.2131264): texto no recuperado. Autonomía, competencia y relaciones se consideran desde la síntesis proporcionada, sin afirmar una reproducción del estudio.
- No se encontró `Clase Productos Digitales y Personas Mayores_2026v2.pdf` en el proyecto. Las orientaciones de la asignatura aquí aplicadas provienen del texto de la solicitud, no de una lectura del PDF.

Estas decisiones son hipótesis informadas de diseño; no constituyen validación con participantes.

## Color y contraste

| Uso | Color |
|---|---|
| Fondo / tarjetas | `#F7F2E9` / `#FFF9F0` |
| Texto principal / secundario | `#24211F` / `#514A44` |
| Acciones / acento | `#8A3F20` / `#D88A2D` |
| Destacados | `#F4DDB8` |
| Positivo / fondo positivo | `#435A3B` / `#E7EBDD` |
| Riesgo / fondo riesgo | `#963B32` / `#F4DEDA` |
| Bordes | `#CFC2B2` |

El esquema se define en `core/theme` y se consume mediante MaterialTheme, sin colores dinámicos. Ámbar decora la llama; no se utiliza para texto pequeño sobre claro. Terracota identifica acciones y el verde queda como apoyo, sin predominio visual. Eliminar usa texto explícito y color de riesgo, con confirmación que nombra al contacto.

Contrastes calculados mediante luminancia relativa WCAG sobre tarjeta: texto principal 15,29:1; secundario 8,31:1; terracota 7,13:1; positivo 7,26:1; riesgo 6,76:1. El texto claro de botones terracota también tiene 7,13:1. Son verificaciones de estas parejas, no una certificación integral WCAG. Bordes suaves decorativos no sustituyen etiquetas; controles delineados emplean el contorno oscuro.

## Tipografía, componentes y navegación

Sans serif del sistema: títulos 28–32 sp, tarjetas 20–22 sp, cuerpo 18 sp, secundarios 16 sp, botones 18 sp, pestañas 16 sp y métricas reales 36 sp. Sin etiquetas de 12 sp. Se conserva fontScale del sistema multiplicado por la preferencia local. Alturas mínimas permiten crecer y las páginas permiten desplazamiento.

Botones principales/secundarios de al menos 56 dp; acciones textuales de al menos 48 dp; tarjetas separadas y márgenes laterales de 22 dp. Acciones de tarjetas separadas 14 dp, pestañas y enlaces de fuentes 12 dp. Las tres etiquetas inferiores permanecen visibles, con fondo y negrita para la selección y semántica de pestaña.

Resumen separa llamadas y correos en tarjetas y expresa ausencia como «Sin datos», nunca cero ficticio. Aprender conserva preguntas y fuentes, añade símbolos temáticos y la instrucción «Toca para aprender». Familia conserva entrada manual, marcador y eliminación confirmada. Guardado, errores, respuestas educativas, cambios de lista y progreso siguen usando el estado local real existente.

## Accesibilidad y autonomía

Se conservan preferencias de texto, contraste y reducción de animaciones. No hay gestos de arrastre ni controles que obliguen a memorizar información. Iconos decorativos acompañan etiquetas; métricas exponen una descripción conjunta, los títulos tienen semántica de encabezado y las llamadas/eliminaciones nombran al destinatario.

Lectura en voz alta continúa pendiente, tal como muestra Ajustes. Una modalidad simplificada opcional tampoco está implementada en la base actual. No se crean interruptores sin efecto ni nuevas funciones en esta etapa. La navegación de tres secciones es la estructura existente; no se presenta como una preferencia alternativa. Estas dos opciones necesitan una etapa funcional autorizada.

La prueba instrumental existente recorre onboarding, navegación, personalización de texto/contraste, lecciones, persistencia y contactos, interceptando los intents externos. Incluye captura con texto muy grande. No sustituye una auditoría auditiva con TalkBack, ni pruebas físicas en Android 10, ni una matriz completa de tamaños y escalas. Revisar también diálogos con teclado, escalas extremas y orientación horizontal antes de una entrega pública.

## Validación con personas mayores chilenas

Probar con participantes con distinta experiencia digital y condiciones visuales/motoras, con consentimiento y datos de prueba. Pedir tareas concretas: explicar el estado de protección, distinguir ausencia de datos de cero, completar una lección y agregar/llamar/eliminar un contacto. Medir éxito sin ayuda, errores, tiempo y comprensión; observar tamaño de letra, selección y lectura de confirmaciones. Comparar preferencias cromáticas sin sugerir una respuesta correcta.

Evaluar autonomía (elección de preferencias y control de contactos), competencia (comprensión del feedback y recuperación de errores) y relaciones (utilidad del contacto de confianza sin perder independencia). Incluir TalkBack y pantallas pequeñas. Registrar hallazgos y ajustar antes de afirmar usabilidad validada.


## Archivos de esta etapa

- `core/theme/Color.kt`, `Theme.kt`, `Type.kt`: paleta completa, superficies Material 3 y escala tipográfica.
- `core/components/HestiaIcons.kt`: marca que usa colores del tema.
- `core/navigation/HestiaApp.kt`: separación y peso de las pestañas.
- `features/summary/SummaryScreen.kt`: tarjetas separadas y ausencia de datos explícita.
- `features/education/EducationScreen.kt`: iconos de lecciones, indicación de acción y separación de fuentes.
- `features/family/FamilyScreen.kt`: estilo de la acción destructiva; los controles compartidos reciben la nueva tipografía y colores.
- `AGENTS.md`, `MVP_SPEC.md`, `README.md` y este documento: sistema vigente y estados consistentes.

Los archivos Kotlin anteriores están bajo `app/src/main/java/cl/grupo28/hestia/`. No cambian Gradle, dependencias, manifiesto, identificadores ni repositorios de datos.


## Resultados técnicos

Validación final con JDK 25 de Android Studio: `assembleDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest` y `lintDebug`, BUILD SUCCESSFUL. Siete pruebas unitarias y tres instrumentales, sin fallos, en Pixel 10 emulado API 37. No se ejecutaron pruebas con participantes.

Lint: cero errores, 17 advertencias: siete avisos de versiones de dependencias, siete recursos sin uso, una etiqueta redundante, un aviso de versión disponible y una comprobación personalizada obsoleta de Lifecycle. El ejecutor instrumental también avisa del uso de `sun.misc.Unsafe` en protobuf; no pertenece al código de Hestia. No se actualizan dependencias para silenciar estos avisos.

Una repetición intermedia fue interrumpida por una instalación concurrente para capturas; se repitió sin concurrencia y la validación final pasó. No se oculta esa ejecución como un resultado aprobado.


Capturas revisadas en `build/review-stage12`: `resumen-completo.png`, `aprender-completo.png`, `familia-estable.png`, `familia-360dp.png` y `resumen-texto-grande.png`. La revisión adicional de Familia en 360 × 640 dp conserva los tres destinos y el botón principal legibles; no es una matriz completa de pantallas pequeñas. Resolución y densidad del emulador restauradas al finalizar. El progreso de cinco lecciones de la captura final proviene de respuestas efectuadas por la prueba instrumental, no de un valor precargado en producción.
