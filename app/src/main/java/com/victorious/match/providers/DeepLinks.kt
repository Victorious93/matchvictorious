package com.victorious.match.providers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens the official app, else its Play Store page. Never automates anything inside it. */
object DeepLinks {
    fun openOfficialApp(ctx: Context, info: PlatformInfo) {
        val launch = ctx.packageManager.getLaunchIntentForPackage(info.packageName)
        val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${info.packageName}"))
        try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) {
            openUrl(ctx, "https://play.google.com/store/apps/details?id=${info.packageName}")
        }
    }
    fun openUrl(ctx: Context, url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) {}
    }
}
