package jp.nico2525.sukashimap.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import jp.nico2525.sukashimap.data.OverlayPosition
import jp.nico2525.sukashimap.data.TransportInfo
import jp.nico2525.sukashimap.data.TransportMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Self-drawn Canvas world map. Reads ring JSON assets ([[[lon,lat],...]]),
 * projects lon/lat to a fitted RectF, draws coastlines/borders/routes/markers.
 */
class MapRenderer(private val context: Context) {

    private var coarse: List<List<PointF>>? = null
    private var fine: List<List<PointF>>? = null
    private var borders: List<List<PointF>>? = null
    private var usStates: List<List<PointF>>? = null
    private var jpPrefs: List<List<PointF>>? = null
    private var jpCoast: List<List<PointF>>? = null
    private var jpWater: List<List<PointF>>? = null

    private fun load(name: String): List<List<PointF>> {
        val text = context.assets.open(name).bufferedReader().use { it.readText() }
        val outer = JSONArray(text)
        return List(outer.length()) { i ->
            val ring = outer.getJSONArray(i)
            List(ring.length()) { j ->
                val p = ring.getJSONArray(j)
                PointF(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
            }
        }
    }

    suspend fun preload() = withContext(Dispatchers.IO) {
        if (coarse == null) coarse = runCatching { load("world_map_coarse.json") }.getOrElse { emptyList() }
        if (borders == null) borders = runCatching { load("country_borders.json") }.getOrElse { emptyList() }
        if (usStates == null) usStates = runCatching { load("us_states.json") }.getOrElse { emptyList() }
        if (jpPrefs == null) jpPrefs = runCatching { load("japan_prefectures.json") }.getOrElse { emptyList() }
        if (jpCoast == null) jpCoast = runCatching { load("japan_coastline.json") }.getOrElse { emptyList() }
        if (jpWater == null) jpWater = runCatching { load("japan_waterbodies.json") }.getOrElse { emptyList() }
    }

    private suspend fun fineRings(): List<List<PointF>> {
        if (fine == null) {
            fine = withContext(Dispatchers.IO) {
                runCatching { load("world_map_fine.json") }.getOrElse { emptyList() }
            }
        }
        return fine!!
    }

    data class MapFrame(val bounds: RectF, val minLon: Double, val maxLon: Double, val minLat: Double, val maxLat: Double)

    private fun isJapan(lon: Double, lat: Double) = lon in 128.0..146.5 && lat in 30.0..46.0
    private fun isUsa(lon: Double, lat: Double) =
        (lon in -130.0..-65.0 && lat in 23.0..50.0) || (lon in -172.0..-128.0 && lat in 50.0..72.0)

    /**
     * Compute a map frame for the given transport info. Returns null when no
     * coordinates are available at all.
     */
    fun frameFor(info: TransportInfo, imgW: Int, imgH: Int): MapFrame? {
        val pts = mutableListOf<Pair<Double, Double>>() // lon,lat
        fun add(lat: Double?, lon: Double?) {
            if (lat != null && lon != null) pts.add(Pair(lon, lat))
        }
        when (info) {
            is TransportInfo.Airplane -> {
                add(info.departureLatitude, info.departureLongitude)
                add(info.arrivalLatitude, info.arrivalLongitude)
            }
            is TransportInfo.Train -> {
                add(info.departureLatitude, info.departureLongitude)
                add(info.arrivalLatitude, info.arrivalLongitude)
            }
            is TransportInfo.Other -> {
                add(info.departureLatitude, info.departureLongitude)
                add(info.destinationLatitude, info.destinationLongitude)
            }
            is TransportInfo.Spot -> {}
        }
        add(info.currentLatitude, info.currentLongitude)
        if (pts.isEmpty()) return null

        val w = imgW.toFloat()
        val h = imgH.toFloat()
        val landscape = w > h
        val margin = 0.03f * w * (if (landscape) 0.85f else 1.0f)
        val boxW = (if (landscape) w * 0.33f else w * 0.45f) * (if (landscape) 0.85f else 1.0f)
        val boxH = (if (landscape) h * 0.45f else h * 0.33f) * (if (landscape) 0.85f else 1.0f)

        var minLon = pts.minOf { it.first }
        var maxLon = pts.maxOf { it.first }
        var minLat = pts.minOf { it.second }
        var maxLat = pts.maxOf { it.second }
        // pad span; single-point gets a default window
        var spanLon = max(0.6, (maxLon - minLon) * 1.15)
        var spanLat = max(0.6, (maxLat - minLat) * 1.15)
        var cLon = (minLon + maxLon) / 2
        var cLat = (minLat + maxLat) / 2
        // match box aspect
        val boxAspect = boxW / boxH
        val latAdjust = Math.cos(Math.toRadians(cLat)).coerceIn(0.3, 1.0)
        if (spanLon * latAdjust / spanLat > boxAspect) {
            spanLat = (spanLon * latAdjust / boxAspect).toFloat().toDouble()
        } else {
            spanLon = (spanLat * boxAspect / latAdjust).toFloat().toDouble()
        }
        minLon = (cLon - spanLon / 2).coerceIn(-180.0, 180.0)
        maxLon = (cLon + spanLon / 2).coerceIn(-180.0, 180.0)
        minLat = (cLat - spanLat / 2).coerceIn(-85.0, 85.0)
        maxLat = (cLat + spanLat / 2).coerceIn(-85.0, 85.0)

        val left = info.mapPosition == OverlayPosition.TOP_LEFT || info.mapPosition == OverlayPosition.BOTTOM_LEFT
        val top = info.mapPosition == OverlayPosition.TOP_LEFT || info.mapPosition == OverlayPosition.TOP_RIGHT
        val x = if (left) margin else w - boxW - margin
        val y = if (top) margin else h - boxH - margin
        return MapFrame(RectF(x, y, x + boxW, y + boxH), minLon, maxLon, minLat, maxLat)
    }

    private fun project(frame: MapFrame, lon: Double, lat: Double): PointF {
        val fx = ((lon - frame.minLon) / (frame.maxLon - frame.minLon)).toFloat()
        val fy = (1.0 - (lat - frame.minLat) / (frame.maxLat - frame.minLat)).toFloat()
        return PointF(frame.bounds.left + fx * frame.bounds.width(), frame.bounds.top + fy * frame.bounds.height())
    }

    private fun drawRings(canvas: Canvas, rings: List<List<PointF>>, frame: MapFrame, paint: Paint) {
        val path = Path()
        for (ring in rings) {
            var started = false
            var prevOutside = false
            for (p in ring) {
                val lon = p.x.toDouble()
                val lat = p.y.toDouble()
                val outside = lon < frame.minLon - 1 || lon > frame.maxLon + 1 || lat < frame.minLat - 1 || lat > frame.maxLat + 1
                if (outside) {
                    prevOutside = true
                    continue
                }
                val q = project(frame, lon, lat)
                if (!started || prevOutside) {
                    path.moveTo(q.x, q.y)
                    started = true
                } else {
                    path.lineTo(q.x, q.y)
                }
                prevOutside = false
            }
        }
        canvas.drawPath(path, paint)
    }

    private fun drawRoute(canvas: Canvas, coords: List<Pair<Double, Double>>, frame: MapFrame, paint: Paint) {
        if (coords.size < 2) return
        val path = Path()
        var started = false
        for ((lon, lat) in coords) {
            val q = project(frame, lon, lat)
            if (!started) {
                path.moveTo(q.x, q.y)
                started = true
            } else path.lineTo(q.x, q.y)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawMarker(canvas: Canvas, frame: MapFrame, lat: Double, lon: Double, color: Int, r: Float) {
        val q = project(frame, lon, lat)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawCircle(q.x, q.y, r, p)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = r * 0.35f
        }
        canvas.drawCircle(q.x, q.y, r, ring)
    }

    /** White-filled dot with a thick colored ring (departure/arrival stations). */
    private fun drawRingMarker(canvas: Canvas, frame: MapFrame, lat: Double, lon: Double, color: Int, r: Float) {
        val q = project(frame, lon, lat)
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(90, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(q.x, q.y, r * 1.25f, halo)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(q.x, q.y, r, fill)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = r * 0.45f
        }
        canvas.drawCircle(q.x, q.y, r * 0.8f, ring)
    }

    suspend fun render(canvas: Canvas, info: TransportInfo, imgW: Int, imgH: Int, lineColor: Int, borderColor: Int) {
        preload()
        val frame = frameFor(info, imgW, imgH) ?: return
        val spanLon = frame.maxLon - frame.minLon
        val useFine = spanLon < 25

        canvas.save()
        canvas.clipRect(frame.bounds)

        val coastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = max(2f, frame.bounds.width() / 180f)
            setShadowLayer(max(2f, frame.bounds.width() / 250f), 0f, 0f, Color.argb(170, 0, 0, 0))
        }
        val base = if (useFine) fineRings().ifEmpty { coarse!! } else coarse!!
        drawRings(canvas, base, frame, coastPaint)

        val cLon = (frame.minLon + frame.maxLon) / 2
        val cLat = (frame.minLat + frame.maxLat) / 2
        val detailPaint = Paint(coastPaint).apply {
            strokeWidth = coastPaint.strokeWidth * 0.4f
            alpha = 170
        }
        if (isJapan(cLon, cLat) && spanLon < 30) {
            drawRings(canvas, jpPrefs!!, frame, detailPaint)
            drawRings(canvas, jpCoast!!, frame, coastPaint)
        } else if (isUsa(cLon, cLat) && spanLon < 60) {
            drawRings(canvas, usStates!!, frame, detailPaint)
        } else if (spanLon > 60) {
            val borderPaint = Paint(detailPaint).apply { color = borderColor }
            drawRings(canvas, borders!!, frame, borderPaint)
        }

        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = max(5f, frame.bounds.width() / 60f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setShadowLayer(max(3f, frame.bounds.width() / 200f), 0f, 0f, Color.argb(190, 0, 0, 0))
        }
        val r = max(9f, frame.bounds.width() / 34f)
        when (info) {
            is TransportInfo.Airplane -> {
                val dLat = info.departureLatitude
                val dLon = info.departureLongitude
                val aLat = info.arrivalLatitude
                val aLon = info.arrivalLongitude
                if (dLat != null && dLon != null && aLat != null && aLon != null) {
                    // great-circle interpolation
                    val pts = greatCircle(dLat, dLon, aLat, aLon, 48)
                    drawRoute(canvas, pts.map { Pair(it.second, it.first) }, frame, routePaint)
                    drawMarker(canvas, frame, dLat, dLon, Color.parseColor("#4FC3F7"), r)
                    drawMarker(canvas, frame, aLat, aLon, Color.parseColor("#FF7043"), r)
                } else {
                    singleMarkers(canvas, frame, info, r)
                }
            }
            is TransportInfo.Train -> {
                for (g in info.geometries) {
                    drawRoute(canvas, parseRing(g), frame, routePaint)
                }
                // departure: cyan ring; arrival: red-orange ring; both white-filled
                info.departureLatitude?.let { dLat ->
                    info.departureLongitude?.let { dLon ->
                        drawRingMarker(canvas, frame, dLat, dLon, Color.parseColor("#29B6F6"), r)
                    }
                }
                info.arrivalLatitude?.let { aLat ->
                    info.arrivalLongitude?.let { aLon ->
                        drawRingMarker(canvas, frame, aLat, aLon, Color.parseColor("#FF5722"), r)
                    }
                }
            }
            is TransportInfo.Other -> {
                info.routePolyline?.forEach { drawRoute(canvas, parseRing(it), frame, routePaint) }
                singleMarkers(canvas, frame, info, r)
            }
            is TransportInfo.Spot -> singleMarkers(canvas, frame, info, r)
        }
        info.currentLatitude?.let { lat ->
            info.currentLongitude?.let { lon ->
                drawMarker(canvas, frame, lat, lon, Color.WHITE, r * 0.8f)
            }
        }
        canvas.restore()
    }

    private fun singleMarkers(canvas: Canvas, frame: MapFrame, info: TransportInfo, r: Float) {
        val (dLat, dLon, aLat, aLon) = when (info) {
            is TransportInfo.Train -> Quad(info.departureLatitude, info.departureLongitude, info.arrivalLatitude, info.arrivalLongitude)
            is TransportInfo.Other -> Quad(info.departureLatitude, info.departureLongitude, info.destinationLatitude, info.destinationLongitude)
            else -> Quad(info.currentLatitude, info.currentLongitude, null, null)
        }
        if (dLat != null && dLon != null) drawMarker(canvas, frame, dLat, dLon, Color.parseColor("#4FC3F7"), r)
        if (aLat != null && aLon != null) drawMarker(canvas, frame, aLat, aLon, Color.parseColor("#FF7043"), r)
    }

    private data class Quad(val a: Double?, val b: Double?, val c: Double?, val d: Double?)

    private fun parseRing(json: String): List<Pair<Double, Double>> {
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val p = arr.getJSONArray(i)
                Pair(p.getDouble(0), p.getDouble(1))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Returns list of (lat, lon) along the great circle. */
    private fun greatCircle(lat1: Double, lon1: Double, lat2: Double, lon2: Double, n: Int): List<Pair<Double, Double>> {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val l1 = Math.toRadians(lon1)
        val l2 = Math.toRadians(lon2)
        val d = 2 * kotlin.math.asin(
            kotlin.math.sqrt(
                kotlin.math.sin((p2 - p1) / 2).let { it * it } +
                    kotlin.math.cos(p1) * kotlin.math.cos(p2) * kotlin.math.sin((l2 - l1) / 2).let { it * it },
            ),
        )
        if (d < 1e-9) return listOf(Pair(lat1, lon1), Pair(lat2, lon2))
        return (0..n).map { i ->
            val f = i.toDouble() / n
            val a = kotlin.math.sin((1 - f) * d) / kotlin.math.sin(d)
            val b = kotlin.math.sin(f * d) / kotlin.math.sin(d)
            val x = a * kotlin.math.cos(p1) * kotlin.math.cos(l1) + b * kotlin.math.cos(p2) * kotlin.math.cos(l2)
            val y = a * kotlin.math.cos(p1) * kotlin.math.sin(l1) + b * kotlin.math.cos(p2) * kotlin.math.sin(l2)
            val z = a * kotlin.math.sin(p1) + b * kotlin.math.sin(p2)
            Pair(Math.toDegrees(kotlin.math.atan2(z, kotlin.math.sqrt(x * x + y * y))), Math.toDegrees(kotlin.math.atan2(y, x)))
        }
    }
}
