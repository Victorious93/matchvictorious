package com.victorious.match.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.victorious.match.backend.*
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared state: a draft pre-filled from an import waiting to be reviewed on the profile screen. */
class PlatformState { val calls = CallState(); var pendingDraft by mutableStateOf<ProfileDraft?>(null); var openChat by mutableStateOf<MatchCard?>(null) }

private fun err(t: Throwable) = t.message?.lineSequence()?.firstOrNull()?.take(200) ?: "Something went wrong"

/** Shows [content] only when signed in; otherwise the email sign-in/sign-up form. No phone number required. */
@Composable
fun SignedIn(content: @Composable (String) -> Unit) {
    val status by Backend.auth.sessionStatus.collectAsState()
    when (val s = status) {
        is SessionStatus.Authenticated -> content(s.session.user?.id ?: return)
        is SessionStatus.Initializing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> AuthScreen()
    }
}

@Composable
fun AuthScreen() {
    var email by remember { mutableStateOf("") }; var pw by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun run(signUp: Boolean) = scope.launch {
        busy = true; msg = null
        runCatching { if (signUp) Backend.signUp(email.trim(), pw) else Backend.signIn(email.trim(), pw) }
            .onSuccess { if (signUp && Backend.me() == null) msg = "Check your email to confirm your account, then sign in." }
            .onFailure { msg = err(it) }
        busy = false
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NeonCard {
            H("Join MatchVictorious")
            P("Email only. No phone number, ever. You must be 18+. Your imports stay on this device; only the profile you save is uploaded.")
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pw, { pw = it }, label = { Text("Password (8+ chars)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            val ok = !busy && email.contains('@') && pw.length >= 8
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { run(false) }, enabled = ok) { Text("Sign in") }
                OutlinedButton(onClick = { run(true) }, enabled = ok) { Text("Create account") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            msg?.let { P(it, Cyan) }
        }
    }
}

@Composable
fun MyProfileScreen(state: PlatformState) = SignedIn { _ ->
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(ProfileDraft()) }
    var status by remember { mutableStateOf<String?>(null) }
    var verification by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.pendingDraft) {
        val pending = state.pendingDraft
        if (pending != null) { draft = pending; state.pendingDraft = null; status = "Pre-filled from your ${pending.importedFrom} import. Review, then Save." }
        else runCatching { Backend.myProfile() }.onSuccess { r -> r?.let { draft = ProfileDraft.fromRow(it); verification = it.verificationStatus } }.onFailure { status = err(it) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            status = runCatching {
                val bytes = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
                require(bytes.size <= 5 * 1024 * 1024) { "Photo must be 5 MB or smaller" }
                val ext = when (ctx.contentResolver.getType(uri)) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
                Backend.uploadPhoto(bytes, ext, 0); "Photo uploaded. It becomes visible after moderation."
            }.getOrElse { err(it) }
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ModerationStatusCard()
        NeonCard {
            H("My profile")
            P("Verification: ${verification ?: "unverified"}. Only verified profiles appear in discovery.", MaterialTheme.colorScheme.secondary)
            OutlinedTextField(draft.displayName, { draft = draft.copy(displayName = it) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.birthdate, { draft = draft.copy(birthdate = it) }, label = { Text("Birthdate (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.gender, { draft = draft.copy(gender = it) }, label = { Text("Gender (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.bio, { draft = draft.copy(bio = it.take(ProfileDraft.MAX_BIO)) }, label = { Text("Bio") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            val errors = draft.errors()
            errors.forEach { P("• $it", MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = errors.isEmpty(), onClick = { scope.launch { status = runCatching { Backend.saveProfile(draft); "Saved." }.getOrElse { err(it) } } }) { Text("Save") }
                OutlinedButton(onClick = { picker.launch("image/*") }) { Text("Add photo") }
            }
            status?.let { P(it, Cyan) }
        }
        PromptsCard()
        VoiceIntroCard()
        LocationCard()
        PreferencesCard()
        NeonCard {
            H("Account")
            TextButton(onClick = { scope.launch { runCatching { Backend.signOut() } } }) { Text("Sign out") }
        }
    }
}

@Composable
fun DiscoverScreen() = SignedIn { _ ->
    val scope = rememberCoroutineScope()
    var queue by remember { mutableStateOf<List<Candidate>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    fun load() = scope.launch { runCatching { Backend.discover() }.onSuccess { queue = it }.onFailure { msg = err(it); queue = emptyList() } }
    LaunchedEffect(Unit) { load() }
    val q = queue
    when {
        q == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        q.isEmpty() -> Empty("No one new right now", msg ?: "Save your profile and check back later. Only verified members appear here.", "Refresh") { load() }
        else -> {
            val c = q.first()
            fun act(like: Boolean, comment: String? = null, promptId: String? = null) = scope.launch {
                runCatching { Backend.swipe(c.id, like, comment, promptId) }.onFailure { msg = err(it) }
                queue = q.drop(1); if (queue!!.isEmpty()) load()
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonCard {
                    PhotoCarousel(c.photos, c.displayName)
                    Spacer(Modifier.height(8.dp))
                    H("${c.displayName}, ${c.age}")
                    LocationPrivacy.distanceLabel(c.distanceKm, c.city)?.let { P(it, MaterialTheme.colorScheme.secondary) }
                    c.bio?.let { P(it) }
                    c.voicePath?.let { VoicePlayButton(it) }
                }
                PromptAnswers(c.prompts) { p, comment -> act(true, comment, p.promptId) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    OutlinedButton(onClick = { act(false) }) { Text("Pass") }
                    Button(onClick = { act(true) }) { Text("Like") }
                }
                msg?.let { P(it, MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
fun ChatsScreen(state: PlatformState) = SignedIn { me ->
    state.openChat?.let { m -> return@SignedIn ChatScreen(me, m, state.calls) { state.openChat = null } }
    var cards by remember { mutableStateOf<List<MatchCard>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { Backend.matchCards() }.onSuccess { cards = it }.onFailure { msg = err(it); cards = emptyList() } }
    val c = cards ?: return@SignedIn Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    if (c.isEmpty()) return@SignedIn Empty("No matches yet", msg ?: "When you and someone both tap Like, you match here.")
    val (fresh, chats) = MatchCard.split(c)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (fresh.isNotEmpty()) {
            item { H("New matches") }
            item { androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(fresh, key = { it.matchId }) { m ->
                    Column(Modifier.clickable { state.openChat = m }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(m.photoPath, m.initial, 64.dp); P(m.otherName.take(12))
                    }
                }
            } }
        }
        if (chats.isNotEmpty()) item { H("Chats") }
        items(chats, key = { it.matchId }) { m ->
            NeonCard(Modifier.clickable { state.openChat = m }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(m.photoPath, m.initial); Spacer(Modifier.width(12.dp))
                    Column { H(m.otherName); P("Last message ${m.lastMessageAt?.take(10) ?: "-"} · expires ${m.expiresAt?.take(10) ?: "-"}", MaterialTheme.colorScheme.secondary) }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(me: String, card: MatchCard, calls: CallState = CallState(), onBack: () -> Unit) {
    val match = MatchRow(card.matchId, me, card.otherId)
    val scope = rememberCoroutineScope()
    var msgs by remember { mutableStateOf<List<MessageRow>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var shareDate by remember { mutableStateOf(false) }
    val other = card.otherId
    androidx.activity.compose.BackHandler(onBack = onBack)
    LaunchedEffect(match.id) {
        runCatching { msgs = Backend.messages(match.id) }.onFailure { info = err(it) }
        runCatching { Backend.liveMessages(match.id).collect { m -> msgs = Chat.merge(msgs, m) } }.onFailure { info = "Live updates unavailable: ${err(it)}" }
    }
    DisposableEffect(match.id) { onDispose { scope.launch { runCatching { Backend.leave(match.id) } } } }
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }
    if (shareDate) AlertDialog(onDismissRequest = { shareDate = false }, confirmButton = { TextButton(onClick = { shareDate = false }) { Text("Close") } },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { ShareDateCard(card) } })
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Avatar(card.photoPath, card.initial, 40.dp); Spacer(Modifier.width(8.dp)); H(card.otherName)
            Spacer(Modifier.weight(1f))
            CallButtons(me, card.matchId, calls)
            Box { TextButton(onClick = { menu = true }) { Text("Safety") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Share my date") }, onClick = { menu = false; shareDate = true })
                    DropdownMenuItem(text = { Text("Unmatch") }, onClick = { menu = false; scope.launch { runCatching { Backend.unmatch(match.id) }; onBack() } })
                    DropdownMenuItem(text = { Text("Block") }, onClick = { menu = false; scope.launch { runCatching { Backend.block(other) }; onBack() } })
                    DropdownMenuItem(text = { Text("Report & block") }, onClick = { menu = false; scope.launch {
                        runCatching { Backend.report(other, "other", "Reported from chat"); Backend.block(other) }; onBack() } })
                }
            }
        }
        if (msgs.isEmpty()) IcebreakerChips(other) { text = it }
        LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(msgs, key = { it.id ?: it.hashCode().toLong() }) { m ->
                val mine = m.senderId == me
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Surface(color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Text(m.body, Modifier.padding(10.dp))
                    }
                }
            }
        }
        info?.let { P(it, MaterialTheme.colorScheme.error) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it.take(Chat.MAX_BODY) }, Modifier.weight(1f), placeholder = { Text("Message") })
            Button(enabled = Chat.clean(text) != null, onClick = { val t = text; text = ""; scope.launch {
                runCatching { Backend.send(match.id, t) }.onFailure { info = err(it); text = t } } }) { Text("Send") }
        }
    }
}
