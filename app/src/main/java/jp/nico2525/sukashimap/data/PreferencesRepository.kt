package jp.nico2525.sukashimap.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import jp.nico2525.sukashimap.model.ExportFormat
import jp.nico2525.sukashimap.model.OverlayCorner
import jp.nico2525.sukashimap.model.OverlayFont
import jp.nico2525.sukashimap.model.OverlayStyle
import jp.nico2525.sukashimap.model.StylePreset
import jp.nico2525.sukashimap.model.TransportInfo
import jp.nico2525.sukashimap.model.TransportMode
import jp.nico2525.sukashimap.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.sukashiDataStore by preferencesDataStore(name = "sukashi_map_prefs")

data class UserPreferencesState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val autoLocationEnabled: Boolean = true,
    val preserveUltraHdr: Boolean = true,
    val defaultExportFormat: ExportFormat = ExportFormat.JPEG_95,
    val savedStyle: OverlayStyle = OverlayStyle(),
    val recentHistory: List<TransportInfo> = emptyList(),
    val stylePresets: List<StylePreset> = PreferencesRepository.defaultPresets
)

class PreferencesRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_AUTO_LOCATION = booleanPreferencesKey("auto_location")
        private val KEY_PRESERVE_HDR = booleanPreferencesKey("preserve_ultra_hdr")
        private val KEY_EXPORT_FORMAT = stringPreferencesKey("default_export_format")
        private val KEY_SAVED_STYLE = stringPreferencesKey("saved_overlay_style")
        private val KEY_RECENT_HISTORY = stringPreferencesKey("recent_transport_history")
        private val KEY_STYLE_PRESETS = stringPreferencesKey("saved_style_presets")

        val defaultPresets: List<StylePreset> = listOf(
            StylePreset(
                id = "classic_sukashi",
                name = "Sukashi Classic (Outfit)",
                style = OverlayStyle(
                    font = OverlayFont.OUTFIT,
                    textColorArgb = 0xFFFFFFFF.toInt(),
                    routeColorArgb = 0xFFFFFFFF.toInt(),
                    borderColorArgb = 0xFFFFFFFF.toInt(),
                    textCorner = OverlayCorner.BOTTOM_RIGHT,
                    mapCorner = OverlayCorner.BOTTOM_LEFT
                )
            ),
            StylePreset(
                id = "rail_amber",
                name = "Shinkansen Amber (Barlow)",
                style = OverlayStyle(
                    font = OverlayFont.BARLOW,
                    textColorArgb = 0xFFFFFFFF.toInt(),
                    routeColorArgb = 0xFFFFB900.toInt(),
                    borderColorArgb = 0xFFFFFFFF.toInt(),
                    textCorner = OverlayCorner.TOP_LEFT,
                    mapCorner = OverlayCorner.TOP_RIGHT
                )
            ),
            StylePreset(
                id = "kaisei_mincho",
                name = "Kaisei 旅情明朝",
                style = OverlayStyle(
                    font = OverlayFont.KAISEI_TOKUMIN,
                    textColorArgb = 0xFFFFFFFF.toInt(),
                    routeColorArgb = 0xFFD83B01.toInt(),
                    borderColorArgb = 0xFFFFFFFF.toInt(),
                    textCorner = OverlayCorner.BOTTOM_LEFT,
                    mapCorner = OverlayCorner.TOP_RIGHT
                )
            ),
            StylePreset(
                id = "white_frame_ink",
                name = "White Frame Ink",
                style = OverlayStyle(
                    font = OverlayFont.DM_SANS,
                    textColorArgb = 0xFF1A1A1A.toInt(),
                    routeColorArgb = 0xFF0078D7.toInt(),
                    borderColorArgb = 0xFF1A1A1A.toInt(),
                    textCorner = OverlayCorner.BOTTOM_RIGHT,
                    mapCorner = OverlayCorner.BOTTOM_LEFT
                )
            )
        )
    }

    val stateFlow: Flow<UserPreferencesState> = context.sukashiDataStore.data.map { prefs ->
        val theme = prefs[KEY_THEME_MODE]?.let {
            runCatching { ThemeMode.valueOf(it) }.getOrNull()
        } ?: ThemeMode.SYSTEM
        val exportFormat = prefs[KEY_EXPORT_FORMAT]?.let {
            runCatching { ExportFormat.valueOf(it) }.getOrNull()
        } ?: ExportFormat.JPEG_95
        val style = prefs[KEY_SAVED_STYLE]?.let {
            runCatching { json.decodeFromString<OverlayStyle>(it) }.getOrNull()
        } ?: OverlayStyle()
        val history = prefs[KEY_RECENT_HISTORY]?.let {
            runCatching { json.decodeFromString<List<TransportInfo>>(it) }.getOrNull()
        } ?: emptyList()
        val presets = prefs[KEY_STYLE_PRESETS]?.let {
            runCatching { json.decodeFromString<List<StylePreset>>(it) }.getOrNull()
        } ?: defaultPresets

        UserPreferencesState(
            themeMode = theme,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true,
            autoLocationEnabled = prefs[KEY_AUTO_LOCATION] ?: true,
            preserveUltraHdr = prefs[KEY_PRESERVE_HDR] ?: true,
            defaultExportFormat = exportFormat,
            savedStyle = style,
            recentHistory = history,
            stylePresets = presets.ifEmpty { defaultPresets }
        )
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.sukashiDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun updateDynamicColor(enabled: Boolean) {
        context.sukashiDataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun updateAutoLocation(enabled: Boolean) {
        context.sukashiDataStore.edit { it[KEY_AUTO_LOCATION] = enabled }
    }

    suspend fun updatePreserveHdr(enabled: Boolean) {
        context.sukashiDataStore.edit { it[KEY_PRESERVE_HDR] = enabled }
    }

    suspend fun updateExportFormat(format: ExportFormat) {
        context.sukashiDataStore.edit { it[KEY_EXPORT_FORMAT] = format.name }
    }

    suspend fun saveOverlayStyle(style: OverlayStyle) {
        context.sukashiDataStore.edit {
            it[KEY_SAVED_STYLE] = json.encodeToString(style)
        }
    }

    suspend fun recordHistory(info: TransportInfo) {
        context.sukashiDataStore.edit { prefs ->
            val existing = prefs[KEY_RECENT_HISTORY]?.let {
                runCatching { json.decodeFromString<List<TransportInfo>>(it) }.getOrNull()
            }.orEmpty()
            // Cap to 5 entries per mode with simplified polylines
            val compactEntry = info.copy(
                routePolylines = info.routePolylines.map { poly ->
                    if (poly.size > 180) RoutingAndPlaceService.simplifyRdpMeters(poly, 120.0) else poly
                }
            )
            val sameMode = existing.filter { it.mode == info.mode && it.summaryLabel() != info.summaryLabel() }.take(4)
            val otherModes = TransportMode.entries
                .filter { it != info.mode }
                .flatMap { m -> existing.filter { it.mode == m }.take(5) }
            val updated = listOf(compactEntry) + sameMode + otherModes
            prefs[KEY_RECENT_HISTORY] = json.encodeToString(updated)
        }
    }

    suspend fun clearHistoryForMode(mode: TransportMode?) {
        context.sukashiDataStore.edit { prefs ->
            if (mode == null) {
                prefs.remove(KEY_RECENT_HISTORY)
            } else {
                val existing = prefs[KEY_RECENT_HISTORY]?.let {
                    runCatching { json.decodeFromString<List<TransportInfo>>(it) }.getOrNull()
                }.orEmpty()
                prefs[KEY_RECENT_HISTORY] = json.encodeToString(existing.filter { it.mode != mode })
            }
        }
    }

    suspend fun saveCustomPreset(name: String, style: OverlayStyle) {
        context.sukashiDataStore.edit { prefs ->
            val current = prefs[KEY_STYLE_PRESETS]?.let {
                runCatching { json.decodeFromString<List<StylePreset>>(it) }.getOrNull()
            } ?: defaultPresets
            val newPreset = StylePreset(
                id = "custom_${System.currentTimeMillis()}",
                name = name.trim().ifBlank { "My Preset" },
                style = style
            )
            prefs[KEY_STYLE_PRESETS] = json.encodeToString(current + newPreset)
        }
    }

    suspend fun deletePreset(id: String) {
        context.sukashiDataStore.edit { prefs ->
            val current = prefs[KEY_STYLE_PRESETS]?.let {
                runCatching { json.decodeFromString<List<StylePreset>>(it) }.getOrNull()
            } ?: defaultPresets
            prefs[KEY_STYLE_PRESETS] = json.encodeToString(current.filterNot { it.id == id })
        }
    }
}
