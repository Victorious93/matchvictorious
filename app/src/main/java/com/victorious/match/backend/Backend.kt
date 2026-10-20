package com.victorious.match.backend

import com.victorious.match.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** Thin wrapper over supabase-kt. All authorization is enforced server-side by RLS. */
object Backend {
    val client by lazy {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
            install(Auth); install(Postgrest); install(Realtime); install(Storage); install(Functions)
        }
    }
    val auth get() = client.auth
    fun me(): String? = auth.currentUserOrNull()?.id

    suspend fun signUp(email: String, password: String) = auth.signUpWith(Email) { this.email = email; this.password = password }
    suspend fun signIn(email: String, password: String) = auth.signInWith(Email) { this.email = email; this.password = password }
    suspend fun signOut() { runCatching { com.victorious.match.push.Push.unregister() }; auth.signOut() }

    suspend fun myProfile(): ProfileRow? = me()?.let { id ->
        client.from("profiles").select(io.github.jan.supabase.postgrest.query.Columns.list(*PROFILE_COLS)) { filter { eq("id", id) } }.decodeSingleOrNull<ProfileRow>()
    }
    suspend fun saveProfile(d: ProfileDraft) {
        val id = me() ?: error("Not signed in")
        client.from("profiles").upsert(d.toRow(id).copy(verificationStatus = null)) { }
    }
    suspend fun uploadPhoto(bytes: ByteArray, ext: String, position: Int) {
        val id = me() ?: error("Not signed in")
        val path = "$id/${UUID.randomUUID()}.$ext"
        client.storage.from("photos").upload(path, bytes)
        client.from("photos").insert(PhotoRow(id, path, position))
    }

    suspend fun discover(limit: Int = 20): List<Candidate> =
        client.postgrest.rpc("discover", buildJsonObject { put("p_limit", limit) }).decodeList()
    suspend fun swipe(target: String, liked: Boolean) =
        client.from("swipes").insert(SwipeRow(me()!!, target, liked))

    suspend fun matches(): List<MatchRow> = client.from("matches").select { filter { exact("unmatched_at", null) }; order("created_at", Order.DESCENDING) }.decodeList()
    suspend fun names(ids: List<String>): Map<String, String> = if (ids.isEmpty()) emptyMap() else
        client.from("profiles").select(io.github.jan.supabase.postgrest.query.Columns.list("id", "display_name", "birthdate")) { filter { isIn("id", ids) } }
            .decodeList<ProfileRow>().associate { it.id to it.displayName }
    suspend fun unmatch(matchId: String) = client.from("matches").update({ set("unmatched_at", java.time.Instant.now().toString()) }) { filter { eq("id", matchId) } }

    suspend fun messages(matchId: String): List<MessageRow> =
        client.from("messages").select { filter { eq("match_id", matchId) }; order("id", Order.ASCENDING); limit(500) }.decodeList()
    suspend fun send(matchId: String, body: String) {
        val text = Chat.clean(body) ?: return
        client.from("messages").insert(MessageRow(matchId = matchId, senderId = me()!!, body = text))
    }
    /** Realtime stream of new messages for one match (RLS applies to realtime too). */
    fun liveMessages(matchId: String): Flow<MessageRow> {
        val ch = client.channel("match-$matchId")
        return ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"; filter("match_id", FilterOperator.EQ, matchId)
        }.map { it.decodeRecord<MessageRow>() }.onStart { ch.subscribe() }
    }
    suspend fun leave(matchId: String) { client.realtime.removeChannel(client.channel("match-$matchId")) }

    suspend fun block(other: String) = client.from("blocks").insert(BlockRow(me()!!, other))
    suspend fun report(other: String, reason: String, details: String?) = client.from("reports").insert(ReportRow(me()!!, other, reason, details))

    suspend fun setLocation(lat: Double, lon: Double) {
        require(LocationPrivacy.valid(lat, lon))
        client.postgrest.rpc("set_my_location", buildJsonObject { put("lat", LocationPrivacy.round(lat)); put("lon", LocationPrivacy.round(lon)) })
    }
    suspend fun setCity(name: String) { client.postgrest.rpc("set_my_city", buildJsonObject { put("city_name", name) }) }
    suspend fun cities(): List<String> = client.from("cities").select(io.github.jan.supabase.postgrest.query.Columns.list("name")) { order("name", Order.ASCENDING) }.decodeList<CityRow>().map { it.name }

    suspend fun prefs(): PrefsRow = me()!!.let { id -> client.from("preferences").select { filter { eq("user_id", id) } }.decodeSingleOrNull<PrefsRow>() ?: PrefsRow(id) }
    suspend fun savePrefs(p: PrefsRow) { client.from("preferences").upsert(p.clamped()) }

    /** Short-lived signed URLs for approved photos (private bucket; storage RLS decides). */
    suspend fun signedUrls(paths: List<String>): List<String> = paths.mapNotNull { p ->
        runCatching { client.storage.from("photos").createSignedUrl(p, 10.minutes) }.getOrNull()
    }

    /** Server-side deletion via the delete-account Edge Function (service role lives only there). */
    suspend fun deleteAccount() {
        runCatching { com.victorious.match.push.Push.unregister() }
        client.functions.invoke("delete-account")
        runCatching { auth.signOut() }
        auth.clearSession()
    }

    suspend fun matchCards(): List<MatchCard> = client.postgrest.rpc("match_cards").decodeList()
    suspend fun registerToken(t: String) { client.postgrest.rpc("register_device_token", buildJsonObject { put("p_token", t) }) }
    suspend fun deleteToken(t: String) { client.from("device_tokens").delete { filter { eq("token", t) } } }
    suspend fun showPreviews(): Boolean = me()?.let { id ->
        client.from("notification_prefs").select { filter { eq("user_id", id) } }.decodeSingleOrNull<NotificationPrefs>()?.showPreviews } ?: false
    suspend fun setShowPreviews(v: Boolean) { client.from("notification_prefs").upsert(NotificationPrefs(me()!!, v)) }

    private val PROFILE_COLS = arrayOf("id", "display_name", "birthdate", "gender", "bio", "imported_from", "verification_status")
}
