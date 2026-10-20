package com.victorious.match.fleet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.victorious.match.BuildConfigLite
import com.victorious.match.data.EncryptedStore

/**
 * STATUS / PING. Protected by the signature permission in the manifest. Returns aggregate counts only;
 * never names, messages or other personal data.
 */
class FleetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(EncryptedStore.PREFS, Context.MODE_PRIVATE)
        val consent = prefs.getBoolean("consent", false)
        val bundles = if (consent) runCatching { EncryptedStore(context).loadAll() }.getOrDefault(emptyList()) else emptyList()
        val extras = android.os.Bundle().apply {
            putString("node", "matchvictorious"); putString("role", "EXTERNAL_NODE"); putString("version", BuildConfigLite.VERSION)
            putBoolean("consent", consent); putInt("imports", bundles.size)
            putInt("matches", bundles.sumOf { it.matches.size }); putInt("messages", bundles.sumOf { it.messages.size })
            putBoolean("live_sync", false); putBoolean("network", true)
        }
        if (isOrderedBroadcast) { resultCode = 1; setResultExtras(extras); resultData = "OK" }
        intent.getStringExtra(FleetApi.EXTRA_REPLY_PACKAGE)?.let { pkg ->
            context.sendBroadcast(Intent(FleetApi.ACTION_STATUS_REPLY).setPackage(pkg).putExtras(extras), FleetApi.PERMISSION)
        }
    }
}
