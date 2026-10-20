package com.victorious.match.backend

import org.junit.Assert.*
import org.junit.Test

class PlusTest {
    @Test fun perksAreConvenienceOnly() {
        assertEquals(3, PlusLogic.perks.size)
        assertTrue(PlusLogic.perks.none { "boost" in it.lowercase() || "rank" in it.lowercase() || "priority" in it.lowercase() })
    }
    @Test fun statuses() {
        assertTrue(PlusLogic.isPlus("active")); assertTrue(PlusLogic.isPlus("grace")); assertTrue(PlusLogic.isPlus("canceled"))
        assertFalse(PlusLogic.isPlus("expired")); assertFalse(PlusLogic.isPlus("on_hold")); assertFalse(PlusLogic.isPlus(null))
    }
    @Test fun errors() {
        assertEquals("That's a Victorious+ feature.", PlusLogic.friendlyError("ERROR: Victorious+ required"))
        assertEquals("No recent pass to rewind.", PlusLogic.friendlyError("nothing to rewind"))
        assertEquals("Victorious+ isn't available yet.", PlusLogic.friendlyError("HTTP 503"))
    }
    @Test fun prefsDefaultsOff() { val p = PrefsRow("u"); assertFalse(p.onlyWithVoice); assertFalse(p.onlyWithPrompts) }
}
