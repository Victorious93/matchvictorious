package com.victorious.match.providers

import org.junit.Assert.*
import org.junit.Test

class GenericImportProviderTest {
    private val p = GenericImportProvider(Platforms.byId("hinge")!!)

    @Test fun jsonMessagesHeuristic() {
        val json = """{"profile":{"first_name":"Alex","bio":"hi"},"matches":[{"chats":[{"body":"Hello","timestamp":"2024"},{"body":"Yo","sender":"them"}]}]}"""
        val b = p.parse("export.json", json.toByteArray(), "1", 1)
        assertEquals("Alex", b.profile!!.name)
        assertEquals(2, b.messages.size)
        assertFalse(b.messages[1].fromMe)
    }

    @Test fun htmlIsSummarised() {
        val b = p.parse("export.html", "<html><body><p>Hi</p></body></html>".toByteArray(), "1", 1)
        assertEquals(1L, b.usage["html_files"])
    }

    @Test fun registryCoversTenPlatforms() {
        assertEquals(10, ProviderRegistry.providers.size)
        assertTrue(ProviderRegistry.get("tinder") is TinderImportProvider)
        assertTrue(ProviderRegistry.providers.none { it.capabilities.canLiveSync })
    }
}
