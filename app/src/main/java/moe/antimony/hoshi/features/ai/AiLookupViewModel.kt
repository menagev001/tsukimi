package moe.antimony.hoshi.features.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.antimony.hoshi.R
import moe.antimony.hoshi.ui.UiText

sealed interface AiLookupState {
    data object Hidden : AiLookupState
    data class Loading(val word: String?, val sentence: String) : AiLookupState
    data class Success(val word: String?, val sentence: String, val response: String) : AiLookupState
    data class Error(val word: String?, val sentence: String, val message: UiText) : AiLookupState
}

@HiltViewModel
internal class AiLookupViewModel @Inject constructor(
    private val repository: AiRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<AiLookupState>(AiLookupState.Hidden)
    val state: StateFlow<AiLookupState> = _state.asStateFlow()

    fun ask(word: String?, sentence: String) {
        _state.value = AiLookupState.Loading(word, sentence)
        viewModelScope.launch {
            val result = repository.ask(word, sentence)
            _state.value = result.fold(
                onSuccess = { response -> AiLookupState.Success(word, sentence, response) },
                onFailure = { error ->
                    AiLookupState.Error(
                        word,
                        sentence,
                        UiText.Resource(R.string.ai_lookup_error_format, error.message.orEmpty()),
                    )
                },
            )
        }
    }

    fun retry() {
        when (val current = _state.value) {
            is AiLookupState.Loading -> ask(current.word, current.sentence)
            is AiLookupState.Success -> ask(current.word, current.sentence)
            is AiLookupState.Error -> ask(current.word, current.sentence)
            AiLookupState.Hidden -> Unit
        }
    }

    fun dismiss() {
        _state.value = AiLookupState.Hidden
    }
}
