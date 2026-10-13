package jp.nico2525.sukashimap

import android.app.Application
import jp.nico2525.sukashimap.prefs.SettingsStore

class SukashiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore(this).applySavedLocale()
    }
}
