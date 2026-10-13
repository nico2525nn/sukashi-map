package jp.nico2525.sukashimap.ads

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import jp.nico2525.sukashimap.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AdMob wrapper. All calls are safe no-ops when keys are empty or Plus is active.
 * Timed reward: hide-logo + skip-ads, 5 uses / 2h.
 */
class AdManager(private val context: Context) {
    private var initialized = false
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var onReward: (() -> Unit)? = null

    var adsRemoved: Boolean = false

    private val prefs get() = context.getSharedPreferences("sukashi_rewards", Context.MODE_PRIVATE)

    fun hasKeys(): Boolean = BuildConfig.ADMOB_APP_ID.isNotEmpty()

    fun init() {
        if (initialized || !hasKeys()) return
        initialized = true
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { MobileAds.initialize(context) {} }
        }
        loadInterstitial()
        loadRewarded()
    }

    fun bannerInto(container: ViewGroup): AdView? {
        if (!hasKeys() || adsRemoved || BuildConfig.ADMOB_BANNER.isEmpty()) return null
        val adView = AdView(context)
        adView.setAdSize(AdSize.BANNER)
        adView.adUnitId = BuildConfig.ADMOB_BANNER
        container.removeAllViews()
        container.addView(adView)
        adView.loadAd(AdRequest.Builder().build())
        return adView
    }

    fun showInterstitial(activity: Activity, after: () -> Unit = {}) {
        val ad = interstitial
        if (!hasKeys() || adsRemoved || ad == null) {
            after()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadInterstitial()
                after()
            }

            override fun onAdFailedToShowFullScreenContent(e: com.google.android.gms.ads.AdError) {
                loadInterstitial()
                after()
            }
        }
        ad.show(activity)
    }

    fun isRewardedReady(): Boolean = rewarded != null

    fun showRewarded(activity: Activity, onEarned: () -> Unit, onUnavailable: () -> Unit) {
        val ad = rewarded
        if (!hasKeys() || ad == null) {
            onUnavailable()
            return
        }
        onReward = onEarned
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewarded()
            }
        }
        ad.show(activity) { onReward?.invoke() }
    }

    private fun loadInterstitial() {
        if (!hasKeys() || BuildConfig.ADMOB_INTERSTITIAL.isEmpty()) return
        InterstitialAd.load(context, BuildConfig.ADMOB_INTERSTITIAL, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                }
            })
    }

    private fun loadRewarded() {
        if (!hasKeys() || BuildConfig.ADMOB_REWARDED.isEmpty()) return
        RewardedAd.load(context, BuildConfig.ADMOB_REWARDED, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded = ad
                }
            })
    }

    // ---- timed reward: 5 uses per 2h window ----
    fun grantTimedReward() {
        val now = System.currentTimeMillis()
        val start = prefs.getLong("timed_start", 0)
        if (now - start > 2 * 3600_000L) {
            prefs.edit().putLong("timed_start", now).putInt("timed_uses", 1).apply()
        } else {
            val uses = prefs.getInt("timed_uses", 0)
            prefs.edit().putInt("timed_uses", uses + 1).apply()
        }
    }

    fun timedRewardActive(): Boolean {
        val now = System.currentTimeMillis()
        val start = prefs.getLong("timed_start", 0)
        if (now - start > 2 * 3600_000L) return false
        return prefs.getInt("timed_uses", 0) in 1..5
    }

    fun consumeTimedUse(): Boolean {
        if (!timedRewardActive()) return false
        return true // uses counted at grant; window gates total
    }
}
