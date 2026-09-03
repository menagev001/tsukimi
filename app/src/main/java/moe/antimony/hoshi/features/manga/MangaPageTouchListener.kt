package moe.antimony.hoshi.features.manga

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import moe.antimony.hoshi.features.reader.SwipePageTouchListener

/**
 * Manga's touch handling has no EPUB analog for pinch-zoom/pan, but reuses
 * [SwipePageTouchListener] verbatim for tap/double-tap/swipe disambiguation (pure motion-time
 * math with no DOM coupling) rather than reimplementing that logic. Zoom/pan are layered on top:
 * a second pointer starts a pinch (swipe/tap tracking is suppressed for the gesture's duration),
 * and single-finger drag pans while already zoomed in instead of triggering a page-turn swipe.
 * Uses the platform's own double-tap timeout (~300ms) rather than the reader's tighter
 * [moe.antimony.hoshi.features.reader.READER_DOUBLE_TAP_TIMEOUT_MS]: that shorter window is tuned
 * so EPUB's single tap feels instant, but here double-tap is a primary, deliberate gesture
 * (sentence breakdown) that needs a forgiving window to register reliably.
 * Also raises the swipe velocity floor above EPUB's default: a whole manga page turn is a bigger,
 * more consequential jump than an EPUB page-turn, so an accidental slow drag/brush shouldn't be
 * able to trigger one as easily.
 */
internal abstract class MangaPageTouchListener(
    context: Context,
    swipeDistance: Float,
) : SwipePageTouchListener(
    swipeDistance = swipeDistance,
    doubleTapEnabled = true,
    doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout().toLong(),
    minSwipeVelocityPxPerSecond = MinSwipeVelocityPxPerSecond,
) {
    private var pinching = false
    private var panning = false
    private var lastPanX = 0f
    private var lastPanY = 0f
    private var panStartX = 0f
    private var panStartY = 0f

    // Set true when a pinch/two-finger-pan gesture ends while at least one finger stays down, so
    // the next single-finger event resyncs lastPanX/Y from there instead of computing a delta
    // against a stale position left over from whatever single-finger touch happened before the
    // pinch started (which otherwise produced a visible jump on lifting the second finger).
    private var pendingPanResync = false

    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                pinching = true
                lastFocusX = detector.focusX
                lastFocusY = detector.focusY
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // The focal point (finger midpoint) moving between calls -- not just the pinch
                // span changing -- is what a two-finger pan looks like; carrying that delta
                // through to onZoom lets a plain two-finger drag (no distance change at all) pan
                // the page, and a pinch that also drifts pan and zoom at once.
                val panDx = detector.focusX - lastFocusX
                val panDy = detector.focusY - lastFocusY
                lastFocusX = detector.focusX
                lastFocusY = detector.focusY
                onZoom(detector.scaleFactor, detector.focusX, detector.focusY, panDx, panDy, settling = false)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                pinching = false
                pendingPanResync = true
                onZoomGestureEnd()
            }
        },
    )

    abstract fun isZoomed(): Boolean
    abstract fun onZoom(scaleFactor: Float, focalX: Float, focalY: Float, panDx: Float, panDy: Float, settling: Boolean)
    abstract fun onZoomGestureEnd()
    abstract fun onPan(dx: Float, dy: Float, settling: Boolean)

    /**
     * Hook for a subclass to claim a touch for something else (e.g. an open dictionary popup)
     * so pan-tracking backs off and leaves it alone -- deliberately separate from
     * [shouldIgnoreReaderGesture], whose own `panning` clause would make that check
     * self-referential once a pan is already in progress (it would permanently block itself the
     * moment `panning` first became true). Without this, a swipe meant to dismiss a popup opened
     * while zoomed in was instead hijacked as page-panning, since [handlePan] never otherwise
     * knew the touch belonged to something on top of the page.
     */
    open fun shouldSuppressPan(event: MotionEvent): Boolean = false

    override fun shouldIgnoreReaderGesture(event: MotionEvent): Boolean =
        pinching ||
            event.pointerCount > 1 ||
            (panning && event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        // A second finger touching down doesn't make `pinching` true immediately --
        // ScaleGestureDetector only calls onScaleBegin once the pointers' span has moved past its
        // own recognition threshold. Routing multi-pointer events through super.onTouch (which
        // checks shouldIgnoreReaderGesture, now including `event.pointerCount > 1`) rather than
        // returning early here lets the base tracker properly suppress/reset its in-progress
        // single-finger gesture instead of leaving stale down/move state that a subsequent event
        // could resolve into an accidental swipe -- this was the source of a pinch-zoom gesture
        // occasionally being misread as a page turn right as it started.
        if (pinching || event.pointerCount > 1) {
            panning = false
            return super.onTouch(view, event)
        }
        if (isZoomed() && event.pointerCount == 1) {
            if (shouldSuppressPan(event)) {
                // Something on top of the page (a popup) wants this touch -- don't hijack it as
                // panning; let it pass through untouched, and resync on the next real pan so it
                // doesn't compute a delta against a now-stale position.
                panning = false
                pendingPanResync = true
                return false
            }
            if (pendingPanResync) {
                pendingPanResync = false
                lastPanX = event.x
                lastPanY = event.y
                panStartX = event.x
                panStartY = event.y
                panning = true
            }
            handlePan(view, event)
            return false
        }
        panning = false
        return super.onTouch(view, event)
    }

    private fun handlePan(view: View, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                panning = false
                lastPanX = event.x
                lastPanY = event.y
                panStartX = event.x
                panStartY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastPanX
                val dy = event.y - lastPanY
                if (panning || abs(event.x - panStartX) > PanStartThresholdPx || abs(event.y - panStartY) > PanStartThresholdPx) {
                    panning = true
                    onPan(dx, dy, settling = false)
                }
                lastPanX = event.x
                lastPanY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (!panning) {
                    if (!shouldIgnoreReaderGesture(event)) dispatchTapOrDoubleTap(view, event.x, event.y)
                } else {
                    onPan(0f, 0f, settling = true)
                }
                panning = false
            }
            MotionEvent.ACTION_CANCEL -> panning = false
        }
    }

    private companion object {
        const val PanStartThresholdPx = 8f
        const val MinSwipeVelocityPxPerSecond = 600f
    }
}
