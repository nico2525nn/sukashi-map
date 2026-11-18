package jp.nico2525.sukashimap.data

import android.content.Context
import android.util.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import kotlin.math.abs

/**
 * Compact, GC-friendly packed geographic ring/polyline:
 * [lon0, lat0, lon1, lat1, ...] with precomputed bounding box for O(1) frustum culling.
 */
class PackedRing(
    val lonLatPairs: FloatArray,
    val minLon: Float,
    val maxLon: Float,
    val minLat: Float,
    val maxLat: Float,
    val isClosedPolygon: Boolean
) {
    val pointCount: Int get() = lonLatPairs.size ushr 1

    fun containsPoint(lon: Double, lat: Double): Boolean {
        val x = lon.toFloat()
        val y = lat.toFloat()
        if (!isClosedPolygon || x < minLon || x > maxLon || y < minLat || y > maxLat || pointCount < 3) return false
        var inside = false
        val n = pointCount
        var j = n - 1
        for (i in 0 until n) {
            val xi = lonLatPairs[i * 2]
            val yi = lonLatPairs[i * 2 + 1]
            val xj = lonLatPairs[j * 2]
            val yj = lonLatPairs[j * 2 + 1]
            val intersect = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).let { if (abs(it) < 1e-7f) 1e-7f else it }) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    fun isNearVertex(lon: Double, lat: Double, tolDeg: Float): Boolean {
        val x = lon.toFloat()
        val y = lat.toFloat()
        if (x < minLon - tolDeg || x > maxLon + tolDeg || y < minLat - tolDeg || y > maxLat + tolDeg) return false
        val tol2 = tolDeg * tolDeg
        val n = pointCount
        for (i in 0 until n) {
            val dx = lonLatPairs[i * 2] - x
            val dy = lonLatPairs[i * 2 + 1] - y
            if (dx * dx + dy * dy <= tol2) return true
        }
        return false
    }
}

data class CountryBoundEntry(
    val id: String,
    val name: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val area: Double
)

data class CountryPolygonEntry(
    val id: String,
    val ring: PackedRing
)

class MapVectorRepository(private val context: Context) {
    private val cacheMutex = Mutex()
    private val ringCache = HashMap<String, List<PackedRing>>()
    @Volatile
    private var countryBoundsCache: List<CountryBoundEntry>? = null
    @Volatile
    private var countryPolygonsCache: List<CountryPolygonEntry>? = null
    @Volatile
    private var nonJapanEastAsiaCache: List<PackedRing>? = null

    suspend fun getJapanCoastline(): List<PackedRing> = getRings("japan_coastline_highres.json")
    suspend fun getJapanPrefectures(): List<PackedRing> = getRings("japan_prefectures.json")
    suspend fun getJapanWaterbodies(): List<PackedRing> = getRings("japan_waterbodies.json")
    suspend fun getUsStates(): List<PackedRing> = getRings("us_states.json")
    suspend fun getWorldMapFine(): List<PackedRing> = getRings("world_map_fine.json")
    suspend fun getWorldMapCoarse(): List<PackedRing> = getRings("world_map_coarse.json")

    /**
     * Neighboring non-Japan coastlines (Korea, Jeju, Taiwan, Sakhalin, Primorsky)
     * so wide Japan maps like KIX -> OKA also show Busan / Jeju in the corner.
     */
    suspend fun getNeighboringWorldRingsForJapan(): List<PackedRing> = withContext(Dispatchers.IO) {
        nonJapanEastAsiaCache?.let { return@withContext it }
        val jpCoast = getJapanCoastline()
        val worldFine = getWorldMapFine()
        val filtered = worldFine.filter { wr ->
            if (wr.maxLon < 115f || wr.minLon > 158f || wr.maxLat < 18f || wr.minLat > 52f) {
                false
            } else {
                // Keep ring only if a sample of its vertices are outside Japan's high-res coastline
                val midIdx = (wr.pointCount / 2) * 2
                val sampleLon = wr.lonLatPairs[midIdx].toDouble()
                val sampleLat = wr.lonLatPairs[midIdx + 1].toDouble()
                val firstLon = wr.lonLatPairs[0].toDouble()
                val firstLat = wr.lonLatPairs[1].toDouble()
                val inJp = jpCoast.any {
                    it.containsPoint(sampleLon, sampleLat) ||
                        it.isNearVertex(sampleLon, sampleLat, 0.08f) ||
                        it.containsPoint(firstLon, firstLat) ||
                        it.isNearVertex(firstLon, firstLat, 0.08f)
                }
                !inJp
            }
        }
        nonJapanEastAsiaCache = filtered
        filtered
    }

