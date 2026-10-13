package jp.nico2525.sukashimap.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Transport overlay content. Field layout mirrors SPEC §3. */
sealed class TransportInfo {
    abstract val mode: TransportMode
    abstract val dateTime: LocalDateTime
    abstract val dateTimeDisplayMode: DateTimeDisplayMode
    abstract val currentLocation: String?
    abstract val currentLatitude: Double?
    abstract val currentLongitude: Double?
    abstract val currentCountryCode: String?
    abstract val showMap: Boolean
    abstract val showWatermark: Boolean
    abstract val mapPosition: OverlayPosition

    abstract val departure: String
    abstract val arrival: String
    abstract val departureName: String
    abstract val arrivalName: String
    abstract val transportLabel: String

    fun formattedDate(): String = when (dateTimeDisplayMode) {
        DateTimeDisplayMode.DATE_ONLY ->
            dateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        DateTimeDisplayMode.DATE_TIME ->
            dateTime.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
        DateTimeDisplayMode.NONE -> ""
    }

    fun formattedCoordinates(): String? {
        val lat = currentLatitude ?: return null
        val lon = currentLongitude ?: return null
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return String.format("%.2f%s, %.2f%s", kotlin.math.abs(lat), ns, kotlin.math.abs(lon), ew)
    }

    fun locationDisplay(mode: LocationDisplayMode): String? = when (mode) {
        LocationDisplayMode.REGION_NAME -> currentLocation ?: formattedCoordinates()
        LocationDisplayMode.COORDINATES -> formattedCoordinates()
    }

    open fun routeDisplay(): String = when {
        departure.isBlank() && arrival.isBlank() -> ""
        departure.isBlank() -> arrival
        arrival.isBlank() -> departure
        else -> "$departure → $arrival"
    }

    abstract fun withShowWatermark(show: Boolean): TransportInfo

    data class Airplane(
        val airline: Airline? = null,
        val manualAirlineCode: String = "",
        val flightNumber: String = "",
        val registrationNumber: String = "",
        val departureAirport: String = "",
        val departureAirportName: String = "",
        val departureLatitude: Double? = null,
        val departureLongitude: Double? = null,
        val arrivalAirport: String = "",
        val arrivalAirportName: String = "",
        val arrivalLatitude: Double? = null,
        val arrivalLongitude: Double? = null,
        override val dateTime: LocalDateTime = LocalDateTime.now(),
        override val dateTimeDisplayMode: DateTimeDisplayMode = DateTimeDisplayMode.DATE_TIME,
        override val currentLocation: String? = null,
        override val currentLatitude: Double? = null,
        override val currentLongitude: Double? = null,
        override val currentCountryCode: String? = null,
        override val showMap: Boolean = true,
        override val showWatermark: Boolean = false,
        override val mapPosition: OverlayPosition = OverlayPosition.TOP_RIGHT,
    ) : TransportInfo() {
        override val mode = TransportMode.AIRPLANE
        override val departure: String get() = departureAirport
        override val arrival: String get() = arrivalAirport
        override val departureName: String get() = departureAirportName
        override val arrivalName: String get() = arrivalAirportName
        override val transportLabel: String get() {
            val code = airline?.code ?: manualAirlineCode
            return listOf(code, flightNumber).filter { it.isNotBlank() }.joinToString(" ")
        }

        fun airlineCode(): String = airline?.code ?: manualAirlineCode

        override fun withShowWatermark(show: Boolean) = copy(showWatermark = show)
    }

