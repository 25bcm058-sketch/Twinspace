package app.twinspace.di

import android.content.Context
import androidx.room.Room
import app.twinspace.data.CloneRepository
import app.twinspace.data.db.AppDatabase
import app.twinspace.engine.CloneLauncher
import app.twinspace.engine.StubVirtualEngine
import app.twinspace.engine.VirtualEngine
import app.twinspace.engine.WorkProfileEngine
import app.twinspace.security.KeystoreManager
import app.twinspace.security.LockManager
import app.twinspace.security.PinManager

/** Hand-rolled DI: small enough that Hilt would be overhead. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val keystore = KeystoreManager()
    val pinManager = PinManager(appContext, keystore)
    val lockManager = LockManager(appContext)

    private val db: AppDatabase =
        Room.databaseBuilder(appContext, AppDatabase::class.java, "twinspace.db")
            .fallbackToDestructiveMigration()
            .build()

    val cloneRepository = CloneRepository(db.cloneDao(), appContext)

    /**
     * Backend selection (ARCHITECTURE.md §1):
     * virtualization engine when linked, Work Profile when this app holds
     * profile/device owner, stub otherwise (clear error, never a crash).
     */
    val engine: VirtualEngine = when {
        WorkProfileEngine.isAvailable(appContext) -> WorkProfileEngine(appContext)
        else -> StubVirtualEngine(appContext)
    }

    val cloneLauncher = CloneLauncher(appContext, cloneRepository, engine, lockManager)
}
