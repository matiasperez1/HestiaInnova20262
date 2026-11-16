package cl.grupo28.hestia.data.gmail

import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.domain.gmail.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.Instant
import kotlin.coroutines.coroutineContext

class GmailRepository(private val api: GmailGateway, private val store: GmailStore, private val engine: EmailRiskEngine = EmailRiskEngine(), private val io: CoroutineDispatcher = Dispatchers.IO) {
    private val mutex = Mutex()
    suspend fun load(owner: String) = withContext(io) { mutex.withLock { store.load(owner) } }
    suspend fun clear() = withContext(io) { mutex.withLock { store.clear() } }
    suspend fun verify(token: String, expectedEmail: String) = withContext(io) {
        require(api.profile(token).equals(expectedEmail, ignoreCase = true)) { "Different Gmail account" }
    }
    suspend fun sync(token: String, owner: String, expectedEmail: String, week: WeekRange): GmailSnapshot = withContext(io) {
        mutex.withLock {
            verify(token, expectedEmail)
            val records = linkedMapOf<String, EmailRecord>()
            val visited = mutableSetOf<String>()
            val pages = mutableSetOf<String?>()
            var page: String? = null
            var partial = false
            do {
                coroutineContext.ensureActive()
                if (pages.size >= 10 || !pages.add(page)) { partial = true; break }
                val result = api.list(token, week, page)
                for (id in result.ids) {
                    coroutineContext.ensureActive()
                    if (!visited.add(id)) continue
                    if (visited.size > 200) { partial = true; break }
                    val message = api.message(token, id) ?: continue // Deleted since listing.
                    if (!week.contains(message.receivedAt) || message.receivedAt > Instant.now()) continue
                    val fingerprint = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
                    records[fingerprint] = EmailRecord(fingerprint, message.receivedAt, message.sender.take(160), message.subject.take(120),
                        engine.analyze(message.subject, message.text, message.sender, message.replyTo), message.limitedContent)
                }
                page = result.nextPage
                if (visited.size >= 200 && page != null) partial = true
            } while (page != null && visited.size < 200 && !partial)
            coroutineContext.ensureActive()
            val snapshot = GmailSnapshot(owner, week, Instant.now(), records.values.sortedByDescending { it.receivedAt }, partial)
            store.save(snapshot) // Commit only a completed bounded run; failures preserve the previous snapshot.
            snapshot
        }
    }
}
