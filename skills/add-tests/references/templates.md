# add-tests templates

Feature `orders`, package `com.acme.shop.feature.orders`. Tests live in `feature/orders/src/test/kotlin/com/acme/shop/feature/orders/…`. The feature convention plugin already adds JUnit4, kotlin-test, coroutines-test, Turbine, Robolectric, androidx.test ext, Compose UI test, `:core:testing` (MainDispatcherRule, MockWebServer or Ktor MockEngine), Hilt testing, and MockK in kmp-sdk mode.

## Contents
1. Fakes
2. ViewModel
3. Use case
4. Repository (offline-first)
5. Remote data source — Retrofit / MockWebServer
6. Remote data source — Ktor / MockEngine
7. DAO / local data source (Robolectric + in-memory Room)
8. Migration
9. Preferences data source
10. Compose screen

## 1. Fakes: `fake/FakeOrdersRepository.kt`
```kotlin
package com.acme.shop.feature.orders.fake

internal class FakeOrdersRepository(initial: List<Order> = emptyList()) : OrdersRepository {
    val orders = MutableStateFlow(initial)
    var refreshResult: DataResult<Unit> = DataResult.Success(Unit)
    var refreshCalls = 0
        private set

    override fun observeOrders(status: OrderStatus?): Flow<List<Order>> =
        orders.map { list -> if (status == null) list else list.filter { it.status == status } }

    override fun observeOrder(id: Long): Flow<Order?> = orders.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun refreshOrders(force: Boolean): DataResult<Unit> {
        refreshCalls++
        return refreshResult
    }

    override suspend fun refreshOrder(id: Long, force: Boolean): DataResult<Unit> = refreshResult

    override suspend fun createOrder(productIds: List<Long>, notes: String?): DataResult<Order> =
        DataResult.Success(order(id = 99)).also { orders.value += it.data }

    override suspend fun cancelOrder(id: Long): DataResult<Unit> {
        orders.value = orders.value.filterNot { it.id == id }
        return DataResult.Success(Unit)
    }
}

internal fun order(id: Long, status: OrderStatus = OrderStatus.PAID) = Order(
    id = id, number = "A-$id", status = status, total = Money(1000, "USD"), notes = null,
    createdAt = Instant.fromEpochMilliseconds(id * 1000),
)

internal class FakeOrdersRemoteDataSource : OrdersRemoteDataSource {
    var getOrdersResult: DataResult<Page<Order>> = DataResult.Success(Page(listOf(order(1)), page = 1, hasMore = false))
    var getOrdersCalls = 0
        private set

    override suspend fun getOrders(page: Int): DataResult<Page<Order>> { getOrdersCalls++; return getOrdersResult }
    override suspend fun getOrder(id: Long): DataResult<Order> = DataResult.Success(order(id))
    override suspend fun createOrder(productIds: List<Long>, notes: String?): DataResult<Order> = DataResult.Success(order(99))
    override suspend fun cancelOrder(id: Long): DataResult<Unit> = DataResult.Success(Unit)
}

internal class FakeOrdersLocalDataSource : OrdersLocalDataSource {
    val orders = MutableStateFlow<List<Order>>(emptyList())

    override fun observeOrders(status: OrderStatus?): Flow<List<Order>> =
        orders.map { list -> if (status == null) list else list.filter { it.status == status } }
    override fun observeOrder(id: Long): Flow<Order?> = orders.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun save(order: Order): DataResult<Unit> { orders.value = orders.value.filterNot { it.id == order.id } + order; return DataResult.Success(Unit) }
    override suspend fun replaceAll(orders: List<Order>): DataResult<Unit> { this.orders.value = orders; return DataResult.Success(Unit) }
    override suspend fun appendPage(orders: List<Order>): DataResult<Unit> { this.orders.value += orders; return DataResult.Success(Unit) }
    override suspend fun delete(id: Long): DataResult<Unit> { orders.value = orders.value.filterNot { it.id == id }; return DataResult.Success(Unit) }
    override suspend fun clear(): DataResult<Unit> { orders.value = emptyList(); return DataResult.Success(Unit) }
}
```
Fakes implement exactly the interfaces the project has — when the real interface differs from these templates, follow the real one.

