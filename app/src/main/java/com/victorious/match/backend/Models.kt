package com.victorious.match.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileRow(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val birthdate: String,
    val gender: String? = null,
    val bio: String? = null,
    @SerialName("imported_from") val importedFrom: String? = null,
    @SerialName("verification_status") val verificationStatus: String? = null,
)

@Serializable
data class Candidate(
    val id: String, @SerialName("display_name") val displayName: String, val age: Int, val bio: String? = null,
    val city: String? = null, @SerialName("distance_km") val distanceKm: Int? = null, val photos: List<String> = emptyList(),
    val prompts: List<CandidatePrompt> = emptyList(), @SerialName("voice_path") val voicePath: String? = null,
)

@Serializable data class CityRow(val name: String)

@Serializable
data class PrefsRow(
    @SerialName("user_id") val userId: String,
    @SerialName("min_age") val minAge: Int = 18,
    @SerialName("max_age") val maxAge: Int = 99,
    @SerialName("max_distance_km") val maxDistanceKm: Int = 50,
    @SerialName("only_with_voice") val onlyWithVoice: Boolean = false,
    @SerialName("only_with_prompts") val onlyWithPrompts: Boolean = false,
) {
    fun clamped() = copy(minAge = minAge.coerceIn(18, 99), maxAge = maxAge.coerceIn(minAge.coerceIn(18, 99), 99), maxDistanceKm = maxDistanceKm.coerceIn(1, 500))
}

@Serializable
data class SwipeRow(@SerialName("swiper_id") val swiperId: String, @SerialName("target_id") val targetId: String, val liked: Boolean,
    val comment: String? = null, @SerialName("prompt_id") val promptId: String? = null)

@Serializable
data class MatchRow(
    val id: String,
    @SerialName("user_a") val userA: String,
    @SerialName("user_b") val userB: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("unmatched_at") val unmatchedAt: String? = null,
) { fun other(me: String) = if (userA == me) userB else userA }

@Serializable
data class MessageRow(
    val id: Long? = null,
    @SerialName("match_id") val matchId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable data class BlockRow(@SerialName("blocker_id") val blockerId: String, @SerialName("blocked_id") val blockedId: String)

@Serializable
data class ReportRow(@SerialName("reporter_id") val reporterId: String, @SerialName("reported_id") val reportedId: String, val reason: String, val details: String? = null)

@Serializable
data class PhotoRow(@SerialName("user_id") val userId: String, @SerialName("storage_path") val storagePath: String, val position: Int = 0)

object Chat {
    const val MAX_BODY = 2000
    fun clean(body: String): String? = body.trim().takeIf { it.isNotEmpty() && it.length <= MAX_BODY }
    /** Merge realtime inserts into the list without duplicates, ordered by id. */
    fun merge(existing: List<MessageRow>, incoming: MessageRow): List<MessageRow> =
        if (incoming.id != null && existing.any { it.id == incoming.id }) existing else (existing + incoming).sortedBy { it.id ?: Long.MAX_VALUE }
}

@Serializable
data class MatchCard(
    @SerialName("match_id") val matchId: String,
    @SerialName("other_id") val otherId: String,
    @SerialName("other_name") val otherName: String,
    @SerialName("photo_path") val photoPath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
) {
    val initial get() = otherName.trim().firstOrNull()?.uppercase() ?: "?"
    companion object {
        /** New matches (no messages yet) vs ongoing chats. */
        fun split(cards: List<MatchCard>) = cards.partition { it.lastMessageAt == null }
    }
}

@Serializable data class NotificationPrefs(@SerialName("user_id") val userId: String, @SerialName("show_previews") val showPreviews: Boolean = false)
