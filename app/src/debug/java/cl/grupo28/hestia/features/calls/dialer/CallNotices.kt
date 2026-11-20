package cl.grupo28.hestia.features.calls.dialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import cl.grupo28.hestia.R
import cl.grupo28.hestia.features.calls.PERSONAL_MARK_TITLE

internal object CallNotices {
    const val ID = 3201
    private const val INCOMING = "hestia_telecom_incoming"
    private const val ONGOING = "hestia_telecom_ongoing"

    fun show(context: Context, ui: CallUi, replace: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val ringing = ui.state == Call.STATE_RINGING
        manager.createNotificationChannel(NotificationChannel(INCOMING, "Llamadas entrantes de Hestia", NotificationManager.IMPORTANCE_HIGH).apply {
            // Telecom rings because IN_CALL_SERVICE_RINGING is not claimed.
            setSound(null, null)
            enableVibration(false)
        })
        manager.createNotificationChannel(NotificationChannel(ONGOING, "Llamada en curso", NotificationManager.IMPORTANCE_LOW))
        val screen = PendingIntent.getActivity(context, 0, TelecomCallActivity.intent(context), PendingIntent.FLAG_IMMUTABLE)
        val label = when {
            !ringing -> "Llamada en curso"
            ui.personalReason != null -> PERSONAL_MARK_TITLE
            ui.reported -> "Posible llamada fraudulenta"
            else -> "Llamada entrante"
        }
        val notice = Notification.Builder(context, if (ringing) INCOMING else ONGOING)
            .setSmallIcon(R.drawable.ic_hestia_notification)
            .setContentTitle(label)
            .setContentText("Toca para abrir Hestia")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(screen)
        if (ringing) {
            notice.addAction(Notification.Action.Builder(null, "Rechazar", action(context, ui, CallCommand.Reject)).build())
                .addAction(Notification.Action.Builder(null, "Contestar", action(context, ui, CallCommand.Answer)).build())
            if (Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()) notice.setFullScreenIntent(screen, true)
        } else {
            notice.addAction(Notification.Action.Builder(null, "Colgar", action(context, ui, CallCommand.HangUp)).build())
        }
        // A notification ID cannot change channels across updates reliably.
        if (replace) manager.cancel(ID)
        try {
            manager.notify(ID, notice.build())
        } catch (_: SecurityException) {
            // Android may revoke notification access while a call is arriving.
        }
    }

    private fun action(context: Context, ui: CallUi, command: CallCommand): PendingIntent =
        PendingIntent.getBroadcast(context, 0, Intent(context, CallActionReceiver::class.java)
            .setAction(command.name)
            .setData(android.net.Uri.fromParts("hestia-call", ui.id, null)), PendingIntent.FLAG_IMMUTABLE)
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.holdsDialerRole()) return
        val id = intent.data?.schemeSpecificPart ?: return
        val command = CallCommand.entries.firstOrNull { it.name == intent.action } ?: return
        InCallSession.service?.perform(id, command)
    }
}
