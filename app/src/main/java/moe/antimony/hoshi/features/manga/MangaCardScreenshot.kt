package moe.antimony.hoshi.features.manga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The revealed OCR block's page image and its box in that image's pixels (`activeBlockImage` in reader-manga.js). */
@Serializable
internal data class MangaBubbleImage(
    val imgPath: String,
    val box: List<Double>,
    val imgWidth: Int,
    val imgHeight: Int,
)

/** A pixel rectangle; [right] and [bottom] are exclusive. */
internal data class MangaCropRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

private const val BubbleMarginFraction = 0.3
private const val MinBubbleMarginPx = 48.0
internal const val MangaScreenshotMaxWidth = 1280

private val bubbleJson = Json { ignoreUnknownKeys = true }

/** Parses the `evaluateJavascript` result of `JSON.stringify(hoshiManga.activeBlockImage())`. */
internal fun mangaBubbleImageFromWebViewResult(result: String?): MangaBubbleImage? = runCatching {
    val json = bubbleJson.decodeFromString<String?>(result ?: return null) ?: return null
    bubbleJson.decodeFromString<MangaBubbleImage>(json).takeIf { it.box.size == 4 }
}.getOrNull()

/** The bubble with some of the panel around it, inside the page. */
internal fun mangaBubbleCropRect(bubble: MangaBubbleImage): MangaCropRect? {
    val (x1, y1, x2, y2) = bubble.box
    val width = x2 - x1
    val height = y2 - y1
    if (width <= 0 || height <= 0 || bubble.imgWidth <= 0 || bubble.imgHeight <= 0) return null
    val margin = max(max(width, height) * BubbleMarginFraction, MinBubbleMarginPx)
    return MangaCropRect(
        left = (x1 - margin).roundToInt().coerceIn(0, bubble.imgWidth - 1),
        top = (y1 - margin).roundToInt().coerceIn(0, bubble.imgHeight - 1),
        right = (x2 + margin).roundToInt().coerceIn(1, bubble.imgWidth),
        bottom = (y2 + margin).roundToInt().coerceIn(1, bubble.imgHeight),
    ).takeIf { it.right > it.left && it.bottom > it.top }
}

/** Cuts the bubble out of [pageFile] as a JPEG in [directory], at most [MangaScreenshotMaxWidth] wide. */
internal fun saveMangaBubbleScreenshot(pageFile: File, bubble: MangaBubbleImage, directory: File): File? = runCatching {
    val crop = mangaBubbleCropRect(bubble) ?: return null
    val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        BitmapRegionDecoder.newInstance(pageFile.path)
    } else {
        @Suppress("DEPRECATION")
        BitmapRegionDecoder.newInstance(pageFile.path, false)
    }
    val bitmap = try {
        // The box is in mokuro's page size; scale it to the image file's actual size.
        val scaleX = decoder.width.toDouble() / bubble.imgWidth
        val scaleY = decoder.height.toDouble() / bubble.imgHeight
        val region = Rect(
            (crop.left * scaleX).roundToInt(),
            (crop.top * scaleY).roundToInt(),
            (crop.right * scaleX).roundToInt().coerceAtMost(decoder.width),
            (crop.bottom * scaleY).roundToInt().coerceAtMost(decoder.height),
        )
        var sampleSize = 1
        while (region.width() / (sampleSize * 2) >= MangaScreenshotMaxWidth) sampleSize *= 2
        decoder.decodeRegion(region, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    } finally {
        decoder.recycle()
    }
    val scaled = if (bitmap.width > MangaScreenshotMaxWidth) {
        val height = (bitmap.height * MangaScreenshotMaxWidth.toDouble() / bitmap.width).roundToInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(bitmap, MangaScreenshotMaxWidth, height, true).also { bitmap.recycle() }
    } else {
        bitmap
    }
    directory.mkdirs()
    val file = File(directory, "manga_bubble_${System.nanoTime()}.jpg")
    file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, MangaScreenshotJpegQuality, it) }
    scaled.recycle()
    file
}.getOrNull()

private const val MangaScreenshotJpegQuality = 85
