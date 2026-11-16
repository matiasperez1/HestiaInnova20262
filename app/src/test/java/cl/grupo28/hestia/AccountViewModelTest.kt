package cl.grupo28.hestia

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import cl.grupo28.hestia.domain.auth.*
import cl.grupo28.hestia.features.account.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private class Repo : AuthRepository {
        override val state = MutableStateFlow(AuthState(true))
        var exchanges = 0
        override suspend fun restore() { }
        override suspend fun signIn(idToken: String, rawNonce: String) {
            exchanges++; state.value = AuthState(true, Account("test", "test@example.invalid"), verified = true)
        }
        override suspend fun signOut() { state.value = AuthState(true) }
        override fun showMessage(message: String) { state.value = state.value.copy(message = message) }
    }
    // The picker abstraction does not need an Android Context in these tests.
    private abstract class Picker : GoogleAccountPicker {
        override suspend fun clear() { }
    }
    @Test fun cancellationDoesNotExchangeOrAuthenticate() = runTest(dispatcher) {
        val repo = Repo()
        val model = AccountViewModel(repo, object : Picker() {
            override suspend fun pick(activityContext: Context): GoogleIdentity = throw GetCredentialCancellationException()
        })
        model.signInWith { throw GetCredentialCancellationException() }; advanceUntilIdle()
        assertEquals(0, repo.exchanges); assertNull(model.state.value.account)
        assertFalse(model.state.value.busy); assertTrue(model.state.value.message.orEmpty().contains("Cancelaste"))
    }
    @Test fun noGoogleAccountExplainsRecoveryWithoutAuthenticating() = runTest(dispatcher) {
        val repo = Repo()
        val model = AccountViewModel(repo, object : Picker() {
            override suspend fun pick(activityContext: Context): GoogleIdentity = throw NoCredentialException()
        })
        model.signInWith { throw NoCredentialException() }; advanceUntilIdle()
        assertEquals(0, repo.exchanges); assertNull(model.state.value.account)
        assertTrue(model.state.value.message.orEmpty().contains("cuenta Google disponible"))
    }
    @Test fun duplicateTapStartsOnlyOneExchange() = runTest(dispatcher) {
        val repo = Repo()
        val picker = object : Picker() { override suspend fun pick(activityContext: Context): GoogleIdentity = error("Not used by launch-boundary test") }
        val model = AccountViewModel(repo, picker)
        val credential = CompletableDeferred<GoogleIdentity>()
        model.signInWith { credential.await() }; runCurrent()
        assertTrue(model.state.value.busy)
        model.signInWith { error("Duplicate picker must not open") }
        credential.complete(GoogleIdentity("id-token", "raw-nonce")); advanceUntilIdle()
        assertEquals(1, repo.exchanges); assertTrue(model.state.value.verified); assertFalse(model.state.value.busy)
    }
}
