package jp.nico2525.sukashimap.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import jp.nico2525.sukashimap.data.CountryBoundEntry
import jp.nico2525.sukashimap.data.MapVectorRepository
import jp.nico2525.sukashimap.data.PackedRing
import jp.nico2525.sukashimap.data.RoutingAndPlaceService
import jp.nico2525.sukashimap.model.Coord
import jp.nico2525.sukashimap.model.DateTimeDisplayMode
import jp.nico2525.sukashimap.model.LocationDisplayMode
import jp.nico2525.sukashimap.model.OverlayCorner
import jp.nico2525.sukashimap.model.OverlayFont
import jp.nico2525.sukashimap.model.OverlayStyle
import jp.nico2525.sukashimap.model.TransportInfo
import jp.nico2525.sukashimap.model.TransportMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class MapDatasetSlice(
    val primaryCoastline: List<PackedRing>,
    val secondaryBorders: List<PackedRing> = emptyList(),
    val neighboringCoastlines: List<PackedRing> = emptyList(),
    val waterCutouts: List<PackedRing> = emptyList(),
    val isJapanMultiPass: Boolean = false,
    val singlePointFrameBounds: GeoBounds? = null
)

data class GeoBounds(
    val minLon: Double,
    val maxLon: Double,
    val minLat: Double,
    val maxLat: Double
)

data class OverlayLayoutMetrics(
    val textBounds: RectF,
    val mapBounds: RectF?
)

object OverlayCanvasRenderer {

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private fun createJetPath(): Path = Path().apply {
        moveTo(9f, 4f)
        lineTo(9f, 2f)
        lineTo(1f, -3f)
        lineTo(1f, -8.5f)
        cubicTo(1f, -9.33f, 0.33f, -10f, -0.5f, -10f)
        cubicTo(-1.33f, -10f, -2f, -9.33f, -2f, -8.5f)
        lineTo(-2f, -3f)
        lineTo(-10f, 2f)
        lineTo(-10f, 4f)
        lineTo(-2f, 1.5f)
        lineTo(-2f, 7f)
        lineTo(-4f, 8.5f)
        lineTo(-4f, 10f)
        lineTo(-0.5f, 9f)
        lineTo(3f, 10f)
        lineTo(3f, 8.5f)
        lineTo(1f, 7f)
        lineTo(1f, 1.5f)
        close()
    }

    /**
     * Validates whether `cur` GPS lies genuinely along the `start..end` corridor
     * (in antimeridian-normalized degree space) so stale/unrelated GPS never warps distant flights.
     */
    private fun resolveValidMidRouteGps(
        start: Coord?,
        end: Coord?,
        cur: Coord?,
        proj: MapProjection
    ): Coord? {
        if (start == null || end == null || cur == null) return null
        val sx = proj.normalizeLon(start.lon)
        val ex = proj.normalizeLon(end.lon)
        val cx = proj.normalizeLon(cur.lon)
        val dStart = hypot(cx - sx, cur.lat - start.lat)
        val dEnd = hypot(cx - ex, cur.lat - end.lat)
        val chord = hypot(ex - sx, end.lat - start.lat)
        if (dStart <= 0.12 || dEnd <= 0.12) return null
        if (dStart + dEnd > max(chord * 1.45, 0.5)) return null
        return cur
    }

    private fun collectFocusPoints(info: TransportInfo): List<Coord> {
        val start = info.startCoord()
        val end = info.endCoord()
        val cur = info.currentGpsCoord()
        val polyCoords = info.routePolylines.flatten()
        val baseEndpoints = listOfNotNull(start, end)
        val baseProj = MapProjectionEngine.selectProjection(baseEndpoints.ifEmpty { listOfNotNull(cur) })
        val validMid = resolveValidMidRouteGps(start, end, cur, baseProj)

        return buildList {
            if (polyCoords.isNotEmpty() && info.mode != TransportMode.AIRPLANE && info.mode != TransportMode.SPOT) {
                addAll(polyCoords)
            }
            if (info.mode != TransportMode.SPOT) {
                start?.let { add(it) }
                end?.let { add(it) }
                if (info.mode == TransportMode.AIRPLANE && validMid != null) {
                    add(validMid)
                }
            }
            if (isEmpty() && cur != null) {
                add(cur)
            }
        }
    }

    suspend fun prepareMapSlice(
        info: TransportInfo,
        repo: MapVectorRepository
    ): MapDatasetSlice {
        val pts = collectFocusPoints(info)
        if (pts.isEmpty()) {
            return MapDatasetSlice(primaryCoastline = repo.getJapanCoastline())
        }

        // Require ALL focus points to lie within Japan (including ~4km offshore runway buffer for OKA / KIX!)
        val allInJapan = pts.all { repo.isPointInJapanOrCoastalBuffer(it.lon, it.lat) }

        if (allInJapan) {
            val singleBounds = if (pts.size == 1) {
                val p = pts.first()
                val prefRing = repo.findEnclosingJapanPrefectureRing(p.lon, p.lat)
                if (prefRing != null) {
                    val minSpan = 1.6
                    val cx = (prefRing.minLon + prefRing.maxLon) / 2.0
                    val cy = (prefRing.minLat + prefRing.maxLat) / 2.0
                    val halfW = max((prefRing.maxLon - prefRing.minLon) / 2.0, minSpan / 2.0)
                    val halfH = max((prefRing.maxLat - prefRing.minLat) / 2.0, minSpan * 0.42)
                    GeoBounds(cx - halfW, cx + halfW, cy - halfH, cy + halfH)
                } else {
                    GeoBounds(p.lon - 1.5, p.lon + 1.5, p.lat - 1.2, p.lat + 1.2)
                }
            } else null

            return MapDatasetSlice(
                primaryCoastline = repo.getJapanCoastline(),
                secondaryBorders = repo.getJapanPrefectures(),
                neighboringCoastlines = repo.getNeighboringWorldRingsForJapan(),
                waterCutouts = repo.getJapanWaterbodies(),
                isJapanMultiPass = true,
                singlePointFrameBounds = singleBounds
            )
        }

        val allInUs = pts.all { repo.isPointInUsStates(it.lon, it.lat) }
        if (allInUs) {
            val p = pts.first()
            val singleBounds = if (pts.size == 1) {
                val st = repo.getUsStates().firstOrNull { it.containsPoint(p.lon, p.lat) }
                if (st != null) {
                    GeoBounds(st.minLon.toDouble(), st.maxLon.toDouble(), st.minLat.toDouble(), st.maxLat.toDouble())
                } else {
                    GeoBounds(p.lon - 4.0, p.lon + 4.0, p.lat - 3.0, p.lat + 3.0)
                }
            } else null
            return MapDatasetSlice(
                primaryCoastline = repo.getUsStates(),
                singlePointFrameBounds = singleBounds
            )
        }

        val proj = MapProjectionEngine.selectProjection(pts)
        val singleCountry: CountryBoundEntry? = if (pts.size == 1) {
            repo.detectCountryBound(pts.first().lon, pts.first().lat, info.photoCountryCode)
        } else null

        val singleBounds = singleCountry?.let {
            GeoBounds(it.minLon, it.maxLon, it.minLat, it.maxLat)
        }

        val rings = if (proj.kind == ProjectionKind.ROBINSON) {
            repo.getWorldMapCoarse()
        } else {
            repo.getWorldMapFine()
        }
        return MapDatasetSlice(
            primaryCoastline = rings,
            singlePointFrameBounds = singleBounds
        )
    }

