package cl.grupo28.hestia.data.auth

import cl.grupo28.hestia.domain.auth.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SessionAuthRepository(
    private val gateway: AuthGateway?,
    private val store: SessionStore,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) : AuthRepository {
    private val mutableState = MutableStateFlow(AuthState(configured = gateway != null))
    override val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var session: AuthSession? = null
    private var loaded = false

    override fun showMessage(message: String) { mutableState.update { it.copy(message = message) } }

    override suspend fun restore() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val api = gateway ?: return@withLock
            if (!loaded) mutableState.update { it.copy(busy = true) }
            try {
                if (!loaded) { session = store.load(); loaded = true }
                val saved = session ?: return@withLock
                val checked = if (saved.expiresAt <= now() + 60) api.refresh(saved.refreshToken)
                    else try {
                        AuthSession(saved.accessToken, saved.refreshToken, saved.expiresAt, api.validate(saved.accessToken))
                    } catch (_: InvalidSessionException) { api.refresh(saved.refreshToken) }
                store.save(checked)
                session = checked
                mutableState.value = AuthState(true, checked.account, verified = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: InvalidSessionException) {
                session = null; loaded = true
                val cleared = runCatching { store.clear() }.isSuccess
                mutableState.value = AuthState(true, message = if (cleared) "Tu sesión terminó. Puedes volver a iniciar sesión."
                    else "Tu sesión terminó. No pudimos borrar sus datos. Cierra y vuelve a abrir Hestia para reintentar.")
            } catch (_: Exception) {
                mutableState.value = AuthState(true, session?.account, verified = false,
                    message = "No pudimos verificar tu cuenta. Revisa tu conexión e inténtalo nuevamente.")
            } finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    override suspend fun signIn(idToken: String, rawNonce: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val api = gateway ?: return@withLock
            mutableState.update { it.copy(busy = true, message = null) }
            try {
                val signedIn = api.exchange(idToken, rawNonce)
                // Publish only after secure persistence succeeds.
                try { store.save(signedIn) }
                catch (failure: Exception) {
                    // Do not leave a newly issued session active if secure storage failed.
                    try { api.logout(signedIn.accessToken) } catch (_: Exception) { }
                    throw failure
                }
                session = signedIn; loaded = true
                mutableState.value = AuthState(true, signedIn.account, verified = true, message = "Iniciaste sesión en Hestia.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { showMessage("No pudimos iniciar sesión. Inténtalo nuevamente.") }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    override suspend fun signOut() = withContext(Dispatchers.IO) {
        mutex.withLock {
            mutableState.update { it.copy(busy = true, message = null) }
            try {
                val saved = session ?: store.load()
                // Erase local credentials even when the device is offline.
                store.clear(); session = null; loaded = true
                var remoteClosed = true
                if (saved != null && gateway != null) {
                    try {
                        val current = if (saved.expiresAt <= now()) gateway.refresh(saved.refreshToken) else saved
                        gateway.logout(current.accessToken)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: InvalidSessionException) { /* Already invalidated by the server. */ }
                    catch (_: Exception) { remoteClosed = false }
                }
                mutableState.value = AuthState(gateway != null, message = if (remoteClosed) "Cerraste sesión en este teléfono."
                    else "Cerraste sesión en este teléfono. No pudimos confirmar el cierre en el servidor.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { showMessage("No pudimos borrar la sesión. Intenta cerrar sesión nuevamente.") }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }
}
