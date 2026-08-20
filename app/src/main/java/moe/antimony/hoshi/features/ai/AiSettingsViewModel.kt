package moe.antimony.hoshi.features.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
internal class AiSettingsViewModel @Inject constructor(
    private val repository: AiRepository,
) : ViewModel() {
    val settings: StateFlow<AiSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiSettings(),
    )

    fun updateBaseUrl(value: String) {
        viewModelScope.launch { repository.updateBaseUrl(value) }
    }

    fun updateModel(value: String) {
        viewModelScope.launch { repository.updateModel(value) }
    }

    fun updateApiKey(value: String) {
        viewModelScope.launch { repository.updateApiKey(value) }
    }
}
