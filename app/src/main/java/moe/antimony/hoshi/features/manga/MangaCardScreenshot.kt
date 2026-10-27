package moe.antimony.hoshi.features.manga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The revealed OCR block's page image and its box in that image's pixels (`activeBlockImage` in
 * reader-manga.js). [highlights] are the mined sentence's glyph boxes, in the same pixels.
 */
@Serializable
internal data class MangaBubbleImage(
    val imgPath: String,
    val box: List<Double>,
    val imgWidth: Int,
    val imgHeight: Int,
    val highlights: List<List<Double>> = emptyList(),
)

/** A marker rectangle in the saved screenshot's pixels. */
internal data class MangaMarkerRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** A pixel rectangle; [right] and [bottom] are exclusive. */
internal data class MangaCropRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

// The crop shows the scene around the bubble: at least this share of the page, or the bubble
// with a margin when that is larger.
private const val MinCropPageWidthFraction = 0.6
private const val MinCropPageHeightFraction = 0.5
private const val BubbleMarginFraction = 0.3
internal const val MangaScreenshotMaxWidth = 1280

private val bubbleJson = Json { ignoreUnknownKeys = true }

/** Parses the `evaluateJavascript` result of `JSON.stringify(hoshiManga.activeBlockImage())`. */
internal fun mangaBubbleImageFromWebViewResult(result: String?): MangaBubbleImage? = runCatching {
    val json = bubbleJson.decodeFromString<String?>(result ?: return null) ?: return null
    bubbleJson.decodeFromString<MangaBubbleImage>(json).takeIf { it.box.size == 4 }
}.getOrNull()

/**
 * The part of the page around the bubble: at least 60 % of the page width and 50 % of its height,
 * centred on the bubble and moved inwards at the page edges.
 */
internal fun mangaBubbleCropRect(bubble: MangaBubbleImage): MangaCropRect? {
    val (x1, y1, x2, y2) = bubble.box
    val bubbleWidth = x2 - x1
    val bubbleHeight = y2 - y1
    val pageWidth = bubble.imgWidth.toDouble()
    val pageHeight = bubble.imgHeight.toDouble()
    if (bubbleWidth <= 0 || bubbleHeight <= 0 || pageWidth <= 0 || pageHeight <= 0) return null
    val margin = max(bubbleWidth, bubbleHeight) * BubbleMarginFraction
    val width = max(bubbleWidth + 2 * margin, pageWidth * MinCropPageWidthFraction).coerceAtMost(pageWidth)
    val height = max(bubbleHeight + 2 * margin, pageHeight * MinCropPageHeightFraction).coerceAtMost(pageHeight)
    val left = ((x1 + x2) / 2 - width / 2).coerceIn(0.0, pageWidth - width)
    val top = ((y1 + y2) / 2 - height / 2).coerceIn(0.0, pageHeight - height)
    return MangaCropRect(
        left = left.roundToInt(),
        top = top.roundToInt(),
        right = (left + width).roundToInt().coerceAtMost(bubble.imgWidth),
        bottom = (top + height).roundToInt().coerceAtMost(bubble.imgHeight),
    )
}

/**
 * [MangaBubbleImage.highlights] in the pixels of a bitmap decoded from [region] of the page file
 * (which is [fileScaleX] × [fileScaleY] the mokuro page size) at [sampleSize], padded a little so
 * the marker covers the glyphs like a highlighter.
 */
internal fun mangaMarkerRects(
    bubble: MangaBubbleImage,
    region: MangaCropRect,
    fileScaleX: Double,
    fileScaleY: Double,
    sampleSize: Int,
): List<MangaMarkerRect> =
    bubble.highlights.filter { it.size == 4 }.map { (x1, y1, x2, y2) ->
        val pad = minOf(x2 - x1, y2 - y1) * MarkerPaddingFraction
        MangaMarkerRect(
            left = (((x1 - pad) * fileScaleX - region.left) / sampleSize).toFloat(),
            top = (((y1 - pad) * fileScaleY - region.top) / sampleSize).toFloat(),
            right = (((x2 + pad) * fileScaleX - region.left) / sampleSize).toFloat(),
            bottom = (((y2 + pad) * fileScaleY - region.top) / sampleSize).toFloat(),
        )
    }

/** Paints [rects] like a yellow highlighter: multiplied, so dark ink stays dark. */
private fun drawMangaMarker(bitmap: Bitmap, rects: List<MangaMarkerRect>) {
    if (rects.isEmpty()) return
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MarkerColor
        xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    }
    rects.forEach { rect ->
        val radius = minOf(rect.right - rect.left, rect.bottom - rect.top) * 0.15f
        canvas.drawRoundRect(RectF(rect.left, rect.top, rect.right, rect.bottom), radius, radius, paint)
    }
}

private const val MarkerPaddingFraction = 0.08
private val MarkerColor = Color.rgb(255, 230, 64)

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
        decoder.decodeRegion(
            region,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inMutable = true
            },
        ).also { decoded ->
            val markers = mangaMarkerRects(
                bubble,
                MangaCropRect(region.left, region.top, region.right, region.bottom),
                scaleX,
                scaleY,
                sampleSize,
            )
            drawMangaMarker(decoded, markers)
        }
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
