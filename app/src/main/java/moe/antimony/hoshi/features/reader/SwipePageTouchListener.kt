package moe.antimony.hoshi.features.reader

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Shorter than [android.view.ViewConfiguration.getDoubleTapTimeout()] (~400ms):
 * that system default makes every single tap feel sluggish since it always
 * waits out the window before opening the lookup popup. This keeps double-tap
 * detection snappy for single taps at the cost of a tighter double-tap window.
 */
internal const val READER_DOUBLE_TAP_TIMEOUT_MS = 180L

abstract class SwipePageTouchListener(
    swipeDistance: Float = DEFAULT_SWIPE_DISTANCE,
    private val doubleTapEnabled: Boolean = false,
    doubleTapTimeoutMs: Long = READER_DOUBLE_TAP_TIMEOUT_MS,
    minSwipeVelocityPxPerSecond: Float = ReaderSwipeGestureTracker.DEFAULT_MIN_EARLY_SWIPE_VELOCITY_PX_PER_SECOND,
) : View.OnTouchListener {
    private val tracker = ReaderSwipeGestureTracker(
        minDistance = swipeDistance,
        minEarlySwipeVelocityPxPerSecond = minSwipeVelocityPxPerSecond,
    )
    private val tapDispatcher = ReaderTapDispatcher(
        doubleTapTimeoutMs = doubleTapTimeoutMs,
        onTap = ::onTap,
        onDoubleTap = ::onDoubleTap,
    )

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        if (shouldIgnoreReaderGesture(event)) {
            tracker.suppressCurrentGesture()
            tapDispatcher.cancelPending(view::removeCallbacks)
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> tracker.onDown(event.x, event.y, event.eventTime)
            MotionEvent.ACTION_POINTER_DOWN -> tracker.onAdditionalPointerDown()
            MotionEvent.ACTION_MOVE -> dispatch(view, tracker.onMove(event.x, event.y, event.eventTime))
            MotionEvent.ACTION_UP -> dispatch(view, tracker.onUp(event.x, event.y, event.eventTime))
            MotionEvent.ACTION_CANCEL -> tracker.onCancel()
        }
        return false
    }

    open fun onLeftSwipe() = Unit
    open fun onRightSwipe() = Unit
    open fun onTap(x: Float, y: Float) = Unit
    open fun onDoubleTap(x: Float, y: Float) = Unit
    open fun shouldIgnoreReaderGesture(event: MotionEvent): Boolean = false

    /**
     * Exposed so a subclass with its own alternate release path for a stationary touch (manga's
     * pan-mode tap, taken when zoomed instead of going through [tracker]/[dispatch] at all) can
     * still resolve through the same double-tap-aware dispatch -- calling [onTap] directly there
     * bypassed [tapDispatcher] entirely, so a double-tap could never be recognized while zoomed.
     */
    protected fun dispatchTapOrDoubleTap(view: View, x: Float, y: Float) {
        if (doubleTapEnabled) {
            tapDispatcher.dispatchTap(
                x = x,
                y = y,
                eventTimeMs = SystemClock.uptimeMillis(),
                postDelayed = view::postDelayed,
                removeCallback = view::removeCallbacks,
            )
        } else {
            onTap(x, y)
        }
    }

    private fun dispatch(view: View, result: ReaderSwipeGestureTracker.Result) {
        when (result) {
            ReaderSwipeGestureTracker.Result.LeftSwipe -> onLeftSwipe()
            ReaderSwipeGestureTracker.Result.RightSwipe -> onRightSwipe()
            is ReaderSwipeGestureTracker.Result.Tap -> dispatchTapOrDoubleTap(view, result.x, result.y)
            ReaderSwipeGestureTracker.Result.None -> Unit
        }
    }

    private companion object {
        const val DEFAULT_SWIPE_DISTANCE = 72f
    }
}

