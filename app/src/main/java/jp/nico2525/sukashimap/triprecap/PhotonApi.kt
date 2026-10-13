package jp.nico2525.sukashimap.triprecap

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One Photon (Komoot) geocoder hit. */
data class PhotonPlace(
    val name: String,
    val country: String?,
    val lat: Double,
    val lon: Double,
)

/**
 * Minimal Photon API client: GET https://photon.komoot.io/api/?q=&limit=&lang=
 * Self-authored parsing of the GeoJSON response (no copied code).
 */
object PhotonApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    suspend fun search(query: String, lang: String, limit: Int = 8): List<PhotonPlace> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.isEmpty()) return@withContext emptyList()
            val rawLang = lang.substringBefore('-').lowercase(Locale.US)
            val language = when (rawLang) {
                "de", "en", "fr" -> rawLang
                else -> "default"
            }
            val url = okhttp3.HttpUrl.Builder()
                .scheme("https")
                .host("photon.komoot.io")
                .addPathSegment("api")
                .addQueryParameter("q", q)
                .addQueryParameter("limit", limit.coerceIn(1, 20).toString())
                .addQueryParameter("lang", language)
                .build()
            val req = Request.Builder().url(url).header("Accept", "application/json").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                parse(body)
            }
        }

    fun parse(body: String): List<PhotonPlace> {
        return try {
            val root = gson.fromJson(body, PhotonResponse::class.java) ?: return emptyList()
            root.features.mapNotNull { f ->
                val coords = f.geometry?.coordinates ?: return@mapNotNull null
                if (coords.size < 2) return@mapNotNull null
                val props = f.properties ?: return@mapNotNull null
                val name = props.name ?: return@mapNotNull null
                PhotonPlace(
                    name = name,
                    country = props.country,
                    lat = coords[1],
                    lon = coords[0],
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private data class PhotonResponse(
        val features: List<PhotonFeature> = emptyList(),
    )

    private data class PhotonFeature(
        val geometry: PhotonGeometry?,
        val properties: PhotonProps?,
    )

    private data class PhotonGeometry(
        val coordinates: List<Double>?,
    )

    private data class PhotonProps(
        val name: String?,
        val country: String?,
        @SerializedName("city") val city: String?,
    )
}
