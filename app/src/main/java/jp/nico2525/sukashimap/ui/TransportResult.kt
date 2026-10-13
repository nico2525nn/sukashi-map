package jp.nico2525.sukashimap.ui

import android.os.Bundle
import jp.nico2525.sukashimap.data.DateTimeDisplayMode
import jp.nico2525.sukashimap.data.OverlayPosition
import jp.nico2525.sukashimap.data.TransportInfo
import jp.nico2525.sukashimap.data.TransportMode

/** Bundle keys for Main <-> TransportBottomSheet transport exchange. */
object TransportResult {
    const val REQUEST_KEY = "transport_result"

    const val KEY_MODE = "transport_mode"
    const val KEY_DATE = "date"
    const val KEY_TIME = "time"
    const val KEY_DATE_TIME_DISPLAY_MODE = "date_time_display_mode"
    const val KEY_OVERLAY_POSITION = "overlay_position"
    const val KEY_MAP_POSITION = "map_position"
    const val KEY_SHOW_MAP = "show_map"
    const val KEY_SHOW_WATERMARK = "extra_show_watermark"
    const val KEY_PHOTO_LAT = "photo_latitude"
    const val KEY_PHOTO_LON = "photo_longitude"
    const val KEY_PHOTO_COUNTRY = "country"

    fun toBundle(
        info: TransportInfo,
        overlayPosition: OverlayPosition,
        fontIndex: Int,
        textColor: Int,
        mapLineColor: Int,
        mapBorderColor: Int,
    ): Bundle = Bundle().apply {
        putString(KEY_MODE, info.mode.name)
        putString(KEY_DATE, info.dateTime.toLocalDate().toString())
        putString(KEY_TIME, info.dateTime.toLocalTime().withSecond(0).withNano(0).toString())
        putString(KEY_DATE_TIME_DISPLAY_MODE, info.dateTimeDisplayMode.name)
        putString(KEY_OVERLAY_POSITION, overlayPosition.name)
        putString(KEY_MAP_POSITION, info.mapPosition.name)
        putBoolean(KEY_SHOW_MAP, info.showMap)
        putBoolean(KEY_SHOW_WATERMARK, info.showWatermark)
        putDouble(KEY_PHOTO_LAT, info.currentLatitude ?: Double.NaN)
        putDouble(KEY_PHOTO_LON, info.currentLongitude ?: Double.NaN)
        putString(KEY_PHOTO_COUNTRY, info.currentCountryCode)
        putString("current_location", info.currentLocation)
        putInt("font_index", fontIndex)
        putInt("text_color", textColor)
        putInt("map_line_color", mapLineColor)
        putInt("map_border_color", mapBorderColor)
        when (info) {
            is TransportInfo.Airplane -> {
                putString("airline_code", info.airline?.code ?: info.manualAirlineCode)
                putString("airline_name", info.airline?.name)
                putString("flight_number", info.flightNumber)
                putString("registration_number", info.registrationNumber)
                putString("dep_airport", info.departureAirport)
                putString("dep_airport_name", info.departureAirportName)
                info.departureLatitude?.let { putDouble("dep_lat", it) }
                info.departureLongitude?.let { putDouble("dep_lon", it) }
                putString("arr_airport", info.arrivalAirport)
                putString("arr_airport_name", info.arrivalAirportName)
                info.arrivalLatitude?.let { putDouble("arr_lat", it) }
                info.arrivalLongitude?.let { putDouble("arr_lon", it) }
            }
            is TransportInfo.Train -> {
                putBoolean("is_domestic", info.isDomesticRoute)
                putString("line_name", info.lineName)
                putString("train_name", info.trainName)
                putString("vehicle_model", info.vehicleModel)
                putString("dep_station", info.departureStation)
                putString("dep_station_full", info.departureStationFull)
                info.departureLatitude?.let { putDouble("dep_lat", it) }
                info.departureLongitude?.let { putDouble("dep_lon", it) }
                putString("arr_station", info.arrivalStation)
                putString("arr_station_full", info.arrivalStationFull)
                info.arrivalLatitude?.let { putDouble("arr_lat", it) }
                info.arrivalLongitude?.let { putDouble("arr_lon", it) }
                putStringArrayList("geometries", ArrayList(info.geometries))
                putStringArrayList("via_lines", ArrayList(info.viaLines))
                putBoolean("shortest", info.isShortestRouteSelected)
            }
            is TransportInfo.Other -> {
                putString("transport_name", info.transportName)
                putString("dep_place", info.departurePlace)
                info.departureLatitude?.let { putDouble("dep_lat", it) }
                info.departureLongitude?.let { putDouble("dep_lon", it) }
                putString("dep_country", info.departureCountryCode)
                putString("destination", info.destination)
                info.destinationLatitude?.let { putDouble("arr_lat", it) }
                info.destinationLongitude?.let { putDouble("arr_lon", it) }
                putString("dest_country", info.destinationCountryCode)
                info.routePolyline?.let { putStringArrayList("route_polyline", ArrayList(it)) }
            }
            is TransportInfo.Spot -> {
                putString("spot1", info.line1)
                putString("spot2", info.line2)
                putString("spot3", info.line3)
            }
        }
    }

    fun overlayPositionOf(b: Bundle): OverlayPosition = runCatching {
        OverlayPosition.valueOf(b.getString(KEY_OVERLAY_POSITION, OverlayPosition.TOP_LEFT.name)!!)
    }.getOrDefault(OverlayPosition.TOP_LEFT)

    fun dateTimeModeOf(b: Bundle): DateTimeDisplayMode = runCatching {
        DateTimeDisplayMode.valueOf(b.getString(KEY_DATE_TIME_DISPLAY_MODE, DateTimeDisplayMode.DATE_TIME.name)!!)
    }.getOrDefault(DateTimeDisplayMode.DATE_TIME)

