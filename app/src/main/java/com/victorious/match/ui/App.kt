package com.victorious.match.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.match.data.EncryptedStore
import com.victorious.match.data.ImportBundle
import com.victorious.match.providers.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val MAX_IMPORT = 200L * 1024 * 1024

class Filters { var platforms by mutableStateOf(setOf<String>()); var query by mutableStateOf(""); var onlyWithMessages by mutableStateOf(false)
    fun platformOk(id: String) = platforms.isEmpty() || id in platforms }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(requested: MutableState<Screen?>) = MatchTheme {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences(EncryptedStore.PREFS, Context.MODE_PRIVATE) }
    var consent by remember { mutableStateOf(prefs.getBoolean("consent", false)) }
    if (!consent) { ConsentScreen { prefs.edit().putBoolean("consent", true).apply(); consent = true }; return@MatchTheme }

    val store = remember { EncryptedStore(ctx) }
    var bundles by remember { mutableStateOf<List<ImportBundle>>(emptyList()) }
    val scope = rememberCoroutineScope()
    fun reload() = scope.launch { bundles = withContext(Dispatchers.IO) { store.loadAll() } }
    LaunchedEffect(Unit) { reload() }
    val filters = remember { Filters() }
    var screen by remember { mutableStateOf(Screen.Discover) }
    val platform = remember { PlatformState() }
    LaunchedEffect(requested.value) { requested.value?.let { screen = it; requested.value = null } }
    val underMore = listOf(Screen.Safety, Screen.Moderation, Screen.Filters, Screen.Guide, Screen.Help, Screen.Settings, Screen.Profiles, Screen.Matches, Screen.Messages)
    BackHandler(enabled = screen != Screen.Discover && platform.openChat == null) { screen = if (screen in underMore) Screen.More else Screen.Discover }

    Scaffold(
        containerColor = Bg,
        topBar = { TopAppBar(title = { Text(if (screen == Screen.Discover) "MatchVictorious" else screen.title, fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, titleContentColor = Cyan)) },
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                listOf(Screen.Discover to "◉", Screen.Chats to "♥", Screen.MyProfile to "☺", Screen.Import to "⇩", Screen.More to "☰").forEach { (s, g) ->
                    val sel = screen == s || (s == Screen.More && screen in underMore)
                    NavigationBarItem(selected = sel, onClick = { screen = s }, icon = { Text(g, fontSize = 20.sp) }, label = { Text(s.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Magenta, selectedTextColor = Cyan, indicatorColor = Color2))
                }
            }
        },
    ) { pad ->
        PushSetup()
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (screen) {
                Screen.Discover -> DiscoverScreen()
                Screen.Chats -> ChatsScreen(platform)
                Screen.MyProfile -> MyProfileScreen(platform)
                Screen.Profiles -> ProfilesScreen(bundles, filters, onUse = { b -> b.profile?.let { platform.pendingDraft = com.victorious.match.backend.ProfileDraft.fromImport(it, b.platformId); screen = Screen.MyProfile } }) { screen = Screen.Import }
                Screen.Matches -> MatchesScreen(bundles, filters)
                Screen.Messages -> MessagesScreen(bundles, filters)
                Screen.Filters -> FiltersScreen(filters)
                Screen.Import -> ImportScreen(store, bundles, { reload() }) { screen = Screen.Guide }
                Screen.Guide -> GuideScreen()
                Screen.Help -> HelpScreen()
                Screen.Moderation -> ModerationScreen()
                Screen.Safety -> SafetyScreen()
                Screen.Settings -> SettingsScreen(store) { store.deleteAll(); bundles = emptyList(); consent = false }
                Screen.More -> MoreScreen { screen = it }
            }
        }
    }
}

private val Color2 = androidx.compose.ui.graphics.Color(0x3300F0FF)

@Composable
fun NeonCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().neonRing().background(Surface, RoundedCornerShape(16.dp)).padding(16.dp), content = content)

@Composable fun H(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, color = Cyan, fontWeight = FontWeight.Bold)
@Composable fun P(t: String, c: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) = Text(t, style = MaterialTheme.typography.bodyMedium, color = c)

