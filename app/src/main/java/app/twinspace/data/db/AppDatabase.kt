package app.twinspace.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CloneEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cloneDao(): CloneDao
}
