package app.twinspace.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.twinspace.TwinSpaceApp
import app.twinspace.engine.CloneEnvironment
import java.util.concurrent.TimeUnit

/**
 * Daily maintenance (ARCHITECTURE.md §10): trims cache/code_cache of clones not
 * launched in 7+ days. Never touches files/, shared_prefs/, databases/ — user
 * data and sessions are sacred (§9).
 */
class MaintenanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as TwinSpaceApp
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        app.container.cloneRepository.staleClones(cutoff).forEach { clone ->
            CloneEnvironment(applicationContext, clone.id).trimCache()
        }
        return Result.success()
    }
}
