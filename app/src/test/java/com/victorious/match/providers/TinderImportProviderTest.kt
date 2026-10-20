package com.victorious.match.providers

import com.victorious.match.data.BundleCodec
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TinderImportProviderTest {
    private val fixture = javaClass.classLoader!!.getResource("tinder_data.json")!!.readBytes()
    private val p = TinderImportProvider()

    @Test fun parsesProfile() {
        val b = p.parse("data.json", fixture, "id1", 42)
        assertEquals("tinder", b.platformId)
        assertEquals("Synthetic Sam", b.profile!!.name)
        assertEquals("Testville", b.profile!!.city)
        assertEquals(listOf("Engineer @ Acme"), b.profile!!.jobs)
        assertEquals(listOf("Test University"), b.profile!!.schools)
    }

    @Test fun parsesMessagesTolerantly() {
        val b = p.parse("data.json", fixture, "id1", 42)
        assertEquals(3, b.matches.size)
        assertEquals(3, b.messages.size) // message without text is skipped
        assertTrue(b.messages.all { it.fromMe })
        assertEquals("Match 3", b.messages.last().matchId)
    }

    @Test fun sumsUsage() {
        val u = p.parse("data.json", fixture, "id1", 42).usage
        assertEquals(7L, u["app_opens"]); assertEquals(10L, u["swipes_likes"]); assertEquals(6L, u["matches"])
    }

    @Test fun readsZip() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("index.html")); z.write("<html></html>".toByteArray())
            z.putNextEntry(ZipEntry("json/data.json")); z.write(fixture); z.putNextEntry(ZipEntry("photos/a.jpg")); z.write(ByteArray(10)) }
        assertEquals("Synthetic Sam", p.parse("myData.zip", out.toByteArray(), "z", 1).profile!!.name)
    }

    @Test fun emptyObjectIsTolerated() {
        val b = p.parse("data.json", "{}".toByteArray(), "e", 1)
        assertNull(b.profile); assertTrue(b.messages.isEmpty()); assertTrue(b.notes.isNotEmpty())
    }

    @Test(expected = ImportException::class) fun rejectsGarbage() { p.parse("x", "not json".toByteArray(), "g", 1) }

    @Test fun codecRoundTrip() {
        val b = p.parse("data.json", fixture, "id1", 42)
        assertEquals(b, BundleCodec.decode(BundleCodec.encode(b)))
    }
}
