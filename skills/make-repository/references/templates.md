# make-repository templates

Feature `orders`, domain model `Order`, data sources from make-api and make-database.

## Interface: `domain/repository/OrdersRepository.kt`
```kotlin
package com.acme.shop.feature.orders.domain.repository

import com.acme.shop.core.common.result.DataResult
import com.acme.shop.feature.orders.domain.model.Order
import com.acme.shop.feature.orders.domain.model.OrderStatus
import kotlinx.coroutines.flow.Flow

internal interface OrdersRepository {
    fun observeOrders(status: OrderStatus? = null): Flow<List<Order>>
    fun observeOrder(id: Long): Flow<Order?>
    suspend fun refreshOrders(force: Boolean = false): DataResult<Unit>
    suspend fun refreshOrder(id: Long, force: Boolean = false): DataResult<Unit>
    suspend fun createOrder(productIds: List<Long>, notes: String?): DataResult<Order>
    suspend fun cancelOrder(id: Long): DataResult<Unit>
}
```

## A. Offline-first (default): `data/repository/OrdersRepositoryImpl.kt`
```kotlin
package com.acme.shop.feature.orders.data.repository

import com.acme.shop.core.common.dispatchers.AppDispatcher
import com.acme.shop.core.common.dispatchers.Dispatcher
import com.acme.shop.core.common.result.DataResult
import com.acme.shop.core.common.result.asEmpty
import com.acme.shop.core.common.result.flatMap
import com.acme.shop.core.common.result.onSuccess
import com.acme.shop.core.database.sync.SyncMetadataStore
import com.acme.shop.feature.orders.data.local.OrdersLocalDataSource
import com.acme.shop.feature.orders.data.remote.OrdersRemoteDataSource
import com.acme.shop.feature.orders.domain.model.Order
import com.acme.shop.feature.orders.domain.model.OrderStatus
import com.acme.shop.feature.orders.domain.repository.OrdersRepository
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Room is the single source of truth: the UI observes the database and refresh pulls from the
 * server into it. Fresh-enough data (TTL) isn't fetched again unless forced (pull-to-refresh).
 */
internal class OrdersRepositoryImpl @Inject constructor(
    private val remote: OrdersRemoteDataSource,
    private val local: OrdersLocalDataSource,
    private val syncMetadata: SyncMetadataStore,
    @param:Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher,
) : OrdersRepository {

    override fun observeOrders(status: OrderStatus?): Flow<List<Order>> = local.observeOrders(status)

    override fun observeOrder(id: Long): Flow<Order?> = local.observeOrder(id)

    override suspend fun refreshOrders(force: Boolean): DataResult<Unit> = withContext(io) {
        if (!force && !syncMetadata.isStale(LIST_KEY, LIST_TTL)) return@withContext DataResult.Success(Unit)
        remote.getOrders(page = 1)
            .flatMap { page -> local.replaceAll(page.items) }
            .onSuccess { syncMetadata.markSynced(LIST_KEY) }
    }

    override suspend fun refreshOrder(id: Long, force: Boolean): DataResult<Unit> = withContext(io) {
        val key = itemKey(id)
        if (!force && !syncMetadata.isStale(key, ITEM_TTL)) return@withContext DataResult.Success(Unit)
        remote.getOrder(id)
            .flatMap { order -> local.save(order) }
            .onSuccess { syncMetadata.markSynced(key) }
    }

    override suspend fun createOrder(productIds: List<Long>, notes: String?): DataResult<Order> = withContext(io) {
        remote.createOrder(productIds, notes).onSuccess { local.save(it) }
    }

    override suspend fun cancelOrder(id: Long): DataResult<Unit> = withContext(io) {
        remote.cancelOrder(id).onSuccess {
            local.delete(id)
            syncMetadata.invalidate(itemKey(id))
        }.asEmpty()
    }

    private companion object {
        const val LIST_KEY = "orders.list"
        fun itemKey(id: Long) = "orders.item:$id"
        val LIST_TTL = 15.minutes
        val ITEM_TTL = 5.minutes
    }
}
```
`onSuccess`/`flatMap` are `inline`, so their lambdas can call `suspend` functions.

