package app.twinspace.engine

import android.content.Context
import app.twinspace.data.CloneRepository
import app.twinspace.data.db.CloneEntity
import app.twinspace.security.LockManager

/**
 * Single entry point for launching a clone. Enforces, in order:
 *  1. per-clone lock (caller must have unlocked via LockManager this session)
 *  2. lazy environment init (first launch creates the sandbox subtree)
 *  3. engine install (first launch) then launch
 *  4. launch bookkeeping
 */
class CloneLauncher(
    private val context: Context,
    private val repository: CloneRepository,
    private val engine: VirtualEngine,
    private val lockManager: LockManager,
) {

    sealed class LaunchOutcome {
        data object Success : LaunchOutcome()
        /** Clone is locked; UI should route to the lock screen first. */
        data object Locked : LaunchOutcome()
        data class Failure(val cause: Throwable) : LaunchOutcome()
    }

    suspend fun launch(clone: CloneEntity): LaunchOutcome {
        if (clone.locked && !lockManager.isCloneUnlockedThisSession(clone.id)) {
            return LaunchOutcome.Locked
        }

        val env = CloneEnvironment(context, clone.id)
        val firstLaunch = !env.isInitialized
        if (firstLaunch) env.ensureCreated()

        val result = if (firstLaunch) {
            engine.install(clone, env).fold(
                onSuccess = { engine.launch(clone, env) },
                onFailure = { Result.failure(it) },
            )
        } else {
            engine.launch(clone, env)
        }

        return result.fold(
            onSuccess = {
                repository.recordLaunch(clone.id)
                LaunchOutcome.Success
            },
            onFailure = { LaunchOutcome.Failure(it) },
        )
    }
}
