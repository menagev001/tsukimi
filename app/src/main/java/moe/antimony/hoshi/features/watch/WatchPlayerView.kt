package moe.antimony.hoshi.features.watch

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.media3.ui.compose.state.rememberPlaybackSpeedState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlinx.coroutines.delay
import moe.antimony.hoshi.R
import moe.antimony.hoshi.content.ContentLanguageProfile
import moe.antimony.hoshi.features.ai.AiLookupState
import moe.antimony.hoshi.features.ai.AiLookupViewModel
import moe.antimony.hoshi.features.anki.AnkiCardJobChip
import moe.antimony.hoshi.features.anki.AnkiCardJobsViewModel
import moe.antimony.hoshi.features.anki.AnkiLongSentenceDialog
import moe.antimony.hoshi.features.reader.LookupPopupAiRequests
import moe.antimony.hoshi.features.reader.LookupPopupHostLayers
import moe.antimony.hoshi.features.reader.LookupPopupMineHandler
import moe.antimony.hoshi.features.reader.ReaderSelectionData
import moe.antimony.hoshi.features.reader.ReaderSettings
import moe.antimony.hoshi.features.reader.rememberLookupPopupHost

private const val ControlsAutoHideMs = 3_000L
private const val ProgressTickMs = 500L
private const val SubtitleOffsetStepMs = 100L
private const val SeekStepMs = 10_000L
private const val SeekFlashMs = 700L
private const val ListMaxWidthDp = 420f
private const val ListWidthFraction = 0.38f
private const val SubtitleVideoMarginFraction = 0.06f
private const val SubtitleInsetWithControlsDp = 120f
private val PlaybackSpeeds = listOf(0.5f, 0.75f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 2f)

