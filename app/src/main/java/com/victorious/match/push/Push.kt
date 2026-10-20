package com.victorious.match.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.victorious.match.BuildConfig
import com.victorious.match.backend.Backend
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** FCM wrapper. Everything is a no-op when the build has no google-services.json. */
object Push {
    private var app: Context? = null
    fun init(ctx: Context) {
        app = ctx.applicationContext
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(NotificationChannel(PushLogic.CHANNEL_ID, "Matches and messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "New matches and messages. Generic text unless you enable previews." })
    }
    fun available(): Boolean = runCatching { PushLogic.isAvailable(BuildConfig.PUSH_CONFIGURED, app?.let { FirebaseApp.getApps(it).size } ?: 0) }.getOrDefault(false)

    private suspend fun token(): String? = if (!available()) null else suspendCancellableCoroutine { c ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { t -> c.resume(if (t.isSuccessful) t.result else null) }
    }

    /** Call after sign-in and whenever FCM rotates the token. */
    suspend fun register(explicit: String? = null) {
        if (Backend.me() == null) return
        val t = explicit ?: token() ?: return
        runCatching { Backend.registerToken(t) }.onFailure { Log.w("Push", "register failed", it) }
    }

    /** Call before sign-out / after account deletion: remove server row and rotate the local token. */
    suspend fun unregister() {
        if (!available()) return
        val t = token()
        if (t != null && Backend.me() != null) runCatching { Backend.deleteToken(t) }
        suspendCancellableCoroutine<Unit> { c -> FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { c.resume(Unit) } }
    }
}
