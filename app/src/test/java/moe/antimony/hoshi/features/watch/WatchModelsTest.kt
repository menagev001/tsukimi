package moe.antimony.hoshi.features.watch

import java.util.UUID
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchModelsTest {
    private val images = WatchImageUrlBuilder { itemId, imageType, tag, maxWidth ->
        "img/$itemId/${imageType.serialName}/$tag/$maxWidth"
    }
    private val itemId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val seriesId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test
    fun episodeMapsNumbersProgressAndStill() {
        val item = BaseItemDto(
            id = itemId,
            type = BaseItemKind.EPISODE,
            name = "Pilot",
            seriesId = seriesId,
            seriesName = "Show",
            parentIndexNumber = 1,
            indexNumber = 3,
            runTimeTicks = 24 * 60 * 1000 * 10_000L,
            imageTags = mapOf(ImageType.PRIMARY to "still"),
            seriesPrimaryImageTag = "poster",
            userData = userData(positionTicks = 12 * 60 * 1000 * 10_000L),
        ).toWatchItem(images)

        assertEquals(WatchItemKind.Episode, item.kind)
        assertEquals(1, item.seasonNumber)
        assertEquals(3, item.episodeNumber)
        assertEquals(24 * 60 * 1000L, item.runTimeMs)
        assertEquals(0.5f, item.progress!!, 0.001f)
        assertEquals("img/$itemId/Primary/still/$ThumbMaxWidth", item.thumbUrl)
        assertEquals("img/$itemId/Primary/still/$PosterMaxWidth", item.posterUrl)
        assertTrue(item.isPlayable)
    }

    @Test
    fun episodeWithoutStillFallsBackToSeriesImages() {
        val item = BaseItemDto(
            id = itemId,
            type = BaseItemKind.EPISODE,
            seriesId = seriesId,
            seriesPrimaryImageTag = "poster",
            parentThumbItemId = seriesId,
            parentThumbImageTag = "thumb",
        ).toWatchItem(images)

        assertEquals("img/$seriesId/Thumb/thumb/$ThumbMaxWidth", item.thumbUrl)
        assertEquals("img/$seriesId/Primary/poster/$PosterMaxWidth", item.posterUrl)
        assertNull(item.progress)
    }

    @Test
    fun movieUsesBackdropForThumbAndPrimaryForPoster() {
        val item = BaseItemDto(
            id = itemId,
            type = BaseItemKind.MOVIE,
            name = "Film",
            productionYear = 2001,
            imageTags = mapOf(ImageType.PRIMARY to "poster"),
            backdropImageTags = listOf("backdrop"),
            userData = userData(played = true),
        ).toWatchItem(images)

        assertEquals("img/$itemId/Backdrop/backdrop/$ThumbMaxWidth", item.thumbUrl)
        assertEquals("img/$itemId/Primary/poster/$PosterMaxWidth", item.posterUrl)
        assertTrue(item.played)
        assertEquals(2001, item.productionYear)
    }

    @Test
    fun seriesIsNotPlayable() {
        val item = BaseItemDto(id = itemId, type = BaseItemKind.SERIES, userData = userData(unplayed = 4))
            .toWatchItem(images)
        assertFalse(item.isPlayable)
        assertEquals(4, item.unplayedCount)
    }

    @Test
    fun onlyVideoLibrariesAreShown() {
        fun view(type: CollectionType?) =
            BaseItemDto(id = itemId, type = BaseItemKind.COLLECTION_FOLDER, collectionType = type).toWatchLibrary(images)

        assertEquals(WatchLibraryKind.Movies, view(CollectionType.MOVIES)?.kind)
        assertEquals(WatchLibraryKind.Shows, view(CollectionType.TVSHOWS)?.kind)
        assertEquals(WatchLibraryKind.Mixed, view(null)?.kind)
        assertNull(view(CollectionType.MUSIC))
        assertNull(view(CollectionType.BOOKS))
    }

    @Test
    fun loadErrorsMapToFailures() {
        assertEquals(WatchLoadFailure.NotSignedIn, watchLoadFailure(JellyfinNotSignedInException()))
        assertEquals(WatchLoadFailure.SessionExpired, watchLoadFailure(InvalidStatusException(401)))
        assertEquals(WatchLoadFailure.ServerUnreachable, watchLoadFailure(TimeoutException("down")))
        assertEquals(WatchLoadFailure.Unknown, watchLoadFailure(IllegalArgumentException()))
    }

    @Test
    fun defaultSeasonSkipsSpecialsAndWatchedSeasons() {
        val specials = season("0", number = 0, unplayed = 2)
        val first = season("1", number = 1, unplayed = 0)
        val second = season("2", number = 2, unplayed = 5)
        assertEquals("2", defaultSeason(listOf(specials, first, second))?.id)
        assertEquals("1", defaultSeason(listOf(specials, first))?.id)
        assertEquals("0", defaultSeason(listOf(specials))?.id)
        assertNull(defaultSeason(emptyList()))
    }

    @Test
    fun runtimeRoundsToWholeMinutes() {
        assertEquals(24, watchRuntimeMinutes(23 * 60_000L + 40_000L))
        assertEquals(1, watchRuntimeMinutes(5_000L))
    }

    private fun season(id: String, number: Int, unplayed: Int) =
        WatchItem(id = id, name = "Season $number", kind = WatchItemKind.Season, seasonNumber = number, unplayedCount = unplayed)

    private fun userData(positionTicks: Long = 0, played: Boolean = false, unplayed: Int? = null) =
        UserItemDataDto(
            playbackPositionTicks = positionTicks,
            playCount = 0,
            isFavorite = false,
            played = played,
            key = "key",
            itemId = itemId,
            unplayedItemCount = unplayed,
        )
}
