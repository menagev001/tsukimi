package moe.antimony.hoshi.features.manga

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import moe.antimony.hoshi.features.reader.SwipePageTouchListener

/**
 * Manga's touch handling has no EPUB analog for pinch-zoom/pan, but reuses
 * [SwipePageTouchListener] verbatim for tap/double-tap/swipe disambiguation (pure motion-time
 * math with no DOM coupling) rather than reimplementing that logic. Zoom/pan are layered on top:
 * a second pointer starts a pinch (swipe/tap tracking is suppressed for the gesture's duration),
 * and single-finger drag pans while already zoomed in instead of triggering a page-turn swipe.
 */
internal abstract class MangaPageTouchListener(
    context: Context,
    swipeDistance: Float,
) : SwipePageTouchListener(swipeDistance = swipeDistance, doubleTapEnabled = true) {
    private var pinching = false
    private var panning = false
    private var lastPanX = 0f
    private var lastPanY = 0f
    private var panStartX = 0f
    private var panStartY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                pinching = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                onZoom(detector.scaleFactor, detector.focusX, detector.focusY, settling = false)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                pinching = false
                onZoomGestureEnd()
            }
        },
    )

    abstract fun isZoomed(): Boolean
    abstract fun onZoom(scaleFactor: Float, focalX: Float, focalY: Float, settling: Boolean)
    abstract fun onZoomGestureEnd()
    abstract fun onPan(dx: Float, dy: Float, settling: Boolean)

    override fun shouldIgnoreReaderGesture(event: MotionEvent): Boolean =
        pinching || (panning && event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (pinching) {
            panning = false
            return false
        }
        if (isZoomed() && event.pointerCount == 1) {
            handlePan(event)
            return false
        }
        panning = false
        return super.onTouch(view, event)
    }

    private fun handlePan(event: MotionEvent) {
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
                    onTap(event.x, event.y)
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
    }
}
