# Hestia — Revisión visual de la ETAPA 1

> Documento histórico. Este diseño fue reemplazado por la [ETAPA 1.1](ETAPA_1_1.md). No usar sus pantallas o requisitos como alcance vigente; consultar AGENTS.md y MVP_SPEC.md.

## Alcance

Primera interfaz local con Kotlin, Compose y Material 3. Paleta marfil, azul profundo, petróleo, ámbar y coral. La marca es un hogar que resguarda una llama abstracta. No se ha conectado ningún servicio ni se han agregado permisos Android.

El estado de todas las protecciones es pendiente de configuración. La ausencia de alertas no equivale a seguridad. El resumen semanal no muestra cifras inventadas. La vinculación familiar explica el flujo futuro y no genera códigos. Los ejemplos educativos están identificados como demostración.

## Recorrido de revisión

1. Bienvenida: marca, propósito y acceso a la vista previa.
2. Inicio: saludo, estado pendiente, alertas vacías, orientación inmediata, protecciones, educación, resumen vacío y familia.
3. Protección: cuatro tarjetas y un detalle por canal, con alcance, límites y estado pendiente. Los botones permiten consultar; no abren permisos.
4. Aprende: seis lecciones locales, estimación de lectura, progreso temporal, ejemplos interactivos y navegación a la siguiente lección.
5. Mi familia: estado sin vinculación, explicación de consentimiento, historial vacío y vista previa de cómo vincular.
6. Orientación: recomendaciones locales para detener una posible estafa. No llama ni avisa a terceros.
7. Ajustes: saludo opcional, texto estándar/grande/muy grande, alto contraste, interfaz simplificada, reducción de transiciones, restablecer y explicación de Hestia.

La barra inferior permanece en todos los destinos principales y detalles. Los detalles ofrecen Volver; el botón Atrás de Android usa el historial de Navigation Compose. Accesibilidad está disponible desde todas las pantallas principales.

## Accesibilidad y límites

- Acciones principales con altura mínima de 56 dp; destinos y controles con al menos 48 dp.
- Texto en sp, sin alturas rígidas para párrafos, pantallas desplazables y etiquetas de navegación que pueden ocupar varias líneas.
- El ajuste interno multiplica el escalado del sistema; no lo reemplaza ni lo limita.
- Con escalado combinado superior a 1,6, la navegación inferior pasa a dos filas para mantener legibles las cuatro etiquetas.
- Estados expresados con palabras y símbolos; iconos decorativos acompañados por texto. Encabezados, pestañas, radios, interruptores y progreso ofrecen semántica Compose.
- El alto contraste refuerza texto y bordes. Los colores dinámicos de Android no reemplazan la paleta.
- La interfaz simplificada reduce secciones de Inicio; los otros destinos siguen disponibles.
- Los ajustes son estado temporal de interfaz, conservado durante recreaciones de Activity; no se escribe en DataStore. El progreso educativo es solo de sesión.
- La lectura en voz alta aparece deshabilitada y marcada para la siguiente etapa. No hay TextToSpeech ni AccessibilityService.
- La semántica facilita TalkBack; una auditoría completa con lector de pantalla y usuarios adultos mayores sigue siendo necesaria.

## Organización

`core/theme`: colores, tipografía y tema. `core/components`: marca, iconos vectoriales y componentes comunes. `core/accessibility`: preferencias visuales. `core/navigation`: contenedor y grafo Navigation Compose. `features/home`, `protection`, `education`, `trustedcontact` y `settings`: pantallas y modelos de presentación.

MainActivity solo inicia la interfaz. No hay lógica de análisis ni repositorios ficticios. No se agregan ViewModels vacíos: el estado visual está elevado al contenedor y las pantallas reciben datos y callbacks. MVVM se incorporará cuando haya lógica y fuentes de datos que lo requieran.

Los componentes LoadingState y ErrorState están disponibles para futuros estados reales. No se simulan esperas ni errores en pantallas que solo usan contenido local. SecurityAlertCard y RiskIndicator se reservan para ejemplos explícitos o incidentes reales futuros; no se inventan alertas en Inicio.

## Dependencias y validación

Única dependencia directa nueva: `androidx.navigation:navigation-compose:2.9.4`, versión estable de la línea Navigation Compose 2.9. Espresso (solo pruebas) se actualiza de 3.5.1 a 3.7.0: la versión anterior falla con `NoSuchMethodException: InputManager.getInstance` en el emulador API 37 antes de ejecutar interacciones. La versión estable 3.7.0 reemplaza esa reflexión por getSystemService, según las notas oficiales de AndroidX Test. No cambia AGP, Gradle, SDK, JDK, package ni configuración Kotlin.

Pruebas instrumentales en `HestiaNavigationTest`: cuatro detalles de protección, seis lecciones, progreso, familia, orientación, ajustes, saludo, contraste y tamaño de texto. Generan capturas en la salida adicional de instrumentación (o directorio de medios de la app) y copias en `Download/hestia-review` del emulador. Las capturas solo contienen esta interfaz de demostración. Las pruebas no agregan permisos al APK.

Ejecutar `assembleDebug`, `testDebugUnitTest`, `lintDebug` y, con emulador, `connectedDebugAndroidTest`. Los resultados y limitaciones de la ejecución se informan en la entrega. No se inicia la ETAPA 2 ni se realizan commits o push.

## Archivos de esta etapa

En `app/src/main/java/cl/grupo28/hestia/`:

```text
core/accessibility/DisplayPreferences.kt
core/components/HestiaComponents.kt
core/components/HestiaIcons.kt
core/navigation/HestiaApp.kt
core/theme/Color.kt
core/theme/Theme.kt
core/theme/Type.kt
features/home/HomeScreen.kt
features/protection/ProtectionUiModel.kt
features/protection/ProtectionScreen.kt
features/education/LessonUiModel.kt
features/education/EducationScreen.kt
features/trustedcontact/TrustedContactScreen.kt
features/settings/SettingsScreen.kt
```

El tema sustituye los tres archivos originales de `ui/theme`. MainActivity se reduce al inicio de HestiaApp. También se modifican `app/build.gradle.kts`, `gradle/libs.versions.toml` y README; se crean este documento y `app/src/androidTest/java/cl/grupo28/hestia/HestiaNavigationTest.kt`.

## Resultado de validación

- `assembleDebug` y `testDebugUnitTest`: aprobados; se conserva la prueba unitaria de plantilla (no valida lógica antifraude).
- `connectedDebugAndroidTest`: 3 pruebas aprobadas en emulador API 37, incluidas identidad y las dos pruebas de recorrido visual.
- Ambas pruebas de interfaz se repitieron con fuente del sistema al 150 %: aprobadas. El ajuste final de navegación en dos filas se comprobó además con sistema al 150 % y la opción Muy grande de Hestia.
- `lintDebug`: 0 errores, 16 advertencias. Permanecen comprobaciones incompatibles de Lifecycle, avisos de versiones disponibles, recursos de plantilla sin uso y una etiqueta redundante del manifest. No se suprimen comprobaciones.
- Contraste calculado de los pares principales: azul/marfil 12,91:1; petróleo oscuro/marfil 6,05:1; texto secundario/superficie 6,35:1; azul/ámbar 8,01:1. Se conserva el petróleo base y un tono más oscuro para textos y controles.
- Capturas inspeccionadas en tamaño estándar, texto ampliado y alto contraste. La fuente del emulador se restableció a su valor original tras las pruebas.
- Pendiente: auditoría manual completa con TalkBack, dispositivos físicos, Android 10 y pruebas con adultos mayores. No se verifican integraciones porque esta etapa no las implementa.
