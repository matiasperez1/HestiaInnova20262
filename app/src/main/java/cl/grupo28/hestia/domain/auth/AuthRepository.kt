package cl.grupo28.hestia.domain.auth

import kotlinx.coroutines.flow.StateFlow

data class Account(val id: String, val email: String)
data class AuthState(
    val configured: Boolean,
    val account: Account? = null,
    val busy: Boolean = false,
    val verified: Boolean = false,
    val message: String? = null,
)

// Tokens never belong in UI state, logs, intents or saved instance state.
class AuthSession(val accessToken: String, val refreshToken: String, val expiresAt: Long, val account: Account)
class InvalidSessionException : Exception()
interface SessionStore {
    fun load(): AuthSession?
    fun save(session: AuthSession)
    fun clear()
}
interface AuthGateway {
    suspend fun exchange(idToken: String, rawNonce: String): AuthSession
    suspend fun refresh(refreshToken: String): AuthSession
    suspend fun validate(accessToken: String): Account
    suspend fun logout(accessToken: String)
}
interface AuthRepository {
    val state: StateFlow<AuthState>
    suspend fun restore()
    suspend fun signIn(idToken: String, rawNonce: String)
    suspend fun signOut()
    fun showMessage(message: String)
}
