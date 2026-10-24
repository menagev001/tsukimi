package moe.antimony.hoshi.features.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.antimony.hoshi.ui.UiText

data class WatchSettingsUiState(
    val settings: JellyfinSettings = JellyfinSettings(),
    val preferences: WatchPreferences = WatchPreferences(),
    val isLoaded: Boolean = false,
    val isBusy: Boolean = false,
    val errorMessage: UiText? = null,
)

private data class WatchSettingsTransientState(
    val isBusy: Boolean = false,
    val errorMessage: UiText? = null,
)

@HiltViewModel
internal class WatchSettingsViewModel @Inject constructor(
    private val repository: JellyfinRepository,
    private val preferencesRepository: WatchPreferencesRepository,
) : ViewModel() {
    private val transientState = MutableStateFlow(WatchSettingsTransientState())

    val uiState: StateFlow<WatchSettingsUiState> =
        combine(repository.settings, preferencesRepository.preferences, transientState) { settings, preferences, transient ->
            WatchSettingsUiState(
                settings = settings,
                preferences = preferences,
                isLoaded = true,
                isBusy = transient.isBusy,
                errorMessage = transient.errorMessage,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WatchSettingsUiState(),
        )

    fun login(serverUrl: String, username: String, password: String) {
        if (transientState.value.isBusy) return
        transientState.value = WatchSettingsTransientState(isBusy = true)
        viewModelScope.launch {
            val result = repository.login(serverUrl, username, password)
            transientState.value = WatchSettingsTransientState(
                errorMessage = (result as? JellyfinLoginResult.Failure)
                    ?.let { UiText.Resource(it.failure.messageRes) },
            )
        }
    }

    fun logout() {
        if (transientState.value.isBusy) return
        transientState.value = WatchSettingsTransientState(isBusy = true)
        viewModelScope.launch {
            repository.logout()
            transientState.update { WatchSettingsTransientState() }
        }
    }

    fun updateMeteredQuality(value: WatchStreamingQuality) {
        viewModelScope.launch { preferencesRepository.updateMeteredQuality(value) }
    }

    fun updateAudioLanguage(value: WatchAudioLanguage) {
        viewModelScope.launch { preferencesRepository.updateAudioLanguage(value) }
    }

    fun clearError() {
        transientState.update { it.copy(errorMessage = null) }
    }
}
