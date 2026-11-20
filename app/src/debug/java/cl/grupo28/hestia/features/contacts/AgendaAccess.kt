package cl.grupo28.hestia.features.contacts

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cl.grupo28.hestia.data.contacts.AgendaContact
import cl.grupo28.hestia.data.contacts.DeviceAgenda
import kotlinx.coroutines.flow.catch
import java.io.IOException

internal enum class AgendaPermission { Ask, Denied, Settings, Granted }

internal fun agendaPermission(granted: Boolean, showRationale: Boolean, requested: Boolean) = when {
    granted -> AgendaPermission.Granted
    showRationale -> AgendaPermission.Denied
    requested -> AgendaPermission.Settings
    else -> AgendaPermission.Ask
}

internal sealed interface AgendaResult {
    data object Loading : AgendaResult
    data object Unavailable : AgendaResult
    data class Loaded(val contacts: List<AgendaContact>) : AgendaResult
}

internal data class AgendaAccess(
    val permission: AgendaPermission,
    val result: AgendaResult,
    val request: () -> Unit,
    val settings: () -> Unit,
    val retry: () -> Unit,
) {
    val contacts: List<AgendaContact>
        get() = if (permission == AgendaPermission.Granted) (result as? AgendaResult.Loaded)?.contacts.orEmpty() else emptyList()
}

@Composable
internal fun rememberAgendaAccess(): AgendaAccess {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
    }
    val owner = LocalLifecycleOwner.current
    val preferences = remember(context) { context.getSharedPreferences("agenda_permission", 0) }
    val repository = remember(context) { DeviceAgenda(context.contentResolver) }
    fun readPermission(): AgendaPermission = agendaPermission(
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED,
        activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_CONTACTS) } == true,
        preferences.getBoolean("requested", false),
    )
    var permission by remember { mutableStateOf(readPermission()) }
    var result by remember { mutableStateOf<AgendaResult>(AgendaResult.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    val refreshPermission = { permission = readPermission() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        preferences.edit { putBoolean("requested", true) }
        refreshPermission()
    }
    DisposableEffect(owner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermission()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(owner, permission, attempt) {
        result = AgendaResult.Loading
        if (permission == AgendaPermission.Granted) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            repository.observe().catch { error ->
                when (error) {
                    is SecurityException -> {
                        refreshPermission()
                        result = AgendaResult.Unavailable
                    }
                    is IOException, is IllegalStateException -> result = AgendaResult.Unavailable
                    else -> throw error
                }
            }.collect { result = AgendaResult.Loaded(it) }
        }
    }
    return AgendaAccess(permission, result,
        request = { launcher.launch(Manifest.permission.READ_CONTACTS) },
        settings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) },
        retry = { attempt++ },
    )
}
