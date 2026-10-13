package jp.nico2525.sukashimap.camera

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.os.Looper
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    val displayName: String?,
    val countryCode: String?,
    val coordinatesOnly: Boolean,
)

/** Fused-location fetch with Geocoder reverse lookup and a coarse country guess. */
class LocationHelper(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): ResolvedLocation? = withContext(Dispatchers.IO) {
        val loc: android.location.Location? = suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { location -> cont.resume(location) }
                .addOnFailureListener { cont.resume(null) }
        }
        if (loc == null) return@withContext null
        val lat = loc.latitude
        val lon = loc.longitude
        var name: String? = null
        var country: String? = guessCountry(lat, lon)
        runCatching {
            if (Geocoder.isPresent()) {
                @Suppress("DEPRECATION")
                val list = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
                val addr = list?.firstOrNull()
                if (addr != null) {
                    name = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: addr.countryName
                    val cc = addr.countryCode
                    if (!cc.isNullOrBlank()) country = cc.uppercase(Locale.US)
                }
            }
        }
        ResolvedLocation(lat, lon, name, country, name == null)
    }

    /** Rough bounding-box guess so map/offline logic works without a Geocoder hit. */
    fun guessCountry(lat: Double, lon: Double): String? = when {
        lat in 24.0..46.0 && lon in 122.0..146.0 -> "JP"
        lat in 24.0..50.0 && lon in -126.0..-66.0 -> "US"
        else -> null
    }
}
