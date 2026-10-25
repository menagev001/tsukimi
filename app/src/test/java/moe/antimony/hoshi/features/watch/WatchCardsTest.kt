package moe.antimony.hoshi.features.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun cardLinesFindTheTappedLine() {
        val cues = listOf(WatchSubtitleCue(0, 1_000, "一"), WatchSubtitleCue(1_000, 2_000, "二\n行"))
        assertEquals(WatchCardLines(1..1, 1_000, 2_000), watchCardLines(cues, "二行", 1_500))
        assertNull(watchCardLines(cues, "三", 0))
    }
}
