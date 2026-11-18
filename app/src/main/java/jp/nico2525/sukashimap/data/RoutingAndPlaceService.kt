package jp.nico2525.sukashimap.data

import android.content.Context
import android.location.Geocoder
import android.os.Build
import jp.nico2525.sukashimap.model.Coord
import jp.nico2525.sukashimap.model.GeoCoordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.hypot

data class PlaceCandidate(
    val name: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val countryCode: String
) {
    val displayLabel: String
        get() = if (subtitle.isNotBlank()) "$name ($subtitle)" else name
}

data class ReverseGeocodeResult(
    val locationLabel: String,
    val countryCode: String
)

class RoutingAndPlaceService(
    private val context: Context,
    private val mapVectorRepository: MapVectorRepository
) {
    private val offlineCuratedPlaces = listOf(
        PlaceCandidate("鎌倉", "神奈川県鎌倉市", 35.3192, 139.5467, "JP"),
        PlaceCandidate("箱根湯本", "神奈川県足柄下郡箱根町", 35.2324, 139.1069, "JP"),
        PlaceCandidate("江ノ島", "神奈川県藤沢市", 35.2998, 139.4803, "JP"),
        PlaceCandidate("熱海", "静岡県熱海市", 35.1036, 139.0777, "JP"),
        PlaceCandidate("河口湖", "山梨県南都留郡富士河口湖町", 35.5171, 138.7518, "JP"),
        PlaceCandidate("軽井沢", "長野県北佐久郡軽井沢町", 36.3427, 138.6352, "JP"),
        PlaceCandidate("日光東照宮", "栃木県日光市", 36.7581, 139.5989, "JP"),
        PlaceCandidate("東京タワー", "東京都港区", 35.6586, 139.7454, "JP"),
        PlaceCandidate("渋谷スカイ", "東京都渋谷区", 35.6585, 139.7022, "JP"),
        PlaceCandidate("横浜みなとみらい", "神奈川県横浜市", 35.4550, 139.6313, "JP"),
        PlaceCandidate("伊勢神宮", "三重県伊勢市", 34.4551, 136.7257, "JP"),
        PlaceCandidate("京都嵐山", "京都府京都市", 35.0116, 135.6767, "JP"),
        PlaceCandidate("天橋立", "京都府宮津市", 35.5700, 135.1917, "JP"),
        PlaceCandidate("淡路島", "兵庫県淡路市", 34.5947, 135.0203, "JP"),
        PlaceCandidate("しまなみ海道（尾道）", "広島県尾道市", 34.4048, 133.1932, "JP"),
        PlaceCandidate("今治", "愛媛県今治市", 34.0662, 132.9977, "JP"),
        PlaceCandidate("出雲大社", "島根県出雲市", 35.4020, 132.6854, "JP"),
        PlaceCandidate("阿蘇草千里", "熊本県阿蘇市", 32.8851, 131.0518, "JP"),
        PlaceCandidate("別府温泉", "大分県別府市", 33.2795, 131.4975, "JP"),
        PlaceCandidate("美ら海水族館", "沖縄県国頭郡本部町", 26.6943, 127.8779, "JP"),
        PlaceCandidate("古宇利島", "沖縄県国頭郡今帰仁村", 26.7061, 128.0177, "JP"),
        PlaceCandidate("小樽運河", "北海道小樽市", 43.1994, 141.0018, "JP"),
        PlaceCandidate("富良野", "北海道富良野市", 43.3421, 142.3832, "JP"),
        PlaceCandidate("知床五湖", "北海道斜里郡斜里町", 44.1228, 145.0805, "JP"),
        PlaceCandidate("竹芝客船ターミナル", "東京都港区（東海汽船）", 35.6542, 139.7634, "JP"),
        PlaceCandidate("伊豆大島（岡田港）", "東京都大島町", 34.7897, 139.3917, "JP"),
        PlaceCandidate("Honolulu", "Hawaii, United States", 21.3069, -157.8583, "US"),
        PlaceCandidate("San Francisco", "California, United States", 37.7749, -122.4194, "US"),
        PlaceCandidate("New York", "New York, United States", 40.7128, -74.0060, "US"),
        PlaceCandidate("London St Pancras", "United Kingdom", 51.5319, -0.1263, "GB"),
        PlaceCandidate("Paris Gare du Nord", "France", 48.8809, 2.3553, "FR"),
        PlaceCandidate("Taipei Main Station", "Taiwan", 25.0478, 121.5170, "TW"),
        PlaceCandidate("Seoul Station", "South Korea", 37.5547, 126.9707, "KR")
    )

    private data class PrefectureInfo(val nameEn: String, val nameJa: String, val lat: Double, val lon: Double)
    private val japanPrefectures = listOf(
        PrefectureInfo("Hokkaido", "北海道", 43.0642, 141.3469),
        PrefectureInfo("Aomori", "青森県", 40.8244, 140.7400),
        PrefectureInfo("Iwate", "岩手県", 39.7036, 141.1527),
        PrefectureInfo("Miyagi", "宮城県", 38.2688, 140.8721),
        PrefectureInfo("Akita", "秋田県", 39.7186, 140.1024),
        PrefectureInfo("Yamagata", "山形県", 38.2404, 140.3633),
        PrefectureInfo("Fukushima", "福島県", 37.7503, 140.4676),
        PrefectureInfo("Ibaraki", "茨城県", 36.3418, 140.4468),
        PrefectureInfo("Tochigi", "栃木県", 36.5657, 139.8836),
        PrefectureInfo("Gunma", "群馬県", 36.3911, 139.0608),
        PrefectureInfo("Saitama", "埼玉県", 35.8569, 139.6489),
        PrefectureInfo("Chiba", "千葉県", 35.6047, 140.1233),
        PrefectureInfo("Tokyo", "東京都", 35.6895, 139.6917),
        PrefectureInfo("Kanagawa", "神奈川県", 35.4478, 139.6425),
        PrefectureInfo("Niigata", "新潟県", 37.9026, 139.0232),
        PrefectureInfo("Toyama", "富山県", 36.6953, 137.2113),
        PrefectureInfo("Ishikawa", "石川県", 36.5947, 136.6256),
        PrefectureInfo("Fukui", "福井県", 36.0652, 136.2216),
        PrefectureInfo("Yamanashi", "山梨県", 35.6642, 138.5684),
        PrefectureInfo("Nagano", "長野県", 36.6513, 138.1810),
        PrefectureInfo("Gifu", "岐阜県", 35.3912, 136.7223),
        PrefectureInfo("Shizuoka", "静岡県", 34.9769, 138.3831),
        PrefectureInfo("Aichi", "愛知県", 35.1802, 136.9066),
        PrefectureInfo("Mie", "三重県", 34.7303, 136.5086),
        PrefectureInfo("Shiga", "滋賀県", 35.0045, 135.8686),
        PrefectureInfo("Kyoto", "京都府", 35.0211, 135.7556),
        PrefectureInfo("Osaka", "大阪府", 34.6863, 135.5200),
        PrefectureInfo("Hyogo", "兵庫県", 34.6913, 135.1830),
        PrefectureInfo("Nara", "奈良県", 34.6851, 135.8327),
        PrefectureInfo("Wakayama", "和歌山県", 34.2260, 135.1675),
        PrefectureInfo("Tottori", "鳥取県", 35.5039, 134.2377),
        PrefectureInfo("Shimane", "島根県", 35.4723, 133.0505),
        PrefectureInfo("Okayama", "岡山県", 34.6618, 133.9344),
        PrefectureInfo("Hiroshima", "広島県", 34.3966, 132.4596),
        PrefectureInfo("Yamaguchi", "山口県", 34.1859, 131.4706),
        PrefectureInfo("Tokushima", "徳島県", 34.0658, 134.5593),
        PrefectureInfo("Kagawa", "香川県", 34.3401, 134.0434),
        PrefectureInfo("Ehime", "愛媛県", 33.8416, 132.7657),
        PrefectureInfo("Kochi", "高知県", 33.5597, 133.5311),
        PrefectureInfo("Fukuoka", "福岡県", 33.6064, 130.4183),
        PrefectureInfo("Saga", "佐賀県", 33.2494, 130.2988),
        PrefectureInfo("Nagasaki", "長崎県", 32.7448, 129.8737),
        PrefectureInfo("Kumamoto", "熊本県", 32.7898, 130.7417),
        PrefectureInfo("Oita", "大分県", 33.2382, 131.6126),
        PrefectureInfo("Miyazaki", "宮崎県", 31.9111, 131.4239),
        PrefectureInfo("Kagoshima", "鹿児島県", 31.5602, 130.5581),
        PrefectureInfo("Okinawa", "沖縄県", 26.2124, 127.6809)
    )

    suspend fun searchPlaces(
        query: String,
        biasLat: Double? = null,
        biasLon: Double? = null
    ): List<PlaceCandidate> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext offlineCuratedPlaces.take(15)

        val localMatches = offlineCuratedPlaces.filter {
            it.name.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true)
        }

        var conn: HttpURLConnection? = null
        val remoteMatches = try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val biasParams = if (biasLat != null && biasLon != null) "&lat=$biasLat&lon=$biasLon" else ""
            val url = URL("https://photon.komoot.io/api/?q=$encoded&limit=12$biasParams")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "SukashiMapAndroid/1.0")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val features = JSONObject(body).optJSONArray("features") ?: JSONArray()
            val parsed = mutableListOf<PlaceCandidate>()
            for (i in 0 until features.length()) {
                val f = features.optJSONObject(i) ?: continue
                val geom = f.optJSONObject("geometry") ?: continue
                val coords = geom.optJSONArray("coordinates") ?: continue
                val lon = coords.optDouble(0)
                val lat = coords.optDouble(1)
                val props = f.optJSONObject("properties") ?: JSONObject()
                val name = props.optString("name").ifBlank { props.optString("city") }
                if (name.isBlank()) continue
                val city = props.optString("city")
                val state = props.optString("state")
                val country = props.optString("country")
                val cc = props.optString("countrycode").uppercase()
                val subtitle = listOf(city, state, country)
                    .filter { it.isNotBlank() && !it.equals(name, ignoreCase = true) }
                    .distinct()
                    .joinToString(", ")
                parsed.add(PlaceCandidate(name, subtitle, lat, lon, cc))
            }
            parsed
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn?.disconnect()
        }

        (localMatches + remoteMatches)
            .distinctBy { "${it.name}:${(it.latitude * 100).toInt()}:${(it.longitude * 100).toInt()}" }
            .take(18)
    }

    suspend fun fetchDrivingRoute(
        depLat: Double,
        depLon: Double,
        destLat: Double,
        destLon: Double,
        viaPhotoLat: Double? = null,
        viaPhotoLon: Double? = null
    ): List<List<Coord>> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val chord = hypot(destLat - depLat, destLon - depLon)
            val useVia = viaPhotoLat != null && viaPhotoLon != null &&
                hypot(viaPhotoLat - depLat, viaPhotoLon - depLon) > 0.01 &&
                hypot(viaPhotoLat - destLat, viaPhotoLon - destLon) > 0.01 &&
                hypot(viaPhotoLat - depLat, viaPhotoLon - depLon) + hypot(viaPhotoLat - destLat, viaPhotoLon - destLon) <= chord * 1.45
            val waypoints = if (useVia) {
                "$depLon,$depLat;$viaPhotoLon,$viaPhotoLat;$destLon,$destLat"
            } else {
                "$depLon,$depLat;$destLon,$destLat"
            }
            val url = URL("https://router.project-osrm.org/route/v1/driving/$waypoints?overview=full&geometries=geojson")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "SukashiMapAndroid/1.0")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val routes = JSONObject(body).optJSONArray("routes") ?: return@withContext emptyList()
            if (routes.length() == 0) return@withContext emptyList()
            val coordsArr = routes.getJSONObject(0)
                .getJSONObject("geometry")
                .getJSONArray("coordinates")
            val raw = ArrayList<Coord>(coordsArr.length())
            for (i in 0 until coordsArr.length()) {
                val c = coordsArr.getJSONArray(i)
                raw.add(Coord(c.getDouble(0), c.getDouble(1)))
            }
            val simplified = simplifyRdpMeters(raw, 150.0)
            if (simplified.size >= 2) listOf(simplified) else emptyList()
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): ReverseGeocodeResult = withContext(Dispatchers.IO) {
        val activeLocale = Locale.getDefault()
        val isJa = activeLocale.language == "ja"

        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, activeLocale)
                val addr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    withTimeoutOrNull(4_000) {
                        suspendCancellableCoroutine { cont ->
                            geocoder.getFromLocation(lat, lon, 1) { list ->
                                cont.resume(list.firstOrNull())
                            }
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
                }
                if (addr != null) {
                    val region = addr.adminArea ?: addr.locality ?: addr.subAdminArea ?: ""
                    val country = addr.countryName ?: ""
                    val cc = addr.countryCode ?: ""
                    val label = listOf(region, country).filter { it.isNotBlank() }.joinToString(", ")
                    if (label.isNotBlank()) {
                        return@withContext ReverseGeocodeResult(label, cc)
                    }
                }
            }
        } catch (_: Exception) {
        }

        // Offline Japan Prefecture Polygon match (matches exact enclosing prefecture ring first!)
        if (mapVectorRepository.isPointInJapanOrCoastalBuffer(lon, lat)) {
            val enclosingRing = mapVectorRepository.findEnclosingJapanPrefectureRing(lon, lat)
            val matchedPref = if (enclosingRing != null) {
                japanPrefectures.filter { enclosingRing.containsPoint(it.lon, it.lat) }
                    .minByOrNull { hypot(it.lat - lat, it.lon - lon) }
            } else null
            val fallbackPref = matchedPref ?: japanPrefectures.minByOrNull {
                val dLat = it.lat - lat
                val dLon = (it.lon - lon) * cos(Math.toRadians(lat))
                dLat * dLat + dLon * dLon
            }
            val label = if (isJa) {
                "${fallbackPref?.nameJa ?: "東京都"}, 日本"
            } else {
                "${fallbackPref?.nameEn ?: "Tokyo"}, Japan"
            }
            return@withContext ReverseGeocodeResult(label, "JP")
        }

        val bound = mapVectorRepository.detectCountryBound(lon, lat)
        if (bound != null) {
            return@withContext ReverseGeocodeResult(bound.name, bound.id)
        }
        ReverseGeocodeResult(formatLatLon(lat, lon), "")
    }

    companion object {
        fun formatLatLon(lat: Double, lon: Double): String = GeoCoordinate.format(lat, lon)

        /**
         * 100% Iterative Ramer-Douglas-Peucker simplification (zero StackOverflowError on 200k+ points!).
         */
        fun simplifyRdpMeters(points: List<Coord>, epsilonMeters: Double): List<Coord> {
            if (points.size <= 3) return points
            val midLatRad = Math.toRadians(points[points.size / 2].lat)
            val metersPerDegLat = 111_132.0
            val metersPerDegLon = 111_320.0 * cos(midLatRad).coerceAtLeast(0.1)

            fun perpDistMeters(pt: Coord, a: Coord, b: Coord): Double {
                val px = (pt.lon - a.lon) * metersPerDegLon
                val py = (pt.lat - a.lat) * metersPerDegLat
                val bx = (b.lon - a.lon) * metersPerDegLon
                val by = (b.lat - a.lat) * metersPerDegLat
                val len2 = bx * bx + by * by
                if (len2 < 1e-6) return hypot(px, py)
                val t = ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
                return hypot(px - t * bx, py - t * by)
            }

            val keep = BooleanArray(points.size)
            keep[0] = true
            keep[points.lastIndex] = true

            val stack = ArrayDeque<IntArray>()
            stack.addLast(intArrayOf(0, points.lastIndex))

            while (stack.isNotEmpty()) {
                val range = stack.removeLast()
                val startIdx = range[0]
                val endIdx = range[1]
                if (endIdx <= startIdx + 1) continue

                var maxD = 0.0
                var index = startIdx
                val a = points[startIdx]
                val b = points[endIdx]
                for (i in (startIdx + 1) until endIdx) {
                    val d = perpDistMeters(points[i], a, b)
                    if (d > maxD) {
                        maxD = d
                        index = i
                    }
                }
                if (maxD > epsilonMeters) {
                    keep[index] = true
                    stack.addLast(intArrayOf(startIdx, index))
                    stack.addLast(intArrayOf(index, endIdx))
                }
            }

            val out = ArrayList<Coord>()
            for (i in points.indices) {
                if (keep[i]) out.add(points[i])
            }
            return out
        }
    }
}
