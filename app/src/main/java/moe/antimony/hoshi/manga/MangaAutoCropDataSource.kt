package moe.antimony.hoshi.manga

import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** A downsampled greyscale copy of a page image, row-major, 0 = black and 255 = white. */
class MangaPageLuma(val width: Int, val height: Int, val luma: IntArray)

fun interface MangaPageLumaSampler {
    /** Null when the file can't be decoded. */
    fun sample(file: File): MangaPageLuma?
}

/**
 * Decodes only a small copy of the page (a power-of-two `inSampleSize` keeping it at least
 * [MinSampleHeight] tall): margin detection needs the page's layout, not its detail, and a full
 * decode of an upscaled page would be ~100x the work on an e-ink device's CPU.
 */
object AndroidMangaPageLumaSampler : MangaPageLumaSampler {
    private const val MinSampleHeight = 300

    override fun sample(file: File): MangaPageLuma? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outHeight / (sampleSize * 2) >= MinSampleHeight) sampleSize *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: return null
        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            for (i in pixels.indices) {
                val color = pixels[i]
                // Rec. 601 luma; manga is almost always greyscale anyway.
                pixels[i] = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
            }
            return MangaPageLuma(width, height, pixels)
        } finally {
            bitmap.recycle()
        }
    }
}

/**
 * Detects a volume's automatic page crop once and caches it in the volume folder, so later opens
 * of the same volume don't decode anything. Only a spread-out sample of pages is measured: the
 * result is a per-volume median, which a few dozen pages already pin down.
 */
class MangaAutoCropDataSource(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sampler: MangaPageLumaSampler = AndroidMangaPageLumaSampler,
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun loadOrDetect(
        volumeRoot: File,
        volume: MokuroVolume,
        pageFile: suspend (imgPath: String) -> File?,
    ): MangaVolumeAutoCrop = withContext(ioDispatcher) {
        val cacheFile = volumeRoot.resolve(AUTO_CROP_FILE_NAME)
        val cached = runCatching {
            json.decodeFromString(MangaVolumeAutoCrop.serializer(), cacheFile.readText())
        }.getOrNull()
        if (cached != null && cached.version == MANGA_AUTO_CROP_VERSION) return@withContext cached

        val margins = mutableMapOf<Int, MangaPageCrop>()
        for (pageIndex in samplePageIndices(volume)) {
            ensureActive()
            val page = volume.pages[pageIndex]
            val file = pageFile(page.imgPath) ?: continue
            val sample = runCatching { sampler.sample(file) }.getOrNull() ?: continue
            mangaPageContentMargins(sample.luma, sample.width, sample.height)?.let { margins[pageIndex] = it }
        }
        val detected = mangaVolumeAutoCrop(margins)
        runCatching { cacheFile.writeText(json.encodeToString(MangaVolumeAutoCrop.serializer(), detected)) }
        detected
    }

    /**
     * Evenly spread single pages, skipping the cover (usually a full-bleed colour image) and
     * two-page spreads scanned as one wide image, whose margins don't describe a single page.
     */
    internal fun samplePageIndices(volume: MokuroVolume): List<Int> {
        val candidates = volume.pages.indices.filter { index ->
            val page = volume.pages[index]
            index > 0 && page.imgWidth <= page.imgHeight
        }
        if (candidates.size <= MAX_SAMPLED_PAGES) return candidates
        return List(MAX_SAMPLED_PAGES) { i -> candidates[i * candidates.size / MAX_SAMPLED_PAGES] }
    }

    private companion object {
        const val AUTO_CROP_FILE_NAME = "autocrop.json"
        const val MAX_SAMPLED_PAGES = 48
    }
}
