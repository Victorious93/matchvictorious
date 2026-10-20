package com.victorious.match.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.victorious.match.backend.*
import com.victorious.match.safety.Checkin
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun shareSheet(ctx: Context, text: String, title: String) =
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

@Composable
fun SafetyScreen() {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SosCard(); CheckinCard(); ShareDateCard(); SafetyTips()
    }
}

@Composable
private fun SosCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    NeonCard {
        H("SOS"); P("Opens your phone dialer with 911 ready (you press call), and lets you share your approximate location yourself. Nothing is sent automatically.")
        Button(onClick = { open = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("SOS") }
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Emergency") },
        text = { Text("Call emergency services, or share your location with someone you trust.") },
        confirmButton = { TextButton(onClick = { open = false; ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:911")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Call 911") } },
        dismissButton = { TextButton(onClick = { open = false; scope.launch {
            val loc = coarseLocation(ctx)
            shareSheet(ctx, SafetyLogic.sosText(loc?.latitude, loc?.longitude), "Share location")
        } }) { Text("Share my location") } })
}

@Composable
private fun CheckinCard() {
    val ctx = LocalContext.current
    var at by remember { mutableStateOf(Checkin.scheduledAt(ctx)) }
    var hours by remember { mutableFloatStateOf(2f) }
    val fmt = remember { DateTimeFormatter.ofPattern("EEE HH:mm").withZone(ZoneId.systemDefault()) }
    NeonCard {
        H("Check-in timer"); P("Get a reminder to check in. If you don't respond, nobody is contacted automatically; the reminder helps you decide to use SOS or share your date.")
        val a = at
        if (a != null) {
            P("Check-in due ${fmt.format(Instant.ofEpochMilli(a))}", Cyan)
            Button(onClick = { Checkin.cancel(ctx); at = null }) { Text("I'm safe — cancel timer") }
        } else {
            P("Remind me in ${"%.1f".format(hours)} h"); Slider(hours, { hours = it }, valueRange = 0.5f..6f, steps = 10)
            Button(onClick = { val t = System.currentTimeMillis() + (hours * 3600_000).toLong(); Checkin.schedule(ctx, t); at = t }) { Text("Start timer") }
        }
    }
}

@Composable
fun ShareDateCard(preset: MatchCard? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cards by remember { mutableStateOf<List<MatchCard>>(emptyList()) }
    var match by remember { mutableStateOf(preset) }
    var name by remember { mutableStateOf(preset?.otherName ?: "") }
    var place by remember { mutableStateOf("") }
    var whenText by remember { mutableStateOf(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(java.time.LocalDateTime.now().plusHours(2).withMinute(0))) }
    var checkinH by remember { mutableFloatStateOf(3f) }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (Backend.me() != null) cards = runCatching { Backend.matchCards() }.getOrDefault(emptyList()) }
    val start = SafetyLogic.parseLocal(whenText)
    val errs = SafetyLogic.errors(name, place, start)
    NeonCard {
        H("Share my date"); P("Creates a private link with your date details that expires 12 hours after the date. You choose who to send it to via the share sheet.")
        if (cards.isNotEmpty()) { var open by remember { mutableStateOf(false) }
            Box { OutlinedButton(onClick = { open = true }) { Text(match?.let { "Match: ${it.otherName}" } ?: "Pick a match (optional)") }
                DropdownMenu(open, { open = false }) { cards.forEach { c -> DropdownMenuItem(text = { Text(c.otherName) }, onClick = { open = false; match = c; name = c.otherName }) } } } }
        OutlinedTextField(name, { name = it.take(60) }, label = { Text("Who") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(place, { place = it.take(200) }, label = { Text("Where") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(whenText, { whenText = it }, label = { Text("When (YYYY-MM-DD HH:MM)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        P("Check in ${"%.1f".format(checkinH)} h after start (also sets your reminder)"); Slider(checkinH, { checkinH = it }, valueRange = 0.5f..8f)
        errs.forEach { P("• $it", MaterialTheme.colorScheme.error) }
        Button(enabled = errs.isEmpty() && Backend.me() != null, onClick = { scope.launch {
            status = runCatching {
                val s = start!!; val ci = s.plusSeconds((checkinH * 3600).toLong())
                val row = SafetyApi.createShare(name, place, s, ci, match?.matchId, null)
                Checkin.schedule(ctx, ci.toEpochMilli())
                shareSheet(ctx, SafetyLogic.shareText(name, place, s, row.token!!, ci), "Share my date"); "Link created. Check-in reminder set."
            }.getOrElse { it.message } } }) { Text("Create link & share") }
        if (Backend.me() == null) P("Sign in to create a share link.", androidx.compose.ui.graphics.Color.Gray)
        status?.let { P(it, Cyan) }
    }
}

@Composable
private fun SafetyTips() = NeonCard {
    H("Safety tips")
    listOf("Meet in a public place and arrange your own transport.", "Tell a friend where you're going — use Share my date.",
        "Keep chats in the app until you're comfortable. You never need to share your phone number.",
        "Video call first. Be wary of anyone who avoids it.", "Never send money, gift cards or crypto to someone you haven't met. That's a scam pattern.",
        "Stay in control of your drink and leave whenever you want.", "Report and block anyone who makes you uncomfortable — reports are confidential.",
        "In an emergency, call 911 (US) or your local emergency number.").forEach { P("• $it") }
}
