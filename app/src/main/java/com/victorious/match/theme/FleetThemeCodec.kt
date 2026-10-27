package com.victorious.match.theme

import org.json.JSONObject

/** Pure parsing/validation for the fleet theme contract (VictorLaunch docs/specs/FLEET_THEME.md, schemaVersion 1). */
data class FleetPalette(
    val background: Long, val surface: Long, val primary: Long, val secondary: Long,
    val onBackground: Long, val muted: Long, val danger: Long, val stale: Long, val tile: Long,
)

data class FleetThemeSnapshot(
    val schemaVersion: Int, val mode: String, val iconStyle: String,
    val iconPackPackage: String, val updatedAtMs: Long, val palette: FleetPalette,
)

object FleetThemeCodec {
    const val SCHEMA_VERSION = 1
    const val PROVIDER_PACKAGE = "com.victorious.launch"
    const val AUTHORITY = "com.victorious.launch.theme"
    const val URI = "content://$AUTHORITY/current"
    const val ACTION_CHANGED = "com.victorious.launch.action.FLEET_THEME_CHANGED"
    val MODES = setOf("LIGHT", "DARK", "NEON_FLEET", "MATERIAL_YOU")
    val PALETTE_KEYS = listOf("background", "surface", "primary", "secondary", "onBackground", "muted", "danger", "stale", "tile")

    /** `#RRGGBB` (opaque) or `#AARRGGBB` -> ARGB long, else null. */
    fun parseHex(s: String?): Long? {
        val h = s?.trim() ?: return null
        if (!h.startsWith("#")) return null
        val body = h.substring(1)
        if (body.length != 6 && body.length != 8) return null
        if (!body.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        val v = body.toLong(16)
        return if (body.length == 6) v or 0xFF000000L else v
    }

    /** Build from a map of palette key -> hex. Null if any required key missing/invalid. */
    fun palette(get: (String) -> String?): FleetPalette? {
        val c = PALETTE_KEYS.map { parseHex(get(it)) ?: return null }
        return FleetPalette(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8])
    }

    /** Parse the `json` column. Unknown fields ignored; schemaVersion != 1 rejected. */
    fun parseJson(json: String?): FleetThemeSnapshot? = runCatching {
        val o = JSONObject(json ?: return null)
        if (o.optInt("schemaVersion", -1) != SCHEMA_VERSION) return null
        val mode = o.optString("mode", "")
        if (mode !in MODES) return null
        val p = o.optJSONObject("palette") ?: return null
        val pal = palette { k -> if (p.has(k)) p.optString(k) else null } ?: return null
        FleetThemeSnapshot(SCHEMA_VERSION, mode, o.optString("iconStyle", "SYSTEM"),
            o.optString("iconPackPackage", ""), o.optLong("updatedAtMs", 0L), pal)
    }.getOrNull()

    /** Parse a row given as column -> value (strings). Prefers `json`, falls back to per-column palette. */
    fun parseRow(cols: Map<String, String?>): FleetThemeSnapshot? {
        if (cols["schemaVersion"]?.toIntOrNull() != SCHEMA_VERSION) return null
        parseJson(cols["json"])?.let { return it }
        val mode = cols["mode"] ?: return null
        if (mode !in MODES) return null
        val pal = palette { cols[it] } ?: return null
        return FleetThemeSnapshot(SCHEMA_VERSION, mode, cols["iconStyle"] ?: "SYSTEM",
            cols["iconPackPackage"] ?: "", cols["updatedAtMs"]?.toLongOrNull() ?: 0L, pal)
    }

    /** Pin check: provider must be VictorLaunch and (if pins configured) signed by a pinned cert SHA-256. */
    fun isTrustedProvider(pkg: String?, certSha256s: List<String>, pins: Set<String>): Boolean {
        if (pkg != PROVIDER_PACKAGE) return false
        if (pins.isEmpty()) return true
        val norm = pins.map { it.replace(":", "").lowercase() }.toSet()
        return certSha256s.isNotEmpty() && certSha256s.all { it.replace(":", "").lowercase() in norm }
    }
}
