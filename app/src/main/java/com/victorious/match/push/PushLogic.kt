package com.victorious.match.push

/** Pure helpers (unit-tested). */
object PushLogic {
    const val CHANNEL_ID = "matches_messages"
    /** Fallback notification text if a data-only message arrives. Generic: no names or bodies. */
    fun fallbackText(type: String?): String = when (type) { "match" -> "You have a new match"; "message" -> "New message"; else -> "MatchVictorious" }
    fun isAvailable(configured: Boolean, firebaseApps: Int) = configured && firebaseApps > 0
    fun needsRuntimePermission(sdk: Int) = sdk >= 33
}
