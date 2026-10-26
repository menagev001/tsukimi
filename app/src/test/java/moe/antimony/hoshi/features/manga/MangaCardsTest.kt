package moe.antimony.hoshi.features.manga

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaCardsTest {
    @Test
    fun cropAddsMarginAroundTheBubbleInsideThePage() {
        val bubble = MangaBubbleImage("p1.jpg", listOf(100.0, 200.0, 300.0, 600.0), imgWidth = 1000, imgHeight = 1500)
        // 30 % of the longer side (400 px) is a 120 px margin.
        assertEquals(MangaCropRect(0, 80, 420, 720), mangaBubbleCropRect(bubble))

        val corner = MangaBubbleImage("p1.jpg", listOf(900.0, 1400.0, 990.0, 1490.0), imgWidth = 1000, imgHeight = 1500)
        // A small bubble still gets 48 px, clamped to the page edges.
        assertEquals(MangaCropRect(852, 1352, 1000, 1500), mangaBubbleCropRect(corner))

        assertNull(mangaBubbleCropRect(bubble.copy(box = listOf(10.0, 10.0, 10.0, 50.0))))
    }

    @Test
    fun readsTheActiveBlockFromTheWebViewResult() {
        val result = "\"{\\\"imgPath\\\":\\\"vol/p1.jpg\\\",\\\"box\\\":[1,2,3,4],\\\"imgWidth\\\":10,\\\"imgHeight\\\":20}\""

        assertEquals(
            MangaBubbleImage("vol/p1.jpg", listOf(1.0, 2.0, 3.0, 4.0), 10, 20),
            mangaBubbleImageFromWebViewResult(result),
        )
        assertNull(mangaBubbleImageFromWebViewResult("null"))
        assertNull(mangaBubbleImageFromWebViewResult(null))
        assertNull(mangaBubbleImageFromWebViewResult("\"not json\""))
    }

    @Test
    fun pageFilesStayInsideThePagesDirectory() {
        val pages = Files.createTempDirectory("pages").toFile()
        val page = File(pages, "vol/p1.jpg").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1)) }
        File(pages.parentFile, "secret.txt").writeText("x")

        assertEquals(page.canonicalFile, mangaPageFile(pages, "vol/p1.jpg"))
        assertNull(mangaPageFile(pages, "../secret.txt"))
        assertNull(mangaPageFile(pages, "vol/missing.jpg"))
    }

    @Test
    fun tagsNameTheVolume() {
        assertEquals(setOf("tsukimi", "tsukimi::manga", "ダンジョン飯_1"), mangaCardTags("ダンジョン飯 1"))
    }
}
