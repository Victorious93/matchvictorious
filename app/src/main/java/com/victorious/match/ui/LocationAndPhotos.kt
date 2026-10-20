package com.victorious.match.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.victorious.match.backend.Backend
import com.victorious.match.backend.PrefsRow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Coarse location only. Returns null if unavailable. */
@SuppressLint("MissingPermission")
suspend fun coarseLocation(ctx: Context): Location? {
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
    val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
    if (Build.VERSION.SDK_INT >= 30) {
        val fresh = suspendCancellableCoroutine<Location?> { c -> lm.getCurrentLocation(provider, null, ctx.mainExecutor) { c.resume(it) } }
        if (fresh != null) return fresh
    }
    return runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
}

@Composable
fun LocationCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var consent by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var showCities by remember { mutableStateOf(false) }
    var cities by remember { mutableStateOf<List<String>>(emptyList()) }
    fun useDevice() = scope.launch {
        val loc = coarseLocation(ctx)
        status = if (loc == null) { showCities = true; "Couldn't get a location. Pick your city instead." }
        else runCatching { Backend.setLocation(loc.latitude, loc.longitude); "Approximate location saved (rounded to about 1 km)." }.getOrElse { it.message }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useDevice() else { showCities = true; status = "Location permission denied. Pick your city instead." }
    }
    LaunchedEffect(showCities) { if (showCities && cities.isEmpty()) cities = runCatching { Backend.cities() }.getOrDefault(emptyList()) }
    NeonCard {
        H("Location")
        P("Used only to show people near you. We store an approximate point (rounded to about 1 km). Other members never see coordinates, only an approximate distance like \"About 7 km away\".")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { consent = true }) { Text("Use my approximate location") }
            OutlinedButton(onClick = { showCities = true }) { Text("Choose city") }
        }
        if (showCities) {
            var open by remember { mutableStateOf(false) }
            Box { OutlinedButton(onClick = { open = true }) { Text("Select a city…") }
                DropdownMenu(open, { open = false }) {
                    cities.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { open = false; scope.launch {
                        status = runCatching { Backend.setCity(c); "Location set to $c." }.getOrElse { it.message } } }) }
                }
            }
        }
        status?.let { P(it, Cyan) }
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false },
        title = { Text("Share approximate location?") },
        text = { Text("MatchVictorious will ask Android for your approximate (coarse) location once, round it to about 1 km, and save it to your profile so Discover can show nearby people. Nobody sees your coordinates. You can switch to a city at any time.") },
        confirmButton = { TextButton(onClick = { consent = false
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) useDevice()
            else perm.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { consent = false }) { Text("Not now") } })
}

@Composable
fun PreferencesCard() {
    val scope = rememberCoroutineScope()
    var p by remember { mutableStateOf<PrefsRow?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { p = runCatching { Backend.prefs() }.getOrNull() }
    val cur = p ?: return
    NeonCard {
        H("Discovery preferences")
        P("Age ${cur.minAge}–${cur.maxAge}")
        RangeSlider(value = cur.minAge.toFloat()..cur.maxAge.toFloat(), onValueChange = { r -> p = cur.copy(minAge = r.start.toInt(), maxAge = r.endInclusive.toInt()) }, valueRange = 18f..99f)
        P("Maximum distance ${cur.maxDistanceKm} km")
        Slider(value = cur.maxDistanceKm.toFloat(), onValueChange = { p = cur.copy(maxDistanceKm = it.toInt()) }, valueRange = 1f..500f)
        Button(onClick = { scope.launch { status = runCatching { Backend.savePrefs(cur); "Preferences saved." }.getOrElse { it.message } } }) { Text("Save preferences") }
        status?.let { P(it, Cyan) }
    }
}

/** Approved photos via short-lived signed URLs; placeholder when there are none. */
@Composable
fun PhotoCarousel(paths: List<String>, name: String) {
    var urls by remember(paths) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(paths) { urls = if (paths.isEmpty()) emptyList() else Backend.signedUrls(paths) }
    val shape = RoundedCornerShape(16.dp)
    val mod = Modifier.fillMaxWidth().aspectRatio(0.8f).clip(shape)
    val u = urls
    when {
        u == null -> Box(mod.background(Surface), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        u.isEmpty() -> Box(mod.background(Surface), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), fontSize = 72.sp, color = Magenta) }
        else -> {
            val pager = rememberPagerState { u.size }
            Box(mod) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                    AsyncImage(model = u[i], contentDescription = "Photo ${i + 1} of $name", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (u.size > 1) Text("${pager.currentPage + 1}/${u.size}", Modifier.align(Alignment.TopEnd).padding(8.dp).background(Bg, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp), color = Cyan)
            }
        }
    }
}

@Composable
fun DeleteAccountCard(onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    NeonCard {
        H("Delete account")
        P("Permanently deletes your MatchVictorious account on our server: profile, photos, likes, matches, messages and location. Safety reports are kept without your account link. Web option: victorious93.github.io/matchvictorious/delete-account.html")
        Button(enabled = Backend.me() != null && !busy, onClick = { confirm = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete account") }
        if (Backend.me() == null) P("Sign in first to delete your account.", androidx.compose.ui.graphics.Color.Gray)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { P(it, Cyan) }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false; typed = "" },
        title = { Text("Delete your account?") },
        text = { Column { Text("This cannot be undone. Type DELETE to confirm."); OutlinedTextField(typed, { typed = it }, singleLine = true) } },
        confirmButton = { TextButton(enabled = typed.trim() == "DELETE", onClick = { confirm = false; typed = ""; busy = true; scope.launch {
            status = runCatching { Backend.deleteAccount(); onDeleted(); "Your account was deleted." }.getOrElse { "Deletion failed: ${it.message}" }; busy = false } }) {
            Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false; typed = "" }) { Text("Cancel") } })
}
