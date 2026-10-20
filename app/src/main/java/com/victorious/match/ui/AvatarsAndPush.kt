package com.victorious.match.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.victorious.match.backend.Backend
import com.victorious.match.push.Push
import com.victorious.match.push.PushLogic
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

/** Circular avatar: first approved photo via a short-lived signed URL, else the initial. */
@Composable
fun Avatar(photoPath: String?, initial: String, size: Dp = 48.dp) {
    var url by remember(photoPath) { mutableStateOf<String?>(null) }
    LaunchedEffect(photoPath) { url = photoPath?.let { Backend.signedUrls(listOf(it)).firstOrNull() } }
    Box(Modifier.size(size).clip(CircleShape).background(Surface).neonRing(circle = true), contentAlignment = Alignment.Center) {
        Text(initial, color = Magenta, fontSize = (size.value * 0.42f).sp)
        url?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape)) }
    }
}

/** After sign-in: explain + request POST_NOTIFICATIONS (Android 13+), then register the FCM token. No-op without Firebase. */
@Composable
fun PushSetup() {
    if (!Push.available()) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by Backend.auth.sessionStatus.collectAsState()
    val signedIn = status is SessionStatus.Authenticated
    val prefs = remember { ctx.getSharedPreferences("push", 0) }
    var explain by remember { mutableStateOf(false) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { scope.launch { Push.register() } }
    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        val granted = !PushLogic.needsRuntimePermission(Build.VERSION.SDK_INT) ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) Push.register() else if (!prefs.getBoolean("asked", false)) explain = true else Push.register()
    }
    if (explain) AlertDialog(onDismissRequest = { explain = false; prefs.edit().putBoolean("asked", true).apply() },
        title = { Text("Get notified about matches and messages?") },
        text = { Text("Notifications say only \"You have a new match\" or \"New message\", with no names or message text, unless you turn on previews in Settings. You can turn them off any time in Android settings.") },
        confirmButton = { TextButton(onClick = { explain = false; prefs.edit().putBoolean("asked", true).apply(); perm.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow") } },
        dismissButton = { TextButton(onClick = { explain = false; prefs.edit().putBoolean("asked", true).apply() }) { Text("Not now") } })
}

@Composable
fun NotificationsCard() {
    val scope = rememberCoroutineScope()
    var previews by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val signedIn = Backend.me() != null
    LaunchedEffect(signedIn) { if (signedIn) previews = runCatching { Backend.showPreviews() }.getOrDefault(false) }
    NeonCard {
        H("Notifications")
        P(if (Push.available()) "Push notifications are enabled in this build." else "Push notifications are not configured in this build yet.", androidx.compose.ui.graphics.Color.Gray)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { P("Show previews"); P("Include names and message text in notifications. Off by default.", androidx.compose.ui.graphics.Color.Gray) }
            Switch(checked = previews, enabled = signedIn, onCheckedChange = { v -> previews = v; scope.launch {
                status = runCatching { Backend.setShowPreviews(v); null }.getOrElse { previews = !v; it.message } } })
        }
        status?.let { P(it, MaterialTheme.colorScheme.error) }
    }
}
