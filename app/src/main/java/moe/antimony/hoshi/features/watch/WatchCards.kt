package moe.antimony.hoshi.features.watch

import java.util.Locale
import moe.antimony.hoshi.features.anki.ankiTitleTag

/** `{source}`: "Series S05E01 3:13" for episodes, "Film 1:02:03" otherwise. */
internal fun watchCardSource(item: WatchItem, videoTimeMs: Long): String {
    val episodeCode = if (item.kind == WatchItemKind.Episode && item.seasonNumber != null && item.episodeNumber != null) {
        String.format(Locale.ROOT, "S%02dE%02d", item.seasonNumber, item.episodeNumber)
    } else {
        null
    }
    val title = if (item.kind == WatchItemKind.Episode) item.seriesName ?: item.name else item.name
    return listOfNotNull(title.takeIf(String::isNotBlank), episodeCode, formatPlaybackTime(videoTimeMs)).joinToString(" ")
}

/** `{sentence-translation}`: the English lines that overlap the card's time range. */
internal fun watchCardTranslation(englishCues: List<WatchSubtitleCue>, startMs: Long, endMs: Long): String =
    englishCues
        .filter { it.startMs < endMs && it.endMs > startMs }
        .joinToString(" ") { it.text.replace("\n", " ") }

/** Card tags: tsukimi, tsukimi::watch and the series or film title (Anki tags cannot contain spaces). */
internal fun watchCardTags(item: WatchItem): Set<String> {
    val title = if (item.kind == WatchItemKind.Episode) item.seriesName ?: item.name else item.name
    return setOfNotNull("tsukimi", "tsukimi::watch", ankiTitleTag(title))
}

/**
 * The lines a card uses: the tapped line ([baseIndex]) plus any lines added before or after it
 * with "+ previous line" / "+ next line". [text] joins them without line breaks.
 */
data class WatchCardSelection(
    val baseIndex: Int,
    val range: IntRange,
    val text: String,
    val canAddPrevious: Boolean,
    val canAddNext: Boolean,
) {
    val isMerged: Boolean
        get() = range.first != baseIndex || range.last != baseIndex
}

internal fun watchCardSelection(cues: List<WatchSubtitleCue>, baseIndex: Int, range: IntRange): WatchCardSelection =
    WatchCardSelection(
        baseIndex = baseIndex,
        range = range,
        text = range.joinToString("") { cues[it].text.replace("\n", "") },
        canAddPrevious = range.first > 0,
        canAddNext = range.last < cues.lastIndex,
    )

/** One more line before ([previous]) or after the current selection, within the track. */
internal fun WatchCardSelection.extended(cues: List<WatchSubtitleCue>, previous: Boolean): WatchCardSelection {
    val next = if (previous) {
        (range.first - 1).coerceAtLeast(0)..range.last
    } else {
        range.first..(range.last + 1).coerceAtMost(cues.lastIndex)
    }
    return watchCardSelection(cues, baseIndex, next)
}

/** Characters the merged sentence has before the tapped line, to shift the word's offset. */
internal fun WatchCardSelection.offsetBeforeBase(cues: List<WatchSubtitleCue>): Int =
    (range.first until baseIndex).sumOf { cues[it].text.replace("\n", "").length }
