package cl.grupo28.hestia.data.calls

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.AtomicFile
import androidx.core.content.ContextCompat
import cl.grupo28.hestia.data.automatic.DemoModeStore
import cl.grupo28.hestia.data.automatic.LocalDemoGateway
import cl.grupo28.hestia.domain.CallVerdict
import cl.grupo28.hestia.domain.automatic.DemoSelection
import cl.grupo28.hestia.domain.normalizeCallNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant

private val fictionalNumber = Regex("20255501[0-9]{2}")
internal const val CALL_DEMO_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

internal fun demoCallNumber(input: String): String? {
    val normalized = normalizeCallNumber(input) ?: return null
    val national = if (normalized.startsWith("+")) normalized.removePrefix("+1") else normalized.removePrefix("1")
    return national.takeIf(fictionalNumber::matches)
}

internal fun communityCallNumber(input: String): String? = demoCallNumber(input)

internal enum class CallPersona(val id: String, val label: String) { ANA("alice", "Ana"), BRUNO("bob", "Bruno") }

internal enum class CallReportCategory(val label: String, val selectable: Boolean = true) {
    POSSIBLE_FRAUD("Posible estafa"),
    IMPERSONATION("Suplantación de identidad"),
    UNWANTED_ADS("Publicidad no deseada"),
    HARASSMENT("Acoso o llamadas insistentes"),
    OTHER_GENERAL("Otro"),
    REQUEST_FOR_MONEY("Solicitud de dinero", false),
    REQUEST_FOR_CODES("Solicitud de claves o códigos", false),
    OTHER("Otro posible fraude", false),
}

internal data class DemoReputationSnapshot(
    val syncedAt: Instant, val threshold: Int, val counts: Map<String, Int>,
    val fraudCounts: Map<String, Int> = counts,
    val categoryCounts: Map<String, Map<CallReportCategory, Int>> = emptyMap(),
) {
    fun verdict(number: String): CallVerdict = when (fraudCounts[number] ?: 0) {
        0 -> CallVerdict.DEBUG_NO_MATCH
        in 1 until threshold -> CallVerdict.DEBUG_PENDING
        else -> CallVerdict.DEBUG_REPORTED
    }
}

internal class DemoReputationCache private constructor(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "demo-call-reputation.json"))
    private var loaded = false
    private var current: DemoReputationSnapshot? = null
    internal val syncLock = Mutex()

    @Synchronized fun read(): DemoReputationSnapshot? {
        if (!loaded) {
            current = runCatching {
                val json = JSONObject(file.openRead().bufferedReader().use { it.readText() })
                parseSnapshot(json, Instant.parse(json.getString("syncedAt")), allowLegacyCache = true).snapshot
            }.getOrNull()
            loaded = true
        }
        return current
    }

    @Synchronized fun save(snapshot: DemoReputationSnapshot) {
        val entries = JSONArray()
        for ((number, count) in snapshot.counts) {
            val categories = JSONObject()
            snapshot.categoryCounts[number].orEmpty().forEach { (category, total) -> categories.put(category.name, total) }
            entries.put(JSONObject().put("number", number).put("count", count)
                .put("fraudCount", snapshot.fraudCounts[number] ?: 0).put("categoryCounts", categories))
        }
        val bytes = JSONObject().put("syncedAt", snapshot.syncedAt.toString())
            .put("threshold", snapshot.threshold).put("entries", entries).toString().toByteArray()
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: IOException) {
            file.failWrite(output)
            throw error
        }
        current = snapshot
        loaded = true
    }

    companion object {
        private var instance: DemoReputationCache? = null
        @Synchronized fun from(context: Context): DemoReputationCache = instance
            ?: DemoReputationCache(context.applicationContext).also { instance = it }
    }
}

internal data class DemoReports(val snapshot: DemoReputationSnapshot, val own: Map<String, CallReportCategory>)

internal interface DemoCallReportRepository {
    val persona: StateFlow<CallPersona>
    val ownership: StateFlow<Map<CallPersona, Map<String, CallReportCategory>>>
    val reputation: StateFlow<DemoReputationSnapshot?>
    suspend fun cached(): DemoReputationSnapshot?
    suspend fun sync(persona: CallPersona): DemoReports
    suspend fun save(persona: CallPersona, number: String, category: CallReportCategory, replace: Boolean = false)
    suspend fun withdraw(persona: CallPersona, number: String)
}

