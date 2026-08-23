package app.twinspace.engine

import android.content.Context
import java.io.File

/**
 * Per-clone sandbox subtree (ARCHITECTURE.md §4).
 *
 * Nothing here is created at clone-creation time — [ensureCreated] is called by
 * the launcher on first launch (lazy-load, §10).
 */
class CloneEnvironment(context: Context, val cloneId: String) {

    val rootDir: File = File(context.filesDir, "clones/$cloneId")
    val filesDir get() = File(rootDir, "files")
    val prefsDir get() = File(rootDir, "shared_prefs")
    val databasesDir get() = File(rootDir, "databases")
    val cacheDir get() = File(rootDir, "cache")
    val codeCacheDir get() = File(rootDir, "code_cache")
    val webViewDir get() = File(rootDir, "webview")
    val identityFile get() = File(rootDir, "identity.json")

    val isInitialized: Boolean get() = rootDir.isDirectory

    fun ensureCreated() {
        listOf(filesDir, prefsDir, databasesDir, cacheDir, codeCacheDir, webViewDir)
            .forEach { it.mkdirs() }
    }

    /** Full wipe — used by delete. */
    fun wipe() {
        rootDir.deleteRecursively()
    }

    /** Data wipe that preserves the virtual identity (used by reset). */
    fun wipeDataOnly() {
        val identity = identityFile.takeIf { it.isFile }?.readBytes()
        rootDir.deleteRecursively()
        if (identity != null) {
            rootDir.mkdirs()
            identityFile.writeBytes(identity)
        }
    }

    fun trimCache() {
        cacheDir.deleteRecursively()
        codeCacheDir.deleteRecursively()
    }

    fun sizeBytes(): Long = rootDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}
