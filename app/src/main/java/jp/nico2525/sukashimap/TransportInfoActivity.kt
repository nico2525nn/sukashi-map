package jp.nico2525.sukashimap

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import jp.nico2525.sukashimap.ui.TransportBottomSheet
import jp.nico2525.sukashimap.ui.TransportResult

/**
 * Standalone transport editor (legacy entry point kept for compatibility).
 * Hosts [TransportBottomSheet] and returns the result as an activity result.
 */
class TransportInfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        supportFragmentManager.setFragmentResultListener(TransportResult.REQUEST_KEY, this) { _, bundle ->
            setResult(RESULT_OK, Intent().putExtras(bundle))
            finish()
        }
        TransportBottomSheet.newInstance(intent.extras).show(supportFragmentManager, "transport")
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentDestroyed(fm: androidx.fragment.app.FragmentManager, f: androidx.fragment.app.Fragment) {
                    if (f is TransportBottomSheet && !isFinishing) {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                }
            },
            false,
        )
    }
}
