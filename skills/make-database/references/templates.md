# make-database templates

Feature `orders`, aggregate `Order`, package `com.acme.shop.feature.orders`.

## Contents
1. Entity
2. DAO
3. Database
4. Mappers
5. Local data source
6. Hilt
7. Migrations
8. Relations
9. Common KSP / build errors

## 1. Entity: `data/local/OrderEntity.kt`
```kotlin
package com.acme.shop.feature.orders.data.local

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "orders",
    indices = [Index("status"), Index("created_at_epoch_ms")],
)
internal data class OrderEntity(
    @PrimaryKey val id: Long,
    val number: String,
    val status: String,                 // OrderStatus.name
    val totalCents: Long,
    val currency: String,
    val notes: String?,
    @androidx.room3.ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
)
```
Server ids are the primary key, so a refresh upserts instead of duplicating. For local-only rows use `@PrimaryKey(autoGenerate = true) val id: Long = 0`.

## 2. DAO: `data/local/OrderDao.kt`
```kotlin
package com.acme.shop.feature.orders.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface OrderDao {

    @Query("SELECT * FROM orders ORDER BY created_at_epoch_ms DESC")
    fun observeAll(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE status = :status ORDER BY created_at_epoch_ms DESC")
    fun observeByStatus(status: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :id")
    fun observeById(id: Long): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE number LIKE '%' || :query || '%' ORDER BY created_at_epoch_ms DESC")
    fun search(query: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getById(id: Long): OrderEntity?

    @Upsert
    suspend fun upsert(order: OrderEntity)

    @Upsert
    suspend fun upsertAll(orders: List<OrderEntity>)

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM orders")
    suspend fun deleteAll()

    /** Replaces the cached list atomically: observers see the old list, then the new one — never an empty gap. */
    @Transaction
    suspend fun replaceAll(orders: List<OrderEntity>) {
        deleteAll()
        upsertAll(orders)
    }
}
```
Room 3 accepts default-method bodies in `@Dao` interfaces for `@Transaction` functions. If KSP complains in your version, turn the DAO into an `abstract class` with an `open suspend fun replaceAll`.

## 3. Database: `data/local/OrdersDatabase.kt`
```kotlin
package com.acme.shop.feature.orders.data.local

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(entities = [OrderEntity::class], version = 1)
internal abstract class OrdersDatabase : RoomDatabase() {
    abstract fun orderDao(): OrderDao
}
```

## 4. Mappers: `data/mapper/OrderMappers.kt` (append to the file make-api created)
```kotlin
import com.acme.shop.feature.orders.data.local.OrderEntity
import kotlin.time.Instant

internal fun OrderEntity.toDomain(): Order = Order(
    id = id,
    number = number,
    status = OrderStatus.entries.firstOrNull { it.name == status } ?: OrderStatus.PENDING,
    total = Money(cents = totalCents, currency = currency),
    notes = notes,
    createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
)

internal fun Order.toEntity(): OrderEntity = OrderEntity(
    id = id,
    number = number,
    status = status.name,
    totalCents = total.cents,
    currency = total.currency,
    notes = notes,
    createdAtEpochMs = createdAt.toEpochMilliseconds(),
)
```
An unknown stored enum value falls back instead of crashing (it can happen after a downgrade).