**SDK use cases (kmp-sdk mode)** are usually final classes with SDK-internal constructors, so they can't be faked by subclassing: stub them with MockK (`mockk<GetSoundUseCaseV2> { every { this@mockk(any(), any()) } returns flowOf(ResultState.Success(sound)) }` — match every parameter, defaults included, with `any()`), or bridge each one behind a small app-side `fun interface` in `SdkUseCaseModule` and fake that.

## 2. ViewModel: `presentation/orderlist/OrderListViewModelTest.kt`
```kotlin
package com.acme.shop.feature.orders.presentation.orderlist

import app.cash.turbine.test
import com.acme.shop.core.common.result.AppError
import com.acme.shop.core.common.result.DataResult
import com.acme.shop.core.testing.MainDispatcherRule
import com.acme.shop.feature.orders.domain.usecase.ObserveOrdersUseCase
import com.acme.shop.feature.orders.domain.usecase.RefreshOrdersUseCase
import com.acme.shop.feature.orders.fake.FakeOrdersRepository
import com.acme.shop.feature.orders.fake.order
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.Rule

class OrderListViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val repository = FakeOrdersRepository()
    private fun viewModel() = OrderListViewModel(ObserveOrdersUseCase(repository), RefreshOrdersUseCase(repository))

    @Test
    fun `shows cached orders and refreshes on start`() = runTest(mainRule.testDispatcher) {
        repository.orders.value = listOf(order(1), order(2))
        val vm = viewModel()

        vm.state.test {
            val state = awaitItem()
            assertEquals(listOf(1L, 2L), state.orders.map { it.id })
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `refresh failure without cache shows a full-screen error`() = runTest {
        repository.refreshResult = DataResult.Failure(AppError.Network.NoInternet)
        viewModel().state.test {
            assertNotNull(expectMostRecentItem().error)
        }
    }

    @Test
    fun `refresh failure with cache keeps the list and emits a message`() = runTest {
        repository.orders.value = listOf(order(1))
        repository.refreshResult = DataResult.Failure(AppError.Network.Timeout)
        val vm = viewModel()

        vm.events.test {
            vm.state.test { cancelAndIgnoreRemainingEvents() }   // subscribing starts the refresh
            vm.onAction(OrderListAction.Refresh)
            assertIs<OrderListEvent.ShowMessage>(awaitItem())
        }
        assertEquals(null, vm.state.value.error)
    }

    @Test
    fun `clicking an order navigates to its detail`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onAction(OrderListAction.OrderClicked(7))
            assertEquals(OrderListEvent.NavigateToDetail(7), awaitItem())
        }
    }
}
```
With `UnconfinedTestDispatcher` (the rule's default) launches run eagerly, which keeps ViewModel tests simple. Pitfall: a `StateFlow` only emits when the value **changes** — if an action leaves the state equal to the current one, `awaitItem()` times out; assert on `expectMostRecentItem()` / `state.value` instead. Use `StandardTestDispatcher()` + `advanceUntilIdle()` when the test must observe intermediate states (e.g. `isSubmitting = true`).

## 3. Use case
```kotlin
class PlaceOrderUseCaseTest {
    private val orders = FakeOrdersRepository()
    private val cart = FakeCartRepository()
    private val placeOrder = PlaceOrderUseCase(orders, cart)

    @Test
    fun `empty cart fails with a domain error`() = runTest {
        assertEquals(DataResult.Failure(AppError.Domain(OrderErrorCodes.EMPTY_CART)), placeOrder(notes = null))
    }

    @Test
    fun `successful order clears the cart`() = runTest {
        cart.items.value = listOf(CartItem(productId = 1, quantity = 1))
        assertIs<DataResult.Success<Order>>(placeOrder(notes = "  "))
        assertTrue(cart.items.value.isEmpty())
    }
}
```

## 4. Repository (offline-first)
Fakes for both data sources and a `SyncMetadataStore` over an in-memory `CoreDatabase` with a controllable clock (Robolectric):
```kotlin
@RunWith(AndroidJUnit4::class)
class OrdersRepositoryImplTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val coreDb = Room.inMemoryDatabaseBuilder<CoreDatabase>(ApplicationProvider.getApplicationContext<Context>())
        .applyDefaults(mainRule.testDispatcher).build()
    private var now = Instant.fromEpochMilliseconds(0)
    private val clock = object : Clock { override fun now() = now }
    private val remote = FakeOrdersRemoteDataSource()
    private val local = FakeOrdersLocalDataSource()
    private val repository = OrdersRepositoryImpl(remote, local, SyncMetadataStore(coreDb.syncMetadataDao(), clock), mainRule.testDispatcher)

    @After fun tearDown() = coreDb.close()

    @Test
    fun `fresh cache skips the network until the ttl expires`() = runTest(mainRule.testDispatcher) {
        repository.refreshOrders()
        repository.refreshOrders()
        assertEquals(1, remote.getOrdersCalls)

        now += 16.minutes
        repository.refreshOrders()
        assertEquals(2, remote.getOrdersCalls)
    }

    @Test
    fun `refresh failure keeps the cache and is reported`() = runTest(mainRule.testDispatcher) {
        local.orders.value = listOf(order(1))
        remote.getOrdersResult = DataResult.Failure(AppError.Network.NoInternet)

        assertEquals(DataResult.Failure(AppError.Network.NoInternet), repository.refreshOrders(force = true))
        assertEquals(listOf(order(1)), repository.observeOrders().first())
    }
}
```
(`CoreDatabase` and `applyDefaults` come from `:core:database`; add `testImplementation(projects.core.database)` only if the feature doesn't already depend on it — the feature convention plugin adds it in native mode.)

## 5. Retrofit API / remote data source with MockWebServer
```kotlin
class OrdersRemoteDataSourceTest {
    private val server = MockWebServer()
    private lateinit var dataSource: OrdersRemoteDataSource

    @BeforeTest
    fun setUp() {
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/v1/"))
            .addConverterFactory(NetworkJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<OrdersApi>()
        dataSource = OrdersRemoteDataSourceImpl(api)
    }

    @AfterTest fun tearDown() = server.close()

    @Test
    fun `getOrder calls the right path and parses a real payload`() = runTest {
        server.enqueue(MockResponse(body = """{"id":7,"number":"A-7","status":"shipped","total_cents":2599,"currency":"USD","created_at":"2026-01-02T10:00:00Z"}"""))

        val result = dataSource.getOrder(7)

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v1/orders/7", request.url.encodedPath)
        assertEquals(OrderStatus.SHIPPED, (result as DataResult.Success).data.status)
    }

    @Test
    fun `createOrder sends the body`() = runTest {
        server.enqueue(MockResponse(code = 201, body = """{"id":1,"number":"A-1","total_cents":0,"currency":"USD","created_at":"2026-01-02T10:00:00Z"}"""))
        dataSource.createOrder(listOf(1, 2), notes = null)
        val body = server.takeRequest().body?.utf8()
        assertEquals("""{"product_ids":[1,2],"notes":null}""", body)
    }
}
```
(`mockwebserver3.MockResponse`/`MockWebServer`; the base URL path `/v1/` checks that endpoint paths are relative.)

## 6. Ktor API with MockEngine
```kotlin
class OrdersApiTest {
    private val config = AppConfig(baseUrl = "https://api.test/v1/", isDebug = false, flavor = "dev")

    @Test
    fun `getOrder calls the right path`() = runTest {
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respond("""{"id":7,"number":"A-7","total_cents":1,"currency":"USD","created_at":"2026-01-02T10:00:00Z"}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = OrdersApi(HttpClientFactory.create(engine, config, tokenProvider = null))

        assertIs<DataResult.Success<OrderDto>>(api.getOrder(7))
        assertEquals("/v1/orders/7", path)
    }
}
```

## 7. DAO / local data source (Robolectric + in-memory Room)
```kotlin
@RunWith(AndroidJUnit4::class)
class OrdersLocalDataSourceTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = Room.inMemoryDatabaseBuilder<OrdersDatabase>(ApplicationProvider.getApplicationContext<Context>())
        .applyDefaults(dispatcher).build()
    private val local = OrdersLocalDataSourceImpl(db.orderDao())

    @After fun tearDown() = db.close()

    @Test
    fun `replaceAll swaps the list and observers see it`() = runTest(dispatcher) {
        local.observeOrders().test {
            assertEquals(emptyList(), awaitItem())
            local.replaceAll(listOf(order(1), order(2)))
            assertEquals(listOf(2L, 1L), awaitItem().map { it.id })          // newest first
            local.replaceAll(listOf(order(3)))
            assertEquals(listOf(3L), awaitItem().map { it.id })
        }
    }

    @Test
    fun `filter by status`() = runTest(dispatcher) {
        local.replaceAll(listOf(order(1, OrderStatus.PAID), order(2, OrderStatus.CANCELLED)))
        assertEquals(listOf(2L), local.observeOrders(OrderStatus.CANCELLED).first().map { it.id })
    }
}
```

## 8. Migration
```kotlin
@RunWith(AndroidJUnit4::class)
class OrdersMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-test"),
        driver = AndroidSQLiteDriver(),
        databaseClass = OrdersDatabase::class,
    )

    @Test
    fun `1 to 2 keeps orders`() = runTest {
        helper.createDatabase(1).apply {
            execSQL("INSERT INTO orders VALUES (1, 'A-1', 'PAID', 100, 'USD', NULL, 0)")
            close()
        }
        helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2)).close()
    }
}
```
Room's testing helper reads the JSON schemas from `schemas/`: add `sourceSets["test"].assets.srcDir("$projectDir/schemas")` in the feature's `android {}` block. Check the exact `MigrationTestHelper` constructor of the room3-testing version in use.

## 9. Preferences data source
```kotlin
class OrdersPreferencesDataSourceTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `filter is stored and cleared`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = TestScope(testScheduler)) { File(tmp.root, "test.preferences_pb") }
        val prefs = OrdersPreferencesDataSourceImpl(dataStore)

        prefs.setLastStatusFilter(OrderStatus.PAID)
        assertEquals(OrderStatus.PAID, prefs.lastStatusFilter.first())
        prefs.setLastStatusFilter(null)
        assertEquals(null, prefs.lastStatusFilter.first())
    }
}
```

## 10. Compose screen (Robolectric)
```kotlin
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class OrderListScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `clicking a row sends OrderClicked`() {
        val actions = mutableListOf<OrderListAction>()
        compose.setContent {
            AppTheme(dynamicColor = false) {
                OrderListScreen(state = OrderListUiState(orders = listOf(order(1)), isLoading = false), onAction = { actions += it })
            }
        }
        compose.onNodeWithText("Order A-1").performClick()
        assertEquals(listOf<OrderListAction>(OrderListAction.OrderClicked(1)), actions)
    }

    @Test
    fun `empty state is shown`() {
        compose.setContent { AppTheme(dynamicColor = false) { OrderListScreen(OrderListUiState(isLoading = false), onAction = {}) } }
        compose.onNodeWithText("You have no orders yet.").assertIsDisplayed()
    }
}
```
Robolectric uses the default (English) resources, so text matchers use the `values/` strings. Add `@GraphicsMode(GraphicsMode.Mode.NATIVE)` if a test needs real rendering (screenshots).
