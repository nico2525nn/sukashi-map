package jp.nico2525.sukashimap

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import jp.nico2525.sukashimap.prefs.SettingsStore
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var store: SettingsStore
    private lateinit var tvLanguageValue: TextView
    private lateinit var switchAutoGeotag: SwitchMaterial
    private lateinit var switchPreserveHdr: SwitchMaterial
    private lateinit var tvVersionValue: TextView

    private val languageTags =
        listOf("", "en", "ja", "ko", "zh-Hans", "zh-Hant", "fr", "de", "it", "es-ES", "pt-PT")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        store = SettingsStore(this)

        findViewById<MaterialToolbar>(R.id.toolbarSettings).setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        tvLanguageValue = findViewById(R.id.tvLanguageValue)
        switchAutoGeotag = findViewById(R.id.switchAutoGeotag)
        switchPreserveHdr = findViewById(R.id.switchPreserveHdr)
        tvVersionValue = findViewById(R.id.tvVersionValue)

        switchAutoGeotag.isChecked = store.autoGeotagging
        switchAutoGeotag.setOnCheckedChangeListener { _, checked ->
            store.autoGeotagging = checked
        }
        switchPreserveHdr.isChecked = store.preserveHdr
        switchPreserveHdr.setOnCheckedChangeListener { _, checked ->
            store.preserveHdr = checked
        }

        val version = BuildConfig.VERSION_NAME
        tvVersionValue.text = version
        tvVersionValue.contentDescription = getString(R.string.settings_app_version_accessibility, version)

        findViewById<android.view.View>(R.id.rowLanguage).setOnClickListener { showLanguageDialog() }
        findViewById<android.view.View>(R.id.rowAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        findViewById<android.view.View>(R.id.rowPrivacyPolicy).setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        tvLanguageValue.text = languageDisplayName(store.languageTag)
        switchAutoGeotag.isChecked = store.autoGeotagging
        switchPreserveHdr.isChecked = store.preserveHdr
    }

    private fun languageDisplayName(tag: String): String {
        if (tag.isEmpty()) return getString(R.string.settings_language_system)
        return runCatching {
            val locale = Locale.forLanguageTag(tag)
            locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
        }.getOrDefault(tag)
    }

    private fun showLanguageDialog() {
        val names = languageTags.map { tag ->
            if (tag.isEmpty()) getString(R.string.settings_language_system)
            else languageDisplayName(tag)
        }.toTypedArray()
        val checked = languageTags.indexOf(store.languageTag).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_language_dialog_title)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                store.languageTag = languageTags[which]
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
