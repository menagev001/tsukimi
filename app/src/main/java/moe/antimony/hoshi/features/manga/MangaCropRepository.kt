package moe.antimony.hoshi.features.manga

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import moe.antimony.hoshi.manga.MangaRepository
import moe.antimony.hoshi.manga.MangaVolumeAutoCrop
import moe.antimony.hoshi.manga.MokuroVolume

/** Reader-facing crop boundary: the per-series choice plus the per-volume detected margins. */
internal interface MangaCropRepository {
    fun setting(seriesKey: String): Flow<MangaCropSetting>
    suspend fun updateSetting(seriesKey: String, transform: (MangaCropSetting) -> MangaCropSetting)
    suspend fun autoCrop(volumeRoot: File, volume: MokuroVolume): MangaVolumeAutoCrop
}

@Singleton
internal class AndroidMangaCropRepository @Inject constructor(
    private val settingsRepository: MangaCropSettingsRepository,
    private val mangaRepository: MangaRepository,
) : MangaCropRepository {
    override fun setting(seriesKey: String): Flow<MangaCropSetting> = settingsRepository.setting(seriesKey)

    override suspend fun updateSetting(seriesKey: String, transform: (MangaCropSetting) -> MangaCropSetting) {
        settingsRepository.update(seriesKey, transform)
    }

    override suspend fun autoCrop(volumeRoot: File, volume: MokuroVolume): MangaVolumeAutoCrop =
        mangaRepository.loadOrDetectAutoCrop(volumeRoot, volume)
}
