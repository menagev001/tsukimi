package moe.antimony.hoshi.features.manga

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import moe.antimony.hoshi.manga.MangaPageCrop
import moe.antimony.hoshi.manga.MangaVolumeAutoCrop
import moe.antimony.hoshi.manga.MokuroVolume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaCropViewModelTest {
    private val detected = MangaVolumeAutoCrop(
        even = MangaPageCrop(left = 0.078f, top = 0.063f, right = 0.096f, bottom = 0.069f),
        odd = MangaPageCrop(left = 0.092f, top = 0.063f, right = 0.078f, bottom = 0.069f),
    )

    @Test
    fun defaultsToAutoAndCropsEachParityAsDetected() {
        val viewModel = boundViewModel(FakeMangaCropRepository(detected))
        val state = viewModel.uiState.value

        assertEquals(MangaCropMode.Auto, state.mode)
        assertFalse(state.detecting)
        assertEquals(detected.even, state.cropForPage(10, isWide = false))
        assertEquals(detected.odd, state.cropForPage(11, isWide = false))
    }

    @Test
    fun offShowsPagesUncropped() {
        val viewModel = boundViewModel(FakeMangaCropRepository(detected))

        viewModel.setMode(MangaCropMode.Off)

        assertEquals(MangaPageCrop.None, viewModel.uiState.value.cropForPage(10, isWide = false))
    }

    @Test
    fun manualStartsFromDetectionRoundedDownToWholePercents() {
        val viewModel = boundViewModel(FakeMangaCropRepository(detected))

        viewModel.setMode(MangaCropMode.Manual)
        val state = viewModel.uiState.value

        assertEquals(MangaCropMode.Manual, state.mode)
        assertEquals(7, state.percent(MangaCropEdge.Left))
        assertEquals(6, state.percent(MangaCropEdge.Top))
        assertEquals(7, state.percent(MangaCropEdge.Right))
        assertEquals(6, state.percent(MangaCropEdge.Bottom))
        // Manual is one uniform crop for every page.
        assertEquals(state.cropForPage(10, isWide = false), state.cropForPage(11, isWide = false))
    }

    @Test
    fun adjustStepsOnePercentAndStaysInRange() {
        val viewModel = boundViewModel(FakeMangaCropRepository(detected))
        viewModel.setMode(MangaCropMode.Manual)

        viewModel.adjust(MangaCropEdge.Top, 1)
        assertEquals(7, viewModel.uiState.value.percent(MangaCropEdge.Top))

        repeat(50) { viewModel.adjust(MangaCropEdge.Top, -1) }
        assertEquals(0, viewModel.uiState.value.percent(MangaCropEdge.Top))

        repeat(50) { viewModel.adjust(MangaCropEdge.Top, 1) }
        assertEquals(30, viewModel.uiState.value.percent(MangaCropEdge.Top))
    }

    @Test
    fun manualValuesSurviveSwitchingModesAndCanBeResetToDetected() {
        val viewModel = boundViewModel(FakeMangaCropRepository(detected))
        viewModel.setMode(MangaCropMode.Manual)
        viewModel.adjust(MangaCropEdge.Left, 5)

        viewModel.setMode(MangaCropMode.Auto)
        viewModel.setMode(MangaCropMode.Manual)
        assertEquals(12, viewModel.uiState.value.percent(MangaCropEdge.Left))

        viewModel.resetManualToDetected()
        assertEquals(7, viewModel.uiState.value.percent(MangaCropEdge.Left))
    }

    @Test
    fun settingIsSharedByVolumesOfTheSameSeries() {
        val repository = FakeMangaCropRepository(detected)
        boundViewModel(repository, volumeRoot = "volume-1").setMode(MangaCropMode.Off)

        val secondVolume = boundViewModel(repository, volumeRoot = "volume-2")

        assertEquals(MangaCropMode.Off, secondVolume.uiState.value.mode)
    }

    @Test
    fun reportsDetectingUntilTheVolumeHasBeenMeasured() {
        val repository = FakeMangaCropRepository(detected, detectImmediately = false)
        val viewModel = boundViewModel(repository)

        assertTrue(viewModel.uiState.value.detecting)
        assertEquals(MangaPageCrop.None, viewModel.uiState.value.cropForPage(10, isWide = false))
    }

    private fun boundViewModel(
        repository: FakeMangaCropRepository,
        volumeRoot: String = "volume-1",
    ): MangaCropViewModel = MangaCropViewModel(repository, CoroutineScope(Dispatchers.Unconfined)).apply {
        bind(File(volumeRoot), seriesKey = "Adolf ni Tsugu", volume = MokuroVolume(pages = emptyList()))
    }

    private class FakeMangaCropRepository(
        private val detected: MangaVolumeAutoCrop,
        private val detectImmediately: Boolean = true,
    ) : MangaCropRepository {
        private val settings = MutableStateFlow<Map<String, MangaCropSetting>>(emptyMap())

        override fun setting(seriesKey: String): Flow<MangaCropSetting> =
            settings.map { it[seriesKey] ?: MangaCropSetting() }

        override suspend fun updateSetting(seriesKey: String, transform: (MangaCropSetting) -> MangaCropSetting) {
            settings.value = settings.value + (seriesKey to transform(settings.value[seriesKey] ?: MangaCropSetting()))
        }

        override suspend fun autoCrop(volumeRoot: File, volume: MokuroVolume): MangaVolumeAutoCrop {
            if (!detectImmediately) kotlinx.coroutines.awaitCancellation()
            return detected
        }
    }
}
