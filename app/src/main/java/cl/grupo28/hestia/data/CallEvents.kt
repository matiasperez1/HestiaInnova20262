package cl.grupo28.hestia.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import cl.grupo28.hestia.domain.EventKind
import cl.grupo28.hestia.domain.SecurityEvent
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.temporal.ChronoUnit

internal data class CallDetection(val event: SecurityEvent, val synthetic: Boolean)

internal class CallEvents(context: Context) {
    private val store = sharedStore(context.applicationContext)
    val events = store.data.map { preferences ->
        preferences[key].orEmpty().mapNotNull(::decode)
    }

    suspend fun record(detection: CallDetection) {
        val event = detection.event
        val row = "${event.sourceId}|${event.occurredAt.toEpochMilli()}|${event.suspicious}|${detection.synthetic}"
        val cutoff = Instant.now().minus(30, ChronoUnit.DAYS)
        store.edit { preferences ->
            val retained = preferences[key].orEmpty().filter { saved ->
                decode(saved)?.event?.let { it.sourceId != event.sourceId && it.occurredAt >= cutoff } == true
            }
            preferences[key] = (retained + row).toSet()
        }
    }

    private fun decode(row: String): CallDetection? {
        val parts = row.split('|')
        if (parts.size != 4) return null
        val time = parts[1].toLongOrNull() ?: return null
        val reported = parts[2].toBooleanStrictOrNull() ?: return null
        val synthetic = parts[3].toBooleanStrictOrNull() ?: return null
        return CallDetection(SecurityEvent(parts[0], EventKind.CALL_REGISTERED, Instant.ofEpochMilli(time), reported), synthetic)
    }

    private companion object {
        val key = stringSetPreferencesKey("detections")
        private var instance: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>? = null

        @Synchronized fun sharedStore(context: Context) = instance ?: PreferenceDataStoreFactory.create(
            produceFile = { context.noBackupFilesDir.resolve("call_detections.preferences_pb") },
        ).also { instance = it }
    }
}
