package cl.grupo28.hestia.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cl.grupo28.hestia.core.accessibility.DisplayPreferences
import cl.grupo28.hestia.data.LocalSettingsRepository
import cl.grupo28.hestia.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HestiaUiState(
    val ready: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val settings: LocalSettings = LocalSettings(),
    val error: String? = null,
    // No source of call/email events is connected in stage 1.1. Null means unavailable, not zero.
    val summary: WeeklySummary? = null,
)

class HestiaViewModel(private val repository: LocalSettingsRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(HestiaUiState())
    val state: StateFlow<HestiaUiState> = mutableState.asStateFlow()
    private var readJob: Job? = null
    init { reload() }

    fun reload() {
        readJob?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        readJob = viewModelScope.launch {
            try {
                repository.settings.collect { settings -> mutableState.update { it.copy(ready = true, loading = false, settings = settings) } }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(loading = false, error = "No pudimos leer tus ajustes. Inténtalo de nuevo.") } }
        }
    }

    fun advanceOnboarding() = save { repository.setOnboardingStep((state.value.settings.onboardingStep + 1).coerceAtMost(7)) }
    fun previousOnboarding() = save { repository.setOnboardingStep((state.value.settings.onboardingStep - 1).coerceAtLeast(0)) }
    fun addContact(name: String, phone: String, onSaved: () -> Unit) = save(onSaved) { repository.addContact(name, phone) }
    fun removeContact(id: String, onSaved: () -> Unit) = save(onSaved) { repository.removeContact(id) }
    fun completeLesson(id: Int, onSaved: () -> Unit) = save(onSaved) { repository.completeLesson(id) }
    fun setDisplay(display: DisplayPreferences) = save { repository.setDisplay(display) }
    fun clearError() { mutableState.update { it.copy(error = null) } }
    fun showError(message: String) { mutableState.update { it.copy(error = message) } }

    private fun save(onSaved: () -> Unit = {}, action: suspend () -> Unit) {
        if (state.value.saving) return
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { action(); onSaved() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (invalid: IllegalArgumentException) { showError(invalid.message ?: "Revisa los datos ingresados.") }
            catch (_: Exception) { showError("No pudimos guardar el cambio. Inténtalo de nuevo.") }
            finally { mutableState.update { it.copy(saving = false) } }
        }
    }

    class Factory(private val repository: LocalSettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HestiaViewModel::class.java))
            return HestiaViewModel(repository) as T
        }
    }
}
