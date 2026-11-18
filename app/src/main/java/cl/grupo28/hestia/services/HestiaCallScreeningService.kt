package cl.grupo28.hestia.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cl.grupo28.hestia.BuildConfig
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
        val context = applicationContext
        val detectedAt = Instant.now()
        val number = details.handle?.takeIf { it.scheme == "tel" }?.schemeSpecificPart?.let(::normalizeCallNumber)
        val id = UUID.randomUUID().toString()
        val telecomOwnsUi = usesHestiaCallUi()
        // Only the small local DEBUG snapshot is read while Telecom is bound.
        val localVerdict = if (BuildConfig.DEBUG && number != null) localCallReputation(context).lookup(number) else null
        try {
            if (BuildConfig.DEBUG) {
                clearCallerIdentification()
                getSystemService(NotificationManager::class.java).cancel(DEBUG_ALERT, 0)
            }
            if (number != null && localVerdict?.reported == true && !telecomOwnsUi) {
                showCallerIdentification(number, localVerdict.synthetic) {
                    if (getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING) && !usesHestiaCallUi()) {
                        notifyReported(DEBUG_ALERT, localVerdict.synthetic)
                    }
                }
            }
        } finally {
            respondToCall(details, CallResponse.Builder().build())
        }
        if (number == null) return
        // Telecom may unbind immediately after the response. Finish in the app process scope.
        writes.launch {
            val verdict = localVerdict ?: localCallReputation(context).lookup(number)
            val event = SecurityEvent(id, EventKind.CALL_REGISTERED, detectedAt, verdict.reported)
            if (!BuildConfig.DEBUG && verdict.reported) notifyReported(id, verdict.synthetic)
            try {
                CallEvents(context).record(CallDetection(event, verdict.synthetic))
            } catch (_: IOException) {
                // A storage failure must never affect the call.
            }
        }
    }

    private fun usesHestiaCallUi(): Boolean =
        BuildConfig.DEBUG && getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)

    private fun notifyReported(id: String, synthetic: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Avisos de llamadas", NotificationManager.IMPORTANCE_HIGH))
        val warning = "El número figura en reportes. Podría ser un fraude. Si contestas, no compartas claves ni códigos."
        val notice = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_hestia_notification)
            .setContentTitle(if (synthetic) "Prueba · Posible llamada fraudulenta" else "Posible llamada fraudulenta")
            .setContentText(warning)
            .setStyle(NotificationCompat.BigTextStyle().bigText(warning))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .apply { if (BuildConfig.DEBUG) setTimeoutAfter(30_000) }
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
        const val DEBUG_ALERT = "caller-identification"
        val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
