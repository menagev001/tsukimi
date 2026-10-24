package moe.antimony.hoshi.features.watch

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchPlayerUiState(
    val source: WatchLoadState<WatchPlaybackSource> = WatchLoadState.Loading,
    val isBuffering: Boolean = false,
    val hasPlaybackError: Boolean = false,
)

@OptIn(UnstableApi::class)
@HiltViewModel(assistedFactory = WatchPlayerViewModel.Factory::class)
internal class WatchPlayerViewModel @AssistedInject constructor(
    @Assisted private val itemId: String,
    @ApplicationContext context: Context,
    private val playbackRepository: WatchPlaybackRepository,
    private val reporter: WatchPlaybackReporter,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WatchPlayerUiState())
    val uiState: StateFlow<WatchPlayerUiState> = _uiState.asStateFlow()

    private var activeSource: WatchPlaybackSource? = null

    /** True between the start and stop reports sent to Jellyfin. */
    private var isReporting = false

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setSeekBackIncrementMs(SeekIncrementMs)
        .setSeekForwardIncrementMs(SeekIncrementMs)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            // Subtitles are drawn by Tsukimi, never by the player.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _uiState.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
                        when (playbackState) {
                            Player.STATE_READY -> reportStart()
                            Player.STATE_ENDED -> reportStop()
                        }
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        reportProgress()
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int,
                    ) {
                        if (reason == Player.DISCONTINUITY_REASON_SEEK) reportProgress()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.w(Tag, "Playback failed: ${error.errorCodeName}", error)
                        _uiState.update { it.copy(hasPlaybackError = true, isBuffering = false) }
                    }
                },
            )
        }

    init {
        load()
        viewModelScope.launch {
            while (true) {
                delay(ProgressReportIntervalMs)
                if (player.isPlaying) reportProgress()
            }
        }
    }

    fun retry() {
        val state = _uiState.value
        when {
            state.source is WatchLoadState.Failed -> load()
            state.hasPlaybackError -> {
                _uiState.update { it.copy(hasPlaybackError = false) }
                player.prepare()
                player.play()
            }
        }
    }

    fun pause() {
        player.pause()
    }

    private fun load() {
        _uiState.value = WatchPlayerUiState()
        viewModelScope.launch {
            val result = playbackRepository.prepare(itemId)
            result.onSuccess(::start)
            _uiState.update { it.copy(source = result.toWatchLoadState()) }
        }
    }

    private fun start(source: WatchPlaybackSource) {
        activeSource = source
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(source.requestHeaders)
            .setAllowCrossProtocolRedirects(true)
        val mediaItem = MediaItem.Builder()
            .setUri(source.streamUrl)
            .apply { if (source.isTranscoding) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        player.setMediaSource(
            DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem),
            source.startPositionMs,
        )
        player.prepare()
        player.play()
    }

    private fun reportStart() {
        val source = activeSource ?: return
        if (isReporting) return
        isReporting = true
        reporter.start(source, player.currentPosition, isPaused = !player.playWhenReady)
    }

    private fun reportProgress() {
        val source = activeSource ?: return
        if (isReporting) reporter.progress(source, player.currentPosition, isPaused = !player.playWhenReady)
    }

    private fun reportStop() {
        val source = activeSource ?: return
        if (!isReporting) return
        isReporting = false
        reporter.stop(source, player.currentPosition)
    }

    override fun onCleared() {
        reportStop()
        player.release()
    }

    @AssistedFactory
    interface Factory {
        fun create(itemId: String): WatchPlayerViewModel
    }

    private companion object {
        const val Tag = "WatchPlayer"
        const val SeekIncrementMs = 10_000L
        const val ProgressReportIntervalMs = 10_000L
    }
}
