package com.victorious.match.providers

import com.victorious.match.data.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Best-effort fallback for platforms with undocumented export formats (ZIP / JSON / HTML).
 * Heuristics: arrays of objects with a text-like field are treated as messages; profile-like keys
 * fill the profile. Unknown content is summarised, never uploaded.
 */
class GenericImportProvider(override val info: PlatformInfo) : DatingProvider {
    override val capabilities = Capabilities()

    override fun parse(fileName: String, bytes: ByteArray, id: String, now: Long): ImportBundle {
        val files: Map<String, ByteArray> = if (ExportReader.isZip(bytes)) ExportReader.textEntries(bytes) else mapOf(fileName to bytes)
        if (files.isEmpty()) throw ImportException("No readable JSON/HTML/text files found")
        val acc = Acc()
        files.forEach { (name, b) ->
            val t = String(b, Charsets.UTF_8).trimStart('\uFEFF')
            val ext = name.substringAfterLast('.').lowercase()
            when {
                ext == "json" || t.trimStart().startsWith("{") || t.trimStart().startsWith("[") ->
                    runCatching { walk(if (t.trimStart().startsWith("[")) JSONArray(t) else JSONObject(t), name, acc) }
                        .onFailure { acc.notes += "$name: unreadable JSON" }
                ext == "html" || ext == "htm" || t.contains("<html", true) -> {
                    val text = htmlToText(t); acc.notes += "$name: HTML, ${text.length} chars of text"
                    acc.usage["html_files"] = (acc.usage["html_files"] ?: 0) + 1
                }
                else -> acc.usage["text_files"] = (acc.usage["text_files"] ?: 0) + 1
            }
        }
        val matches = acc.msgs.groupBy { it.matchId }.map { (k, v) -> ImportedMatch(k, null, v.firstOrNull()?.sentAt, v.size) }
        acc.notes.add(0, "Generic import of ${files.size} file(s); format not officially documented")
        return ImportBundle(id, info.id, fileName, now, acc.profile.takeIf { it != ImportedProfile() }, matches, acc.msgs, acc.usage, acc.notes)
    }

    private class Acc { var profile = ImportedProfile(); val msgs = mutableListOf<ImportedMessage>(); val usage = linkedMapOf<String, Long>(); val notes = mutableListOf<String>() }

    private val textKeys = listOf("message", "text", "body", "content", "msg")
    private val timeKeys = listOf("sent_date", "timestamp", "date", "time", "created_at", "sent_at")

    private fun walk(node: Any?, path: String, acc: Acc, depth: Int = 0) {
        if (depth > 12) return
        when (node) {
            is JSONObject -> {
                fun s(vararg k: String) = k.firstNotNullOfOrNull { node.str(it) }
                val p = acc.profile
                acc.profile = p.copy(
                    name = p.name ?: s("first_name", "full_name", "display_name").takeIf { depth <= 2 },
                    bio = p.bio ?: s("bio", "about", "about_me").takeIf { depth <= 2 },
                    birthDate = p.birthDate ?: s("birth_date", "birthday", "dob"),
                    gender = p.gender ?: s("gender").takeIf { depth <= 2 },
                )
                node.keys().forEach { k -> walk(node.opt(k), "$path/$k", acc, depth + 1) }
            }
            is JSONArray -> {
                val objs = node.objs()
                val msgLike = objs.filter { o -> textKeys.any { o.str(it) != null } }
                if (msgLike.isNotEmpty() && msgLike.size >= objs.size / 2) {
                    val conv = path.substringAfterLast('/').ifBlank { "conversation" }
                    msgLike.forEach { o ->
                        val mid = o.str("match_id") ?: o.str("conversation_id") ?: o.str("thread_id") ?: conv
                        val from = o.str("from") ?: o.str("sender") ?: o.str("author")
                        acc.msgs += ImportedMessage(mid, timeKeys.firstNotNullOfOrNull { o.str(it) }, textKeys.firstNotNullOf { o.str(it) },
                            from == null || from.equals("you", true) || from.equals("me", true))
                    }
                } else objs.forEach { walk(it, path, acc, depth + 1) }
                (0 until node.length()).map { node.opt(it) }.filter { it is JSONArray }.forEach { walk(it, path, acc, depth + 1) }
            }
            is Number -> if (depth <= 3) acc.usage[path.substringAfterLast('/')] = (acc.usage[path.substringAfterLast('/')] ?: 0) + node.toLong()
        }
    }

    companion object {
        fun htmlToText(h: String) = h.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("<[^>]+>"), " ").replace(Regex("&nbsp;"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
