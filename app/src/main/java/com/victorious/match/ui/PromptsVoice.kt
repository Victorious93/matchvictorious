package com.victorious.match.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.victorious.match.backend.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PromptsCard() {
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<List<PromptOption>>(emptyList()) }
    val picks = remember { mutableStateListOf<Pair<String?, String>>(null to "", null to "", null to "") }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        catalog = runCatching { PromptsApi.catalog() }.getOrDefault(emptyList())
        runCatching { PromptsApi.mine() }.getOrNull()?.sortedBy { it.slot }?.forEachIndexed { i, r -> if (i < 3) picks[i] = r.promptId to r.answer }
    }
    NeonCard {
        H("Prompts"); P("Pick up to 3 and answer them. People can like a specific answer with a comment.")
        for (i in 0 until PromptRules.SLOTS) {
            var open by remember { mutableStateOf(false) }
            val (pid, ans) = picks[i]
            Box { OutlinedButton(onClick = { open = true }) { Text(catalog.firstOrNull { it.id == pid }?.text ?: "Choose prompt ${i + 1}") }
                DropdownMenu(open, { open = false }) {
                    DropdownMenuItem(text = { Text("(none)") }, onClick = { open = false; picks[i] = null to "" })
                    catalog.filter { o -> picks.none { it.first == o.id } || o.id == pid }.forEach { o ->
                        DropdownMenuItem(text = { Text(o.text) }, onClick = { open = false; picks[i] = o.id to ans }) }
                } }
            if (pid != null) OutlinedTextField(ans, { picks[i] = pid to it.take(PromptRules.MAX_ANSWER) }, Modifier.fillMaxWidth(), label = { Text("Your answer") })
        }
        val errs = PromptRules.errors(picks.toList())
        errs.forEach { P("• $it", MaterialTheme.colorScheme.error) }
        Button(enabled = errs.isEmpty(), onClick = { scope.launch {
            status = runCatching { PromptsApi.save(picks.filter { it.first != null }.map { it.first!! to it.second }); "Prompts saved." }.getOrElse { it.message } } }) { Text("Save prompts") }
        status?.let { P(it, Cyan) }
    }
}

/** Plays a voice intro via a short-lived signed URL. */
@Composable
fun VoicePlayButton(path: String, label: String = "▶ Voice intro") {
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(path) { onDispose { player?.release(); player = null } }
    OutlinedButton(onClick = {
        player?.let { it.release(); player = null; return@OutlinedButton }
        scope.launch {
            val url = PromptsApi.signedVoiceUrl(path) ?: return@launch
            player = MediaPlayer().apply { setDataSource(url); setOnCompletionListener { it.release(); player = null }; setOnPreparedListener { it.start() }; prepareAsync() }
        }
    }) { Text(if (player != null) "■ Stop" else label) }
}

@Composable
fun VoiceIntroCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mine by remember { mutableStateOf<VoiceIntroRow?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var started by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    val file = remember { File(ctx.cacheDir, "voice_intro.m4a") }
    LaunchedEffect(Unit) { mine = runCatching { PromptsApi.myVoice() }.getOrNull() }
    fun stopAndUpload() {
        val r = recorder ?: return
        val secs = (SystemClock.elapsedRealtime() - started) / 1000.0
        runCatching { r.stop() }; r.release(); recorder = null
        scope.launch { status = runCatching {
            val bytes = withContext(Dispatchers.IO) { file.readBytes().also { file.delete() } }
            PromptsApi.uploadVoice(bytes, secs.coerceAtMost(PromptRules.MAX_VOICE_S)); mine = PromptsApi.myVoice(); "Uploaded. It becomes visible after moderation."
        }.getOrElse { it.message } }
    }
    fun start() {
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64000); r.setAudioSamplingRate(44100); r.setMaxDuration(30_000); r.setOutputFile(file.absolutePath)
        r.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopAndUpload() }
        r.prepare(); r.start(); recorder = r; started = SystemClock.elapsedRealtime()
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) start() else status = "Microphone permission denied." }
    LaunchedEffect(recorder) { while (recorder != null) { elapsed = ((SystemClock.elapsedRealtime() - started) / 1000).toInt(); delay(250) } }
    DisposableEffect(Unit) { onDispose { recorder?.let { runCatching { it.stop() }; it.release() } } }
    NeonCard {
        H("Voice intro"); P("Up to 30 seconds. Reviewed by moderators before others can hear it. Stored privately; played via short-lived links.")
        mine?.let { v -> P("Status: ${v.moderationStatus}${v.rejectionReason?.let { " ($it)" } ?: ""} · ${v.durationS}s", MaterialTheme.colorScheme.secondary); VoicePlayButton(v.storagePath, "▶ Play mine") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (recorder == null) Button(onClick = {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) runCatching { start() }.onFailure { status = it.message }
                else perm.launch(Manifest.permission.RECORD_AUDIO) }) { Text(if (mine == null) "Record" else "Re-record") }
            else Button(onClick = { stopAndUpload() }) { Text("Stop (${elapsed}s / 30s)") }
            if (mine != null && recorder == null) TextButton(onClick = { scope.launch { runCatching { PromptsApi.deleteVoice() }; mine = null } }) { Text("Delete") }
        }
        status?.let { P(it, Cyan) }
    }
}

/** Prompt answers on a Discover card, each with "Like with comment". */
@Composable
fun PromptAnswers(prompts: List<CandidatePrompt>, onLikeWithComment: (CandidatePrompt, String) -> Unit) {
    var target by remember { mutableStateOf<CandidatePrompt?>(null) }
    prompts.forEach { p ->
        NeonCard {
            P(p.prompt, MaterialTheme.colorScheme.secondary); H(p.answer)
            TextButton(onClick = { target = p }) { Text("♥ Like this answer") }
        }
    }
    target?.let { p ->
        var text by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { target = null }, title = { Text("Like with a comment") },
            text = { Column { P("\"${p.prompt}\": ${p.answer}"); OutlinedTextField(text, { text = it.take(PromptRules.MAX_COMMENT) }, label = { Text("Comment (optional)") }) ;
                P("Your comment is shown only if you match.", androidx.compose.ui.graphics.Color.Gray) } },
            confirmButton = { TextButton(onClick = { target = null; onLikeWithComment(p, text) }) { Text("Send like") } },
            dismissButton = { TextButton(onClick = { target = null }) { Text("Cancel") } })
    }
}

/** Suggested openers above an empty chat; tapping fills the text box (never sends automatically). */
@Composable
fun IcebreakerChips(otherId: String, onPick: (String) -> Unit) {
    var ideas by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(otherId) { ideas = Icebreakers.suggest(runCatching { PromptsApi.of(otherId, PromptsApi.catalog()) }.getOrDefault(emptyList())) }
    if (ideas.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        P("Icebreakers", MaterialTheme.colorScheme.secondary)
        ideas.forEach { s -> SuggestionChip(onClick = { onPick(s) }, label = { Text(s) }) }
    }
}
