package moe.antimony.hoshi.features.manga

import java.io.File
import moe.antimony.hoshi.manga.MangaVolumeEntry
import moe.antimony.hoshi.manga.MangaVolumeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaSeriesGroupingTest {
    @Test
    fun singleVolumeSeriesRendersUngrouped() {
        val volume = volumeEntry(id = "vol-a", title = "20th Century Boys", seriesTitle = "20th Century Boys")

        val groups = groupMangaVolumesBySeries(listOf(volume))

        assertEquals(1, groups.size)
        assertNull(groups.single().seriesKey)
        assertEquals(listOf(volume), groups.single().volumes)
    }

    @Test
    fun multipleVolumesOfTheSameSeriesGroupTogether() {
        val volumeOne = volumeEntry(id = "vol-1", title = "20th Century Boys (Volume 01)", seriesTitle = "20th Century Boys")
        val volumeTwo = volumeEntry(id = "vol-2", title = "20th Century Boys (Volume 02)", seriesTitle = "20th Century Boys")

        val groups = groupMangaVolumesBySeries(listOf(volumeOne, volumeTwo))

        assertEquals(1, groups.size)
        assertEquals("20th Century Boys", groups.single().seriesKey)
        assertEquals(listOf(volumeOne, volumeTwo), groups.single().volumes)
    }

    @Test
    fun volumesWithoutASeriesTitleNeverGroupWithEachOtherEvenIfTitlesMatch() {
        // Volumes imported before seriesTitle existed (or genuine unrelated one-shots) must not
        // accidentally merge just because their display titles happen to be identical.
        val volumeOne = volumeEntry(id = "vol-1", title = "Untitled", seriesTitle = null)
        val volumeTwo = volumeEntry(id = "vol-2", title = "Untitled", seriesTitle = null)

        val groups = groupMangaVolumesBySeries(listOf(volumeOne, volumeTwo))

        assertEquals(2, groups.size)
        assertEquals(0, groups.count { it.seriesKey != null })
    }

    @Test
    fun groupOrderingFollowsEachGroupsFirstMemberInInputOrder() {
        val seriesAVolume1 = volumeEntry(id = "a-1", title = "Series A (1)", seriesTitle = "Series A")
        val seriesBVolume1 = volumeEntry(id = "b-1", title = "Series B (1)", seriesTitle = "Series B")
        val seriesAVolume2 = volumeEntry(id = "a-2", title = "Series A (2)", seriesTitle = "Series A")

        val groups = groupMangaVolumesBySeries(listOf(seriesAVolume1, seriesBVolume1, seriesAVolume2))

        assertEquals(listOf("Series A", null), groups.map { it.seriesKey })
        // Series B never gained a second volume, so it stays a singleton.
        assertEquals(listOf(seriesAVolume1, seriesAVolume2), groups[0].volumes)
        assertEquals(listOf(seriesBVolume1), groups[1].volumes)
    }

    private fun volumeEntry(id: String, title: String, seriesTitle: String?): MangaVolumeEntry =
        MangaVolumeEntry(
            root = File(id),
            metadata = MangaVolumeMetadata(
                id = id,
                title = title,
                cover = null,
                folder = id,
                lastAccess = 0.0,
                seriesTitle = seriesTitle,
            ),
        )
}
