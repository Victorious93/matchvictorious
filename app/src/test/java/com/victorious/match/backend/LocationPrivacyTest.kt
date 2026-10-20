package com.victorious.match.backend

import org.junit.Assert.*
import org.junit.Test

class LocationPrivacyTest {
    @Test fun roundsToHundredth() {
        assertEquals(42.40, LocationPrivacy.round(42.401234), 1e-9)
        assertEquals(-71.12, LocationPrivacy.round(-71.123456), 1e-9)
        assertEquals(10.01, LocationPrivacy.round(10.005001), 1e-9)
    }
    @Test fun validatesRange() {
        assertTrue(LocationPrivacy.valid(0.0, 0.0)); assertFalse(LocationPrivacy.valid(91.0, 0.0)); assertFalse(LocationPrivacy.valid(0.0, -181.0))
    }
    @Test fun distanceLabelsAreApproximate() {
        assertEquals("Less than 1 km away", LocationPrivacy.distanceLabel(1, null))
        assertEquals("About 7 km away · Boston, MA", LocationPrivacy.distanceLabel(7, "Boston, MA"))
        assertEquals("Boston, MA", LocationPrivacy.distanceLabel(null, "Boston, MA"))
        assertNull(LocationPrivacy.distanceLabel(null, null))
    }
    @Test fun prefsAreClamped() {
        val p = PrefsRow("u", minAge = 12, maxAge = 5, maxDistanceKm = 9999).clamped()
        assertEquals(18, p.minAge); assertEquals(18, p.maxAge); assertEquals(500, p.maxDistanceKm)
    }
}