    suspend fun preloadCoreMaps() = withContext(Dispatchers.IO) {
        getJapanCoastline()
        getJapanPrefectures()
        getJapanWaterbodies()
        getWorldMapFine()
        getWorldMapCoarse()
        getCountryBounds()
    }

    suspend fun getRings(assetName: String): List<PackedRing> = withContext(Dispatchers.IO) {
        synchronized(ringCache) { ringCache[assetName] }?.let { return@withContext it }
        cacheMutex.withLock {
            synchronized(ringCache) { ringCache[assetName] }?.let { return@withLock it }
            val loaded = parseRingArrayAsset(assetName)
            synchronized(ringCache) { ringCache[assetName] = loaded }
            loaded
        }
    }

    suspend fun getCountryBounds(): List<CountryBoundEntry> = withContext(Dispatchers.IO) {
        countryBoundsCache?.let { return@withContext it }
        cacheMutex.withLock {
            countryBoundsCache?.let { return@withLock it }
            val list = ArrayList<CountryBoundEntry>(256)
            context.assets.open("country_bounds.json").bufferedReader().use { reader ->
                JsonReader(reader).use { jr ->
                    jr.beginArray()
                    while (jr.hasNext()) {
                        var id = ""
                        var name = ""
                        var minLat = 0.0
                        var maxLat = 0.0
                        var minLon = 0.0
                        var maxLon = 0.0
                        var area = 0.0
                        jr.beginObject()
                        while (jr.hasNext()) {
                            when (jr.nextName()) {
                                "id" -> id = jr.nextString()
                                "name" -> name = jr.nextString()
                                "minLat" -> minLat = jr.nextDouble()
                                "maxLat" -> maxLat = jr.nextDouble()
                                "minLon" -> minLon = jr.nextDouble()
                                "maxLon" -> maxLon = jr.nextDouble()
                                "area" -> area = jr.nextDouble()
                                else -> jr.skipValue()
                            }
                        }
                        jr.endObject()
                        list.add(CountryBoundEntry(id, name, minLat, maxLat, minLon, maxLon, area))
                    }
                    jr.endArray()
                }
            }
            countryBoundsCache = list
            list
        }
    }

    suspend fun getCountryPolygons(): List<CountryPolygonEntry> = withContext(Dispatchers.IO) {
        countryPolygonsCache?.let { return@withContext it }
        cacheMutex.withLock {
            countryPolygonsCache?.let { return@withLock it }
            val list = ArrayList<CountryPolygonEntry>(512)
            context.assets.open("country_polygons.json").bufferedReader().use { reader ->
                JsonReader(reader).use { jr ->
                    jr.beginArray()
                    while (jr.hasNext()) {
                        var id = ""
                        var ring: PackedRing? = null
                        jr.beginObject()
                        while (jr.hasNext()) {
                            when (jr.nextName()) {
                                "id" -> id = jr.nextString()
                                "points" -> ring = readSingleRing(jr)
                                else -> jr.skipValue()
                            }
                        }
                        jr.endObject()
                        if (id.isNotEmpty() && ring != null) {
                            list.add(CountryPolygonEntry(id, ring))
                        }
                    }
                    jr.endArray()
                }
            }
            countryPolygonsCache = list
            list
        }
    }

