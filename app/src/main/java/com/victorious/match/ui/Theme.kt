package com.victorious.match.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Cyan = Color(0xFF00F0FF)
val Magenta = Color(0xFFFF2BD6)
val Bg = Color(0xFF07070D)
val Surface = Color(0xFF12121C)
val RingBrush = Brush.sweepGradient(listOf(Cyan, Magenta, Cyan))
val LinearRing = Brush.linearGradient(listOf(Cyan, Magenta))

fun Modifier.neonRing(circle: Boolean = false) = border(2.dp, RingBrush, if (circle) CircleShape else RoundedCornerShape(16.dp))

private val NeonDefault = darkColorScheme(
        primary = Cyan, secondary = Magenta, tertiary = Magenta, background = Bg, surface = Surface,
        surfaceVariant = Color(0xFF1B1B2A), onPrimary = Color.Black, onSecondary = Color.Black,
        onBackground = Color(0xFFEDEDF7), onSurface = Color(0xFFEDEDF7), error = Color(0xFFFF5470),
)

/** Fleet palette from VictorLaunch when available; neon cyan→magenta default otherwise. */
private fun fleetScheme(p: com.victorious.match.theme.FleetPalette, light: Boolean): ColorScheme {
    fun c(v: Long) = Color(v.toInt())
    val base = if (light) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = c(p.primary), secondary = c(p.secondary), tertiary = c(p.secondary),
        background = c(p.background), surface = c(p.surface), surfaceVariant = c(p.tile),
        onBackground = c(p.onBackground), onSurface = c(p.onBackground), onSurfaceVariant = c(p.muted),
        error = c(p.danger), outline = c(p.muted),
        onPrimary = if (light) Color.White else Color.Black, onSecondary = if (light) Color.White else Color.Black,
    )
}

@Composable
fun MatchTheme(content: @Composable () -> Unit) {
    val state by com.victorious.match.theme.FleetThemeClient.state.collectAsState()
    val scheme = (state as? com.victorious.match.theme.FleetThemeClient.State.Applied)?.snapshot
        ?.let { fleetScheme(it.palette, it.mode == "LIGHT") } ?: NeonDefault
    MaterialTheme(colorScheme = scheme, content = content)
}
