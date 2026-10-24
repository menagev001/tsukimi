package moe.antimony.hoshi.features.watch

import android.util.Log
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.di.IoDispatcher
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType

@Singleton
class WatchBrowseRepository @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun home(): Result<WatchHome> = request { session, images ->
        coroutineScope {
            val resume = async {
                session.api.libraryApi.getResumeItems(
                    userId = session.userId,
                    limit = HomeRowLimit,
                    mediaTypes = listOf(MediaType.VIDEO),
                    enableUserData = true,
                    imageTypeLimit = 1,
                    enableImageTypes = HomeImageTypes,
                    enableTotalRecordCount = false,
                ).content.items
            }
            val nextUp = async {
                session.api.showApi.getNextUp(
                    userId = session.userId,
                    limit = HomeRowLimit,
                    enableUserData = true,
                    imageTypeLimit = 1,
                    enableImageTypes = HomeImageTypes,
                    enableTotalRecordCount = false,
                    enableResumable = false,
                ).content.items
            }
            val views = async {
                session.api.userViewApi.getUserViews(userId = session.userId).content.items
            }
            WatchHome(
                continueWatching = resume.await().map { it.toWatchItem(images) },
                nextUp = nextUp.await().map { it.toWatchItem(images) },
                libraries = views.await().mapNotNull { it.toWatchLibrary(images) },
            )
        }
    }

    suspend fun libraryItems(libraryId: String, kind: WatchLibraryKind): Result<List<WatchItem>> =
        request { session, images ->
            session.api.libraryApi.getItems(
                userId = session.userId,
                parentId = UUID.fromString(libraryId),
                recursive = true,
                includeItemTypes = when (kind) {
                    WatchLibraryKind.Movies -> listOf(BaseItemKind.MOVIE)
                    WatchLibraryKind.Shows -> listOf(BaseItemKind.SERIES)
                    WatchLibraryKind.Mixed -> listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.VIDEO)
                },
                sortBy = listOf(ItemSortBy.SORT_NAME),
                enableUserData = true,
                imageTypeLimit = 1,
                enableImageTypes = listOf(ImageType.PRIMARY),
                enableTotalRecordCount = false,
            ).content.items.map { it.toWatchItem(images) }
        }

    suspend fun series(seriesId: String): Result<WatchSeries> = request { session, images ->
        val id = UUID.fromString(seriesId)
        coroutineScope {
            val series = async { session.api.libraryApi.getItem(itemId = id, userId = session.userId).content }
            val seasons = async {
                session.api.showApi.getSeasons(
                    seriesId = id,
                    userId = session.userId,
                    enableUserData = true,
                    enableImages = true,
                ).content.items
            }
            WatchSeries(
                series = series.await().toWatchItem(images),
                seasons = seasons.await().map { it.toWatchItem(images) },
            )
        }
    }

    suspend fun episodes(seriesId: String, seasonId: String): Result<List<WatchItem>> =
        request { session, images ->
            session.api.showApi.getEpisodes(
                seriesId = UUID.fromString(seriesId),
                userId = session.userId,
                seasonId = UUID.fromString(seasonId),
                fields = listOf(ItemFields.OVERVIEW),
                enableUserData = true,
                enableImages = true,
            ).content.items.map { it.toWatchItem(images) }
        }

    private suspend fun <T> request(
        block: suspend (JellyfinSession, WatchImageUrlBuilder) -> T,
    ): Result<T> = withContext(ioDispatcher) {
        try {
            val session = jellyfinRepository.session()
            val images = WatchImageUrlBuilder { itemId, imageType, tag, maxWidth ->
                session.api.imageApi.getItemImageUrl(
                    itemId = itemId,
                    imageType = imageType,
                    maxWidth = maxWidth,
                    tag = tag,
                    quality = ImageQuality,
                )
            }
            Result.success(block(session, images))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(Tag, "Jellyfin request failed: ${error.message}")
            Result.failure(error)
        }
    }

    private companion object {
        const val Tag = "WatchBrowseRepository"
        const val HomeRowLimit = 16
        const val ImageQuality = 90
        val HomeImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB, ImageType.BACKDROP)
    }
}
