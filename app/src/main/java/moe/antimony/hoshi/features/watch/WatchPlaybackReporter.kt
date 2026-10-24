package moe.antimony.hoshi.features.watch

import android.util.Log
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import moe.antimony.hoshi.di.ApplicationScope
import moe.antimony.hoshi.di.IoDispatcher
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode

/**
 * Tells Jellyfin about playback so watched state and Continue Watching match the official apps.
 * Reports run one after another in the application scope, so the stop report still arrives after the
 * player screen is gone.
 */
@Singleton
class WatchPlaybackReporter @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val reports = Channel<Pair<String, suspend ApiClient.() -> Unit>>(Channel.UNLIMITED)

    init {
        appScope.launch(ioDispatcher) {
            for ((kind, block) in reports) {
                try {
                    jellyfinRepository.session().api.block()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w(Tag, "Playback $kind report failed: ${error.message}")
                }
            }
        }
    }

    fun start(source: WatchPlaybackSource, positionMs: Long, isPaused: Boolean) = report("start") {
        sessionApi.reportPlaybackStart(
            PlaybackStartInfo(
                itemId = source.itemUuid,
                mediaSourceId = source.mediaSourceId,
                playSessionId = source.playSessionId,
                positionTicks = positionMs.msToTicks(),
                isPaused = isPaused,
                isMuted = false,
                canSeek = true,
                playMethod = source.playMethod,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    fun progress(source: WatchPlaybackSource, positionMs: Long, isPaused: Boolean) = report("progress") {
        sessionApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                itemId = source.itemUuid,
                mediaSourceId = source.mediaSourceId,
                playSessionId = source.playSessionId,
                positionTicks = positionMs.msToTicks(),
                isPaused = isPaused,
                isMuted = false,
                canSeek = true,
                playMethod = source.playMethod,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    fun stop(source: WatchPlaybackSource, positionMs: Long) = report("stop") {
        sessionApi.reportPlaybackStopped(
            PlaybackStopInfo(
                itemId = source.itemUuid,
                mediaSourceId = source.mediaSourceId,
                playSessionId = source.playSessionId,
                positionTicks = positionMs.msToTicks(),
                failed = false,
            ),
        )
    }

    private fun report(kind: String, block: suspend ApiClient.() -> Unit) {
        reports.trySend(kind to block)
    }

    private companion object {
        const val Tag = "WatchPlaybackReporter"
    }
}

private val WatchPlaybackSource.itemUuid: UUID
    get() = UUID.fromString(item.id)

private val WatchPlaybackSource.playMethod: PlayMethod
    get() = if (isTranscoding) PlayMethod.TRANSCODE else PlayMethod.DIRECT_PLAY
