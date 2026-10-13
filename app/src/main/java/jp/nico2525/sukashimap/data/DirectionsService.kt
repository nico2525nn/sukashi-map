package jp.nico2525.sukashimap.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Driving-route polyline provider. Primary: OSRM demo server (OSM data, no key).
 * Falls back to a straight line so the overlay map always draws something.
 */
object DirectionsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchDrivingRoutePolyline(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        simplifyToleranceMeters: Double = 150.0,
    ): List<String> = withContext(Dispatchers.IO) {
        val coords = fetchOsrm(fromLat, fromLon, toLat, toLon)
            ?: listOf(Pair(fromLon, fromLat), Pair(toLon, toLat))
        val simplified = simplify(coords, simplifyToleranceMeters)
        if (simplified.size < 2) return@withContext emptyList()
        listOf("[" + simplified.joinToString(",") { "[${it.first},${it.second}]" } + "]")
    }

    suspend fun fetchRoutePolylineViaPoint(
        fromLat: Double,
        fromLon: Double,
        viaLat: Double?,
        viaLon: Double?,
        toLat: Double,
        toLon: Double,
    ): List<String> {
        if (viaLat == null || viaLon == null) {
            return fetchDrivingRoutePolyline(fromLat, fromLon, toLat, toLon)
        }
        val leg1 = fetchDrivingRoutePolyline(fromLat, fromLon, viaLat, viaLon)
        val leg2 = fetchDrivingRoutePolyline(viaLat, viaLon, toLat, toLon)
        return leg1 + leg2
    }

    private fun fetchOsrm(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): List<Pair<Double, Double>>? {
        return try {
            val url = "https://router.project-osrm.org/route/v1/driving/$fromLon,$fromLat;$toLon,$toLat" +
                "?overview=full&geometries=geojson"
            val req = Request.Builder().url(url).header("User-Agent", "sukashi-map/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val routes = JSONObject(body).optJSONArray("routes") ?: return null
                if (routes.length() == 0) return null
                val coords = routes.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
                List(coords.length()) { i ->
                    val p = coords.getJSONArray(i)
                    Pair(p.getDouble(0), p.getDouble(1))
                }.takeIf { it.size >= 2 }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** RDP simplification on equirectangular-projected meters. */
    private fun simplify(coords: List<Pair<Double, Double>>, toleranceMeters: Double): List<Pair<Double, Double>> {
        if (coords.size < 3 || toleranceMeters <= 0) return coords
        val latMean = coords.map { it.second }.average()
        val kx = 111320.0 * kotlin.math.cos(Math.toRadians(latMean))
        val ky = 110540.0
        val pts = coords.map { Pair(it.first * kx, it.second * ky) }
        fun perp(p: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
            val dx = b.first - a.first
            val dy = b.second - a.second
            val l2 = dx * dx + dy * dy
            if (l2 == 0.0) return abs(p.first - a.first) + abs(p.second - a.second)
            var t = ((p.first - a.first) * dx + (p.second - a.second) * dy) / l2
            t = min(1.0, max(0.0, t))
            val px = a.first + t * dx - p.first
            val py = a.second + t * dy - p.second
            return kotlin.math.sqrt(px * px + py * py)
        }
        fun rdp(idx: List<Int>): List<Int> {
            if (idx.size < 3) return idx
            val a = pts[idx.first()]
            val b = pts[idx.last()]
            var dm = 0.0
            var im = 0
            for (i in 1 until idx.size - 1) {
                val d = perp(pts[idx[i]], a, b)
                if (d > dm) {
                    dm = d
                    im = i
                }
            }
            if (dm > toleranceMeters) {
                return rdp(idx.subList(0, im + 1)).dropLast(1) + rdp(idx.subList(im, idx.size))
            }
            return listOf(idx.first(), idx.last())
        }
        return rdp(coords.indices.toList()).map { coords[it] }
    }
}
