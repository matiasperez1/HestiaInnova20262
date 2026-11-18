package cl.grupo28.hestia.data.calls

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Duration
import java.time.Instant

internal enum class RecentKind(val label: String) {
    RECEIVED("Recibida"), OUTGOING("Saliente"), MISSED("Perdida"), DECLINED("Rechazada"),
}

internal data class RecentCall(
    val id: String, val number: String, val at: Instant, val kind: RecentKind, val synthetic: Boolean,
    val answeredBy: Set<CallPersona> = emptySet(),
)
internal data class RecentsState(val enabled: Boolean, val calls: List<RecentCall>)

internal fun retainRecentCalls(calls: List<RecentCall>, now: Instant): List<RecentCall> = calls
    .filter { it.at >= now.minus(Duration.ofDays(30)) }
    .sortedByDescending { it.at }
    .distinctBy { it.id }
    .take(30)

internal class PhoneRecents(context: Context) {
    private val store = sharedStore(context.applicationContext)
    val state = store.data.map { preferences ->
        val enabled = preferences[enabledKey] == true
        RecentsState(enabled, if (enabled) retainRecentCalls(decode(preferences[rowsKey]), Instant.now()) else emptyList())
    }

    suspend fun setEnabled(enabled: Boolean) {
        store.edit {
            it[enabledKey] = enabled
            if (!enabled) it.remove(rowsKey)
        }
    }

    suspend fun record(call: RecentCall) {
        store.edit { preferences ->
            if (preferences[enabledKey] != true) return@edit
            val entries = retainRecentCalls(listOf(call) + decode(preferences[rowsKey]), call.at)
            preferences[rowsKey] = encode(entries)
        }
    }

    fun markAnswered(id: String, persona: CallPersona) {
        writes.launch {
            try {
                store.edit { preferences ->
                    if (preferences[enabledKey] != true) return@edit
                    val entries = retainRecentCalls(decode(preferences[rowsKey]), Instant.now())
                    preferences[rowsKey] = encode(entries.map { if (it.id == id) it.copy(answeredBy = it.answeredBy + persona) else it })
                }
            } catch (_: IOException) { /* The next call still works if its history cannot be saved. */ }
        }
    }

    private fun encode(entries: List<RecentCall>): String = JSONArray().apply {
        entries.forEach { entry ->
            put(JSONObject().put("id", entry.id).put("number", entry.number)
                .put("at", entry.at.toEpochMilli()).put("kind", entry.kind.name).put("synthetic", entry.synthetic)
                .put("answeredBy", JSONArray(entry.answeredBy.map { it.id })))
        }
    }.toString()

    private fun decode(raw: String?): List<RecentCall> {
        val array = runCatching { JSONArray(raw ?: "[]") }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val row = array.getJSONObject(index)
                val answered = row.optJSONArray("answeredBy") ?: JSONArray()
                val personas = CallPersona.entries.filter { persona ->
                    (0 until answered.length()).any { answered.optString(it) == persona.id }
                }.toSet()
                RecentCall(row.getString("id"), row.getString("number"), Instant.ofEpochMilli(row.getLong("at")),
                    RecentKind.valueOf(row.getString("kind")), row.optBoolean("synthetic"), personas)
            }.getOrNull()
        }
    }

    private companion object {
        val enabledKey = booleanPreferencesKey("enabled")
        val rowsKey = stringPreferencesKey("calls")
        val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>? = null

        @Synchronized fun sharedStore(context: Context) = store ?: PreferenceDataStoreFactory.create(
            produceFile = { context.noBackupFilesDir.resolve("phone_recents.preferences_pb") },
        ).also { store = it }
    }
}
