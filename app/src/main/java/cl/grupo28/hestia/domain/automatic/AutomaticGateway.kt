package cl.grupo28.hestia.domain.automatic

data class AutomaticStatus(val status: String, val active: Boolean, val lastSync: String?, val expiration: String?, val startedAt: String?)
data class AutomaticSummary(val analyzed: Int, val suspicious: Int, val weekStart: String, val weekEnd: String)
data class GmailAlert(val id: String, val receivedAt: String, val risk: String, val subject: String, val sender: String, val reasons: List<String>, val limited: Boolean)
data class AlertPage(val items: List<GmailAlert>, val next: String?)
class AutomaticError(val code: String) : Exception(code)
interface AutomaticGateway {
    val configured: Boolean
    suspend fun status(owner: String): AutomaticStatus
    suspend fun connect(owner: String, code: String): AutomaticStatus
    suspend fun sync(owner: String)
    suspend fun disconnect(owner: String, deleteEvents: Boolean): Boolean
    suspend fun summary(owner: String): AutomaticSummary
    suspend fun alerts(owner: String, before: String? = null): AlertPage
    suspend fun alert(owner: String, id: String): GmailAlert
    suspend fun register(owner: String, installation: String, token: String, enabled: Boolean)
    suspend fun unregister(owner: String, installation: String)
}
