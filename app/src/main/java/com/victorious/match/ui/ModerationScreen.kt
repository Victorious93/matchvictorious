package com.victorious.match.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.victorious.match.backend.*
import kotlinx.coroutines.launch

/** Ask for a reason, then run [onSubmit]. */
@Composable
private fun ReasonDialog(title: String, required: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val e = ModerationLogic.reasonError(required, text)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column { OutlinedTextField(text, { text = it }, label = { Text(if (required) "Reason (shown to the user)" else "Note (optional)") }, minLines = 2)
            e?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton(enabled = e == null, onClick = { onSubmit(text.trim()) }) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun ModerationScreen() = SignedIn { _ ->
    var admin by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { admin = ModApi.isAdmin() }
    when (admin) {
        null -> Box(Modifier.fillMaxSize()) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        false -> Empty("Moderators only", "Your account is not a moderator.")
        true -> {
            var tab by remember { mutableIntStateOf(0) }
            Column {
                ScrollableTabRow(tab) { listOf("Profiles", "Selfies", "Photos", "Voice", "Reports", "Events").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } }
                when (tab) { 0 -> ProfilesQueue(); 1 -> SelfieQueue(); 2 -> PhotosQueue(); 3 -> VoiceQueue(); 4 -> ReportsQueue(); else -> CreateEventCard() }
            }
        }
    }
}

@Composable
private fun <T> Queue(load: suspend () -> List<T>, empty: String, item: @Composable (T, reload: () -> Unit, report: (Result<Unit>) -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<T>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    fun reload() { scope.launch { runCatching { load() }.onSuccess { rows = it }.onFailure { msg = it.message; rows = emptyList() } } }
    LaunchedEffect(Unit) { reload() }
    val r = rows ?: return Box(Modifier.fillMaxSize()) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        msg?.let { m -> item { P(m, MaterialTheme.colorScheme.error) } }
        if (r.isEmpty()) item { P(empty) }
        items(r) { t -> item(t, { reload() }) { res -> msg = res.exceptionOrNull()?.message; reload() } }
    }
}

@Composable
private fun ProfilesQueue() {
    val scope = rememberCoroutineScope()
    Queue({ ModApi.pendingProfiles() }, "No profiles waiting for verification.") { p, _, done ->
        var reject by remember { mutableStateOf(false) }
        NeonCard {
            PhotoCarousel(p.photos, p.displayName)
            H("${p.displayName}, ${p.age}"); listOfNotNull(p.gender, p.city, p.bio, "Warnings: ${p.warnings}").forEach { P(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { done(runCatching { ModApi.setVerification(p.id, true, null) }) } }) { Text("Verify") }
                OutlinedButton(onClick = { reject = true }) { Text("Reject") }
            }
        }
        if (reject) ReasonDialog("Reject verification", true, { reject = false }) { r -> reject = false; scope.launch { done(runCatching { ModApi.setVerification(p.id, false, r) }) } }
    }
}

@Composable
private fun PhotosQueue() {
    val scope = rememberCoroutineScope()
    Queue({ ModApi.pendingPhotos() }, "No photos waiting for review.") { ph, _, done ->
        var reject by remember { mutableStateOf(false) }
        NeonCard {
            PhotoCarousel(listOf(ph.storagePath), ph.displayName)
            H(ph.displayName); P("Uploaded ${ph.createdAt?.take(16) ?: "-"}", MaterialTheme.colorScheme.secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { done(runCatching { ModApi.reviewPhoto(ph.photoId, true, null) }) } }) { Text("Approve") }
                OutlinedButton(onClick = { reject = true }) { Text("Reject") }
            }
        }
        if (reject) ReasonDialog("Reject photo", true, { reject = false }) { r -> reject = false; scope.launch { done(runCatching { ModApi.reviewPhoto(ph.photoId, false, r) }) } }
    }
}

@Composable
private fun VoiceQueue() {
    val scope = rememberCoroutineScope()
    Queue({ ModApi.pendingVoice() }, "No voice intros waiting for review.") { v, _, done ->
        var reject by remember { mutableStateOf(false) }
        NeonCard {
            H(v.displayName); P("${v.durationS}s · ${v.createdAt?.take(16) ?: ""}", MaterialTheme.colorScheme.secondary)
            VoicePlayButton(v.storagePath, "▶ Listen")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { done(runCatching { ModApi.reviewVoice(v.userId, true, null) }) } }) { Text("Approve") }
                OutlinedButton(onClick = { reject = true }) { Text("Reject") }
            }
        }
        if (reject) ReasonDialog("Reject voice intro", true, { reject = false }) { r -> reject = false; scope.launch { done(runCatching { ModApi.reviewVoice(v.userId, false, r) }) } }
    }
}

