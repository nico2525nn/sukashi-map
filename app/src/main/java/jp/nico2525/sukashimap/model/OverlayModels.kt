package jp.nico2525.sukashimap.model

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import jp.nico2525.sukashimap.R
import kotlinx.serialization.Serializable

@Serializable
enum class TransportMode {
    AIRPLANE,
    TRAIN,
    OTHER,
    SPOT
}

@Serializable
enum class DateTimeDisplayMode {
    DATE_ONLY,
    DATE_AND_TIME,
    NONE
}

@Serializable
enum class LocationDisplayMode {
    NAME,
    COORDINATES,
    NONE
}

@Serializable
enum class OverlayCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

@Serializable
enum class OverlayFont(val id: String, val displayName: String, val fontResId: Int?) {
    DEFAULT("default", "Default", null),
    BARLOW("barlow", "Barlow", R.font.barlow_bold),
    JETBRAINS_MONO("jetbrains_mono", "JetBrains Mono", R.font.jetbrains_mono_bold),
    DM_SANS("dm_sans", "DM Sans", R.font.dm_sans_bold),
    OUTFIT("outfit", "Outfit", R.font.outfit_bold),
    ARTEMIS_INTER("artemis", "ARTEMIS INTER", R.font.artemis_inter),
    KAISEI_TOKUMIN("kaisei_tokumin", "Kaisei Tokumin", R.font.kaisei_tokumin_bold);