    fun modeOf(b: Bundle): TransportMode = runCatching {
        TransportMode.valueOf(b.getString(KEY_MODE, TransportMode.SPOT.name)!!)
    }.getOrDefault(TransportMode.SPOT)

    fun fromBundle(b: Bundle): TransportInfo {
        val mode = modeOf(b)
        val date = runCatching { java.time.LocalDate.parse(b.getString(KEY_DATE, "")) }
            .getOrDefault(java.time.LocalDate.now())
        val time = runCatching { java.time.LocalTime.parse(b.getString(KEY_TIME, "")) }
            .getOrDefault(java.time.LocalTime.now().withSecond(0).withNano(0))
        val dateTime = java.time.LocalDateTime.of(date, time)
        val display = dateTimeModeOf(b)
        val mapPos = runCatching {
            OverlayPosition.valueOf(b.getString(KEY_MAP_POSITION, OverlayPosition.TOP_RIGHT.name)!!)
        }.getOrDefault(OverlayPosition.TOP_RIGHT)
        val showMap = b.getBoolean(KEY_SHOW_MAP, true)
        val showWm = b.getBoolean(KEY_SHOW_WATERMARK, true)
        val lat = b.getDouble(KEY_PHOTO_LAT, Double.NaN).takeUnless { it.isNaN() }
        val lon = b.getDouble(KEY_PHOTO_LON, Double.NaN).takeUnless { it.isNaN() }
        val country = b.getString(KEY_PHOTO_COUNTRY)
        val place = b.getString("current_location")
        return when (mode) {
            TransportMode.AIRPLANE -> TransportInfo.Airplane(
                airline = b.getString("airline_code")?.takeIf { it.isNotBlank() }?.let {
                    jp.nico2525.sukashimap.data.Airline(it, b.getString("airline_name").orEmpty())
                },
                flightNumber = b.getString("flight_number").orEmpty(),
                registrationNumber = b.getString("registration_number").orEmpty(),
                departureAirport = b.getString("dep_airport").orEmpty(),
                departureAirportName = b.getString("dep_airport_name").orEmpty(),
                departureLatitude = b.doubleOrNull("dep_lat"),
                departureLongitude = b.doubleOrNull("dep_lon"),
                arrivalAirport = b.getString("arr_airport").orEmpty(),
                arrivalAirportName = b.getString("arr_airport_name").orEmpty(),
                arrivalLatitude = b.doubleOrNull("arr_lat"),
                arrivalLongitude = b.doubleOrNull("arr_lon"),
                dateTime = dateTime, dateTimeDisplayMode = display,
                currentLocation = place, currentLatitude = lat, currentLongitude = lon,
                currentCountryCode = country, showMap = showMap, showWatermark = showWm,
                mapPosition = mapPos,
            )
            TransportMode.TRAIN -> TransportInfo.Train(
                isDomesticRoute = b.getBoolean("is_domestic", true),
                lineName = b.getString("line_name").orEmpty(),
                trainName = b.getString("train_name").orEmpty(),
                vehicleModel = b.getString("vehicle_model").orEmpty(),
                departureStation = b.getString("dep_station").orEmpty(),
                departureStationFull = b.getString("dep_station_full").orEmpty(),
                departureLatitude = b.doubleOrNull("dep_lat"),
                departureLongitude = b.doubleOrNull("dep_lon"),
                arrivalStation = b.getString("arr_station").orEmpty(),
                arrivalStationFull = b.getString("arr_station_full").orEmpty(),
                arrivalLatitude = b.doubleOrNull("arr_lat"),
                arrivalLongitude = b.doubleOrNull("arr_lon"),
                geometries = b.getStringArrayList("geometries") ?: emptyList(),
                viaLines = b.getStringArrayList("via_lines") ?: emptyList(),
                isShortestRouteSelected = b.getBoolean("shortest", true),
                dateTime = dateTime, dateTimeDisplayMode = display,
                currentLocation = place, currentLatitude = lat, currentLongitude = lon,
                currentCountryCode = country, showMap = showMap, showWatermark = showWm,
                mapPosition = mapPos,
            )
            TransportMode.OTHER -> TransportInfo.Other(
                transportName = b.getString("transport_name").orEmpty(),
                departurePlace = b.getString("dep_place").orEmpty(),
                departureLatitude = b.doubleOrNull("dep_lat"),
                departureLongitude = b.doubleOrNull("dep_lon"),
                departureCountryCode = b.getString("dep_country"),
                destination = b.getString("destination").orEmpty(),
                destinationLatitude = b.doubleOrNull("arr_lat"),
                destinationLongitude = b.doubleOrNull("arr_lon"),
                destinationCountryCode = b.getString("dest_country"),
                routePolyline = b.getStringArrayList("route_polyline"),
                dateTime = dateTime, dateTimeDisplayMode = display,
                currentLocation = place, currentLatitude = lat, currentLongitude = lon,
                currentCountryCode = country, showMap = showMap, showWatermark = showWm,
                mapPosition = mapPos,
            )
            TransportMode.SPOT -> TransportInfo.Spot(
                line1 = b.getString("spot1").orEmpty(),
                line2 = b.getString("spot2").orEmpty(),
                line3 = b.getString("spot3").orEmpty(),
                dateTime = dateTime, dateTimeDisplayMode = display,
                currentLocation = place, currentLatitude = lat, currentLongitude = lon,
                currentCountryCode = country, showMap = showMap, showWatermark = showWm,
                mapPosition = mapPos,
            )
        }
    }

    private fun Bundle.doubleOrNull(key: String): Double? =
        if (containsKey(key)) getDouble(key) else null
}
