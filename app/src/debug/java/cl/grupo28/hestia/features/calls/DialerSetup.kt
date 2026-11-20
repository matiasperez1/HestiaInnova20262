package cl.grupo28.hestia.features.calls

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.grupo28.hestia.core.components.HestiaCard
import cl.grupo28.hestia.core.components.SecondaryButton
import cl.grupo28.hestia.core.components.SectionHeader

@Composable
internal fun DialerSetup() {
    val context = LocalContext.current
    val roles = remember(context) { context.getSystemService(RoleManager::class.java) }
    val notices = remember(context) { context.getSystemService(NotificationManager::class.java) }
    if (!roles.isRoleAvailable(RoleManager.ROLE_DIALER)) {
        HestiaCard { SectionHeader("Teléfono predeterminado", "Android no permite cambiarlo en este dispositivo.") }
        return
    }
    val telecom = remember(context) { context.getSystemService(TelecomManager::class.java) }
    var isGooglePhone by remember { mutableStateOf(telecom.defaultDialerPackage == "com.google.android.dialer") }
    var selected by remember { mutableStateOf(roles.isRoleHeld(RoleManager.ROLE_DIALER)) }
    var notificationsEnabled by remember { mutableStateOf(notices.areNotificationsEnabled()) }
    var fullScreen by remember { mutableStateOf(Build.VERSION.SDK_INT < 34 || notices.canUseFullScreenIntent()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        selected = roles.isRoleHeld(RoleManager.ROLE_DIALER)
        isGooglePhone = telecom.defaultDialerPackage == "com.google.android.dialer"
    }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = notices.areNotificationsEnabled()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                selected = roles.isRoleHeld(RoleManager.ROLE_DIALER)
                isGooglePhone = telecom.defaultDialerPackage == "com.google.android.dialer"
                notificationsEnabled = notices.areNotificationsEnabled()
                fullScreen = Build.VERSION.SDK_INT < 34 || notices.canUseFullScreenIntent()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    HestiaCard {
        val active = when {
            selected -> "Hestia"
            isGooglePhone -> "Google Teléfono"
            else -> "Otra aplicación de teléfono"
        }
        SectionHeader("Teléfono predeterminado", active)
        if (!selected) {
            Text("Si eliges Hestia, podrás usar su teclado y atender llamadas desde su pantalla.")
            SecondaryButton("Usar Hestia como teléfono", { launcher.launch(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER)) })
        } else {
            if (!notificationsEnabled) {
                Text("Permite los avisos para poder abrir la llamada desde la bandeja de Android.")
                if (Build.VERSION.SDK_INT >= 33) {
                    SecondaryButton("Permitir avisos de llamada", { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) })
                }
                SecondaryButton("Ajustes de avisos", {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                })
            }
            if (Build.VERSION.SDK_INT >= 34 && !fullScreen) {
                SecondaryButton("Permitir avisos a pantalla completa", {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", context.packageName, null)))
                })
            }
            Text("Puedes volver a Google Teléfono desde las aplicaciones predeterminadas de Android.")
        }
        SecondaryButton("Cambiar aplicación de teléfono", { context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) })
    }
}
