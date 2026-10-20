package com.victorious.match.backend

import org.junit.Assert.*
import org.junit.Test

class CallsTest {
    private val c = CallRow("c", "m", "me", "video", "active")
    @Test fun videoNeedsBothSides() {
        assertFalse(CallLogic.videoVisible(c)); assertFalse(CallLogic.videoVisible(c.copy(videoOkInitiator = true)))
        assertTrue(CallLogic.videoVisible(c.copy(videoOkInitiator = true, videoOkCallee = true)))
        assertTrue(CallLogic.myVideoOk(c.copy(videoOkInitiator = true), "me")); assertFalse(CallLogic.myVideoOk(c.copy(videoOkInitiator = true), "other"))
    }
    @Test fun incomingAndOver() {
        assertTrue(CallLogic.isIncoming(c.copy(status = "ringing"), "other")); assertFalse(CallLogic.isIncoming(c.copy(status = "ringing"), "me"))
        assertTrue(CallLogic.isOver(c.copy(status = "declined"))); assertFalse(CallLogic.isOver(c))
    }
    @Test fun consentStates() {
        assertEquals(CallLogic.Consent.NONE, CallLogic.consent(emptyList(), "me"))
        assertEquals(CallLogic.Consent.MINE_ONLY, CallLogic.consent(listOf(ConsentRow("m", "me")), "me"))
        assertEquals(CallLogic.Consent.THEIRS_ONLY, CallLogic.consent(listOf(ConsentRow("m", "x")), "me"))
        assertEquals(CallLogic.Consent.BOTH, CallLogic.consent(listOf(ConsentRow("m", "x"), ConsentRow("m", "me")), "me"))
    }
    @Test fun errors() {
        assertTrue(CallLogic.friendlyError("ERROR: daily call limit reached").contains("20"))
        assertTrue(CallLogic.friendlyError("both people must allow calls").startsWith("Both"))
    }
}
