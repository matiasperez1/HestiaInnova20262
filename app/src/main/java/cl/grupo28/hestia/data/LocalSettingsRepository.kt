package cl.grupo28.hestia.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import cl.grupo28.hestia.core.accessibility.*
import cl.grupo28.hestia.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.hestiaStore by preferencesDataStore(name = "hestia_local")

interface LocalSettingsRepository {
    val settings: Flow<LocalSettings>
    suspend fun setOnboardingStep(step: Int)
    suspend fun addContact(name: String, phone: String)
    suspend fun removeContact(id: String)
    suspend fun completeLesson(id: Int)
    suspend fun setDisplay(display: DisplayPreferences)
}

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : LocalSettingsRepository {
    constructor(context: Context) : this(context.applicationContext.hestiaStore)
    private val stepKey = intPreferencesKey("onboarding_step")
    private val contactsKey = stringPreferencesKey("emergency_contacts")
    private val lessonsKey = stringSetPreferencesKey("completed_lessons_v2")
    private val sizeKey = stringPreferencesKey("text_size")
    private val contrastKey = booleanPreferencesKey("high_contrast")
    private val motionKey = booleanPreferencesKey("reduce_motion")

    override val settings = store.data.map { preferences ->
        LocalSettings(
            onboardingStep = (preferences[stepKey] ?: 0).coerceIn(0, 7),
            contacts = decodeContacts(preferences[contactsKey]),
            completedLessons = preferences[lessonsKey].orEmpty().mapNotNull { it.toIntOrNull()?.takeIf { id -> id in 1..5 } }.toSet(),
            display = DisplayPreferences(
                textSize = TextSize.entries.firstOrNull { it.name == preferences[sizeKey] } ?: TextSize.STANDARD,
                highContrast = preferences[contrastKey] ?: false,
                reduceMotion = preferences[motionKey] ?: false,
            ),
        )
    }

    override suspend fun setOnboardingStep(step: Int) {
        require(step in 0..7)
        store.edit { it[stepKey] = step }
    }

    override suspend fun addContact(name: String, phone: String) {
        val cleanName = ContactRules.name(name)
        val cleanPhone = ContactRules.phone(phone)
        store.edit { preferences ->
            val contacts = decodeContacts(preferences[contactsKey])
            require(contacts.none { it.phone == cleanPhone }) { "Ese teléfono ya está en tu lista." }
            preferences[contactsKey] = encodeContacts(contacts + EmergencyContact(UUID.randomUUID().toString(), cleanName, cleanPhone))
        }
    }

    override suspend fun removeContact(id: String) {
        store.edit { it[contactsKey] = encodeContacts(decodeContacts(it[contactsKey]).filterNot { contact -> contact.id == id }) }
    }

    override suspend fun completeLesson(id: Int) {
        require(id in 1..5)
        store.edit { it[lessonsKey] = it[lessonsKey].orEmpty() + id.toString() }
    }

    override suspend fun setDisplay(display: DisplayPreferences) {
        store.edit {
            it[sizeKey] = display.textSize.name
            it[contrastKey] = display.highContrast
            it[motionKey] = display.reduceMotion
        }
    }

    private fun decodeContacts(value: String?): List<EmergencyContact> {
        val array = JSONArray(value ?: "[]")
        return (0 until array.length()).map { index ->
            array.getJSONObject(index).let { EmergencyContact(it.getString("id"), it.getString("name"), it.getString("phone")) }
        }
    }
    private fun encodeContacts(contacts: List<EmergencyContact>): String = JSONArray().apply {
        contacts.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone)) }
    }.toString()
}
