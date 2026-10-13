package jp.nico2525.sukashimap.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class PhotonPlace(
    val name: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val countryCode: String?,
)

/** Minimal Photon (Komoot) forward-geocoding client used by the Other transport form. */
object PhotonApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, lang: String = "en", limit: Int = 8): List<PhotonPlace> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext emptyList()
            val safeLang = when (lang.lowercase().take(2)) {
                "de", "en", "fr" -> lang.lowercase().take(2)
                else -> "default"
            }
            val url = "https://photon.komoot.io/api/?q=" +
                URLEncoder.encode(q, "UTF-8") + "&lang=" + safeLang + "&limit=" + limit
            val req = Request.Builder().url(url).header("User-Agent", "sukashi-map/1.0").build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        android.util.Log.w("PhotonApi", "HTTP ${resp.code}")
                        return@use emptyList()
                    }
                    val body = resp.body?.string() ?: return@use emptyList()
                    val features = JSONObject(body).optJSONArray("features") ?: return@use emptyList()
                    List(features.length()) { i ->
                        val f = features.getJSONObject(i)
                        val props = f.optJSONObject("properties")
                        val geom = f.optJSONObject("geometry")?.optJSONArray("coordinates")
                        val lon = geom?.optDouble(0) ?: 0.0
                        val lat = geom?.optDouble(1) ?: 0.0
                        val name = props?.optString("name").orEmpty()
                        val city = props?.optString("city").orEmpty()
                        val country = props?.optString("country").orEmpty()
                        val cc = props?.optString("countrycode")?.uppercase().takeUnless { it.isNullOrBlank() }
                        val display = listOf(name, city, country).filter { it.isNotBlank() }.distinct()
                            .joinToString(", ").ifBlank { name }
                        PhotonPlace(name, display, lat, lon, cc)
                    }
                }
            }.onFailure { android.util.Log.w("PhotonApi", "search failed", it) }.getOrDefault(emptyList())
        }
}
