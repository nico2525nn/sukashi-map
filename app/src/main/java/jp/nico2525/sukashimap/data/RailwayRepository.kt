package jp.nico2525.sukashimap.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.JsonReader
import jp.nico2525.sukashimap.model.Coord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.StringReader
import java.util.PriorityQueue
import kotlin.math.hypot
import kotlin.math.roundToLong

data class StationRecord(
    val id: Long,
    val companyName: String,
    val lineName: String,
    val stationName: String,
    val groupCode: String,
    val latitude: Double,
    val longitude: Double
) {
    val lineKey: String get() = "$companyName::$lineName"
    val dropdownLabel: String get() = "$stationName ($companyName $lineName)"
}

data class RailwayLineEntry(
    val companyName: String,
    val lineName: String
) {
    val lineKey: String get() = "$companyName::$lineName"
    val displayLabel: String get() = "$lineName ($companyName)"
}

data class TrainRouteCalculationResult(
    val polylines: List<List<Coord>>,
    val candidateViaLines: List<String>,
    val autoDetectedLineName: String? = null,
    val autoDetectedCompany: String? = null
)

class RailwayRepository(private val context: Context) {
    private val initMutex = Mutex()
    @Volatile
    private var database: SQLiteDatabase? = null

    private data class CachedTransferGraph(
        val groupToLineKeys: Map<String, List<String>>,
        val lineKeyToGroups: Map<String, List<String>>
    )

    @Volatile
    private var cachedGraph: CachedTransferGraph? = null

    companion object {
        const val TOKYO_LAT = 35.681236
        const val TOKYO_LON = 139.767125

        // Physically connected cross-JR Shinkansen corridors (excludes disconnected 西九州新幹線 gap)
        private val shinkansenCorridors: List<Set<String>> = listOf(
            setOf("東海道新幹線", "山陽新幹線", "九州新幹線"),
            setOf("北陸新幹線", "上越新幹線", "東北新幹線"),
            setOf("東北新幹線", "北海道新幹線", "山形新幹線", "秋田新幹線", "上越新幹線")
        )

        fun expandedCorridorLines(lineName: String): Set<String> {
            val clean = lineName.substringAfter("::").trim()
            if (clean.isEmpty()) return emptySet()
            val matched = shinkansenCorridors.filter { clean in it }.flatten().toSet()
            return if (matched.isNotEmpty()) matched else setOf(clean)
        }

        fun formatViaChipLabel(lineKey: String): String {
            val parts = lineKey.split("::", limit = 2)
            return if (parts.size == 2) "${parts[1]} (${parts[0]})" else lineKey
        }
    }

