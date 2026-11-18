package cl.grupo28.hestia.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cl.grupo28.hestia.R
import cl.grupo28.hestia.data.CallDetection
import cl.grupo28.hestia.data.CallEvents
import cl.grupo28.hestia.data.localCallReputation
import cl.grupo28.hestia.domain.EventKind
import cl.grupo28.hestia.domain.SecurityEvent
import cl.grupo28.hestia.domain.normalizeCallNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.util.UUID

class HestiaCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return
        respondToCall(details, CallResponse.Builder().build())
        val handle = details.handle?.takeIf { it.scheme == "tel" } ?: return
        val number = normalizeCallNumber(handle.schemeSpecificPart.orEmpty()) ?: return
        val verdict = localCallReputation.lookup(number)
        val event = SecurityEvent(UUID.randomUUID().toString(), EventKind.CALL_REGISTERED, Instant.now(), verdict.reported)
        val context = applicationContext
        // Telecom may unbind immediately after the response. Finish in the app process scope.
        writes.launch {
            if (verdict.reported) notifyReported(event.sourceId, verdict.synthetic)
            try {
                CallEvents(context).record(CallDetection(event, verdict.synthetic))
            } catch (_: IOException) {
                // A storage failure must never affect the call.
            }
        }
    }

    private fun notifyReported(id: String, synthetic: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Avisos de llamadas", NotificationManager.IMPORTANCE_HIGH))
        val notice = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_hestia_notification)
            .setContentTitle(if (synthetic) "Hestia · PRUEBA DE LLAMADAS" else "Hestia · Número reportado")
            .setContentText(if (synthetic) "Llamada al número de prueba detectada." else "El número figura en reportes. Si contestas, no compartas claves.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id, 0, notice)
        } catch (_: SecurityException) {
            // The user may revoke notifications between the check and the post.
        }
    }

    private companion object {
        const val CHANNEL = "hestia_call_reports"
        val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