@OptIn(UnstableApi::class)
@Composable
internal fun WatchPlayerView(
    itemId: String,
    title: String,
    readerSettings: ReaderSettings,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<WatchPlayerViewModel, WatchPlayerViewModel.Factory>(
        creationCallback = { factory -> factory.create(itemId) },
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val player = viewModel.player
    val playPauseState = rememberPlayPauseButtonState(player)
    val isPlaying = !playPauseState.showPlay
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionCount by remember { mutableIntStateOf(0) }
    var seekFlash by remember { mutableStateOf<WatchSeekFlash?>(null) }
    var listVisible by rememberSaveable { mutableStateOf(false) }
    val currentLines by rememberUpdatedState(uiState.subtitles.primaryLines)
    val cueTexts = remember(uiState.subtitles.cues) { uiState.subtitles.cues.map { it.text } }
    val currentCueTexts by rememberUpdatedState(cueTexts)
    val cardJobs: AnkiCardJobsViewModel = hiltViewModel()
    val framePlayer = remember(player) { WatchFrameCapturePlayer(player) }
    val newestCardJobId = cardJobs.jobs.collectAsStateWithLifecycle().value.lastOrNull()?.id
    val popupHost = rememberLookupPopupHost(
        readerSettings = readerSettings,
        darkInterface = true,
        eInkMode = false,
        contentLanguageProfile = ContentLanguageProfile.Japanese,
        documentTitle = (uiState.source as? WatchLoadState.Ready)?.value?.item?.let { it.seriesName ?: it.name } ?: title,
        rootPopupVertical = false,
        aiRequests = remember(viewModel) { WatchAiRequests(viewModel) },
        mineEntry = remember(viewModel, framePlayer, cardJobs) {
            LookupPopupMineHandler { formatId, payloadJson, context, selection, chooseSentence ->
                // Copy the frame now, before the popup closes and playback moves on.
                val frame = framePlayer.captureFrame()
                cardJobs.requestCard(viewModel.card(formatId, payloadJson, context, selection.text, frame), chooseSentence)
                true
            }
        },
        // Lines on screen first; a lookup in the line list finds its line in the whole track.
        transformSelection = { selection -> withSubtitleLineSentence(selection, currentLines + currentCueTexts) },
    )
    val aiLookupState by popupHost.aiLookupViewModel.state.collectAsStateWithLifecycle()
    val pendingLongCard by cardJobs.pendingLongCard.collectAsStateWithLifecycle()
    val lookupActive = popupHost.isOpen || aiLookupState != AiLookupState.Hidden || pendingLongCard != null
    LaunchedEffect(lookupActive) {
        viewModel.onLookupActiveChanged(lookupActive)
        if (lookupActive) controlsVisible = false
    }
    val rootLookupSentence = popupHost.lookupPopups.firstOrNull()?.state?.selection?.sentence
    LaunchedEffect(rootLookupSentence) {
        viewModel.setCardBase(rootLookupSentence)
    }
    // The card is built in the background; close the popup so playback continues meanwhile.
    LaunchedEffect(newestCardJobId) {
        if (newestCardJobId != null) popupHost.dismiss()
    }
    LaunchedEffect(seekFlash) {
        if (seekFlash != null) {
            delay(SeekFlashMs)
            seekFlash = null
        }
    }

    BackHandler(onBack = onClose)
    BackHandler(enabled = popupHost.isOpen, onBack = popupHost::dismiss)
    ImmersiveSystemBars()
    KeepScreenOn(isPlaying)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pause() }
    DisposableEffect(viewModel) { onDispose { viewModel.pause() } }
    LaunchedEffect(controlsVisible, isPlaying, interactionCount) {
        if (controlsVisible && isPlaying) {
            delay(ControlsAutoHideMs)
            controlsVisible = false
        }
    }

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val presentationState = rememberPresentationState(player)
            val videoAspectRatio = presentationState.videoSizeDp
                ?.takeIf { it.width > 0f && it.height > 0f }
                ?.let { it.width / it.height }
            ContentFrame(player = framePlayer, modifier = Modifier.fillMaxSize())
            val liveSubtitleInsetDp = subtitleBottomInsetDp(
                screenWidthDp = maxWidth.value,
                screenHeightDp = maxHeight.value,
                videoAspectRatio = videoAspectRatio,
                controlsVisible = controlsVisible,
            )
            // Lines stay put while a lookup is open: the popup's highlight is measured once, and
            // the controls hiding would otherwise slide the line away from it.
            val lookupSubtitleInsetDp = remember(lookupActive) { liveSubtitleInsetDp }
            val hasLines = uiState.subtitles.selectedTrackIndex != null
            WatchSubtitleOverlay(
                popupHost = popupHost,
                primaryLines = uiState.subtitles.primaryLines,
                cues = uiState.subtitles.cues,
                activeCueIndex = uiState.subtitles.activeCueIndex,
                listVisible = listVisible && hasLines,
                listAvailable = hasLines,
                listWidth = minOf(ListMaxWidthDp, maxWidth.value * ListWidthFraction).dp,
                onSeekToCue = viewModel::seekToCue,
                onListVisibleChange = { listVisible = it },
                fontSizePx = uiState.subtitles.fontSizePx,
                bottomInset = (if (lookupActive) lookupSubtitleInsetDp else liveSubtitleInsetDp).dp,
                onBlankTap = { controlsVisible = !controlsVisible },
                onSideDoubleTap = { forward ->
                    viewModel.seekBy(if (forward) SeekStepMs else -SeekStepMs)
                    seekFlash = seekFlash.next(forward)
                },
            )

            when (val source = uiState.source) {
                WatchLoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is WatchLoadState.Failed -> WatchLoadFailureMessage(
                    failure = source.failure,
                    onRetry = viewModel::retry,
                    onOpenSettings = null,
                )
                is WatchLoadState.Ready -> when {
                    uiState.hasPlaybackError -> WatchCenteredMessage(
                        message = stringResource(R.string.watch_player_error),
                        primaryAction = stringResource(R.string.action_retry) to viewModel::retry,
                    )
                    uiState.isBuffering -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }

            seekFlash?.let { flash ->
                WatchSeekFlashBadge(flash, Modifier.align(if (flash.forward) Alignment.CenterEnd else Alignment.CenterStart))
            }

            val ready = uiState.source as? WatchLoadState.Ready
            AnimatedVisibility(
                visible = controlsVisible || ready == null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                WatchPlayerControls(
                    player = player,
                    title = ready?.value?.item?.let { it.seriesName ?: it.name } ?: title,
                    subtitle = ready?.value?.item?.let { watchItemSubtitle(it) },
                    showTransport = ready != null && !uiState.hasPlaybackError,
                    audioTracks = uiState.audioTracks,
                    onSelectAudioTrack = viewModel::selectAudioTrack,
                    subtitles = uiState.subtitles,
                    onSelectSubtitleTrack = viewModel::selectSubtitleTrack,
                    onAdjustSubtitleOffset = viewModel::adjustSubtitleOffset,
                    onPreviousLine = viewModel::seekToPreviousLine,
                    onNextLine = viewModel::seekToNextLine,
                    listVisible = listVisible,
                    onToggleList = { listVisible = !listVisible },
                    isPlaying = isPlaying,
                    onPlayPause = playPauseState::onClick,
                    onInteraction = { interactionCount++ },
                    onClose = onClose,
                )
            }

            val cardSelection = uiState.cardSelection
            if (popupHost.isOpen && cardSelection != null) {
                WatchCardLinesBar(
                    selection = cardSelection,
                    onAddPrevious = { viewModel.extendCardSelection(previous = true) },
                    onAddNext = { viewModel.extendCardSelection(previous = false) },
                    onReset = viewModel::resetCardSelection,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .safeDrawingPadding()
                        .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                )
            }

            AnkiLongSentenceDialog(cardJobs)

            AnkiCardJobChip(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 64.dp),
            )

            LookupPopupHostLayers(
                host = popupHost,
                viewportWidthDp = maxWidth.value.toDouble(),
                viewportHeightDp = maxHeight.value.toDouble(),
                darkInterface = true,
                eInkMode = false,
                verticalWriting = false,
            )
        }
    }
}

