package cl.grupo28.hestia.domain.gmail

import cl.grupo28.hestia.domain.WeekRange
import java.time.Instant

const val GMAIL_READONLY = "https://www.googleapis.com/auth/gmail.readonly"
enum class EmailRisk { LOW, MEDIUM, HIGH }
data class EmailAnalysis(val score: Int, val level: EmailRisk, val reasons: List<String>)
// Body content is deliberately absent from persisted records and UI state.
data class EmailRecord(val fingerprint: String, val receivedAt: Instant, val sender: String, val subject: String, val analysis: EmailAnalysis, val limitedContent: Boolean = false)
data class GmailSnapshot(val ownerId: String, val week: WeekRange, val checkedAt: Instant, val records: List<EmailRecord>, val partial: Boolean) {
    val suspicious: Int get() = records.count { it.analysis.level != EmailRisk.LOW }
}
class GmailMessage(val id: String, val receivedAt: Instant, val sender: String, val subject: String, val replyTo: String, val text: String, val limitedContent: Boolean)
data class GmailPage(val ids: List<String>, val nextPage: String?)
class GmailAccessException(val kind: Kind) : Exception() {
    enum class Kind { AUTHORIZATION, API_DISABLED, QUOTA, UNAVAILABLE }
}
interface GmailGateway {
    suspend fun profile(token: String): String
    suspend fun list(token: String, week: WeekRange, page: String?): GmailPage
    suspend fun message(token: String, id: String): GmailMessage?
}
interface GmailStore {
    fun load(ownerId: String): GmailSnapshot?
    fun save(snapshot: GmailSnapshot)
    fun clear()
}
