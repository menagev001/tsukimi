package moe.antimony.hoshi.manga

import kotlinx.serialization.Serializable

/**
 * How much of each edge of a page image to hide, as fractions of the image's own width (left,
 * right) and height (top, bottom). The OCR overlay is laid out in full-image coordinates, so a crop
 * only changes which part of the page is fitted to the screen -- never the overlay geometry.
 */
@Serializable
data class MangaPageCrop(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
) {
    fun clamped(maxEdge: Float): MangaPageCrop = MangaPageCrop(
        left = left.coerceIn(0f, maxEdge),
        top = top.coerceIn(0f, maxEdge),
        right = right.coerceIn(0f, maxEdge),
        bottom = bottom.coerceIn(0f, maxEdge),
    )

    companion object {
        val None = MangaPageCrop()
    }
}

/**
 * The automatically detected crop for one volume. One consistent crop per page parity rather than
 * per page, so text stays the same size from page to page; parity is kept separate because a
 * scan's wider (spine-side) margin alternates between the left and right edge of facing pages.
 */
@Serializable
data class MangaVolumeAutoCrop(
    val version: Int = MANGA_AUTO_CROP_VERSION,
    val even: MangaPageCrop = MangaPageCrop.None,
    val odd: MangaPageCrop = MangaPageCrop.None,
) {
    fun forPage(pageIndex: Int, isWide: Boolean): MangaPageCrop {
        if (isWide) {
            // A two-page spread scanned as one image has outer margins on both sides and the
            // spine in its middle, so neither parity's left/right applies; take the smaller of all.
            val side = minOf(even.left, even.right, odd.left, odd.right)
            return MangaPageCrop(
                left = side,
                top = minOf(even.top, odd.top),
                right = side,
                bottom = minOf(even.bottom, odd.bottom),
            )
        }
        return if (pageIndex % 2 == 0) even else odd
    }

    /** Starting point for manual cropping: never hides more than Auto does on either parity. */
    fun uniformCrop(): MangaPageCrop = MangaPageCrop(
        left = minOf(even.left, odd.left),
        top = minOf(even.top, odd.top),
        right = minOf(even.right, odd.right),
        bottom = minOf(even.bottom, odd.bottom),
    )
}

/** Bumped whenever the detection below changes, so cached per-volume results are recomputed. */
const val MANGA_AUTO_CROP_VERSION = 1

/** Auto never hides more than this much of any edge, so an unusual page can't be zoomed into. */
const val MANGA_AUTO_CROP_MAX_EDGE = 0.2f

// Pixels darker than this count as ink; lighter ones as paper (tolerates yellowed/grey scans).
private const val INK_LUMA_THRESHOLD = 160

// Content has to continue for this fraction of the page before it counts, which is what skips
// page numbers, scan specks and other small isolated marks sitting in the margin.
private const val MIN_CONTENT_RUN_FRACTION = 0.03f

// Of a candidate run, this share of rows/columns must contain ink (tolerates thin gaps in art).
private const val MIN_CONTENT_RUN_FILL = 0.8f

// Pulled back off the detected edge so antialiased line ends and panel borders are never clipped.
private const val SAFETY_MARGIN = 0.005f

// Fewer measurable pages than this in a parity group is too few to trust on their own.
private const val MIN_GROUP_SAMPLES = 3

/**
 * Measures the paper margin around one page's content. [luma] is a row-major greyscale image
 * (0 = black, 255 = white), typically a small downsampled copy of the page. Returns null for a
 * page with no content at all (a blank page), which tells the caller nothing about the margins.
 */
fun mangaPageContentMargins(luma: IntArray, width: Int, height: Int): MangaPageCrop? {
    if (width <= 0 || height <= 0 || luma.size < width * height) return null
    val rowHasInk = BooleanArray(height)
    val columnHasInk = BooleanArray(width)
    for (y in 0 until height) {
        val rowStart = y * width
        for (x in 0 until width) {
            if (luma[rowStart + x] < INK_LUMA_THRESHOLD) {
                rowHasInk[y] = true
                columnHasInk[x] = true
            }
        }
    }
    val left = firstContentRun(columnHasInk, fromStart = true) ?: return null
    val right = firstContentRun(columnHasInk, fromStart = false) ?: return null
    val top = firstContentRun(rowHasInk, fromStart = true) ?: return null
    val bottom = firstContentRun(rowHasInk, fromStart = false) ?: return null
    return MangaPageCrop(
        left = left.toFloat() / width,
        top = top.toFloat() / height,
        right = right.toFloat() / width,
        bottom = bottom.toFloat() / height,
    )
}

/**
 * Distance from the chosen end of [hasInk] to the first run of real content, or null when there is
 * none. A run is a window of [MIN_CONTENT_RUN_FRACTION] of the length that is mostly ink.
 */
private fun firstContentRun(hasInk: BooleanArray, fromStart: Boolean): Int? {
    val size = hasInk.size
    val window = maxOf(2, (size * MIN_CONTENT_RUN_FRACTION).toInt())
    if (window > size) return null
    val required = (window * MIN_CONTENT_RUN_FILL).toInt().coerceAtLeast(1)
    fun inkAt(distance: Int) = if (fromStart) hasInk[distance] else hasInk[size - 1 - distance]
    var count = 0
    for (distance in 0 until window) if (inkAt(distance)) count++
    for (start in 0..size - window) {
        if (start > 0) {
            if (inkAt(start - 1)) count--
            if (inkAt(start + window - 1)) count++
        }
        // The run has to *begin* with ink, so the margin ends at real content, not before it.
        if (count >= required && inkAt(start)) return start
    }
    return null
}

/**
 * Combines per-page margins (keyed by page index) into one crop per page parity. Each edge is the
 * median across that parity's pages, so pages whose art runs to the edge (full-bleed) or that are
 * mostly white don't drag the crop away from the typical page. A parity with too few measured
 * pages borrows the other parity's values, mirrored left-for-right as facing pages are.
 */
fun mangaVolumeAutoCrop(pageMargins: Map<Int, MangaPageCrop>): MangaVolumeAutoCrop {
    val even = pageMargins.filterKeys { it % 2 == 0 }.values.toList()
    val odd = pageMargins.filterKeys { it % 2 != 0 }.values.toList()
    val evenCrop = even.takeIf { it.size >= MIN_GROUP_SAMPLES }?.let(::medianCrop)
    val oddCrop = odd.takeIf { it.size >= MIN_GROUP_SAMPLES }?.let(::medianCrop)
    val fallback = (even + odd).takeIf { it.isNotEmpty() }?.let(::medianCrop) ?: MangaPageCrop.None
    return MangaVolumeAutoCrop(
        even = (evenCrop ?: oddCrop?.mirrored() ?: fallback).finalizeAuto(),
        odd = (oddCrop ?: evenCrop?.mirrored() ?: fallback).finalizeAuto(),
    )
}

private fun medianCrop(crops: List<MangaPageCrop>): MangaPageCrop = MangaPageCrop(
    left = crops.map { it.left }.median(),
    top = crops.map { it.top }.median(),
    right = crops.map { it.right }.median(),
    bottom = crops.map { it.bottom }.median(),
)

private fun List<Float>.median(): Float {
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2f
}

private fun MangaPageCrop.mirrored() = copy(left = right, right = left)

private fun MangaPageCrop.finalizeAuto() = MangaPageCrop(
    left = left - SAFETY_MARGIN,
    top = top - SAFETY_MARGIN,
    right = right - SAFETY_MARGIN,
    bottom = bottom - SAFETY_MARGIN,
).clamped(MANGA_AUTO_CROP_MAX_EDGE)
