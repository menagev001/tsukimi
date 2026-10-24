package moe.antimony.hoshi.features.watch

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.assisted.Assisted
import java.util.Locale
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
    val audioTracks: List<WatchAudioTrackOption> = emptyList(),
)

data class WatchAudioTrackOption(
    val label: String,
    val isSelected: Boolean,
    /** Index of the player's audio track group, for direct play. */
    internal val playerGroupIndex: Int? = null,
    /** Jellyfin stream index, for transcodes that carry a single audio stream. */
    internal val jellyfinStreamIndex: Int? = null,
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

                    override fun onTracksChanged(tracks: Tracks) {
                        updateAudioTracks()
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

    fun selectAudioTrack(option: WatchAudioTrackOption) {
        if (option.isSelected) return
        option.playerGroupIndex?.let { groupIndex ->
            val group = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.getOrNull(groupIndex) ?: return
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                .build()
            return
        }
        val streamIndex = option.jellyfinStreamIndex ?: return
        viewModelScope.launch {
            val positionMs = player.currentPosition
            playbackRepository.prepare(itemId, audioStreamIndex = streamIndex, startPositionMs = positionMs)
                .onSuccess { source ->
                    reportStop()
                    start(source)
                    _uiState.update { it.copy(source = WatchLoadState.Ready(source)) }
                }
        }
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
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setPreferredAudioLanguage(source.preferredAudioLanguage)
            .build()
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

    private fun updateAudioTracks() {
        val source = activeSource ?: return
        val options = if (source.isTranscoding) {
            source.audioStreams.map { stream ->
                WatchAudioTrackOption(
                    label = stream.label,
                    isSelected = stream.index == source.audioStreamIndex,
                    jellyfinStreamIndex = stream.index,
                )
            }
        } else {
            val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            // Direct play keeps the container's track order, which is also Jellyfin's stream order.
            val jellyfinLabels = source.audioStreams.map { it.label }.takeIf { it.size == groups.size }
            groups.mapIndexed { index, group ->
                WatchAudioTrackOption(
                    label = jellyfinLabels?.get(index) ?: playerTrackLabel(group.getTrackFormat(0)),
                    isSelected = group.isSelected,
                    playerGroupIndex = index,
                )
            }
        }
        _uiState.update { it.copy(audioTracks = options) }
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

    private fun playerTrackLabel(format: Format): String =
        listOfNotNull(
            format.label,
            format.language?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } },
            format.channelCount.takeIf { it > 0 }?.let { "${it}ch" },
        ).distinct().joinToString(" · ")

    private companion object {
        const val Tag = "WatchPlayer"
        const val SeekIncrementMs = 10_000L
        const val ProgressReportIntervalMs = 10_000L
    }
}
