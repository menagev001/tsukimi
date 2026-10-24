package moe.antimony.hoshi.features.watch

import moe.antimony.hoshi.features.reader.ReaderSelectionData
import moe.antimony.hoshi.features.reader.ReaderSelectionRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchSubtitlesTest {
    @Test
    fun parsesCuesWithCrlfBomAndFormatting() {
        val srt = "\uFEFF1\r\n00:00:01,000 --> 00:00:02,500\r\n<i>おはよう</i>\r\n\r\n" +
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
    fun decodesEntitiesAndRemovesDirectionMarks() {
        // Line from a streaming-service rip: literal &lrm; plus U+202A/U+202C embeddings.
        val srt = "63\n00:03:09,294 --> 00:03:13,465\n" +
            "\u202Aヘッ！&lrm;\u3000&lrm;お前らよりは\u202C\n" +
            "\u202Aスケベなこと考えねえよ&lrm;\u3000&lrm;ケケッ\u202C\n"

        assertEquals(
            "ヘッ！\u3000お前らよりは\nスケベなこと考えねえよ\u3000ケケッ",
            parseSrt(srt).single().text,
        )
    }

    @Test
    fun decodesNamedAndNumericEntities() {
        assertEquals("<a & b> \"c\" あい", decodeSubtitleEntities("&lt;a &amp; b&gt; &quot;c&quot; &#12354;&#x3044;"))
        assertEquals("&unknown; &", decodeSubtitleEntities("&unknown; &"))
        assertEquals("Tom & Jerry", cleanSubtitleLine("<i>Tom &amp; Jerry</i>\u200E"))
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
    fun lookupSentenceIsTheWholeLineWithoutLineBreaks() {
        val selection = ReaderSelectionData(
            text = "スケベ",
            sentence = "お前らよりは\nスケベなこと考えねえよ",
            rect = ReaderSelectionRect(0.0, 0.0, 1.0, 1.0),
            normalizedOffset = null,
            sentenceOffset = 7,
        )
        val lines = listOf("別の行", "ヘッ！　お前らよりは\nスケベなこと考えねえよ　ケケッ")

        val result = withSubtitleLineSentence(selection, lines)

        assertEquals("ヘッ！　お前らよりはスケベなこと考えねえよ　ケケッ", result.sentence)
        assertEquals("スケベ", result.sentence.substring(result.sentenceOffset!!, result.sentenceOffset!! + 3))
    }

    @Test
    fun lookupSentenceStaysWhenNoLineMatches() {
        val selection = ReaderSelectionData(
            text = "a",
            sentence = "not shown",
            rect = ReaderSelectionRect(0.0, 0.0, 1.0, 1.0),
            normalizedOffset = null,
        )
        assertEquals(selection, withSubtitleLineSentence(selection, listOf("other")))
    }

    @Test
    fun aiContextTakesTwoLinesEachSideOfTheShownLine() {
        val cues = listOf(
            WatchSubtitleCue(0, 1_000, "一"),
            WatchSubtitleCue(1_000, 2_000, "二"),
            WatchSubtitleCue(2_000, 3_000, "同じ"),
            WatchSubtitleCue(3_000, 4_000, "四\n行"),
            WatchSubtitleCue(4_000, 5_000, "五"),
            WatchSubtitleCue(5_000, 6_000, "六"),
            WatchSubtitleCue(9_000, 10_000, "同じ"),
        )

        val context = subtitleAiContext(cues, "同じ", positionMs = 2_500)!!
        assertEquals(listOf("一", "二"), context.before)
        assertEquals(listOf("四行", "五"), context.after)

        val later = subtitleAiContext(cues, "同じ", positionMs = 9_500)!!
        assertEquals(listOf("五", "六"), later.before)
        assertEquals(emptyList<String>(), later.after)

        assertNull(subtitleAiContext(cues, "ない", positionMs = 0))
    }

    @Test
    fun tapResultTokens() {
        assertEquals(WatchTapResult.Nothing, WatchTapResult.fromWebViewResult(null))
        assertEquals(WatchTapResult.Nothing, WatchTapResult.fromWebViewResult("null"))
        assertEquals(WatchTapResult.Nothing, WatchTapResult.fromWebViewResult("\"image\""))
        assertEquals(WatchTapResult.List, WatchTapResult.fromWebViewResult("\"list\""))
        assertEquals(WatchTapResult.SeekToCue(12), WatchTapResult.fromWebViewResult("\"seek:12\""))
        assertEquals(WatchTapResult.Selected, WatchTapResult.fromWebViewResult("\"言葉\""))
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
