package com.victorious.match.backend

import com.victorious.match.push.PushLogic
import org.junit.Assert.*
import org.junit.Test

class AvatarsPushTest {
    private fun card(id: String, last: String?, name: String = "Bo") = MatchCard(id, "o$id", name, lastMessageAt = last)

    @Test fun splitsNewMatchesFromChats() {
        val (fresh, chats) = MatchCard.split(listOf(card("1", null), card("2", "2026-10-20T01:00:00Z"), card("3", null)))
        assertEquals(listOf("1", "3"), fresh.map { it.matchId }); assertEquals(listOf("2"), chats.map { it.matchId })
    }
    @Test fun initialFallback() {
        assertEquals("B", card("1", null, " bo").initial); assertEquals("?", card("1", null, "  ").initial)
    }
    @Test fun genericTextHasNoContent() {
        assertEquals("You have a new match", PushLogic.fallbackText("match"))
        assertEquals("New message", PushLogic.fallbackText("message"))
        assertEquals("MatchVictorious", PushLogic.fallbackText(null))
    }
    @Test fun availabilityRequiresConfigAndFirebaseApp() {
        assertFalse(PushLogic.isAvailable(false, 1)); assertFalse(PushLogic.isAvailable(true, 0)); assertTrue(PushLogic.isAvailable(true, 1))
        assertTrue(PushLogic.needsRuntimePermission(33)); assertFalse(PushLogic.needsRuntimePermission(32))
    }
}
