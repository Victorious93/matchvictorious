package com.victorious.match.backend

import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable data class CallRow(
    val id: String, @SerialName("match_id") val matchId: String, @SerialName("initiator_id") val initiatorId: String,
    val kind: String = "audio", val status: String = "ringing",
    @SerialName("video_ok_initiator") val videoOkInitiator: Boolean = false, @SerialName("video_ok_callee") val videoOkCallee: Boolean = false)
@Serializable data class ConsentRow(@SerialName("match_id") val matchId: String, @SerialName("user_id") val userId: String)
@Serializable data class CallToken(val url: String = "", val token: String = "", val room: String = "", val kind: String = "audio", val disabled: Boolean = false, val error: String? = null)

object CallLogic {
    const val DAILY_LIMIT = 20
    fun videoVisible(c: CallRow) = c.videoOkInitiator && c.videoOkCallee
    fun myVideoOk(c: CallRow, me: String) = if (c.initiatorId == me) c.videoOkInitiator else c.videoOkCallee
    fun isIncoming(c: CallRow, me: String) = c.status == "ringing" && c.initiatorId != me
    fun isOver(c: CallRow) = c.status == "ended" || c.status == "declined"
    enum class Consent { NONE, MINE_ONLY, THEIRS_ONLY, BOTH }
    fun consent(rows: List<ConsentRow>, me: String): Consent {
        val mine = rows.any { it.userId == me }; val theirs = rows.any { it.userId != me }
        return when { mine && theirs -> Consent.BOTH; mine -> Consent.MINE_ONLY; theirs -> Consent.THEIRS_ONLY; else -> Consent.NONE }
    }
    fun friendlyError(msg: String?): String = when {
        msg == null -> "Call failed"
        "daily call limit" in msg -> "You've reached today's call limit ($DAILY_LIMIT)."
        "both people must allow" in msg -> "Both of you need to allow calls first."
        "call not available" in msg -> "That call is no longer available."
        else -> msg.lineSequence().first().take(160)
    }
}

class CallsDisabledException : Exception("Calls are not configured yet")

object CallsApi {
    private val c get() = Backend.client
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun consents(match: String): List<ConsentRow> = c.from("call_consents").select { filter { eq("match_id", match) } }.decodeList()
    suspend fun setConsent(match: String, allow: Boolean) {
        if (allow) c.from("call_consents").upsert(ConsentRow(match, Backend.me()!!)) { ignoreDuplicates = true }
        else c.from("call_consents").delete { filter { eq("match_id", match); eq("user_id", Backend.me()!!) } }
    }
    suspend fun start(match: String, kind: String): CallRow {
        val id = c.postgrest.rpc("start_call", buildJsonObject { put("p_match", match); put("p_kind", kind) }).decodeAs<String>()
        return get(id)!!
    }
    suspend fun get(id: String): CallRow? = c.from("call_sessions").select { filter { eq("id", id) } }.decodeSingleOrNull()
    suspend fun respond(id: String, accept: Boolean) { c.postgrest.rpc("respond_call", buildJsonObject { put("p_call", id); put("p_accept", accept) }) }
    suspend fun end(id: String) { c.postgrest.rpc("end_call", buildJsonObject { put("p_call", id) }) }
    suspend fun setVideoOk(id: String, ok: Boolean) { c.postgrest.rpc("set_video_ok", buildJsonObject { put("p_call", id); put("p_ok", ok) }) }
    suspend fun token(id: String): CallToken {
        val r = runCatching { c.functions.invoke("livekit-token", buildJsonObject { put("call_id", id) }) }
            .getOrElse { e -> if ("503" in (e.message ?: "") || "not configured" in (e.message ?: "")) throw CallsDisabledException() else throw e }
        val t = json.decodeFromString<CallToken>(r.bodyAsText())
        if (t.disabled) throw CallsDisabledException()
        if (!r.status.isSuccess() || t.token.isEmpty()) error(t.error ?: "Could not join call")
        return t
    }
    /** Inserts/updates of calls I'm part of (RLS-filtered). */
    fun live(): Flow<CallRow> {
        val ch = c.channel("calls-${Backend.me()}")
        return ch.postgresChangeFlow<PostgresAction>(schema = "public") { table = "call_sessions" }
            .mapNotNull { a -> runCatching { when (a) { is PostgresAction.Insert -> a.decodeRecord<CallRow>(); is PostgresAction.Update -> a.decodeRecord<CallRow>(); else -> null } }.getOrNull() }
            .onStart { ch.subscribe() }
    }
    suspend fun stopLive() { c.realtime.removeChannel(c.channel("calls-${Backend.me()}")) }
}
