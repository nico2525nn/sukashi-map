package jp.nico2525.sukashimap.model

import java.util.Locale
import kotlin.math.abs

/**
 * Pure Kotlin helpers for formatting and entering latitude/longitude.
 *
 * Kept free of Android dependencies so the parsing rules can be unit tested on the JVM.
 */
object GeoCoordinate {

    /** WGS84 decimal degrees, e.g. `35.65850°N, 139.70220°E`. */
    fun format(lat: Double, lon: Double, decimals: Int = 5): String {
        val safeDecimals = decimals.coerceIn(0, 8)
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return String.format(
            Locale.US,
            "%.${safeDecimals}f°%s, %.${safeDecimals}f°%s",
            abs(lat),
            ns,
            abs(lon),
            ew
        )
    }

    fun isValidLatitude(value: Double): Boolean = value.isFinite() && value in -90.0..90.0

    fun isValidLongitude(value: Double): Boolean = value.isFinite() && value in -180.0..180.0

    fun parseLatitude(text: String): Double? = parse(text, isLatitude = true)

    fun parseLongitude(text: String): Double? = parse(text, isLatitude = false)

    /**
     * Accepts decimal degrees (`35.6585`, `-123.15`), hemisphere suffixes/prefixes
     * (`35.6585N`, `N35.6585`, `123.15W`) and DMS (`35°39'30.6"N`).
     *
     * Hemisphere letters that belong to the other axis are rejected rather than silently
     * ignored, so `35.6W` cannot become `+35.6` latitude.
     */
    fun parse(text: String, isLatitude: Boolean): Double? {
        val raw = text.trim()
        if (raw.isEmpty()) return null

        var normalized = raw
            .uppercase(Locale.US)
            .replace('，', ',')
            .replace('　', ' ')
            .replace('−', '-')
            .replace('–', '-')

        // Treat a lone comma as a decimal separator when the input is not sexagesimal.
        // This also covers forms like `48,8587 N` and `48,8587°N`.
        val hasSexagesimalMarkers = normalized.any { it in "'\"′″:" }
        if (!hasSexagesimalMarkers && normalized.count { it == ',' } == 1) {
            normalized = normalized.replace(',', '.')
        }

        val negativeDirections = if (isLatitude) setOf('S') else setOf('W')
        val positiveDirections = if (isLatitude) setOf('N') else setOf('E')
        val wrongAxisDirections = if (isLatitude) setOf('E', 'W') else setOf('N', 'S')
        if (normalized.any { it in wrongAxisDirections }) return null

        var direction: Char? = null
        for (candidate in positiveDirections + negativeDirections) {
            if (normalized.contains(candidate)) {
                direction = candidate
                break
            }
        }

        val withoutDirection = normalized
            .replace(Regex("[NSEW]"), " ")
            .replace(',', ' ')
            .trim()

        val decimal = withoutDirection.toDoubleOrNull()
        val value = if (decimal != null) {
            if (direction != null) abs(decimal) else decimal
        } else {
            parseDms(withoutDirection) ?: return null
        }

        val signed = when {
            direction in negativeDirections -> -abs(value)
            direction in positiveDirections -> abs(value)
            else -> value
        }

        return if (isLatitude) {
            signed.takeIf { isValidLatitude(it) }
        } else {
            signed.takeIf { isValidLongitude(it) }
        }
    }

    private fun parseDms(text: String): Double? {
        val cleaned = text
            .replace('°', ' ')
            .replace('d', ' ')
            .replace('D', ' ')
            .replace('′', ' ')
            .replace('\'', ' ')
            .replace('’', ' ')
            .replace('″', ' ')
            .replace('"', ' ')
            .replace('”', ' ')
            .replace(':', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (cleaned.isEmpty()) return null

        val parts = cleaned.split(' ').filter { it.isNotBlank() }
        if (parts.size !in 1..3) return null

        val degreeToken = parts.getOrNull(0) ?: return null
        val degrees = degreeToken.toDoubleOrNull() ?: return null
        val minutes = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val seconds = parts.getOrNull(2)?.toDoubleOrNull() ?: 0.0
        if (minutes < 0.0 || minutes >= 60.0 || seconds < 0.0 || seconds >= 60.0) return null

        val magnitude = abs(degrees) + minutes / 60.0 + seconds / 3600.0
        // Use the token, not `degrees < 0`: -0.0 compares equal to 0.0 but still means south/west.
        return if (degreeToken.startsWith("-")) -magnitude else magnitude
    }
}