private fun parseSnapshot(json: JSONObject, syncedAt: Instant, allowLegacyCache: Boolean = false): DemoReports {
    val threshold = json.getInt("threshold").also { require(it >= 2) }
    val entries = json.getJSONArray("entries").also { require(it.length() <= 100) }
    val counts = mutableMapOf<String, Int>()
    val fraud = mutableMapOf<String, Int>()
    val categories = mutableMapOf<String, Map<CallReportCategory, Int>>()
    val own = mutableMapOf<String, CallReportCategory>()
    repeat(entries.length()) { index ->
        val item = entries.getJSONObject(index)
        val number = item.getString("number").also { require(communityCallNumber(it) == it) }
        val count = item.getInt("count").also { require(it in 1..CallPersona.entries.size) }
        require(counts.put(number, count) == null)
        fraud[number] = (if (allowLegacyCache) item.optInt("fraudCount", count) else item.getInt("fraudCount"))
            .also { require(it in 0..count) }
        (if (allowLegacyCache) item.optJSONObject("categoryCounts") else item.getJSONObject("categoryCounts"))?.let { byCategory ->
            categories[number] = byCategory.keys().asSequence().associate { key ->
                CallReportCategory.valueOf(key) to byCategory.getInt(key).also { require(it in 1..count) }
            }
        }
        if (!item.isNull("ownCategory")) own[number] = CallReportCategory.valueOf(item.getString("ownCategory"))
    }
    return DemoReports(DemoReputationSnapshot(syncedAt, threshold, counts, fraud, categories), own)
}

internal fun canSyncCallDemo(context: Context): Boolean = Build.VERSION.SDK_INT < 37 ||
    ContextCompat.checkSelfPermission(context, CALL_DEMO_PERMISSION) == PackageManager.PERMISSION_GRANTED

internal class DemoCallReports private constructor(context: Context) : DemoCallReportRepository {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("phone_demo_identity", Context.MODE_PRIVATE)
    private val selected = MutableStateFlow(CallPersona.entries.firstOrNull { it.id == preferences.getString("persona", null) } ?: CallPersona.ANA)
    override val persona = selected.asStateFlow()
    private val known = MutableStateFlow<Map<CallPersona, Map<String, CallReportCategory>>>(emptyMap())
    override val ownership = known.asStateFlow()
    private val latest = MutableStateFlow<DemoReputationSnapshot?>(null)
    override val reputation = latest.asStateFlow()

    fun select(persona: CallPersona) {
        preferences.edit().putString("persona", persona.id).apply()
        selected.value = persona
    }
    private val cache = DemoReputationCache.from(app)
    private val clients = CallPersona.entries.associateWith { persona ->
        // Keep these sessions independent of the Gmail demo selection and the real account.
        val selection = DemoSelection(true, persona.id)
        LocalDemoGateway(object : DemoModeStore {
            override fun read() = selection
            override fun save(selection: DemoSelection) = error("Phone demo identities are fixed")
        }, localPermission = { canSyncCallDemo(app) })
    }

    override suspend fun cached(): DemoReputationSnapshot? = withContext(Dispatchers.IO) {
        cache.read().also { latest.compareAndSet(null, it) }
    }

    override suspend fun sync(persona: CallPersona): DemoReports = cache.syncLock.withLock {
        val json = request(persona, "/demo/calls/reputation")
        val result = parseSnapshot(json, Instant.now())
        withContext(Dispatchers.IO) { cache.save(result.snapshot) }
        known.update { it + (persona to result.own) }
        latest.value = result.snapshot
        result
    }

    override suspend fun save(persona: CallPersona, number: String, category: CallReportCategory, replace: Boolean) = cache.syncLock.withLock {
        require(demoCallNumber(number) == number)
        request(persona, "/demo/calls/report", JSONObject().put("number", number).put("replace", replace)
            .put("category", category.name).put("consent", true))
        known.update { it + (persona to (it[persona].orEmpty() + (number to category))) }
    }

    override suspend fun withdraw(persona: CallPersona, number: String) = cache.syncLock.withLock {
        require(demoCallNumber(number) == number)
        request(persona, "/demo/calls/withdraw", JSONObject().put("number", number))
        known.update { entries -> entries[persona]?.let { entries + (persona to (it - number)) } ?: entries }
    }

    private suspend fun request(persona: CallPersona, path: String, body: JSONObject? = null): JSONObject =
        clients.getValue(persona).call(LocalDemoGateway.owner(persona.id), path, body)

    companion object {
        private var instance: DemoCallReports? = null
        @Synchronized fun from(context: Context) = instance ?: DemoCallReports(context).also { instance = it }
    }
}
