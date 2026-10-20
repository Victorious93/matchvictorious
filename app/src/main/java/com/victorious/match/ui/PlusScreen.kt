package com.victorious.match.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.ProductDetails
import com.victorious.match.backend.*
import kotlinx.coroutines.launch

private tailrec fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }

@Composable
fun PlusScreen() = SignedIn { me ->
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var plus by remember { mutableStateOf<Boolean?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var product by remember { mutableStateOf<ProductDetails?>(null) }
    var likers by remember { mutableStateOf<List<Liker>?>(null) }
    fun handle(r: VerifyResult) { when {
        r.disabled -> status = "Victorious+ isn't available yet."
        r.error != null -> status = PlusLogic.friendlyError(r.error)
        else -> { plus = r.plus; status = if (r.plus) "Victorious+ is active." else "No active subscription found." } } }
    val billing = remember { PlayBilling(ctx) { p -> scope.launch { handle(PlusApi.verify(p.purchaseToken, p.products.firstOrNull())) } } }
    DisposableEffect(Unit) { onDispose { billing.close() } }
    LaunchedEffect(Unit) { plus = PlusApi.isPlus(); product = runCatching { billing.product() }.getOrNull() }
    LaunchedEffect(plus) { if (plus == true) likers = runCatching { PlusApi.likesReceived() }.getOrDefault(emptyList()) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NeonCard {
            H("Victorious+")
            PlusLogic.perks.forEach { P("• $it") }
            P("Convenience only — Victorious+ never boosts you or changes who sees whom.", MaterialTheme.colorScheme.secondary)
            when {
                plus == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                plus == true -> P("You're a Victorious+ member. Manage or cancel in Google Play → Subscriptions.", Cyan)
                product != null -> {
                    val price = product!!.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
                    Button(onClick = { ctx.activity()?.let { if (!billing.buy(it, product!!, me)) status = "Couldn't start purchase." } }) {
                        Text("Subscribe" + (price?.let { " · $it" } ?: "")) }
                }
                else -> P("Victorious+ isn't available yet on this build.", androidx.compose.ui.graphics.Color.Gray)
            }
            TextButton(onClick = { scope.launch {
                val owned = runCatching { billing.owned() }.getOrDefault(emptyList())
                handle(owned.firstOrNull()?.let { PlusApi.verify(it.purchaseToken, it.products.firstOrNull()) } ?: PlusApi.verify(null, null))
            } }) { Text("Restore purchase") }
            status?.let { P(it, Cyan) }
        }
        if (plus == true) NeonCard {
            H("Who liked you")
            val l = likers
            if (l == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else if (l.isEmpty()) P("No new likes yet.")
            else l.forEach { P("${it.displayName}, ${it.age}" + (it.city?.let { c -> " · $c" } ?: "")) }
            P("Like them back in Discover to match.", androidx.compose.ui.graphics.Color.Gray)
        }
        if (plus == true) PreferencesCard(plus = true)
    }
}
