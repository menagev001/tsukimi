package moe.antimony.hoshi.features.furigana

import de.manhhao.hoshi.LookupResult
import de.manhhao.hoshi.TermResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A tiny fake dictionary keyed by exact leading substrings, mimicking the real native lookup's
 * "longest match starting at position 0 of whatever text you hand it" contract closely enough to
 * exercise the segmenter's greedy walk without needing the real (Android-only) native library.
 */
private class FakeDictionary(private val entries: Map<String, Pair<String, String>>) : FuriganaLookup {
    override fun lookup(text: String, scanLength: Int): LookupResult? {
        val window = text.take(scanLength)
        val (matched, entry) = entries.entries
            .filter { window.startsWith(it.key) }
            .maxByOrNull { it.key.length }
            ?.let { it.key to it.value }
            ?: return null
        val (expression, reading) = entry
        return LookupResult(
            matched = matched,
            term = TermResult(
                expression = expression,
                reading = reading,
                rules = "",
                glossaries = emptyArray(),
                frequencies = emptyArray(),
                pitches = emptyArray(),
            ),
            traceCandidates = emptyArray(),
        )
    }
}

class FuriganaSegmenterTest {
    @Test
    fun walksThroughMultipleWordsAdvancingByMatchedLength() {
        val dictionary = FakeDictionary(
            mapOf(
                "私" to ("私" to "わたし"),
                "は" to ("は" to "は"),
                "食べた" to ("食べる" to "たべる"),
            ),
        )
        val segmenter = FuriganaSegmenter(dictionary, isKnownWord = { _, _ -> false })

        val spans = segmenter.segment("私は食べた。")

        assertEquals(
            listOf("私", "は", "食べた", "。"),
            spans.map { it.text },
        )
        assertEquals("わたし", spans[0].reading)
        assertEquals("食べる", spans[2].expression)
        assertEquals("たべる", spans[2].reading)
    }

    @Test
    fun unmatchedCharactersAdvanceOneAtATimeAndAreTreatedAsNoFuriganaNeeded() {
        val dictionary = FakeDictionary(emptyMap())
        val segmenter = FuriganaSegmenter(dictionary, isKnownWord = { _, _ -> false })

        val spans = segmenter.segment("ab")

        assertEquals(listOf("a", "b"), spans.map { it.text })
        assertEquals(listOf(true, true), spans.map { it.isKnownWord })
        assertEquals(listOf("", ""), spans.map { it.expression })
    }

    @Test
    fun isKnownWordCallbackDrivesTheFlagPerMatchedWord() {
        val dictionary = FakeDictionary(
            mapOf(
                "食べる" to ("食べる" to "たべる"),
                "飲む" to ("飲む" to "のむ"),
            ),
        )
        val segmenter = FuriganaSegmenter(
            dictionary,
            isKnownWord = { expression, _ -> expression == "食べる" },
        )

        val spans = segmenter.segment("食べる飲む")

        assertEquals(true, spans[0].isKnownWord)
        assertEquals(false, spans[1].isKnownWord)
    }
}
