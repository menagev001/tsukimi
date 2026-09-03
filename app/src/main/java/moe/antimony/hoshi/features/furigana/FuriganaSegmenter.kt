package moe.antimony.hoshi.features.furigana

import de.manhhao.hoshi.LookupResult
import moe.antimony.hoshi.dictionary.DictionaryRepository

/**
 * One dictionary-matched word (or, when nothing matched, one character of punctuation/kana
 * filler) from a greedy longest-match walk across a span of text.
 *
 * [expression]/[reading] are the *dictionary lemma*, not necessarily the exact on-page
 * conjugated form in [text] -- e.g. for on-page "食べた", [text] is "食べた" but [expression]/
 * [reading] are the dictionary entry "食べる"/"たべる". Turning that into correctly-placed
 * furigana over the conjugated surface text is a separate, not-yet-solved step; this class only
 * answers "what word is this, and is it known".
 */
data class FuriganaWordSpan(
    val text: String,
    val expression: String,
    val reading: String,
    val isKnownWord: Boolean,
)

fun interface FuriganaLookup {
    fun lookup(text: String, scanLength: Int): LookupResult?
}

/** Adapts the reader's existing tap-to-lookup engine into the narrow seam [FuriganaSegmenter] needs. */
internal fun DictionaryRepository.asFuriganaLookup(): FuriganaLookup =
    FuriganaLookup { text, scanLength ->
        lookup(text, maxResults = 1, scanLength = scanLength)
            .firstOrNull()
            ?.takeIf { it.matched.isNotEmpty() }
    }

/**
 * Greedily walks a span of text left to right, calling [lookup] at each position and advancing
 * by however many characters the longest dictionary match consumed -- the same "longest match
 * from here" primitive the reader's tap-to-lookup already uses, just called in a loop instead of
 * once. Characters where nothing matches (punctuation, whitespace) are emitted as their own
 * single-character, "known" (i.e. no-furigana-needed) span so callers can just concatenate.
 */
class FuriganaSegmenter(
    private val lookup: FuriganaLookup,
    private val isKnownWord: (expression: String, reading: String) -> Boolean,
    private val scanLength: Int = 16,
) {
    fun segment(text: String): List<FuriganaWordSpan> {
        val spans = mutableListOf<FuriganaWordSpan>()
        var offset = 0
        while (offset < text.length) {
            // Bounded to scanLength, not text.length: an unbounded substring() here reallocates
            // and copies the *entire remaining tail* on every step, making a single long text
            // node (a whole chapter with few paragraph breaks) cost O(length^2) instead of
            // O(length) -- fast on a short sample, catastrophic on a real multi-thousand-
            // character chapter.
            val window = text.substring(offset, minOf(text.length, offset + scanLength))
            val match = lookup.lookup(window, scanLength)
            if (match != null) {
                spans += FuriganaWordSpan(
                    text = match.matched,
                    expression = match.term.expression,
                    reading = match.term.reading,
                    isKnownWord = isKnownWord(match.term.expression, match.term.reading),
                )
                offset += match.matched.length
            } else {
                val codePoint = text.codePointAt(offset)
                val charCount = Character.charCount(codePoint)
                spans += FuriganaWordSpan(
                    text = text.substring(offset, offset + charCount),
                    expression = "",
                    reading = "",
                    isKnownWord = true,
                )
                offset += charCount
            }
        }
        return spans
    }
}
