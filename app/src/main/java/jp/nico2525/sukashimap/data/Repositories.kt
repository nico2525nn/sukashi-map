package jp.nico2525.sukashimap.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private fun readAsset(context: Context, name: String): String =
    context.assets.open("seed/$name").bufferedReader().use { it.readText() }

/** Airport directory backed by OpenFlights seed data. */
class AirportRepository(context: Context) {
    private val app = context.applicationContext
    private var cache: List<Airport>? = null

    suspend fun all(): List<Airport> = withContext(Dispatchers.IO) {
        cache ?: load().also { cache = it }
    }

    private fun load(): List<Airport> {
        val arr = JSONArray(readAsset(app, "airports.json"))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Airport(
                iata = o.optString("iata"),
                icao = o.optString("icao"),
                name = o.optString("name"),
                city = o.optString("city"),
                country = o.optString("country"),
                lat = o.optDouble("lat"),
                lon = o.optDouble("lon"),
            )
        }
    }

    /** Prefix/substring search across code, name and city. Max 50 hits. */
    suspend fun search(query: String): List<Airport> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return all().filter {
            it.iata.lowercase().startsWith(q) || it.icao.lowercase().startsWith(q) ||
                it.name.lowercase().contains(q) || it.city.lowercase().contains(q)
        }.sortedWith(compareBy({ !(it.iata.lowercase().startsWith(q) || it.icao.lowercase().startsWith(q)) }, { it.name })).take(50)
    }

    suspend fun findByCode(code: String): Airport? {
        val c = code.trim().uppercase()
        if (c.isEmpty()) return null
        return all().firstOrNull { it.iata.equals(c, true) || it.icao.equals(c, true) }
    }
}

/** Airline directory backed by OpenFlights seed data. */
class AirlineRepository(context: Context) {
    private val app = context.applicationContext
    private var cache: List<Airline>? = null

    suspend fun all(): List<Airline> = withContext(Dispatchers.IO) {
        cache ?: load().also { cache = it }
    }

    private fun load(): List<Airline> {
        val arr = JSONArray(readAsset(app, "airlines.json"))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Airline(o.optString("code"), o.optString("name"), o.optBoolean("active", true))
        }
    }

    suspend fun search(query: String): List<Airline> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all().take(50)
        return all().filter {
            it.code.lowercase().startsWith(q) || it.name.lowercase().contains(q)
        }.sortedWith(compareByDescending<Airline> { it.active }.thenBy { it.code }).take(50)
    }

    suspend fun findByCode(code: String): Airline? {
        val c = code.trim().uppercase()
        if (c.isEmpty()) return null
        return all().firstOrNull { it.code.equals(c, true) }
    }
}

/** Railway directory backed by MLIT N02 seed data. */
class RailwayRepository(context: Context) {
    private val app = context.applicationContext
    private var linesCache: List<RailLine>? = null
    private var stationsCache: List<Station>? = null
    private var sectionsCache: List<RailSection>? = null

    suspend fun lines(): List<RailLine> = withContext(Dispatchers.IO) {
        linesCache ?: loadLines().also { linesCache = it }
    }

    suspend fun stations(): List<Station> = withContext(Dispatchers.IO) {
        stationsCache ?: loadStations().also { stationsCache = it }
    }

    suspend fun sections(): List<RailSection> = withContext(Dispatchers.IO) {
        sectionsCache ?: loadSections().also { sectionsCache = it }
    }

    private fun loadLines(): List<RailLine> {
        val arr = JSONArray(readAsset(app, "rail_lines.json"))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val st = o.optJSONArray("stations")
            RailLine(
                o.optString("name"), o.optString("operator"), o.optInt("type"),
                List(st?.length() ?: 0) { j -> st!!.optString(j) },
            )
        }
    }

    private fun loadStations(): List<Station> {
        val arr = JSONArray(readAsset(app, "rail_stations.json"))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Station(o.optString("name"), o.optDouble("lat"), o.optDouble("lon"), o.optString("line"), o.optString("operator"))
        }
    }

    private fun loadSections(): List<RailSection> {
        val arr = JSONArray(readAsset(app, "rail_sections.json"))
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val c = o.optJSONArray("coords")
            val coords = List(c?.length() ?: 0) { j ->
                val p = c!!.optJSONArray(j)
                Pair(p.optDouble(0), p.optDouble(1))
            }
            RailSection(o.optString("line"), o.optString("operator"), coords)
        }
    }

    private var aliasesCache: Pair<Map<String, List<String>>, Map<String, List<String>>>? = null

    private fun aliases(): Pair<Map<String, List<String>>, Map<String, List<String>>> {
        aliasesCache?.let { return it }
        val result = runCatching {
            val o = org.json.JSONObject(readAsset(app, "romaji_aliases.json"))
            fun section(key: String): Map<String, List<String>> {
                val s = o.optJSONObject(key) ?: return emptyMap()
                return s.keys().asSequence().associateWith { k ->
                    val arr = s.optJSONArray(k)
                    List(arr?.length() ?: 0) { i -> arr!!.optString(i).lowercase() }
                }
            }
            Pair(section("lines"), section("stations"))
        }.getOrDefault(Pair(emptyMap(), emptyMap()))
        aliasesCache = result
        return result
    }

    suspend fun searchLines(query: String): List<RailLine> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val ql = q.lowercase()
        val (lineAlias, _) = aliases()
        return lines().filter {
            it.name.contains(q) || it.operator.contains(q) ||
                (lineAlias[it.name]?.any { a -> a.contains(ql) } == true)
        }.take(50)
    }

    suspend fun searchStations(query: String, lineFilter: String? = null): List<Station> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val ql = q.lowercase()
        val (_, stationAlias) = aliases()
        return stations().filter {
            (lineFilter.isNullOrBlank() || it.line == lineFilter) &&
                (it.name.contains(q) || (stationAlias[it.name]?.any { a -> a.contains(ql) } == true))
        }.take(50)
    }

    suspend fun stationsOfLine(line: String): List<Station> =
        stations().filter { it.line == line }

    suspend fun geometriesOfLine(line: String): List<String> =
        sections().filter { it.line == line }.map { s ->
            "[" + s.coords.joinToString(",") { "[${it.first},${it.second}]" } + "]"
        }
}
