package app.twinspace.engine

import android.content.Context
import app.twinspace.data.db.CloneEntity

/**
 * Placeholder backend used when neither the virtualization engine core has been
 * linked (Phase 1, ARCHITECTURE.md §13) nor the Work Profile backend is available.
 *
 * Design rule: fail loudly and clearly, never crash, never half-launch.
 */
class StubVirtualEngine(private val context: Context) : VirtualEngine {

    override val backendName = "none"

    override fun isOperational() = false

    override suspend fun install(clone: CloneEntity, env: CloneEnvironment): Result<Unit> =
        Result.failure(notLinked())

    override suspend fun launch(clone: CloneEntity, env: CloneEnvironment): Result<Unit> =
        Result.failure(notLinked())

    override suspend fun stop(clone: CloneEntity): Result<Unit> = Result.success(Unit)

    override fun isRunning(clone: CloneEntity) = false

    override suspend fun uninstall(clone: CloneEntity, env: CloneEnvironment): Result<Unit> =
        Result.success(Unit)

    private fun notLinked() = EngineNotOperationalException(
        "No engine backend available on this device. Link the virtualization " +
            "engine core (docs/ARCHITECTURE.md §13) or activate the Work Profile " +
            "backend via device/profile owner."
    )
}
