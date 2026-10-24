package moe.antimony.hoshi.features.watch

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.di.DefaultDispatcher
import moe.antimony.hoshi.di.IoDispatcher
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.videoApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlaybackInfoDto

/** Everything the player needs to open one video. */
data class WatchPlaybackSource(
    val item: WatchItem,
    val streamUrl: String,
    val requestHeaders: Map<String, String>,
    val isTranscoding: Boolean,
    val mediaSourceId: String?,
    val playSessionId: String?,
    val startPositionMs: Long,
    /** Jellyfin's audio streams of the media source, in container order. */
    val audioStreams: List<WatchAudioStream>,
    /** The Jellyfin stream index Jellyfin was asked for; a transcode contains only this one. */
    val audioStreamIndex: Int?,
    /** ISO 639-1 language the player should prefer when the stream has several audio tracks. */
    val preferredAudioLanguage: String?,
    /** Text subtitle streams; Tsukimi downloads and draws these itself. */
    val subtitleTracks: List<WatchSubtitleTrack>,
)

data class WatchAudioStream(
    val index: Int,
    val label: String,
    val language: String?,
)

class WatchPlaybackUnavailableException : IllegalStateException("Jellyfin offered no playable stream")

@Singleton
class WatchPlaybackRepository @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val preferencesRepository: WatchPreferencesRepository,
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    private var decoderCapabilities: WatchDecoderCapabilities? = null

    /**
     * Asks Jellyfin how to play [itemId]. [audioStreamIndex] and [startPositionMs] override the
     * preferred audio language and the saved resume position, for switching audio mid-playback.
     */
    suspend fun prepare(
        itemId: String,
        audioStreamIndex: Int? = null,
        startPositionMs: Long? = null,
    ): Result<WatchPlaybackSource> = withContext(ioDispatcher) {
        try {
            val session = jellyfinRepository.session()
            val id = UUID.fromString(itemId)
            val preferences = preferencesRepository.preferences.first()
            val maxBitrate = if (isActiveNetworkMetered()) {
                preferences.meteredQuality.maxBitrate
            } else {
                OriginalQualityBitrate
            }
            val item = session.api.libraryApi.getItem(itemId = id, userId = session.userId).content
            val itemAudioStreams = item.mediaSources?.firstOrNull()?.mediaStreams.orEmpty().toWatchAudioStreams()
            val requestedAudioIndex = audioStreamIndex
                ?: preferredAudioStreamIndex(itemAudioStreams, preferences.audioLanguage)
            val info = session.api.mediaInfoApi.getPostedPlaybackInfo(
                itemId = id,
                data = PlaybackInfoDto(
                    userId = session.userId,
                    maxStreamingBitrate = maxBitrate,
                    deviceProfile = jellyfinDeviceProfile(capabilities(), maxBitrate),
                    audioStreamIndex = requestedAudioIndex,
                    enableDirectPlay = true,
                    enableDirectStream = true,
                    enableTranscoding = true,
                    allowVideoStreamCopy = true,
                    allowAudioStreamCopy = true,
                    autoOpenLiveStream = true,
                    subtitleStreamIndex = -1,
                ),
            ).content
            if (info.errorCode != null) {
                Log.w(Tag, "Jellyfin refused playback: ${info.errorCode}")
                throw WatchPlaybackUnavailableException()
            }
            val source = info.mediaSources.firstOrNull() ?: throw WatchPlaybackUnavailableException()
            val watchItem = item.toWatchItem { imageId, imageType, tag, maxWidth ->
                session.api.imageApi.getItemImageUrl(itemId = imageId, imageType = imageType, maxWidth = maxWidth, tag = tag)
            }
            val (streamUrl, isTranscoding) = streamUrl(session, id, source, info.playSessionId)
            Log.i(Tag, "Playing ${source.container} by ${if (isTranscoding) "transcoding" else "direct play"}")
            val api = session.api
            Result.success(
                WatchPlaybackSource(
                    item = watchItem,
                    streamUrl = streamUrl,
                    requestHeaders = mapOf(
                        "Authorization" to AuthorizationHeaderBuilder.buildHeader(
                            clientName = api.clientInfo.name,
                            clientVersion = api.clientInfo.version,
                            deviceId = api.deviceInfo.id,
                            deviceName = api.deviceInfo.name,
                            accessToken = api.accessToken,
                        ),
                    ),
                    isTranscoding = isTranscoding,
                    mediaSourceId = source.id,
                    playSessionId = info.playSessionId,
                    startPositionMs = startPositionMs ?: resumePositionMs(watchItem),
                    audioStreams = source.mediaStreams.orEmpty().toWatchAudioStreams(),
                    audioStreamIndex = requestedAudioIndex ?: source.defaultAudioStreamIndex,
                    preferredAudioLanguage = preferences.audioLanguage.playerCode,
                    subtitleTracks = source.mediaStreams.orEmpty().toWatchSubtitleTracks(),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(Tag, "Preparing playback failed: ${error.message}")
            Result.failure(error)
        }
    }

    private fun streamUrl(
        session: JellyfinSession,
        itemId: UUID,
        source: MediaSourceInfo,
        playSessionId: String?,
    ): Pair<String, Boolean> {
        if (source.supportsDirectPlay) {
            return session.api.videoApi.getVideoStreamUrl(
                itemId = itemId,
                container = source.container,
                static = true,
                tag = source.eTag,
                playSessionId = playSessionId,
                mediaSourceId = source.id,
            ) to false
        }
        val transcodingUrl = source.transcodingUrl ?: throw WatchPlaybackUnavailableException()
        val baseUrl = session.api.baseUrl.orEmpty().trimEnd('/')
        return "$baseUrl$transcodingUrl" to true
    }

    private fun isActiveNetworkMetered(): Boolean =
        context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false

    private suspend fun capabilities(): WatchDecoderCapabilities =
        decoderCapabilities ?: withContext(defaultDispatcher) { detectWatchDecoderCapabilities() }
            .also { decoderCapabilities = it }

    private companion object {
        const val Tag = "WatchPlayback"
    }
}

private fun List<MediaStream>.toWatchAudioStreams(): List<WatchAudioStream> =
    filter { it.type == MediaStreamType.AUDIO }.map { stream ->
        WatchAudioStream(
            index = stream.index,
            label = stream.displayTitle ?: stream.title ?: stream.language ?: stream.codec.orEmpty(),
            language = stream.language,
        )
    }

private fun List<MediaStream>.toWatchSubtitleTracks(): List<WatchSubtitleTrack> =
    filter { it.type == MediaStreamType.SUBTITLE && it.isTextSubtitleStream }.map { stream ->
        WatchSubtitleTrack(
            index = stream.index,
            label = stream.displayTitle ?: stream.title ?: stream.language ?: stream.codec.orEmpty(),
            language = stream.language,
        )
    }

/** The first audio stream in the preferred language, or null to let Jellyfin pick its default. */
internal fun preferredAudioStreamIndex(streams: List<WatchAudioStream>, language: WatchAudioLanguage): Int? {
    val codes = setOfNotNull(language.jellyfinCode, language.playerCode)
    if (codes.isEmpty()) return null
    return streams.firstOrNull { it.language?.lowercase() in codes }?.index
}

/** Resume from the saved position unless the item was finished or barely started. */
internal fun resumePositionMs(item: WatchItem): Long {
    if (item.played) return 0
    val runTime = item.runTimeMs ?: return item.positionMs
    return item.positionMs.takeIf { it in MinResumeMs until (runTime * MaxResumeFraction).toLong() } ?: 0
}

private const val MinResumeMs = 10_000L
private const val MaxResumeFraction = 0.95
