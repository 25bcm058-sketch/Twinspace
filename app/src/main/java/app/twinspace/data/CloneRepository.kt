package app.twinspace.data

import android.content.Context
import android.content.pm.PackageManager
import app.twinspace.data.db.CloneDao
import app.twinspace.data.db.CloneEntity
import app.twinspace.data.db.CloneState
import app.twinspace.engine.CloneEnvironment
import app.twinspace.engine.DeviceIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class CloneRepository(
    private val dao: CloneDao,
    private val context: Context,
) {
    fun observeClones(): Flow<List<CloneEntity>> = dao.observeAll()

    suspend fun count(): Int = dao.count()

    suspend fun getById(id: String): CloneEntity? = dao.getById(id)

    /**
     * Creates the clone *record* and virtual identity only. The on-disk
     * environment is lazy — created on first launch (ARCHITECTURE.md §10).
     */
    suspend fun createClone(packageName: String, label: String, badgeColor: Int): CloneEntity =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val identity = DeviceIdentity.generate()
            val clone = CloneEntity(
                id = id,
                packageName = packageName,
                label = label.ifBlank { defaultLabelFor(packageName) },
                badgeColor = badgeColor,
                iconPath = null,
                createdAt = System.currentTimeMillis(),
                lastLaunchedAt = 0L,
                state = CloneState.READY,
                locked = false,
                androidId = identity.androidId,
                advertisingId = identity.advertisingId,
                processSuffix = ":clone_${id.replace("-", "").take(8)}",
            )
            dao.insert(clone)
            identity.persist(CloneEnvironment(context, id).identityFile)
            clone
        }

    suspend fun rename(id: String, newLabel: String) {
        dao.getById(id)?.let { dao.update(it.copy(label = newLabel)) }
    }

    suspend fun setLocked(id: String, locked: Boolean) {
        dao.getById(id)?.let { dao.update(it.copy(locked = locked)) }
    }

    suspend fun setClipboardSharing(id: String, allowed: Boolean) {
        dao.getById(id)?.let { dao.update(it.copy(clipboardSharing = allowed)) }
    }

    suspend fun recordLaunch(id: String) = dao.recordLaunch(id, System.currentTimeMillis())

    /** Wipes exactly this clone's sandbox subtree, then removes the record. */
    suspend fun deleteClone(id: String) = withContext(Dispatchers.IO) {
        CloneEnvironment(context, id).wipe()
        dao.deleteById(id)
    }

    /** Reset = wipe data, keep the clone (fresh install feeling, same identity). */
    suspend fun resetClone(id: String) = withContext(Dispatchers.IO) {
        CloneEnvironment(context, id).wipeDataOnly()
    }

    suspend fun staleClones(beforeMillis: Long): List<CloneEntity> = dao.staleClones(beforeMillis)

    private fun defaultLabelFor(packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}
