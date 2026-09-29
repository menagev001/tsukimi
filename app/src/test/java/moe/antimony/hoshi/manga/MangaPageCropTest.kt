package moe.antimony.hoshi.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaPageCropTest {
    @Test
    fun contentMarginsMeasureTheBlankBorderAroundArt() {
        val page = Page(width = 200, height = 300).fill(x = 20, y = 15, width = 150, height = 260)

        val margins = requireNotNull(mangaPageContentMargins(page.luma, page.width, page.height))

        assertEquals(20f / 200, margins.left, 1e-6f)
        assertEquals(30f / 200, margins.right, 1e-6f)
        assertEquals(15f / 300, margins.top, 1e-6f)
        assertEquals(25f / 300, margins.bottom, 1e-6f)
    }

    @Test
    fun contentMarginsIgnorePageNumbersAndSpecksInTheMargin() {
        val page = Page(width = 200, height = 300)
            .fill(x = 20, y = 30, width = 160, height = 240)
            // A page number: small, isolated, in the bottom margin.
            .fill(x = 95, y = 285, width = 8, height = 4)
            // A scan speck near the top-left corner.
            .fill(x = 3, y = 4, width = 1, height = 1)

        val margins = requireNotNull(mangaPageContentMargins(page.luma, page.width, page.height))

        assertEquals(20f / 200, margins.left, 1e-6f)
        assertEquals(30f / 300, margins.top, 1e-6f)
        assertEquals(30f / 300, margins.bottom, 1e-6f)
    }

    @Test
    fun contentMarginsTreatLightGreyPaperAsBlank() {
        val page = Page(width = 100, height = 100, paper = 200).fill(x = 10, y = 10, width = 80, height = 80)

        val margins = requireNotNull(mangaPageContentMargins(page.luma, page.width, page.height))

        assertEquals(0.1f, margins.left, 1e-6f)
    }

    @Test
    fun contentMarginsAreNullForABlankPage() {
        val page = Page(width = 100, height = 100)

        assertNull(mangaPageContentMargins(page.luma, page.width, page.height))
    }

    @Test
    fun volumeAutoCropUsesPerParityMediansMinusSafetyMargin() {
        val even = MangaPageCrop(left = 0.08f, top = 0.05f, right = 0.10f, bottom = 0.07f)
        val odd = MangaPageCrop(left = 0.10f, top = 0.05f, right = 0.08f, bottom = 0.07f)
        val fullBleed = MangaPageCrop.None
        val margins = mapOf(
            2 to even, 4 to even, 6 to fullBleed,
            3 to odd, 5 to odd, 7 to odd,
        )

        val auto = mangaVolumeAutoCrop(margins)

        assertEquals(0.075f, auto.even.left, 1e-6f)
        assertEquals(0.095f, auto.even.right, 1e-6f)
        assertEquals(0.095f, auto.odd.left, 1e-6f)
        assertEquals(0.075f, auto.odd.right, 1e-6f)
    }

    @Test
    fun volumeAutoCropMirrorsTheOtherParityWhenOneHasTooFewPages() {
        val even = MangaPageCrop(left = 0.05f, top = 0.05f, right = 0.10f, bottom = 0.05f)

        val auto = mangaVolumeAutoCrop(mapOf(2 to even, 4 to even, 6 to even, 3 to even))

        assertEquals(auto.even.left, auto.odd.right, 1e-6f)
        assertEquals(auto.even.right, auto.odd.left, 1e-6f)
    }

    @Test
    fun volumeAutoCropNeverExceedsTheMaximumEdge() {
        val huge = MangaPageCrop(left = 0.4f, top = 0.4f, right = 0.4f, bottom = 0.4f)

        val auto = mangaVolumeAutoCrop((1..6).associateWith { huge })

        assertEquals(MANGA_AUTO_CROP_MAX_EDGE, auto.even.left, 1e-6f)
        assertEquals(MANGA_AUTO_CROP_MAX_EDGE, auto.odd.bottom, 1e-6f)
    }

    @Test
    fun volumeAutoCropIsNoCropWithoutMeasurements() {
        val auto = mangaVolumeAutoCrop(emptyMap())

        assertEquals(MangaPageCrop.None, auto.even)
        assertEquals(MangaPageCrop.None, auto.odd)
    }

    @Test
    fun widePagesUseTheSmallestSideAndVerticalCrop() {
        val auto = MangaVolumeAutoCrop(
            even = MangaPageCrop(left = 0.08f, top = 0.05f, right = 0.10f, bottom = 0.07f),
            odd = MangaPageCrop(left = 0.10f, top = 0.04f, right = 0.06f, bottom = 0.08f),
        )

        assertEquals(auto.even, auto.forPage(4, isWide = false))
        assertEquals(auto.odd, auto.forPage(5, isWide = false))
        assertEquals(
            MangaPageCrop(left = 0.06f, top = 0.04f, right = 0.06f, bottom = 0.07f),
            auto.forPage(4, isWide = true),
        )
    }

    private class Page(val width: Int, val height: Int, paper: Int = 255) {
        val luma = IntArray(width * height) { paper }

        fun fill(x: Int, y: Int, width: Int, height: Int): Page = apply {
            for (row in y until y + height) {
                for (column in x until x + width) luma[row * this.width + column] = 0
            }
        }
    }
}
