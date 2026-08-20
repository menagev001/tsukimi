package moe.antimony.hoshi.features.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeScheduler {
    private val scheduled = mutableListOf<Runnable>()

    val postDelayed: (Runnable, Long) -> Unit = { runnable, _ -> scheduled += runnable }
    val removeCallback: (Runnable) -> Unit = { runnable -> scheduled -= runnable }

    fun runPending() {
        val pending = scheduled.toList()
        scheduled.clear()
        pending.forEach { it.run() }
    }
}

class ReaderTapDispatcherTest {
    @Test
    fun singleTapFiresAfterTimeoutWhenNoSecondTapFollows() {
        val scheduler = FakeScheduler()
        var tapped: Pair<Float, Float>? = null
        var doubleTapped: Pair<Float, Float>? = null
        val dispatcher = ReaderTapDispatcher(
            doubleTapTimeoutMs = 300L,
            onTap = { x, y -> tapped = x to y },
            onDoubleTap = { x, y -> doubleTapped = x to y },
        )

        dispatcher.dispatchTap(10f, 20f, eventTimeMs = 1_000L, postDelayed = scheduler.postDelayed, removeCallback = scheduler.removeCallback)
        assertNull(tapped)
        scheduler.runPending()

        assertEquals(10f to 20f, tapped)
        assertNull(doubleTapped)
    }

    @Test
    fun secondTapWithinTimeoutAndSlopFiresDoubleTapInsteadOfSingleTap() {
        val scheduler = FakeScheduler()
        var tapCount = 0
        var doubleTapped: Pair<Float, Float>? = null
        val dispatcher = ReaderTapDispatcher(
            doubleTapTimeoutMs = 300L,
            onTap = { _, _ -> tapCount++ },
            onDoubleTap = { x, y -> doubleTapped = x to y },
        )

        dispatcher.dispatchTap(10f, 20f, eventTimeMs = 1_000L, postDelayed = scheduler.postDelayed, removeCallback = scheduler.removeCallback)
        dispatcher.dispatchTap(12f, 22f, eventTimeMs = 1_150L, postDelayed = scheduler.postDelayed, removeCallback = scheduler.removeCallback)
        scheduler.runPending()

        assertEquals(0, tapCount)
        assertEquals(12f to 22f, doubleTapped)
    }

    @Test
    fun secondTapAfterTimeoutWindowFiresAsItsOwnSingleTap() {
        val scheduler = FakeScheduler()
        var tapCount = 0
        var doubleTapped: Pair<Float, Float>? = null
        val dispatcher = ReaderTapDispatcher(
            doubleTapTimeoutMs = 300L,
            onTap = { _, _ -> tapCount++ },
            onDoubleTap = { x, y -> doubleTapped = x to y },
        )

        dispatcher.dispatchTap(10f, 20f, eventTimeMs = 1_000L, postDelayed = scheduler.postDelayed, removeCallback = scheduler.removeCallback)
        scheduler.runPending()
        dispatcher.dispatchTap(12f, 22f, eventTimeMs = 1_500L, postDelayed = scheduler.postDelayed, removeCallback = scheduler.removeCallback)
        scheduler.runPending()

        assertEquals(2, tapCount)
        assertNull(doubleTapped)
    }

    @Test
    fun secondTapOutsideSlopFiresAsItsOwnSingleTapInsteadOfDoubleTap() {
        val scheduler = FakeScheduler()
        var tapCount = 0
        var doubleTapped: Pair<Float, Float>? = null
        val dispatcher = ReaderTapDispatcher(
            doubleTapTimeoutMs = 300L,
            onTap = { _, _ -> tapCount++ },
            onDoubleTap = { x, y -> doubleTapped = x to y },
        )

        dispatcher.dispatchTap(10f, 20f, eventTimeMs = 1_000L, postDelayed = scheduler.postDelayed, removeCallback = scheduler.removeCallback)
        dispatcher.dispatchTap(400f, 500f, eventTimeMs = 1_100L, postDelayed = scheduler.postDelayed, removeCallback = scheduler.removeCallback)
        scheduler.runPending()

        assertEquals(2, tapCount)
        assertNull(doubleTapped)
    }

    @Test
    fun cancelPendingSuppressesTheBufferedSingleTap() {
        val scheduler = FakeScheduler()
        var tapCount = 0
        val dispatcher = ReaderTapDispatcher(
            doubleTapTimeoutMs = 300L,
            onTap = { _, _ -> tapCount++ },
            onDoubleTap = { _, _ -> },
        )

        dispatcher.dispatchTap(10f, 20f, eventTimeMs = 1_000L, postDelayed = scheduler.postDelayed, removeCallback = scheduler.removeCallback)
        dispatcher.cancelPending(scheduler.removeCallback)
        scheduler.runPending()

        assertEquals(0, tapCount)
    }
}
