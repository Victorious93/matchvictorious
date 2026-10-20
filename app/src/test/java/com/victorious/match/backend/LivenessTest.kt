package com.victorious.match.backend

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class LivenessTest {
    private fun f(n: Int = 1, yaw: Float = 0f, smile: Float? = 0f, l: Float? = 0.9f, r: Float? = 0.9f) = FaceFrame(n, yaw, smile, l, r)
    @Test fun turns() {
        assertTrue(LivenessLogic.detects("turn_left", f(yaw = -30f), false)); assertFalse(LivenessLogic.detects("turn_left", f(yaw = 30f), false))
        assertTrue(LivenessLogic.detects("turn_right", f(yaw = 30f), false)); assertFalse(LivenessLogic.detects("turn_right", f(yaw = 10f), false))
    }
    @Test fun smileAndBlink() {
        assertTrue(LivenessLogic.detects("smile", f(smile = 0.95f), false)); assertFalse(LivenessLogic.detects("smile", f(smile = null), false))
        assertFalse("blink needs open eyes first", LivenessLogic.detects("blink", f(l = 0.1f, r = 0.1f), false))
        assertTrue(LivenessLogic.detects("blink", f(l = 0.1f, r = 0.1f), true))
        assertTrue(LivenessLogic.eyesOpen(f()))
    }
    @Test fun multipleOrNoFacesNeverPass() {
        LivenessLogic.gestures.forEach { assertFalse(LivenessLogic.detects(it, f(n = 2, yaw = 40f, smile = 1f, l = 0f, r = 0f), true)) }
        assertFalse(LivenessLogic.frontal(f(n = 0))); assertTrue(LivenessLogic.frontal(f(yaw = 5f)))
    }
    @Test fun resultHasOnlyBooleans() {
        val r = LivenessLogic.result(mapOf("smile" to true, "landmarks" to true), true)
        assertEquals(setOf("smile", "single_face"), r.keys); assertTrue(r.values.all { it.jsonPrimitive.booleanOrNull != null })
    }
    @Test fun selfiePathInOwnFolder() { assertTrue(LivenessLogic.selfiePath("u1").matches(Regex("u1/[0-9a-f-]{36}\\.jpg"))) }
    @Test fun errors() {
        assertEquals("Too many attempts today. Try again tomorrow.", LivenessLogic.friendlyError("ERROR: too many attempts, try again tomorrow"))
        assertEquals("Please accept the biometric consent first.", LivenessLogic.friendlyError("biometric consent required"))
    }
    @Test fun consentVersioned() { assertTrue(BIOMETRIC_CONSENT_VERSION.length in 1..40) }
}
