package cl.grupo28.hestia.data

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import cl.grupo28.hestia.data.security.EncryptedDeviceFile
import cl.grupo28.hestia.domain.PERSONAL_REPORT_DESCRIPTION_LIMIT
import cl.grupo28.hestia.domain.PersonalCallReport
import cl.grupo28.hestia.domain.PersonalReportReason
import cl.grupo28.hestia.domain.normalizePersonalNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.Locale
import java.util.UUID

internal sealed interface PersonalReportState {
    data object Loading : PersonalReportState
    data object Unavailable : PersonalReportState
    data class Ready(val reports: Map<String, PersonalCallReport>) : PersonalReportState

    fun active(number: String?): PersonalCallReport? = number?.let { (this as? Ready)?.reports?.get(it) }?.takeIf { it.active }
}

internal class PersonalReports(
    private val store: PersonalReportStore,
    private val canonicalize: (String) -> String?,
    private val now: () -> Instant = Instant::now,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Mutex()
    private val current = MutableStateFlow<PersonalReportState>(PersonalReportState.Loading)
    val state = current.asStateFlow()

    fun normalize(input: String): String? = canonicalize(input)

    suspend fun load() = withContext(io) { lock.withLock { readIfNeeded() } }

    suspend fun active(input: String): PersonalCallReport? {
        val number = normalize(input) ?: return null
        return load().active(number)
    }

    suspend fun save(input: String, reason: PersonalReportReason, description: String?, editingId: String? = null): PersonalCallReport =
        withContext(io) { lock.withLock {
            val number = requireNotNull(normalize(input)) { "Invalid number" }
            val note = description?.trim()?.takeIf(String::isNotEmpty)
            require(note == null || note.length <= PERSONAL_REPORT_DESCRIPTION_LIMIT)
            val entries = requireAvailable()
            val existing = entries[number]
            if (editingId != null && (existing?.active != true || existing.id != editingId)) throw PersonalReportChanged()
            if (editingId == null && existing?.active == true) throw PersonalReportChanged()
            val time = now()
            val report = PersonalCallReport(existing?.id ?: UUID.randomUUID().toString(), number,
                existing?.originalNumber?.takeIf { editingId != null } ?: input.trim(), reason, note,
                existing?.createdAt?.takeIf { editingId != null } ?: time, true, time)
            val updated = entries + (number to report)
            store.save(updated)
            current.value = PersonalReportState.Ready(updated)
            report
        } }

    suspend fun withdraw(report: PersonalCallReport) = withContext(io) { lock.withLock {
        val entries = requireAvailable()
        val existing = entries[report.normalizedNumber]
        if (existing != report || !report.active) throw PersonalReportChanged()
        val updated = entries + (report.normalizedNumber to report.copy(active = false, modifiedAt = now()))
        store.save(updated)
        current.value = PersonalReportState.Ready(updated)
    } }

    private fun requireAvailable() = (readIfNeeded() as? PersonalReportState.Ready)?.reports
        ?: throw IOException("Personal reports unavailable")

    private fun readIfNeeded(): PersonalReportState {
        val existing = current.value
        if (existing is PersonalReportState.Ready) return existing
        val loaded = try { PersonalReportState.Ready(store.load()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { PersonalReportState.Unavailable }
        current.value = loaded
        return loaded
    }

    companion object {
        private var instance: PersonalReports? = null
        @Synchronized fun from(context: Context): PersonalReports = instance ?: run {
            val app = context.applicationContext
            val file = EncryptedDeviceFile(app, "hestia-personal-call-reports", "hestia.calls.personal.v1")
            PersonalReports(PersonalReportStore(file::read, file::write), { value -> AndroidReportNumber.normalize(app, value) })
                .also { instance = it }
        }
    }
}

internal class PersonalReportChanged : IOException()

internal class PersonalReportStore(private val read: () -> ByteArray?, private val write: (ByteArray) -> Unit) {
    fun load(): Map<String, PersonalCallReport> {
        val content = read() ?: return emptyMap()
        val payload = JSONObject(content.toString(Charsets.UTF_8))
        require(payload.getInt("version") == 1)
        val items = payload.getJSONArray("reports")
        val entries = buildMap {
            repeat(items.length()) { index ->
                val row = items.getJSONObject(index)
                val key = row.getString("number")
                val report = PersonalCallReport(row.getString("id"), key, row.getString("original"),
                    PersonalReportReason.valueOf(row.getString("reason")), row.optString("description").ifEmpty { null },
                    Instant.parse(row.getString("createdAt")), row.getBoolean("active"), Instant.parse(row.getString("modifiedAt")))
                require(put(key, report) == null)
            }
        }
        return entries
    }

    fun save(reports: Map<String, PersonalCallReport>) {
        val rows = JSONArray()
        reports.values.forEach { report ->
            rows.put(JSONObject().put("id", report.id).put("number", report.normalizedNumber).put("original", report.originalNumber)
                .put("reason", report.reason.name).put("description", report.description.orEmpty())
                .put("createdAt", report.createdAt.toString()).put("active", report.active).put("modifiedAt", report.modifiedAt.toString()))
        }
        write(JSONObject().put("version", 1).put("reports", rows).toString().toByteArray(Charsets.UTF_8))
    }
}

private object AndroidReportNumber {
    fun normalize(context: Context, value: String): String? {
        val sim = runCatching { context.getSystemService(TelephonyManager::class.java)?.simCountryIso }.getOrNull()
        val region = sim?.takeIf { it.length == 2 }
            ?: context.resources.configuration.locales[0]?.country.orEmpty().ifBlank { "ZZ" }
        return normalizePersonalNumber(value, region.uppercase(Locale.ROOT), PhoneNumberUtils::formatNumberToE164)
    }
}
