package com.victorious.match.data

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Stores each import as an AES-256-GCM [EncryptedFile] under app-private storage.
 * The key lives in the Android Keystore. Nothing ever leaves the device.
 */
class EncryptedStore(private val context: Context) {
    private val dir = File(context.filesDir, "imports").apply { mkdirs() }
    private val key by lazy { MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build() }

    private fun enc(f: File) = EncryptedFile.Builder(context, f, key, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()

    fun save(b: ImportBundle) {
        val f = File(dir, "${b.id}.bin"); if (f.exists()) f.delete()
        enc(f).openFileOutput().use { it.write(BundleCodec.encode(b).toByteArray()) }
    }

    fun loadAll(): List<ImportBundle> = dir.listFiles { f -> f.name.endsWith(".bin") }.orEmpty().mapNotNull { f ->
        runCatching { BundleCodec.decode(enc(f).openFileInput().use { String(it.readBytes()) }) }.getOrNull()
    }.sortedByDescending { it.importedAt }

    fun delete(id: String) { File(dir, "$id.bin").delete() }

    /** Delete All Data: imports, preferences (consent included) and caches. */
    fun deleteAll() {
        dir.deleteRecursively(); dir.mkdirs()
        context.cacheDir.deleteRecursively()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    companion object { const val PREFS = "mv_prefs" }
}
