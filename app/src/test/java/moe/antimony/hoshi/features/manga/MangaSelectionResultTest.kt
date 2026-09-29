package moe.antimony.hoshi.features.manga

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `nearText` and `blockToggled` are the cases this type exists for. `reader-manga.js`'s
 * `selectTextAt` returns them for taps that were on or beside OCR text but produced no new
 * selection: a second tap on an already-open word, or a tap that only revealed or hid an OCR block.
 * Collapsing those into "tapped blank artwork" (as the shared `ReaderSelectionResult` necessarily
 * does) would snap the page out of zoom or turn it.
 */
class MangaSelectionResultTest {

    @Test
    fun `a selected word is neither blank nor near-text`() {
        val result = MangaSelectionResult.fromWebViewResult("\"猫\"")

        assertFalse(result.selectedNothing)
        assertFalse(result.nearText)
        assertFalse(result.blockToggled)
        assertFalse(result.isImageTap)
        assertFalse(result.isLinkTap)
    }

    @Test
    fun `null means a tap that was genuinely nowhere near text`() {
        val result = MangaSelectionResult.fromWebViewResult("null")

        assertTrue(result.selectedNothing)
        assertFalse(result.nearText)
    }

    @Test
    fun `a missing or undefined evaluation result is treated as a blank tap`() {
        assertTrue(MangaSelectionResult.fromWebViewResult(null).selectedNothing)
        assertTrue(MangaSelectionResult.fromWebViewResult("undefined").selectedNothing)
        assertTrue(MangaSelectionResult.fromWebViewResult("  null  ").selectedNothing)
    }

    @Test
    fun `near-text is reported without being reported as a blank tap`() {
        val result = MangaSelectionResult.fromWebViewResult("\"${MangaSelectionResult.NEAR_TEXT_TOKEN}\"")

        assertTrue(result.nearText)
        assertFalse(result.selectedNothing)
    }

    @Test
    fun `revealing or hiding a block is reported without being reported as a blank tap`() {
        val result = MangaSelectionResult.fromWebViewResult("\"${MangaSelectionResult.BLOCK_TOGGLED_TOKEN}\"")

        assertTrue(result.blockToggled)
        assertFalse(result.nearText)
        assertFalse(result.selectedNothing)
    }

    @Test
    fun `image and link taps still pass through`() {
        assertTrue(MangaSelectionResult.fromWebViewResult("\"image\"").isImageTap)
        assertTrue(MangaSelectionResult.fromWebViewResult("\"link\"").isLinkTap)
        assertFalse(MangaSelectionResult.fromWebViewResult("\"image\"").selectedNothing)
    }
}
