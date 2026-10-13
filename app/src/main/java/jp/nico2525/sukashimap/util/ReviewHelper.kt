package jp.nico2525.sukashimap.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import jp.nico2525.sukashimap.R
import jp.nico2525.sukashimap.prefs.SettingsStore

/** Post-save review prompt. Call [recordSave] after each save, [maybePrompt] from Main. */
object ReviewHelper {
    private const val MIN_SAVES = 3

    fun recordSave(activity: Activity) {
        val store = SettingsStore(activity)
        store.saveCount = store.saveCount + 1
    }

    fun maybePrompt(activity: Activity) {
        val store = SettingsStore(activity)
        if (store.saveCount < MIN_SAVES || store.reviewPrompted) return
        MaterialAlertDialogBuilder(activity)
            .setMessage(R.string.review_prompt_message)
            .setPositiveButton(R.string.review_prompt_positive) { _, _ ->
                store.reviewPrompted = true
                openStore(activity)
            }
            .setNegativeButton(R.string.review_prompt_negative) { dialog, _ ->
                store.reviewPrompted = true
                dialog.dismiss()
            }
            .show()
    }

    private fun openStore(activity: Activity) {
        val pkg = activity.packageName
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri())
        try {
            activity.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            try {
                activity.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://play.google.com/store/apps/details?id=$pkg".toUri(),
                    ),
                )
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(
                    activity,
                    R.string.review_store_unavailable,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}