    fun render(
        context: Context,
        canvas: Canvas,
        canvasWidth: Float,
        canvasHeight: Float,
        info: TransportInfo,
        style: OverlayStyle,
        mapSlice: MapDatasetSlice?
    ): OverlayLayoutMetrics {
        val isLandscape = canvasWidth > canvasHeight
        val landscapeScale = if (isLandscape) 0.85f else 1.0f
        val unit = (canvasWidth / 1000f) * landscapeScale

        val textMargin = 0.05f * canvasWidth * landscapeScale
        val mapMargin = 0.03f * canvasWidth * landscapeScale

        val mapRect: RectF? = if (style.showMap && mapSlice != null) {
            drawMapOverlay(
                canvas = canvas,
                W = canvasWidth,
                H = canvasHeight,
                landscapeScale = landscapeScale,
                unit = unit,
                mapMargin = mapMargin,
                info = info,
                style = style,
                slice = mapSlice
            )
        } else null

        val textRect = drawTextOverlay(
            context = context,
            canvas = canvas,
            W = canvasWidth,
            H = canvasHeight,
            unit = unit,
            textMargin = textMargin,
            info = info,
            style = style
        )

        return OverlayLayoutMetrics(textBounds = textRect, mapBounds = mapRect)
    }

    private fun drawMapOverlay(
        canvas: Canvas,
        W: Float,
        H: Float,
        landscapeScale: Float,
        unit: Float,
        mapMargin: Float,
        info: TransportInfo,
        style: OverlayStyle,
        slice: MapDatasetSlice
    ): RectF? {
        val focusPoints = collectFocusPoints(info)
        if (focusPoints.isEmpty()) return null

        val start = if (info.mode == TransportMode.SPOT) null else info.startCoord()
        val end = if (info.mode == TransportMode.SPOT) null else info.endCoord()
        val rawCur = info.currentGpsCoord()
        val proj = MapProjectionEngine.selectProjection(focusPoints)
        val validMidCur = resolveValidMidRouteGps(start, end, rawCur, proj)

        var minPx = Float.POSITIVE_INFINITY
        var maxPx = Float.NEGATIVE_INFINITY
        var minPy = Float.POSITIVE_INFINITY
        var maxPy = Float.NEGATIVE_INFINITY

        val framingPoints: List<Coord> = if (focusPoints.size == 1 && slice.singlePointFrameBounds != null) {
            val b = slice.singlePointFrameBounds
            listOf(
                Coord(b.minLon, b.minLat),
                Coord(b.maxLon, b.minLat),
                Coord(b.minLon, b.maxLat),
                Coord(b.maxLon, b.maxLat),
                focusPoints.first()
            )
        } else if (focusPoints.size == 1) {
            val p = focusPoints.first()
            listOf(Coord(p.lon - 2.0, p.lat - 1.6), Coord(p.lon + 2.0, p.lat + 1.6))
        } else {
            focusPoints
        }

        var minLonDeg = Double.POSITIVE_INFINITY
        var maxLonDeg = Double.NEGATIVE_INFINITY
        for (pt in framingPoints) {
            val nLon = proj.normalizeLon(pt.lon)
            if (nLon < minLonDeg) minLonDeg = nLon
            if (nLon > maxLonDeg) maxLonDeg = nLon
            val px = proj.projectX(pt.lon, pt.lat)
            val py = proj.projectY(pt.lat)
            if (px < minPx) minPx = px
            if (px > maxPx) maxPx = px
            if (py < minPy) minPy = py
            if (py > maxPy) maxPy = py
        }

        val minProjSpan = 0.018f
        if (maxPx - minPx < minProjSpan) {
            val mid = (minPx + maxPx) * 0.5f
            minPx = mid - minProjSpan * 0.5f
            maxPx = mid + minProjSpan * 0.5f
        }
        if (maxPy - minPy < minProjSpan) {
            val mid = (minPy + maxPy) * 0.5f
            minPy = mid - minProjSpan * 0.5f
            maxPy = mid + minProjSpan * 0.5f
        }

        val spanX = maxPx - minPx
        val spanY = maxPy - minPy
        val padX = spanX * 0.24f
        val padYTop = spanY * (if (info.mode == TransportMode.AIRPLANE) 0.30f else 0.24f)
        val padYBottom = spanY * 0.24f
        minPx -= padX
        maxPx += padX
        minPy -= padYTop
        maxPy += padYBottom

        val paddedProjW = maxPx - minPx
        val paddedProjH = maxPy - minPy

        val isLandscape = W > H
        val minBoundMargin = mapMargin * 0.4f
        val userBoxScale = style.mapBoxScale.coerceIn(0.5f, 2.5f)
        val maxMapW = ((if (isLandscape) 0.33f * W else 0.45f * W) * landscapeScale * userBoxScale)
            .coerceAtMost((W - minBoundMargin * 2f).coerceAtLeast(48f))
        val maxMapH = ((if (isLandscape) 0.45f * H else 0.33f * H) * landscapeScale * userBoxScale)
            .coerceAtMost((H - minBoundMargin * 2f).coerceAtLeast(48f))

        val clampedAspect = max(paddedProjW / paddedProjH.coerceAtLeast(1e-6f), 0.85f)
        var boxW = maxMapW
        var boxH = boxW / clampedAspect
        if (boxH > maxMapH) {
            boxH = maxMapH
            boxW = boxH * clampedAspect
        }
        if (focusPoints.size == 1) {
            boxH = max(boxH, 0.45f * maxMapH)
            boxW = max(boxW, boxH * 0.85f).coerceAtMost(maxMapW)
        }

        val boxLeft: Float
        val boxTop: Float
        val free = style.mapFreePos
        if (free != null) {
            val maxLeft = (W - boxW - minBoundMargin).coerceAtLeast(minBoundMargin)
            val maxTop = (H - boxH - minBoundMargin).coerceAtLeast(minBoundMargin)
            boxLeft = (free.x * W - boxW * 0.5f).coerceIn(minBoundMargin, maxLeft)
            boxTop = (free.y * H - boxH * 0.5f).coerceIn(minBoundMargin, maxTop)
        } else {
            boxLeft = when (style.mapCorner) {
                OverlayCorner.TOP_LEFT, OverlayCorner.BOTTOM_LEFT -> mapMargin
                OverlayCorner.TOP_RIGHT, OverlayCorner.BOTTOM_RIGHT -> (W - mapMargin - boxW).coerceAtLeast(0f)
            }
            boxTop = when (style.mapCorner) {
                OverlayCorner.TOP_LEFT, OverlayCorner.TOP_RIGHT -> mapMargin
                OverlayCorner.BOTTOM_LEFT, OverlayCorner.BOTTOM_RIGHT -> (H - mapMargin - boxH).coerceAtLeast(0f)
            }
        }
        val mapRect = RectF(boxLeft, boxTop, boxLeft + boxW, boxTop + boxH)

        val internalZoom = style.mapInternalZoom.coerceIn(0.5f, 5.0f)
        val baseFitScale = min(boxW / paddedProjW, boxH / paddedProjH) * internalZoom
        val projCenterX = (minPx + maxPx) * 0.5f - (style.mapInternalPanX * paddedProjW / internalZoom)
        val projCenterY = (minPy + maxPy) * 0.5f - (style.mapInternalPanY * paddedProjH / internalZoom)
        val boxCenterX = mapRect.centerX()
        val boxCenterY = mapRect.centerY()

        fun toScreenX(lon: Double, lat: Double): Float =
            boxCenterX + (proj.projectX(lon, lat) - projCenterX) * baseFitScale

        fun toScreenY(lat: Double): Float =
            boxCenterY + (proj.projectY(lat) - projCenterY) * baseFitScale

        // Pan-aware & Antimeridian-aware visible longitude frustum
        val approxHalfLonSpan = ((boxW / baseFitScale) / 0.012f) + 6f
        val panLonShift = -(style.mapInternalPanX * (maxLonDeg - minLonDeg).toFloat().coerceAtLeast(2f) / internalZoom)
        val approxMidLon = ((minLonDeg + maxLonDeg) * 0.5).toFloat() + panLonShift
        val viewMinLon = approxMidLon - approxHalfLonSpan
        val viewMaxLon = approxMidLon + approxHalfLonSpan

        fun isRingOutsideFrustum(ring: PackedRing): Boolean {
            if (!proj.wrapAntimeridian) {
                return ring.maxLon < viewMinLon || ring.minLon > viewMaxLon
            }
            // In wrapped Pacific space [0..360]: purely western rings shift by +360
            return if (ring.maxLon < 0f) {
                (ring.maxLon + 360f) < viewMinLon || (ring.minLon + 360f) > viewMaxLon
            } else if (ring.minLon >= 0f) {
                ring.maxLon < viewMinLon || ring.minLon > viewMaxLon
            } else {
                false // Straddles 0 deg Prime Meridian -> buildProjectedPath splits it safely!
            }
        }

        val zoomFactor = sqrt(internalZoom).coerceIn(0.75f, 2.5f)
        val borderStroke = max(1.2f, 0.005f * boxW * zoomFactor)
        val haloStroke = 1.65f * borderStroke
        val borderLum = ColorUtils.calculateLuminance(style.borderColorArgb)
        val haloColor = if (borderLum < 0.5) Color.argb(140, 255, 255, 255) else Color.argb(140, 0, 0, 0)

        val effectiveLonSpan = (maxLonDeg - minLonDeg) / internalZoom

        val layerId = canvas.saveLayer(mapRect, null)
        canvas.clipRect(mapRect)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = haloColor
            strokeWidth = haloStroke
        }

