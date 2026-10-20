package com.victorious.match.ui

enum class Screen(val key: String, val title: String) {
    Discover("discover", "Discover"), Chats("chats", "Chats"), MyProfile("me", "Profile"),
    Profiles("profiles", "Imported profiles"), Matches("matches", "Imported matches"), Messages("messages", "Imported messages"),
    Filters("filters", "Filters"), Import("import", "Import"), Guide("guide", "Request your data"),
    Help("help", "Help"), Moderation("moderation", "Moderation"), Safety("safety", "Safety center"), Events("events", "Events & group dates"), Settings("settings", "Settings"), More("more", "More");
    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.key == k && it != More } }
}
