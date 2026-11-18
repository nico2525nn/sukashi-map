package jp.nico2525.sukashimap.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class AirportRecord(
    val iata: String,
    val icao: String,
    val name: String,
    val city: String,
    val country: String,
    val latitude: Double,
    val longitude: Double
) {
    val primaryCode: String get() = iata.ifBlank { icao }
    val dropdownLabel: String
        get() {
            val codePart = iata.ifBlank { icao }
            val cityPart = if (city.isNotBlank()) " ($city, $country)" else " ($country)"
            return "$codePart — $name$cityPart"
        }
}

class AirportRepository(private val context: Context) {
    private val initMutex = Mutex()
    @Volatile
    private var database: SQLiteDatabase? = null

    private val japaneseAirportAliases: Map<String, List<String>> = mapOf(
        "羽田" to listOf("HND"),
        "ハネダ" to listOf("HND"),
        "東京国際" to listOf("HND"),
        "成田" to listOf("NRT"),
        "ナリタ" to listOf("NRT"),
        "東京" to listOf("HND", "NRT"),
        "関空" to listOf("KIX"),
        "カンクウ" to listOf("KIX"),
        "関西" to listOf("KIX"),
        "伊丹" to listOf("ITM"),
        "イタミ" to listOf("ITM"),
        "大阪" to listOf("ITM", "KIX", "UKB"),
        "神戸" to listOf("UKB"),
        "コウベ" to listOf("UKB"),
        "新千歳" to listOf("CTS"),
        "シンチトセ" to listOf("CTS"),
        "千歳" to listOf("CTS"),
        "チトセ" to listOf("CTS"),
        "札幌" to listOf("CTS", "OKD"),
        "サッポロ" to listOf("CTS", "OKD"),
        "丘珠" to listOf("OKD"),
        "福岡" to listOf("FUK"),
        "フクオカ" to listOf("FUK"),
        "那覇" to listOf("OKA"),
        "ナハ" to listOf("OKA"),
        "沖縄" to listOf("OKA", "ISG", "MMY"),
        "オキナワ" to listOf("OKA", "ISG", "MMY"),
        "石垣" to listOf("ISG"),
        "イシガキ" to listOf("ISG"),
        "宮古" to listOf("MMY"),
        "ミヤコ" to listOf("MMY"),
        "中部" to listOf("NGO"),
        "セントレア" to listOf("NGO"),
        "名古屋" to listOf("NGO", "NKM"),
        "ナゴヤ" to listOf("NGO", "NKM"),
        "小牧" to listOf("NKM"),
        "仙台" to listOf("SDJ"),
        "センダイ" to listOf("SDJ"),
        "広島" to listOf("HIJ"),
        "ヒロシマ" to listOf("HIJ"),
        "鹿児島" to listOf("KOJ"),
        "カゴシマ" to listOf("KOJ"),
        "熊本" to listOf("KMJ"),
        "クマモト" to listOf("KMJ"),
        "宮崎" to listOf("KMI"),
        "ミヤザキ" to listOf("KMI"),
        "長崎" to listOf("NGS"),
        "ナガサキ" to listOf("NGS"),
        "大分" to listOf("OIT"),
        "オオイタ" to listOf("OIT"),
        "北九州" to listOf("KKJ"),
        "佐賀" to listOf("HSG"),
        "松山" to listOf("MYJ", "TSA"),
        "マツヤマ" to listOf("MYJ", "TSA"),
        "高松" to listOf("TAK"),
        "タカマツ" to listOf("TAK"),
        "高知" to listOf("KCZ"),
        "徳島" to listOf("TKS"),
        "小松" to listOf("KMQ"),
        "コマツ" to listOf("KMQ"),
        "金沢" to listOf("KMQ"),
        "富山" to listOf("TOY"),
        "新潟" to listOf("KIJ"),
        "函館" to listOf("HKD"),
        "ハコダテ" to listOf("HKD"),
        "旭川" to listOf("AKJ"),
        "釧路" to listOf("KUH"),
        "帯広" to listOf("OBO"),
        "女満別" to listOf("MMB"),
        "稚内" to listOf("WKJ"),
        "青森" to listOf("AOJ"),
        "三沢" to listOf("MSJ"),
        "秋田" to listOf("AXT"),
        "山形" to listOf("GAJ"),
        "庄内" to listOf("SYO"),
        "福島" to listOf("FKS"),
        "茨城" to listOf("IBR"),
        "静岡" to listOf("FSZ"),
        "松本" to listOf("MMJ"),
        "出雲" to listOf("IZO"),
        "米子" to listOf("YGJ"),
        "岡山" to listOf("OKJ"),
        "山口宇部" to listOf("UBJ"),
        "岩国" to listOf("IWK"),
        "奄美" to listOf("ASJ"),
        "与那国" to listOf("OGN"),
        "久米島" to listOf("UEO"),
        "仁川" to listOf("ICN"),
        "インチョン" to listOf("ICN"),
        "ソウル" to listOf("ICN", "GMP"),
        "金浦" to listOf("GMP"),
        "キンポ" to listOf("GMP"),
        "釜山" to listOf("PUS"),
        "プサン" to listOf("PUS"),
        "済州" to listOf("CJU"),
        "台北" to listOf("TPE", "TSA"),
        "タイペイ" to listOf("TPE", "TSA"),
        "桃園" to listOf("TPE"),
        "高雄" to listOf("KHH"),
        "香港" to listOf("HKG"),
        "ホンコン" to listOf("HKG"),
        "上海" to listOf("PVG", "SHA"),
        "北京" to listOf("PEK", "PKX"),
        "シンガポール" to listOf("SIN"),
        "チャンギ" to listOf("SIN"),
        "バンコク" to listOf("BKK", "DMK"),
        "スワンナプーム" to listOf("BKK"),
        "ホノルル" to listOf("HNL"),
        "ハワイ" to listOf("HNL", "KOA", "OGG"),
        "グアム" to listOf("GUM"),
        "ロサンゼルス" to listOf("LAX"),
        "サンフランシスコ" to listOf("SFO"),
        "ニューヨーク" to listOf("JFK", "EWR", "LGA"),
        "シアトル" to listOf("SEA"),
        "シカゴ" to listOf("ORD"),
        "バンクーバー" to listOf("YVR"),
        "ロンドン" to listOf("LHR", "LGW"),
        "ヒースロー" to listOf("LHR"),
        "パリ" to listOf("CDG", "ORY"),
        "フランクフルト" to listOf("FRA"),
        "ミュンヘン" to listOf("MUC"),
        "シドニー" to listOf("SYD"),
        "メルボルン" to listOf("MEL"),
        "ドバイ" to listOf("DXB"),
        "ドーハ" to listOf("DOH")
    )

