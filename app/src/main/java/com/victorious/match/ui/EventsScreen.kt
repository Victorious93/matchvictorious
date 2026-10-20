package com.victorious.match.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.victorious.match.backend.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val evFmt = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm").withZone(ZoneId.systemDefault())
private fun fmt(s: String) = runCatching { evFmt.format(Instant.parse(s)) }.getOrDefault(s)

@Composable
fun EventsScreen() = SignedIn { me ->
    val scope = rememberCoroutineScope()
    var city by remember { mutableStateOf<String?>(null) }
    var cities by remember { mutableStateOf<List<String>>(emptyList()) }
    var events by remember { mutableStateOf<List<EventItem>?>(null) }
    var open by remember { mutableStateOf<EventItem?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    fun load() = scope.launch { events = runCatching { EventsApi.list(city) }.getOrElse { msg = it.message; emptyList() } }
    LaunchedEffect(city) { load() }
    LaunchedEffect(Unit) { cities = runCatching { Backend.cities() }.getOrDefault(emptyList()) }
    open?.let { e -> return@SignedIn EventDetail(me, e) { open = null; load() } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { var dd by remember { mutableStateOf(false) }
            Box { OutlinedButton(onClick = { dd = true }) { Text(city ?: "All cities") }
                DropdownMenu(dd, { dd = false }) {
                    DropdownMenuItem(text = { Text("All cities") }, onClick = { dd = false; city = null })
                    cities.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { dd = false; city = c }) } } } }
        msg?.let { item { P(it, MaterialTheme.colorScheme.error) } }
        val ev = events
        if (ev == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (ev.isEmpty()) item { P("No upcoming events here yet. Group dates are organised by MatchVictorious moderators.") }
        else items(ev, key = { it.id }) { e ->
            NeonCard(Modifier.clickable { open = e }) {
                H(e.title); P("${fmt(e.startsAt)} · ${e.venue}, ${e.city}", MaterialTheme.colorScheme.secondary)
                P("${e.going}/${e.capacity} going" + if (e.iAmGoing) " · You're going" else "")
            }
        }
    }
}

@Composable
private fun EventDetail(me: String, e: EventItem, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var going by remember { mutableStateOf(e.iAmGoing) }
    var attendees by remember { mutableStateOf<List<String>>(emptyList()) }
    var msgs by remember { mutableStateOf<List<EventMessage>>(emptyList()) }
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var text by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    LaunchedEffect(going) {
        if (!going) { attendees = emptyList(); msgs = emptyList(); return@LaunchedEffect }
        attendees = runCatching { EventsApi.attendees(e.id) }.getOrDefault(emptyList())
        msgs = runCatching { EventsApi.messages(e.id) }.getOrDefault(emptyList())
        names = runCatching { Backend.names(msgs.map { it.senderId }.distinct()) }.getOrDefault(emptyMap())
        runCatching { EventsApi.live(e.id).collect { m -> if (msgs.none { it.id == m.id }) msgs = msgs + m
            if (m.senderId !in names) names = names + runCatching { Backend.names(listOf(m.senderId)) }.getOrDefault(emptyMap()) } }
    }
    DisposableEffect(e.id) { onDispose { scope.launch { runCatching { EventsApi.leave(e.id) } } } }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("‹ Events") }
        H(e.title); P("${fmt(e.startsAt)} – ${fmt(e.endsAt)}", MaterialTheme.colorScheme.secondary); P("${e.venue}, ${e.city}")
        e.description?.let { P(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!going) Button(onClick = { scope.launch { runCatching { EventsApi.rsvp(e.id); going = true }.onFailure { info = EventLogic.rsvpError(it.message) } } }) { Text("RSVP") }
            else OutlinedButton(onClick = { scope.launch { runCatching { EventsApi.cancelRsvp(e.id); going = false } } }) { Text("Cancel RSVP") }
        }
        info?.let { P(it, MaterialTheme.colorScheme.error) }
        if (!going) { P("RSVP (verified members only) to see who's going and join the group chat.", androidx.compose.ui.graphics.Color.Gray); return@Column }
        P("Going: " + attendees.joinToString(), MaterialTheme.colorScheme.secondary)
        H("Group chat")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(msgs, key = { it.id ?: it.hashCode().toLong() }) { m -> P((if (m.senderId == me) "You" else names[m.senderId] ?: "Member") + ": " + m.body) }
        }
        Row { OutlinedTextField(text, { text = it.take(1000) }, Modifier.weight(1f), placeholder = { Text("Message the group") })
            Button(enabled = text.isNotBlank(), onClick = { val t = text; text = ""; scope.launch { runCatching { EventsApi.send(e.id, t) }.onFailure { info = it.message; text = t } } }) { Text("Send") } }
    }
}

/** Moderator form to create events. */
@Composable
fun CreateEventCard() {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }; var desc by remember { mutableStateOf("") }; var venue by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }; var start by remember { mutableStateOf("") }; var end by remember { mutableStateOf("") }
    var cap by remember { mutableStateOf("20") }; var status by remember { mutableStateOf<String?>(null) }
    var cities by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { cities = runCatching { Backend.cities() }.getOrDefault(emptyList()) }
    val s = SafetyLogic.parseLocal(start); val en = SafetyLogic.parseLocal(end)
    val errs = EventLogic.errors(title, city, venue, s, en, cap.toIntOrNull())
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NeonCard {
            H("Create event")
            OutlinedTextField(title, { title = it.take(100) }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc, { desc = it.take(2000) }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            var dd by remember { mutableStateOf(false) }
            Box { OutlinedButton(onClick = { dd = true }) { Text(city.ifBlank { "City" }) }
                DropdownMenu(dd, { dd = false }) { cities.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { dd = false; city = c }) } } }
            OutlinedTextField(venue, { venue = it.take(200) }, label = { Text("Venue") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(start, { start = it }, label = { Text("Start (YYYY-MM-DD HH:MM)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(end, { end = it }, label = { Text("End (YYYY-MM-DD HH:MM)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(cap, { cap = it.filter(Char::isDigit).take(3) }, label = { Text("Capacity") }, singleLine = true)
            errs.forEach { P("• $it", MaterialTheme.colorScheme.error) }
            Button(enabled = errs.isEmpty(), onClick = { scope.launch { status = runCatching {
                EventsApi.create(NewEvent(title.trim(), desc.trim().ifBlank { null }, city, venue.trim(), s!!.toString(), en!!.toString(), cap.toInt(), Backend.me()!!))
                title = ""; desc = ""; venue = ""; "Event created." }.getOrElse { it.message } } }) { Text("Create") }
            status?.let { P(it, Cyan) }
        }
    }
}
