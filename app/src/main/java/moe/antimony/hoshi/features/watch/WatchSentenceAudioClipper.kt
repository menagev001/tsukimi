package moe.antimony.hoshi.features.watch

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import moe.antimony.hoshi.features.sasayaki.AacAdtsCueAudioRewriter

/** A time range of a Jellyfin video to cut out as sentence audio. */
data class WatchAudioClipRequest(
    val streamUrl: String,
    val requestHeaders: Map<String, String>,
    val startMs: Long,
    val endMs: Long,
    /** Language of the audio track the user hears, so a dub is not clipped instead. */
    val audioLanguage: String?,
)

/**
 * Cuts the audio of a time range out of the original video file with Media3 Transformer, like
 * Sasayaki's audiobook clips, and writes it as AAC (ADTS) for Anki. Video is dropped, so the
 * tablet never decodes it; only the audio track is decoded and re-encoded.
 */
@OptIn(UnstableApi::class)
@Singleton
class WatchSentenceAudioClipper @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val outputRoot = File(appContext.cacheDir, "anki-media/watch")

    /** The clip file, or null when the source cannot be read or decoded. */
    suspend fun clip(request: WatchAudioClipRequest): File? {
        outputRoot.mkdirs()
        val name = "watch_sentence_${System.nanoTime()}"
        val transformerOutput = File(outputRoot, "$name.tmp.m4a")
        val output = File(outputRoot, "$name.aac")
        val startedAt = SystemClock.elapsedRealtime()
        val exported = export(request, transformerOutput)
        val result = if (exported && AacAdtsCueAudioRewriter.rewrite(input = transformerOutput, output = output)) {
            output.takeIf { it.isFile && it.length() > 0L }
        } else {
            output.delete()
            null
        }
        transformerOutput.delete()
        Log.i(
            Tag,
            "Sentence clip of ${request.endMs - request.startMs} ms " +
                "${if (result != null) "took" else "failed after"} ${SystemClock.elapsedRealtime() - startedAt} ms",
        )
        return result
    }

    private suspend fun export(request: WatchAudioClipRequest, outputFile: File): Boolean {
        val thread = HandlerThread("TsukimiSentenceClip").also { it.start() }
        val handler = Handler(thread.looper)
        try {
            return suspendCancellableCoroutine { continuation ->
                var transformer: Transformer? = null
                handler.post {
                    runCatching {
                        transformer = buildTransformer(request, thread, onDone = { success ->
                            if (continuation.isActive) continuation.resume(success)
                        })
                        transformer?.start(editedMediaItem(request), outputFile.absolutePath)
                    }.onFailure { error ->
                        Log.w(Tag, "Sentence clip could not start: ${error.message}")
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
                continuation.invokeOnCancellation { handler.post { transformer?.cancel() } }
            }
        } finally {
            thread.quitSafely()
        }
    }

    private fun buildTransformer(request: WatchAudioClipRequest, thread: HandlerThread, onDone: (Boolean) -> Unit): Transformer {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(request.requestHeaders)
            .setAllowCrossProtocolRedirects(true)
        val trackSelectorParameters = DefaultTrackSelector.Parameters.Builder()
            .setForceHighestSupportedBitrate(true)
            .setConstrainAudioChannelCountToDeviceCapabilities(false)
            .setPreferredAudioLanguage(request.audioLanguage)
            .build()
        val assetLoaderFactory = DefaultAssetLoaderFactory(
            appContext,
            DefaultDecoderFactory.Builder(appContext).build(),
            Clock.DEFAULT,
            DefaultMediaSourceFactory(dataSourceFactory),
            DataSourceBitmapLoader.Builder(appContext).build(),
            { context -> DefaultTrackSelector(context).apply { setParameters(trackSelectorParameters) } },
        )
        return Transformer.Builder(appContext)
            .setLooper(thread.looper)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setAssetLoaderFactory(assetLoaderFactory)
            .addListener(
                object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        onDone(true)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        Log.w(Tag, "Sentence clip failed: ${exportException.errorCodeName}")
                        onDone(false)
                    }
                },
            )
            .build()
    }

    private fun editedMediaItem(request: WatchAudioClipRequest): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(request.streamUrl)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(request.startMs.coerceAtLeast(0))
                    .setEndPositionMs(request.endMs.coerceAtLeast(request.startMs + 1))
                    .build(),
            )
            .build()
        return EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .build()
    }

    private companion object {
        const val Tag = "WatchSentenceAudio"
    }
}
