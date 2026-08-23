package app.twinspace.engine

import app.twinspace.data.db.CloneEntity

/**
 * The seam between TwinSpace and whatever actually runs the clone
 * (ARCHITECTURE.md §1, §13).
 *
 * Implementations:
 *  - [WorkProfileEngine] — OS-supported, functional today when profile/device owner.
 *  - Virtualization engine core — Phase 1 integration target.
 *  - [StubVirtualEngine] — explicit "not linked" state; never crashes.
 */
interface VirtualEngine {

    val backendName: String

    /** True if this backend can actually launch clones on this device right now. */
    fun isOperational(): Boolean

    /**
     * Prepares the clone for first launch: resolves the source APK (in place —
     * never copied, §12) and initializes the environment.
     */
    suspend fun install(clone: CloneEntity, env: CloneEnvironment): Result<Unit>

    /** Launches the clone. Must be safe to call from the UI thread. */
    suspend fun launch(clone: CloneEntity, env: CloneEnvironment): Result<Unit>

    /** Stops the clone's process if running. */
    suspend fun stop(clone: CloneEntity): Result<Unit>

    fun isRunning(clone: CloneEntity): Boolean

    /** Engine-side cleanup before the environment subtree is wiped. */
    suspend fun uninstall(clone: CloneEntity, env: CloneEnvironment): Result<Unit>
}

class EngineNotOperationalException(message: String) : Exception(message)
