package moe.antimony.hoshi.features.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchCardsTest {
    private val episode = WatchItem(
        id = "1",
        name = "黄金の風",
        kind = WatchItemKind.Episode,
        seriesName = "ジョジョの奇妙な冒険",
        seasonNumber = 5,
        episodeNumber = 1,
    )

    @Test
    fun sourceNamesSeriesEpisodeAndTime() {
        assertEquals("ジョジョの奇妙な冒険 S05E01 3:09", watchCardSource(episode, 189_294))
        assertEquals(
            "Film 1:02:03",
            watchCardSource(WatchItem(id = "2", name = "Film", kind = WatchItemKind.Movie), 3_723_000),
        )
    }

    @Test
    fun translationJoinsOverlappingEnglishLines() {
        val english = listOf(
            WatchSubtitleCue(0, 1_000, "Before."),
            WatchSubtitleCue(900, 2_100, "Hey!\nYou guys"),
            WatchSubtitleCue(2_000, 3_000, "are perverts."),
            WatchSubtitleCue(3_000, 4_000, "After."),
        )
        assertEquals("Hey! You guys are perverts.", watchCardTranslation(english, 1_000, 3_000))
        assertEquals("", watchCardTranslation(emptyList(), 1_000, 3_000))
    }

    @Test
    fun tagsUseTitleWithoutSpaces() {
        assertEquals(setOf("tsukimi", "tsukimi::watch", "ジョジョの奇妙な冒険"), watchCardTags(episode))
        assertEquals(
            setOf("tsukimi", "tsukimi::watch", "Spirited_Away"),
            watchCardTags(WatchItem(id = "2", name = "Spirited  Away", kind = WatchItemKind.Movie)),
        )
    }

    private val lines = listOf(
        WatchSubtitleCue(0, 1_000, "今日は"),
        WatchSubtitleCue(1_000, 2_000, "いい天気\nだから"),
        WatchSubtitleCue(2_000, 3_000, "散歩に行こう"),
    )

    @Test
    fun selectionStartsAtTheTappedLine() {
        val selection = watchCardSelection(lines, 1, 1..1)
        assertEquals("いい天気だから", selection.text)
        assertFalse(selection.isMerged)
        assertTrue(selection.canAddPrevious)
        assertTrue(selection.canAddNext)
    }

    @Test
    fun addingLinesMergesTextAndShiftsTheWordOffset() {
        val merged = watchCardSelection(lines, 1, 1..1)
            .extended(lines, previous = true)
            .extended(lines, previous = false)

        assertEquals(0..2, merged.range)
        assertEquals("今日はいい天気だから散歩に行こう", merged.text)
        assertTrue(merged.isMerged)
        assertFalse(merged.canAddPrevious)
        assertFalse(merged.canAddNext)
        assertEquals(3, merged.offsetBeforeBase(lines))
        assertEquals(merged, merged.extended(lines, previous = true).extended(lines, previous = false))
    }
}
