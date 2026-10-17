# make-usecase templates

Feature `orders`, package `com.acme.shop.feature.orders.domain.usecase`.

## Observe (Flow from one repository)
```kotlin
package com.acme.shop.feature.orders.domain.usecase

import com.acme.shop.feature.orders.domain.model.Order
import com.acme.shop.feature.orders.domain.model.OrderStatus
import com.acme.shop.feature.orders.domain.repository.OrdersRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

internal class ObserveOrdersUseCase @Inject constructor(
    private val repository: OrdersRepository,
) {
    operator fun invoke(status: OrderStatus? = null): Flow<List<Order>> = repository.observeOrders(status)
}
```

## Refresh (command returning DataResult)
```kotlin
internal class RefreshOrdersUseCase @Inject constructor(
    private val repository: OrdersRepository,
) {
    suspend operator fun invoke(force: Boolean = false): DataResult<Unit> = repository.refreshOrders(force)
}
```

## Business rule + validation
```kotlin
internal object OrderErrorCodes {
    const val EMPTY_CART = "orders_empty_cart"
    const val NOTES_TOO_LONG = "orders_notes_too_long"
}

internal class PlaceOrderUseCase @Inject constructor(
    private val orders: OrdersRepository,
    private val cart: CartRepository,
) {
    suspend operator fun invoke(notes: String?): DataResult<Order> {
        val items = cart.observeCart().first()
        if (items.isEmpty()) return DataResult.Failure(AppError.Domain(OrderErrorCodes.EMPTY_CART))
        if ((notes?.length ?: 0) > MAX_NOTES) return DataResult.Failure(AppError.Domain(OrderErrorCodes.NOTES_TOO_LONG))

        return orders.createOrder(productIds = items.map { it.productId }, notes = notes?.trim()?.ifBlank { null })
            .onSuccess { cart.clear() }
    }

    private companion object {
        const val MAX_NOTES = 500
    }
}
```
The ViewModel maps `OrderErrorCodes.*` to string resources; other errors fall back to `AppError.toUiText()`.

## Combining repositories (derived data)
```kotlin
internal data class OrdersSummary(val openCount: Int, val spentThisMonth: Money)

internal class ObserveOrdersSummaryUseCase @Inject constructor(
    private val orders: OrdersRepository,
    private val clock: Clock,                                  // provide Clock.System in a Hilt module; fake in tests
) {
    operator fun invoke(): Flow<OrdersSummary> = orders.observeOrders().map { list ->
        val monthStart = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.let { LocalDate(it.year, it.month, 1) }
        OrdersSummary(
            openCount = list.count { it.status == OrderStatus.PENDING || it.status == OrderStatus.PAID },
            spentThisMonth = Money(
                cents = list.filter { it.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date >= monthStart }.sumOf { it.total.cents },
                currency = list.firstOrNull()?.total?.currency ?: "USD",
            ),
        )
    }
}
```
(`kotlinx-datetime` is not in the base catalog: add `kotlinx-datetime` when a use case needs calendar math, or keep to `kotlin.time.Instant` arithmetic.)

## Observe + refresh together (a common screen need)
Keep them as two use cases — the ViewModel observes one and triggers the other on start / pull-to-refresh. Don't hide a refresh inside an observe use case: refresh errors would have nowhere to go.

## Cross-feature data
A use case may only use repositories of its own feature or `:core:*` modules. Data owned by another feature (the signed-in user) is exposed by a use case in a `:core:*` module (`ObserveSessionUseCase` in `:core:session`).

## Testing shape (add-tests has the full template)
```kotlin
class PlaceOrderUseCaseTest {
    private val orders = FakeOrdersRepository()
    private val cart = FakeCartRepository()
    private val placeOrder = PlaceOrderUseCase(orders, cart)

    @Test
    fun `empty cart fails with a domain error and creates nothing`() = runTest {
        val result = placeOrder(notes = null)
        assertEquals(DataResult.Failure(AppError.Domain(OrderErrorCodes.EMPTY_CART)), result)
        assertTrue(orders.created.isEmpty())
    }
}
```

## SDK bridge (kmp-sdk): `core/sdk/.../di/SdkUseCaseModule.kt`
```kotlin
@Module
@InstallIn(ViewModelComponent::class)
object SdkUseCaseModule {
    // tezca-android:sdk-usecases
    @Provides
    fun provideGetSoundUseCase(): GetSoundUseCase = SetupDi.getUseCase()

    @Provides
    fun provideObservePlaylistsUseCase(): ObservePlaylistsUseCase = SetupDi.getUseCase()
}
```
Stateful SDK services:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SdkServicesModule {
    @Provides
    @Singleton
    fun provideSessionManager(): SessionManager = SetupDi.getUseCase()
}
```
Providers run lazily (when a ViewModel is created) and `SdkInitializer` runs first in `Application.onCreate`, but resolving a use case usually builds part of the SDK's graph: whatever the SDK needs then (its init, Firebase via `google-services.json`, a signed-in user) must already be in place, or the ViewModel's creation crashes. Check the SDK's DI module for what the use case pulls in. The SDK's container decides lifetimes (Koin `single`s are app-wide), whatever Hilt component the provider is in.
