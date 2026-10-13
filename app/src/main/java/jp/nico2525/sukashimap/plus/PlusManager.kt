package jp.nico2525.sukashimap.plus

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant
import java.time.ZoneOffset

enum class PlusPlan(val productId: String, val days: Long, val years: Long) {
    DAYS_2("passage_plus_2days", 2, 0),
    WEEK_1("passage_plus_1week", 7, 0),
    YEAR_1("passage_plus_1year", 0, 1);

    fun expiryFrom(purchaseMs: Long): Long {
        val zdt = Instant.ofEpochMilli(purchaseMs).atZone(ZoneOffset.UTC)
        val end = if (years > 0) zdt.plusYears(years) else zdt.plusDays(days)
        return end.toInstant().toEpochMilli()
    }

    companion object {
        fun byProductId(id: String) = entries.firstOrNull { it.productId == id }
    }
}

data class PlusState(
    val active: Boolean,
    val expiryMs: Long = 0,
    val products: Set<String> = emptySet(),
    val offlineGrace: Boolean = false,
)

/**
 * Plus entitlement: JSON file in no-backup dir + legacy prefs migration.
 * Time source: purchase time extended by plan; expiry re-verified when online.
 */
class PlusManager(context: Context) {
    private val app = context.applicationContext
    private val file = AtomicFile(File(app.noBackupFilesDir, "plus_entitlement.json"))
    private val prefs = app.getSharedPreferences("sukashi_settings", Context.MODE_PRIVATE)

    fun current(): PlusState {
        val now = System.currentTimeMillis()
        val (expiry, products) = read()
        if (expiry > now) return PlusState(true, expiry, products)
        return PlusState(false, expiry, products)
    }

    fun isActive(): Boolean = current().active

    fun grant(productId: String, purchaseMs: Long) {
        val plan = PlusPlan.byProductId(productId) ?: return
        val expiry = plan.expiryFrom(purchaseMs)
        val (oldExpiry, oldProducts) = read()
        write(maxOf(expiry, oldExpiry), oldProducts + productId)
    }

    fun remainingText(expiryMs: Long): String {
        val now = System.currentTimeMillis()
        var rem = (expiryMs - now).coerceAtLeast(0)
        val days = rem / 86_400_000
        rem %= 86_400_000
        val hours = rem / 3_600_000
        rem %= 3_600_000
        val mins = rem / 60_000
        return when {
            days > 0 -> "${days}d ${hours}h left"
            hours > 0 -> "${hours}h ${mins}m left"
            else -> "${mins}m left"
        }
    }

    private fun read(): Pair<Long, Set<String>> {
        try {
            val bytes = file.readFully()
            val o = JSONObject(String(bytes, Charsets.UTF_8))
            val expiry = o.optLong("expiry", 0).coerceAtLeast(0)
            val products = o.optString("products", "")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            return Pair(expiry, products)
        } catch (_: FileNotFoundException) {
            // legacy prefs migration
            val expiry = prefs.getLong("plus_cache_expiration_ms", 0)
            val products = prefs.getString("plus_cache_product_ids", "")
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
            if (expiry > 0) write(expiry, products)
            return Pair(expiry, products)
        } catch (_: Exception) {
            return Pair(0, emptySet())
        }
    }

    private fun write(expiry: Long, products: Set<String>) {
        try {
            val o = JSONObject()
            o.put("expiry", expiry)
            o.put("products", products.joinToString(","))
            val bytes = o.toString().toByteArray(Charsets.UTF_8)
            val out = file.startWrite()
            try {
                out.write(bytes)
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
            }
        } catch (_: Exception) {
        }
    }
}