        val scratchPath = Path()

        fun drawRings(rings: List<PackedRing>, width: Float, colorArgb: Int, withHalo: Boolean, closePolys: Boolean = false) {
            strokePaint.strokeWidth = width
            strokePaint.color = colorArgb
            haloPaint.strokeWidth = width * 1.65f
            for (ring in rings) {
                if (isRingOutsideFrustum(ring)) continue
                buildProjectedPath(ring, proj, ::toScreenX, ::toScreenY, closePolys, mapRect, scratchPath)
                if (scratchPath.isEmpty) continue
                if (withHalo) canvas.drawPath(scratchPath, haloPaint)
                canvas.drawPath(scratchPath, strokePaint)
            }
        }

        if (slice.isJapanMultiPass) {
            // Draw neighboring East Asia coastlines (Busan, Jeju, Taiwan) when visible in frame!
            if (slice.neighboringCoastlines.isNotEmpty()) {
                drawRings(slice.neighboringCoastlines, borderStroke, style.borderColorArgb, withHalo = true)
            }
            if (effectiveLonSpan >= 3.0) {
                val prefColor = ColorUtils.setAlphaComponent(style.borderColorArgb, 95)
                val prefWidth = max(0.7f, 0.0015f * boxW * zoomFactor)
                drawRings(slice.secondaryBorders, prefWidth, prefColor, withHalo = false)
                drawRings(slice.primaryCoastline, borderStroke, style.borderColorArgb, withHalo = true)
            } else {
                drawRings(slice.secondaryBorders, borderStroke, style.borderColorArgb, withHalo = true)
            }
            if (slice.waterCutouts.isNotEmpty()) {
                val lakeShoreColor = ColorUtils.setAlphaComponent(style.borderColorArgb, 200)
                strokePaint.strokeWidth = borderStroke * 0.9f
                strokePaint.color = lakeShoreColor
                haloPaint.strokeWidth = borderStroke * 1.5f
                for (lake in slice.waterCutouts) {
                    if (isRingOutsideFrustum(lake)) continue
                    buildProjectedPath(lake, proj, ::toScreenX, ::toScreenY, closePoly = true, mapRect = mapRect, outPath = scratchPath)
                    if (!scratchPath.isEmpty) {
                        canvas.drawPath(scratchPath, clearPaint)
                        canvas.drawPath(scratchPath, haloPaint)
                        canvas.drawPath(scratchPath, strokePaint)
                    }
                }
            }
        } else {
            drawRings(slice.primaryCoastline, borderStroke, style.borderColorArgb, withHalo = true)
        }

