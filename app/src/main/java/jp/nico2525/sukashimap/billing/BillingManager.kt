package jp.nico2525.sukashimap.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import jp.nico2525.sukashimap.BuildConfig
import jp.nico2525.sukashimap.plus.PlusManager
import jp.nico2525.sukashimap.plus.PlusPlan

/**
 * BillingClient wrapper for one-time Plus passes (non-renewing).
 * Product IDs come from BuildConfig (own Play Console products required).
 */
class BillingManager(private val activity: Activity, private val plus: PlusManager) {
    interface Callback {
        fun onProducts(products: List<ProductDetails>)
        fun onProductsError()
        fun onPurchaseCompleted(productId: String)
        fun onPurchasePending()
        fun onPurchaseFailed()
        fun onRestored(productIds: Set<String>)
        fun onRestoreEmpty()
        fun onRestoreError()
    }

    var callback: Callback? = null
    val products = mutableMapOf<String, ProductDetails>()

    private fun post(fn: () -> Unit) = activity.runOnUiThread(fn)

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryPurchases(restore = true)
            BillingClient.BillingResponseCode.USER_CANCELED -> {}
            else -> post { callback?.onPurchaseFailed() }
        }
    }

    private val client = BillingClient.newBuilder(activity)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun productIds(): List<String> =
        BuildConfig.BILLING_PRODUCT_IDS.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { PlusPlan.entries.map { it.productId } }

    fun connect(onReady: () -> Unit = {}) {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                activity.runOnUiThread {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryProducts()
                        onReady()
                    } else {
                        callback?.onProductsError()
                    }
                }
            }

            override fun onBillingServiceDisconnected() {}
        })
    }

    fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productIds().map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }).build()
        client.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                products.clear()
                details.productDetailsList.forEach { products[it.productId] = it }
                val list = details.productDetailsList
                post { callback?.onProducts(list) }
            } else {
                post { callback?.onProductsError() }
            }
        }
    }

    fun launchPurchase(productId: String) {
        val details = products[productId] ?: run {
            callback?.onPurchaseFailed()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()),
            ).build()
        client.launchBillingFlow(activity, params)
    }

    fun queryPurchases(restore: Boolean) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                if (restore) post { callback?.onRestoreError() }
                return@queryPurchasesAsync
            }
            if (purchases.isEmpty()) {
                if (restore) post { callback?.onRestoreEmpty() }
                return@queryPurchasesAsync
            }
            val ids = mutableSetOf<String>()
            purchases.forEach {
                it.products.forEach { id ->
                    if (PlusPlan.byProductId(id) != null) {
                        plus.grant(id, it.purchaseTime)
                        ids.add(id)
                    }
                }
            }
            if (restore) {
                if (ids.isEmpty()) post { callback?.onRestoreEmpty() } else post { callback?.onRestored(ids) }
            } else if (ids.isNotEmpty()) {
                val first = ids.first()
                post { callback?.onPurchaseCompleted(first) }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            post { callback?.onPurchasePending() }
            return
        }
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            post { callback?.onPurchaseFailed() }
            return
        }
        purchase.products.forEach { plus.grant(it, purchase.purchaseTime) }
        if (!purchase.isAcknowledged) {
            client.acknowledgePurchase(
                com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken).build(),
            ) {}
        }
        val id = purchase.products.firstOrNull()
        if (id != null) post { callback?.onPurchaseCompleted(id) } else post { callback?.onPurchaseFailed() }
    }

    fun destroy() {
        client.endConnection()
    }
}
