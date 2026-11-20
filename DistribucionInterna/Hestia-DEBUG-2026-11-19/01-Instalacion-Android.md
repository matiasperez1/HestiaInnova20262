# Instalar Hestia en Android

Usa un equipo de prueba Android 15 con SIM si vas a ensayar llamadas. El APK declara Android 10 como versión mínima. La instalación física se comprobó en Android 15. Los pasos y nombres del menú pueden variar según fabricante y políticas del equipo.

1. Descarga `Hestia-DEBUG.apk` y `SHA256SUMS.txt` desde la carpeta que compartió el equipo.
2. Si tienes un computador, calcula la huella SHA-256 del APK y compárala con `SHA256SUMS.txt`. Si difiere, descarga el archivo de nuevo o pide al equipo que lo revise.
3. En Android, abre el APK desde **Archivos → Descargas**. Si el sistema te lleva a un ajuste, permite **Instalar aplicaciones desconocidas** para la app desde la que abriste el APK. Vuelve al instalador y pulsa **Instalar**. [Instalación desde otras fuentes en Android](https://developer.android.com/distribute/marketing-tools/alternative-distribution?hl=es-419)
4. Abre **Hestia**. Después puedes retirar el permiso de instalar aplicaciones desconocidas desde **Ajustes → Aplicaciones → Acceso especial → Instalar aplicaciones desconocidas**. Mantén Play Protect activo. Si una política del equipo bloquea la instalación, registra el mensaje y consúltalo con la persona responsable. [Google Play Protect](https://support.google.com/android/answer/2812853?hl=es-419)

## Comprobar el SHA-256

En Windows, abre PowerShell en la carpeta descargada.

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath .\Hestia-DEBUG.apk
```

En macOS o Linux, abre una terminal en la carpeta descargada.

```sh
shasum -a 256 Hestia-DEBUG.apk
```

Si ya tienes Hestia instalada, Android solo acepta una actualización con firma compatible. Si aparece **Aplicación no instalada** por un conflicto de firma, conserva tu instalación y envía el mensaje al equipo para coordinar la actualización. Desinstalar eliminaría datos locales de esa instalación.
