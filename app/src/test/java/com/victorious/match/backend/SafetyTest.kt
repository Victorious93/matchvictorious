package com.victorious.match.backend

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SafetyTest {
    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-20T10:00:00Z")

    @Test fun parsesAndValidates() {
        val s = SafetyLogic.parseLocal("2026-10-20 19:30", utc)
        assertEquals(Instant.parse("2026-10-20T19:30:00Z"), s)
        assertNull(SafetyLogic.parseLocal("tonight", utc))
        assertTrue(SafetyLogic.errors("Sam", "Cafe", s, now).isEmpty())
        assertEquals(3, SafetyLogic.errors("", "", null, now).size)
        assertTrue(SafetyLogic.errors("Sam", "Cafe", now.plusSeconds(40L * 86400), now).isNotEmpty())
    }
    @Test fun expiryWithinServerCap() {
        val s = Instant.parse("2026-10-20T19:00:00Z")
        assertEquals(Instant.parse("2026-10-21T07:00:00Z"), SafetyLogic.expiry(s))
    }
    @Test fun shareTextHasLinkAndNoAutoSend() {
        val t = SafetyLogic.shareText("Sam", "Cafe X", Instant.parse("2026-10-20T19:00:00Z"), "abc", Instant.parse("2026-10-20T22:00:00Z"), utc)
        assertTrue(t.contains("Sam") && t.contains("Cafe X") && t.endsWith(SafetyLogic.SHARE_PAGE + "abc") && t.contains("check in"))
    }
    @Test fun sos() {
        assertEquals("https://maps.google.com/?q=42.40000,-71.12000", SafetyLogic.mapsLink(42.4, -71.12))
        assertTrue(SafetyLogic.sosText(null, null).startsWith("I need help"))
    }
}
