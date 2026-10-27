package com.victorious.match.theme

import org.junit.Assert.*
import org.junit.Test

class FleetThemeCodecTest {
    private val pal = """{"background":"#FF0B0C12","surface":"#FF161821","primary":"#FF00E5FF","secondary":"#FFE840FF","onBackground":"#FFF5F7FF","muted":"#FF9AA3B5","danger":"#FFFF6B8A","stale":"#FFFFC14D","tile":"#CC1A1D2B"}"""
    private fun json(v: Int = 1, mode: String = "NEON_FLEET", p: String = pal) =
        """{"schemaVersion":$v,"mode":"$mode","iconStyle":"SYSTEM","iconPackPackage":"","updatedAtMs":5,"palette":$p,"future":1}"""

    @Test fun parsesSpecExample() {
        val s = FleetThemeCodec.parseJson(json())!!
        assertEquals("NEON_FLEET", s.mode); assertEquals(5L, s.updatedAtMs)
        assertEquals(0xFF00E5FFL, s.palette.primary); assertEquals(0xCC1A1D2BL, s.palette.tile)
    }
    @Test fun rejectsOtherSchema() { assertNull(FleetThemeCodec.parseJson(json(v = 2))); assertNull(FleetThemeCodec.parseJson(json(v = 0))) }
    @Test fun rejectsUnknownMode() = assertNull(FleetThemeCodec.parseJson(json(mode = "HACKER")))
    @Test fun rejectsBadOrMissingPalette() {
        assertNull(FleetThemeCodec.parseJson(json(p = pal.replace("#FF00E5FF", "red"))))
        assertNull(FleetThemeCodec.parseJson(json(p = """{"primary":"#00E5FF"}""")))
        assertNull(FleetThemeCodec.parseJson("not json")); assertNull(FleetThemeCodec.parseJson(null))
    }
    @Test fun hex() {
        assertEquals(0xFF00E5FFL, FleetThemeCodec.parseHex("#00E5FF"))
        assertEquals(0x8000E5FFL, FleetThemeCodec.parseHex("#8000e5ff"))
        assertNull(FleetThemeCodec.parseHex("00E5FF")); assertNull(FleetThemeCodec.parseHex("#12345")); assertNull(FleetThemeCodec.parseHex("#GG0000"))
    }
    @Test fun rowFallsBackToColumnsAndIgnoresUnknown() {
        val cols = mutableMapOf<String, String?>("schemaVersion" to "1", "mode" to "DARK", "json" to "broken", "extra" to "x")
        FleetThemeCodec.PALETTE_KEYS.forEach { cols[it] = "#112233" }
        assertEquals("DARK", FleetThemeCodec.parseRow(cols)!!.mode)
        cols["schemaVersion"] = "2"; assertNull(FleetThemeCodec.parseRow(cols))
    }
    @Test fun pinCheck() {
        val p = FleetThemeCodec.PROVIDER_PACKAGE
        assertFalse(FleetThemeCodec.isTrustedProvider("evil.app", listOf("aa"), emptySet()))
        assertTrue(FleetThemeCodec.isTrustedProvider(p, listOf("aa"), emptySet()))
        assertTrue(FleetThemeCodec.isTrustedProvider(p, listOf("AA:BB"), setOf("aabb")))
        assertFalse(FleetThemeCodec.isTrustedProvider(p, listOf("ccdd"), setOf("aabb")))
        assertFalse(FleetThemeCodec.isTrustedProvider(p, emptyList(), setOf("aabb")))
    }
}