## B. Remote-only
```kotlin
internal class PaymentsRepositoryImpl @Inject constructor(
    private val remote: PaymentsRemoteDataSource,
    @param:Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher,
) : PaymentsRepository {
    // No cache on purpose: the user asked for always-fresh data.
    override suspend fun getPlans(): DataResult<List<Plan>> = withContext(io) { remote.getPlans() }

    override suspend fun confirmPayment(intentId: String): DataResult<PaymentReceipt> =
        withContext(io) { remote.confirm(intentId) }

    /** Poll-based "live" value when the UI needs a stream and there's no socket. */
    override fun observePaymentStatus(intentId: String): Flow<DataResult<PaymentStatus>> = flow {
        while (currentCoroutineContext().isActive) {
            val result = remote.status(intentId)
            emit(result)
            if ((result as? DataResult.Success)?.data?.isFinal == true) break
            delay(3.seconds)
        }
    }.flowOn(io)
}
```
A remote-only interface exposes `suspend fun get…(): DataResult<T>` instead of `observe…` + `refresh…`.

## C. Local-only
```kotlin
internal class CartRepositoryImpl @Inject constructor(
    private val local: CartLocalDataSource,
    @param:Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher,
) : CartRepository {
    override fun observeCart(): Flow<List<CartItem>> = local.observeItems()
    override suspend fun add(item: CartItem): DataResult<Unit> = withContext(io) { local.save(item) }
    override suspend fun clear(): DataResult<Unit> = withContext(io) { local.clear() }
}
```

## D. Offline writes (only when the user asks for them)
Write locally first with a `pendingSync` flag, then try the server; unsent rows are pushed on the next refresh or by a WorkManager job.
```kotlin
override suspend fun updateNotes(id: Long, notes: String): DataResult<Unit> = withContext(io) {
    local.updateNotes(id, notes, pendingSync = true)                    // UI updates immediately
    when (val result = remote.updateNotes(id, notes)) {
        is DataResult.Success -> local.save(result.data)                // clears pendingSync
        is DataResult.Failure if result.error is AppError.Network.NoInternet -> DataResult.Success(Unit) // retried later
        is DataResult.Failure -> result
    }
}

/** Called at the start of refreshOrders() and from a periodic @HiltWorker. */
suspend fun pushPendingChanges() {
    local.getPending().forEach { pending ->
        remote.updateNotes(pending.id, pending.notes.orEmpty()).onSuccess { local.save(it) }
    }
}
```
Needs a `pending_sync` column (make-database) and an agreed conflict policy (last write wins by default). For guaranteed delivery, enqueue a unique `OneTimeWorkRequest` with a network constraint (`androidx.work` + `androidx.hilt:hilt-work`).

## E. Paginated lists (offline-first)
Page 1 replaces the cache on refresh; further pages append. The repository remembers whether more pages exist, so it is a `@Singleton`. It keeps the `observeOrder`/`refreshOrder`/commands from A unchanged; only the list functions change.
```kotlin
// imports in addition to A's:
import com.acme.shop.core.common.result.map
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface OrdersRepository {
    fun observeOrders(status: OrderStatus? = null): Flow<List<Order>>
    suspend fun refreshOrders(force: Boolean = false): DataResult<Unit>   // page 1, replaces cache
    suspend fun loadMoreOrders(): DataResult<Boolean>                      // next page; true = more remain
}

@Singleton
internal class OrdersRepositoryImpl @Inject constructor(/* … */) : OrdersRepository {
    private val paging = Mutex()
    private var nextPage = 2
    private var hasMore = true

    override suspend fun refreshOrders(force: Boolean): DataResult<Unit> = withContext(io) {
        if (!force && !syncMetadata.isStale(LIST_KEY, LIST_TTL)) return@withContext DataResult.Success(Unit)
        paging.withLock {
            remote.getOrders(page = 1).flatMap { page ->
                local.replaceAll(page.items).onSuccess {
                    nextPage = 2
                    hasMore = page.hasMore
                    syncMetadata.markSynced(LIST_KEY)
                }
            }
        }
    }

    override suspend fun loadMoreOrders(): DataResult<Boolean> = withContext(io) {
        paging.withLock {
            if (!hasMore) return@withLock DataResult.Success(false)
            remote.getOrders(page = nextPage).flatMap { page ->
                local.appendPage(page.items).map {
                    nextPage++
                    hasMore = page.hasMore
                    page.hasMore
                }
            }
        }
    }
}
```
The ViewModel keeps `isLoadingMore` / `canLoadMore` / `loadMoreError` in its state and sends `LoadMore` when the list nears its end (make-screen's list template). For huge or infinite lists, Paging 3 with a `RemoteMediator` over the same DAO is the heavier alternative — only if the user wants it.

## Hilt: `di/OrdersModule.kt`
```kotlin
// tezca-android:binds
@Binds
abstract fun bindOrdersRepository(impl: OrdersRepositoryImpl): OrdersRepository
```
