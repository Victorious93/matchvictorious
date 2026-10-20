package com.victorious.match.safety

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.victorious.match.MainActivity

/** Local check-in reminder. Nothing is sent to anyone automatically. */
object Checkin {
    const val CHANNEL = "safety_checkin"
    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 7001, Intent(ctx, CheckinReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    fun schedule(ctx: Context, atMillis: Long) {
        ctx.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi(ctx))
        ctx.getSharedPreferences("safety", 0).edit().putLong("checkin_at", atMillis).apply()
    }
    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pi(ctx))
        ctx.getSharedPreferences("safety", 0).edit().remove("checkin_at").apply()
        NotificationManagerCompat.from(ctx).cancel(7001)
    }
    fun scheduledAt(ctx: Context): Long? = ctx.getSharedPreferences("safety", 0).getLong("checkin_at", 0L).takeIf { it > 0 }
}

class CheckinReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(Checkin.CHANNEL, "Safety check-ins", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(ctx, 7002, Intent(ctx, MainActivity::class.java).putExtra("open_safety", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, Checkin.CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Time to check in").setContentText("Are you OK? Tap to check in, or use SOS.")
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(open).build()
        runCatching { NotificationManagerCompat.from(ctx).notify(7001, n) }
        ctx.getSharedPreferences("safety", 0).edit().remove("checkin_at").apply()
    }
}
