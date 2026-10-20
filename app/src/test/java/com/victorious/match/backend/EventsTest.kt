package com.victorious.match.backend

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class EventsTest {
    private val now = Instant.parse("2026-10-20T10:00:00Z")
    private val s = Instant.parse("2026-10-25T19:00:00Z"); private val e = Instant.parse("2026-10-25T22:00:00Z")
    @Test fun validEvent() { assertTrue(EventLogic.errors("Board games", "Boston, MA", "Cafe", s, e, 20, now).isEmpty()) }
    @Test fun invalidEvent() {
        val errs = EventLogic.errors("x", "", "", e, s, 1, now)
        assertTrue(errs.any { "Title" in it } && errs.any { "city" in it } && errs.any { "End must" in it } && errs.any { "Capacity" in it })
        assertTrue(EventLogic.errors("Board games", "B", "Cafe", now.minusSeconds(60), e, 5, now).any { "future" in it })
    }
    @Test fun spotsAndErrors() {
        assertEquals(0, EventItem("i", "t", null, "c", "v", "", "", 3, going = 5).spotsLeft)
        assertEquals("This event is full.", EventLogic.rsvpError("ERROR: event is full"))
        assertEquals("Only verified members can RSVP.", EventLogic.rsvpError("only verified members can RSVP"))
    }
}
