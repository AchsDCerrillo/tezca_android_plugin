package __PKG__.core.database.sync

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import __PKG__.core.database.CoreDatabase
import __PKG__.core.database.applyDefaults

/** Pattern for every DAO / local data source test: Robolectric + an in-memory Room database. */
@RunWith(AndroidJUnit4::class)
class SyncMetadataStoreTest {

    private val dispatcher = StandardTestDispatcher()
    private val database = Room.inMemoryDatabaseBuilder<CoreDatabase>(ApplicationProvider.getApplicationContext<Context>())
        .applyDefaults(dispatcher)
        .build()
    private var now = Instant.fromEpochMilliseconds(1_000_000)
    private val clock = object : Clock {
        override fun now() = now
    }
    private val store = SyncMetadataStore(database.syncMetadataDao(), clock)

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun `resource is stale until synced and again after its ttl`() = runTest(dispatcher) {
        assertTrue(store.isStale("orders.list", ttl = 15.minutes))

        store.markSynced("orders.list")
        assertFalse(store.isStale("orders.list", ttl = 15.minutes))

        now += 16.minutes
        assertTrue(store.isStale("orders.list", ttl = 15.minutes))
    }

    @Test
    fun `invalidate forces the next refresh`() = runTest(dispatcher) {
        store.markSynced("orders.list")
        store.invalidate("orders.list")
        assertTrue(store.isStale("orders.list", ttl = 15.minutes))
    }
}
