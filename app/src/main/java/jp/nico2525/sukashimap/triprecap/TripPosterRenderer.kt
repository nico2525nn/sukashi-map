package jp.nico2525.sukashimap.triprecap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Poster content snapshot used by the renderer. */
data class PosterSpec(
    val title: String,
    val dateRange: String,
    val photoUris: List<Uri>,
    val photoCount: Int,
    val places: List<VisitStop>,
    val routeMeasured: List<Pair<Double, Double>>,
    val routeEstimated: List<Pair<Double, Double>>,
    val aspectW: Int,
    val aspectH: Int,
    val cornerRadiusPx: Int,
    val backgroundColor: Int,
    val textColor: Int,
    val fontKind: Int,
    val mapZoom: Float,
    val showPlaceNames: Boolean,
    val routeColor: Int,
    val sizePx: Int,
)

/**
 * Canvas-composited travel poster. Self-drawn north-up equirectangular map
 * panel (coastline-free schematic with graticule + route + markers), photo
 * strip, title/date header — all rendered from live data, no copied assets.
 */
class TripPosterRenderer(private val context: Context) {

    fun render(spec: PosterSpec): Bitmap {
        val w = spec.sizePx
        val h = (w.toDouble() * spec.aspectH / spec.aspectW).roundToInt().coerceIn(w / 3, w * 3)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = if (spec.cornerRadiusPx > 0) spec.cornerRadiusPx * w / 1080f else 0f
        c.drawColor(spec.backgroundColor)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Header: title + date.
        val typeface = when (spec.fontKind) {
            1 -> Typeface.SERIF
            2 -> Typeface.MONOSPACE
            else -> Typeface.DEFAULT
        }
        paint.typeface = Typeface.create(typeface, Typeface.BOLD)
        paint.color = spec.textColor
        paint.textSize = w * 0.055f
        val pad = w * 0.05f
        var y = pad + paint.textSize
        c.drawText(spec.title.ifEmpty { "Journey" }, pad, y, paint)
        paint.textSize = w * 0.035f
        paint.typeface = typeface
        y += paint.textSize * 1.6f
        if (spec.dateRange.isNotEmpty()) c.drawText(spec.dateRange, pad, y, paint)
        y += paint.textSize * 1.2f

        // Map panel: top ~55% of remaining space.
        val mapTop = y
        val mapH = (h - y) * 0.55f
        val mapRect = RectF(pad, mapTop, w - pad, mapTop + mapH)
        drawMapPanel(c, mapRect, spec, r, paint)

        // Photo strip below map.
        val stripTop = mapTop + mapH + pad * 0.4f
        drawPhotoStrip(c, RectF(pad, stripTop, w - pad, h - pad), spec, paint)

        // Rounded-corner mask: punch transparent corners when requested.
        if (r > 0) {
            applyRoundedMask(c, w, h, r)
        }
        return bmp
    }

    private fun drawMapPanel(
        c: Canvas,
        rect: RectF,
        spec: PosterSpec,
        radius: Float,
        paint: Paint,
    ) {
        c.save()
        if (radius > 0) c.clipRect(rect)
        // Panel background: deep slate (dark) or pale paper (light bg).
        val dark = isDark(spec.backgroundColor)
        val panel = if (dark) Color.rgb(18, 28, 44) else Color.rgb(226, 234, 242)
        paint.style = Paint.Style.FILL
        paint.color = panel
        if (radius > 0) c.drawRoundRect(rect, radius, radius, paint) else c.drawRect(rect, paint)

        // Graticule.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, rect.width() / 900f)
        paint.color = if (dark) Color.argb(70, 255, 255, 255) else Color.argb(70, 20, 40, 70)
        for (i in 1..5) {
            val x = rect.left + rect.width() * i / 6f
            c.drawLine(x, rect.top, x, rect.bottom, paint)
        }
        for (i in 1..3) {
            val yy = rect.top + rect.height() * i / 4f
            c.drawLine(rect.left, yy, rect.right, yy, paint)
        }

        val points = allPoints(spec)
        if (points.isNotEmpty()) {
            val (minLat, maxLat, minLon, maxLon) = bounds(points)
            val spanLat = max(0.02, maxLat - minLat)
            val spanLon = max(0.02, maxLon - minLon)
            val zoom = 1f + spec.mapZoom * 0.6f
            fun px(lat: Double, lon: Double): Pair<Float, Float> {
                val cx = (minLon + maxLon) / 2
                val cy = (minLat + maxLat) / 2
                val nx = ((lon - cx) / spanLon) * zoom + 0.5
                val ny = ((cy - lat) / spanLat) * zoom + 0.5
                return Pair(
                    (rect.left + nx * rect.width()).toFloat(),
                    (rect.top + ny * rect.height()).toFloat(),
                )
            }
            // Estimated (thin dashed) then measured (thick).
            drawPoly(c, spec.routeEstimated, ::px, paint, spec.routeColor, rect.width() / 700f, dashed = true)
            drawPoly(c, spec.routeMeasured, ::px, paint, spec.routeColor, rect.width() / 320f, dashed = false)
            // Place markers + optional labels.
            paint.style = Paint.Style.FILL
            for ((i, p) in spec.places.withIndex()) {
                val (x, y) = px(p.lat, p.lon)
                paint.color = Color.parseColor("#FF7043")
                c.drawCircle(x, y, rect.width() / 90f, paint)
                paint.color = Color.WHITE
                paint.textSize = rect.width() / 70f
                c.drawText((i + 1).toString(), x - paint.textSize * 0.3f, y + paint.textSize * 0.35f, paint)
                if (spec.showPlaceNames) {
                    paint.color = spec.textColor
                    c.drawText(p.name, x + rect.width() / 60f, y, paint)
                }
            }
            // Photo pins.
            val photoPts = photoPoints()
            paint.color = Color.parseColor("#4FC3F7")
            for ((lat, lon) in photoPts.take(60)) {
                val (x, y) = px(lat, lon)
                c.drawCircle(x, y, rect.width() / 160f, paint)
            }
        } else {
            paint.style = Paint.Style.FILL
            paint.color = if (dark) Color.argb(160, 255, 255, 255) else Color.argb(160, 20, 40, 70)
            paint.textSize = rect.width() / 28f
            paint.textAlign = Paint.Align.CENTER
            c.drawText("—", rect.centerX(), rect.centerY(), paint)
            paint.textAlign = Paint.Align.LEFT
        }
        // Border.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, rect.width() / 600f)
        paint.color = if (dark) Color.argb(120, 255, 255, 255) else Color.argb(120, 20, 40, 70)
        if (radius > 0) c.drawRoundRect(rect, radius, radius, paint) else c.drawRect(rect, paint)
        c.restore()
    }

