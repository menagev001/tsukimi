package moe.antimony.hoshi.features.watch

import moe.antimony.hoshi.features.reader.ReaderSelectionData

/** One subtitle line with its display time. [text] may contain line breaks. */
data class WatchSubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/** A text subtitle stream of the playing media source. */
data class WatchSubtitleTrack(
    val index: Int,
    val label: String,
    val language: String?,
)

private val SrtTimingRegex = Regex(
    """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""",
)
private val MarkupTagRegex = Regex("""</?[a-zA-Z][^>]*>""")
private val AssOverrideRegex = Regex("""\{\\[^}]*\}""")
private val EntityRegex = Regex("""&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});""")
private val NamedEntities = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
    "lrm" to "",
    "rlm" to "",
)

// Direction marks and embeddings (common in subtitles ripped from streaming services),
// zero-width spaces and BOMs: invisible, but they would end up in lookups and cards.
private val InvisibleControlRegex = Regex("[\u200B\u200E\u200F\u202A-\u202E\u2066-\u2069\uFEFF]")

/** Decodes HTML character references such as `&lrm;` or `&#12354;`; unknown ones stay as written. */
internal fun decodeSubtitleEntities(text: String): String =
    EntityRegex.replace(text) { match ->
        val name = match.groupValues[1]
        when {
            name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let(::codePointString)
            name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePointString)
            else -> NamedEntities[name.lowercase()]
        } ?: match.value
    }

private fun codePointString(codePoint: Int): String? =
    if (Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else null

/** One subtitle text line as displayed: no markup, entities decoded, invisible controls removed. */
internal fun cleanSubtitleLine(line: String): String =
    decodeSubtitleEntities(line.replace(AssOverrideRegex, "").replace(MarkupTagRegex, ""))
        .replace(InvisibleControlRegex, "")
        .trim()

/**
 * Parses SubRip text as served by Jellyfin's subtitle stream endpoint. Formatting tags are
 * removed because Tsukimi draws plain text; cues without text are dropped.
 */
internal fun parseSrt(source: String): List<WatchSubtitleCue> {
    val lines = source.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val cues = mutableListOf<WatchSubtitleCue>()
    var index = 0
    while (index < lines.size) {
        val timing = SrtTimingRegex.find(lines[index])
        if (timing == null) {
            index++
            continue
        }
        val (h1, m1, s1, ms1, h2, m2, s2, ms2) = timing.destructured
        val textLines = mutableListOf<String>()
        index++
        while (index < lines.size && lines[index].isNotBlank()) {
            // A missing blank line before the next cue would otherwise swallow its timing line.
            if (SrtTimingRegex.containsMatchIn(lines[index])) break
            textLines += lines[index]
            index++
        }
        // Drop a trailing cue number that belongs to the next cue.
        if (textLines.size > 1 && textLines.last().trim().all(Char::isDigit) && index < lines.size &&
            SrtTimingRegex.containsMatchIn(lines[index])
        ) {
            textLines.removeAt(textLines.lastIndex)
        }
        val text = textLines
            .flatMap { it.split("\\N") }
            .map(::cleanSubtitleLine)
            .filter(String::isNotEmpty)
            .joinToString("\n")
        if (text.isNotEmpty()) {
            cues += WatchSubtitleCue(
                startMs = srtTimeMs(h1, m1, s1, ms1),
                endMs = srtTimeMs(h2, m2, s2, ms2),
                text = text,
            )
        }
    }
    return cues.sortedBy { it.startMs }
}

private fun srtTimeMs(hours: String, minutes: String, seconds: String, millis: String): Long =
    hours.toLong() * 3_600_000 + minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + millis.padEnd(3, '0').toLong()

/**
 * Indices of the cues shown at [positionMs], in start order. Several cues can overlap, for
 * example two speakers at once. [cues] must be sorted by start time.
 */
internal fun activeCueIndices(cues: List<WatchSubtitleCue>, positionMs: Long): List<Int> {
    // Last cue that has started.
    var low = 0
    var high = cues.lastIndex
    var lastStarted = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (cues[mid].startMs <= positionMs) {
            lastStarted = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    if (lastStarted < 0) return emptyList()
    val active = mutableListOf<Int>()
    var index = lastStarted
    while (index >= 0 && lastStarted - index < MaxOverlapLookBack) {
        if (cues[index].endMs > positionMs) active += index
        index--
    }
    return active.reversed()
}

private const val MaxOverlapLookBack = 8

/** The cue to jump to for "previous line": the current line's start if well into it, else the line before. */
internal fun previousCueStartMs(cues: List<WatchSubtitleCue>, positionMs: Long): Long? {
    val current = cues.indexOfLast { it.startMs <= positionMs }
    if (current < 0) return null
    val cue = cues[current]
    return if (positionMs - cue.startMs > RestartLineThresholdMs || current == 0) cue.startMs else cues[current - 1].startMs
}

internal fun nextCueStartMs(cues: List<WatchSubtitleCue>, positionMs: Long): Long? =
    cues.firstOrNull { it.startMs > positionMs + NextLineToleranceMs }?.startMs

private const val RestartLineThresholdMs = 1_500L
private const val NextLineToleranceMs = 50L

private val JapaneseCodes = setOf("jpn", "ja", "jp", "japanese")
private val EnglishCodes = setOf("eng", "en", "english")

internal fun WatchSubtitleTrack.isJapanese(): Boolean = language?.lowercase() in JapaneseCodes
internal fun WatchSubtitleTrack.isEnglish(): Boolean = language?.lowercase() in EnglishCodes
internal fun WatchSubtitleTrack.isUntagged(): Boolean =
    language.isNullOrBlank() || language.lowercase() in setOf("und", "unknown", "mis", "zxx")

/** True when enough of the cue text is kana that the track is Japanese, not just Kanji (Chinese). */
internal fun looksJapanese(cues: List<WatchSubtitleCue>): Boolean {
    val sample = cues.asSequence().take(KanaSampleCues).joinToString("") { it.text }
    if (sample.isEmpty()) return false
    val kana = sample.count { it in '぀'..'ヿ' }
    val letters = sample.count { it.isLetter() }
    return letters > 0 && kana.toDouble() / letters >= MinKanaShare
}

private const val KanaSampleCues = 60
private const val MinKanaShare = 0.2

/**
 * Uses the whole subtitle line that contains the selection as its sentence, as Watch mode looks
 * words up per line rather than per sentence. Display line breaks are removed and the word's
 * offset into the sentence follows the change, so cards still mark the right word.
 */
internal fun withSubtitleLineSentence(selection: ReaderSelectionData, lines: List<String>): ReaderSelectionData {
    val line = lines.firstOrNull { selection.sentence.isNotEmpty() && it.contains(selection.sentence) } ?: return selection
    val sentenceStart = line.indexOf(selection.sentence)
    val offsetInLine = selection.sentenceOffset?.let { (sentenceStart + it).coerceIn(0, line.length) }
    return selection.copy(
        sentence = line.replace("\n", ""),
        sentenceOffset = offsetInLine?.let { offset -> offset - line.substring(0, offset).count { it == '\n' } },
    )
}
