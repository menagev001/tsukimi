package moe.antimony.hoshi.features.reader

import java.io.File
import kotlinx.coroutines.runBlocking
import moe.antimony.hoshi.features.anki.AnkiMiningContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderCardsTest {
    private val context = AnkiMiningContext(sentence = "パンを食べる。", documentTitle = "本")

    @Test
    fun bookCardsAreTaggedAndCutTheAudiobookCue() = runBlocking {
        var exports = 0
        val card = bookPendingCard(
            formatId = "f",
            payloadJson = """{"expression":"食べる"}""",
            context = context,
            selectionText = "食べる。",
            title = "吾輩は 猫である",
            exportSasayakiAudio = { exports++; File("/cache/cue.aac") },
        )

        assertEquals(7, card.sentenceLength)
        val request = card.build(false)
        assertEquals("食べる", request.label)
        assertTrue(request.context.extraTags.containsAll(setOf("tsukimi", "tsukimi::book", "吾輩は_猫である")))
        assertEquals("/cache/cue.aac", request.prepareContext!!(request.context).sasayakiAudioPath)
        assertEquals(1, exports)
    }

    @Test
    fun anAiExampleSentenceSkipsTheCue() {
        val card = bookPendingCard("f", "{}", context, "x", "本", exportSasayakiAudio = { error("not expected") })

        val request = card.build(true)
        assertTrue(request.useAiExampleSentence)
        assertNull(request.prepareContext)
    }
}