    private val photoGeo = ArrayList<Pair<Double, Double>>()
    fun setPhotoGeo(pts: List<Pair<Double, Double>>) {
        photoGeo.clear()
        photoGeo.addAll(pts)
    }
    private fun photoPoints(): List<Pair<Double, Double>> = photoGeo.toList()

    private fun drawPoly(
        c: Canvas,
        pts: List<Pair<Double, Double>>,
        px: (Double, Double) -> Pair<Float, Float>,
        paint: Paint,
        color: Int,
        width: Float,
        dashed: Boolean,
    ) {
        if (pts.size < 2) return
        paint.style = Paint.Style.STROKE
        paint.color = color
        paint.strokeWidth = width
        paint.pathEffect = if (dashed) android.graphics.DashPathEffect(floatArrayOf(10f, 8f), 0f) else null
        val path = android.graphics.Path()
        pts.forEachIndexed { i, (la, lo) ->
            val (x, y) = px(la, lo)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        c.drawPath(path, paint)
        paint.pathEffect = null
    }

    private fun drawPhotoStrip(c: Canvas, rect: RectF, spec: PosterSpec, paint: Paint) {
        val n = spec.photoUris.size
        if (n == 0) {
            paint.style = Paint.Style.FILL
            paint.color = spec.textColor
            paint.alpha = 140
            paint.textSize = rect.width() / 26f
            c.drawText("12 photos max", rect.left, rect.top + paint.textSize, paint)
            paint.alpha = 255
            return
        }
        val cols = when {
            n <= 3 -> n
            n <= 6 -> 3
            else -> 4
        }
        val rows = (n + cols - 1) / cols
        val gap = rect.width() * 0.012f
        val cellW = (rect.width() - gap * (cols - 1)) / cols
        val cellH = minOf((rect.height() - gap * (rows - 1)) / rows, cellW * 1.2f)
        for (i in spec.photoUris.indices) {
            val col = i % cols
            val row = i / cols
            val left = rect.left + col * (cellW + gap)
            val top = rect.top + row * (cellH + gap)
            val dst = RectF(left, top, left + cellW, top + cellH)
            val bmp = decodeThumb(spec.photoUris[i], 512)
            c.save()
            c.clipRect(dst)
            if (bmp != null) {
                val src = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
                // Center-crop.
                val scale = max(dst.width() / src.width(), dst.height() / src.height())
                val sw = dst.width() / scale
                val sh = dst.height() / scale
                val sx = ((src.width() - sw) / 2).roundToInt().coerceAtLeast(0)
                val sy = ((src.height() - sh) / 2).roundToInt().coerceAtLeast(0)
                val sww = sw.roundToInt().coerceAtMost(bmp.width - sx)
                val shh = sh.roundToInt().coerceAtMost(bmp.height - sy)
                c.drawBitmap(bmp, android.graphics.Rect(sx, sy, sx + sww, sy + shh), dst, paint)
                if (!bmp.isRecycled) bmp.recycle()
            } else {
                paint.style = Paint.Style.FILL
                paint.color = Color.GRAY
                c.drawRect(dst, paint)
            }
            c.restore()
        }
    }

    private fun decodeThumb(uri: Uri, maxDim: Int): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(ins, null, b)
                var s = 1
                while (maxOf(b.outWidth, b.outHeight) / s > maxDim) s *= 2
                context.contentResolver.openInputStream(uri)?.use { ins2 ->
                    BitmapFactory.decodeStream(ins2, null, BitmapFactory.Options().apply { inSampleSize = s })
                }
            }
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun applyRoundedMask(c: Canvas, w: Int, h: Int, r: Float) {
        // Destination-out rounded-rect inverse: draw corner patches transparent.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        }
        val path = android.graphics.Path().apply {
            addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, android.graphics.Path.Direction.CW)
        }
        c.save()
        c.clipOutPath(path)
        c.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        c.restore()
        paint.xfermode = null
    }

    private fun allPoints(spec: PosterSpec): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        spec.places.forEach { out += it.lat to it.lon }
        out += spec.routeMeasured
        out += spec.routeEstimated
        out += photoGeo
        return out
    }

    private fun bounds(pts: List<Pair<Double, Double>>): DoubleArray {
        var minLat = 90.0; var maxLat = -90.0; var minLon = 180.0; var maxLon = -180.0
        for ((la, lo) in pts) {
            if (abs(la) > 90 || abs(lo) > 180) continue
            if (la < minLat) minLat = la
            if (la > maxLat) maxLat = la
            if (lo < minLon) minLon = lo
            if (lo > maxLon) maxLon = lo
        }
        return doubleArrayOf(minLat, maxLat, minLon, maxLon)
    }

    private fun isDark(color: Int): Boolean {
        val lum = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
        return lum < 128
    }
}
