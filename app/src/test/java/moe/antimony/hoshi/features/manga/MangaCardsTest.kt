package moe.antimony.hoshi.features.manga

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaCardsTest {
    @Test
    fun cropShowsTheSceneAroundTheBubble() {
        // Page 1000x1500: at least 600x750, centred on the bubble (centre 200,400), inside the page.
        val bubble = MangaBubbleImage("p1.jpg", listOf(100.0, 200.0, 300.0, 600.0), imgWidth = 1000, imgHeight = 1500)
        assertEquals(MangaCropRect(0, 25, 600, 775), mangaBubbleCropRect(bubble))

        // Near the bottom right corner the crop moves inwards instead of shrinking.
        val corner = MangaBubbleImage("p1.jpg", listOf(900.0, 1400.0, 990.0, 1490.0), imgWidth = 1000, imgHeight = 1500)
        assertEquals(MangaCropRect(400, 750, 1000, 1500), mangaBubbleCropRect(corner))

        // A bubble larger than the minimum keeps its margin, limited to the page.
        val big = MangaBubbleImage("p1.jpg", listOf(100.0, 100.0, 900.0, 1100.0), imgWidth = 1000, imgHeight = 1500)
        assertEquals(MangaCropRect(0, 0, 1000, 1500), mangaBubbleCropRect(big))

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
