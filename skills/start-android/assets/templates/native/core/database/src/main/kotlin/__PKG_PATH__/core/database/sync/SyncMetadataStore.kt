package __PKG__.core.database.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Freshness bookkeeping for offline-first repositories: refresh from the network only when the
 * cached copy is older than its time-to-live, or when the caller forces it.
 */
@Singleton
class SyncMetadataStore(
    private val dao: SyncMetadataDao,
    private val clock: Clock,
) {
    @Inject constructor(dao: SyncMetadataDao) : this(dao, Clock.System)

    suspend fun isStale(key: String, ttl: Duration): Boolean {
        val last = dao.get(key)?.lastSyncedAtEpochMs ?: return true
        return clock.now().toEpochMilliseconds() - last >= ttl.inWholeMilliseconds
    }

    suspend fun markSynced(key: String) {
        dao.upsert(SyncMetadataEntity(key = key, lastSyncedAtEpochMs = clock.now().toEpochMilliseconds()))
    }

    suspend fun invalidate(key: String) = dao.delete(key)

    /** Call on logout so the next user doesn't see "fresh" data that belonged to someone else. */
    suspend fun clear() = dao.clear()
}
