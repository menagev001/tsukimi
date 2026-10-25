package moe.antimony.hoshi.features.watch

import android.content.Context
import android.graphics.Bitmap
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
import moe.antimony.hoshi.features.ai.AiSentenceContext
import moe.antimony.hoshi.features.anki.AnkiCardRequest
import moe.antimony.hoshi.features.anki.AnkiMiningContext
import moe.antimony.hoshi.features.anki.AnkiMiningPayload
import java.io.File
import java.util.Locale
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.antimony.hoshi.di.IoDispatcher

data class WatchPlayerUiState(
    val source: WatchLoadState<WatchPlaybackSource> = WatchLoadState.Loading,
    val isBuffering: Boolean = false,
    val hasPlaybackError: Boolean = false,
    val audioTracks: List<WatchAudioTrackOption> = emptyList(),
    val subtitles: WatchSubtitlesUiState = WatchSubtitlesUiState(),
    /** The lines a card from the open lookup would use; null without a lookup on a subtitle. */
    val cardSelection: WatchCardSelection? = null,
)

data class WatchSubtitlesUiState(
    val tracks: List<WatchSubtitleTrack> = emptyList(),
    /** Jellyfin stream index of the shown track, or null when subtitles are off. */
    val selectedTrackIndex: Int? = null,
    val isLoading: Boolean = false,
    val offsetMs: Long = 0,
    val fontSizePx: Int = WatchSubtitleSize.Medium.fontSizePx,
    /** Texts of the Japanese cues shown right now. */
    val primaryLines: List<String> = emptyList(),
    /** The whole selected track, for the line list. */
    val cues: List<WatchSubtitleCue> = emptyList(),
    /** Index in [cues] of the line playing now (or last played), for the line list. */
    val activeCueIndex: Int? = null,
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
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val playbackRepository: WatchPlaybackRepository,
    private val reporter: WatchPlaybackReporter,
    private val subtitleRepository: WatchSubtitleRepository,
    private val preferencesRepository: WatchPreferencesRepository,
    private val audioClipper: WatchSentenceAudioClipper,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WatchPlayerUiState())
    val uiState: StateFlow<WatchPlayerUiState> = _uiState.asStateFlow()

    private var activeSource: WatchPlaybackSource? = null

    /** True between the start and stop reports sent to Jellyfin. */
    private var isReporting = false

    private var primaryCues: List<WatchSubtitleCue> = emptyList()
    private var englishCues: List<WatchSubtitleCue> = emptyList()
    private var resumeAfterLookup = true
    private var pausedForLookup = false
    private var subtitlesLoadedFor: String? = null

    private val screenshotDirectory = File(context.cacheDir, "anki-media")

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
                        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                            reportProgress()
                            updateActiveCues()
                        }
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
        viewModelScope.launch {
            while (true) {
                delay(CueTickMs)
                if (player.isPlaying) updateActiveCues()
            }
        }
        viewModelScope.launch {
            preferencesRepository.preferences.collect { preferences ->
                resumeAfterLookup = preferences.resumeAfterLookup
                updateSubtitles { it.copy(fontSizePx = preferences.subtitleSize.fontSizePx) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.subtitleOffsetMs(itemId).collect { offsetMs ->
                updateSubtitles { it.copy(offsetMs = offsetMs) }
                updateActiveCues()
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

    /** Pauses while a lookup popup or AI result is open and resumes after it, if set to. */
    fun onLookupActiveChanged(active: Boolean) {
        if (active) {
            if (player.isPlaying) {
                pausedForLookup = true
                player.pause()
            }
        } else if (pausedForLookup) {
            pausedForLookup = false
            if (resumeAfterLookup) player.play()
        }
    }

    /**
     * The sentence and context for an AI lookup from [sentence]: the merged lines and their
     * neighbours when lines were added, else the line itself. Null context when it is not a line.
     */
    fun aiRequestFor(sentence: String): Pair<String, AiSentenceContext?> {
        val selection = cardSelectionFor(sentence) ?: return sentence to null
        return selection.text to subtitleAiContext(primaryCues, selection.range)
    }

    /** Starts the card selection at the line of the open lookup, or clears it ([sentence] null). */
    fun setCardBase(sentence: String?) {
        val selection = sentence?.let { lineSelection(it) }
        if (selection?.baseIndex != _uiState.value.cardSelection?.baseIndex) {
            _uiState.update { it.copy(cardSelection = selection) }
        }
    }

    fun extendCardSelection(previous: Boolean) {
        _uiState.update { state -> state.copy(cardSelection = state.cardSelection?.extended(primaryCues, previous)) }
    }

    fun resetCardSelection() {
        _uiState.update { state ->
            state.copy(cardSelection = state.cardSelection?.let { watchCardSelection(primaryCues, it.baseIndex, it.baseIndex..it.baseIndex) })
        }
    }

    /** The current selection when [sentence] is its tapped line, else that line on its own. */
    private fun cardSelectionFor(sentence: String): WatchCardSelection? =
        _uiState.value.cardSelection?.takeIf { primaryCues[it.baseIndex].text.replace("\n", "") == sentence }
            ?: lineSelection(sentence)

    private fun lineSelection(sentence: String): WatchCardSelection? {
        val index = cueIndexForSentence(primaryCues, sentence, player.currentPosition - _uiState.value.subtitles.offsetMs)
            ?: return null
        return watchCardSelection(primaryCues, index, index..index)
    }

    /**
     * Adds what a Watch card knows beyond the popup: `{source}`, `{sentence-translation}` and the
     * tags. Times are video times, so the subtitle offset is applied.
     */
    fun cardContext(base: AnkiMiningContext): AnkiMiningContext {
        val item = activeSource?.item ?: return base
        val offsetMs = _uiState.value.subtitles.offsetMs
        val selection = cardSelectionFor(base.sentence)
            ?: return base.copy(
                source = watchCardSource(item, player.currentPosition),
                extraTags = base.extraTags + watchCardTags(item),
            )
        val first = primaryCues[selection.range.first]
        val last = primaryCues[selection.range.last]
        return base.copy(
            sentence = selection.text,
            sentenceOffset = base.sentenceOffset?.plus(selection.offsetBeforeBase(primaryCues)),
            source = watchCardSource(item, primaryCues[selection.baseIndex].startMs + offsetMs),
            sentenceTranslation = watchCardTranslation(englishCues, first.startMs, last.endMs),
            extraTags = base.extraTags + watchCardTags(item),
        )
    }

    /**
     * A card for the popup's Anki button. [frame] is the video frame copied when the button was
     * pressed. Inside the job the frame is saved and the lines' audio is clipped; both files are
     * deleted when the job ends. A failed clip still adds the card, tagged as missing its audio.
     */
    fun cardRequest(
        formatId: String?,
        payloadJson: String,
        context: AnkiMiningContext,
        selectionText: String,
        frame: Deferred<Bitmap?>,
    ): AnkiCardRequest {
        var screenshot: File? = null
        var sentenceAudio: File? = null
        val audioClip = audioClipRequest(context.sentence)
        return AnkiCardRequest(
            label = runCatching { AnkiMiningPayload.fromJson(payloadJson).expression }.getOrNull()
                ?.takeIf(String::isNotBlank) ?: selectionText,
            formatId = formatId,
            payloadJson = payloadJson,
            context = cardContext(context),
            prepareContext = { cardContext ->
                coroutineScope {
                    val clip = async {
                        audioClip?.let { withTimeoutOrNull(AudioClipTimeoutMs) { audioClipper.clip(it) } }
                    }
                    screenshot = frame.await()?.let { bitmap ->
                        withContext(ioDispatcher) { saveWatchScreenshot(bitmap, screenshotDirectory) }
                    }
                    sentenceAudio = clip.await()
                }
                cardContext.copy(
                    screenshotPath = screenshot?.path,
                    sentenceAudioPath = sentenceAudio?.path,
                    extraTags = cardContext.extraTags + listOfNotNull(AudioMissingTag.takeIf { audioClip != null && sentenceAudio == null }),
                )
            },
            cleanUp = {
                screenshot?.delete()
                sentenceAudio?.delete()
            },
        )
    }

    /** The card's lines in video time, padded on both sides, from the original file. */
    private fun audioClipRequest(sentence: String): WatchAudioClipRequest? {
        val source = activeSource ?: return null
        val selection = cardSelectionFor(sentence) ?: return null
        val offsetMs = _uiState.value.subtitles.offsetMs
        return WatchAudioClipRequest(
            streamUrl = source.originalStreamUrl,
            requestHeaders = source.requestHeaders,
            startMs = primaryCues[selection.range.first].startMs + offsetMs - SentenceAudioPaddingMs,
            endMs = primaryCues[selection.range.last].endMs + offsetMs + SentenceAudioPaddingMs,
            audioLanguage = source.audioStreams.firstOrNull { it.index == source.audioStreamIndex }?.language,
        )
    }

    fun seekToCue(cueIndex: Int) {
        val cue = primaryCues.getOrNull(cueIndex) ?: return
        player.seekTo((cue.startMs + _uiState.value.subtitles.offsetMs).coerceAtLeast(0))
    }

    fun seekBy(deltaMs: Long) {
        player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0))
    }

    fun selectSubtitleTrack(track: WatchSubtitleTrack?) {
        val source = activeSource ?: return
        if (track == null) {
            primaryCues = emptyList()
            updateSubtitles { it.copy(selectedTrackIndex = null, isLoading = false, cues = emptyList()) }
            updateActiveCues()
            return
        }
        updateSubtitles { it.copy(selectedTrackIndex = track.index, isLoading = true) }
        viewModelScope.launch {
            val cues = subtitleRepository.cues(itemId, source.mediaSourceId.orEmpty(), track).getOrNull()
            if (_uiState.value.subtitles.selectedTrackIndex != track.index) return@launch
            primaryCues = cues.orEmpty()
            updateSubtitles {
                it.copy(isLoading = false, selectedTrackIndex = track.index.takeIf { cues != null }, cues = primaryCues)
            }
            updateActiveCues()
        }
    }

    fun adjustSubtitleOffset(deltaMs: Long) {
        val offsetMs = (_uiState.value.subtitles.offsetMs + deltaMs).coerceIn(-MaxSubtitleOffsetMs, MaxSubtitleOffsetMs)
        viewModelScope.launch { preferencesRepository.updateSubtitleOffsetMs(itemId, offsetMs) }
    }

    fun seekToPreviousLine() {
        val offsetMs = _uiState.value.subtitles.offsetMs
        previousCueStartMs(primaryCues, player.currentPosition - offsetMs)?.let { player.seekTo(it + offsetMs) }
    }

    fun seekToNextLine() {
        val offsetMs = _uiState.value.subtitles.offsetMs
        nextCueStartMs(primaryCues, player.currentPosition - offsetMs)?.let { player.seekTo(it + offsetMs) }
    }

    private fun loadSubtitles(source: WatchPlaybackSource) {
        if (subtitlesLoadedFor == source.mediaSourceId) return
        subtitlesLoadedFor = source.mediaSourceId
        updateSubtitles { it.copy(tracks = source.subtitleTracks, isLoading = source.subtitleTracks.isNotEmpty()) }
        if (source.subtitleTracks.isEmpty()) return
        viewModelScope.launch {
            val subtitles = subtitleRepository.load(itemId, source.mediaSourceId.orEmpty(), source.subtitleTracks)
            primaryCues = subtitles.japanese?.cues.orEmpty()
            englishCues = subtitles.english?.cues.orEmpty()
            updateSubtitles {
                it.copy(isLoading = false, selectedTrackIndex = subtitles.japanese?.track?.index, cues = primaryCues)
            }
            updateActiveCues()
        }
    }

    private fun updateActiveCues() {
        val positionMs = player.currentPosition - _uiState.value.subtitles.offsetMs
        val active = activeCueIndices(primaryCues, positionMs)
        val lines = active.map { primaryCues[it].text }
        val activeIndex = active.firstOrNull()
            ?: primaryCues.indexOfLast { it.startMs <= positionMs }.takeIf { it >= 0 }
        val current = _uiState.value.subtitles
        if (lines != current.primaryLines || activeIndex != current.activeCueIndex) {
            updateSubtitles { it.copy(primaryLines = lines, activeCueIndex = activeIndex) }
        }
    }

    private inline fun updateSubtitles(transform: (WatchSubtitlesUiState) -> WatchSubtitlesUiState) {
        _uiState.update { it.copy(subtitles = transform(it.subtitles)) }
    }

    private fun load() {
        _uiState.update { WatchPlayerUiState(subtitles = it.subtitles) }
        viewModelScope.launch {
            val result = playbackRepository.prepare(itemId)
            result.onSuccess { source ->
                start(source)
                loadSubtitles(source)
            }
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
        const val CueTickMs = 100L
        const val MaxSubtitleOffsetMs = 30_000L
        const val SentenceAudioPaddingMs = 250L
        const val AudioClipTimeoutMs = 60_000L
        const val AudioMissingTag = "tsukimi::audio-missing"
    }
}
