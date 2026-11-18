package jp.nico2525.sukashimap.render

import jp.nico2525.sukashimap.model.Coord
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sign
import kotlin.math.tan

enum class ProjectionKind {
    SCALED_MERCATOR,
    ROBINSON
}

class MapProjection(
    val kind: ProjectionKind,
    val wrapAntimeridian: Boolean
) {
    fun normalizeLon(lon: Double): Double {
        return if (wrapAntimeridian && lon < 0.0) lon + 360.0 else lon
    }

    fun projectX(lon: Double, lat: Double): Float {
        val adjLon = normalizeLon(lon)
        return when (kind) {
            ProjectionKind.SCALED_MERCATOR -> (0.015 * adjLon).toFloat()
            ProjectionKind.ROBINSON -> {
                val clampedLat = lat.coerceIn(-90.0, 90.0)
                val plen = MapProjectionEngine.lerpRobinson(MapProjectionEngine.ROBINSON_PLEN, abs(clampedLat))
                (0.014812 * adjLon * plen).toFloat()
            }
        }
    }

    fun projectY(lat: Double): Float {
        return when (kind) {
            ProjectionKind.SCALED_MERCATOR -> {
                val clamped = lat.coerceIn(-85.0, 85.0)
                val rad = Math.toRadians(clamped)
                (-ln(tan(rad / 2.0 + Math.PI / 4.0)) * 0.85).toFloat()
            }
            ProjectionKind.ROBINSON -> {
                val clamped = lat.coerceIn(-90.0, 90.0)
                val pdfe = MapProjectionEngine.lerpRobinson(MapProjectionEngine.ROBINSON_PDFE, abs(clamped))
                (-sign(clamped) * pdfe * 1.3523).toFloat()
            }
        }
    }
}

object MapProjectionEngine {
    // 19-row Robinson lookup table at 5-degree intervals (0..90)
    internal val ROBINSON_PLEN = doubleArrayOf(
        1.0000, 0.9986, 0.9954, 0.9900, 0.9822,
        0.9730, 0.9600, 0.9427, 0.9216, 0.8962,
        0.8679, 0.8350, 0.7986, 0.7597, 0.7186,
        0.6732, 0.6213, 0.5722, 0.5322
    )

    internal val ROBINSON_PDFE = doubleArrayOf(
        0.0000, 0.0620, 0.1240, 0.1860, 0.2480,
        0.3100, 0.3720, 0.4340, 0.4958, 0.5571,
        0.6176, 0.6769, 0.7346, 0.7903, 0.8435,
        0.8936, 0.9394, 1.0000.let { 0.9761 }, 1.0000
    )

    internal fun lerpRobinson(table: DoubleArray, absLat: Double): Double {
        val c = absLat.coerceIn(0.0, 90.0)
        val pos = c / 5.0
        val idx = floor(pos).toInt().coerceIn(0, 17)
        val frac = pos - idx
        return table[idx] + (table[idx + 1] - table[idx]) * frac
    }

    fun selectProjection(points: List<Coord>): MapProjection {
        if (points.size < 2) {
            return MapProjection(ProjectionKind.SCALED_MERCATOR, wrapAntimeridian = false)
        }
        val minLonDirect = points.minOf { it.lon }
        val maxLonDirect = points.maxOf { it.lon }
        val spanDirect = maxLonDirect - minLonDirect

        val wrappedLons = points.map { if (it.lon < 0.0) it.lon + 360.0 else it.lon }
        val spanWrapped = wrappedLons.max() - wrappedLons.min()
        val wrap = spanDirect > 180.0 && spanWrapped < spanDirect

        val lonSpan = if (wrap) spanWrapped else spanDirect
        val latSpan = points.maxOf { it.lat } - points.minOf { it.lat }

        val kind = if (lonSpan >= 40.0 || latSpan >= 40.0) {
            ProjectionKind.ROBINSON
        } else {
            ProjectionKind.SCALED_MERCATOR
        }
        return MapProjection(kind, wrap)
    }
}
