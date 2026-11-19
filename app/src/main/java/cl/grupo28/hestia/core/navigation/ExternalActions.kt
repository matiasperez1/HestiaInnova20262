package cl.grupo28.hestia.core.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

internal const val OPEN_PHONE_RECENTS_ACTION = "cl.grupo28.hestia.OPEN_PHONE_RECENTS"

/** External intents only after an explicit tap. Dialing itself remains with the user's phone app. */
fun openExternal(context: Context, intent: Intent, onError: (String) -> Unit) {
    try { context.startActivity(intent) }
    catch (_: ActivityNotFoundException) { onError("No hay una aplicación disponible para abrir esta opción.") }
    catch (_: SecurityException) { onError("No pudimos abrir esta opción en tu teléfono.") }
}

fun dialIntent(phone: String): Intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
