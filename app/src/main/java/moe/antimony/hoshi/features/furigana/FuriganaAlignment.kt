package moe.antimony.hoshi.features.furigana

internal sealed interface FuriganaPart {
    val text: String

    data class Ruby(val kanjiText: String, val reading: String) : FuriganaPart {
        override val text: String get() = kanjiText
    }

    data class Plain(override val text: String) : FuriganaPart
}

/** A maximal run of same-kind (kanji or kana) characters within a dictionary spelling. */
internal class LemmaSegment(val text: String, val isKanji: Boolean)

internal fun String.toLemmaSegments(): List<LemmaSegment> {
    val segments = mutableListOf<LemmaSegment>()
    var start = 0
    while (start < length) {
        val kanji = isKanji(this[start])
        var end = start + 1
        while (end < length && isKanji(this[end]) == kanji) end++
        segments += LemmaSegment(substring(start, end), kanji)
        start = end
    }
    return segments
}

/**
 * Breaks [expression] into a list of (segment, reading-for-that-segment) pairs, or null if
 * [reading] can't be aligned against it.
 *
 * A dictionary spelling like 振り向く isn't one kanji block -- it alternates 振(kanji) / り(kana)
 * / 向(kanji) / く(kana), each kanji block having its own reading. Kana characters are pronounced
 * exactly as written, so they act as anchors: whatever appears between one anchor and the next in
 * [reading] must be that kanji block's reading. Walking both strings together this way -- find the
 * next kana anchor's literal text in reading, the kanji block before it gets everything up to that
 * point -- recovers the correct per-kanji reading (振->ふ, 向->む) instead of only handling a
 * single leading kanji block.
 *
 * Returns null if a kana anchor can't be found where expected (some irregular reading that doesn't
 * literally reuse its own written kana) -- furigana just doesn't apply here rather than guessing
 * at an alignment that can't be verified.
 */
internal fun alignLemma(expression: String, reading: String): List<Pair<LemmaSegment, String>>? {
    val segments = expression.toLemmaSegments()
    val aligned = mutableListOf<Pair<LemmaSegment, String>>()
    var readingIndex = 0
    for ((index, segment) in segments.withIndex()) {
        if (!segment.isKanji) {
            if (!reading.startsWith(segment.text, readingIndex)) return null
            aligned += segment to segment.text
            readingIndex += segment.text.length
            continue
        }
        val nextAnchor = segments.getOrNull(index + 1)?.takeIf { !it.isKanji }
        val end = if (nextAnchor == null) {
            reading.length
        } else {
            reading.indexOf(nextAnchor.text, readingIndex).takeIf { it >= 0 } ?: return null
        }
        if (end <= readingIndex) return null
        aligned += segment to reading.substring(readingIndex, end)
        readingIndex = end
    }
    return aligned.takeIf { readingIndex == reading.length }
}

/**
 * Breaks a span into the `<ruby>`/plain-text parts it should render as, or null if it shouldn't
 * get furigana at all.
 *
 * [FuriganaWordSpan.reading] is the *dictionary lemma's* reading (e.g. for matched="鳴らしている",
 * expression="鳴らす", reading="ならす" -- the lemma's own reading, not "ならしている"), so it
 * can't be compared against [FuriganaWordSpan.text]'s tail directly -- [alignLemma] instead pairs
 * each of [FuriganaWordSpan.expression]'s kanji blocks with its own reading, using expression's
 * own kana characters as anchors. Regular conjugation only ever changes what follows the *last*
 * kanji block, never anything before it, so everything up through that block is treated as a
 * stable prefix and reused as-is against the real on-page [FuriganaWordSpan.text]; only the
 * remainder after it is left unclassified, as plain text, whatever it actually says
 * (う/した/している/わず/...). [FuriganaWordSpan.text] must literally start with that stable
 * prefix, or this returns null -- for a handful of irregular verbs (来る/する) the kanji's own
 * reading changes across conjugated forms (来る=くる vs 来た=きた), which can't be detected this
 * way since 来 is literally still 来 either way; a known, accepted gap rather than something worth
 * a hardcoded irregular-verb table right now.
 */
internal fun FuriganaWordSpan.furiganaParts(): List<FuriganaPart>? {
    if (isKnownWord || expression.isEmpty() || reading.isEmpty()) return null
    val aligned = alignLemma(expression, reading) ?: return null
    val lastKanjiIndex = aligned.indexOfLast { it.first.isKanji }
    if (lastKanjiIndex < 0) return null
    val stablePrefix = aligned.take(lastKanjiIndex + 1).joinToString("") { it.first.text }
    if (!text.startsWith(stablePrefix)) return null
    val parts = aligned.take(lastKanjiIndex + 1).map { (segment, segReading) ->
        if (segment.isKanji) FuriganaPart.Ruby(segment.text, segReading) else FuriganaPart.Plain(segment.text)
    }
    val remainder = text.substring(stablePrefix.length)
    return if (remainder.isEmpty()) parts else parts + FuriganaPart.Plain(remainder)
}

internal fun isKanji(char: Char): Boolean = char in '一'..'鿿'
