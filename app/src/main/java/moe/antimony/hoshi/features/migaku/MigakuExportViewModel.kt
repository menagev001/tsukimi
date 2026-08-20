package moe.antimony.hoshi.features.migaku

import android.content.ContentResolver
import android.net.Uri
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

sealed interface MigakuExportState {
    data object Idle : MigakuExportState
    data object Exporting : MigakuExportState
    data class Success(val count: Int) : MigakuExportState
    data class Error(val message: UiText) : MigakuExportState
}

@HiltViewModel
internal class MigakuExportViewModel @Inject constructor(
    private val repository: MigakuExportRepository,
    private val contentResolver: ContentResolver,
) : ViewModel() {
    private val _state = MutableStateFlow<MigakuExportState>(MigakuExportState.Idle)
    val state: StateFlow<MigakuExportState> = _state.asStateFlow()

    fun export(uri: Uri) {
        _state.value = MigakuExportState.Exporting
        viewModelScope.launch {
            _state.value = runCatching {
                contentResolver.openOutputStream(uri)?.use { output ->
                    repository.writeExport(output)
                } ?: error("Unable to open export destination.")
            }.fold(
                onSuccess = { count -> MigakuExportState.Success(count) },
                onFailure = { error ->
                    MigakuExportState.Error(
                        UiText.Resource(R.string.migaku_export_error_format, error.message.orEmpty()),
                    )
                },
            )
        }
    }
}
