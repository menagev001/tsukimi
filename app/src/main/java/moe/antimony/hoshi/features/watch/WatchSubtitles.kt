package moe.antimony.hoshi.features.watch

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
            .map { it.replace(AssOverrideRegex, "").replace(MarkupTagRegex, "").replace("\\N", "\n").trim() }
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