@Composable
private fun ReportsQueue() {
    val scope = rememberCoroutineScope()
    Queue({ ModApi.reports() }, "No open reports.") { r, _, done ->
        var action by remember { mutableStateOf<String?>(null) }
        NeonCard {
            H("${r.reason.uppercase()} · ${r.displayTarget}")
            P("Status: ${r.status} · ${r.createdAt?.take(16) ?: ""}", MaterialTheme.colorScheme.secondary)
            r.details?.let { P(it) }
            r.snapshot?.let { P("Snapshot at report time: $it", androidx.compose.ui.graphics.Color.Gray) }
            if (r.reportedId != null) P("Account: ${if (r.reportedBanned == true) "BANNED" else "active"} · warnings ${r.reportedWarnings ?: 0}")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { action = "dismiss" }) { Text("Dismiss") }
                TextButton(enabled = ModerationLogic.canAct(r, "warn"), onClick = { action = "warn" }) { Text("Warn") }
                TextButton(enabled = ModerationLogic.canAct(r, "ban"), onClick = { action = "ban" }) { Text("Ban", color = MaterialTheme.colorScheme.error) }
                if (r.reportedBanned == true) TextButton(onClick = { action = "unban" }) { Text("Unban") }
            }
        }
        action?.let { a ->
            ReasonDialog(when (a) { "dismiss" -> "Dismiss report"; "warn" -> "Warn user"; "ban" -> "Ban user"; else -> "Unban user" }, a == "ban" || a == "warn", { action = null }) { note ->
                action = null
                scope.launch { done(runCatching {
                    if (a == "unban") ModApi.setBan(r.reportedId!!, false, note.ifBlank { null }) else ModApi.resolve(r.id, a, note.ifBlank { null })
                }) }
            }
        }
    }
}

/** In-app status notice for the signed-in user (verification result, warnings, suspension). */
@Composable
fun ModerationStatusCard() {
    var s by remember { mutableStateOf<ModStatus?>(null) }
    LaunchedEffect(Unit) { s = ModApi.myStatus() }
    val st = s ?: return
    if (st.note == null && !st.isBanned) return
    NeonCard {
        H(if (st.isBanned) "Account suspended" else "Message from moderators")
        st.note?.let { P(it, if (st.isBanned || st.verificationStatus == "rejected") MaterialTheme.colorScheme.error else Cyan) }
        if (st.warnings > 0) P("Warnings: ${st.warnings}", androidx.compose.ui.graphics.Color.Gray)
    }
}

/** Liveness fallback queue: compare the live selfie with profile photos. The selfie is deleted right after the decision. */
@Composable
private fun SelfieQueue() {
    val scope = rememberCoroutineScope()
    var purged by remember { mutableStateOf<String?>(null) }
    Column {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = { scope.launch { purged = runCatching { "Purged ${LivenessApi.purgeExpired()} selfie(s)." }.getOrElse { it.message } } }) { Text("Purge expired selfies") }
            purged?.let { P(it) }
        }
        Queue({ LivenessApi.pending() }, "No selfies waiting for review.") { c, _, done ->
            var reject by remember { mutableStateOf(false) }
            var selfieUrl by remember(c.checkId) { mutableStateOf<String?>(null) }
            LaunchedEffect(c.checkId) { selfieUrl = c.selfiePath?.let { LivenessApi.signedSelfieUrl(it) } }
            NeonCard {
                H(c.displayName); P("Challenge: " + c.challenge.joinToString { LivenessLogic.label(it) })
                P("On-device results: " + LivenessLogic.summarize(c.result))
                P("Live selfie", Cyan)
                selfieUrl?.let { coil3.compose.AsyncImage(it, "Live selfie", Modifier.fillMaxWidth().heightIn(max = 320.dp)) } ?: P("(selfie unavailable)")
                P("Profile photos", Cyan); PhotoCarousel(c.photos, c.displayName)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { done(runCatching { LivenessApi.review(c.checkId, true, null) }) } }) { Text("Same person — verify") }
                    OutlinedButton(onClick = { reject = true }) { Text("Reject") }
                }
            }
            if (reject) ReasonDialog("Reject selfie verification", true, { reject = false }) { r -> reject = false; scope.launch { done(runCatching { LivenessApi.review(c.checkId, false, r) }) } }
        }
    }
}
