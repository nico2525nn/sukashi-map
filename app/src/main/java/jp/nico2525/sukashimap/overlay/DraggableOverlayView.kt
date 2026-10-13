package jp.nico2525.sukashimap.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

data class OverlayItem(
    val id: String,
    val bitmap: Bitmap,
    var x: Float, // photo-space coords
    var y: Float,
    var scale: Float,
    val scalable: Boolean = true,
)

enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, NONE }

/**
 * Drag / pinch-scale overlay bitmaps over the captured photo preview.
 * Manipulation can be gated (Plus / rewarded ad) via [manipulationGate].
 */
class DraggableOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val items = mutableListOf<OverlayItem>()
    private var selected: OverlayItem? = null
    private var selectedCorner: Corner = Corner.NONE

    /** Photo dimensions rendered into this view (fit-center). */
    var photoWidth: Float = 0f
    var photoHeight: Float = 0f

    /** Return true to allow drag/scale; false triggers [onManipulationBlocked]. */
    var manipulationGate: (() -> Boolean)? = null
    var onManipulationBlocked: (() -> Unit)? = null
    var onInternalMapManipulationEnded: (() -> Unit)? = null

    private var lastX = 0f
    private var lastY = 0f
    private var pinchStart = 0f
    private var pinchScale = 1f
    private var pinchCx = 0f
    private var pinchCy = 0f
    private var moving = false
    private var scaling = false

    private val handleR = 48f
    private val framePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        setShadowLayer(4f, 0f, 0f, Color.argb(128, 0, 0, 0))
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(6f, 0f, 0f, Color.argb(128, 0, 0, 0))
    }
    private val handleRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#43A047")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()

    fun setOverlayItems(list: List<OverlayItem>) {
        items.clear()
        items.addAll(list)
        selected = null
        snapWatermark()
        invalidate()
    }

    fun getOverlayItems(): List<OverlayItem> = items.toList()

    private fun viewTransform(): Triple<Float, Float, Float> {
        if (photoWidth <= 0 || photoHeight <= 0 || width == 0 || height == 0) {
            return Triple(1f, 0f, 0f)
        }
        val s = minOf(width / photoWidth, height / photoHeight)
        return Triple(s, (width - photoWidth * s) / 2f, (height - photoHeight * s) / 2f)
    }

    private fun itemRect(it: OverlayItem): RectF =
        RectF(it.x, it.y, it.x + it.bitmap.width * it.scale, it.y + it.bitmap.height * it.scale)

    private fun toView(r: RectF): RectF {
        val (s, ox, oy) = viewTransform()
        return RectF(r.left * s + ox, r.top * s + oy, r.right * s + ox, r.bottom * s + oy)
    }

    private fun toPhoto(x: Float, y: Float): Pair<Float, Float> {
        val (s, ox, oy) = viewTransform()
        return Pair((x - ox) / s, (y - oy) / s)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val (s, ox, oy) = viewTransform()
        for (it in items) {
            matrix.reset()
            matrix.postScale(it.scale * s, it.scale * s)
            matrix.postTranslate(it.x * s + ox, it.y * s + oy)
            canvas.drawBitmap(it.bitmap, matrix, bitmapPaint)
        }
        selected?.let { drawSelection(canvas, it) }
    }

    private fun drawSelection(canvas: Canvas, it: OverlayItem) {
        val r = toView(itemRect(it))
        canvas.drawRect(r, framePaint)
        val corners = listOf(
            Corner.TOP_LEFT to Pair(r.left, r.top),
            Corner.TOP_RIGHT to Pair(r.right, r.top),
            Corner.BOTTOM_LEFT to Pair(r.left, r.bottom),
            Corner.BOTTOM_RIGHT to Pair(r.right, r.bottom),
        )
        for ((_, p) in corners) {
            canvas.drawCircle(p.first, p.second, 24f, handlePaint)
            canvas.drawCircle(p.first, p.second, 24f, handleRingPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                moving = false
                val hit = hitTest(event.x, event.y)
                selected = hit?.first
                selectedCorner = hit?.second ?: Corner.NONE
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2 && selected != null) {
                    pinchStart = pinchDist(event)
                    pinchScale = selected!!.scale
                    pinchCx = (event.getX(0) + event.getX(1)) / 2f
                    pinchCy = (event.getY(0) + event.getY(1)) / 2f
                    scaling = true
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val sel = selected ?: return true
                if (scaling && event.pointerCount >= 2) {
                    if (!allowManipulation()) {
                        scaling = false
                        return true
                    }
                    val d = pinchDist(event)
                    if (pinchStart > 0 && sel.scalable) {
                        sel.scale = (pinchScale * d / pinchStart).coerceIn(0.2f, 5f)
                        clamp(sel)
                        invalidate()
                    }
                    return true
                }
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (!moving && hypot(dx, dy) < 8f) return true
                if (!allowManipulation()) return true
                moving = true
                val (s) = viewTransform()
                if (selectedCorner != Corner.NONE && sel.scalable) {
                    // corner drag = scale around opposite corner
                    val r = itemRect(sel)
                    val (px, py) = toPhoto(event.x, event.y)
                    val (ax, ay) = when (selectedCorner) {
                        Corner.TOP_LEFT -> Pair(r.right, r.bottom)
                        Corner.TOP_RIGHT -> Pair(r.left, r.bottom)
                        Corner.BOTTOM_LEFT -> Pair(r.right, r.top)
                        Corner.BOTTOM_RIGHT -> Pair(r.left, r.top)
                        Corner.NONE -> Pair(r.left, r.top)
                    }
                    val oldW = r.width().coerceAtLeast(1f)
                    val oldH = r.height().coerceAtLeast(1f)
                    val newW = kotlin.math.abs(px - ax).coerceAtLeast(1f)
                    val newH = kotlin.math.abs(py - ay).coerceAtLeast(1f)
                    val f = minOf(newW / oldW, newH / oldH).coerceIn(0.2f, 5f)
                    val cx = (ax + (if (px > ax) 1 else -1) * 0f)
                    sel.scale = (sel.scale * f).coerceIn(0.2f, 5f)
                    // keep anchor corner fixed
                    val bw = sel.bitmap.width * sel.scale
                    val bh = sel.bitmap.height * sel.scale
                    when (selectedCorner) {
                        Corner.TOP_LEFT -> {
                            sel.x = ax - bw
                            sel.y = ay - bh
                        }
                        Corner.TOP_RIGHT -> {
                            sel.x = ax
                            sel.y = ay - bh
                        }
                        Corner.BOTTOM_LEFT -> {
                            sel.x = ax - bw
                            sel.y = ay
                        }
                        Corner.BOTTOM_RIGHT -> {
                            sel.x = ax
                            sel.y = ay
                        }
                        Corner.NONE -> {}
                    }
                    clamp(sel)
                } else {
                    sel.x += dx / s
                    sel.y += dy / s
                    clamp(sel)
                }
                lastX = event.x
                lastY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (moving || scaling) {
                    if (selected?.id == "map") onInternalMapManipulationEnded?.invoke()
                }
                moving = false
                scaling = false
                selectedCorner = Corner.NONE
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (scaling) {
                    scaling = false
                    if (selected?.id == "map") onInternalMapManipulationEnded?.invoke()
                }
                lastX = event.x
                lastY = event.y
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun pinchDist(e: MotionEvent): Float =
        hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))

    private fun hitTest(x: Float, y: Float): Pair<OverlayItem, Corner>? {
        for (it in items.asReversed()) {
            val r = toView(itemRect(it))
            val corners = mapOf(
                Corner.TOP_LEFT to Pair(r.left, r.top),
                Corner.TOP_RIGHT to Pair(r.right, r.top),
                Corner.BOTTOM_LEFT to Pair(r.left, r.bottom),
                Corner.BOTTOM_RIGHT to Pair(r.right, r.bottom),
            )
            for ((c, p) in corners) {
                if (hypot(x - p.first, y - p.second) <= handleR) {
                    return Pair(it, c)
                }
            }
            if (r.contains(x, y)) return Pair(it, Corner.NONE)
        }
        return null
    }

    private fun allowManipulation(): Boolean {
        val ok = manipulationGate?.invoke() ?: true
        if (!ok) onManipulationBlocked?.invoke()
        return ok
    }

    private fun clamp(it: OverlayItem) {
        if (photoWidth <= 0 || photoHeight <= 0) return
        val w = it.bitmap.width * it.scale
        val h = it.bitmap.height * it.scale
        it.x = if (w <= photoWidth) it.x.coerceIn(0f, photoWidth - w) else (photoWidth - w) / 2f
        it.y = if (h <= photoHeight) it.y.coerceIn(0f, photoHeight - h) else (photoHeight - h) / 2f
    }

    /** Keep watermark in a free corner that doesn't overlap other items. */
    private fun snapWatermark() {
        val wm = items.firstOrNull { it.id == "watermark" } ?: return
        if (photoWidth <= 0 || photoHeight <= 0) return
        val w = wm.bitmap.width * wm.scale
        val h = wm.bitmap.height * wm.scale
        val m = photoWidth * 0.05f
        val candidates = listOf(
            RectF(m, m, m + w, m + h),
            RectF(photoWidth - m - w, m, photoWidth - m, m + h),
            RectF(m, photoHeight - m - h, m + w, photoHeight - m),
            RectF(photoWidth - m - w, photoHeight - m - h, photoWidth - m, photoHeight - m),
        )
        val others = items.filter { it.id != "watermark" }.map { itemRect(it) }
        for (c in candidates) {
            if (others.none { RectF.intersects(c, it) }) {
                wm.x = c.left
                wm.y = c.top
                return
            }
        }
        wm.x = candidates[2].left
        wm.y = candidates[2].top
    }
}