    private suspend fun getDb(): SQLiteDatabase = withContext(Dispatchers.IO) {
        database?.takeIf { it.isOpen }?.let { return@withContext it }
        initMutex.withLock {
            database?.takeIf { it.isOpen }?.let { return@withLock it }
            val dbFile = context.getDatabasePath("sukashi_railways_v1.db")
            if (!dbFile.exists() || dbFile.length() < 1_000_000L) {
                dbFile.parentFile?.mkdirs()
                val tmp = File(dbFile.parentFile, "sukashi_railways_v1.db.tmp")
                context.assets.open("railways.db").use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                if (dbFile.exists()) dbFile.delete()
                tmp.renameTo(dbFile)
            }
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).also {
                database = it
            }
        }
    }

    private fun getTransferGraph(db: SQLiteDatabase): CachedTransferGraph {
        cachedGraph?.let { return it }
        synchronized(this) {
            cachedGraph?.let { return it }
            val groupToLines = HashMap<String, MutableList<String>>(4096)
            val lineToGroups = HashMap<String, MutableList<String>>(1024)
            db.rawQuery("SELECT group_code, company_name, line_name FROM stations WHERE group_code != ''", null).use { c ->
                while (c.moveToNext()) {
                    val g = c.getString(0)
                    val key = "${c.getString(1)}::${c.getString(2)}"
                    val gl = groupToLines.getOrPut(g) { mutableListOf() }
                    if (key !in gl) gl.add(key)
                    val lg = lineToGroups.getOrPut(key) { mutableListOf() }
                    if (g !in lg) lg.add(g)
                }
            }
            return CachedTransferGraph(groupToLines, lineToGroups).also { cachedGraph = it }
        }
    }

    suspend fun searchLines(
        query: String,
        refLat: Double? = null,
        refLon: Double? = null,
        limit: Int = 30
    ): List<RailwayLineEntry> = withContext(Dispatchers.IO) {
        val q = query.trim()
        val rLat = refLat?.takeIf { it in 20.0..46.5 } ?: TOKYO_LAT
        val rLon = refLon?.takeIf { it in 122.0..154.0 } ?: TOKYO_LON
        val db = getDb()
        val sql = if (q.isEmpty()) {
            """
            SELECT company_name, line_name, MIN((latitude - ?) * (latitude - ?) + (longitude - ?) * (longitude - ?)) AS dist2
            FROM stations
            GROUP BY company_name, line_name
            ORDER BY CASE WHEN line_name LIKE '%新幹線%' THEN 0 ELSE 1 END, dist2 ASC
            LIMIT ?
            """.trimIndent()
        } else {
            """
            SELECT company_name, line_name, MIN((latitude - ?) * (latitude - ?) + (longitude - ?) * (longitude - ?)) AS dist2
            FROM stations
            WHERE line_name LIKE ? OR company_name LIKE ?
            GROUP BY company_name, line_name
            ORDER BY CASE
                WHEN line_name = ? THEN 0
                WHEN line_name LIKE ? THEN 1
                ELSE 2
            END, dist2 ASC
            LIMIT ?
            """.trimIndent()
        }
        val args = if (q.isEmpty()) {
            arrayOf(rLat.toString(), rLat.toString(), rLon.toString(), rLon.toString(), limit.toString())
        } else {
            arrayOf(
                rLat.toString(), rLat.toString(), rLon.toString(), rLon.toString(),
                "%$q%", "%$q%", q, "$q%", limit.toString()
            )
        }
        val list = mutableListOf<RailwayLineEntry>()
        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                list.add(RailwayLineEntry(c.getString(0), c.getString(1)))
            }
        }
        list
    }

    suspend fun searchStations(
        query: String,
        preferredLineName: String = "",
        refLat: Double? = null,
        refLon: Double? = null,
        limit: Int = 30
    ): List<StationRecord> = withContext(Dispatchers.IO) {
        val q = query.trim().removeSuffix("駅")
        val rLat = refLat?.takeIf { it in 20.0..46.5 } ?: TOKYO_LAT
        val rLon = refLon?.takeIf { it in 122.0..154.0 } ?: TOKYO_LON
        val db = getDb()

        val prefCorridor = expandedCorridorLines(preferredLineName)
        val results = mutableListOf<StationRecord>()

        if (q.isEmpty() && preferredLineName.isNotBlank()) {
            val ph = prefCorridor.joinToString(",") { "?" }
            val args = prefCorridor.toTypedArray() + arrayOf(
                rLat.toString(), rLat.toString(), rLon.toString(), rLon.toString(), limit.toString()
            )
            db.rawQuery(
                """
                SELECT id, company_name, line_name, station_name, group_code, latitude, longitude
                FROM stations
                WHERE line_name IN ($ph)
                ORDER BY (latitude - ?) * (latitude - ?) + (longitude - ?) * (longitude - ?) ASC
                LIMIT ?
                """.trimIndent(),
                args
            ).use { c ->
                while (c.moveToNext()) results.add(c.toStationRecord())
            }
            return@withContext results
        }

        if (q.isEmpty()) {
            db.rawQuery(
                """
                SELECT id, company_name, line_name, station_name, group_code, latitude, longitude
                FROM stations
                ORDER BY (latitude - ?) * (latitude - ?) + (longitude - ?) * (longitude - ?) ASC
                LIMIT ?
                """.trimIndent(),
                arrayOf(rLat.toString(), rLat.toString(), rLon.toString(), rLon.toString(), limit.toString())
            ).use { c ->
                while (c.moveToNext()) results.add(c.toStationRecord())
            }
            return@withContext results
        }

        db.rawQuery(
            """
            SELECT id, company_name, line_name, station_name, group_code, latitude, longitude
            FROM stations
            WHERE station_name LIKE ?
            ORDER BY CASE
                WHEN station_name = ? THEN 0
                WHEN station_name LIKE ? THEN 1
                ELSE 2
            END,
            (latitude - ?) * (latitude - ?) + (longitude - ?) * (longitude - ?) ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf("%$q%", q, "$q%", rLat.toString(), rLat.toString(), rLon.toString(), rLon.toString(), (limit * 2).toString())
        ).use { c ->
            while (c.moveToNext()) results.add(c.toStationRecord())
        }

        if (prefCorridor.isNotEmpty()) {
            results.sortedWith(
                compareBy<StationRecord> { if (it.lineName in prefCorridor) 0 else 1 }
                    .thenBy { if (it.stationName == q) 0 else 1 }
            ).take(limit)
        } else {
            results.take(limit)
        }
    }

    suspend fun computeTrainRoute(
        depStationName: String,
        depLat: Double?,
        depLon: Double?,
        depGroupCode: String,
        arrStationName: String,
        arrLat: Double?,
        arrLon: Double?,
        arrGroupCode: String,
        preferredLineName: String,
        selectedViaLines: List<String>,
        isAutoShortest: Boolean
    ): TrainRouteCalculationResult = withContext(Dispatchers.IO) {
        if (depLat == null || depLon == null || arrLat == null || arrLon == null) {
            return@withContext TrainRouteCalculationResult(emptyList(), emptyList())
        }
        val db = getDb()
        val depStations = queryStationLines(db, depStationName, depGroupCode, depLat, depLon)
        val arrStations = queryStationLines(db, arrStationName, arrGroupCode, arrLat, arrLon)

        // Key strictly by company_name::line_name to prevent "本線" / "空港線" / "東西線" operator collisions!
        val depKeys = depStations.map { it.lineKey }.toSet()
        val arrKeys = arrStations.map { it.lineKey }.toSet()
        val directSharedKeys = depKeys.intersect(arrKeys)

        val corridorMatch = shinkansenCorridors.firstOrNull { corr ->
            depStations.any { it.lineName in corr } && arrStations.any { it.lineName in corr }
        }

        var autoLine: String? = null
        var autoCompany: String? = null
        val candidateVias = mutableListOf<String>()

        var polylines: List<List<Coord>> = emptyList()

        // Try direct shared (company, line) or connected Shinkansen corridor first
        if (directSharedKeys.isNotEmpty()) {
            val chosenKey = directSharedKeys.firstOrNull { it.endsWith("::${preferredLineName.trim()}") }
                ?: directSharedKeys.first()
            val parts = chosenKey.split("::", limit = 2)
            autoCompany = parts.getOrNull(0)
            autoLine = parts.getOrNull(1)
            polylines = extractShortestTrackCorridorByKeys(
                db = db,
                lineKeys = setOf(chosenKey),
                corridorLineNames = emptySet(),
                start = Coord(depLon, depLat),
                end = Coord(arrLon, arrLat)
            )
        } else if (corridorMatch != null) {
            val depShinkansen = depStations.first { it.lineName in corridorMatch }
            autoCompany = depShinkansen.companyName
            autoLine = depShinkansen.lineName
            polylines = extractShortestTrackCorridorByKeys(
                db = db,
                lineKeys = emptySet(),
                corridorLineNames = corridorMatch,
                start = Coord(depLon, depLat),
                end = Coord(arrLon, arrLat)
            )
        }

        // If not on a single line/corridor (or disconnected), run cached transfer graph BFS!
        if (polylines.isEmpty()) {
            val startGroup = depGroupCode.ifBlank { depStations.firstOrNull()?.groupCode.orEmpty() }
            val endGroup = arrGroupCode.ifBlank { arrStations.firstOrNull()?.groupCode.orEmpty() }
            val transfer = discoverTransferLines(db, startGroup, endGroup)
            candidateVias.addAll(transfer.allCandidateLineKeys)

            val activeKeys = if (!isAutoShortest && selectedViaLines.isNotEmpty()) {
                selectedViaLines.toSet()
            } else {
                transfer.shortestPathLineKeys.toSet()
            }
            val expandedNames = activeKeys.map { it.substringAfter("::") }
                .flatMap { shinkansenCorridors.filter { corr -> it in corr }.flatten() }
                .toSet()

            polylines = extractShortestTrackCorridorByKeys(
                db = db,
                lineKeys = activeKeys,
                corridorLineNames = expandedNames,
                start = Coord(depLon, depLat),
                end = Coord(arrLon, arrLat)
            )

            // Fallback for virtual/through-service JR lines whose physical sections are split across
            // underlying trunk lines (e.g. 湘南新宿ライン omitting Osaki–Yokohama 東海道線 tracks)
            if (polylines.isEmpty() && (isAutoShortest || selectedViaLines.isEmpty()) && transfer.allCandidateLineKeys.isNotEmpty()) {
                val allKeys = transfer.allCandidateLineKeys.toSet()
                val allExpandedNames = allKeys.map { it.substringAfter("::") }
                    .flatMap { shinkansenCorridors.filter { corr -> it in corr }.flatten() }
                    .toSet()
                polylines = extractShortestTrackCorridorByKeys(
                    db = db,
                    lineKeys = allKeys,
                    corridorLineNames = allExpandedNames,
                    start = Coord(depLon, depLat),
                    end = Coord(arrLon, arrLat)
                )
            }
        }

        TrainRouteCalculationResult(
            polylines = polylines,
            candidateViaLines = candidateVias,
            autoDetectedLineName = autoLine,
            autoDetectedCompany = autoCompany
        )
    }

    private data class TransferResult(
        val shortestPathLineKeys: List<String>,
        val allCandidateLineKeys: List<String>
    )

    private fun discoverTransferLines(
        db: SQLiteDatabase,
        startGroup: String,
        endGroup: String
    ): TransferResult {
        if (startGroup.isBlank() || endGroup.isBlank() || startGroup == endGroup) {
            return TransferResult(emptyList(), emptyList())
        }
        val graph = getTransferGraph(db)

        data class BfsNode(val group: String, val lineKeysUsed: List<String>)
        val queue = ArrayDeque<BfsNode>()
        val bestDepth = HashMap<String, Int>()
        queue.add(BfsNode(startGroup, emptyList()))
        bestDepth[startGroup] = 0

        var minHops = Int.MAX_VALUE
        val validPaths = mutableListOf<List<String>>()

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur.lineKeysUsed.size > minHops || cur.lineKeysUsed.size >= 5) continue

            val linesHere = graph.groupToLineKeys[cur.group].orEmpty()
            for (lineKey in linesHere) {
                if (lineKey in cur.lineKeysUsed) continue
                val nextLines = cur.lineKeysUsed + lineKey
                val nextGroups = graph.lineKeyToGroups[lineKey].orEmpty()
                if (endGroup in nextGroups) {
                    if (nextLines.size < minHops) {
                        minHops = nextLines.size
                    }
                    if (nextLines.size <= minHops + 1) {
                        validPaths.add(nextLines)
                    }
                    continue
                }
                if (nextLines.size < 4 && nextLines.size < minHops) {
                    for (ng in nextGroups) {
                        val prev = bestDepth[ng]
                        if (prev == null || nextLines.size <= prev) {
                            bestDepth[ng] = nextLines.size
                            queue.add(BfsNode(ng, nextLines))
                        }
                    }
                }
            }
        }

        if (validPaths.isEmpty()) return TransferResult(emptyList(), emptyList())
        val shortest = validPaths.minBy { it.size }
        val candidates = validPaths
            .filter { it.size <= shortest.size + 1 }
            .flatten()
            .distinct()
            .take(12)
        return TransferResult(shortest, candidates)
    }

    private fun extractShortestTrackCorridorByKeys(
        db: SQLiteDatabase,
        lineKeys: Set<String>,
        corridorLineNames: Set<String>,
        start: Coord,
        end: Coord
    ): List<List<Coord>> {
        if (lineKeys.isEmpty() && corridorLineNames.isEmpty()) return emptyList()

        val rawSegments = mutableListOf<List<Coord>>()
        for (key in lineKeys) {
            val parts = key.split("::", limit = 2)
            if (parts.size == 2) {
                db.rawQuery(
                    "SELECT geometry_json FROM railroad_sections WHERE company_name = ? AND line_name = ?",
                    arrayOf(parts[0], parts[1])
                ).use { c ->
                    while (c.moveToNext()) {
                        val seg = parseCoordArray(c.getString(0))
                        if (seg.size >= 2) rawSegments.add(seg)
                    }
                }
            }
        }
        if (corridorLineNames.isNotEmpty()) {
            val ph = corridorLineNames.joinToString(",") { "?" }
            db.rawQuery(
                "SELECT geometry_json FROM railroad_sections WHERE line_name IN ($ph)",
                corridorLineNames.toTypedArray()
            ).use { c ->
                while (c.moveToNext()) {
                    val seg = parseCoordArray(c.getString(0))
                    if (seg.size >= 2) rawSegments.add(seg)
                }
            }
        }
        if (rawSegments.isEmpty()) return emptyList()

        fun quantKey(pt: Coord): Long {
            val qx = (pt.lon * 400.0).roundToLong()
            val qy = (pt.lat * 400.0).roundToLong()
            return (qx shl 32) xor (qy and 0xFFFFFFFFL)
        }

        // Simultaneously project BOTH start and end onto continuous sub-edges [seg[i], seg[i+1]]
        // and slice in sorted parameter order so reverse-order stations on the same 500-vertex section work 100%!
        val splitSegments = ArrayList<List<Coord>>(rawSegments.size + 8)
        for (seg in rawSegments) {
            val splits = mutableListOf<Triple<Int, Double, Coord>>() // (edgeIndex, t, projectedPoint)
            for (target in listOf(start, end)) {
                var bestEdge = -1
                var bestT = 0.0
                var bestDist = Double.MAX_VALUE
                var bestProj = target
                for (i in 0 until seg.lastIndex) {
                    val a = seg[i]
                    val b = seg[i + 1]
                    val dx = b.lon - a.lon
                    val dy = b.lat - a.lat
                    val len2 = dx * dx + dy * dy
                    val t = if (len2 < 1e-12) 0.0 else (((target.lon - a.lon) * dx + (target.lat - a.lat) * dy) / len2).coerceIn(0.0, 1.0)
                    val px = a.lon + t * dx
                    val py = a.lat + t * dy
                    val d = hypot(target.lon - px, target.lat - py)
                    if (d < bestDist) {
                        bestDist = d
                        bestEdge = i
                        bestT = t
                        bestProj = Coord(px, py)
                    }
                }
                if (bestEdge >= 0 && bestDist < 0.015) {
                    // Only split interior if not already at start/end of the whole segment
                    val isAtFirst = bestEdge == 0 && bestT < 0.02
                    val isAtLast = bestEdge == seg.lastIndex - 1 && bestT > 0.98
                    if (!isAtFirst && !isAtLast) {
                        splits.add(Triple(bestEdge, bestT, bestProj))
                    }
                }
            }

            if (splits.isEmpty()) {
                splitSegments.add(seg)
            } else {
                val sortedSplits = splits.sortedWith(compareBy<Triple<Int, Double, Coord>> { it.first }.thenBy { it.second })
                var curPiece = mutableListOf<Coord>()
                var splitPtr = 0
                for (i in 0 until seg.lastIndex) {
                    curPiece.add(seg[i])
                    while (splitPtr < sortedSplits.size && sortedSplits[splitPtr].first == i) {
                        val sp = sortedSplits[splitPtr].third
                        if (hypot(curPiece.last().lon - sp.lon, curPiece.last().lat - sp.lat) > 1e-6) {
                            curPiece.add(sp)
                        }
                        if (curPiece.size >= 2) {
                            splitSegments.add(curPiece)
                        }
                        curPiece = mutableListOf(sp)
                        splitPtr++
                    }
                }
                curPiece.add(seg.last())
                if (curPiece.size >= 2) {
                    splitSegments.add(curPiece)
                }
            }
        }

        data class Edge(val toNode: Long, val weight: Double, val coords: List<Coord>)
        val nodeCoords = HashMap<Long, Coord>()
        val adj = HashMap<Long, MutableList<Edge>>()

        for (seg in splitSegments) {
            val uPt = seg.first()
            val vPt = seg.last()
            val u = quantKey(uPt)
            val v = quantKey(vPt)
            nodeCoords.putIfAbsent(u, uPt)
            nodeCoords.putIfAbsent(v, vPt)
            var len = 0.0
            for (i in 1 until seg.size) {
                len += hypot(seg[i].lon - seg[i - 1].lon, seg[i].lat - seg[i - 1].lat)
            }
            adj.getOrPut(u) { mutableListOf() }.add(Edge(v, len, seg))
            adj.getOrPut(v) { mutableListOf() }.add(Edge(u, len, seg.asReversed()))
        }

        val allNodes = nodeCoords.entries.toList()
        for (i in allNodes.indices) {
            val (idA, ptA) = allNodes[i]
            val qxA = (ptA.lon * 400.0).roundToLong()
            val qyA = (ptA.lat * 400.0).roundToLong()
            for (dx in -2L..2L) {
                for (dy in -2L..2L) {
                    if (dx == 0L && dy == 0L) continue
                    val neighborKey = ((qxA + dx) shl 32) xor ((qyA + dy) and 0xFFFFFFFFL)
                    val ptB = nodeCoords[neighborKey] ?: continue
                    if (idA < neighborKey) {
                        val gap = hypot(ptA.lon - ptB.lon, ptA.lat - ptB.lat)
                        if (gap <= 0.008) {
                            val bridge = listOf(ptA, ptB)
                            adj.getOrPut(idA) { mutableListOf() }.add(Edge(neighborKey, gap * 3.0, bridge))
                            adj.getOrPut(neighborKey) { mutableListOf() }.add(Edge(idA, gap * 3.0, bridge.asReversed()))
                        }
                    }
                }
            }
        }

        val startNode = nodeCoords.minByOrNull { (_, c) -> hypot(c.lon - start.lon, c.lat - start.lat) }?.key
            ?: return emptyList()
        val endNode = nodeCoords.minByOrNull { (_, c) -> hypot(c.lon - end.lon, c.lat - end.lat) }?.key
            ?: return emptyList()
        if (startNode == endNode) return listOf(listOf(start, end))

        val dist = HashMap<Long, Double>()
        val prevNode = HashMap<Long, Long>()
        val prevEdge = HashMap<Long, List<Coord>>()
        val pq = PriorityQueue<Pair<Long, Double>>(compareBy { it.second })

        dist[startNode] = 0.0
        pq.add(startNode to 0.0)

        while (pq.isNotEmpty()) {
            val (u, d) = pq.poll() ?: break
            if (d > (dist[u] ?: Double.MAX_VALUE)) continue
            if (u == endNode) break
            for (edge in adj[u].orEmpty()) {
                val nd = d + edge.weight
                if (nd < (dist[edge.toNode] ?: Double.MAX_VALUE)) {
                    dist[edge.toNode] = nd
                    prevNode[edge.toNode] = u
                    prevEdge[edge.toNode] = edge.coords
                    pq.add(edge.toNode to nd)
                }
            }
        }

        if (!prevNode.containsKey(endNode)) return emptyList()

        val orderedSegments = ArrayDeque<List<Coord>>()
        var cur = endNode
        while (cur != startNode) {
            val seg = prevEdge[cur] ?: break
            orderedSegments.addFirst(seg)
            cur = prevNode[cur] ?: break
        }

        val stitched = ArrayList<Coord>(256)
        for (seg in orderedSegments) {
            for (pt in seg) {
                val last = stitched.lastOrNull()
                if (last == null || hypot(last.lon - pt.lon, last.lat - pt.lat) > 1e-5) {
                    stitched.add(pt)
                }
            }
        }
        if (stitched.size < 2) return emptyList()
        // Simplify to 40m precision (~95% vertex reduction, zero visual loss, fast rendering & compact DataStore!)
        return listOf(RoutingAndPlaceService.simplifyRdpMeters(stitched, 40.0))
    }

    private fun queryStationLines(
        db: SQLiteDatabase,
        stationName: String,
        groupCode: String,
        lat: Double,
        lon: Double
    ): List<StationRecord> {
        val list = mutableListOf<StationRecord>()
        if (groupCode.isNotBlank()) {
            db.rawQuery(
                "SELECT id, company_name, line_name, station_name, group_code, latitude, longitude FROM stations WHERE group_code = ?",
                arrayOf(groupCode)
            ).use { c ->
                while (c.moveToNext()) list.add(c.toStationRecord())
            }
            if (list.isNotEmpty()) return list
        }
        val clean = stationName.trim().removeSuffix("駅")
        if (clean.isNotEmpty()) {
            db.rawQuery(
                """
                SELECT id, company_name, line_name, station_name, group_code, latitude, longitude
                FROM stations
                WHERE station_name = ?
                ORDER BY (latitude - ?) * (latitude - ?) + (longitude - ?) * (longitude - ?) ASC
                LIMIT 15
                """.trimIndent(),
                arrayOf(clean, lat.toString(), lat.toString(), lon.toString(), lon.toString())
            ).use { c ->
                while (c.moveToNext()) list.add(c.toStationRecord())
            }
        }
        return list
    }

    private fun parseCoordArray(json: String): List<Coord> {
        val out = ArrayList<Coord>(32)
        JsonReader(StringReader(json)).use { reader ->
            reader.beginArray()
            while (reader.hasNext()) {
                reader.beginArray()
                val lon = reader.nextDouble()
                val lat = reader.nextDouble()
                while (reader.hasNext()) reader.skipValue()
                reader.endArray()
                out.add(Coord(lon, lat))
            }
            reader.endArray()
        }
        return out
    }

    private fun android.database.Cursor.toStationRecord(): StationRecord = StationRecord(
        id = getLong(0),
        companyName = getString(1) ?: "",
        lineName = getString(2) ?: "",
        stationName = getString(3) ?: "",
        groupCode = getString(4) ?: "",
        latitude = getDouble(5),
        longitude = getDouble(6)
    )
}
