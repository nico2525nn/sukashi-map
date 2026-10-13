package jp.nico2525.sukashimap.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Remembers recent TransportInfo form inputs per mode, surfaced as history chips.
 * Stored as JSON-ish strings in SharedPreferences (no Room dependency for MVP).
 */
class HistoryManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sukashi_history", Context.MODE_PRIVATE)

    data class Entry(val mode: TransportMode, val title: String, val payload: Map<String, String>)

    fun load(mode: TransportMode): List<Entry> {
        val raw = prefs.getStringSet("history_${mode.name}", emptySet()) ?: return emptyList()
        return raw.mapNotNull { parse(mode, it) }.sortedByDescending { it.payload["ts"] ?: "" }.take(12)
    }

    fun save(entry: Entry) {
        val key = "history_${entry.mode.name}"
        val set = prefs.getStringSet(key, emptySet())!!.toMutableSet()
        val flat = entry.payload.entries.joinToString("\u0001") { "${it.key}=${it.value}" }
        set.removeAll { it.startsWith("${entry.title}\u0002") }
        set.add("${entry.title}\u0002$flat")
        while (set.size > 30) set.remove(set.first())
        prefs.edit().putStringSet(key, set).apply()
    }

    fun clear(mode: TransportMode) {
        prefs.edit().remove("history_${mode.name}").apply()
    }

    private fun parse(mode: TransportMode, raw: String): Entry? {
        val parts = raw.split("\u0002")
        if (parts.size != 2) return null
        val payload = parts[1].split("\u0001").mapNotNull {
            val kv = it.split("=", limit = 2)
            if (kv.size == 2) kv[0] to kv[1] else null
        }.toMap()
        return Entry(mode, parts[0], payload)
    }
}
