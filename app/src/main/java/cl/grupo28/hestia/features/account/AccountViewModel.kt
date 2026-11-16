package cl.grupo28.hestia.features.account

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.grupo28.hestia.data.auth.*
import cl.grupo28.hestia.domain.auth.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AccountViewModel(private val repository: AuthRepository, private val picker: GoogleAccountPicker) : ViewModel() {
    private val mutableState = MutableStateFlow(repository.state.value)
    val state = mutableState.asStateFlow()
    private var action: Job? = null
    private var foreground: Job? = null
    init { viewModelScope.launch { repository.state.collect { mutableState.value = it.copy(busy = it.busy || action?.isActive == true) } } }
    fun start() {
        if (foreground?.isActive == true) return
        foreground = viewModelScope.launch {
            while (isActive) {
                if (action?.isActive != true) repository.restore()
                delay(60_000)
            }
        }
    }
    fun stop() { foreground?.cancel(); foreground = null }
    fun signIn(context: Context) = signInWith { picker.pick(context) }
    internal fun signInWith(pickAccount: suspend () -> GoogleIdentity) {
        if (action?.isActive == true || !state.value.configured) return
        action = viewModelScope.launch {
            mutableState.update { it.copy(busy = true, message = null) }
            try {
                val identity = pickAccount()
                repository.signIn(identity.idToken, identity.rawNonce)
            } catch (cancelled: GetCredentialCancellationException) { repository.showMessage("Cancelaste el inicio de sesión. Puedes continuar sin cuenta.") }
            catch (_: NoCredentialException) { repository.showMessage("No encontramos una cuenta Google disponible. Revisa las cuentas del teléfono.") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { repository.showMessage("No pudimos abrir tu cuenta Google. Inténtalo nuevamente.") }
            finally { mutableState.value = repository.state.value.copy(busy = false) }
        }
    }
    fun retry() {
        if (action?.isActive == true) return
        action = viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            try { repository.restore() }
            finally { mutableState.value = repository.state.value.copy(busy = false) }
        }
    }
    fun signOut() {
        if (action?.isActive == true) return
        action = viewModelScope.launch {
            try {
                repository.signOut()
                if (repository.state.value.account == null) {
                    try { picker.clear() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { repository.showMessage(repository.state.value.message.orEmpty() + " No pudimos restablecer el selector de Google; elige tu cuenta al volver a entrar.") }
                }
            } finally { mutableState.value = repository.state.value.copy(busy = false) }
        }
    }
    class Factory(context: Context) : ViewModelProvider.Factory {
        private val context = context.applicationContext
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AccountViewModel::class.java))
            val config = AuthConfiguration.current()
            val repo = SessionAuthRepository(if (config.valid) SupabaseAuthGateway(config) else null, KeystoreSessionStore(context))
            return AccountViewModel(repo, CredentialGoogleAccountPicker(context, config.webClientId)) as T
        }
    }
}
