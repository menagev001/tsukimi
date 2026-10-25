package moe.antimony.hoshi.features.watch

import java.util.Locale

/** The subtitle lines a card is made from, as an index range into the Japanese cues. */
internal data class WatchCardLines(
    val cueRange: IntRange,
    val startMs: Long,
    val endMs: Long,
)

/** The cue whose text is [sentence], see [cueIndexForSentence]. */
internal fun watchCardLines(cues: List<WatchSubtitleCue>, sentence: String, positionMs: Long): WatchCardLines? {
    val index = cueIndexForSentence(cues, sentence, positionMs) ?: return null
    return WatchCardLines(index..index, cues[index].startMs, cues[index].endMs)
}

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
    val titleTag = title.trim().replace(Regex("\\s+"), "_")
    return setOfNotNull("tsukimi", "tsukimi::watch", titleTag.takeIf(String::isNotEmpty))
}
