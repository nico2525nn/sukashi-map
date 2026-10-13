package jp.nico2525.sukashimap.billing

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.ProductDetails
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import jp.nico2525.sukashimap.R
import jp.nico2525.sukashimap.plus.PlusManager
import jp.nico2525.sukashimap.plus.PlusPlan

class PaywallActivity : AppCompatActivity(), BillingManager.Callback {

    companion object {
        const val EXTRA_SOURCE = "extra_source"
    }

    private lateinit var plus: PlusManager
    private var billing: BillingManager? = null

    private lateinit var layoutFeatures: LinearLayout
    private lateinit var layoutPlans: LinearLayout
    private lateinit var tvRemaining: TextView
    private lateinit var tvSource: TextView
    private lateinit var progressProducts: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var btnPurchase: MaterialButton
    private lateinit var btnRestore: MaterialButton
    private lateinit var btnRetry: MaterialButton

    private data class PlanRow(
        val plan: PlusPlan,
        val card: MaterialCardView,
        val priceView: TextView,
    )

    private val planRows = mutableListOf<PlanRow>()
    private var selectedPlan: PlusPlan = PlusPlan.YEAR_1

    private val planTexts = mapOf(
        PlusPlan.DAYS_2 to Pair(R.string.plan_2days_title, R.string.plan_2days_desc),
        PlusPlan.WEEK_1 to Pair(R.string.plan_1week_title, R.string.plan_1week_desc),
        PlusPlan.YEAR_1 to Pair(R.string.plan_1year_title, R.string.plan_1year_desc),
    )

    private val featureItems = listOf(
        Pair(R.string.paywall_feature_no_ads, R.drawable.ic_plus_no_ads),
        Pair(R.string.paywall_feature_free_placement, R.drawable.ic_plus_free_placement),
        Pair(R.string.paywall_feature_hide_logo, R.drawable.ic_plus_hide_logo),
        Pair(R.string.paywall_feature_crop, R.drawable.ic_crop),
        Pair(R.string.paywall_feature_trip_recap, R.drawable.ic_map),
        Pair(R.string.paywall_feature_original_quality_video, R.drawable.ic_videocam),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paywall)

        plus = PlusManager(this)

        findViewById<MaterialToolbar>(R.id.toolbarPaywall).setNavigationOnClickListener {
            finish()
        }
        layoutFeatures = findViewById(R.id.layoutFeatures)
        layoutPlans = findViewById(R.id.layoutPlans)
        tvRemaining = findViewById(R.id.tvRemaining)
        tvSource = findViewById(R.id.tvSource)
        progressProducts = findViewById(R.id.progressProducts)
        tvError = findViewById(R.id.tvError)
        btnPurchase = findViewById(R.id.btnPurchase)
        btnRestore = findViewById(R.id.btnRestore)
        btnRetry = findViewById(R.id.btnRetry)

        val source = intent.getStringExtra(EXTRA_SOURCE)
        if (!source.isNullOrEmpty()) {
            tvSource.visibility = View.VISIBLE
            tvSource.text = getString(R.string.paywall_source_format, source)
        }

        featureItems.forEach { (textRes, iconRes) ->
            val row = layoutInflater.inflate(R.layout.item_plus_feature, layoutFeatures, false)
            row.findViewById<TextView>(R.id.tvFeatureText).setText(textRes)
            row.findViewById<ImageView>(R.id.ivFeatureIcon).setImageResource(iconRes)
            layoutFeatures.addView(row)
        }

        PlusPlan.entries.forEach { plan ->
            val card = layoutInflater.inflate(
                R.layout.item_plan_card,
                layoutPlans,
                false,
            ) as MaterialCardView
            val (titleRes, descRes) = planTexts[plan]
                ?: Pair(R.string.paywall_title, R.string.plan_no_auto_renew)
            card.findViewById<TextView>(R.id.tvPlanTitle).setText(titleRes)
            card.findViewById<TextView>(R.id.tvPlanDesc).setText(descRes)
            val priceView = card.findViewById<TextView>(R.id.tvPlanPrice)
            card.setOnClickListener {
                selectedPlan = plan
                refreshPlanSelection()
            }
            layoutPlans.addView(card)
            planRows.add(PlanRow(plan, card, priceView))
        }
        refreshPlanSelection()

