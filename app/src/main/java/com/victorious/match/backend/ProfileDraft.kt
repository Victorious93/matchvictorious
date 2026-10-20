package com.victorious.match.backend

import com.victorious.match.data.ImportedProfile
import java.time.LocalDate
import java.time.Period

/** Editable profile form state. Pure Kotlin so it is unit-testable. No phone number by design. */
data class ProfileDraft(
    val displayName: String = "",
    val birthdate: String = "",   // ISO yyyy-MM-dd
    val gender: String = "",
    val bio: String = "",
    val importedFrom: String? = null,
) {
    fun errors(today: LocalDate = LocalDate.now()): List<String> = buildList {
        if (displayName.isBlank()) add("Name is required")
        if (displayName.trim().length > MAX_NAME) add("Name must be at most $MAX_NAME characters")
        val bd = parseDate(birthdate)
        if (bd == null) add("Birthdate must be YYYY-MM-DD")
        else if (!isAdult(bd, today)) add("You must be 18 or older")
        if (bio.length > MAX_BIO) add("Bio must be at most $MAX_BIO characters")
        if (gender.length > 40) add("Gender must be at most 40 characters")
    }

    fun toRow(userId: String) = ProfileRow(
        id = userId, displayName = displayName.trim(), birthdate = parseDate(birthdate)!!.toString(),
        gender = gender.trim().ifBlank { null }, bio = bio.trim().ifBlank { null }, importedFrom = importedFrom,
    )

    companion object {
        const val MAX_NAME = 40
        const val MAX_BIO = 500

        fun isAdult(birthdate: LocalDate, today: LocalDate = LocalDate.now()) = Period.between(birthdate, today).years >= 18

        /** Accepts "1990-01-31", "1990-01-31T00:00:00.000Z" (Tinder export), etc. */
        fun parseDate(s: String?): LocalDate? = s?.trim()?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }

        /** Bootstrap from an on-device import. Only fields the user then reviews and saves are uploaded. */
        fun fromImport(p: ImportedProfile, platformId: String) = ProfileDraft(
            displayName = (p.name ?: "").trim().take(MAX_NAME),
            birthdate = parseDate(p.birthDate)?.toString() ?: "",
            gender = (p.gender ?: "").trim().take(40),
            bio = (p.bio ?: "").trim().take(MAX_BIO),
            importedFrom = platformId,
        )

        fun fromRow(r: ProfileRow) = ProfileDraft(r.displayName, r.birthdate, r.gender ?: "", r.bio ?: "", r.importedFrom)
    }
}
