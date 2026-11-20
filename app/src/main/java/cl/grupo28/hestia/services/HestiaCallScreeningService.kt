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
import cl.grupo28.hestia.data.PersonalReports
import cl.grupo28.hestia.data.localCallReputation
import cl.grupo28.hestia.domain.EventKind
import cl.grupo28.hestia.domain.CallSignals
import cl.grupo28.hestia.domain.CallVerdict
import cl.grupo28.hestia.domain.SecurityEvent
import cl.grupo28.hestia.domain.normalizeCallNumber
import cl.grupo28.hestia.features.calls.PERSONAL_MARK_TEXT
import cl.grupo28.hestia.features.calls.PERSONAL_MARK_TITLE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.util.UUID

class HestiaCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return
        val context = applicationContext
        val detectedAt = Instant.now()
        val id = UUID.randomUUID().toString()
        writes.afterAllowingCall(allow = {
            respondToCall(details, CallResponse.Builder().build())
            if (BuildConfig.DEBUG) {
                latestDebugId = id
                clearCallerIdentification()
                getSystemService(NotificationManager::class.java).cancel(DEBUG_ALERT, 0)
            }
        }) {
            val raw = details.handle?.takeIf { it.scheme == "tel" }?.schemeSpecificPart ?: return@afterAllowingCall
            val communityNumber = normalizeCallNumber(raw)
            val verdict = communityNumber?.let { localCallReputation(context).lookup(it) } ?: CallVerdict.NO_MATCH
            val personal = if (verdict.synthetic) null else PersonalReports.from(context).active(raw)
            val signals = CallSignals(personal?.reason, verdict)
            if (signals.shouldWarn) withContext(Dispatchers.Main.immediate) {
                if (!holdsScreeningRole() || usesHestiaCallUi()) return@withContext
                if (!BuildConfig.DEBUG) notifyReported(id, signals)
                else if (latestDebugId == id) {
                    showCallerIdentification(personal?.normalizedNumber ?: communityNumber ?: raw, signals) {
                        if (latestDebugId == id && holdsScreeningRole() && !usesHestiaCallUi()) notifyReported(DEBUG_ALERT, signals)
                    }
                }
            }
            if (communityNumber == null && personal == null) return@afterAllowingCall
            val event = SecurityEvent(id, EventKind.CALL_REGISTERED, detectedAt, verdict.reported)
            try {
                CallEvents(context).record(CallDetection(event, verdict.synthetic))
            } catch (_: IOException) {
                // A storage failure must never affect the call.
            }
        }
    }

    private fun usesHestiaCallUi(): Boolean =
        BuildConfig.DEBUG && getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)

    private fun holdsScreeningRole(): Boolean = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

    private fun notifyReported(id: String, signals: CallSignals) {
        val manager = getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Avisos de llamadas", NotificationManager.IMPORTANCE_HIGH))
        val warning = when {
            signals.personal != null && signals.community.reported -> "$PERSONAL_MARK_TEXT También figura en reportes comunitarios."
            signals.personal != null -> PERSONAL_MARK_TEXT
            else -> "El número figura en reportes. Podría ser un fraude. Si contestas, no compartas claves ni códigos."
        }
        val title = when {
            signals.personal != null -> PERSONAL_MARK_TITLE
            signals.community.synthetic -> "Prueba · Posible llamada fraudulenta"
            else -> "Posible llamada fraudulenta"
        }
        val notice = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_hestia_notification)
            .setContentTitle(title)
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
        var latestDebugId: String? = null // Accessed only on the Telecom/main thread.
    }
}

// Telecom may unbind immediately after the response; follow-up work lives in the app process scope.
internal fun CoroutineScope.afterAllowingCall(allow: () -> Unit, inspect: suspend () -> Unit) {
    allow()
    launch { inspect() }
}