    /**
     * Checks both polygon interior AND ~4km offshore coastal buffer so offshore reclaimed
     * runways like Naha (OKA) and Kansai (KIX) reliably classify as Japan while excluding Korea!
     */
    suspend fun isPointInJapanOrCoastalBuffer(lon: Double, lat: Double): Boolean {
        if (lon !in 122.5..154.0 || lat !in 20.0..45.8) return false
        val coastline = getJapanCoastline()
        if (coastline.any { it.containsPoint(lon, lat) }) return true
        return coastline.any { it.isNearVertex(lon, lat, 0.045f) }
    }

    suspend fun isPointInUsStates(lon: Double, lat: Double): Boolean {
        if (lon !in -126.0..-66.5 || lat !in 24.0..49.8) return false
        val states = getUsStates()
        return states.any { it.containsPoint(lon, lat) || it.isNearVertex(lon, lat, 0.045f) }
    }

    suspend fun findEnclosingJapanPrefectureRing(lon: Double, lat: Double): PackedRing? {
        val prefs = getJapanPrefectures()
        val exact = prefs.filter { it.containsPoint(lon, lat) }
            .maxByOrNull { (it.maxLon - it.minLon) * (it.maxLat - it.minLat) }
        if (exact != null) return exact
        return prefs.filter { it.isNearVertex(lon, lat, 0.05f) }
            .maxByOrNull { (it.maxLon - it.minLon) * (it.maxLat - it.minLat) }
    }

    suspend fun detectCountryBound(lon: Double, lat: Double, isoHint: String = ""): CountryBoundEntry? {
        val bounds = getCountryBounds()
        if (isoHint.length == 2) {
            bounds.firstOrNull { it.id.equals(isoHint, ignoreCase = true) }?.let { return it }
        }
        val polygons = getCountryPolygons()
        val matchedIds = polygons.filter { it.ring.containsPoint(lon, lat) }.map { it.id }.toSet()
        if (matchedIds.isNotEmpty()) {
            return bounds.filter { it.id in matchedIds }.minByOrNull { it.area }
        }
        return bounds.filter {
            lat in it.minLat..it.maxLat && lon in it.minLon..it.maxLon
        }.minByOrNull { it.area }
    }

    private fun parseRingArrayAsset(assetName: String): List<PackedRing> {
        val rings = ArrayList<PackedRing>(2048)
        context.assets.open(assetName).use { raw ->
            JsonReader(InputStreamReader(raw, Charsets.UTF_8)).use { jr ->
                jr.beginArray()
                while (jr.hasNext()) {
                    val ring = readSingleRing(jr)
                    if (ring != null) rings.add(ring)
                }
                jr.endArray()
            }
        }
        return rings
    }

    private fun readSingleRing(jr: JsonReader): PackedRing? {
        var buf = FloatArray(128)
        var size = 0
        var minLon = Float.POSITIVE_INFINITY
        var maxLon = Float.NEGATIVE_INFINITY
        var minLat = Float.POSITIVE_INFINITY
        var maxLat = Float.NEGATIVE_INFINITY

        jr.beginArray()
        while (jr.hasNext()) {
            jr.beginArray()
            val lon = jr.nextDouble().toFloat()
            val lat = jr.nextDouble().toFloat()
            while (jr.hasNext()) jr.skipValue()
            jr.endArray()

            if (size + 2 > buf.size) {
                buf = buf.copyOf(buf.size * 2)
            }
            buf[size++] = lon
            buf[size++] = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
        }
        jr.endArray()

        val count = size ushr 1
        if (count < 2) return null
        val trimmed = if (size == buf.size) buf else buf.copyOf(size)
        val isClosed = count >= 4 &&
            abs(trimmed[0] - trimmed[size - 2]) < 0.05f &&
            abs(trimmed[1] - trimmed[size - 1]) < 0.05f
        return PackedRing(trimmed, minLon, maxLon, minLat, maxLat, isClosed)
    }
}
