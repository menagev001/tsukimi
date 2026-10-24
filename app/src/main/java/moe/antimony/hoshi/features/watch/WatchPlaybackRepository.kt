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
import moe.antimony.hoshi.di.DefaultDispatcher
import moe.antimony.hoshi.di.IoDispatcher
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.videoApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.api.MediaSourceInfo
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
)

class WatchPlaybackUnavailableException : IllegalStateException("Jellyfin offered no playable stream")

@Singleton
class WatchPlaybackRepository @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {
    private var decoderCapabilities: WatchDecoderCapabilities? = null

    suspend fun prepare(itemId: String): Result<WatchPlaybackSource> = withContext(ioDispatcher) {
        try {
            val session = jellyfinRepository.session()
            val id = UUID.fromString(itemId)
            val profile = jellyfinDeviceProfile(capabilities(), maxBitrate = OriginalQualityBitrate)
            coroutineScope {
                val item = async { session.api.libraryApi.getItem(itemId = id, userId = session.userId).content }
                val playbackInfo = async {
                    session.api.mediaInfoApi.getPostedPlaybackInfo(
                        itemId = id,
                        data = PlaybackInfoDto(
                            userId = session.userId,
                            maxStreamingBitrate = OriginalQualityBitrate,
                            deviceProfile = profile,
                            enableDirectPlay = true,
                            enableDirectStream = true,
                            enableTranscoding = true,
                            allowVideoStreamCopy = true,
                            allowAudioStreamCopy = true,
                            autoOpenLiveStream = true,
                            subtitleStreamIndex = -1,
                        ),
                    ).content
                }
                val info = playbackInfo.await()
                if (info.errorCode != null) {
                    Log.w(Tag, "Jellyfin refused playback: ${info.errorCode}")
                    throw WatchPlaybackUnavailableException()
                }
                val source = info.mediaSources.firstOrNull() ?: throw WatchPlaybackUnavailableException()
                val watchItem = item.await().toWatchItem { imageId, imageType, tag, maxWidth ->
                    session.api.imageApi.getItemImageUrl(itemId = imageId, imageType = imageType, maxWidth = maxWidth, tag = tag)
                }
                val (streamUrl, isTranscoding) = streamUrl(session, id, source, info.playSessionId)
                Log.i(Tag, "Playing ${source.container} by ${if (isTranscoding) "transcoding" else "direct play"}")
                val api = session.api
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
                    startPositionMs = resumePositionMs(watchItem),
                )
            }.let { Result.success(it) }
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

    private suspend fun capabilities(): WatchDecoderCapabilities =
        decoderCapabilities ?: withContext(defaultDispatcher) { detectWatchDecoderCapabilities() }
            .also { decoderCapabilities = it }

    private companion object {
        const val Tag = "WatchPlayback"
        const val OriginalQualityBitrate = 120_000_000
    }
}

/** Resume from the saved position unless the item was finished or barely started. */
internal fun resumePositionMs(item: WatchItem): Long {
    if (item.played) return 0
    val runTime = item.runTimeMs ?: return item.positionMs
    return item.positionMs.takeIf { it in MinResumeMs until (runTime * MaxResumeFraction).toLong() } ?: 0
}

private const val MinResumeMs = 10_000L
private const val MaxResumeFraction = 0.95
