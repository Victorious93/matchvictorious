package com.victorious.match.providers

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Reads ZIP exports in memory. Caps entry size so a hostile ZIP cannot exhaust memory. */
object ExportReader {
    const val MAX_ENTRY = 64L * 1024 * 1024
    const val MAX_TOTAL = 256L * 1024 * 1024

    fun isZip(b: ByteArray) = b.size > 3 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()

    /** Returns text-like entries (json/html/htm/txt/csv) keyed by path. Media is skipped. */
    fun textEntries(zip: ByteArray): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>(); var total = 0L
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val n = e.name
                if (e.isDirectory || n.contains("..") || !n.substringAfterLast('.').lowercase().let { it in setOf("json", "html", "htm", "txt", "csv") }) continue
                val buf = java.io.ByteArrayOutputStream(); val tmp = ByteArray(8192); var read = 0L
                while (true) { val r = z.read(tmp); if (r < 0) break; read += r; if (read > MAX_ENTRY) throw ImportException("Entry too large: $n"); buf.write(tmp, 0, r) }
                total += read; if (total > MAX_TOTAL) throw ImportException("Export too large")
                out[n] = buf.toByteArray()
            }
        }
        return out
    }
}
