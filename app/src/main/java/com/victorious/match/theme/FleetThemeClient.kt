package com.victorious.match.theme

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.victorious.match.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.util.concurrent.Executors

/** Read-only client for VictorLaunch's fleet theme. Never writes the provider. */
object FleetThemeClient {
    sealed interface State {
        data object Default : State
        data object NotPinned : State
        data class Applied(val snapshot: FleetThemeSnapshot) : State
    }

    private val _state = MutableStateFlow<State>(State.Default)
    val state: StateFlow<State> = _state
    private val io = Executors.newSingleThreadExecutor()
    private var started = false
    private val uri: Uri = Uri.parse(FleetThemeCodec.URI)

    private val pins: Set<String> = BuildConfig.FLEET_LAUNCH_CERT_SHA256
        .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        if (started) { refresh(app); return }
        started = true
        runCatching {
            app.contentResolver.registerContentObserver(uri, false, object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = refresh(app)
            })
        }.onFailure { Log.w(TAG, "observer not registered: ${it.message}") }
        refresh(app)
    }

    fun refresh(context: Context) { val app = context.applicationContext; io.execute { _state.value = query(app) } }

    private fun query(ctx: Context): State = try {
        val pm = ctx.packageManager
        val info = pm.resolveContentProvider(FleetThemeCodec.AUTHORITY, 0)
        if (info == null || !FleetThemeCodec.isTrustedProvider(info.packageName, certs(pm, info.packageName), pins)) {
            State.Default
        } else ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use State.Default
            val row = (0 until c.columnCount).associate { i -> c.getColumnName(i) to runCatching { c.getString(i) }.getOrNull() }
            FleetThemeCodec.parseRow(row)?.let { State.Applied(it) } ?: State.Default
        } ?: State.Default
    } catch (e: SecurityException) {
        Log.i(TAG, "not pinned by VictorLaunch; using default theme"); State.NotPinned
    } catch (e: Exception) {
        Log.w(TAG, "fleet theme unavailable: ${e.message}"); State.Default
    }

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun certs(pm: PackageManager, pkg: String): List<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val si = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return emptyList()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        return sigs.orEmpty().map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }
    }

    private const val TAG = "FleetTheme"
}

/** Wake-up only: extras are never trusted; we re-query the provider. */
class FleetThemeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == FleetThemeCodec.ACTION_CHANGED) FleetThemeClient.refresh(context)
    }
}
