package com.victorious.match.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.victorious.match.backend.*
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.launch

/** Global: watches for incoming calls and hosts the active call UI. */
class CallState { var active by mutableStateOf<CallRow?>(null); var incoming by mutableStateOf<CallRow?>(null); var info by mutableStateOf<String?>(null) }

@Composable
fun CallHost(state: CallState) {
    val session by Backend.auth.sessionStatus.collectAsState()
    val me = (session as? io.github.jan.supabase.auth.status.SessionStatus.Authenticated)?.session?.user?.id ?: return
    val scope = rememberCoroutineScope()
    LaunchedEffect(me) {
        runCatching { CallsApi.live().collect { c ->
            when {
                state.active?.id == c.id -> state.active = if (CallLogic.isOver(c)) null else c
                CallLogic.isIncoming(c, me) && state.active == null -> state.incoming = c
                state.incoming?.id == c.id && c.status != "ringing" -> state.incoming = null
            }
        } }
    }
    state.incoming?.let { c ->
        AlertDialog(onDismissRequest = {}, title = { Text("Incoming ${c.kind} call") },
            text = { Text(if (c.kind == "video") "Video starts blurred. Both of you must accept before it's shown." else "Audio call from your match.") },
            confirmButton = { TextButton(onClick = { state.incoming = null; scope.launch {
                runCatching { CallsApi.respond(c.id, true); state.active = CallsApi.get(c.id) }.onFailure { state.info = CallLogic.friendlyError(it.message) } } }) { Text("Accept") } },
            dismissButton = { TextButton(onClick = { state.incoming = null; scope.launch { runCatching { CallsApi.respond(c.id, false) } } }) { Text("Decline") } })
    }
    state.info?.let { AlertDialog(onDismissRequest = { state.info = null }, text = { Text(it) }, confirmButton = { TextButton(onClick = { state.info = null }) { Text("OK") } }) }
    state.active?.let { c -> CallScreen(me, c) { state.active = null } }
}

@Composable
fun CallScreen(me: String, call: CallRow, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val room = remember(call.id) { LiveKit.create(ctx.applicationContext) }
    var status by remember { mutableStateOf(if (call.status == "ringing") "Ringing…" else "Connecting…") }
    var micOn by remember { mutableStateOf(true) }
    var camOn by remember { mutableStateOf(false) }
    var remoteVideo by remember { mutableStateOf<VideoTrack?>(null) }
    var report by remember { mutableStateOf(false) }
    val renderer = remember(call.id) { TextureViewRenderer(ctx).also { room.initVideoRenderer(it) } }
    fun hangUp() { scope.launch { runCatching { CallsApi.end(call.id) } }; room.disconnect(); onClose() }
    BackHandler { hangUp() }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) scope.launch { camOn = room.localParticipant.setCameraEnabled(true) } }

    LaunchedEffect(call.id) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) micPerm.launch(Manifest.permission.RECORD_AUDIO)
        runCatching {
            val t = CallsApi.token(call.id)
            room.connect(t.url, t.token)
            room.localParticipant.setMicrophoneEnabled(true)
            status = if (call.status == "ringing") "Ringing…" else "Connected"
            room.events.collect { e ->
                when (e) {
                    is RoomEvent.TrackSubscribed -> (e.track as? VideoTrack)?.let { it.addRenderer(renderer); remoteVideo = it }
                    is RoomEvent.TrackUnsubscribed -> if (e.track == remoteVideo) remoteVideo = null
                    is RoomEvent.ParticipantDisconnected -> { status = "The other person left"; hangUp() }
                    is RoomEvent.Disconnected -> status = "Disconnected"
                    else -> {}
                }
            }
        }.onFailure { status = if (it is CallsDisabledException) "Calls aren't available yet." else CallLogic.friendlyError(it.message) }
    }
    LaunchedEffect(call.status) { if (call.status == "active" && status == "Ringing…") status = "Connected" }
    DisposableEffect(call.id) { onDispose { runCatching { remoteVideo?.removeRenderer(renderer); renderer.release(); room.disconnect(); room.release() } } }

    Surface(Modifier.fillMaxSize(), color = Bg) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            H(if (call.kind == "video") "Video call" else "Voice call"); P(status, Cyan)
            P("Calls are never recorded.", Color.Gray)
            if (call.kind == "video") Box(Modifier.fillMaxWidth().weight(1f)) {
                AndroidView({ renderer }, Modifier.fillMaxSize())
                if (!CallLogic.videoVisible(call) || remoteVideo == null) Box(Modifier.fillMaxSize().background(Color(0xF0101018)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        P(when { remoteVideo == null -> "No video yet"; CallLogic.myVideoOk(call, me) -> "Waiting for the other person to accept video…"; else -> "Video is hidden until you both accept." })
                        if (!CallLogic.myVideoOk(call, me) && call.status == "active") Button(onClick = { scope.launch { runCatching { CallsApi.setVideoOk(call.id, true) } } }) { Text("Accept video") }
                    }
                }
            } else Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                OutlinedButton(onClick = { scope.launch { micOn = !micOn; room.localParticipant.setMicrophoneEnabled(micOn) } }) { Text(if (micOn) "Mute" else "Unmute") }
                if (call.kind == "video") OutlinedButton(onClick = {
                    if (camOn) scope.launch { room.localParticipant.setCameraEnabled(false); camOn = false }
                    else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scope.launch { camOn = room.localParticipant.setCameraEnabled(true) }
                    else camPerm.launch(Manifest.permission.CAMERA) }) { Text(if (camOn) "Camera off" else "Camera on") }
                TextButton(onClick = { report = true }) { Text("Report", color = MaterialTheme.colorScheme.error) }
            }
            Button(onClick = { hangUp() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("End call") }
        }
    }
    if (report) AlertDialog(onDismissRequest = { report = false }, title = { Text("Report and end call?") },
        text = { Text("The call ends, the person is reported to moderators and blocked.") },
        confirmButton = { TextButton(onClick = { report = false; scope.launch {
            val other = runCatching { Backend.matchCards().firstOrNull { it.matchId == call.matchId }?.otherId }.getOrNull()
            if (other != null) runCatching { Backend.report(other, "harassment", "Reported during a ${call.kind} call"); Backend.block(other) }
            hangUp() } }) { Text("Report", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { report = false }) { Text("Cancel") } })
}

