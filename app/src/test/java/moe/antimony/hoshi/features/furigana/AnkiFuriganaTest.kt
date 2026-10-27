package moe.antimony.hoshi.features.furigana

import org.junit.Assert.assertEquals
import org.junit.Test

class AnkiFuriganaTest {
    private val words = mapOf(
        "パン" to FuriganaWordSpan("パン", "パン", "ぱん", isKnownWord = false),
        "食べた" to FuriganaWordSpan("食べた", "食べる", "たべる", isKnownWord = false),
        "毎朝" to FuriganaWordSpan("毎朝", "毎朝", "まいあさ", isKnownWord = false),
    )

    /** Longest known word first, else one character, like the dictionary segmenter. */
    private fun segment(text: String): List<FuriganaWordSpan> {
        val spans = mutableListOf<FuriganaWordSpan>()
        var rest = text
        while (rest.isNotEmpty()) {
            val word = words.keys.filter(rest::startsWith).maxByOrNull { it.length }
            val span = word?.let(words::getValue) ?: FuriganaWordSpan(rest.take(1), "", "", isKnownWord = true)
            spans += span
            rest = rest.drop(span.text.length)
        }
        return spans
    }

    @Test
    fun addsReadingsToKanjiWordsAndKeepsTheTargetBold() {
        assertEquals(
            " 毎朝[まいあさ]パンを<b> 食[た]べた</b>。",
            ankiFuriganaHtml("毎朝パンを<b>食べた</b>。", ::segment),
        )
    }

    @Test
    fun leavesTextWithoutKanjiAlone() {
        assertEquals("パンを<br>どうぞ", ankiFuriganaHtml("パンを<br>どうぞ", ::segment))
    }
}
