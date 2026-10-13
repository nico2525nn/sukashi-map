package jp.nico2525.sukashimap.triprecap

/** One user-curated stop on the recap map. */
data class VisitStop(
    val id: Long,
    var name: String,
    var lat: Double,
    var lon: Double,
    var dateText: String = "",
)

/** Poster options edited through the Ratio / Style panels. */
data class RecapStyle(
    var aspectIndex: Int = 0,
    var zoom: Float = 0.5f,
    var cornerRadiusPx: Int = 0,
    var backgroundColor: Int = 0xFF101418.toInt(),
    var textColor: Int = 0xFFFFFFFF.toInt(),
    var fontKind: Int = 0,
    var showPlaceNames: Boolean = true,
    var routeColor: Int = 0xFFFF7043.toInt(),
    var highQuality: Boolean = false,
) {
    companion object {
        /** width to height pairs. */
        val ASPECTS = listOf(
            1 to 1,
            4 to 5,
            2 to 3,
            9 to 16,
        )
        val ASPECT_LABELS = listOf("1:1", "4:5", "2:3", "9:16")
    }
}
