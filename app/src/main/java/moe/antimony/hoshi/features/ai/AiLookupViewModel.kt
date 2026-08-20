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

sealed interface AiLookupRequest {
    val sentence: String

    data class WordNuance(val word: String, override val sentence: String) : AiLookupRequest
    data class SentenceBreakdown(override val sentence: String) : AiLookupRequest
}

sealed interface AiLookupState {
    data object Hidden : AiLookupState
    data class Confirm(val request: AiLookupRequest.SentenceBreakdown) : AiLookupState
    data class Loading(val request: AiLookupRequest) : AiLookupState
    data class Success(val request: AiLookupRequest, val response: String) : AiLookupState
    data class Error(val request: AiLookupRequest, val message: UiText) : AiLookupState
}

@HiltViewModel
internal class AiLookupViewModel @Inject constructor(
    private val repository: AiRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<AiLookupState>(AiLookupState.Hidden)
    val state: StateFlow<AiLookupState> = _state.asStateFlow()

    fun askWordNuance(word: String, sentence: String) {
        ask(AiLookupRequest.WordNuance(word, sentence))
    }

    fun requestSentenceBreakdown(sentence: String) {
        _state.value = AiLookupState.Confirm(AiLookupRequest.SentenceBreakdown(sentence))
    }

    fun confirmSentenceBreakdown() {
        val current = _state.value
        if (current is AiLookupState.Confirm) {
            ask(current.request)
        }
    }

    fun retry() {
        when (val current = _state.value) {
            is AiLookupState.Loading -> ask(current.request)
            is AiLookupState.Success -> ask(current.request)
            is AiLookupState.Error -> ask(current.request)
            is AiLookupState.Confirm, AiLookupState.Hidden -> Unit
        }
    }

    fun dismiss() {
        _state.value = AiLookupState.Hidden
    }

    private fun ask(request: AiLookupRequest) {
        _state.value = AiLookupState.Loading(request)
        viewModelScope.launch {
            val result = when (request) {
                is AiLookupRequest.WordNuance -> repository.askWordNuance(request.word, request.sentence)
                is AiLookupRequest.SentenceBreakdown -> repository.askSentenceBreakdown(request.sentence)
            }
            _state.value = result.fold(
                onSuccess = { response -> AiLookupState.Success(request, response) },
                onFailure = { error ->
                    AiLookupState.Error(
                        request,
                        UiText.Resource(R.string.ai_lookup_error_format, error.message.orEmpty()),
                    )
                },
            )
        }
    }
}
