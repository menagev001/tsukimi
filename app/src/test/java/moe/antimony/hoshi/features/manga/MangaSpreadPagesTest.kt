package moe.antimony.hoshi.features.manga

import org.junit.Assert.assertEquals
import org.junit.Test

class MangaSpreadPagesTest {
    @Test
    fun emptyVolumeHasNoGroups() {
        assertEquals(emptyList<List<Int>>(), mangaSpreadGroups(0))
    }

    @Test
    fun singlePageVolumeIsOneSingletonGroup() {
        assertEquals(listOf(listOf(0)), mangaSpreadGroups(1))
    }

    @Test
    fun firstPageIsAlwaysASingletonEvenWhenMoreFollow() {
        val groups = mangaSpreadGroups(5)
        assertEquals(listOf(0), groups.first())
    }

    @Test
    fun pagesAfterTheFirstPairUpTwoAtATime() {
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3, 4)),
            mangaSpreadGroups(5),
        )
    }

    @Test
    fun trailingSinglePageAfterTheLeadingSingletonStaysAlone() {
        // 4 pages: [0] alone, [1,2] paired, then page 3 has no partner left.
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3)),
            mangaSpreadGroups(4),
        )
    }

    @Test
    fun twoPageVolumeIsTwoSingletons() {
        // Page 0 alone (leading singleton), page 1 has no partner left either.
        assertEquals(
            listOf(listOf(0), listOf(1)),
            mangaSpreadGroups(2),
        )
    }

    @Test
    fun groupIndexForPageFindsTheContainingGroup() {
        val groups = mangaSpreadGroups(5)

        assertEquals(0, mangaSpreadGroupIndexForPage(groups, 0))
        assertEquals(1, mangaSpreadGroupIndexForPage(groups, 1))
        assertEquals(1, mangaSpreadGroupIndexForPage(groups, 2))
        assertEquals(2, mangaSpreadGroupIndexForPage(groups, 3))
        assertEquals(2, mangaSpreadGroupIndexForPage(groups, 4))
    }

    @Test
    fun groupIndexForPageFallsBackToFirstGroupWhenPageIsOutOfRange() {
        val groups = mangaSpreadGroups(3)

        assertEquals(0, mangaSpreadGroupIndexForPage(groups, 99))
    }

    @Test
    fun singlePageModeIsExpressedAsEveryPageBeingItsOwnGroup() {
        // How MangaReaderWebView represents portrait/single-page mode: not a separate code path,
        // just every page already singleton.
        val singlePageGroups = (0 until 4).map { listOf(it) }

        assertEquals(0, mangaSpreadGroupIndexForPage(singlePageGroups, 0))
        assertEquals(2, mangaSpreadGroupIndexForPage(singlePageGroups, 2))
    }
}
