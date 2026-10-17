package __PKG__.core.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import __PKG__.core.database.sync.SyncMetadataDao
import __PKG__.core.database.sync.SyncMetadataEntity

/**
 * App-wide database for cross-feature bookkeeping only. Each feature module owns its own database
 * (make-database), so `:core:database` never has to know feature entities.
 */
@Database(entities = [SyncMetadataEntity::class], version = 1)
abstract class CoreDatabase : RoomDatabase() {
    abstract fun syncMetadataDao(): SyncMetadataDao
}
