package moe.antimony.hoshi.features.furigana

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The last Migaku import on this screen: its summary, or a failure. */
sealed interface KnownWordsImportState {
    data object None : KnownWordsImportState

    data object Importing : KnownWordsImportState

    data class Imported(val summary: KnownWordsImportSummary) : KnownWordsImportState

    data object Failed : KnownWordsImportState
}

data class KnownWordsUiState(
    val counts: KnownWordsCounts? = null,
    val settings: KnownWordsSettings = KnownWordsSettings(),
    val syncStatus: KnownWordsAnkiSyncStatus = KnownWordsAnkiSyncStatus.Idle,
    val importState: KnownWordsImportState = KnownWordsImportState.None,
)

@HiltViewModel
internal class KnownWordsSettingsViewModel @Inject constructor(
    private val knownWordsRepository: KnownWordsRepository,
    private val settingsRepository: KnownWordsSettingsRepository,
    private val ankiSync: KnownWordsAnkiSync,
) : ViewModel() {
    private val counts = MutableStateFlow<KnownWordsCounts?>(null)
    private val importState = MutableStateFlow<KnownWordsImportState>(KnownWordsImportState.None)

    val uiState: StateFlow<KnownWordsUiState> = combine(
        counts,
        settingsRepository.settings,
        ankiSync.status,
        importState,
    ) { counts, settings, syncStatus, importState ->
        KnownWordsUiState(counts, settings, syncStatus, importState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KnownWordsUiState())

    init {
        refreshCounts()
    }

    fun updateAnkiSyncEnabled(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAnkiSyncEnabled(value)
            if (value) syncNow() else knownWordsRepository.replaceAnkiWords(emptyList())
            refreshCounts()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            ankiSync.sync()
            refreshCounts()
        }
    }

    /** [text] is the picked file's content; null when it could not be read. */
    fun importMigaku(text: String?) {
        viewModelScope.launch {
            importState.value = KnownWordsImportState.Importing
            val summary = text?.let { runCatching { knownWordsRepository.importMigakuExport(it) }.getOrNull() }
            importState.value = if (summary == null || summary.exportedKnownCount == 0) {
                KnownWordsImportState.Failed
            } else {
                KnownWordsImportState.Imported(summary)
            }
            refreshCounts()
        }
    }

    private fun refreshCounts() {
        viewModelScope.launch { counts.value = knownWordsRepository.counts() }
    }
}
