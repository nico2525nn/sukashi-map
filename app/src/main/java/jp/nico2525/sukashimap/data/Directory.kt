package jp.nico2525.sukashimap.data

data class Airline(val code: String, val name: String, val active: Boolean = true) {
    fun display(): String = "$name: $code".let { if (name.isBlank()) code else "$code: $name" }
}

data class Airport(
    val iata: String,
    val icao: String,
    val name: String,
    val city: String,
    val country: String,
    val lat: Double,
    val lon: Double,
) {
    fun code(): String = iata.ifBlank { icao }
    fun display(): String = "${code()} - $name"
}

data class Station(
    val name: String,
    val lat: Double,
    val lon: Double,
    val line: String,
    val operator: String,
)

data class RailLine(
    val name: String,
    val operator: String,
    val type: Int,
    val stations: List<String> = emptyList(),
)

data class RailSection(
    val line: String,
    val operator: String,
    val coords: List<Pair<Double, Double>>, // lon, lat
)