        canvas.restoreToCount(layerId)

        // Draw Routes & Markers inside clipped mapRect
        canvas.save()
        canvas.clipRect(mapRect)

        val routeStroke = max(2.2f, 0.01f * boxW)
        val markerR = max(6.0f, 0.04f * boxW)
        val routeIsLight = ColorUtils.calculateLuminance(style.routeColorArgb) > 0.45

        val hasGroundPolyline = info.mode != TransportMode.AIRPLANE &&
            info.mode != TransportMode.SPOT &&
            info.routePolylines.any { it.size >= 2 }

        if (hasGroundPolyline) {
            val casingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = 1.65f * routeStroke
                color = if (ColorUtils.calculateLuminance(style.routeColorArgb) > 0.85) {
                    Color.argb(160, 0, 0, 0)
                } else {
                    Color.WHITE
                }
            }
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = routeStroke
                color = style.routeColorArgb
            }
            for (poly in info.routePolylines) {
                if (poly.size < 2) continue
                scratchPath.reset()
                var prevAdjLon = proj.normalizeLon(poly[0].lon)
                scratchPath.moveTo(toScreenX(poly[0].lon, poly[0].lat), toScreenY(poly[0].lat))
                for (i in 1 until poly.size) {
                    val adjLon = proj.normalizeLon(poly[i].lon)
                    val sx = toScreenX(poly[i].lon, poly[i].lat)
                    val sy = toScreenY(poly[i].lat)
                    if (abs(adjLon - prevAdjLon) > 170.0) {
                        scratchPath.moveTo(sx, sy)
                    } else {
                        scratchPath.lineTo(sx, sy)
                    }
                    prevAdjLon = adjLon
                }
                canvas.drawPath(scratchPath, casingPaint)
                canvas.drawPath(scratchPath, corePaint)
            }
        } else if (start != null && end != null) {
            val sx = toScreenX(start.lon, start.lat)
            val sy = toScreenY(start.lat)
            val ex = toScreenX(end.lon, end.lat)
            val ey = toScreenY(end.lat)

            scratchPath.reset()
            if (validMidCur != null) {
                val mx = toScreenX(validMidCur.lon, validMidCur.lat)
                val my = toScreenY(validMidCur.lat)
                val lift = 30f * unit
                scratchPath.moveTo(sx, sy)
                scratchPath.quadTo((sx + mx) * 0.5f, min(sy, my) - lift, mx, my)
                scratchPath.quadTo((mx + ex) * 0.5f, min(my, ey) - lift, ex, ey)
            } else {
                val chord = hypot(ex - sx, ey - sy)
                val lift = min(42f * unit, chord * 0.24f)
                scratchPath.moveTo(sx, sy)
                scratchPath.quadTo((sx + ex) * 0.5f, min(sy, ey) - lift, ex, ey)
            }

            val dash = DashPathEffect(floatArrayOf(0.85f * routeStroke, 2.15f * routeStroke), 0f)
            val arcHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = 1.6f * routeStroke
                color = if (routeIsLight) Color.argb(130, 0, 0, 0) else Color.argb(155, 255, 255, 255)
                pathEffect = dash
            }
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeWidth = routeStroke
                color = style.routeColorArgb
                pathEffect = dash
            }
            canvas.drawPath(scratchPath, arcHaloPaint)
            canvas.drawPath(scratchPath, arcPaint)
        }

        // Endpoint & Mid-Route Markers
        if (info.mode == TransportMode.SPOT || (start == null && end == null)) {
            val pt = rawCur ?: focusPoints.first()
            // Spec §5.4.B: Single-point Spot uses Sky Blue #4FC3F7
            drawBullseyeMarker(canvas, toScreenX(pt.lon, pt.lat), toScreenY(pt.lat), markerR, 0xFF4FC3F7.toInt())
        } else if (start != null && end != null) {
            val sx = toScreenX(start.lon, start.lat)
            val sy = toScreenY(start.lat)
            val ex = toScreenX(end.lon, end.lat)
            val ey = toScreenY(end.lat)

            drawBullseyeMarker(canvas, ex, ey, markerR, 0xFFFF7043.toInt())

            if (info.mode == TransportMode.AIRPLANE) {
                if (validMidCur != null) {
                    drawBullseyeMarker(canvas, sx, sy, markerR, 0xFF4FC3F7.toInt())
                    val mx = toScreenX(validMidCur.lon, validMidCur.lat)
                    val my = toScreenY(validMidCur.lat)
                    val angleDeg = Math.toDegrees(atan2((ey - my).toDouble(), (ex - mx).toDouble())).toFloat() + 90f
                    drawAirplaneDiscMarker(canvas, mx, my, markerR * 1.25f, angleDeg)
                } else if (style.showOriginPlaneBadge) {
                    val angleDeg = Math.toDegrees(atan2((ey - sy).toDouble(), (ex - sx).toDouble())).toFloat() + 90f
                    drawAirplaneDiscMarker(canvas, sx, sy, markerR * 1.25f, angleDeg)
                } else {
                    drawBullseyeMarker(canvas, sx, sy, markerR, 0xFF4FC3F7.toInt())
                }
            } else {
                drawBullseyeMarker(canvas, sx, sy, markerR, 0xFF4FC3F7.toInt())
                if (validMidCur != null) {
                    drawTeardropPin(canvas, toScreenX(validMidCur.lon, validMidCur.lat), toScreenY(validMidCur.lat), markerR * 1.35f)
                }
            }
        }

        canvas.restore()
        return mapRect
    }

    /**
     * Meridian-safe projected path builder:
     * Checks `abs(adjLon - prevAdjLon) > 170.0` in normalized space so BOTH ±180° (unwrapped)
     * AND 0° Prime Meridian (when wrapAntimeridian == true) split cleanly without horizontal slash lines!
     */
    internal fun buildProjectedPath(
        ring: PackedRing,
        proj: MapProjection,
        toX: (Double, Double) -> Float,
        toY: (Double) -> Float,
        closePoly: Boolean,
        mapRect: RectF,
        outPath: Path
    ): Boolean {
        outPath.reset()
        val pairs = ring.lonLatPairs
        val n = ring.pointCount
        if (n < 2) return false

        var jumped = false
        var hasVisibleSegment = false

        val firstLon = pairs[0].toDouble()
        val firstLat = pairs[1].toDouble()
        var prevAdjLon = proj.normalizeLon(firstLon)
        var prevSx = toX(firstLon, firstLat)
        var prevSy = toY(firstLat)
        outPath.moveTo(prevSx, prevSy)

        val cullPad = 24f
        for (i in 1 until n) {
            val lon = pairs[i * 2].toDouble()
            val lat = pairs[i * 2 + 1].toDouble()
            val adjLon = proj.normalizeLon(lon)
            val sx = toX(lon, lat)
            val sy = toY(lat)

            if (abs(adjLon - prevAdjLon) > 170.0) {
                outPath.moveTo(sx, sy)
                jumped = true
            } else {
                outPath.lineTo(sx, sy)
                val segMinX = min(prevSx, sx)
                val segMaxX = max(prevSx, sx)
                val segMinY = min(prevSy, sy)
                val segMaxY = max(prevSy, sy)
                if (segMaxX >= mapRect.left - cullPad && segMinX <= mapRect.right + cullPad &&
                    segMaxY >= mapRect.top - cullPad && segMinY <= mapRect.bottom + cullPad
                ) {
                    hasVisibleSegment = true
                }
            }
            prevAdjLon = adjLon
            prevSx = sx
            prevSy = sy
        }

        if (!hasVisibleSegment) {
            outPath.reset()
            return false
        }

        if (closePoly && ring.isClosedPolygon && !jumped && ring.pointCount >= 3) {
            outPath.close()
        }
        return !jumped
    }

    private fun drawBullseyeMarker(canvas: Canvas, cx: Float, cy: Float, r: Float, colorArgb: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL
        p.color = ColorUtils.setAlphaComponent(colorArgb, 62)
        canvas.drawCircle(cx, cy, 1.65f * r, p)

        p.color = colorArgb
        canvas.drawCircle(cx, cy, 0.82f * r, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.24f * r
        p.color = Color.WHITE
        canvas.drawCircle(cx, cy, 0.88f * r, p)

        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        canvas.drawCircle(cx, cy, 0.26f * r, p)
    }

    private fun drawAirplaneDiscMarker(canvas: Canvas, cx: Float, cy: Float, r: Float, angleDeg: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL
        p.color = Color.argb(65, 79, 195, 247)
        canvas.drawCircle(cx, cy, r * 1.28f, p)

        p.setShadowLayer(r * 0.35f, 0f, r * 0.1f, Color.argb(110, 0, 0, 0))
        p.color = Color.WHITE
        canvas.drawCircle(cx, cy, r * 0.95f, p)
        p.clearShadowLayer()

        val jet = createJetPath()
        val m = Matrix()
        val s = (r * 0.95f) / 14.5f
        m.postScale(s, s)
        m.postRotate(angleDeg)
        m.postTranslate(cx, cy)
        jet.transform(m)

        p.color = 0xFF263238.toInt()
        canvas.drawPath(jet, p)
    }

    private fun drawTeardropPin(canvas: Canvas, px: Float, py: Float, r: Float) {
        val pinPath = Path().apply {
            moveTo(px, py)
            cubicTo(px - r * 0.9f, py - r * 1.1f, px - r * 0.95f, py - r * 2.2f, px, py - r * 2.2f)
            cubicTo(px + r * 0.95f, py - r * 2.2f, px + r * 0.9f, py - r * 1.1f, px, py)
            close()
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL
        p.color = 0xFFE53935.toInt()
        canvas.drawPath(pinPath, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = max(1.8f, r * 0.18f)
        p.color = Color.WHITE
        canvas.drawPath(pinPath, p)

        p.style = Paint.Style.FILL
        canvas.drawCircle(px, py - r * 1.45f, r * 0.34f, p)
    }

    private sealed interface TextRowSpec {
        val height: Float
        val width: Float

        data class SimpleLine(
            val text: String,
            val paint: Paint,
            override val width: Float,
            override val height: Float
        ) : TextRowSpec

        data class DualColumnLine(
            val primary: String,
            val primaryPaint: Paint,
            val secondary: String,
            val secondaryPaint: Paint,
            val gap: Float,
            override val width: Float,
            override val height: Float
        ) : TextRowSpec

        data class FlightIataLine(
            val dep: String,
            val arr: String,
            val paint: Paint,
            val iconSize: Float,
            val iconGap: Float,
            override val width: Float,
            override val height: Float
        ) : TextRowSpec

        data class ArrowPairLine(
            val leftText: String?,
            val rightText: String,
            val paint: Paint,
            val arrowWidth: Float,
            val arrowGap: Float,
            override val width: Float,
            override val height: Float
        ) : TextRowSpec

        data class SubAirportLine(
            val name: String,
            val withSmallPlane: Boolean,
            val paint: Paint,
            override val width: Float,
            override val height: Float
        ) : TextRowSpec

        data class LocationPinLine(
            val text: String,
            val paint: Paint,
            val pinSize: Float,
            override val width: Float,
            override val height: Float
        ) : TextRowSpec
    }

    private fun drawTextOverlay(
        context: Context,
        canvas: Canvas,
        W: Float,
        H: Float,
        unit: Float,
        textMargin: Float,
        info: TransportInfo,
        style: OverlayStyle
    ): RectF {
        val scale = style.textScale.coerceIn(0.5f, 2.5f)
        val u = unit * scale
        val customTypeface = style.font.resolveTypeface(context)
        val cjkBold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val cjkRegular = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        val shadowColor = if (ColorUtils.calculateLuminance(style.textColorArgb) < 0.25) {
            Color.argb(120, 255, 255, 255)
        } else {
            Color.argb(180, 0, 0, 0)
        }

        fun makePaint(tf: Typeface, sizePx: Float, alpha: Int = 255, shadowMul: Float = 1f): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                typeface = tf
                textSize = sizePx
                color = ColorUtils.setAlphaComponent(style.textColorArgb, alpha)
                setShadowLayer(8f * u * shadowMul, 0f, 1.2f * u, shadowColor)
            }

        val rows = mutableListOf<TextRowSpec>()

        when (info.mode) {
            TransportMode.AIRPLANE -> {
                val flt = info.buildFlightCode()
                val reg = info.registrationNumber.trim()
                if (flt.isNotBlank() || reg.isNotBlank()) {
                    if (flt.isNotBlank() && reg.isNotBlank()) {
                        val pMain = makePaint(selectGlyphSafeTypeface(style.font, customTypeface, cjkBold, flt), 46f * u)
                        val pSub = makePaint(selectGlyphSafeTypeface(style.font, customTypeface, cjkBold, reg), 39f * u, alpha = 235)
                        val gap = 12f * u
                        val w = pMain.measureText(flt) + gap + pSub.measureText(reg)
                        rows.add(TextRowSpec.DualColumnLine(flt, pMain, reg, pSub, gap, w, 54f * u))
                    } else {
                        val txt = flt.ifBlank { reg }
                        val pMain = makePaint(selectGlyphSafeTypeface(style.font, customTypeface, cjkBold, txt), 46f * u)
                        rows.add(TextRowSpec.SimpleLine(txt, pMain, pMain.measureText(txt), 54f * u))
                    }
                }

                val dep = info.departureAirportCode.trim().uppercase()
                val arr = info.arrivalAirportCode.trim().uppercase()
                if (dep.isNotBlank() && arr.isNotBlank()) {
                    val pCode = makePaint(selectGlyphSafeTypeface(style.font, customTypeface, cjkBold, "$dep$arr"), 52f * u)
                    val iconSize = 42f * u
                    val iconGap = 15f * u
                    val w = pCode.measureText(dep) + iconGap + iconSize + iconGap + pCode.measureText(arr)
                    rows.add(TextRowSpec.FlightIataLine(dep, arr, pCode, iconSize, iconGap, w, 62f * u))
                } else if (dep.isNotBlank() || arr.isNotBlank()) {
                    val only = dep.ifBlank { arr }
                    val pCode = makePaint(selectGlyphSafeTypeface(style.font, customTypeface, cjkBold, only), 52f * u)
                    rows.add(TextRowSpec.SimpleLine(only, pCode, pCode.measureText(only), 62f * u))
                }

                val pAirportSub = makePaint(cjkRegular, 24f * u, alpha = 225, shadowMul = 0.75f)
                if (info.departureAirportName.isNotBlank()) {
                    val t = info.departureAirportName.trim()
                    rows.add(TextRowSpec.SubAirportLine(t, false, pAirportSub, pAirportSub.measureText(t), 30f * u))
                }
                if (info.arrivalAirportName.isNotBlank()) {
                    val t = info.arrivalAirportName.trim()
                    val w = pAirportSub.measureText(t) + 28f * u
                    rows.add(TextRowSpec.SubAirportLine(t, true, pAirportSub, w, 32f * u))
                }
            }

            TransportMode.TRAIN -> {
                if (info.railwayLineName.isNotBlank()) {
                    val t = info.railwayLineName.trim()
                    val pLine = makePaint(selectGlyphSafeTypeface(style.font, customTypeface, cjkBold, t), 46f * u)
                    rows.add(TextRowSpec.SimpleLine(t, pLine, pLine.measureText(t), 54f * u))
                }
                val tName = info.trainName.trim()
                val vModel = info.vehicleModel.trim()
                if (tName.isNotBlank() || vModel.isNotBlank()) {
                    if (tName.isNotBlank() && vModel.isNotBlank()) {
                        val pPrimary = makePaint(selectGlyphSafeTypeface(style.font, customTypeface, cjkBold, tName), 45f * u)
                        val pModel = makePaint(selectGlyphSafeTypeface(style.font, customTypeface, cjkBold, vModel), 39f * u, alpha = 230)
                        val gap = 12f * u
                        val w = pPrimary.measureText(tName) + gap + pModel.measureText(vModel)
                        rows.add(TextRowSpec.DualColumnLine(tName, pPrimary, vModel, pModel, gap, w, 52f * u))
                    } else {
                        val single = tName.ifBlank { vModel }
                        val pSingle = makePaint(selectGlyphSafeTypeface(style.font, customTypeface, cjkBold, single), 45f * u)
                        rows.add(TextRowSpec.SimpleLine(single, pSingle, pSingle.measureText(single), 52f * u))
                    }
                }
                addArrowEndpointRows(rows, info.departureStationName, info.arrivalStationName, style.font, customTypeface, cjkBold, u, W, ::makePaint)
            }

            TransportMode.OTHER -> {
                if (info.transportName.isNotBlank()) {
                    val t = info.transportName.trim()
                    val pTr = makePaint(selectGlyphSafeTypeface(style.font, customTypeface, cjkBold, t), 46f * u)
                    rows.add(TextRowSpec.SimpleLine(t, pTr, pTr.measureText(t), 54f * u))
                }
                addArrowEndpointRows(rows, info.departurePlaceName, info.destinationPlaceName, style.font, customTypeface, cjkBold, u, W, ::makePaint)
            }

            TransportMode.SPOT -> {
                for (raw in listOf(info.spotLine1, info.spotLine2, info.spotLine3)) {
                    val t = raw.trim()
                    if (t.isNotBlank()) {
                        val pSpot = makePaint(selectGlyphSafeTypeface(style.font, customTypeface, cjkBold, t), 48f * u)
                        rows.add(TextRowSpec.SimpleLine(t, pSpot, pSpot.measureText(t), 56f * u))
                    }
                }
            }
        }

        // Location Line
        val locText: String? = when (info.locationDisplayMode) {
            LocationDisplayMode.NONE -> null
            LocationDisplayMode.NAME -> info.photoLocationName.trim().ifBlank {
                if (info.photoLat != null && info.photoLon != null) {
                    RoutingAndPlaceService.formatLatLon(info.photoLat, info.photoLon)
                } else null
            }
            LocationDisplayMode.COORDINATES -> {
                if (info.photoLat != null && info.photoLon != null) {
                    RoutingAndPlaceService.formatLatLon(info.photoLat, info.photoLon)
                } else info.photoLocationName.trim().ifBlank { null }
            }
        }
        if (!locText.isNullOrBlank()) {
            val pLoc = makePaint(cjkBold, 28f * u, alpha = 255, shadowMul = 0.75f)
            val pinSize = 26f * u
            val w = pinSize + 8f * u + pLoc.measureText(locText)
            rows.add(TextRowSpec.LocationPinLine(locText, pLoc, pinSize, w, 38f * u))
        }

        // Date / Time Line (Spec §5.5.B: Typeface.DEFAULT_BOLD, alpha = 200)
        val dtText: String? = when (info.dateTimeDisplayMode) {
            DateTimeDisplayMode.NONE -> null
            DateTimeDisplayMode.DATE_ONLY -> String.format(Locale.US, "%04d/%02d/%02d", info.year, info.month, info.day)
            DateTimeDisplayMode.DATE_AND_TIME -> String.format(
                Locale.US,
                "%04d/%02d/%02d %02d:%02d",
                info.year,
                info.month,
                info.day,
                info.hour,
                info.minute
            )
        }
        if (!dtText.isNullOrBlank()) {
            val pDt = makePaint(cjkBold, 25f * u, alpha = 205, shadowMul = 0.75f)
            rows.add(TextRowSpec.SimpleLine(dtText, pDt, pDt.measureText(dtText), 34f * u))
        }

        if (rows.isEmpty()) {
            return RectF(textMargin, textMargin, textMargin + 100f * u, textMargin + 50f * u)
        }

        val blockW = rows.maxOf { it.width }
        val blockH = rows.sumOf { it.height.toDouble() }.toFloat()

        val alignRight = style.textCorner == OverlayCorner.TOP_RIGHT || style.textCorner == OverlayCorner.BOTTOM_RIGHT
        val minBound = textMargin * 0.3f
        val free = style.textFreePos
        val left: Float
        val top: Float
        if (free != null) {
            val maxLeft = (W - blockW - minBound).coerceAtLeast(minBound)
            val maxTop = (H - blockH - minBound).coerceAtLeast(minBound)
            left = (free.x * W - blockW * 0.5f).coerceIn(minBound, maxLeft)
            top = (free.y * H - blockH * 0.5f).coerceIn(minBound, maxTop)
        } else {
            left = if (alignRight) (W - textMargin - blockW).coerceAtLeast(minBound) else textMargin
            top = when (style.textCorner) {
                OverlayCorner.TOP_LEFT, OverlayCorner.TOP_RIGHT -> textMargin
                OverlayCorner.BOTTOM_LEFT, OverlayCorner.BOTTOM_RIGHT -> (H - textMargin - blockH).coerceAtLeast(minBound)
            }
        }

        var cursorY = top
        for (row in rows) {
            val rowLeft = if (alignRight) (left + blockW - row.width) else left
            val baseline = cursorY + row.height * 0.78f
            when (row) {
                is TextRowSpec.SimpleLine -> {
                    canvas.drawText(row.text, rowLeft, baseline, row.paint)
                }

                is TextRowSpec.DualColumnLine -> {
                    canvas.drawText(row.primary, rowLeft, baseline, row.primaryPaint)
                    val secX = rowLeft + row.primaryPaint.measureText(row.primary) + row.gap
                    canvas.drawText(row.secondary, secX, baseline, row.secondaryPaint)
                }

                is TextRowSpec.FlightIataLine -> {
                    canvas.drawText(row.dep, rowLeft, baseline, row.paint)
                    val depW = row.paint.measureText(row.dep)
                    val iconCx = rowLeft + depW + row.iconGap + row.iconSize * 0.5f
                    val iconCy = baseline - row.iconSize * 0.40f
                    drawInlinePlaneGlyph(canvas, iconCx, iconCy, row.iconSize, style.textColorArgb, shadowColor, u)
                    val arrX = rowLeft + depW + row.iconGap * 2f + row.iconSize
                    canvas.drawText(row.arr, arrX, baseline, row.paint)
                }

                is TextRowSpec.ArrowPairLine -> {
                    var curX = rowLeft
                    if (!row.leftText.isNullOrBlank()) {
                        canvas.drawText(row.leftText, curX, baseline, row.paint)
                        curX += row.paint.measureText(row.leftText) + row.arrowGap
                    }
                    val arrowCy = baseline - row.arrowWidth * 0.45f
                    drawInlineArrowGlyph(canvas, curX, arrowCy, row.arrowWidth, style.textColorArgb, shadowColor, u)
                    curX += row.arrowWidth + row.arrowGap
                    canvas.drawText(row.rightText, curX, baseline, row.paint)
                }

                is TextRowSpec.SubAirportLine -> {
                    if (row.withSmallPlane) {
                        val smallSize = 19f * u
                        drawInlinePlaneGlyph(
                            canvas,
                            rowLeft + smallSize * 0.5f,
                            baseline - smallSize * 0.42f,
                            smallSize,
                            ColorUtils.setAlphaComponent(style.textColorArgb, 230),
                            shadowColor,
                            u * 0.6f
                        )
                        canvas.drawText(row.name, rowLeft + 28f * u, baseline, row.paint)
                    } else {
                        canvas.drawText(row.name, rowLeft, baseline, row.paint)
                    }
                }

                is TextRowSpec.LocationPinLine -> {
                    val pinCx = rowLeft + row.pinSize * 0.45f
                    val pinBottomY = baseline + 2f * u
                    drawRoundPushpinIcon(canvas, pinCx, pinBottomY, row.pinSize)
                    canvas.drawText(row.text, rowLeft + row.pinSize + 8f * u, baseline, row.paint)
                }
            }
            cursorY += row.height
        }

        return RectF(left, top, left + blockW, top + blockH)
    }

    private fun addArrowEndpointRows(
        rows: MutableList<TextRowSpec>,
        depRaw: String,
        arrRaw: String,
        font: OverlayFont,
        customTf: Typeface,
        cjkBold: Typeface,
        u: Float,
        W: Float,
        makePaint: (Typeface, Float, Int, Float) -> Paint
    ) {
        val dep = depRaw.trim()
        val arr = arrRaw.trim()
        if (dep.isBlank() && arr.isBlank()) return
        val tf = selectGlyphSafeTypeface(font, customTf, cjkBold, "$dep$arr")
        val p = makePaint(tf, 46f * u, 255, 1f)
        if (dep.isBlank() || arr.isBlank()) {
            val single = dep.ifBlank { arr }
            rows.add(TextRowSpec.SimpleLine(single, p, p.measureText(single), 54f * u))
            return
        }
        val arrowW = 34f * u
        val gap = 12f * u
        val singleW = p.measureText(dep) + gap + arrowW + gap + p.measureText(arr)
        if (singleW > 0.50f * W) {
            rows.add(TextRowSpec.SimpleLine(dep, p, p.measureText(dep), 52f * u))
            val line2W = arrowW + gap + p.measureText(arr)
            rows.add(TextRowSpec.ArrowPairLine(null, arr, p, arrowW, gap, line2W, 54f * u))
        } else {
            rows.add(TextRowSpec.ArrowPairLine(dep, arr, p, arrowW, gap, singleW, 54f * u))
        }
    }

    /**
     * Verifies glyph coverage directly via `Paint.hasGlyph` so Latin-only bundled fonts
     * (like Artemis Inter's 143 glyphs or Barlow) NEVER render missing-glyph tofu!
     */
    internal fun selectGlyphSafeTypeface(
        font: OverlayFont,
        custom: Typeface,
        cjkFallback: Typeface,
        text: String
    ): Typeface {
        if (font == OverlayFont.DEFAULT || text.isBlank()) return custom
        if (font != OverlayFont.KAISEI_TOKUMIN && text.any { it.code >= 0x3000 }) {
            return cjkFallback
        }
        val probe = Paint().apply { typeface = custom }
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            if (!Character.isWhitespace(cp)) {
                val sub = text.substring(i, i + charCount)
                if (!probe.hasGlyph(sub)) return cjkFallback
            }
            i += charCount
        }
        return custom
    }

    private fun drawInlinePlaneGlyph(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        colorArgb: Int,
        shadowColor: Int,
        u: Float
    ) {
        val jet = createJetPath()
        val m = Matrix()
        val s = size / 21f
        m.postScale(s, s)
        m.postRotate(90f)
        m.postTranslate(cx, cy)
        jet.transform(m)

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.FILL
            color = colorArgb
            setShadowLayer(6f * u, 0f, 1f * u, shadowColor)
        }
        canvas.drawPath(jet, p)
    }

    /**
     * Crisp, bold vector right-arrow (`→`) scaled proportionally to headline cap-height.
     */
    private fun drawInlineArrowGlyph(
        canvas: Canvas,
        leftX: Float,
        centerY: Float,
        width: Float,
        colorArgb: Int,
        shadowColor: Int,
        u: Float
    ) {
        val rightX = leftX + width
        val headLen = width * 0.34f
        val strokeW = max(2.2f, 4.4f * u)
        val path = Path().apply {
            moveTo(leftX, centerY)
            lineTo(rightX - strokeW * 0.4f, centerY)
            moveTo(rightX - headLen, centerY - headLen * 0.78f)
            lineTo(rightX, centerY)
            lineTo(rightX - headLen, centerY + headLen * 0.78f)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = strokeW
            color = colorArgb
            setShadowLayer(6f * u, 0f, 1f * u, shadowColor)
        }
        canvas.drawPath(path, p)
    }

    private fun drawRoundPushpinIcon(canvas: Canvas, cx: Float, bottomY: Float, size: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val needleTop = bottomY - size * 0.55f
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = max(1.8f, size * 0.11f)
        p.color = 0xFFCFD8DC.toInt()
        canvas.drawLine(cx, needleTop, cx, bottomY, p)

        val headCy = bottomY - size * 0.72f
        val headR = size * 0.28f
        p.style = Paint.Style.FILL
        p.setShadowLayer(size * 0.18f, 0f, size * 0.05f, Color.argb(120, 0, 0, 0))
        p.color = 0xFFE53935.toInt()
        canvas.drawCircle(cx, headCy, headR, p)
        p.clearShadowLayer()

        p.color = Color.argb(150, 255, 255, 255)
        canvas.drawCircle(cx - headR * 0.28f, headCy - headR * 0.28f, headR * 0.26f, p)
    }
}
