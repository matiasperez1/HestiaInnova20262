package cl.grupo28.hestia.services

import android.app.KeyguardManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneNumberUtils
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.grupo28.hestia.core.accessibility.DisplayPreferences
import cl.grupo28.hestia.core.components.SecondaryButton
import cl.grupo28.hestia.core.theme.HestiaTheme
import cl.grupo28.hestia.data.DataStoreSettingsRepository
import cl.grupo28.hestia.features.calls.CallRiskCard
import cl.grupo28.hestia.features.calls.CallRiskDetails
import java.util.Locale
import java.util.UUID

private class CallerAlert(val number: String, val synthetic: Boolean) {
    var visible = false
    var dismissed = false
    var close: (() -> Unit)? = null
}

// Telecom callbacks and these activity lifecycle callbacks run on the main thread.
private val alerts = mutableMapOf<String, CallerAlert>()
private val main = Handler(Looper.getMainLooper())
private const val ALERT_DURATION = 25_000L
private const val VISIBILITY_GRACE = 1_800L

internal fun clearCallerIdentification() {
    for (alert in alerts.values) {
        alert.dismissed = true
        alert.close?.invoke()
    }
    alerts.clear()
}

internal fun Context.showCallerIdentification(number: String, synthetic: Boolean, fallback: () -> Unit) {
    // Preserve the system's incoming-call presentation and privacy on the lock screen.
    if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
        main.post { fallback() }
        return
    }
    val key = UUID.randomUUID().toString()
    val alert = CallerAlert(number, synthetic)
    alerts[key] = alert
    try {
        startActivity(Intent(this, CallerIdentificationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("alert", key))
    } catch (_: SecurityException) {
        alerts.remove(key)
        main.post { fallback() }
        return
    }
    main.postDelayed({
        if (!alert.visible && !alert.dismissed) {
            alerts.remove(key)
            fallback()
        }
    }, VISIBILITY_GRACE)
    main.postDelayed({ alerts.remove(key) }, ALERT_DURATION)
}

class CallerIdentificationActivity : ComponentActivity() {
    private var alert: CallerAlert? = null
    private val expires = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val item = alerts[intent.getStringExtra("alert")]
        if (item == null || !getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            finish()
            return
        }
        alert = item
        item.close = ::finish
        // A floating app window, with the rest of the screen available to Google Teléfono.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.setGravity(Gravity.CENTER)
        window.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        main.postDelayed(expires, ALERT_DURATION)
        val settings = DataStoreSettingsRepository(applicationContext).settings
        val number = PhoneNumberUtils.formatNumber(item.number, Locale.getDefault().country.ifEmpty { "CL" }) ?: item.number
        setContent {
            val preferences by settings.collectAsState(initial = null)
            var details by remember { mutableStateOf(false) }
            BackHandler { item.dismissed = true; finish() }
            HestiaTheme(preferences?.display ?: DisplayPreferences()) {
                Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 4.dp) {
                    Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (item.synthetic) "Hestia · Número de prueba" else "Identificación de Hestia")
                        Text(number, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        CallRiskCard({ details = true })
                        SecondaryButton("Cerrar aviso", { item.dismissed = true; finish() })
                    }
                }
                if (details) CallRiskDetails(number, item.synthetic, { details = false })
            }
        }
    }

    override fun onStart() { super.onStart(); alert?.visible = true }
    override fun onStop() { alert?.visible = false; super.onStop() }
    override fun onDestroy() {
        alert?.close = null
        main.removeCallbacks(expires)
        super.onDestroy()
    }
}