    fun resolveTypeface(context: Context): Typeface {
        val resId = fontResId ?: return Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        return try {
            ResourcesCompat.getFont(context, resId) ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        } catch (_: Exception) {
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    fun toComposeFontFamily(): FontFamily {
        val resId = fontResId ?: return FontFamily.SansSerif
        return FontFamily(Font(resId, FontWeight.Bold))
    }
}

object OverlayColorPresets {
    val swatchesArgb: List<Int> = listOf(
        0xFFFFFFFF.toInt(), // Pure White (Default)
        0xFF1A1A1A.toInt(), // Charcoal Black
        0xFF767676.toInt(), // Neutral Slate Gray
        0xFF0078D7.toInt(), // Aviation / Metro Blue
        0xFF002050.toInt(), // Midnight Navy
        0xFFFFB900.toInt(), // Amber Gold
        0xFFD83B01.toInt(), // Vermilion / Rust Orange
        0xFF008272.toInt(), // Deep Shinkansen Teal
        0xFF107C10.toInt()  // Forest / JR Green
    )
}

@Serializable
data class Coord(
    val lon: Double,
    val lat: Double
)

@Serializable
data class NormOffset(
    val x: Float,
    val y: Float
)

@Serializable
data class TransportInfo(
    val mode: TransportMode = TransportMode.AIRPLANE,

    // AIRPLANE fields
    val airlineCode: String = "NH",
    val airlineDisplayName: String = "ANA (All Nippon Airways)",
    val flightNumber: String = "1731",
    val registrationNumber: String = "",
    val departureAirportCode: String = "KIX",
    val departureAirportName: String = "Kansai International Airport",
    val departureAirportCity: String = "Osaka",
    val departureAirportCountry: String = "JP",
    val departureLat: Double? = 34.427299,
    val departureLon: Double? = 135.244003,
    val arrivalAirportCode: String = "OKA",
    val arrivalAirportName: String = "Naha Airport",
    val arrivalAirportCity: String = "Naha",
    val arrivalAirportCountry: String = "JP",
    val arrivalLat: Double? = 26.195801,
    val arrivalLon: Double? = 127.646004,

    // TRAIN fields
    val isDomesticJapanRail: Boolean = true,
    val railwayCompany: String = "JR東日本",
    val railwayLineName: String = "北陸新幹線",
    val trainName: String = "はくたか 570",
    val vehicleModel: String = "E7系",
    val departureStationName: String = "飯山",
    val departureStationGroupCode: String = "001769",
    val departureStationLat: Double? = 36.845143,
    val departureStationLon: Double? = 138.359011,
    val departureStationCountry: String = "JP",
    val arrivalStationName: String = "東京",
    val arrivalStationGroupCode: String = "003766",
    val arrivalStationLat: Double? = 35.681103,
    val arrivalStationLon: Double? = 139.767880,
    val arrivalStationCountry: String = "JP",
    val candidateViaLines: List<String> = emptyList(),
    val selectedViaLines: List<String> = emptyList(),
    val isAutoShortestVia: Boolean = true,

    // OTHER (Car / Ferry / Bus) fields
    val transportName: String = "湘南ドライブ",
    val departurePlaceName: String = "鎌倉",
    val departurePlaceLat: Double? = 35.3192,
    val departurePlaceLon: Double? = 139.5467,
    val departurePlaceCountry: String = "JP",
    val destinationPlaceName: String = "箱根湯本",
    val destinationPlaceLat: Double? = 35.2324,
    val destinationPlaceLon: Double? = 139.1069,
    val destinationPlaceCountry: String = "JP",

    // SPOT (3-line free stamp) fields
    val spotLine1: String = "SHIBUYA SKY",
    val spotLine2: String = "Tokyo Twilight",
    val spotLine3: String = "229m Above Ground",

    // Computed ground polylines (Train track segments or OSRM road route)
    val routePolylines: List<List<Coord>> = emptyList(),

    // Photo / Current GPS & Reverse-Geocoded Location
    val photoLat: Double? = 34.4342,
    val photoLon: Double? = 135.2440,
    val photoCountryCode: String = "JP",
    val photoLocationName: String = "Osaka, Japan",
    val locationDisplayMode: LocationDisplayMode = LocationDisplayMode.NAME,

    // Date & Time
    val year: Int = 2026,
    val month: Int = 5,
    val day: Int = 25,
    val hour: Int = 15,
    val minute: Int = 40,
    val dateTimeDisplayMode: DateTimeDisplayMode = DateTimeDisplayMode.DATE_ONLY
) {
    fun startCoord(): Coord? = when (mode) {
        TransportMode.AIRPLANE -> if (departureLat != null && departureLon != null) Coord(departureLon, departureLat) else null
        TransportMode.TRAIN -> if (departureStationLat != null && departureStationLon != null) Coord(departureStationLon, departureStationLat) else null
        TransportMode.OTHER -> if (departurePlaceLat != null && departurePlaceLon != null) Coord(departurePlaceLon, departurePlaceLat) else null
        TransportMode.SPOT -> null
    }

    fun endCoord(): Coord? = when (mode) {
        TransportMode.AIRPLANE -> if (arrivalLat != null && arrivalLon != null) Coord(arrivalLon, arrivalLat) else null
        TransportMode.TRAIN -> if (arrivalStationLat != null && arrivalStationLon != null) Coord(arrivalStationLon, arrivalStationLat) else null
        TransportMode.OTHER -> if (destinationPlaceLat != null && destinationPlaceLon != null) Coord(destinationPlaceLon, destinationPlaceLat) else null
        TransportMode.SPOT -> null
    }

    fun currentGpsCoord(): Coord? =
        if (photoLat != null && photoLon != null) Coord(photoLon, photoLat) else null

    fun summaryLabel(): String = when (mode) {
        TransportMode.AIRPLANE -> {
            val flt = buildFlightCode()
            val route = listOf(departureAirportCode, arrivalAirportCode).filter { it.isNotBlank() }.joinToString(" ✈ ")
            listOf(flt, route).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Flight" }
        }
        TransportMode.TRAIN -> {
            val route = listOf(departureStationName, arrivalStationName).filter { it.isNotBlank() }.joinToString(" → ")
            listOf(railwayLineName, route).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Train" }
        }
        TransportMode.OTHER -> {
            val route = listOf(departurePlaceName, destinationPlaceName).filter { it.isNotBlank() }.joinToString(" → ")
            listOf(transportName, route).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Drive" }
        }
        TransportMode.SPOT -> {
            listOf(spotLine1, spotLine2, spotLine3).firstOrNull { it.isNotBlank() } ?: "Spot"
        }
    }

    fun buildFlightCode(): String {
        val code = airlineCode.trim().uppercase()
        val num = flightNumber.trim().uppercase()
        if (code.isBlank() && num.isBlank()) return ""
        if (code.isBlank()) return num
        if (num.isBlank()) return code
        return if (num.startsWith(code)) {
            val rest = num.removePrefix(code).trim()
            if (rest.isEmpty()) code else "$code $rest"
        } else {
            "$code $num"
        }
    }
}

@Serializable
data class OverlayStyle(
    val font: OverlayFont = OverlayFont.OUTFIT,
    val textColorArgb: Int = 0xFFFFFFFF.toInt(),
    val routeColorArgb: Int = 0xFFFFFFFF.toInt(),
    val borderColorArgb: Int = 0xFFFFFFFF.toInt(),
    val showMap: Boolean = true,
    val showOriginPlaneBadge: Boolean = true,
    val textCorner: OverlayCorner = OverlayCorner.BOTTOM_RIGHT,
    val mapCorner: OverlayCorner = OverlayCorner.BOTTOM_LEFT,
    val textFreePos: NormOffset? = null,
    val mapFreePos: NormOffset? = null,
    val textScale: Float = 1.0f,
    val mapBoxScale: Float = 1.0f,
    val mapInternalZoom: Float = 1.0f,
    val mapInternalPanX: Float = 0.0f,
    val mapInternalPanY: Float = 0.0f
)

@Serializable
enum class CropAspectPreset(val labelJa: String, val portraitRatio: Float?) {
    ORIGINAL("オリジナル", null),
    FREE("フリー", null),
    RATIO_4_5("4:5", 4f / 5f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_2_3("2:3", 2f / 3f),
    RATIO_1_1("1:1", 1f)
}

@Serializable
data class CropFrameSettings(
    val aspectPreset: CropAspectPreset = CropAspectPreset.ORIGINAL,
    val isLandscapeOrientation: Boolean = false,
    val freeAspectRatio: Float = 1.0f,
    val cropCenterX: Float = 0.5f,
    val cropCenterY: Float = 0.5f,
    val cropZoom: Float = 1.0f,
    val frameMarginRatio: Float = 0.0f,
    val frameColorArgb: Int = 0xFFFFFFFF.toInt()
) {
    fun effectiveAspect(sourceWidth: Int, sourceHeight: Int): Float {
        if (aspectPreset == CropAspectPreset.FREE) {
            return freeAspectRatio.coerceIn(0.4f, 2.5f)
        }
        val base = aspectPreset.portraitRatio ?: return (sourceWidth.toFloat() / sourceHeight.coerceAtLeast(1).toFloat())
        return if (isLandscapeOrientation && base != 1f) (1f / base) else base
    }
}

@Serializable
enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    JPEG_95("JPEG (高画質 95%)", "jpg", "image/jpeg"),
    PNG_LOSSLESS("PNG (ロスレス無劣化)", "png", "image/png")
}

@Serializable
data class StylePreset(
    val id: String,
    val name: String,
    val style: OverlayStyle
)
