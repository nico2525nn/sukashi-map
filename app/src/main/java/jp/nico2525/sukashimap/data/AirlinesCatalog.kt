package jp.nico2525.sukashimap.data

data class AirlineEntry(
    val code: String,
    val shortName: String,
    val fullName: String,
    val country: String = ""
) {
    val dropdownLabel: String
        get() = if (shortName.equals(fullName, ignoreCase = true)) {
            "$code: $fullName"
        } else {
            "$code: $shortName ($fullName)"
        }
}

object AirlinesCatalog {
    val all: List<AirlineEntry> = listOf(
        // Japanese Carriers (All 17)
        AirlineEntry("NH", "ANA", "All Nippon Airways", "JP"),
        AirlineEntry("JL", "JAL", "Japan Airlines", "JP"),
        AirlineEntry("BC", "Skymark", "Skymark Airlines", "JP"),
        AirlineEntry("MM", "Peach", "Peach Aviation", "JP"),
        AirlineEntry("GK", "Jetstar", "Jetstar Japan", "JP"),
        AirlineEntry("7G", "StarFlyer", "Star Flyer", "JP"),
        AirlineEntry("HD", "AIRDO", "Air Do", "JP"),
        AirlineEntry("6J", "Solaseed", "Solaseed Air", "JP"),
        AirlineEntry("JH", "FDA", "Fuji Dream Airlines", "JP"),
        AirlineEntry("ZG", "ZIPAIR", "ZIPAIR Tokyo", "JP"),
        AirlineEntry("NQ", "AirJapan", "AirJapan", "JP"),
        AirlineEntry("IJ", "Spring Japan", "Spring Airlines Japan", "JP"),
        AirlineEntry("NU", "JTA", "Japan Transocean Air", "JP"),
        AirlineEntry("RC", "RAC", "Ryukyu Air Commuter", "JP"),
        AirlineEntry("MZ", "Amakusa", "Amakusa Airlines", "JP"),
        AirlineEntry("OC", "ORC", "Oriental Air Bridge", "JP"),
        AirlineEntry("BV", "Toki Air", "Toki Air", "JP"),
        AirlineEntry("JC", "JAC", "Japan Air Commuter", "JP"),
        AirlineEntry("XM", "J-AIR", "J-AIR", "JP"),
        AirlineEntry("FW", "IBEX", "IBEX Airlines", "JP"),

        // Asia-Pacific
        AirlineEntry("SQ", "Singapore", "Singapore Airlines"),
        AirlineEntry("TR", "Scoot", "Scoot"),
        AirlineEntry("CX", "Cathay", "Cathay Pacific"),
        AirlineEntry("HX", "HK Airlines", "Hong Kong Airlines"),
        AirlineEntry("UO", "HK Express", "HK Express"),
        AirlineEntry("HB", "Greater Bay", "Greater Bay Airlines"),
        AirlineEntry("KE", "Korean Air", "Korean Air"),
        AirlineEntry("OZ", "Asiana", "Asiana Airlines"),
        AirlineEntry("7C", "Jeju Air", "Jeju Air"),
        AirlineEntry("LJ", "Jin Air", "Jin Air"),
        AirlineEntry("TW", "T'way", "T'way Air"),
        AirlineEntry("BX", "Air Busan", "Air Busan"),
        AirlineEntry("RS", "Air Seoul", "Air Seoul"),
        AirlineEntry("ZE", "Eastar Jet", "Eastar Jet"),
        AirlineEntry("YP", "Air Premia", "Air Premia"),
        AirlineEntry("CI", "China Airlines", "China Airlines"),
        AirlineEntry("BR", "EVA Air", "EVA Air"),
        AirlineEntry("JX", "STARLUX", "STARLUX Airlines"),
        AirlineEntry("IT", "Tigerair Taiwan", "Tigerair Taiwan"),
        AirlineEntry("CA", "Air China", "Air China"),
        AirlineEntry("MU", "China Eastern", "China Eastern Airlines"),
        AirlineEntry("CZ", "China Southern", "China Southern Airlines"),
        AirlineEntry("HU", "Hainan", "Hainan Airlines"),
        AirlineEntry("ZH", "Shenzhen", "Shenzhen Airlines"),
        AirlineEntry("MF", "XiamenAir", "Xiamen Airlines"),
        AirlineEntry("FM", "Shanghai", "Shanghai Airlines"),
        AirlineEntry("HO", "Juneyao", "Juneyao Air"),
        AirlineEntry("9C", "Spring", "Spring Airlines"),
        AirlineEntry("3U", "Sichuan", "Sichuan Airlines"),
        AirlineEntry("NX", "Air Macau", "Air Macau"),
        AirlineEntry("TG", "THAI", "Thai Airways International"),
        AirlineEntry("FD", "Thai AirAsia", "Thai AirAsia"),
        AirlineEntry("XJ", "Thai AirAsia X", "Thai AirAsia X"),
        AirlineEntry("SL", "Thai Lion", "Thai Lion Air"),
        AirlineEntry("VZ", "Thai Vietjet", "Thai Vietjet Air"),
        AirlineEntry("PG", "Bangkok Airways", "Bangkok Airways"),
        AirlineEntry("VN", "Vietnam Airlines", "Vietnam Airlines"),
        AirlineEntry("VJ", "VietJet", "VietJet Air"),
        AirlineEntry("QH", "Bamboo", "Bamboo Airways"),
        AirlineEntry("MH", "Malaysia", "Malaysia Airlines"),
        AirlineEntry("AK", "AirAsia", "AirAsia"),
        AirlineEntry("D7", "AirAsia X", "AirAsia X"),
        AirlineEntry("OD", "Batik Air", "Batik Air Malaysia"),
        AirlineEntry("GA", "Garuda", "Garuda Indonesia"),
        AirlineEntry("JT", "Lion Air", "Lion Air"),
        AirlineEntry("PR", "PAL", "Philippine Airlines"),
        AirlineEntry("5J", "Cebu Pacific", "Cebu Pacific"),
        AirlineEntry("BI", "Royal Brunei", "Royal Brunei Airlines"),
        AirlineEntry("QF", "Qantas", "Qantas"),
        AirlineEntry("JQ", "Jetstar", "Jetstar Airways"),
        AirlineEntry("VA", "Virgin Australia", "Virgin Australia"),
        AirlineEntry("NZ", "Air New Zealand", "Air New Zealand"),
        AirlineEntry("FJ", "Fiji Airways", "Fiji Airways"),
        AirlineEntry("PX", "Air Niugini", "Air Niugini"),
        AirlineEntry("SB", "Aircalin", "Aircalin"),
        AirlineEntry("TN", "Air Tahiti Nui", "Air Tahiti Nui"),
        AirlineEntry("AI", "Air India", "Air India"),
        AirlineEntry("6E", "IndiGo", "IndiGo"),
        AirlineEntry("UK", "Vistara", "Vistara"),
        AirlineEntry("UL", "SriLankan", "SriLankan Airlines"),
        AirlineEntry("PK", "PIA", "Pakistan International Airlines"),
        AirlineEntry("BG", "Biman", "Biman Bangladesh Airlines"),
        AirlineEntry("RA", "Nepal Airlines", "Nepal Airlines"),
        AirlineEntry("OM", "MIAT", "MIAT Mongolian Airlines"),
        AirlineEntry("KC", "Air Astana", "Air Astana"),
        AirlineEntry("HY", "Uzbekistan", "Uzbekistan Airways"),

        // North America
        AirlineEntry("AA", "American", "American Airlines"),
        AirlineEntry("DL", "Delta", "Delta Air Lines"),
        AirlineEntry("UA", "United", "United Airlines"),
        AirlineEntry("AS", "Alaska", "Alaska Airlines"),
        AirlineEntry("HA", "Hawaiian", "Hawaiian Airlines"),
        AirlineEntry("WN", "Southwest", "Southwest Airlines"),
        AirlineEntry("B6", "JetBlue", "JetBlue Airways"),
        AirlineEntry("NK", "Spirit", "Spirit Airlines"),
        AirlineEntry("F9", "Frontier", "Frontier Airlines"),
        AirlineEntry("G4", "Allegiant", "Allegiant Air"),
        AirlineEntry("MX", "Breeze", "Breeze Airways"),
        AirlineEntry("SY", "Sun Country", "Sun Country Airlines"),
        AirlineEntry("AC", "Air Canada", "Air Canada"),
        AirlineEntry("WS", "WestJet", "WestJet"),
        AirlineEntry("TS", "Air Transat", "Air Transat"),
        AirlineEntry("PD", "Porter", "Porter Airlines"),
        AirlineEntry("AM", "Aeromexico", "Aeromexico"),
        AirlineEntry("Y4", "Volaris", "Volaris"),
        AirlineEntry("VB", "VivaAerobus", "VivaAerobus"),

        // Europe
        AirlineEntry("BA", "British Airways", "British Airways"),
        AirlineEntry("VS", "Virgin Atlantic", "Virgin Atlantic"),
        AirlineEntry("U2", "easyJet", "easyJet"),
        AirlineEntry("FR", "Ryanair", "Ryanair"),
        AirlineEntry("LS", "Jet2", "Jet2.com"),
        AirlineEntry("EI", "Aer Lingus", "Aer Lingus"),
        AirlineEntry("LH", "Lufthansa", "Lufthansa"),
        AirlineEntry("EW", "Eurowings", "Eurowings"),
        AirlineEntry("DE", "Condor", "Condor"),
        AirlineEntry("LX", "SWISS", "Swiss International Air Lines"),
        AirlineEntry("OS", "Austrian", "Austrian Airlines"),
        AirlineEntry("SN", "Brussels", "Brussels Airlines"),
        AirlineEntry("AF", "Air France", "Air France"),
        AirlineEntry("KL", "KLM", "KLM Royal Dutch Airlines"),
        AirlineEntry("HV", "Transavia", "Transavia"),
        AirlineEntry("AY", "Finnair", "Finnair"),
        AirlineEntry("SK", "SAS", "Scandinavian Airlines"),
        AirlineEntry("DY", "Norwegian", "Norwegian Air Shuttle"),
        AirlineEntry("FI", "Icelandair", "Icelandair"),
        AirlineEntry("IB", "Iberia", "Iberia"),
        AirlineEntry("VY", "Vueling", "Vueling"),
        AirlineEntry("UX", "Air Europa", "Air Europa"),
        AirlineEntry("TP", "TAP", "TAP Air Portugal"),
        AirlineEntry("AZ", "ITA Airways", "ITA Airways"),
        AirlineEntry("A3", "Aegean", "Aegean Airlines"),
        AirlineEntry("LO", "LOT", "LOT Polish Airlines"),
        AirlineEntry("OK", "CSA", "Czech Airlines"),
        AirlineEntry("RO", "TAROM", "TAROM"),
        AirlineEntry("JU", "Air Serbia", "Air Serbia"),
        AirlineEntry("OU", "Croatia", "Croatia Airlines"),
        AirlineEntry("BT", "airBaltic", "airBaltic"),
        AirlineEntry("W6", "Wizz Air", "Wizz Air"),
        AirlineEntry("TK", "Turkish", "Turkish Airlines"),
        AirlineEntry("PC", "Pegasus", "Pegasus Airlines"),

        // Middle East & Africa
        AirlineEntry("EK", "Emirates", "Emirates"),
        AirlineEntry("EY", "Etihad", "Etihad Airways"),
        AirlineEntry("FZ", "flydubai", "flydubai"),
        AirlineEntry("G9", "Air Arabia", "Air Arabia"),
        AirlineEntry("QR", "Qatar", "Qatar Airways"),
        AirlineEntry("SV", "Saudia", "Saudia"),
        AirlineEntry("XY", "flynas", "flynas"),
        AirlineEntry("WY", "Oman Air", "Oman Air"),
        AirlineEntry("GF", "Gulf Air", "Gulf Air"),
        AirlineEntry("KU", "Kuwait", "Kuwait Airways"),
        AirlineEntry("RJ", "Royal Jordanian", "Royal Jordanian"),
        AirlineEntry("ME", "MEA", "Middle East Airlines"),
        AirlineEntry("LY", "El Al", "El Al Israel Airlines"),
        AirlineEntry("MS", "EgyptAir", "EgyptAir"),
        AirlineEntry("ET", "Ethiopian", "Ethiopian Airlines"),
        AirlineEntry("KQ", "Kenya Airways", "Kenya Airways"),
        AirlineEntry("AT", "Royal Air Maroc", "Royal Air Maroc"),
        AirlineEntry("SA", "SAA", "South African Airways"),
        AirlineEntry("MK", "Air Mauritius", "Air Mauritius"),

        // Latin America
        AirlineEntry("LA", "LATAM", "LATAM Airlines"),
        AirlineEntry("AV", "Avianca", "Avianca"),
        AirlineEntry("CM", "Copa", "Copa Airlines"),
        AirlineEntry("AR", "Aerolineas", "Aerolineas Argentinas"),
        AirlineEntry("G3", "GOL", "GOL Linhas Aereas"),
        AirlineEntry("AD", "Azul", "Azul Brazilian Airlines")
    )

    fun search(query: String, limit: Int = 25): List<AirlineEntry> {
        val q = query.trim()
        if (q.isEmpty()) return all.take(limit)
        return all
            .mapNotNull { entry ->
                val score = when {
                    entry.code.equals(q, ignoreCase = true) -> 0
                    entry.shortName.equals(q, ignoreCase = true) -> 1
                    entry.code.startsWith(q, ignoreCase = true) -> 2
                    entry.shortName.startsWith(q, ignoreCase = true) -> 3
                    entry.fullName.startsWith(q, ignoreCase = true) -> 4
                    entry.shortName.contains(q, ignoreCase = true) || entry.fullName.contains(q, ignoreCase = true) -> 5
                    else -> null
                }
                score?.let { it to entry }
            }
            .sortedBy { it.first }
            .map { it.second }
            .take(limit)
    }
}
