# Verificación del paquete interno

| Dato | Valor comprobado |
| --- | --- |
| APK | `Hestia-DEBUG.apk` |
| Variante | DEBUG, depuración habilitada |
| Identificador y versión | `cl.grupo28.hestia` · `1.0` · `versionCode 1` |
| Tamaño | 40.537.125 bytes |
| SHA-256 del APK | `34c6bf3900c99b513fefdeb147c1a49d83cc1bcf44500fa0a796b227836ca7c2` |
| Firma | Android Debug, esquema APK v2, un firmante |
| SHA-256 del certificado público | `7b4e7cbf1d4de2f3afe9b05b0d10d3229ce7c798f194d97a48feea133ed34eff` |
| SDK mínimo y objetivo | API 29 y API 37 |
| Arquitecturas incluidas | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |

`apksigner` confirmó la firma para Android 15, API 35. Se verificó la alineación con `zipalign` y la integridad de las 639 entradas ZIP mediante sus CRC. La copia distribuida coincide con la huella de `SHA256SUMS.txt`. [Herramienta apksigner](https://developer.android.com/tools/apksigner)

## Pruebas del binario

- **Moto G06, Android 15, API 35.** Android aceptó mediante `adb install -r` el APK de esta carpeta. El archivo instalado tenía el mismo SHA-256. Se conservó la fecha de primera instalación y se restauró byte a byte el APK anterior del dispositivo de validación.
- **Compilación y pruebas locales.** Se ejecutó `assembleDebug` en una copia temporal de las fuentes actuales. Se aprobaron 108 pruebas unitarias con la configuración interna. La distribución contiene únicamente el APK DEBUG de la app, las guías y su huella.
- **Emulador API 37, perfil limpio.** El APK de esta carpeta superó cinco pruebas instrumentadas. Se comprobaron la configuración inicial sin cuenta, la navegación, Familia y el progreso educativo, el módulo Teléfono y el flujo de marcar y retirar un número localmente con una entrada de prueba. También se comprobó el aviso personal en la interfaz de llamada.

## Configuración y revisión local

Se comprobó que los ocho campos de configuración de OAuth, Supabase, API y Firebase quedan vacíos. El modo DEMO LOCAL inicia desactivado y conserva el host del emulador. En el contenido del APK no aparecieron los valores de configuración del proyecto ni del archivo histórico que se revisó. El APK también pasó un control de patrones de credenciales y de nombres de archivos sensibles. Los contactos de confianza y el registro de llamadas se almacenan en cada teléfono a partir de lo que cada persona elija.

La revisión de Git abarcó 14 commits accesibles mediante referencias y reflogs locales, 547 blobs y un control de patrones sobre 462 blobs de texto. La coincidencia de un patrón de clave pública en texto correspondió a valores marcados como prueba en un archivo de pruebas unitarias. El RAR de Git se revisó por su inventario de 16.698 entradas y por la configuración identificada, que se leyó solo en memoria. El RAR conserva una entrada `secrets.properties` con configuración de OAuth y Supabase y permanece en el historial local. La distribución se preparó sin el RAR ni el repositorio. El control de patrones es una inspección dirigida a este paquete y al historial local disponible.
