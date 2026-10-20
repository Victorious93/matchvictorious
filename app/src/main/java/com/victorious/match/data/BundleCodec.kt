package com.victorious.match.data

import org.json.JSONArray
import org.json.JSONObject

/** JSON (de)serialisation for [ImportBundle]; the encrypted store writes this text. */
object BundleCodec {
    fun encode(b: ImportBundle): String = JSONObject().apply {
        put("id", b.id); put("platformId", b.platformId); put("sourceName", b.sourceName); put("importedAt", b.importedAt)
        b.profile?.let { p ->
            put("profile", JSONObject().apply {
                putOpt("name", p.name); putOpt("bio", p.bio); putOpt("birthDate", p.birthDate); putOpt("gender", p.gender)
                putOpt("interestedIn", p.interestedIn); putOpt("city", p.city)
                put("jobs", JSONArray(p.jobs)); put("schools", JSONArray(p.schools))
            })
        }
        put("matches", JSONArray().apply { b.matches.forEach { m -> put(JSONObject().put("id", m.id).putOpt("displayName", m.displayName).putOpt("matchedAt", m.matchedAt).put("messageCount", m.messageCount)) } })
        put("messages", JSONArray().apply { b.messages.forEach { m -> put(JSONObject().put("matchId", m.matchId).putOpt("sentAt", m.sentAt).put("text", m.text).put("fromMe", m.fromMe)) } })
        put("usage", JSONObject().apply { b.usage.forEach { (k, v) -> put(k, v) } })
        put("notes", JSONArray(b.notes))
    }.toString()

    fun decode(s: String): ImportBundle {
        val o = JSONObject(s)
        val p = o.optJSONObject("profile")?.let {
            ImportedProfile(it.str("name"), it.str("bio"), it.str("birthDate"), it.str("gender"), it.str("interestedIn"), it.str("city"),
                it.optJSONArray("jobs").strings(), it.optJSONArray("schools").strings())
        }
        val matches = o.optJSONArray("matches").objs().map { ImportedMatch(it.getString("id"), it.str("displayName"), it.str("matchedAt"), it.optInt("messageCount")) }
        val msgs = o.optJSONArray("messages").objs().map { ImportedMessage(it.getString("matchId"), it.str("sentAt"), it.optString("text"), it.optBoolean("fromMe")) }
        val usage = o.optJSONObject("usage")?.let { u -> u.keys().asSequence().associateWith { u.optLong(it) } } ?: emptyMap()
        return ImportBundle(o.getString("id"), o.getString("platformId"), o.optString("sourceName"), o.optLong("importedAt"), p, matches, msgs, usage, o.optJSONArray("notes").strings())
    }
}

internal fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) optString(k).takeIf { it.isNotBlank() } else null
internal fun JSONArray?.objs(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
internal fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { i ->
    when (val v = opt(i)) { is String -> v; is JSONObject -> v.str("name") ?: v.str("title") ?: v.str("displayed"); null -> null; else -> v.toString() }
}