@Composable
fun ConsentScreen(onAccept: () -> Unit) = Column(
    Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Spacer(Modifier.height(24.dp))
    Box(Modifier.size(120.dp).neonRing(circle = true), contentAlignment = Alignment.Center) { Text("MV", fontSize = 40.sp, color = Magenta, fontWeight = FontWeight.Black) }
    Text("MatchVictorious", fontSize = 28.sp, color = Cyan, fontWeight = FontWeight.Bold)
    NeonCard {
        H("Before you start")
        P("• Imports YOUR data exports that you requested from dating platforms.\n" +
          "• Imports are parsed and stored on this device, encrypted, and are never uploaded.\n" +
          "• If you create a MatchVictorious account, only the profile, photos, likes and chats you create are stored on our server (Supabase). No phone number is collected.\n" +
          "• Never asks for your dating-app passwords. No automation, no accessibility service, no notification reading, no live sync.\n" +
          "• Exports contain other people's names and messages. Keep them private and do not share them.\n" +
          "• Not affiliated with or endorsed by any dating platform.\n" +
          "• Settings → Delete All Data wipes everything at any time.")
    }
    var ok by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(ok, { ok = it }); P("I understand and consent to on-device processing of my exports.") }
    Button(onClick = onAccept, enabled = ok, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
}

@Composable
fun ProfilesScreen(bundles: List<ImportBundle>, f: Filters, onUse: (ImportBundle) -> Unit = {}, onImport: () -> Unit) {
    val list = bundles.filter { f.platformOk(it.platformId) }
    if (list.isEmpty()) return Empty("No imports yet", "Import a data export to see your profiles.", "Import", onImport)
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list, key = { it.id }) { b ->
            val p = b.profile
            NeonCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).neonRing(circle = true), contentAlignment = Alignment.Center) { Text((p?.name ?: name(b)).take(1).uppercase(), color = Magenta, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Column { H(p?.name ?: "(no name)"); P(name(b) + " · " + b.sourceName, MaterialTheme.colorScheme.secondary) }
                }
                Spacer(Modifier.height(8.dp))
                p?.bio?.let { P(it) }
                listOfNotNull(p?.birthDate?.let { "Born $it" }, p?.gender?.let { "Gender $it" }, p?.city, p?.jobs?.joinToString().takeIf { !it.isNullOrBlank() }, p?.schools?.joinToString().takeIf { !it.isNullOrBlank() })
                    .forEach { P("• $it") }
                P("${b.matches.size} conversations · ${b.messages.size} messages")
                if (b.usage.isNotEmpty()) P(b.usage.entries.take(8).joinToString("  ") { "${it.key}: ${it.value}" }, MaterialTheme.colorScheme.primary)
                b.notes.forEach { P("ℹ $it", androidx.compose.ui.graphics.Color.Gray) }
                if (p != null) TextButton(onClick = { onUse(b) }) { Text("Use for my MatchVictorious profile") }
            }
        }
    }
}

fun name(b: ImportBundle) = Platforms.byId(b.platformId)?.displayName ?: b.platformId

@Composable
fun MatchesScreen(bundles: List<ImportBundle>, f: Filters) {
    val rows = bundles.filter { f.platformOk(it.platformId) }.flatMap { b -> b.matches.map { b to it } }
        .filter { (_, m) -> (!f.onlyWithMessages || m.messageCount > 0) && (f.query.isBlank() || (m.displayName ?: m.id).contains(f.query, true)) }
    if (rows.isEmpty()) return Empty("No matches", "Nothing imported or nothing matches your filters.")
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rows) { (b, m) -> NeonCard { H(m.displayName ?: m.id); P("${name(b)} · ${m.messageCount} messages" + (m.matchedAt?.let { " · first $it" } ?: "")) } }
    }
}

