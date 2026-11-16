package cl.grupo28.hestia

import cl.grupo28.hestia.data.auth.*
import cl.grupo28.hestia.domain.auth.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AuthRepositoryTest {
    private val account = Account("test-user", "account@example.invalid")
    private fun session(expiry: Long = 1000, refresh: String = "old-refresh") = AuthSession("test-access", refresh, expiry, account)
    private class Store(var value: AuthSession? = null) : SessionStore {
        var failSave = false
        override fun load() = value
        override fun save(session: AuthSession) { if (failSave) throw IOException(); value = session }
        override fun clear() { value = null }
    }
    private inner class Gateway : AuthGateway {
        var offline = false
        var revoked = false
        var expiredAccess = false
        var refreshed = false
        override suspend fun exchange(idToken: String, rawNonce: String): AuthSession {
            assertEquals("id-token", idToken); assertEquals("raw-nonce", rawNonce)
            if (offline) throw IOException()
            return session()
        }
        override suspend fun refresh(refreshToken: String): AuthSession {
            if (offline) throw IOException()
            if (revoked) throw InvalidSessionException()
            refreshed = true
            return session(2000, "rotated-refresh")
        }
        override suspend fun validate(accessToken: String): Account {
            if (offline) throw IOException()
            if (revoked || expiredAccess) throw InvalidSessionException()
            return account
        }
        override suspend fun logout(accessToken: String) { if (offline) throw IOException() }
    }
    @Test fun unconfiguredNeverPublishesAccount() = runBlocking {
        val repo = SessionAuthRepository(null, Store())
        repo.signIn("id-token", "raw-nonce"); repo.restore()
        assertFalse(repo.state.value.configured); assertNull(repo.state.value.account)
    }
    @Test fun successfulExchangePersistsBeforePublishingIdentity() = runBlocking {
        val store = Store(); val repo = SessionAuthRepository(Gateway(), store) { 100 }
        repo.signIn("id-token", "raw-nonce")
        assertEquals(account, repo.state.value.account); assertTrue(repo.state.value.verified)
        assertNotNull(store.value); assertFalse(repo.state.value.busy)
    }
    @Test fun storageFailureDoesNotPublishSuccessfulLogin() = runBlocking {
        val store = Store().apply { failSave = true }; val repo = SessionAuthRepository(Gateway(), store)
        repo.signIn("id-token", "raw-nonce")
        assertNull(repo.state.value.account); assertNotNull(repo.state.value.message)
    }
    @Test fun expiredSessionRefreshesAndStoresRotatedToken() = runBlocking {
        val store = Store(session(50)); val gateway = Gateway(); val repo = SessionAuthRepository(gateway, store) { 100 }
        repo.restore()
        assertTrue(gateway.refreshed); assertEquals("rotated-refresh", store.value?.refreshToken)
        assertEquals(account, repo.state.value.account)
    }
    @Test fun newRepositoryRestoresAndValidatesExistingIdentity() = runBlocking {
        val store = Store(session()); val repo = SessionAuthRepository(Gateway(), store) { 100 }
        repo.restore(); assertTrue(repo.state.value.verified); assertEquals(account, repo.state.value.account)
    }
    @Test fun revokedSessionIsRemoved() = runBlocking {
        val store = Store(session(50)); val repo = SessionAuthRepository(Gateway().apply { revoked = true }, store) { 100 }
        repo.restore(); assertNull(repo.state.value.account); assertNull(store.value)
    }
    @Test fun offlineRestorationDoesNotClaimVerifiedSession() = runBlocking {
        val store = Store(session()); val repo = SessionAuthRepository(Gateway().apply { offline = true }, store) { 100 }
        repo.restore(); assertEquals(account, repo.state.value.account); assertFalse(repo.state.value.verified)
        assertNotNull(store.value)
    }
    @Test fun offlineLogoutClearsLocalTokensAndExplainsRemoteLimit() = runBlocking {
        val store = Store(session()); val gateway = Gateway(); val repo = SessionAuthRepository(gateway, store) { 100 }
        repo.restore(); gateway.offline = true; repo.signOut()
        assertNull(store.value); assertNull(repo.state.value.account)
        assertTrue(repo.state.value.message.orEmpty().contains("servidor"))
    }
    @Test fun rejectedAccessTokenCanRecoverThroughValidRefreshToken() = runBlocking {
        val store = Store(session()); val gateway = Gateway().apply { expiredAccess = true }
        val repo = SessionAuthRepository(gateway, store) { 100 }
        repo.restore(); assertTrue(gateway.refreshed); assertTrue(repo.state.value.verified)
        assertEquals("rotated-refresh", store.value?.refreshToken)
    }
    @Test fun rejectsAdministrativeOrMalformedConfiguration() {
        assertFalse(AuthConfiguration("http://example.invalid", "sb_publishable_public-value", "test.apps.googleusercontent.com").valid)
        assertFalse(AuthConfiguration("https://example.invalid", "sb_secret_not-for-apk", "test.apps.googleusercontent.com").valid)
        assertTrue(AuthConfiguration("https://example.invalid", "sb_publishable_public-value", "test.apps.googleusercontent.com").valid)
    }
}
