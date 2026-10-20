package com.victorious.match.backend

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.minutes

@Serializable data class PromptOption(val id: String, val text: String)
@Serializable data class ProfilePromptRow(@SerialName("user_id") val userId: String, val slot: Int, @SerialName("prompt_id") val promptId: String, val answer: String)
@Serializable data class CandidatePrompt(@SerialName("prompt_id") val promptId: String, val prompt: String, val answer: String)
@Serializable data class VoiceIntroRow(@SerialName("user_id") val userId: String, @SerialName("storage_path") val storagePath: String,
    @SerialName("duration_s") val durationS: Double, @SerialName("moderation_status") val moderationStatus: String = "pending",
    @SerialName("rejection_reason") val rejectionReason: String? = null)
@Serializable data class PendingVoice(@SerialName("user_id") val userId: String, @SerialName("display_name") val displayName: String,
    @SerialName("storage_path") val storagePath: String, @SerialName("duration_s") val durationS: Double, @SerialName("created_at") val createdAt: String? = null)

object PromptRules {
    const val SLOTS = 3
    const val MAX_ANSWER = 200
    const val MAX_COMMENT = 300
    const val MAX_VOICE_S = 30.0
    /** Validates up to 3 (promptId, answer) picks: distinct prompts, non-blank answers within limits. */
    fun errors(picks: List<Pair<String?, String>>): List<String> = buildList {
        val chosen = picks.filter { it.first != null }
        if (chosen.size > SLOTS) add("Pick at most $SLOTS prompts")
        if (chosen.map { it.first }.toSet().size != chosen.size) add("Each prompt can be used once")
        chosen.forEach { (_, a) -> if (a.isBlank()) add("Answer every chosen prompt") else if (a.length > MAX_ANSWER) add("Answers must be at most $MAX_ANSWER characters") }
    }.distinct()
    fun voiceOk(seconds: Double) = seconds > 0.5 && seconds <= MAX_VOICE_S
}

/** Suggested openers built from the other person's prompts (purely local, nothing is sent automatically). */
object Icebreakers {
    fun suggest(prompts: List<CandidatePrompt>, max: Int = 3): List<String> = prompts.take(max).map { p ->
        val a = p.answer.trim().let { if (it.length > 40) it.take(37) + "…" else it }
        when (p.promptId) {
            "two_truths" -> "My guess for the lie: … am I right?"
            "first_date" -> "\"$a\" sounds fun. When are we doing it?"
            "geek_out", "song" -> "Okay, tell me more about $a!"
            "travel" -> "$a! What was the highlight?"
            "comfort_food" -> "Where's the best $a around here?"
            "unpopular_opinion", "hot_take" -> "Bold take: \"$a\". Defend it 😄"
            else -> "I liked your answer to \"${p.prompt}\": $a. Tell me more?"
        }
    }.ifEmpty { listOf("Hi! What's been the best part of your week?") }
}

object PromptsApi {
    private val c get() = Backend.client
    suspend fun catalog(): List<PromptOption> = c.from("prompt_catalog").select { filter { eq("active", true) }; order("text", Order.ASCENDING) }.decodeList()
    suspend fun mine(): List<ProfilePromptRow> = c.from("profile_prompts").select { filter { eq("user_id", Backend.me()!!) } }.decodeList()
    suspend fun of(user: String, catalog: List<PromptOption>): List<CandidatePrompt> {
        val names = catalog.associate { it.id to it.text }
        return c.from("profile_prompts").select { filter { eq("user_id", user) }; order("slot", Order.ASCENDING) }.decodeList<ProfilePromptRow>()
            .map { CandidatePrompt(it.promptId, names[it.promptId] ?: it.promptId, it.answer) }
    }
    /** Replace my prompts with [picks] (slot order). */
    suspend fun save(picks: List<Pair<String, String>>) {
        val me = Backend.me()!!
        c.from("profile_prompts").delete { filter { eq("user_id", me) } }
        if (picks.isNotEmpty()) c.from("profile_prompts").insert(picks.mapIndexed { i, (p, a) -> ProfilePromptRow(me, i + 1, p, a.trim()) })
    }
    suspend fun myVoice(): VoiceIntroRow? = c.from("voice_intros").select { filter { eq("user_id", Backend.me()!!) } }.decodeSingleOrNull()
    suspend fun uploadVoice(bytes: ByteArray, seconds: Double) {
        require(PromptRules.voiceOk(seconds)) { "Voice intro must be up to 30 seconds" }
        val me = Backend.me()!!
        val path = "$me/${java.util.UUID.randomUUID()}.m4a"
        c.storage.from("voice").upload(path, bytes) { contentType = io.ktor.http.ContentType.parse("audio/mp4") }
        myVoice()?.let { old -> deleteVoiceRow(); runCatching { c.storage.from("voice").delete(old.storagePath) } }
        c.from("voice_intros").insert(VoiceIntroRow(me, path, (seconds * 10).toInt() / 10.0))
    }
    private suspend fun deleteVoiceRow() { c.from("voice_intros").delete { filter { eq("user_id", Backend.me()!!) } } }
    suspend fun deleteVoice() { myVoice()?.let { deleteVoiceRow(); runCatching { c.storage.from("voice").delete(it.storagePath) } } }
    suspend fun signedVoiceUrl(path: String): String? = runCatching { c.storage.from("voice").createSignedUrl(path, 10.minutes) }.getOrNull()
}