        btnPurchase.setOnClickListener {
            val manager = billing
            if (manager == null || !manager.products.containsKey(selectedPlan.productId)) {
                Toast.makeText(this, R.string.paywall_purchase_retry, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            manager.launchPurchase(selectedPlan.productId)
        }
        btnRestore.setOnClickListener {
            billing?.queryPurchases(restore = true)
        }
        btnRetry.setOnClickListener {
            tvError.visibility = View.GONE
            btnRetry.visibility = View.GONE
            progressProducts.visibility = View.VISIBLE
            billing?.queryProducts()
        }

        refreshRemaining()

        val manager = BillingManager(this, plus)
        manager.callback = this
        billing = manager
        progressProducts.visibility = View.VISIBLE
        manager.connect()
    }

    override fun onResume() {
        super.onResume()
        refreshRemaining()
    }

    override fun onDestroy() {
        billing?.destroy()
        billing = null
        super.onDestroy()
    }

    private fun refreshRemaining() {
        val state = plus.current()
        tvRemaining.text = if (state.active) {
            getString(R.string.paywall_remaining_format, plus.remainingText(state.expiryMs))
        } else {
            getString(R.string.paywall_no_plus)
        }
    }

    private fun refreshPlanSelection() {
        val selectedColor = resolveThemeColor(
            androidx.appcompat.R.attr.colorPrimary,
            android.R.color.holo_blue_light,
        )
        val idleColor = resolveThemeColor(
            com.google.android.material.R.attr.colorOutline,
            android.R.color.darker_gray,
        )
        planRows.forEach { row ->
            row.card.strokeColor = if (row.plan == selectedPlan) selectedColor else idleColor
            val price = billing?.products?.get(row.plan.productId)
                ?.oneTimePurchaseOfferDetails?.formattedPrice
            row.priceView.text = price ?: ""
        }
    }

    private fun resolveThemeColor(attrRes: Int, fallbackRes: Int): Int {
        val out = android.util.TypedValue()
        return if (theme.resolveAttribute(attrRes, out, true)) {
            if (out.type in android.util.TypedValue.TYPE_FIRST_COLOR_INT..android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                out.data
            } else {
                runCatching { getColor(out.resourceId) }.getOrDefault(getColor(fallbackRes))
            }
        } else {
            getColor(fallbackRes)
        }
    }

    // ---- BillingManager.Callback ----

    override fun onProducts(products: List<ProductDetails>) {
        progressProducts.visibility = View.GONE
        tvError.visibility = View.GONE
        btnRetry.visibility = View.GONE
        refreshPlanSelection()
    }

    override fun onProductsError() {
        progressProducts.visibility = View.GONE
        tvError.visibility = View.VISIBLE
        btnRetry.visibility = View.VISIBLE
    }

    override fun onPurchaseCompleted(productId: String) {
        Toast.makeText(this, R.string.paywall_purchase_thanks, Toast.LENGTH_SHORT).show()
        refreshRemaining()
        finish()
    }

    override fun onPurchasePending() {
        Toast.makeText(this, R.string.paywall_purchase_pending, Toast.LENGTH_SHORT).show()
    }

    override fun onPurchaseFailed() {
        Toast.makeText(this, R.string.paywall_purchase_failed, Toast.LENGTH_SHORT).show()
    }

    override fun onRestored(productIds: Set<String>) {
        Toast.makeText(this, R.string.paywall_restore_success, Toast.LENGTH_SHORT).show()
        refreshRemaining()
    }

    override fun onRestoreEmpty() {
        Toast.makeText(this, R.string.paywall_restore_none, Toast.LENGTH_SHORT).show()
    }

    override fun onRestoreError() {
        Toast.makeText(this, R.string.paywall_restore_error, Toast.LENGTH_SHORT).show()
    }
}
