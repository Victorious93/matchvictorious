package com.victorious.match.backend

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume

const val PLUS_PRODUCT_ID = "victorious_plus"

@Serializable data class VerifyResult(val plus: Boolean = false, val status: String? = null, @SerialName("expires_at") val expiresAt: String? = null,
    val disabled: Boolean = false, val error: String? = null)
@Serializable data class Liker(val id: String, @SerialName("display_name") val displayName: String, val age: Int, val city: String? = null,
    val photo: String? = null, @SerialName("liked_at") val likedAt: String)

/** Convenience-only perks. Nothing here changes who sees you or in what order (no pay-to-rank). */
object PlusLogic {
    val perks = listOf("See who liked you", "Rewind your last pass (24h)", "Extra filters: has voice intro, has prompts")
    fun friendlyError(msg: String?): String = when {
        msg == null -> "Something went wrong"
        "Victorious+ required" in msg -> "That's a Victorious+ feature."
        "nothing to rewind" in msg -> "No recent pass to rewind."
        "503" in msg || "not configured" in msg -> "Victorious+ isn't available yet."
        "another account" in msg -> "This purchase belongs to another account."
        else -> msg.lineSequence().first().take(160)
    }
    fun isPlus(status: String?) = status in setOf("active", "grace", "canceled")
}

object PlusApi {
    private val c get() = Backend.client
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun isPlus(): Boolean = runCatching { c.postgrest.rpc("my_plus").decodeAs<Boolean>() }.getOrDefault(false)
    suspend fun likesReceived(): List<Liker> = c.postgrest.rpc("likes_received").decodeList()
    suspend fun rewind(): String = c.postgrest.rpc("rewind_last_pass").decodeAs<String>()
    /** Server verification (Play Developer API) — the only way an entitlement is granted. */
    suspend fun verify(token: String?, productId: String?): VerifyResult {
        val body = buildJsonObject { if (token != null) { put("purchase_token", token); put("product_id", productId) } }
        val r = runCatching { c.functions.invoke("play-verify", body) }.getOrElse { e ->
            return if ("503" in (e.message ?: "")) VerifyResult(disabled = true) else VerifyResult(error = e.message) }
        return runCatching { json.decodeFromString<VerifyResult>(r.bodyAsText()) }.getOrElse { VerifyResult(error = it.message) }
    }
}

/** Thin Play Billing wrapper. Works only on Play-installed builds with the product configured; otherwise reports unavailable. */
class PlayBilling(ctx: Context, private val onPurchase: (Purchase) -> Unit) : PurchasesUpdatedListener {
    private val client = BillingClient.newBuilder(ctx.applicationContext).setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build()
    var lastError: String? = null; private set

    private suspend fun ready(): Boolean = client.isReady || suspendCancellableCoroutine { k ->
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(r: BillingResult) { if (k.isActive) k.resume(r.responseCode == BillingClient.BillingResponseCode.OK) }
            override fun onBillingServiceDisconnected() { if (k.isActive) k.resume(false) }
        })
    }

    suspend fun product(): ProductDetails? {
        if (!ready()) { lastError = "Google Play Billing unavailable on this device"; return null }
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PLUS_PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build())).build()
        return suspendCancellableCoroutine { k -> client.queryProductDetailsAsync(params) { r, res ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) lastError = r.debugMessage
            if (k.isActive) k.resume(res.productDetailsList.firstOrNull()) } }
    }

    fun buy(activity: Activity, pd: ProductDetails, userId: String): Boolean {
        val offer = pd.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return false
        val p = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(pd).setOfferToken(offer).build())).setObfuscatedAccountId(userId).build()
        return client.launchBillingFlow(activity, p).responseCode == BillingClient.BillingResponseCode.OK
    }

    /** Restore: re-send any owned subscription to the server. */
    suspend fun owned(): List<Purchase> {
        if (!ready()) return emptyList()
        return suspendCancellableCoroutine { k -> client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS).build()) { _, list -> if (k.isActive) k.resume(list) } }
    }

    override fun onPurchasesUpdated(r: BillingResult, purchases: MutableList<Purchase>?) {
        if (r.responseCode != BillingClient.BillingResponseCode.OK) { lastError = r.debugMessage; return }
        purchases?.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }?.forEach(onPurchase)
    }
    fun close() = client.endConnection()
}
