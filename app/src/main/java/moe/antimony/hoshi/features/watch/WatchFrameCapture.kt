package moe.antimony.hoshi.features.watch

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/**
 * The player handed to Media3's video frame. It notes the [SurfaceView] Media3 attaches, so a
 * frame can be copied from the video alone, without the subtitle overlay drawn above it.
 */
@OptIn(UnstableApi::class)
internal class WatchFrameCapturePlayer(player: Player) : ForwardingPlayer(player) {
    private var surfaceView: SurfaceView? = null

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        this.surfaceView = surfaceView
        super.setVideoSurfaceView(surfaceView)
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        if (this.surfaceView === surfaceView) this.surfaceView = null
        super.clearVideoSurfaceView(surfaceView)
    }

    /**
     * Starts copying the frame on screen now and completes with it, or null when there is no
     * video surface or the copy fails. Must be called on the main thread.
     */
    fun captureFrame(): Deferred<Bitmap?> {
        val result = CompletableDeferred<Bitmap?>()
        val view = surfaceView?.takeIf { it.holder.surface?.isValid == true && it.width > 0 && it.height > 0 }
            ?: return result.also { it.complete(null) }
        val (width, height) = screenshotSize(videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio, view)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(view, bitmap, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    result.complete(bitmap)
                } else {
                    Log.w(Tag, "Frame copy failed: $copyResult")
                    bitmap.recycle()
                    result.complete(null)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (error: IllegalArgumentException) {
            Log.w(Tag, "Frame copy failed: ${error.message}")
            result.complete(null)
        }
        return result
    }

    private fun screenshotSize(videoWidth: Int, videoHeight: Int, pixelRatio: Float, view: SurfaceView): Pair<Int, Int> =
        if (videoWidth > 0 && videoHeight > 0) {
            watchScreenshotSize((videoWidth * pixelRatio).roundToInt(), videoHeight)
        } else {
            watchScreenshotSize(view.width, view.height)
        }

    private companion object {
        const val Tag = "WatchFrameCapture"
    }
}

internal const val WatchScreenshotMaxWidth = 1280

/** Keeps the aspect ratio and limits the width to [WatchScreenshotMaxWidth]. */
internal fun watchScreenshotSize(width: Int, height: Int): Pair<Int, Int> {
    if (width <= WatchScreenshotMaxWidth) return width to height
    val scale = WatchScreenshotMaxWidth.toFloat() / width
    return WatchScreenshotMaxWidth to (height * scale).roundToInt().coerceAtLeast(1)
}

/** Writes [bitmap] as a JPEG into [directory] and recycles it. */
internal fun saveWatchScreenshot(bitmap: Bitmap, directory: File): File? = runCatching {
    directory.mkdirs()
    val file = File(directory, "watch_screenshot_${System.nanoTime()}.jpg")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, WatchScreenshotJpegQuality, it) }
    file
}.also { bitmap.recycle() }.getOrNull()

private const val WatchScreenshotJpegQuality = 85
