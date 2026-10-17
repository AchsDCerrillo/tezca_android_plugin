package __PKG__.core.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import __PKG__.core.common.dispatchers.AppDispatcher
import __PKG__.core.common.dispatchers.Dispatcher

/**
 * Builds Room databases with the project defaults: the platform SQLite driver and queries on the
 * injected IO dispatcher (DAO calls are main-safe). Feature modules use it from their Hilt module:
 *
 * ```
 * @Provides @Singleton
 * fun provideOrdersDatabase(factory: DatabaseFactory): OrdersDatabase = factory.create<OrdersDatabase>("orders.db")
 * ```
 */
@Singleton
class DatabaseFactory @Inject constructor(
    @param:ApplicationContext val context: Context,
    @param:Dispatcher(AppDispatcher.IO) val ioDispatcher: CoroutineDispatcher,
) {
    inline fun <reified T : RoomDatabase> create(
        name: String,
        configure: RoomDatabase.Builder<T>.() -> Unit = {},
    ): T = Room.databaseBuilder<T>(context, name)
        .apply(configure)
        .applyDefaults(ioDispatcher)
        .build()

    /** In-memory database for tests and previews. */
    inline fun <reified T : RoomDatabase> inMemory(): T =
        Room.inMemoryDatabaseBuilder<T>(context).applyDefaults(ioDispatcher).build()
}

fun <T : RoomDatabase> RoomDatabase.Builder<T>.applyDefaults(dispatcher: CoroutineDispatcher): RoomDatabase.Builder<T> =
    setDriver(AndroidSQLiteDriver()).setQueryCoroutineContext(dispatcher)
