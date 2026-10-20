package com.victorious.match.backend

import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable data class DateShareRow(
    val id: String? = null, @SerialName("user_id") val userId: String, val token: String? = null,
    @SerialName("match_id") val matchId: String? = null, @SerialName("date_name") val dateName: String, val place: String,
    @SerialName("starts_at") val startsAt: String, @SerialName("checkin_at") val checkinAt: String? = null, val note: String? = null,
    @SerialName("expires_at") val expiresAt: String, val revoked: Boolean = false)

object SafetyLogic {
    const val SHARE_PAGE = "https://victorious93.github.io/matchvictorious/date.html?t="
    private val input = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val pretty = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
    fun parseLocal(s: String, zone: ZoneId = ZoneId.systemDefault()): Instant? = runCatching { LocalDateTime.parse(s.trim(), input).atZone(zone).toInstant() }.getOrNull()
    /** Share link expires 12 h after the date starts (server caps at 24 h). */
    fun expiry(start: Instant): Instant = start.plusSeconds(12 * 3600)
    fun errors(name: String, place: String, start: Instant?, now: Instant = Instant.now()): List<String> = buildList {
        if (name.isBlank() || name.length > 60) add("Enter who you're meeting (max 60 characters)")
        if (place.isBlank() || place.length > 200) add("Enter where (max 200 characters)")
        if (start == null) add("Enter date and time as YYYY-MM-DD HH:MM")
        else if (start.isBefore(now.minusSeconds(3600)) || start.isAfter(now.plusSeconds(30L * 86400))) add("Pick a time within the next 30 days")
    }
    fun shareText(name: String, place: String, start: Instant, token: String, checkin: Instant?, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        append("Safety note: I'm meeting $name at $place on ${pretty.format(start.atZone(zone))}.")
        checkin?.let { append(" I'll check in by ${pretty.format(it.atZone(zone))}. If you don't hear from me, please check on me.") }
        append("\nDetails (expires automatically): $SHARE_PAGE$token")
    }
    fun mapsLink(lat: Double, lon: Double) = "https://maps.google.com/?q=%.5f,%.5f".format(java.util.Locale.US, lat, lon)
    fun sosText(lat: Double?, lon: Double?) = if (lat == null || lon == null) "I need help. Please call me or emergency services." else "I need help. My location: ${mapsLink(lat, lon)}"
}

object SafetyApi {
    suspend fun createShare(name: String, place: String, start: Instant, checkin: Instant?, matchId: String?, note: String?): DateShareRow =
        Backend.client.from("date_shares").insert(DateShareRow(userId = Backend.me()!!, matchId = matchId, dateName = name.trim(), place = place.trim(),
            startsAt = start.toString(), checkinAt = checkin?.toString(), note = note?.trim()?.ifBlank { null }, expiresAt = SafetyLogic.expiry(start).toString())) { select() }.decodeSingle()
    suspend fun revoke(id: String) { Backend.client.from("date_shares").update({ set("revoked", true) }) { filter { eq("id", id) } } }
}
