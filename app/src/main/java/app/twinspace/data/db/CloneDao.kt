package app.twinspace.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CloneDao {

    @Query("SELECT * FROM clones ORDER BY lastLaunchedAt DESC")
    fun observeAll(): Flow<List<CloneEntity>>

    @Query("SELECT * FROM clones WHERE id = :id")
    suspend fun getById(id: String): CloneEntity?

    @Query("SELECT COUNT(*) FROM clones")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(clone: CloneEntity)

    @Update
    suspend fun update(clone: CloneEntity)

    @Query("DELETE FROM clones WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE clones SET lastLaunchedAt = :at WHERE id = :id")
    suspend fun recordLaunch(id: String, at: Long)

    @Query("SELECT * FROM clones WHERE lastLaunchedAt < :before")
    suspend fun staleClones(before: Long): List<CloneEntity>
}
