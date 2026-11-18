package cl.grupo28.hestia.features.calls

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.grupo28.hestia.core.components.SecondaryButton

data class CallSetupState(val available: Boolean, val active: Boolean, val noticesEnabled: Boolean)
data class CallSetup(val state: CallSetupState, val requestRole: () -> Unit, val enableNotices: () -> Unit)

@Composable
fun rememberCallSetup(): CallSetup {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var state by remember(context) { mutableStateOf(readSetup(context)) }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { state = readSetup(context) }
    val notices = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { state = readSetup(context) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state = readSetup(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return CallSetup(state, {
        val manager = context.getSystemService(RoleManager::class.java)
        if (manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            role.launch(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }, {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notices.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
    })
}

private fun readSetup(context: Context): CallSetupState {
    val manager = context.getSystemService(RoleManager::class.java)
    val available = manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
    val notifications = context.getSystemService(NotificationManager::class.java)
    return CallSetupState(
        available,
        available && manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING),
        notifications.areNotificationsEnabled() &&
            notifications.getNotificationChannel("hestia_call_reports")?.importance != NotificationManager.IMPORTANCE_NONE,
    )
}

@Composable
fun CallRoleContent(setup: CallSetup) {
    Text(
        when {
            setup.state.active -> "Identificación de llamadas activada"
            setup.state.available -> "Puedes permitir que Hestia identifique números reportados. Tu teléfono seguirá sonando con normalidad."
            else -> "Este dispositivo no ofrece identificación de llamadas para Hestia."
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    if (setup.state.available && !setup.state.active) SecondaryButton("Activar identificación", setup.requestRole)
}

@Composable
fun CallNoticeContent(setup: CallSetup) {
    Text(
        if (setup.state.noticesEnabled) "Avisos de llamadas permitidos" else "Permite los avisos para ver una notificación cuando detectemos un número reportado.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (!setup.state.noticesEnabled) SecondaryButton("Activar notificaciones", setup.enableNotices)
}
