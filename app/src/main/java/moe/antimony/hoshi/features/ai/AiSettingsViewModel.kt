package moe.antimony.hoshi.features.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _defaultWordNuancePrompt = MutableStateFlow("")
    val defaultWordNuancePrompt: StateFlow<String> = _defaultWordNuancePrompt.asStateFlow()

    private val _defaultSentenceBreakdownPrompt = MutableStateFlow("")
    val defaultSentenceBreakdownPrompt: StateFlow<String> = _defaultSentenceBreakdownPrompt.asStateFlow()

    private val _defaultExampleSentencePrompt = MutableStateFlow("")
    val defaultExampleSentencePrompt: StateFlow<String> = _defaultExampleSentencePrompt.asStateFlow()

    init {
        viewModelScope.launch { _defaultWordNuancePrompt.value = repository.defaultWordNuancePrompt() }
        viewModelScope.launch { _defaultSentenceBreakdownPrompt.value = repository.defaultSentenceBreakdownPrompt() }
        viewModelScope.launch { _defaultExampleSentencePrompt.value = repository.defaultExampleSentencePrompt() }
    }

    fun updateBaseUrl(value: String) {
        viewModelScope.launch { repository.updateBaseUrl(value) }
    }

    fun updateModel(value: String) {
        viewModelScope.launch { repository.updateModel(value) }
    }

    fun updateApiKey(value: String) {
        viewModelScope.launch { repository.updateApiKey(value) }
    }

    fun updateWordNuancePromptOverride(value: String) {
        viewModelScope.launch { repository.updateWordNuancePromptOverride(value) }
    }

    fun updateSentenceBreakdownPromptOverride(value: String) {
        viewModelScope.launch { repository.updateSentenceBreakdownPromptOverride(value) }
    }

    fun updateExampleSentencePromptOverride(value: String) {
        viewModelScope.launch { repository.updateExampleSentencePromptOverride(value) }
    }

    fun updateLongSentenceLimit(value: Int) {
        viewModelScope.launch { repository.updateLongSentenceLimit(value.coerceIn(LongSentenceLimitRange)) }
    }
}

internal val LongSentenceLimitRange = 10..200
