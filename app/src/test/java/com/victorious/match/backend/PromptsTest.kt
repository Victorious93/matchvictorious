package com.victorious.match.backend

import org.junit.Assert.*
import org.junit.Test

class PromptsTest {
    @Test fun promptRules() {
        assertTrue(PromptRules.errors(listOf("a" to "x", null to "", "b" to "y")).isEmpty())
        assertTrue(PromptRules.errors(listOf("a" to "x", "a" to "y")).any { "once" in it })
        assertTrue(PromptRules.errors(listOf("a" to " ")).any { "Answer" in it })
        assertTrue(PromptRules.errors(listOf("a" to "z".repeat(201))).any { "200" in it })
        assertTrue(PromptRules.errors(listOf("a" to "1", "b" to "2", "c" to "3", "d" to "4")).any { "at most" in it })
    }
    @Test fun voiceLimits() {
        assertTrue(PromptRules.voiceOk(30.0)); assertFalse(PromptRules.voiceOk(30.1)); assertFalse(PromptRules.voiceOk(0.2))
    }
    @Test fun icebreakers() {
        val s = Icebreakers.suggest(listOf(CandidatePrompt("travel", "The last place I travelled was", "Lisbon"),
            CandidatePrompt("green_flag", "A green flag I look for", "Kindness to waiters")))
        assertEquals(2, s.size); assertTrue(s[0].contains("Lisbon")); assertTrue(s[1].contains("Kindness"))
        assertEquals(1, Icebreakers.suggest(emptyList()).size)
        assertTrue(Icebreakers.suggest(listOf(CandidatePrompt("geek_out", "p", "a".repeat(80))))[0].contains("…"))
    }
}
