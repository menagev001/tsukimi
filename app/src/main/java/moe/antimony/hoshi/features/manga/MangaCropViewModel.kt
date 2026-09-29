package moe.antimony.hoshi.features.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.antimony.hoshi.manga.MangaPageCrop
import moe.antimony.hoshi.manga.MangaVolumeAutoCrop
import moe.antimony.hoshi.manga.MokuroVolume

internal enum class MangaCropEdge {
    Top,
    Bottom,
    Left,
    Right,
}

internal data class MangaCropUiState(
    val mode: MangaCropMode = MangaCropMode.Auto,
    // Always resolved (never null) so the panel can show numbers even before Manual is chosen.
    val manual: MangaPageCrop = MangaPageCrop.None,
    // Null until detection for the open volume has finished.
    val auto: MangaVolumeAutoCrop? = null,
) {
    val detecting: Boolean get() = mode == MangaCropMode.Auto && auto == null

    fun cropForPage(pageIndex: Int, isWide: Boolean): MangaPageCrop = when (mode) {
        MangaCropMode.Off -> MangaPageCrop.None
        MangaCropMode.Auto -> auto?.forPage(pageIndex, isWide) ?: MangaPageCrop.None
        MangaCropMode.Manual -> manual
    }

    fun percent(edge: MangaCropEdge): Int = (manual.edge(edge) * 100).roundToInt()
}

@HiltViewModel
internal class MangaCropViewModel private constructor(
    private val repository: MangaCropRepository,
    private val injectedScope: CoroutineScope?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) : ViewModel() {
    @Inject
    constructor(repository: MangaCropRepository) : this(repository, injectedScope = null, marker = Unit)

    internal constructor(repository: MangaCropRepository, coroutineScope: CoroutineScope) :
        this(repository, injectedScope = coroutineScope, marker = Unit)

    private val workScope: CoroutineScope
        get() = injectedScope ?: viewModelScope

    private data class Binding(val volumeRoot: File, val seriesKey: String)

    private val binding = MutableStateFlow<Binding?>(null)
    private val autoCrop = MutableStateFlow<MangaVolumeAutoCrop?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MangaCropUiState> = binding
        .filterNotNull()
        .flatMapLatest { repository.setting(it.seriesKey) }
        .combine(autoCrop) { setting, auto ->
            MangaCropUiState(
                mode = setting.mode,
                manual = setting.manual ?: auto.manualStart(),
                auto = auto,
            )
        }
        .stateIn(workScope, SharingStarted.Eagerly, MangaCropUiState())

    /** Idempotent per volume; the reader calls this whenever it (re)composes for a volume. */
    fun bind(volumeRoot: File, seriesKey: String, volume: MokuroVolume) {
        val next = Binding(volumeRoot, seriesKey)
        if (binding.value == next) return
        binding.value = next
        autoCrop.value = null
        workScope.launch {
            val detected = runCatching { repository.autoCrop(volumeRoot, volume) }.getOrNull()
            // A volume that can't be measured simply isn't cropped by Auto.
            if (binding.value == next) autoCrop.value = detected ?: MangaVolumeAutoCrop()
        }
    }

    fun setMode(mode: MangaCropMode) = updateSetting { setting ->
        // Entering Manual for the first time starts from what Auto detected, so the reader
        // nudges a sensible crop instead of building one up from zero.
        setting.copy(mode = mode, manual = setting.manual ?: autoCrop.value.manualStart())
    }

    fun adjust(edge: MangaCropEdge, steps: Int) = updateSetting { setting ->
        val current = setting.manual ?: autoCrop.value.manualStart()
        val next = (current.edge(edge) + steps * MANUAL_STEP).coerceIn(0f, MANUAL_MAX_EDGE)
        setting.copy(mode = MangaCropMode.Manual, manual = current.withEdge(edge, next.roundToStep()))
    }

    fun resetManualToDetected() = updateSetting { setting ->
        setting.copy(mode = MangaCropMode.Manual, manual = autoCrop.value.manualStart())
    }

    private fun updateSetting(transform: (MangaCropSetting) -> MangaCropSetting) {
        val seriesKey = binding.value?.seriesKey ?: return
        workScope.launch { repository.updateSetting(seriesKey, transform) }
    }

    internal companion object {
        const val MANUAL_STEP = 0.01f
        const val MANUAL_MAX_EDGE = 0.3f
    }
}

// Whole percent steps, rounded *down* from the detection so a fresh manual crop never hides more
// than Auto did.
private fun MangaVolumeAutoCrop?.manualStart(): MangaPageCrop {
    val uniform = this?.uniformCrop() ?: return MangaPageCrop.None
    fun down(value: Float) = floor(value * 100f + 1e-3f) / 100f
    return MangaPageCrop(down(uniform.left), down(uniform.top), down(uniform.right), down(uniform.bottom))
}

private fun Float.roundToStep(): Float = (this * 100f).roundToInt() / 100f

private fun MangaPageCrop.edge(edge: MangaCropEdge): Float = when (edge) {
    MangaCropEdge.Top -> top
    MangaCropEdge.Bottom -> bottom
    MangaCropEdge.Left -> left
    MangaCropEdge.Right -> right
}

private fun MangaPageCrop.withEdge(edge: MangaCropEdge, value: Float): MangaPageCrop = when (edge) {
    MangaCropEdge.Top -> copy(top = value)
    MangaCropEdge.Bottom -> copy(bottom = value)
    MangaCropEdge.Left -> copy(left = value)
    MangaCropEdge.Right -> copy(right = value)
}
