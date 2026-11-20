package cl.grupo28.hestia.data.contacts

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.PhoneNumberUtils
import cl.grupo28.hestia.domain.normalizeCallNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

internal data class AgendaNumber(val display: String, val dial: String)

internal data class AgendaContact(
    val id: Long,
    val name: String,
    val thumbnail: Uri?,
    val numbers: List<AgendaNumber>,
)

internal class DeviceAgenda(private val resolver: ContentResolver) {
    fun observe() = callbackFlow {
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        resolver.registerContentObserver(ContactsContract.AUTHORITY_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate().map { read() }.flowOn(Dispatchers.IO)

    private fun read(): List<AgendaContact> {
        val columns = arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.PHOTO_THUMBNAIL_URI, Phone.NUMBER)
        val contacts = linkedMapOf<Long, ContactNumbers>()
        val cursor = resolver.query(Phone.CONTENT_URI, columns, null, null, null)
            ?: throw IOException("Contacts unavailable")
        cursor.use {
            while (it.moveToNext()) {
                val number = it.getString(3)?.trim().orEmpty()
                val dial = PhoneNumberUtils.stripSeparators(number)
                if (dial.none(Char::isDigit)) continue
                val id = it.getLong(0)
                val contact = contacts.getOrPut(id) {
                    ContactNumbers(it.getString(1)?.trim().orEmpty(), it.getString(2)?.let(Uri::parse))
                }
                contact.numbers.putIfAbsent(normalizeCallNumber(dial) ?: dial, AgendaNumber(number, dial))
            }
        }
        val names = Collator.getInstance()
        return contacts.map { (id, contact) ->
            AgendaContact(id, contact.name, contact.thumbnail, contact.numbers.values.toList())
        }.sortedWith { first, second -> names.compare(first.label, second.label) }
    }

    private class ContactNumbers(val name: String, val thumbnail: Uri?) {
        val numbers = linkedMapOf<String, AgendaNumber>()
    }
}

internal val AgendaContact.label: String get() = name.ifBlank { numbers.first().display }

internal fun List<AgendaContact>.searchAgenda(query: String): Sequence<AgendaContact> {
    val term = query.trim()
    if (term.isEmpty()) return asSequence()
    val digits = term.filter(Char::isDigit)
    val name = term.searchableName()
    return asSequence().filter { contact ->
        contact.name.searchableName().contains(name) ||
            (digits.isNotEmpty() && term.none(Char::isLetter) && contact.numbers.any { number ->
                number.dial.filter(Char::isDigit).contains(digits) ||
                    normalizeCallNumber(number.dial)?.filter(Char::isDigit)?.contains(digits) == true
            })
    }
}

private val accents = "\\p{M}+".toRegex()
private fun String.searchableName() = Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(accents, "")
