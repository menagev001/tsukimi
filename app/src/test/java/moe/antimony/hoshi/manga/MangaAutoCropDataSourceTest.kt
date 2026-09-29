package moe.antimony.hoshi.manga

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaAutoCropDataSourceTest {
    @Test
    fun detectsOnceThenReusesTheCachedResult() = runBlocking {
        val root = Files.createTempDirectory("hoshi-manga-autocrop").toFile()
        val sampled = mutableListOf<String>()
        val dataSource = MangaAutoCropDataSource(Dispatchers.Unconfined) { file ->
            sampled += file.name
            borderedPage()
        }
        val volume = volume(pageCount = 10)

        val first = dataSource.loadOrDetect(root, volume) { File(it) }
        val sampledAfterFirst = sampled.size
        val second = dataSource.loadOrDetect(root, volume) { File(it) }

        assertTrue(sampledAfterFirst > 0)
        assertEquals(sampledAfterFirst, sampled.size)
        assertEquals(first, second)
        assertEquals(0.1f - 0.005f, first.even.left, 1e-4f)
    }

    @Test
    fun sampleSkipsTheCoverAndWideSpreads() {
        val dataSource = MangaAutoCropDataSource(Dispatchers.Unconfined) { null }
        val pages = List(6) { index ->
            val wide = index == 3
            MokuroPage(imgWidth = if (wide) 200 else 100, imgHeight = 150, imgPath = "$index.webp", blocks = emptyList())
        }

        val indices = dataSource.samplePageIndices(MokuroVolume(pages = pages))

        assertEquals(listOf(1, 2, 4, 5), indices)
    }

    @Test
    fun sampleIsCappedAndSpreadAcrossTheVolume() {
        val dataSource = MangaAutoCropDataSource(Dispatchers.Unconfined) { null }

        val indices = dataSource.samplePageIndices(volume(pageCount = 300))

        assertEquals(48, indices.size)
        assertEquals(1, indices.first())
        assertTrue(indices.last() > 280)
    }

    @Test
    fun undecodablePagesYieldNoCrop() = runBlocking {
        val root = Files.createTempDirectory("hoshi-manga-autocrop-undecodable").toFile()
        val dataSource = MangaAutoCropDataSource(Dispatchers.Unconfined) { null }

        val result = dataSource.loadOrDetect(root, volume(pageCount = 10)) { File(it) }

        assertEquals(MangaPageCrop.None, result.even)
        assertEquals(MangaPageCrop.None, result.odd)
    }

    private fun volume(pageCount: Int) = MokuroVolume(
        pages = List(pageCount) { MokuroPage(imgWidth = 100, imgHeight = 150, imgPath = "$it.webp", blocks = emptyList()) },
    )

    // 10% blank border on every side.
    private fun borderedPage(): MangaPageLuma {
        val width = 100
        val height = 100
        val luma = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            if (x in 10 until 90 && y in 10 until 90) 0 else 255
        }
        return MangaPageLuma(width, height, luma)
    }
}