    data class Train(
        val isDomesticRoute: Boolean = true,
        val operator: String = "",
        val lineName: String = "",
        val trainName: String = "",
        val vehicleModel: String = "",
        val departureStation: String = "",
        val departureStationFull: String = "",
        val departureLatitude: Double? = null,
        val departureLongitude: Double? = null,
        val arrivalStation: String = "",
        val arrivalStationFull: String = "",
        val arrivalLatitude: Double? = null,
        val arrivalLongitude: Double? = null,
        val geometries: List<String> = emptyList(),
        val viaLines: List<String> = emptyList(),
        val isShortestRouteSelected: Boolean = true,
        override val dateTime: LocalDateTime = LocalDateTime.now(),
        override val dateTimeDisplayMode: DateTimeDisplayMode = DateTimeDisplayMode.DATE_TIME,
        override val currentLocation: String? = null,
        override val currentLatitude: Double? = null,
        override val currentLongitude: Double? = null,
        override val currentCountryCode: String? = null,
        override val showMap: Boolean = true,
        override val showWatermark: Boolean = false,
        override val mapPosition: OverlayPosition = OverlayPosition.TOP_RIGHT,
    ) : TransportInfo() {
        override val mode = TransportMode.TRAIN
        override val departure: String get() = departureStation
        override val arrival: String get() = arrivalStation
        override val departureName: String get() = departureStationFull.ifBlank { departureStation }
        override val arrivalName: String get() = arrivalStationFull.ifBlank { arrivalStation }
        override val transportLabel: String get() =
            listOf(lineName, trainName).filter { it.isNotBlank() }.joinToString(" ")

        /** First header line: operator + line (e.g. "JR東日本 東北新幹線"). */
        fun headerLine1(): String =
            listOf(operator, lineName).filter { it.isNotBlank() }.joinToString(" ")

        /** Second header line: train + vehicle (e.g. "はやぶさ 9号"). */
        fun headerLine2(): String =
            listOf(trainName, vehicleModel).filter { it.isNotBlank() }.joinToString(" ")

        override fun withShowWatermark(show: Boolean) = copy(showWatermark = show)
    }

    data class Other(
        val transportName: String = "",
        val departurePlace: String = "",
        val departureLatitude: Double? = null,
        val departureLongitude: Double? = null,
        val departureCountryCode: String? = null,
        val destination: String = "",
        val destinationLatitude: Double? = null,
        val destinationLongitude: Double? = null,
        val destinationCountryCode: String? = null,
        val routePolyline: List<String>? = null,
        override val dateTime: LocalDateTime = LocalDateTime.now(),
        override val dateTimeDisplayMode: DateTimeDisplayMode = DateTimeDisplayMode.DATE_TIME,
        override val currentLocation: String? = null,
        override val currentLatitude: Double? = null,
        override val currentLongitude: Double? = null,
        override val currentCountryCode: String? = null,
        override val showMap: Boolean = true,
        override val showWatermark: Boolean = false,
        override val mapPosition: OverlayPosition = OverlayPosition.TOP_RIGHT,
    ) : TransportInfo() {
        override val mode = TransportMode.OTHER
        override val departure: String get() = departurePlace
        override val arrival: String get() = destination
        override val departureName: String get() = departurePlace
        override val arrivalName: String get() = destination
        override val transportLabel: String get() = transportName

        override fun withShowWatermark(show: Boolean) = copy(showWatermark = show)
    }

    data class Spot(
        val line1: String = "",
        val line2: String = "",
        val line3: String = "",
        override val dateTime: LocalDateTime = LocalDateTime.now(),
        override val dateTimeDisplayMode: DateTimeDisplayMode = DateTimeDisplayMode.DATE_TIME,
        override val currentLocation: String? = null,
        override val currentLatitude: Double? = null,
        override val currentLongitude: Double? = null,
        override val currentCountryCode: String? = null,
        override val showMap: Boolean = true,
        override val showWatermark: Boolean = false,
        override val mapPosition: OverlayPosition = OverlayPosition.TOP_RIGHT,
    ) : TransportInfo() {
        override val mode = TransportMode.SPOT
        override val departure: String get() = ""
        override val arrival: String get() = ""
        override val departureName: String get() = ""
        override val arrivalName: String get() = ""
        override val transportLabel: String get() = ""

        override fun routeDisplay(): String =
            listOf(line1, line2, line3).filter { it.isNotBlank() }.joinToString("\n")

        override fun withShowWatermark(show: Boolean) = copy(showWatermark = show)
    }
}