/** Call buttons for a chat header: handles the two-sided opt-in. */
@Composable
fun CallButtons(me: String, matchId: String, calls: CallState) {
    val scope = rememberCoroutineScope()
    var consent by remember { mutableStateOf(CallLogic.Consent.NONE) }
    var ask by remember { mutableStateOf<String?>(null) }
    suspend fun refresh() { consent = CallLogic.consent(runCatching { CallsApi.consents(matchId) }.getOrDefault(emptyList()), me) }
    LaunchedEffect(matchId) { refresh() }
    fun tap(kind: String) {
        if (consent != CallLogic.Consent.BOTH) { ask = kind; return }
        scope.launch { runCatching { calls.active = CallsApi.start(matchId, kind) }.onFailure { calls.info = CallLogic.friendlyError(it.message) } }
    }
    TextButton(onClick = { tap("audio") }) { Text("📞") }
    TextButton(onClick = { tap("video") }) { Text("🎥") }
    ask?.let {
        AlertDialog(onDismissRequest = { ask = null }, title = { Text("Calls with this match") },
            text = { Text(when (consent) {
                CallLogic.Consent.MINE_ONLY -> "You've allowed calls. Waiting for your match to allow calls too."
                CallLogic.Consent.THEIRS_ONLY -> "Your match allows calls. Allow calls with them? Audio comes first; video stays blurred until you both accept. Calls are never recorded."
                else -> "Allow calls with this match? Calls only start when you both allow them. Audio first; video stays blurred until you both accept. Calls are never recorded."
            }) },
            confirmButton = { if (consent != CallLogic.Consent.MINE_ONLY) TextButton(onClick = { ask = null; scope.launch { runCatching { CallsApi.setConsent(matchId, true) }; refresh() } }) { Text("Allow calls") }
                else TextButton(onClick = { ask = null; scope.launch { runCatching { CallsApi.setConsent(matchId, false) }; refresh() } }) { Text("Withdraw") } },
            dismissButton = { TextButton(onClick = { ask = null }) { Text("Close") } })
    }
}
