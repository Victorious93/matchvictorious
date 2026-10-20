package com.victorious.match.backend

import com.victorious.match.data.ImportedProfile
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ProfileDraftTest {
    private val today = LocalDate.of(2026, 10, 20)

    @Test fun adultBoundary() {
        assertTrue(ProfileDraft.isAdult(LocalDate.of(2008, 10, 20), today))
        assertFalse(ProfileDraft.isAdult(LocalDate.of(2008, 10, 21), today))
    }

    @Test fun parsesTinderStyleDates() {
        assertEquals(LocalDate.of(1990, 1, 31), ProfileDraft.parseDate("1990-01-31T00:00:00.000Z"))
        assertNull(ProfileDraft.parseDate("31/01/1990"))
        assertNull(ProfileDraft.parseDate(null))
    }

    @Test fun minorIsRejected() {
        val e = ProfileDraft("Sam", "2010-01-01").errors(today)
        assertTrue(e.any { "18" in it })
    }

    @Test fun validDraftHasNoErrorsAndMapsToRow() {
        val d = ProfileDraft(" Sam ", "1995-05-05", "", " hi ")
        assertTrue(d.errors(today).isEmpty())
        val r = d.toRow("u1")
        assertEquals("Sam", r.displayName); assertEquals("hi", r.bio); assertNull(r.gender); assertNull(r.verificationStatus)
    }

    @Test fun importBootstrapTruncatesAndTags() {
        val d = ProfileDraft.fromImport(ImportedProfile(name = "A".repeat(60), bio = "b".repeat(900), birthDate = "1990-02-03T00:00:00Z", gender = "F"), "tinder")
        assertEquals(40, d.displayName.length); assertEquals(500, d.bio.length)
        assertEquals("1990-02-03", d.birthdate); assertEquals("tinder", d.importedFrom)
    }

    @Test fun matchOtherAndChatMerge() {
        val m = MatchRow("m", "a", "b")
        assertEquals("b", m.other("a")); assertEquals("a", m.other("b"))
        val l = listOf(MessageRow(1, "m", "a", "x"))
        assertEquals(1, Chat.merge(l, MessageRow(1, "m", "a", "x")).size)
        assertEquals(listOf(1L, 2L), Chat.merge(l, MessageRow(2, "m", "b", "y")).map { it.id })
        assertNull(Chat.clean("   ")); assertEquals("hi", Chat.clean(" hi "))
        assertNull(Chat.clean("x".repeat(2001)))
    }
}
