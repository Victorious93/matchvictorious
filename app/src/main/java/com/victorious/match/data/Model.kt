package com.victorious.match.data

data class ImportedProfile(
    val name: String? = null,
    val bio: String? = null,
    val birthDate: String? = null,
    val gender: String? = null,
    val interestedIn: String? = null,
    val city: String? = null,
    val jobs: List<String> = emptyList(),
    val schools: List<String> = emptyList(),
)

data class ImportedMatch(val id: String, val displayName: String? = null, val matchedAt: String? = null, val messageCount: Int = 0)

data class ImportedMessage(val matchId: String, val sentAt: String?, val text: String, val fromMe: Boolean)

/** Everything parsed from one export file. Stored encrypted, on-device only. */
data class ImportBundle(
    val id: String,
    val platformId: String,
    val sourceName: String,
    val importedAt: Long,
    val profile: ImportedProfile? = null,
    val matches: List<ImportedMatch> = emptyList(),
    val messages: List<ImportedMessage> = emptyList(),
    val usage: Map<String, Long> = emptyMap(),
    val notes: List<String> = emptyList(),
)
