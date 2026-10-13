package jp.nico2525.sukashimap

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class PrivacyPolicyActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)
        findViewById<MaterialToolbar>(R.id.toolbarPrivacy).setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        val web = findViewById<WebView>(R.id.webPrivacy)
        web.settings.javaScriptEnabled = false
        web.loadUrl("file:///android_asset/privacy_policy.html")
    }
}
