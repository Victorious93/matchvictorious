package com.victorious.match.backend

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Serializable data class PendingProfile(val id: String, @SerialName("display_name") val displayName: String, val age: Int, val gender: String? = null,
    val bio: String? = null, val city: String? = null, @SerialName("created_at") val createdAt: String? = null, val photos: List<String> = emptyList(), val warnings: Int = 0)
@Serializable data class PendingPhoto(@SerialName("photo_id") val photoId: String, @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String, @SerialName("storage_path") val storagePath: String, @SerialName("created_at") val createdAt: String? = null)
@Serializable data class ModReport(val id: String, @SerialName("reporter_id") val reporterId: String? = null, @SerialName("reported_id") val reportedId: String? = null,
    val reason: String, val details: String? = null, val status: String, val snapshot: JsonObject? = null, @SerialName("created_at") val createdAt: String? = null,
    @SerialName("reported_name") val reportedName: String? = null, @SerialName("reported_banned") val reportedBanned: Boolean? = null,
    @SerialName("reported_warnings") val reportedWarnings: Int? = null) {
    val snapshotName get() = runCatching { snapshot?.get("display_name")?.jsonPrimitive?.content }.getOrNull()
    val displayTarget get() = reportedName ?: snapshotName?.let { "$it (deleted account)" } ?: "(deleted account)"
}
@Serializable data class ModStatus(@SerialName("verification_status") val verificationStatus: String, @SerialName("is_banned") val isBanned: Boolean,
    val warnings: Int = 0, val note: String? = null)

object ModerationLogic {
    const val MAX_REASON = 400
    /** Rejections and bans need a reason (the server enforces this too). */
    fun reasonError(required: Boolean, reason: String): String? = when {
        required && reason.isBlank() -> "A reason is required"
        reason.length > MAX_REASON -> "Reason must be at most $MAX_REASON characters"
        else -> null
    }
    fun canAct(r: ModReport, action: String) = action == "dismiss" || r.reportedId != null
}

/** All moderation runs through admin-checked security-definer RPCs; the client is never trusted. */
object ModApi {
    private val pg get() = Backend.client.postgrest
    suspend fun isAdmin(): Boolean = runCatching { pg.rpc("is_admin").decodeAs<Boolean>() }.getOrDefault(false)
    suspend fun pendingProfiles(): List<PendingProfile> = pg.rpc("mod_pending_profiles").decodeList()
    suspend fun setVerification(user: String, verified: Boolean, reason: String?) { pg.rpc("mod_set_verification", buildJsonObject { put("p_user", user); put("p_verified", verified); put("p_reason", reason) }) }
    suspend fun pendingPhotos(): List<PendingPhoto> = pg.rpc("mod_pending_photos").decodeList()
    suspend fun reviewPhoto(id: String, approve: Boolean, reason: String?) { pg.rpc("mod_review_photo", buildJsonObject { put("p_photo", id); put("p_approve", approve); put("p_reason", reason) }) }
    suspend fun reports(includeClosed: Boolean = false): List<ModReport> = pg.rpc("mod_reports", buildJsonObject { put("p_include_closed", includeClosed) }).decodeList()
    suspend fun resolve(report: String, action: String, note: String?) { pg.rpc("mod_resolve_report", buildJsonObject { put("p_report", report); put("p_action", action); put("p_note", note) }) }
    suspend fun setBan(user: String, banned: Boolean, reason: String?) { pg.rpc("mod_set_ban", buildJsonObject { put("p_user", user); put("p_banned", banned); put("p_reason", reason) }) }
    suspend fun myStatus(): ModStatus? = runCatching { pg.rpc("my_moderation_status").decodeList<ModStatus>().firstOrNull() }.getOrNull()
}
