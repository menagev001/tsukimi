package moe.antimony.hoshi.features.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSubtitlesTest {
    @Test
    fun parsesCuesWithCrlfBomAndFormatting() {
        val srt = "﻿1\r\n00:00:01,000 --> 00:00:02,500\r\n<i>おはよう</i>\r\n\r\n" +
            "2\r\n00:00:03,000 --> 00:00:05,000\r\n{\\an8}今日は\r\nいい天気だね\r\n\r\n"

        assertEquals(
            listOf(
                WatchSubtitleCue(1_000, 2_500, "おはよう"),
                WatchSubtitleCue(3_000, 5_000, "今日は\nいい天気だね"),
            ),
            parseSrt(srt),
        )
    }

    @Test
    fun parsesHoursDotMillisAndMissingBlankLine() {
        val srt = "1\n01:02:03.4 --> 01:02:04.500\nA\n2\n01:02:05,000 --> 01:02:06,000\nB\n"

        assertEquals(
            listOf(
                WatchSubtitleCue(3_723_400, 3_724_500, "A"),
                WatchSubtitleCue(3_725_000, 3_726_000, "B"),
            ),
            parseSrt(srt),
        )
    }

    @Test
    fun dropsEmptyCuesAndSortsByStart() {
        val srt = "1\n00:00:05,000 --> 00:00:06,000\nlater\n\n2\n00:00:01,000 --> 00:00:02,000\n<b></b>\n\n" +
            "3\n00:00:03,000 --> 00:00:04,000\nearlier\n"

        assertEquals(listOf("earlier", "later"), parseSrt(srt).map { it.text })
    }

    @Test
    fun activeCuesIncludeOverlaps() {
        val cues = listOf(
            WatchSubtitleCue(0, 1_000, "a"),
            WatchSubtitleCue(2_000, 6_000, "b"),
            WatchSubtitleCue(3_000, 4_000, "c"),
            WatchSubtitleCue(7_000, 8_000, "d"),
        )

        assertEquals(emptyList<Int>(), activeCueIndices(cues, 1_500))
        assertEquals(listOf(0), activeCueIndices(cues, 0))
        assertEquals(listOf(1, 2), activeCueIndices(cues, 3_500))
        assertEquals(listOf(1), activeCueIndices(cues, 5_000))
        assertEquals(emptyList<Int>(), activeCueIndices(cues, 6_000))
        assertEquals(emptyList<Int>(), activeCueIndices(emptyList(), 0))
    }

    @Test
    fun previousAndNextLineTargets() {
        val cues = listOf(
            WatchSubtitleCue(1_000, 2_000, "a"),
            WatchSubtitleCue(5_000, 6_000, "b"),
            WatchSubtitleCue(9_000, 10_000, "c"),
        )

        assertEquals(1_000L, previousCueStartMs(cues, 5_500))
        assertEquals(5_000L, previousCueStartMs(cues, 7_000))
        assertEquals(1_000L, previousCueStartMs(cues, 1_200))
        assertNull(previousCueStartMs(cues, 500))
        assertEquals(9_000L, nextCueStartMs(cues, 5_000))
        assertEquals(5_000L, nextCueStartMs(cues, 1_000))
        assertNull(nextCueStartMs(cues, 9_500))
    }

    @Test
    fun detectsJapaneseByKanaNotKanji() {
        assertTrue(looksJapanese(listOf(WatchSubtitleCue(0, 1, "今日はいい天気だね"))))
        assertFalse(looksJapanese(listOf(WatchSubtitleCue(0, 1, "今天天气很好"))))
        assertFalse(looksJapanese(listOf(WatchSubtitleCue(0, 1, "Good morning"))))
        assertFalse(looksJapanese(emptyList()))
    }

    @Test
    fun trackLanguageTags() {
        assertTrue(WatchSubtitleTrack(1, "", "jpn").isJapanese())
        assertTrue(WatchSubtitleTrack(1, "", "JA").isJapanese())
        assertTrue(WatchSubtitleTrack(1, "", "eng").isEnglish())
        assertTrue(WatchSubtitleTrack(1, "", null).isUntagged())
        assertTrue(WatchSubtitleTrack(1, "", "und").isUntagged())
        assertFalse(WatchSubtitleTrack(1, "", "jpn").isUntagged())
    }
}
