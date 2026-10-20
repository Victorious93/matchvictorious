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
data class Candidate(val id: String, @SerialName("display_name") val displayName: String, val age: Int, val bio: String? = null, @SerialName("distance_km") val distanceKm: Double? = null)

@Serializable
data class SwipeRow(@SerialName("swiper_id") val swiperId: String, @SerialName("target_id") val targetId: String, val liked: Boolean)

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
