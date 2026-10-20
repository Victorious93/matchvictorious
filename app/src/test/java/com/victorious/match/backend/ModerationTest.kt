package com.victorious.match.backend

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ModerationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun reasonRules() {
        assertNotNull(ModerationLogic.reasonError(true, "  "))
        assertNull(ModerationLogic.reasonError(false, ""))
        assertNull(ModerationLogic.reasonError(true, "blurry selfie"))
        assertNotNull(ModerationLogic.reasonError(false, "x".repeat(401)))
    }

    @Test fun deletedAccountReportUsesSnapshot() {
        val r = json.decodeFromString<ModReport>("""{"id":"r1","reported_id":null,"reason":"scam","status":"open","snapshot":{"display_name":"Bo","bio":null}}""")
        assertEquals("Bo (deleted account)", r.displayTarget)
        assertTrue(ModerationLogic.canAct(r, "dismiss")); assertFalse(ModerationLogic.canAct(r, "ban"))
    }

    @Test fun liveAccountReport() {
        val r = json.decodeFromString<ModReport>("""{"id":"r1","reported_id":"u","reason":"spam","status":"open","reported_name":"Al","reported_banned":false,"reported_warnings":2}""")
        assertEquals("Al", r.displayTarget); assertTrue(ModerationLogic.canAct(r, "warn"))
    }

    @Test fun statusDecodes() {
        val s = json.decodeFromString<ModStatus>("""{"verification_status":"rejected","is_banned":false,"warnings":0,"note":"Verification rejected: blurry"}""")
        assertEquals("rejected", s.verificationStatus); assertFalse(s.isBanned)
    }
}
