package app.twinspace.security

import android.content.Context

/**
 * Session lock state for the launcher and per-clone locks (ARCHITECTURE.md §11).
 * In-memory only: process death = locked again, which is the desired behavior.
 */
class LockManager(context: Context) {

    private val prefs = context.getSharedPreferences("twinspace_lock_prefs", Context.MODE_PRIVATE)

    @Volatile private var launcherUnlockedAt: Long = 0L
    private val sessionUnlockedClones = mutableSetOf<String>()

    val lockTimeoutMillis: Long
        get() = prefs.getLong(KEY_TIMEOUT, DEFAULT_TIMEOUT_MS)

    fun setLockTimeout(millis: Long) = prefs.edit().putLong(KEY_TIMEOUT, millis).apply()

    fun unlockLauncher() {
        launcherUnlockedAt = System.currentTimeMillis()
    }

    fun lockNow() {
        launcherUnlockedAt = 0L
        sessionUnlockedClones.clear()
    }

    fun isLauncherLocked(pinEnabled: Boolean): Boolean {
        if (!pinEnabled) return false
        return System.currentTimeMillis() - launcherUnlockedAt > lockTimeoutMillis
    }

    fun unlockClone(cloneId: String) {
        sessionUnlockedClones += cloneId
    }

    fun isCloneUnlockedThisSession(cloneId: String): Boolean = cloneId in sessionUnlockedClones

    private companion object {
        const val KEY_TIMEOUT = "lock_timeout_ms"
        const val DEFAULT_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
