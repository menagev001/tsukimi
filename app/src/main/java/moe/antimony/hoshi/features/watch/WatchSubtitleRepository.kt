package moe.antimony.hoshi.features.watch

import android.util.Log
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.di.IoDispatcher
import org.jellyfin.sdk.api.client.extensions.subtitleApi

data class WatchSubtitleSelection(
    val track: WatchSubtitleTrack,
    val cues: List<WatchSubtitleCue>,
)

data class WatchSubtitles(
    val tracks: List<WatchSubtitleTrack>,
    val japanese: WatchSubtitleSelection?,
    val english: WatchSubtitleSelection?,
)

@Singleton
class WatchSubtitleRepository @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val cacheLock = Mutex()
    private val cache = object : LinkedHashMap<String, List<WatchSubtitleCue>>(CacheSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<WatchSubtitleCue>>?) = size > CacheSize
    }

    /** Picks and downloads the Japanese track and, when there is one, the English track. */
    suspend fun load(itemId: String, mediaSourceId: String, tracks: List<WatchSubtitleTrack>): WatchSubtitles {
        val japanese = pickJapanese(itemId, mediaSourceId, tracks)
        val english = tracks
            .firstOrNull { it.isEnglish() && it.index != japanese?.track?.index }
            ?.let { track -> cues(itemId, mediaSourceId, track).getOrNull()?.let { WatchSubtitleSelection(track, it) } }
        return WatchSubtitles(tracks = tracks, japanese = japanese, english = english)
    }

    suspend fun cues(
        itemId: String,
        mediaSourceId: String,
        track: WatchSubtitleTrack,
    ): Result<List<WatchSubtitleCue>> = withContext(ioDispatcher) {
        val key = "$itemId/$mediaSourceId/${track.index}"
        cacheLock.withLock { cache[key] }?.let { return@withContext Result.success(it) }
        try {
            val session = jellyfinRepository.session()
            val srt = session.api.subtitleApi.getSubtitle(
                routeItemId = UUID.fromString(itemId),
                routeMediaSourceId = mediaSourceId,
                routeIndex = track.index,
                routeFormat = "srt",
            ).content
            val cues = parseSrt(srt)
            cacheLock.withLock { cache[key] = cues }
            Result.success(cues)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(Tag, "Subtitle download failed for stream ${track.index}: ${error.message}")
            Result.failure(error)
        }
    }

    private suspend fun pickJapanese(
        itemId: String,
        mediaSourceId: String,
        tracks: List<WatchSubtitleTrack>,
    ): WatchSubtitleSelection? {
        tracks.firstOrNull { it.isJapanese() }?.let { track ->
            return cues(itemId, mediaSourceId, track).getOrNull()?.let { WatchSubtitleSelection(track, it) }
        }
        return tracks
            .filter { it.isUntagged() }
            .take(MaxUntaggedTracksChecked)
            .firstNotNullOfOrNull { track ->
                cues(itemId, mediaSourceId, track).getOrNull()
                    ?.takeIf(::looksJapanese)
                    ?.let { WatchSubtitleSelection(track, it) }
            }
    }

    private companion object {
        const val Tag = "WatchSubtitles"
        const val CacheSize = 6
        const val MaxUntaggedTracksChecked = 3
    }
}
