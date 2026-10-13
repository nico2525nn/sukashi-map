package jp.nico2525.sukashimap.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import jp.nico2525.sukashimap.data.LocationDisplayMode

/** App settings persisted in SharedPreferences ("sukashi_settings"). */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sukashi_settings", Context.MODE_PRIVATE)

    var autoGeotagging: Boolean
        get() = prefs.getBoolean("auto_geotagging_enabled", true)
        set(v) = prefs.edit().putBoolean("auto_geotagging_enabled", v).apply()

    var preserveHdr: Boolean
        get() = prefs.getBoolean("preserve_hdr_enabled", true)
        set(v) = prefs.edit().putBoolean("preserve_hdr_enabled", v).apply()

    var locationDisplayMode: LocationDisplayMode
        get() = runCatching {
            LocationDisplayMode.valueOf(prefs.getString("location_display_mode", "REGION_NAME")!!)
        }.getOrDefault(LocationDisplayMode.REGION_NAME)
        set(v) = prefs.edit().putString("location_display_mode", v.name).apply()

    var gallerySortTaken: Boolean
        get() = prefs.getBoolean("gallery_sort_taken", false)
        set(v) = prefs.edit().putBoolean("gallery_sort_taken", v).apply()

    var galleryColumns: Int
        get() = prefs.getInt("gallery_columns", 3).coerceIn(1, 5)
        set(v) = prefs.edit().putInt("gallery_columns", v).apply()

    var languageTag: String
        get() = prefs.getString("language_tag", "") ?: ""
        set(v) {
            prefs.edit().putString("language_tag", v).apply()
            AppCompatDelegate.setApplicationLocales(
                if (v.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(v),
            )
        }

    fun applySavedLocale() {
        val tag = prefs.getString("language_tag", "") ?: ""
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag),
        )
    }

    /** Overlay style knobs per transport mode (font index, colors). */
    fun overlayFontIndex(mode: String): Int = prefs.getInt("font_$mode", 0)
    fun setOverlayFontIndex(mode: String, i: Int) = prefs.edit().putInt("font_$mode", i).apply()
    fun textColor(mode: String): Int = prefs.getInt("textcolor_$mode", -1)
    fun setTextColor(mode: String, c: Int) = prefs.edit().putInt("textcolor_$mode", c).apply()
    fun mapLineColor(mode: String): Int = prefs.getInt("mapline_$mode", 0xFFFF5252.toInt())
    fun setMapLineColor(mode: String, c: Int) = prefs.edit().putInt("mapline_$mode", c).apply()
    fun mapBorderColor(mode: String): Int = prefs.getInt("mapborder_$mode", -1)
    fun setMapBorderColor(mode: String, c: Int) = prefs.edit().putInt("mapborder_$mode", c).apply()

    var reviewPrompted: Boolean
        get() = prefs.getBoolean("review_prompted", false)
        set(v) = prefs.edit().putBoolean("review_prompted", v).apply()

    var saveCount: Int
        get() = prefs.getInt("save_count", 0)
        set(v) = prefs.edit().putInt("save_count", v).apply()
}
