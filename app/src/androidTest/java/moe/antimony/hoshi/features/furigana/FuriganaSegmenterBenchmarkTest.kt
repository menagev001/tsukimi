package moe.antimony.hoshi.features.furigana

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import moe.antimony.hoshi.dictionary.DictionaryImportDataSource
import moe.antimony.hoshi.dictionary.DictionaryLookupQueryService
import moe.antimony.hoshi.dictionary.DictionaryRepository
import moe.antimony.hoshi.dictionary.DictionaryStorageDataSource
import moe.antimony.hoshi.dictionary.HoshiDictionaryNativeBridge
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a correctness test -- a manual benchmark. Run this yourself from Android Studio
 * (right-click the test method -> Run) on a connected device/emulator that already has at least
 * one term dictionary imported and enabled -- it reuses that real dictionary data and the real
 * native lookup engine via [InstrumentationRegistry]'s target context, so the numbers reflect
 * your actual setup. With no dictionary imported, every lookup misses and the timing is
 * meaningless (though it'll still run).
 *
 * Logs to Logcat under the tag "FuriganaBenchmark".
 */
@RunWith(AndroidJUnit4::class)
class FuriganaSegmenterBenchmarkTest {
    @Test
    fun segmentsARealisticPageOfTextAndLogsTiming() {
        val filesDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val bridge = HoshiDictionaryNativeBridge()
        val storage = DictionaryStorageDataSource(filesDir)
        val repository = DictionaryRepository(
            filesDir,
            storage,
            DictionaryImportDataSource(bridge),
            DictionaryLookupQueryService(bridge),
        )

        val segmenter = FuriganaSegmenter(
            lookup = repository.asFuriganaLookup(),
            // Isolate raw segmentation cost from the (cheap, in-memory) known-word filter.
            isKnownWord = { _, _ -> false },
        )

        // Warm up first (native session init, JIT) so the timed loop reflects steady-state cost.
        segmenter.segment(SAMPLE_PAGE)

        val iterations = 20
        val start = System.nanoTime()
        var lastSpanCount = 0
        repeat(iterations) {
            lastSpanCount = segmenter.segment(SAMPLE_PAGE).size
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        val perPageMs = elapsedMs / iterations

        Log.i(
            "FuriganaBenchmark",
            "Segmented a ${SAMPLE_PAGE.length}-character page into $lastSpanCount spans: " +
                "%.2f ms/page average over %d runs (%.1f ms total).".format(perPageMs, iterations, elapsedMs),
        )

        assertTrue("Segmentation should have produced spans", lastSpanCount > 0)
    }

    private companion object {
        // A representative page-length span of prose. Swap in real chapter text (or run this
        // against several different passages) if you want numbers for your own books specifically.
        const val SAMPLE_PAGE = """
            彼女は静かに窓の外を眺めていた。夕暮れの光が部屋の中にゆっくりと差し込み、机の上に置かれた古い写真を淡く照らしている。
            もう何年も前のことなのに、あの日の記憶だけは驚くほど鮮明に残っていた。誰かが階段を上ってくる足音が聞こえて、
            彼女は慌てて写真を引き出しにしまい込んだ。ドアが開く前に、深く息を吸い込んで、いつもと変わらない表情を作ろうと努めた。
            廊下から聞こえる声は、思っていたよりもずっと穏やかで、なぜだか少しだけ肩の力が抜けるのを感じた。
            それでも心臓はまだ速く打ち続けていて、彼女は自分がどれほど緊張していたかに、今さらながら気づかされた。
        """
    }
}
