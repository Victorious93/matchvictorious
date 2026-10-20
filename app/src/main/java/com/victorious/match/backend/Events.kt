package com.victorious.match.backend

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

@Serializable data class EventItem(val id: String, val title: String, val description: String? = null, val city: String, val venue: String,
    @SerialName("starts_at") val startsAt: String, @SerialName("ends_at") val endsAt: String, val capacity: Int, val going: Int = 0,
    @SerialName("i_am_going") val iAmGoing: Boolean = false) {
    val spotsLeft get() = (capacity - going).coerceAtLeast(0)
}
@Serializable data class NewEvent(val title: String, val description: String?, val city: String, val venue: String,
    @SerialName("starts_at") val startsAt: String, @SerialName("ends_at") val endsAt: String, val capacity: Int, @SerialName("created_by") val createdBy: String)
@Serializable data class RsvpRow(@SerialName("event_id") val eventId: String, @SerialName("user_id") val userId: String)
@Serializable data class EventMessage(val id: Long? = null, @SerialName("event_id") val eventId: String, @SerialName("sender_id") val senderId: String,
    val body: String, @SerialName("created_at") val createdAt: String? = null)

object EventLogic {
    fun errors(title: String, city: String, venue: String, start: Instant?, end: Instant?, capacity: Int?, now: Instant = Instant.now()) = buildList {
        if (title.trim().length !in 3..100) add("Title must be 3–100 characters")
        if (city.isBlank()) add("Pick a city")
        if (venue.trim().length !in 2..200) add("Venue must be 2–200 characters")
        if (start == null || end == null) add("Enter start and end as YYYY-MM-DD HH:MM")
        else { if (!end.isAfter(start)) add("End must be after start"); if (start.isBefore(now)) add("Start must be in the future") }
        if (capacity == null || capacity !in 2..500) add("Capacity must be 2–500")
    }
    fun rsvpError(msg: String?) = when {
        msg == null -> "RSVP failed"
        "verified" in msg -> "Only verified members can RSVP."
        "full" in msg -> "This event is full."
        "over" in msg -> "This event is over."
        else -> msg.lineSequence().first().take(160)
    }
}

object EventsApi {
    private val c get() = Backend.client
    suspend fun list(city: String?): List<EventItem> = c.postgrest.rpc("list_events", buildJsonObject { put("p_city", city) }).decodeList()
    suspend fun rsvp(id: String) { c.postgrest.rpc("rsvp_event", buildJsonObject { put("p_event", id) }) }
    suspend fun cancelRsvp(id: String) { c.from("event_rsvps").delete { filter { eq("event_id", id); eq("user_id", Backend.me()!!) } } }
    /** Attendee names; RLS returns rows only if I'm a verified attendee (blocked pairs hidden). */
    suspend fun attendees(id: String): List<String> {
        val ids = c.from("event_rsvps").select { filter { eq("event_id", id) } }.decodeList<RsvpRow>().map { it.userId }
        return Backend.names(ids).values.sorted()
    }
    suspend fun create(e: NewEvent) { c.from("events").insert(e) }
    suspend fun cancel(id: String) { c.from("events").update({ set("cancelled", true) }) { filter { eq("id", id) } } }
    suspend fun messages(id: String): List<EventMessage> = c.from("event_messages").select { filter { eq("event_id", id) }; order("id", Order.ASCENDING); limit(500) }.decodeList()
    suspend fun send(id: String, body: String) { val t = body.trim().take(1000); if (t.isNotEmpty()) c.from("event_messages").insert(EventMessage(eventId = id, senderId = Backend.me()!!, body = t)) }
    fun live(id: String): Flow<EventMessage> { val ch = c.channel("event-$id")
        return ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "event_messages"; filter("event_id", FilterOperator.EQ, id) }
            .map { it.decodeRecord<EventMessage>() }.onStart { ch.subscribe() } }
    suspend fun leave(id: String) { c.realtime.removeChannel(c.channel("event-$id")) }
}
