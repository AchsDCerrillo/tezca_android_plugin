package __PKG__.core.database.sync

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/** When a cached resource (e.g. "orders.list", "orders.item:42") was last refreshed from the server. */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val key: String,
    val lastSyncedAtEpochMs: Long,
)
