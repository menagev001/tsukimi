package moe.antimony.hoshi.features.reader

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.features.anki.AnkiCardRequest
import moe.antimony.hoshi.features.anki.AnkiCardSource
import moe.antimony.hoshi.features.anki.AnkiMiningContext
import moe.antimony.hoshi.features.anki.AnkiPendingCard
import moe.antimony.hoshi.features.anki.ankiCardLabel
import moe.antimony.hoshi.features.anki.ankiTitleTag

/** Card tags: tsukimi, tsukimi::book and the book title. */
internal fun bookCardTags(title: String): Set<String> =
    setOfNotNull("tsukimi", "tsukimi::book", ankiTitleTag(title))

/**
 * The card for the book reader popup's Anki button. [exportSasayakiAudio] cuts the audiobook cue
 * for `{sasayaki-audio}`; it runs inside the job, and not at all when an AI example sentence
 * replaces the sentence, since the cue would not match it.
 */
internal fun bookPendingCard(
    formatId: String?,
    payloadJson: String,
    context: AnkiMiningContext,
    selectionText: String,
    title: String,
    exportSasayakiAudio: (() -> File?)?,
): AnkiPendingCard = AnkiPendingCard(
    sentenceLength = context.sentence.length,
    build = { useAiExample ->
        val export = exportSasayakiAudio?.takeUnless { useAiExample }
        AnkiCardRequest(
            label = ankiCardLabel(payloadJson, selectionText),
            formatId = formatId,
            payloadJson = payloadJson,
            context = context.copy(
                extraTags = context.extraTags + bookCardTags(title),
                cardSource = AnkiCardSource.Book,
            ),
            prepareContext = export?.let {
                { cardContext ->
                    cardContext.copy(sasayakiAudioPath = withContext(Dispatchers.IO) { export() }?.absolutePath)
                }
            },
            useAiExampleSentence = useAiExample,
        )
    },
)
