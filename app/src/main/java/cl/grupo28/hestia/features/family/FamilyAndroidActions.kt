package cl.grupo28.hestia.features.family

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import cl.grupo28.hestia.core.navigation.dialIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class HelpChannel { CALL, SMS, SHARE }

internal fun helpIntent(channel: HelpChannel, phone: String, message: String): Intent = when (channel) {
    HelpChannel.CALL -> dialIntent(phone)
    HelpChannel.SMS -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null)).putExtra("sms_body", message)
    HelpChannel.SHARE -> Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, message), null)
}

internal class FamilyAndroidActions(private val context: Context) {
    fun open(channel: HelpChannel, phone: String, message: String = ""): String? = try {
        context.startActivity(helpIntent(channel, phone, message))
        null
    } catch (_: ActivityNotFoundException) {
        when (channel) {
            HelpChannel.CALL -> "No encontramos un marcador en este teléfono."
            HelpChannel.SMS -> "No se pudo abrir Mensajes. Puedes elegir Compartir advertencia o llamar."
            HelpChannel.SHARE -> "No encontramos una aplicación para compartir. Puedes probar con Mensajes."
        }
    } catch (_: SecurityException) {
        "No se pudo abrir la aplicación. Puedes volver y probar otra opción."
    }

    fun contactPicker(): Intent = Intent(Intent.ACTION_PICK).setType(Phone.CONTENT_TYPE)

    suspend fun readContact(uri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (uri.scheme != "content" || uri.authority != ContactsContract.AUTHORITY) return@withContext null
        context.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0).orEmpty() to it.getString(1).orEmpty() else null
        }
    }
}
