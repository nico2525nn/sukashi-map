package jp.nico2525.sukashimap

import android.app.Application
import com.google.android.material.color.DynamicColors
import jp.nico2525.sukashimap.prefs.SettingsStore

class SukashiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore(this).applySavedLocale()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