    private fun hiraganaToKatakana(input: String): String = buildString(input.length) {
        for (ch in input) {
            if (ch in '\u3041'..'\u3096') {
                append((ch.code + 0x60).toChar())
            } else {
                append(ch)
            }
        }
    }

    private suspend fun getDb(): SQLiteDatabase = withContext(Dispatchers.IO) {
        database?.takeIf { it.isOpen }?.let { return@withContext it }
        initMutex.withLock {
            database?.takeIf { it.isOpen }?.let { return@withLock it }
            val dbFile = context.getDatabasePath("sukashi_airports_v1.db")
            if (!dbFile.exists() || dbFile.length() < 100_000L) {
                dbFile.parentFile?.mkdirs()
                val tmp = File(dbFile.parentFile, "sukashi_airports_v1.db.tmp")
                context.assets.open("airports.db").use { input ->
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

    suspend fun findByCode(code: String): AirportRecord? = withContext(Dispatchers.IO) {
        val clean = code.trim().uppercase()
        if (clean.isEmpty()) return@withContext null
        val db = getDb()
        db.rawQuery(
            """
            SELECT iata, icao, name, city, country, latitude, longitude
            FROM airports
            WHERE UPPER(iata) = ? OR UPPER(icao) = ?
            ORDER BY CASE WHEN UPPER(iata) = ? THEN 0 ELSE 1 END
            LIMIT 1
            """.trimIndent(),
            arrayOf(clean, clean, clean)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toAirportRecord() else null
        }
    }

    suspend fun searchAirports(query: String, limit: Int = 20): List<AirportRecord> = withContext(Dispatchers.IO) {
        val rawQ = query.trim()
        val db = getDb()
        if (rawQ.isEmpty()) {
            val popular = listOf("HND", "NRT", "KIX", "ITM", "CTS", "FUK", "OKA", "NGO", "SDJ", "HIJ", "ICN", "TPE", "SIN", "HNL", "LAX", "LHR")
            val placeholders = popular.joinToString(",") { "?" }
            val results = mutableListOf<AirportRecord>()
            db.rawQuery(
                "SELECT iata, icao, name, city, country, latitude, longitude FROM airports WHERE iata IN ($placeholders)",
                popular.toTypedArray()
            ).use { c ->
                while (c.moveToNext()) results.add(c.toAirportRecord())
            }
            return@withContext results.sortedBy { popular.indexOf(it.iata).let { idx -> if (idx < 0) 999 else idx } }
        }

        val normalizedJp = hiraganaToKatakana(
            rawQ.removeSuffix("国際空港").removeSuffix("空港").trim()
        )
        val rawCleaned = rawQ.removeSuffix("国際空港").removeSuffix("空港").trim()

        val aliasCodes = if (rawCleaned.isNotEmpty()) {
            japaneseAirportAliases.entries
                .filter { (k, _) ->
                    k.contains(rawCleaned) || rawCleaned.contains(k) ||
                        (normalizedJp.isNotEmpty() && (k.contains(normalizedJp) || normalizedJp.contains(k)))
                }
                .flatMap { it.value }
                .distinct()
        } else emptyList()

        val results = LinkedHashMap<String, AirportRecord>()
        if (aliasCodes.isNotEmpty()) {
            val ph = aliasCodes.joinToString(",") { "?" }
            val fetched = mutableListOf<AirportRecord>()
            db.rawQuery(
                "SELECT iata, icao, name, city, country, latitude, longitude FROM airports WHERE iata IN ($ph)",
                aliasCodes.toTypedArray()
            ).use { c ->
                while (c.moveToNext()) fetched.add(c.toAirportRecord())
            }
            // Preserve exact hub priority order (e.g. OKA before ISG, LHR before LGW, ICN before GMP!)
            fetched.sortedBy { aliasCodes.indexOf(it.iata).let { idx -> if (idx < 0) 999 else idx } }
                .forEach { rec -> results["${rec.iata}:${rec.icao}"] = rec }
        }

        val likeAny = "%$rawQ%"
        val likePrefix = "$rawQ%"
        db.rawQuery(
            """
            SELECT iata, icao, name, city, country, latitude, longitude
            FROM airports
            WHERE iata LIKE ? OR icao LIKE ? OR city LIKE ? OR name LIKE ?
            ORDER BY CASE
                WHEN UPPER(iata) = UPPER(?) THEN 1
                WHEN UPPER(icao) = UPPER(?) THEN 2
                WHEN UPPER(iata) LIKE UPPER(?) THEN 3
                WHEN UPPER(icao) LIKE UPPER(?) THEN 4
                WHEN UPPER(city) LIKE UPPER(?) THEN 5
                WHEN UPPER(name) LIKE UPPER(?) THEN 6
                ELSE 7
            END,
            CASE WHEN iata != '' THEN 0 ELSE 1 END,
            name ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(likeAny, likeAny, likeAny, likeAny, rawQ, rawQ, likePrefix, likePrefix, likePrefix, likePrefix, limit.toString())
        ).use { c ->
            while (c.moveToNext() && results.size < limit) {
                val rec = c.toAirportRecord()
                results.putIfAbsent("${rec.iata}:${rec.icao}", rec)
            }
        }
        results.values.toList()
    }

    private fun android.database.Cursor.toAirportRecord(): AirportRecord = AirportRecord(
        iata = getString(0) ?: "",
        icao = getString(1) ?: "",
        name = getString(2) ?: "",
        city = getString(3) ?: "",
        country = getString(4) ?: "",
        latitude = getDouble(5),
        longitude = getDouble(6)
    )
}