## 5. Local data source: `data/local/OrdersLocalDataSource.kt`
```kotlin
package com.acme.shop.feature.orders.data.local

import com.acme.shop.core.common.result.DataResult
import com.acme.shop.core.common.result.localCall
import com.acme.shop.feature.orders.data.mapper.toDomain
import com.acme.shop.feature.orders.data.mapper.toEntity
import com.acme.shop.feature.orders.domain.model.Order
import com.acme.shop.feature.orders.domain.model.OrderStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal interface OrdersLocalDataSource {
    fun observeOrders(status: OrderStatus? = null): Flow<List<Order>>
    fun observeOrder(id: Long): Flow<Order?>
    suspend fun save(order: Order): DataResult<Unit>
    suspend fun replaceAll(orders: List<Order>): DataResult<Unit>
    suspend fun appendPage(orders: List<Order>): DataResult<Unit>
    suspend fun delete(id: Long): DataResult<Unit>
    suspend fun clear(): DataResult<Unit>
}

internal class OrdersLocalDataSourceImpl @Inject constructor(
    private val dao: OrderDao,
) : OrdersLocalDataSource {

    override fun observeOrders(status: OrderStatus?): Flow<List<Order>> =
        (if (status == null) dao.observeAll() else dao.observeByStatus(status.name))
            .map { list -> list.map { it.toDomain() } }

    override fun observeOrder(id: Long): Flow<Order?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun save(order: Order) = localCall { dao.upsert(order.toEntity()) }

    override suspend fun replaceAll(orders: List<Order>) = localCall { dao.replaceAll(orders.map { it.toEntity() }) }

    override suspend fun appendPage(orders: List<Order>) = localCall { dao.upsertAll(orders.map { it.toEntity() }) }

    override suspend fun delete(id: Long) = localCall { dao.deleteById(id) }

    override suspend fun clear() = localCall { dao.deleteAll() }
}
```
DAO calls are already off the main thread (`DatabaseFactory` sets the query dispatcher), so no `withContext` here. Heavy mapping of big lists can add `.flowOn(defaultDispatcher)` with an injected `@Dispatcher(AppDispatcher.Default)`.

## 6. Hilt: `di/OrdersModule.kt`
```kotlin
import com.acme.shop.core.database.DatabaseFactory

@Module
@InstallIn(SingletonComponent::class)
internal abstract class OrdersModule {
    // tezca-android:binds
    @Binds
    abstract fun bindOrdersLocalDataSource(impl: OrdersLocalDataSourceImpl): OrdersLocalDataSource

    companion object {
        // tezca-android:provides
        @Provides
        @Singleton
        fun provideOrdersDatabase(factory: DatabaseFactory): OrdersDatabase = factory.create("orders.db")

        @Provides
        fun provideOrderDao(database: OrdersDatabase): OrderDao = database.orderDao()
    }
}
```

## 7. Migrations
Added a column with a default → auto migration:
```kotlin
@Database(
    entities = [OrderEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
```
(The new entity property needs `@ColumnInfo(defaultValue = "…")` or must be nullable.)

Renamed/deleted column → spec:
```kotlin
@RenameColumn(tableName = "orders", fromColumnName = "notes", toColumnName = "comment")
internal class OrdersMigration2To3 : AutoMigrationSpec

autoMigrations = [AutoMigration(from = 2, to = 3, spec = OrdersMigration2To3::class)]
```

Anything else → manual migration:
```kotlin
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE orders ADD COLUMN tracking_code TEXT")
        connection.execSQL("UPDATE orders SET tracking_code = NULL")
    }
}
// provider: factory.create("orders.db") { addMigrations(MIGRATION_3_4) }
```
(`androidx.sqlite.SQLiteConnection` + `androidx.sqlite.execSQL`.) Test migrations with `MigrationTestHelper` from `room3-testing` (add-tests).

## 8. Relations (one-to-many)
```kotlin
@Entity(
    tableName = "order_lines",
    foreignKeys = [ForeignKey(entity = OrderEntity::class, parentColumns = ["id"], childColumns = ["order_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("order_id")],
)
internal data class OrderLineEntity(@PrimaryKey val id: Long, @ColumnInfo(name = "order_id") val orderId: Long, val sku: String, val quantity: Int)

internal data class OrderWithLines(
    @Embedded val order: OrderEntity,
    @Relation(parentColumn = "id", entityColumn = "order_id") val lines: List<OrderLineEntity>,
)

// DAO
@Transaction
@Query("SELECT * FROM orders WHERE id = :id")
fun observeWithLines(id: Long): Flow<OrderWithLines?>
```

## 9. Common KSP / build errors
| Error | Fix |
|---|---|
| `Unresolved reference: room3` / no generated `_Impl` | `alias(libs.plugins.tezca.android.room)` missing in the feature's `plugins {}` |
| `Schema export directory was not provided` | same: the convention plugin sets `schemaDirectory` |
| `Cannot find getter for field` | entity properties must be `val`s in the primary constructor (no private fields) |
| `There is a problem with the query: no such column` | column names are the `@ColumnInfo(name)` / property names; check after renames |
| `A migration from N to N+1 was required but not found` | bump `version` together with an `AutoMigration`/`Migration` |
| `Cannot access database on the main thread` | a DAO call isn't `suspend`/`Flow`; never use `allowMainThreadQueries()` |
