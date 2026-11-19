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
    suspend fun addContact(name: String, phone: String, relationship: String? = null)
    suspend fun editContact(id: String, name: String, phone: String, relationship: String?)
    suspend fun setPrimaryContact(id: String)
    suspend fun moveContactUp(id: String)
    suspend fun removeContact(id: String)
    suspend fun openLesson(id: String)
    suspend fun saveLessonCheckpoint(id: String, checkpoint: LessonCheckpoint)
    suspend fun completeLesson(id: String)
    suspend fun setDisplay(display: DisplayPreferences)
}

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : LocalSettingsRepository {
    constructor(context: Context) : this(context.applicationContext.hestiaStore)
    private val stepKey = intPreferencesKey("onboarding_step")
    private val contactsKey = stringPreferencesKey("emergency_contacts")
    private val legacyLessonsKey = stringSetPreferencesKey("completed_lessons_v2")
    private val completedLessonsKey = stringSetPreferencesKey("education_completed")
    private val checkpointsKey = stringPreferencesKey("education_checkpoints")
    private val lastLessonKey = stringPreferencesKey("education_last_lesson")
    private val sizeKey = stringPreferencesKey("text_size")
    private val contrastKey = booleanPreferencesKey("high_contrast")
    private val motionKey = booleanPreferencesKey("reduce_motion")

    override val settings = store.data.map { preferences ->
        LocalSettings(
            onboardingStep = (preferences[stepKey] ?: 0).coerceIn(0, 7),
            contacts = decodeContacts(preferences[contactsKey]),
            education = EducationProgress(
                completed = preferences[completedLessonsKey].orEmpty() + preferences[legacyLessonsKey].orEmpty()
                    .mapNotNull { value -> value.toIntOrNull()?.let(EducationIds.legacyLessons::get) },
                checkpoints = decodeCheckpoints(preferences[checkpointsKey]),
                lastLessonId = preferences[lastLessonKey],
            ),
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

    override suspend fun addContact(name: String, phone: String, relationship: String?) = saveContact(null, name, phone, relationship)

    override suspend fun editContact(id: String, name: String, phone: String, relationship: String?) = saveContact(id, name, phone, relationship)

    private suspend fun saveContact(id: String?, name: String, phone: String, relationship: String?) {
        val cleanName = ContactRules.name(name)
        val cleanPhone = ContactRules.phone(phone)
        val relation = ContactRules.relationship(relationship)
        updateContacts { contacts ->
            require(contacts.none { it.id != id && normalizeCallNumber(it.phone) == normalizeCallNumber(cleanPhone) }) {
                "Ese teléfono ya está en tu lista."
            }
            if (id == null) contacts + EmergencyContact(UUID.randomUUID().toString(), cleanName, cleanPhone, relation)
            else {
                require(contacts.any { it.id == id }) { "Este contacto ya no está en tu lista." }
                contacts.map { if (it.id == id) it.copy(name = cleanName, phone = cleanPhone, relationship = relation) else it }
            }
        }
    }

    override suspend fun setPrimaryContact(id: String) = updateContacts { contacts ->
        require(contacts.any { it.id == id }) { "Este contacto ya no está en tu lista." }
        contacts.map { it.copy(primary = it.id == id) }.sortedByDescending { it.primary }
    }

    override suspend fun moveContactUp(id: String) = updateContacts { contacts ->
        val index = contacts.indexOfFirst { it.id == id }
        if (index <= 0 || contacts[index].primary || contacts[index - 1].primary) contacts
        else contacts.toMutableList().apply { add(index - 1, removeAt(index)) }
    }

    override suspend fun removeContact(id: String) = updateContacts { contacts -> contacts.filterNot { it.id == id } }

    private suspend fun updateContacts(transform: (List<EmergencyContact>) -> List<EmergencyContact>) {
        store.edit { it[contactsKey] = encodeContacts(transform(decodeContacts(it[contactsKey]))) }
    }

    override suspend fun openLesson(id: String) {
        store.edit { preferences ->
            val checkpoints = decodeCheckpoints(preferences[checkpointsKey])
            if (id !in checkpoints) preferences[checkpointsKey] = encodeCheckpoints(checkpoints + (id to LessonCheckpoint()))
            preferences[lastLessonKey] = id
        }
    }

    override suspend fun saveLessonCheckpoint(id: String, checkpoint: LessonCheckpoint) {
        store.edit {
            it[checkpointsKey] = encodeCheckpoints(decodeCheckpoints(it[checkpointsKey]) + (id to checkpoint))
            it[lastLessonKey] = id
        }
    }

    override suspend fun completeLesson(id: String) {
        store.edit {
            it[completedLessonsKey] = it[completedLessonsKey].orEmpty() + id
            it[checkpointsKey] = encodeCheckpoints(decodeCheckpoints(it[checkpointsKey]) - id)
            it[lastLessonKey] = id
        }
    }

    private fun decodeCheckpoints(value: String?): Map<String, LessonCheckpoint> {
        val json = value?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptyMap()
        return buildMap {
            for (id in json.keys()) {
                val entry = json.optJSONObject(id) ?: continue
                val step = LessonStep.entries.firstOrNull { it.name == entry.optString("step") } ?: continue
                put(id, LessonCheckpoint(
                    step = step,
                    answer = entry.optString("answer").takeIf(String::isNotBlank),
                    followUpAnswer = entry.optString("followUpAnswer").takeIf(String::isNotBlank),
                ))
            }
        }
    }

    private fun encodeCheckpoints(checkpoints: Map<String, LessonCheckpoint>): String = JSONObject().apply {
        checkpoints.forEach { (id, checkpoint) ->
            put(id, JSONObject()
                .put("step", checkpoint.step.name)
                .put("answer", checkpoint.answer)
                .put("followUpAnswer", checkpoint.followUpAnswer))
        }
    }.toString()

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
            array.getJSONObject(index).let {
                EmergencyContact(it.getString("id"), it.getString("name"), it.getString("phone"),
                    it.optString("relationship").takeIf(String::isNotBlank), it.optBoolean("primary"))
            }
        }.sortedByDescending { it.primary }
    }
    private fun encodeContacts(contacts: List<EmergencyContact>): String = JSONArray().apply {
        contacts.forEach {
            put(JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone)
                .put("relationship", it.relationship).put("primary", it.primary))
        }
    }.toString()
}
