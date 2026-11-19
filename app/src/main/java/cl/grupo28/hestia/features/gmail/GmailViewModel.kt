package cl.grupo28.hestia.features.gmail

import android.content.Context
import androidx.lifecycle.*
import cl.grupo28.hestia.data.gmail.*
import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.domain.auth.AuthState
import cl.grupo28.hestia.domain.gmail.*
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.util.UUID

enum class GmailOperation { AUTHORIZING, REVIEWING, DISCONNECTING }

data class GmailUiState(val eligible: Boolean = false, val verified: Boolean = false, val busy: Boolean = false,
    val snapshot: GmailSnapshot? = null, val message: String? = null, val authorizationReceived: Boolean = false, val operation: GmailOperation? = null,
    val connectionLost: Boolean = false)

class GmailViewModel(private val repository: GmailRepository, auth: StateFlow<AuthState>, private val invalidate: (String) -> Unit = {}) : ViewModel() {
    private val mutableState = MutableStateFlow(GmailUiState())
    val state = mutableState.asStateFlow()
    private var owner: String? = null
    private var email: String? = null
    private var operation: Job? = null
    private var pending: Pair<String, Boolean>? = null
    init {
        viewModelScope.launch {
            auth.collectLatest { account ->
                val previous = owner
                if (previous != account.account?.id) {
                    operation?.cancel(); pending = null
                    owner = account.account?.id; email = account.account?.email
                    mutableState.value = GmailUiState()
                    try {
                        val snapshot = owner?.let { repository.load(it) }
                        if (owner == null && previous != null) repository.clear()
                        mutableState.value = GmailUiState(snapshot = snapshot)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { mutableState.value = GmailUiState(message = "No pudimos recuperar la revisión guardada.") }
                }
                email = account.account?.email
                mutableState.update { it.copy(eligible = account.account != null && account.verified && !account.busy) }
            }
        }
    }
    fun begin(sync: Boolean, authorize: (String, String) -> Unit) {
        if (!state.value.eligible || state.value.busy) return
        val address = email ?: return
        val id = UUID.randomUUID().toString()
        pending = id to sync
        mutableState.update { it.copy(busy = true, message = null, operation = GmailOperation.AUTHORIZING, connectionLost = false) }
        try { authorize(address, id) } catch (_: Exception) { failed(id, "No pudimos abrir la autorización de Gmail.") }
    }
    fun failed(id: String, message: String) {
        if (pending?.first != id) return
        pending = null
        mutableState.update { it.copy(busy = false, verified = false, message = message, operation = null) }
    }
    fun authorized(id: String, token: String?, scopes: List<String>) {
        val request = pending?.takeIf { it.first == id } ?: return
        if (token.isNullOrBlank() || GMAIL_READONLY !in scopes) { failed(id, "No se concedió el permiso de lectura de Gmail."); return }
        val expectedOwner = owner ?: return
        val expectedEmail = email ?: return
        pending = null
        mutableState.update { it.copy(authorizationReceived = true, operation = if (request.second) GmailOperation.REVIEWING else GmailOperation.AUTHORIZING) }
        operation = viewModelScope.launch {
            try {
                val snapshot = if (request.second) repository.sync(token, expectedOwner, expectedEmail, WeekRange.current())
                    else { repository.verify(token, expectedEmail); state.value.snapshot }
                if (owner == expectedOwner) mutableState.update { it.copy(verified = true, snapshot = snapshot,
                    message = if (request.second) { if (snapshot?.partial == true) "Revisión parcial terminada." else "Revisión completada." } else "Autorización verificada. Puedes revisar tus correos.") }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: GmailAccessException) {
                if (error.kind == GmailAccessException.Kind.AUTHORIZATION) runCatching { invalidate(token) }
                if (owner == expectedOwner) mutableState.update { it.copy(verified = false, message = when(error.kind) {
                    GmailAccessException.Kind.AUTHORIZATION -> "El acceso a Gmail venció o fue revocado. Vuelve a autorizarlo."
                    GmailAccessException.Kind.API_DISABLED -> "La conexión a Gmail todavía no está habilitada para Hestia."
                    GmailAccessException.Kind.QUOTA -> "Gmail recibió muchas solicitudes. Espera unos minutos y reintenta."
                    else -> "No pudimos revisar Gmail. Inténtalo nuevamente."
                }) }
            } catch (_: IllegalArgumentException) {
                if (owner == expectedOwner) mutableState.update { it.copy(verified = false, message = "La cuenta de Gmail no coincide con tu cuenta de Hestia.") }
            } catch (_: IOException) {
                if (owner == expectedOwner) mutableState.update { it.copy(connectionLost = true, message = "No pudimos conectarnos a Gmail. Puedes volver a intentar la revisión.") }
            } catch (_: Exception) {
                if (owner == expectedOwner) mutableState.update { it.copy(message = "No pudimos terminar la revisión. Se conserva el resultado anterior. Inténtalo nuevamente.") }
            } finally { if (owner == expectedOwner) mutableState.update { it.copy(busy = false, operation = null) } }
        }
    }
    fun disconnect(revoke: (String, (Boolean) -> Unit) -> Unit) {
        if (state.value.busy) return
        val expectedOwner = owner ?: return
        val address = email ?: return
        operation = viewModelScope.launch {
            mutableState.update { it.copy(busy = true, operation = GmailOperation.DISCONNECTING) }
            try {
                repository.clear()
                if (owner != expectedOwner) return@launch
                mutableState.value = GmailUiState(eligible = state.value.eligible, busy = true, operation = GmailOperation.DISCONNECTING)
                revoke(address) { success ->
                    if (owner == expectedOwner) mutableState.update { it.copy(busy = false, operation = null, message = if (success)
                        "Se revocó el acceso a Google y se borró la revisión local."
                        else "Gmail quedó desconectado en este teléfono. No pudimos confirmar la revocación en Google.") }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (owner == expectedOwner) mutableState.update { it.copy(busy = false, operation = null, message = "No pudimos desconectar Gmail. Inténtalo nuevamente.") } }
        }
    }
    class Factory(context: Context, private val auth: StateFlow<AuthState>) : ViewModelProvider.Factory {
        private val context = context.applicationContext
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(GmailViewModel::class.java))
            return GmailViewModel(GmailRepository(GmailApi(), EncryptedGmailStore(context)), auth) { token ->
                Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(token).build())
            } as T
        }
    }
}
