package com.victorious.match.ui

enum class Screen(val key: String, val title: String) {
    Profiles("profiles", "Profiles"), Matches("matches", "Matches"), Messages("messages", "Messages"),
    Filters("filters", "Filters"), Import("import", "Import"), Guide("guide", "Request your data"),
    Help("help", "Help"), Settings("settings", "Settings"), More("more", "More");
    companion object { fun fromKey(k: String?) = entries.firstOrNull { it.key == k && it != More } }
}