internal class ReaderSwipeGestureTracker(
    private val minDistance: Float,
    private val minEarlySwipeVelocityPxPerSecond: Float = DEFAULT_MIN_EARLY_SWIPE_VELOCITY_PX_PER_SECOND,
) {
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var hasDown = false
    private var swipeDispatched = false

    fun onDown(x: Float, y: Float, eventTime: Long) {
        downX = x
        downY = y
        downTime = eventTime
        hasDown = true
        swipeDispatched = false
    }

    fun onMove(x: Float, y: Float, eventTime: Long): Result {
        if (!hasDown || swipeDispatched || minDistance <= 0f) return Result.None
        val dx = x - downX
        val dy = y - downY
        val elapsedMs = (eventTime - downTime).coerceAtLeast(1L)
        val velocityX = abs(dx) * 1_000f / elapsedMs
        val hasPageDistance = abs(dx) >= minDistance
        val hasFastFlickDistance = abs(dx) >= minDistance / 2f &&
            velocityX >= MIN_FAST_FLICK_VELOCITY_PX_PER_SECOND
        if (
            !hasPageDistance && !hasFastFlickDistance ||
            elapsedMs > MAX_EARLY_SWIPE_DURATION_MS ||
            velocityX < minEarlySwipeVelocityPxPerSecond
        ) {
            return Result.None
        }
        swipeDispatched = true
        return if (dx < 0f) Result.LeftSwipe else Result.RightSwipe
    }

    fun onUp(x: Float, y: Float, eventTime: Long): Result {
        if (!hasDown) return Result.None
        val dx = x - downX
        val dy = y - downY
        val elapsedMs = eventTime - downTime
        val wasSwipeDispatched = swipeDispatched
        onCancel()
        return if (
            !wasSwipeDispatched &&
            elapsedMs <= MAX_TAP_DURATION_MS &&
            abs(dx) < TAP_SLOP &&
            abs(dy) < TAP_SLOP
        ) {
            Result.Tap(x, y)
        } else {
            Result.None
        }
    }

    fun onCancel() {
        hasDown = false
        swipeDispatched = false
    }

    fun suppressCurrentGesture() {
        onCancel()
    }

    fun onAdditionalPointerDown() {
        suppressCurrentGesture()
    }

    sealed class Result {
        data object None : Result()
        data object LeftSwipe : Result()
        data object RightSwipe : Result()
        data class Tap(val x: Float, val y: Float) : Result()
    }

    companion object {
        const val DEFAULT_MIN_EARLY_SWIPE_VELOCITY_PX_PER_SECOND = 360f
        private const val TAP_SLOP = 72f
        private const val MIN_FAST_FLICK_VELOCITY_PX_PER_SECOND = 900f
        private const val MAX_EARLY_SWIPE_DURATION_MS = 300L
        private const val MAX_TAP_DURATION_MS = 500L
    }
}

/**
 * Buffers a tap for [doubleTapTimeoutMs] to detect a following second tap at
 * roughly the same position, matching Android's standard double-tap window.
 */
internal class ReaderTapDispatcher(
    private val doubleTapTimeoutMs: Long,
    private val onTap: (Float, Float) -> Unit,
    private val onDoubleTap: (Float, Float) -> Unit,
) {
    private var pendingTap: Runnable? = null
    private var pendingX = 0f
    private var pendingY = 0f
    private var pendingTime = 0L

    fun dispatchTap(
        x: Float,
        y: Float,
        eventTimeMs: Long,
        postDelayed: (Runnable, Long) -> Unit,
        removeCallback: (Runnable) -> Unit,
    ) {
        val pending = pendingTap
        if (
            pending != null &&
            eventTimeMs - pendingTime <= doubleTapTimeoutMs &&
            abs(x - pendingX) <= DOUBLE_TAP_SLOP &&
            abs(y - pendingY) <= DOUBLE_TAP_SLOP
        ) {
            removeCallback(pending)
            pendingTap = null
            onDoubleTap(x, y)
            return
        }
        // A non-matching pending tap (different position, or its window already
        // elapsed) is left running so it still fires as its own single tap; only
        // this new tap becomes the one a following tap can pair with.
        lateinit var runnable: Runnable
        runnable = Runnable {
            if (pendingTap === runnable) pendingTap = null
            onTap(x, y)
        }
        pendingTap = runnable
        pendingX = x
        pendingY = y
        pendingTime = eventTimeMs
        postDelayed(runnable, doubleTapTimeoutMs)
    }

    fun cancelPending(removeCallback: (Runnable) -> Unit) {
        pendingTap?.let(removeCallback)
        pendingTap = null
    }

    private companion object {
        const val DOUBLE_TAP_SLOP = 100f
    }
}
