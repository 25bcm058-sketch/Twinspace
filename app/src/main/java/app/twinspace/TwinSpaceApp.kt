package app.twinspace

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.twinspace.di.AppContainer
import app.twinspace.work.MaintenanceWorker
import java.util.concurrent.TimeUnit

class TwinSpaceApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleMaintenance()
    }

    /** Daily idle-time cache trim for clones unused 7+ days (ARCHITECTURE.md §10). */
    private fun scheduleMaintenance() {
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "clone-maintenance",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS).build()
        )
    }
}