@Composable
fun MessagesScreen(bundles: List<ImportBundle>, f: Filters) {
    val rows = bundles.filter { f.platformOk(it.platformId) }.flatMap { b -> b.messages.map { b to it } }
        .filter { (_, m) -> f.query.isBlank() || m.text.contains(f.query, true) || m.matchId.contains(f.query, true) }
    if (rows.isEmpty()) return Empty("No messages", "Read-only view of imported messages.")
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { P("Read-only. Imported messages cannot be replied to from here.", androidx.compose.ui.graphics.Color.Gray) }
        items(rows) { (b, m) ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromMe) Alignment.End else Alignment.Start) {
                Text("${name(b)} · ${m.matchId}" + (m.sentAt?.let { " · $it" } ?: ""), fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.Gray)
                Box(Modifier.fillMaxWidth(0.85f).neonRing().background(if (m.fromMe) Color2 else Surface, RoundedCornerShape(16.dp)).padding(12.dp)) { P(m.text) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltersScreen(f: Filters) = Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    H("Platforms"); P("None selected = all platforms.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Platforms.all.forEach { p -> FilterChip(selected = p.id in f.platforms, onClick = { f.platforms = if (p.id in f.platforms) f.platforms - p.id else f.platforms + p.id }, label = { Text(p.displayName) }) }
    }
    OutlinedTextField(f.query, { f.query = it }, label = { Text("Search matches & messages") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(f.onlyWithMessages, { f.onlyWithMessages = it }); Spacer(Modifier.width(8.dp)); P("Only matches with messages") }
    OutlinedButton(onClick = { f.platforms = emptySet(); f.query = ""; f.onlyWithMessages = false }) { Text("Reset filters") }
}

@Composable
fun ImportScreen(store: EncryptedStore, bundles: List<ImportBundle>, reload: () -> Unit, onGuide: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var platform by remember { mutableStateOf("tinder") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; status = "Parsing on-device…"
        scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val fileName = displayName(ctx, uri)
                    val bytes = ctx.contentResolver.openInputStream(uri)!!.use { s ->
                        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(65536); var n = 0L
                        while (true) { val r = s.read(buf); if (r < 0) break; n += r; if (n > MAX_IMPORT) throw ImportException("File larger than 200 MB"); out.write(buf, 0, r) }
                        out.toByteArray()
                    }
                    val b = ProviderRegistry.get(platform).parse(fileName, bytes, UUID.randomUUID().toString(), System.currentTimeMillis())
                    store.save(b)
                    "Imported ${b.matches.size} conversations and ${b.messages.size} messages from $fileName."
                }.getOrElse { "Import failed: ${it.message}" }
            }
            busy = false; reload()
        }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            NeonCard {
                H("1. Choose the platform")
                Platforms.all.chunked(2).forEach { row -> Row { row.forEach { p ->
                    Row(Modifier.weight(1f).clickable { platform = p.id }, verticalAlignment = Alignment.CenterVertically) { RadioButton(platform == p.id, { platform = p.id }); Text(p.displayName, fontSize = 14.sp) } } } }
                val info = Platforms.byId(platform)!!
                P("Format: ${info.expectedFormat}", MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(8.dp)); H("2. Pick your export file")
                P("ZIP, JSON or HTML from your device storage. Nothing is uploaded.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = { picker.launch(arrayOf("application/zip", "application/json", "text/html", "text/plain", "application/octet-stream")) }) { Text("Pick file") }
                    OutlinedButton(onClick = onGuide) { Text("How to get it") }
                }
                TextButton(onClick = { DeepLinks.openOfficialApp(ctx, info) }) { Text("Open official ${info.displayName} app") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                status?.let { P(it, Cyan) }
            }
        }
        if (bundles.isNotEmpty()) item { H("Imported files") }
        items(bundles, key = { it.id }) { b ->
            NeonCard { Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { P("${name(b)} · ${b.sourceName}"); P("${b.messages.size} messages", androidx.compose.ui.graphics.Color.Gray) }
                TextButton(onClick = { store.delete(b.id); reload() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            } }
        }
    }
}

private fun displayName(ctx: Context, uri: Uri): String = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull() ?: "export"