/** The sentence a card will use, with buttons to add the line before or after it. */
@Composable
private fun WatchCardLinesBar(
    selection: WatchCardSelection,
    onAddPrevious: () -> Unit,
    onAddNext: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.75f),
        contentColor = Color.White,
        modifier = modifier.widthIn(max = 900.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            TextButton(onClick = onAddPrevious, enabled = selection.canAddPrevious) {
                Text(stringResource(R.string.watch_card_add_previous_line))
            }
            Text(
                text = selection.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(vertical = 8.dp),
            )
            TextButton(onClick = onAddNext, enabled = selection.canAddNext) {
                Text(stringResource(R.string.watch_card_add_next_line))
            }
            if (selection.isMerged) {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.action_reset))
                }
            }
        }
    }
}

/** AI lookups from subtitles carry the two lines before and after the tapped line. */
private class WatchAiRequests(private val viewModel: WatchPlayerViewModel) : LookupPopupAiRequests {
    override fun askWordNuance(ai: AiLookupViewModel, word: String, selection: ReaderSelectionData) {
        val (sentence, context) = viewModel.aiRequestFor(selection.sentence)
        ai.askWordNuance(word = word, sentence = sentence, context = context)
    }

    override fun requestSentenceBreakdown(ai: AiLookupViewModel, selection: ReaderSelectionData) {
        val (sentence, context) = viewModel.aiRequestFor(selection.sentence)
        ai.requestSentenceBreakdown(sentence, context)
    }
}

/** The "−10 s" / "+10 s" badge after a side double tap; repeated taps on one side add up. */
internal data class WatchSeekFlash(val forward: Boolean, val seconds: Int)

private fun WatchSeekFlash?.next(forward: Boolean): WatchSeekFlash {
    val stepSeconds = (SeekStepMs / 1000).toInt()
    return if (this != null && this.forward == forward) {
        copy(seconds = seconds + stepSeconds)
    } else {
        WatchSeekFlash(forward = forward, seconds = stepSeconds)
    }
}

