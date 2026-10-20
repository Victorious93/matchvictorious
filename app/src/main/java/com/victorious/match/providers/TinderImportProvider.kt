package com.victorious.match.providers

import com.victorious.match.data.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses Tinder's self-serve export (account.gotinder.com/download): a ZIP containing data.json,
 * or data.json itself. Every section is optional; unknown fields are ignored.
 */
class TinderImportProvider(override val info: PlatformInfo = Platforms.byId("tinder")!!) : DatingProvider {
    override val capabilities = Capabilities()

    override fun parse(fileName: String, bytes: ByteArray, id: String, now: Long): ImportBundle {
        val json = if (ExportReader.isZip(bytes)) {
            val entries = ExportReader.textEntries(bytes)
            entries.entries.firstOrNull { it.key.substringAfterLast('/').equals("data.json", true) }?.value
                ?: throw ImportException("data.json not found in ZIP")
        } else bytes
        return parseJson(String(json, Charsets.UTF_8), fileName, id, now)
    }

    fun parseJson(text: String, fileName: String, id: String, now: Long): ImportBundle {
        val root = try { JSONObject(text.trimStart('\uFEFF')) } catch (e: Exception) { throw ImportException("Not valid Tinder JSON") }
        val notes = mutableListOf<String>()
        val user = root.optJSONObject("User")
        val profile = user?.let { u ->
            ImportedProfile(
                name = u.str("full_name") ?: u.str("name") ?: u.str("username"),
                bio = u.str("bio"), birthDate = u.str("birth_date"), gender = u.str("gender"),
                interestedIn = u.str("interested_in") ?: u.str("gender_filter"),
                city = u.optJSONObject("city")?.str("name") ?: u.optJSONObject("pos_info")?.optJSONObject("city")?.str("name"),
                jobs = u.optJSONArray("jobs").jobs(), schools = u.optJSONArray("schools").strings(),
            )
        } ?: run { notes += "No User section"; null }

        val matches = mutableListOf<ImportedMatch>(); val msgs = mutableListOf<ImportedMessage>()
        root.optJSONArray("Messages").objs().forEachIndexed { i, conv ->
            val mid = conv.str("match_id") ?: "Match ${i + 1}"
            val list = conv.optJSONArray("messages").objs()
            list.forEach { m ->
                val text = m.str("message") ?: m.str("text") ?: return@forEach
                // In Tinder exports every message is the exporter's own side; "from" may be absent.
                val from = m.str("from")
                msgs += ImportedMessage(mid, m.str("sent_date") ?: m.str("timestamp"), text, from == null || from.equals("You", true) || from == "0")
            }
            matches += ImportedMatch(mid, null, list.firstOrNull()?.str("sent_date"), list.size)
        }
        if (!root.has("Messages")) notes += "No Messages section"

        val usage = linkedMapOf<String, Long>()
        root.optJSONObject("Usage")?.let { u -> u.keys().forEach { k -> usage[k] = sumSection(u.opt(k)) } } ?: run { notes += "No Usage section" }
        // Matches exist even without messages: use Usage.matches total if larger.
        val usageMatches = usage["matches"] ?: 0
        if (usageMatches > matches.size) notes += "Usage reports $usageMatches matches; ${matches.size} have conversations"
        listOf("Photos", "Spotify", "Purchases", "Campaigns", "Experiences", "StreamingData").filter { root.has(it) }.forEach { notes += "Section $it present (not imported)" }

        return ImportBundle(id, info.id, fileName, now, profile, matches, msgs, usage, notes)
    }

    private fun sumSection(v: Any?): Long = when (v) {
        is Number -> v.toLong()
        is JSONObject -> v.keys().asSequence().sumOf { sumSection(v.opt(it)) }
        is JSONArray -> (0 until v.length()).sumOf { sumSection(v.opt(it)) }
        else -> 0L
    }

    private fun JSONArray?.jobs(): List<String> = objs().mapNotNull { j ->
        val t = j.optJSONObject("title")?.str("name") ?: j.str("title"); val c = j.optJSONObject("company")?.str("name") ?: j.str("company")
        listOfNotNull(t, c).joinToString(" @ ").takeIf { it.isNotBlank() }
    }.ifEmpty { strings() }
}