@Composable
fun GuideScreen() {
    val ctx = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { P("None of these platforms offers a public API, so MatchVictorious works only with the data export you request yourself (GDPR/CCPA right of access). Exports can take from hours to 30 days. Tinder's download link expires after 24 hours.") }
        items(Platforms.all) { p ->
            NeonCard {
                H(p.displayName); P(p.requestSteps); P("Format: ${p.expectedFormat}", androidx.compose.ui.graphics.Color.Gray)
                Row { TextButton(onClick = { DeepLinks.openUrl(ctx, p.requestDataUrl) }) { Text("Official page") }
                      TextButton(onClick = { DeepLinks.openOfficialApp(ctx, p) }) { Text("Open app") } }
            }
        }
        item { P("Links open in your browser. Imported exports never leave this device.", androidx.compose.ui.graphics.Color.Gray) }
    }
}

@Composable
fun HelpScreen() {
    val ctx = LocalContext.current
    val manual = remember { runCatching { ctx.assets.open("MANUAL.md").bufferedReader().readText() }.getOrDefault("Manual not found.") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        manual.lines().forEach { l ->
            when {
                l.startsWith("# ") -> Text(l.drop(2), fontSize = 24.sp, color = Cyan, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                l.startsWith("## ") -> Text(l.drop(3), fontSize = 19.sp, color = Magenta, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                l.startsWith("### ") -> Text(l.drop(4), fontSize = 16.sp, color = Cyan, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                l.startsWith("    ") || l.startsWith("```") -> if (!l.startsWith("```")) Text(l, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Cyan)
                else -> P(l.replace("**", "").replace("`", ""))
            }
        }
    }
}

@Composable
fun SettingsScreen(store: EncryptedStore, onDeleteAll: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NeonCard { H("Privacy"); P("Network: only for your MatchVictorious account (Supabase). Imports never leave the device.\nStorage: AES-256-GCM EncryptedFile, key in Android Keystore\nBackups: disabled\nLive sync: not available (no platform API)") }
        NeonCard { H("Fleet API"); P("droidcommand-AI may query STATUS (aggregate counts only) and open screens. Requires the signature permission com.victorious.match.permission.FLEET_CONTROL. See Help.") }
        NotificationsCard()
        DeleteAccountCard {}
        NeonCard {
            H("Delete All Data"); P("Removes every import, the consent record and caches from this device. Cannot be undone.")
            Button(onClick = { confirm = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete All Data") }
        }
        P("MatchVictorious ${com.victorious.match.BuildConfigLite.VERSION}. Not affiliated with any dating platform.", androidx.compose.ui.graphics.Color.Gray)
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Delete all data?") }, text = { Text("All imports will be erased permanently.") },
        confirmButton = { TextButton(onClick = { confirm = false; onDeleteAll() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@Composable
fun MoreScreen(go: (Screen) -> Unit) = Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    var admin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { admin = com.victorious.match.backend.Backend.me() != null && com.victorious.match.backend.ModApi.isAdmin() }
    NeonCard(Modifier.clickable { go(Screen.Safety) }) { H("Safety center"); P("SOS, check-in timer, Share my date, safety tips") }
    if (admin) NeonCard(Modifier.clickable { go(Screen.Moderation) }) { H("Moderation"); P("Verify profiles, review photos, handle reports") }
    listOf(Screen.Profiles to "Profiles from your imported exports (on-device)", Screen.Matches to "Imported matches (on-device)",
        Screen.Messages to "Imported messages (on-device)", Screen.Filters to "Platform, search and message filters", Screen.Guide to "How to request your export from each platform",
        Screen.Help to "User manual", Screen.Settings to "Privacy, fleet API, Delete All Data").forEach { (s, d) ->
        NeonCard(Modifier.clickable { go(s) }) { H(s.title); P(d) }
    }
}

@Composable
fun Empty(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) =
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(96.dp).neonRing(circle = true))
        Spacer(Modifier.height(16.dp)); H(title); P(body); 
        if (action != null) { Spacer(Modifier.height(12.dp)); Button(onClick = onAction) { Text(action) } }
    }
