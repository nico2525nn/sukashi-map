package jp.nico2525.sukashimap.util

import android.app.Activity
import android.widget.Toast
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import jp.nico2525.sukashimap.R

/** UMP consent flow wrapper. Degrades to a toast / no-op when UMP is unavailable. */
object ConsentHelper {

    /** Initial consent request. Calls [onDone] on every path. */
    fun requestConsent(activity: Activity, onDone: () -> Unit = {}) {
        val info = runCatching { UserMessagingPlatform.getConsentInformation(activity) }
            .getOrNull() ?: run {
                onDone()
                return
            }
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                if (info.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                ) {
                    showForm(activity, onDone)
                } else {
                    onDone()
                }
            },
            {
                onDone()
            },
        )
    }

    /** Re-shows the privacy-options form (Settings entry point). Falls back to a toast. */
    fun showPrivacyOptions(activity: Activity) {
        val info = runCatching { UserMessagingPlatform.getConsentInformation(activity) }
            .getOrNull()
        if (info == null) {
            toastUnavailable(activity)
            return
        }
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                if (info.privacyOptionsRequirementStatus !=
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                ) {
                    toastUnavailable(activity)
                    return@requestConsentInfoUpdate
                }
                showForm(activity, null)
            },
            {
                toastUnavailable(activity)
            },
        )
    }

    private fun showForm(activity: Activity, onDone: (() -> Unit)?) {
        runCatching {
            UserMessagingPlatform.showPrivacyOptionsForm(
                activity,
                ConsentForm.OnConsentFormDismissedListener { onDone?.invoke() },
            )
        }.onFailure {
            if (onDone != null) onDone() else toastUnavailable(activity)
        }
    }

    private fun toastUnavailable(activity: Activity) {
        Toast.makeText(activity, R.string.settings_privacy_unavailable, Toast.LENGTH_SHORT).show()
    }
}
