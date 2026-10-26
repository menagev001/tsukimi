package moe.antimony.hoshi.features.manga

import java.io.File
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.features.anki.AnkiCardRequest
import moe.antimony.hoshi.features.anki.AnkiMiningContext
import moe.antimony.hoshi.features.anki.AnkiPendingCard
import moe.antimony.hoshi.features.anki.ankiCardLabel
import moe.antimony.hoshi.features.anki.ankiTitleTag

/** Card tags: tsukimi, tsukimi::manga and the volume title. */
internal fun mangaCardTags(title: String): Set<String> =
    setOfNotNull("tsukimi", "tsukimi::manga", ankiTitleTag(title))

/**
 * The card for the manga popup's Anki button. Inside the job the tapped speech bubble is cut out
 * of its page as `{screenshot}`; the image is deleted when the job ends.
 */
internal fun mangaPendingCard(
    formatId: String?,
    payloadJson: String,
    context: AnkiMiningContext,
    selectionText: String,
    title: String,
    pagesDirectory: File,
    screenshotDirectory: File,
    bubble: Deferred<MangaBubbleImage?>,
): AnkiPendingCard {
    var screenshot: File? = null
    return AnkiPendingCard(
        sentenceLength = context.sentence.length,
        build = { useAiExample ->
            AnkiCardRequest(
                label = ankiCardLabel(payloadJson, selectionText),
                formatId = formatId,
                payloadJson = payloadJson,
                context = context.copy(extraTags = context.extraTags + mangaCardTags(title)),
                prepareContext = { cardContext ->
                    screenshot = bubble.await()?.let { image ->
                        val pageFile = mangaPageFile(pagesDirectory, image.imgPath) ?: return@let null
                        withContext(Dispatchers.IO) { saveMangaBubbleScreenshot(pageFile, image, screenshotDirectory) }
                    }
                    cardContext.copy(screenshotPath = screenshot?.path)
                },
                cleanUp = { screenshot?.delete() },
                useAiExampleSentence = useAiExample,
            )
        },
        onCancel = { bubble.cancel() },
    )
}

/** The page image inside [pagesDirectory]; null when [imgPath] would leave it. */
internal fun mangaPageFile(pagesDirectory: File, imgPath: String): File? {
    val root = pagesDirectory.canonicalFile
    val file = File(root, imgPath).canonicalFile
    return file.takeIf { it.path.startsWith(root.path + File.separator) && it.isFile }
}
