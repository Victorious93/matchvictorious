package com.victorious.match.providers

import com.victorious.match.data.ImportBundle

/** What a provider is allowed to do. Live sync is always false: no platform offers a public API. */
data class Capabilities(val canImport: Boolean = true, val canDeepLink: Boolean = true) {
    val canLiveSync: Boolean get() = false
}

data class PlatformInfo(
    val id: String,
    val displayName: String,
    val packageName: String,
    /** Official data-request page (see research.md). */
    val requestDataUrl: String,
    val requestSteps: String,
    val expectedFormat: String,
)

interface DatingProvider {
    val info: PlatformInfo
    val capabilities: Capabilities
    /** Parse an export picked by the user. Runs entirely on-device. */
    fun parse(fileName: String, bytes: ByteArray, id: String, now: Long): ImportBundle
}

class ImportException(msg: String) : Exception(msg)
