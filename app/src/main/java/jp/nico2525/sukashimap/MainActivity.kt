package jp.nico2525.sukashimap

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import jp.nico2525.sukashimap.ui.SukashiMapApp
import jp.nico2525.sukashimap.ui.SukashiViewModel
import jp.nico2525.sukashimap.ui.theme.SukashiTheme

class MainActivity : ComponentActivity() {
    private val viewModel: SukashiViewModel by viewModels()
    private var incomingSharedUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            extractIncomingShare(intent)
        } else {
            incomingSharedUri = savedInstanceState
                .getString(KEY_INCOMING_SHARED_URI)
                ?.let(Uri::parse)
        }

        setContent {
            val prefs by viewModel.prefsState.collectAsState()
            SukashiTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor
            ) {
                SukashiMapApp(
                    viewModel = viewModel,
                    incomingSharedUri = incomingSharedUri,
                    onConsumedSharedUri = {
                        incomingSharedUri = null
                        intent?.removeExtra(Intent.EXTRA_STREAM)
                        if (intent?.action == Intent.ACTION_SEND) {
                            intent?.action = Intent.ACTION_MAIN
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIncomingShare(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        incomingSharedUri?.let { outState.putString(KEY_INCOMING_SHARED_URI, it.toString()) }
    }

    private fun extractIncomingShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (uri != null) {
                incomingSharedUri = uri
            }
        }
    }

    private companion object {
        const val KEY_INCOMING_SHARED_URI = "incoming_shared_uri"
    }
}