@Composable
private fun WatchSeekFlashBadge(flash: WatchSeekFlash, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(horizontal = 48.dp)
            .size(112.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
    ) {
        Text(
            text = stringResource(
                if (flash.forward) R.string.watch_seek_forward_format else R.string.watch_seek_back_format,
                flash.seconds,
            ),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun WatchPlayerControls(
    player: Player,
    title: String,
    subtitle: String?,
    showTransport: Boolean,
    audioTracks: List<WatchAudioTrackOption>,
    onSelectAudioTrack: (WatchAudioTrackOption) -> Unit,
    subtitles: WatchSubtitlesUiState,
    onSelectSubtitleTrack: (WatchSubtitleTrack?) -> Unit,
    onAdjustSubtitleOffset: (Long) -> Unit,
    onPreviousLine: () -> Unit,
    onNextLine: () -> Unit,
    listVisible: Boolean,
    onToggleList: () -> Unit,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onInteraction: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.6f),
                    0.25f to Color.Transparent,
                    0.75f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.6f),
                ),
            )
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (showTransport && subtitles.selectedTrackIndex != null) {
                IconButton(onClick = { onToggleList(); onInteraction() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.List,
                        contentDescription = stringResource(
                            if (listVisible) R.string.watch_player_hide_line_list else R.string.watch_player_show_line_list,
                        ),
                    )
                }
            }
            if (showTransport && subtitles.tracks.isNotEmpty()) {
                WatchSubtitleButton(
                    subtitles = subtitles,
                    onSelect = onSelectSubtitleTrack,
                    onAdjustOffset = onAdjustSubtitleOffset,
                    onInteraction = onInteraction,
                )
            }
            if (showTransport && audioTracks.size > 1) {
                WatchAudioTrackButton(
                    tracks = audioTracks,
                    onSelect = onSelectAudioTrack,
                    onInteraction = onInteraction,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (showTransport) {
            val hasLines = subtitles.selectedTrackIndex != null
            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (hasLines) {
                    IconButton(onClick = { onPreviousLine(); onInteraction() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.watch_player_previous_line), Modifier.size(36.dp))
                    }
                }
                IconButton(onClick = { player.seekBack(); onInteraction() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.Replay10, stringResource(R.string.watch_player_rewind), Modifier.size(40.dp))
                }
                IconButton(onClick = { onPlayPause(); onInteraction() }, modifier = Modifier.size(80.dp)) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.watch_player_pause else R.string.watch_player_play),
                        modifier = Modifier.size(56.dp),
                    )
                }
                IconButton(onClick = { player.seekForward(); onInteraction() }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Rounded.Forward10, stringResource(R.string.watch_player_forward), Modifier.size(40.dp))
                }
                if (hasLines) {
                    IconButton(onClick = { onNextLine(); onInteraction() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.SkipNext, stringResource(R.string.watch_player_next_line), Modifier.size(36.dp))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (showTransport) {
            WatchPlayerTimeline(player = player, onInteraction = onInteraction)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun WatchPlayerTimeline(
    player: Player,
    onInteraction: () -> Unit,
) {
    val progress = rememberProgressStateWithTickInterval(player, ProgressTickMs)
    val speedState = rememberPlaybackSpeedState(player)
    var dragPositionMs by remember { mutableStateOf<Long?>(null) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    val durationMs = progress.durationMs.coerceAtLeast(0)
    val positionMs = dragPositionMs ?: progress.currentPositionMs

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(formatPlaybackTime(positionMs), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
            onValueChange = { fraction ->
                dragPositionMs = (fraction * durationMs).toLong()
                onInteraction()
            },
            onValueChangeFinished = {
                dragPositionMs?.let(player::seekTo)
                dragPositionMs = null
            },
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Text(formatPlaybackTime(durationMs), style = MaterialTheme.typography.labelLarge)
        Box {
            TextButton(onClick = { speedMenuOpen = true; onInteraction() }) {
                Text(
                    text = stringResource(R.string.watch_player_speed_format, formatPlaybackSpeed(speedState.playbackSpeed)),
                    color = Color.White,
                )
            }
            DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                PlaybackSpeeds.forEach { speed ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.watch_player_speed_format, formatPlaybackSpeed(speed))) },
                        onClick = {
                            speedState.updatePlaybackSpeed(speed)
                            speedMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchSubtitleButton(
    subtitles: WatchSubtitlesUiState,
    onSelect: (WatchSubtitleTrack?) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onInteraction: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true; onInteraction() }) {
            Icon(Icons.Rounded.Subtitles, contentDescription = stringResource(R.string.watch_player_subtitles))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.watch_subtitles_off)) },
                trailingIcon = { if (subtitles.selectedTrackIndex == null) Icon(Icons.Rounded.Check, contentDescription = null) },
                onClick = {
                    onSelect(null)
                    menuOpen = false
                },
            )
            subtitles.tracks.forEach { track ->
                DropdownMenuItem(
                    text = { Text(track.label) },
                    trailingIcon = {
                        if (track.index == subtitles.selectedTrackIndex) Icon(Icons.Rounded.Check, contentDescription = null)
                    },
                    onClick = {
                        onSelect(track)
                        menuOpen = false
                    },
                )
            }
            if (subtitles.selectedTrackIndex != null) {
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(stringResource(R.string.watch_subtitle_timing), modifier = Modifier.weight(1f, fill = false))
                    IconButton(onClick = { onAdjustOffset(-SubtitleOffsetStepMs) }) {
                        Icon(Icons.Rounded.Remove, stringResource(R.string.watch_subtitle_earlier))
                    }
                    Text(
                        stringResource(R.string.watch_subtitle_offset_format, formatSubtitleOffset(subtitles.offsetMs)),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconButton(onClick = { onAdjustOffset(SubtitleOffsetStepMs) }) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.watch_subtitle_later))
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchAudioTrackButton(
    tracks: List<WatchAudioTrackOption>,
    onSelect: (WatchAudioTrackOption) -> Unit,
    onInteraction: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true; onInteraction() }) {
            Icon(Icons.Rounded.Audiotrack, contentDescription = stringResource(R.string.watch_player_audio_track))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            tracks.forEach { track ->
                DropdownMenuItem(
                    text = { Text(track.label) },
                    trailingIcon = {
                        if (track.isSelected) Icon(Icons.Rounded.Check, contentDescription = null)
                    },
                    onClick = {
                        onSelect(track)
                        menuOpen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ImmersiveSystemBars() {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(context, view) {
        val controller = context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            if (previousBehavior != null) controller.systemBarsBehavior = previousBehavior
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** "m:ss", or "h:mm:ss" from one hour on. */
internal fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/**
 * Distance from the bottom of the screen to the lowest subtitle line: a little above the bottom
 * edge of the picture (not of the screen, which may show letterbox bars), and above the
 * timeline while the controls are visible.
 */
internal fun subtitleBottomInsetDp(
    screenWidthDp: Float,
    screenHeightDp: Float,
    videoAspectRatio: Float?,
    controlsVisible: Boolean,
): Float {
    val videoHeightDp = videoAspectRatio?.let { minOf(screenHeightDp, screenWidthDp / it) } ?: screenHeightDp
    val letterboxDp = (screenHeightDp - videoHeightDp) / 2f
    val insetDp = letterboxDp + videoHeightDp * SubtitleVideoMarginFraction
    return if (controlsVisible) maxOf(insetDp, SubtitleInsetWithControlsDp) else insetDp
}

/** "+0.3", "-1.2", "0.0": signed seconds with one decimal. */
internal fun formatSubtitleOffset(offsetMs: Long): String {
    val tenths = Math.round(offsetMs / 100.0)
    val sign = when {
        tenths > 0 -> "+"
        tenths < 0 -> "-"
        else -> ""
    }
    val absolute = kotlin.math.abs(tenths)
    return "$sign${absolute / 10}.${absolute % 10}"
}

/** "1", "1.25", "0.9": no trailing zeros. */
internal fun formatPlaybackSpeed(speed: Float): String =
    BigDecimal(speed.toDouble()).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
