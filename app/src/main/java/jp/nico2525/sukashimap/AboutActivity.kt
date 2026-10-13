package jp.nico2525.sukashimap

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        findViewById<MaterialToolbar>(R.id.toolbarAbout).setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        findViewById<TextView>(R.id.tvAboutBody).text = buildString {
            append(getString(R.string.about_intro))
            append("\n\n")
            append(getString(R.string.map_data_citations))
            append("\n")
            append(getString(R.string.about_trip_map_credits))
        }
    }
}
