package moe.antimony.hoshi.features.watch

import androidx.annotation.StringRes
import java.util.UUID
import kotlinx.serialization.Serializable
import moe.antimony.hoshi.R
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType

@Serializable
enum class WatchLibraryKind { Movies, Shows, Mixed }

data class WatchLibrary(
    val id: String,
    val name: String,
    val kind: WatchLibraryKind,
    val imageUrl: String?,
)

enum class WatchItemKind { Movie, Series, Season, Episode, Video, Other }

data class WatchItem(
    val id: String,
    val name: String,
    val kind: WatchItemKind,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val productionYear: Int? = null,
    val runTimeMs: Long? = null,
    val positionMs: Long = 0,
    val played: Boolean = false,
    val unplayedCount: Int? = null,
    val overview: String? = null,
    /** Portrait image: movie or series poster, season poster. */
    val posterUrl: String? = null,
    /** Landscape image: episode still, or a backdrop for movies and series. */
    val thumbUrl: String? = null,
) {
    val isPlayable: Boolean
        get() = kind == WatchItemKind.Movie || kind == WatchItemKind.Episode || kind == WatchItemKind.Video

    /** Playback progress between 0 and 1, or null when the item was not started. */
    val progress: Float?
        get() {
            val runTime = runTimeMs?.takeIf { it > 0 } ?: return null
            if (positionMs <= 0) return null
            return (positionMs.toFloat() / runTime).coerceIn(0f, 1f)
        }
}

data class WatchSeries(
    val series: WatchItem,
    val seasons: List<WatchItem>,
)

data class WatchHome(
    val continueWatching: List<WatchItem>,
    val nextUp: List<WatchItem>,
    val libraries: List<WatchLibrary>,
)

enum class WatchLoadFailure(@param:StringRes val messageRes: Int) {
    NotSignedIn(R.string.watch_error_not_signed_in),
    SessionExpired(R.string.watch_error_session_expired),
    ServerUnreachable(R.string.watch_login_error_unreachable),
    SecureConnectionFailed(R.string.watch_login_error_secure_connection),
    PlaybackUnavailable(R.string.watch_error_playback_unavailable),
    Unknown(R.string.watch_error_load_failed),
}

internal fun watchLoadFailure(error: Throwable): WatchLoadFailure = when (error) {
    is JellyfinNotSignedInException -> WatchLoadFailure.NotSignedIn
    is WatchPlaybackUnavailableException -> WatchLoadFailure.PlaybackUnavailable
    is TimeoutException -> WatchLoadFailure.ServerUnreachable
    is SecureConnectionException -> WatchLoadFailure.SecureConnectionFailed
    is InvalidStatusException -> if (error.status == 401) {
        WatchLoadFailure.SessionExpired
    } else {
        WatchLoadFailure.Unknown
    }
    else -> WatchLoadFailure.Unknown
}

/** Builds an image URL for an item; implemented with the SDK's image API outside tests. */
internal fun interface WatchImageUrlBuilder {
    fun url(itemId: UUID, imageType: ImageType, tag: String, maxWidth: Int): String
}

internal const val PosterMaxWidth = 400
internal const val ThumbMaxWidth = 640
private const val TicksPerMillisecond = 10_000L

internal fun Long.ticksToMs(): Long = this / TicksPerMillisecond

internal fun Long.msToTicks(): Long = this * TicksPerMillisecond

internal fun BaseItemDto.toWatchLibrary(images: WatchImageUrlBuilder): WatchLibrary? {
    val kind = when (collectionType) {
        CollectionType.MOVIES -> WatchLibraryKind.Movies
        CollectionType.TVSHOWS -> WatchLibraryKind.Shows
        CollectionType.HOMEVIDEOS, CollectionType.MUSICVIDEOS, null -> WatchLibraryKind.Mixed
        else -> return null
    }
    return WatchLibrary(
        id = id.toString(),
        name = name.orEmpty(),
        kind = kind,
        imageUrl = imageTags?.get(ImageType.PRIMARY)?.let { images.url(id, ImageType.PRIMARY, it, ThumbMaxWidth) },
    )
}

internal fun BaseItemDto.toWatchItem(images: WatchImageUrlBuilder): WatchItem {
    val kind = when (type) {
        BaseItemKind.MOVIE -> WatchItemKind.Movie
        BaseItemKind.SERIES -> WatchItemKind.Series
        BaseItemKind.SEASON -> WatchItemKind.Season
        BaseItemKind.EPISODE -> WatchItemKind.Episode
        BaseItemKind.VIDEO, BaseItemKind.MUSIC_VIDEO -> WatchItemKind.Video
        else -> WatchItemKind.Other
    }
    val primaryTag = imageTags?.get(ImageType.PRIMARY)
    val ownPrimary = primaryTag?.let { images.url(id, ImageType.PRIMARY, it, PosterMaxWidth) }
    val posterUrl = when (kind) {
        WatchItemKind.Season, WatchItemKind.Episode -> ownPrimary
            ?: seriesPrimaryImageTag?.let { tag ->
                seriesId?.let { images.url(it, ImageType.PRIMARY, tag, PosterMaxWidth) }
            }
        else -> ownPrimary
    }
    val backdropUrl = backdropImageTags?.firstOrNull()?.let { images.url(id, ImageType.BACKDROP, it, ThumbMaxWidth) }
        ?: parentBackdropImageTags?.firstOrNull()?.let { tag ->
            parentBackdropItemId?.let { images.url(it, ImageType.BACKDROP, tag, ThumbMaxWidth) }
        }
    val thumbUrl = when (kind) {
        WatchItemKind.Episode, WatchItemKind.Video -> primaryTag?.let { images.url(id, ImageType.PRIMARY, it, ThumbMaxWidth) }
            ?: parentThumbImageTag?.let { tag ->
                parentThumbItemId?.let { images.url(it, ImageType.THUMB, tag, ThumbMaxWidth) }
            }
            ?: backdropUrl
        else -> imageTags?.get(ImageType.THUMB)?.let { images.url(id, ImageType.THUMB, it, ThumbMaxWidth) }
            ?: backdropUrl
            ?: ownPrimary
    }
    return WatchItem(
        id = id.toString(),
        name = name.orEmpty(),
        kind = kind,
        seriesId = seriesId?.toString(),
        seriesName = seriesName,
        seasonNumber = when (kind) {
            WatchItemKind.Season -> indexNumber
            WatchItemKind.Episode -> parentIndexNumber
            else -> null
        },
        episodeNumber = indexNumber.takeIf { kind == WatchItemKind.Episode },
        productionYear = productionYear,
        runTimeMs = runTimeTicks?.ticksToMs(),
        positionMs = userData?.playbackPositionTicks?.ticksToMs() ?: 0,
        played = userData?.played ?: false,
        unplayedCount = userData?.unplayedItemCount,
        overview = overview,
        posterUrl = posterUrl,
        thumbUrl = thumbUrl,
    )
}
