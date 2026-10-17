# make-api templates

The examples use the feature `orders` (package `com.acme.shop.feature.orders`) and the aggregate `Order`. Everything in the feature is `internal` (android-architecture §6).

## Contents
1. DTOs (shared)
2. R — Retrofit service
3. K — Ktor routes + Api class
4. Domain model + mappers (shared)
5. Remote data source (R and K)
6. Hilt wiring
7. S — Special cases

## 1. DTOs: `data/remote/OrderDto.kt`
```kotlin
package com.acme.shop.feature.orders.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OrderDto(
    @SerialName("id") val id: Long,
    @SerialName("number") val number: String,
    @SerialName("status") val status: OrderStatusDto = OrderStatusDto.PENDING,
    @SerialName("total_cents") val totalCents: Long,
    @SerialName("currency") val currency: String,
    @SerialName("notes") val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
internal enum class OrderStatusDto {
    @SerialName("pending") PENDING,
    @SerialName("paid") PAID,
    @SerialName("shipped") SHIPPED,
    @SerialName("cancelled") CANCELLED,
}

@Serializable
internal data class CreateOrderRequestDto(
    @SerialName("product_ids") val productIds: List<Long>,
    @SerialName("notes") val notes: String? = null,
)

/** Envelope for paginated lists: { "items": [...], "page": 1, "total_pages": 4 } */
@Serializable
internal data class PageDto<T>(
    @SerialName("items") val items: List<T>,
    @SerialName("page") val page: Int,
    @SerialName("total_pages") val totalPages: Int,
)
```
`NetworkJson` ignores unknown keys and coerces bad enum values to the default, so a new server field or status never crashes the app.

## 2. R — Retrofit service: `data/remote/OrdersApi.kt`
```kotlin
package com.acme.shop.feature.orders.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Raw HTTP calls of the orders feature. Only OrdersRemoteDataSourceImpl uses it. */
internal interface OrdersApi {

    @GET("orders")
    suspend fun getOrders(
        @Query("page") page: Int,
        @Query("status") status: String? = null, // null query params are omitted
    ): Response<PageDto<OrderDto>>

    @GET("orders/{id}")
    suspend fun getOrder(@Path("id") id: Long): Response<OrderDto>

    @POST("orders")
    suspend fun createOrder(@Body body: CreateOrderRequestDto): Response<OrderDto>

    @DELETE("orders/{id}")
    suspend fun cancelOrder(@Path("id") id: Long): Response<Unit>
}
```
- Paths are relative (no leading `/`): a leading slash would drop the `/v1/` part of the base URL.
- Always `Response<T>` (not bare `T`): `safeApiCall` maps the status code; a bare `T` turns every non-2xx into an exception.
- `@Header("X-Idempotency-Key") key: String` for per-request headers; `@QueryMap` for dynamic filters.

## 3. K — Ktor routes + Api
`data/remote/OrdersResources.kt`:
```kotlin
package com.acme.shop.feature.orders.data.remote

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

/** GET orders?page=&status=  |  GET/DELETE orders/{id} */
@Serializable
@Resource("orders")
internal class OrdersResource(val page: Int? = null, val status: String? = null) {
    @Serializable
    @Resource("{id}")
    internal class Id(val parent: OrdersResource = OrdersResource(), val id: Long)
}
```
`data/remote/OrdersApi.kt`:
```kotlin
package com.acme.shop.feature.orders.data.remote

import com.acme.shop.core.common.result.DataResult
import com.acme.shop.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import javax.inject.Inject

internal class OrdersApi @Inject constructor(private val client: HttpClient) {

    suspend fun getOrders(page: Int, status: String?): DataResult<PageDto<OrderDto>> =
        safeCall { client.get(OrdersResource(page = page, status = status)) }

    suspend fun getOrder(id: Long): DataResult<OrderDto> =
        safeCall { client.get(OrdersResource.Id(id = id)) }

    suspend fun createOrder(body: CreateOrderRequestDto): DataResult<OrderDto> =
        safeCall { client.post(OrdersResource()) { setBody(body) } }

    suspend fun cancelOrder(id: Long): DataResult<Unit> =
        safeCall { client.delete(OrdersResource.Id(id = id)) }
}
```
Import the **resources** `get/post/put/delete` (`io.ktor.client.plugins.resources.*`). `io.ktor.client.request.get` also compiles with a resource object but sends its `toString()` as the path.

## 4. Domain model and mappers
`domain/model/Order.kt`:
```kotlin
package com.acme.shop.feature.orders.domain.model

import kotlin.time.Instant

internal data class Order(
    val id: Long,
    val number: String,
    val status: OrderStatus,
    val total: Money,
    val notes: String?,
    val createdAt: Instant,
)

internal enum class OrderStatus { PENDING, PAID, SHIPPED, CANCELLED }

internal data class Money(val cents: Long, val currency: String)

/** One page of a paginated list. */
internal data class Page<T>(val items: List<T>, val page: Int, val hasMore: Boolean)
```
`data/mapper/OrderMappers.kt`:
```kotlin
package com.acme.shop.feature.orders.data.mapper

import com.acme.shop.feature.orders.data.remote.OrderDto
import com.acme.shop.feature.orders.data.remote.OrderStatusDto
import com.acme.shop.feature.orders.domain.model.Money
import com.acme.shop.feature.orders.domain.model.Order
import com.acme.shop.feature.orders.domain.model.OrderStatus
import kotlin.time.Instant

internal fun OrderDto.toDomain(): Order = Order(
    id = id,
    number = number,
    status = status.toDomain(),
    total = Money(cents = totalCents, currency = currency),
    notes = notes,
    createdAt = Instant.parse(createdAt),
)

internal fun OrderStatusDto.toDomain(): OrderStatus = when (this) {
    OrderStatusDto.PENDING -> OrderStatus.PENDING
    OrderStatusDto.PAID -> OrderStatus.PAID
    OrderStatusDto.SHIPPED -> OrderStatus.SHIPPED
    OrderStatusDto.CANCELLED -> OrderStatus.CANCELLED
}
```
`Instant.parse` throws on malformed input; `mapDto { }` converts that into `AppError.Network.Serialization`. For optional/sloppy dates use `runCatching { Instant.parse(it) }.getOrNull()` and a nullable field.

## 5. Remote data source: `data/remote/OrdersRemoteDataSource.kt`
```kotlin
package com.acme.shop.feature.orders.data.remote

import com.acme.shop.core.common.result.DataResult
import com.acme.shop.core.network.mapDto
import com.acme.shop.core.network.safeApiCall          // Retrofit only
import com.acme.shop.feature.orders.data.mapper.toDomain
import com.acme.shop.feature.orders.domain.model.Order
import com.acme.shop.feature.orders.domain.model.Page
import javax.inject.Inject

/** What the repository needs from the server, already in domain terms. */
internal interface OrdersRemoteDataSource {
    suspend fun getOrders(page: Int = 1): DataResult<Page<Order>>
    suspend fun getOrder(id: Long): DataResult<Order>
    suspend fun createOrder(productIds: List<Long>, notes: String?): DataResult<Order>
    suspend fun cancelOrder(id: Long): DataResult<Unit>
}

// Retrofit
internal class OrdersRemoteDataSourceImpl @Inject constructor(
    private val api: OrdersApi,
) : OrdersRemoteDataSource {
    override suspend fun getOrders(page: Int): DataResult<Page<Order>> =
        safeApiCall { api.getOrders(page) }.mapDto { dto ->
            Page(items = dto.items.map { it.toDomain() }, page = dto.page, hasMore = dto.page < dto.totalPages)
        }

    override suspend fun getOrder(id: Long): DataResult<Order> =
        safeApiCall { api.getOrder(id) }.mapDto { it.toDomain() }

    override suspend fun createOrder(productIds: List<Long>, notes: String?): DataResult<Order> =
        safeApiCall { api.createOrder(CreateOrderRequestDto(productIds, notes)) }.mapDto { it.toDomain() }

    override suspend fun cancelOrder(id: Long): DataResult<Unit> = safeApiCall { api.cancelOrder(id) }
}
```
Ktor: identical, without `safeApiCall` — the Api already returns `DataResult` (`api.getOrder(id).mapDto { it.toDomain() }`).

## 6. Hilt: `di/OrdersModule.kt`
```kotlin
@Module
@InstallIn(SingletonComponent::class)
internal abstract class OrdersModule {
    // tezca-android:binds
    @Binds
    abstract fun bindOrdersRemoteDataSource(impl: OrdersRemoteDataSourceImpl): OrdersRemoteDataSource

    companion object {
        // tezca-android:provides
        @Provides
        @Singleton
        fun provideOrdersApi(retrofit: Retrofit): OrdersApi = retrofit.create()   // Retrofit only (import retrofit2.create)
    }
}
```

## 7. S — Special cases

**Authentication.** `:core:network` adds `Authorization: Bearer` from `TokenProvider` (Retrofit: `AuthInterceptor` + `TokenAuthenticator`; Ktor: `Auth { bearer }`). Never add the header by hand. The session owner binds a real `TokenProvider` (make-preferences).

**Endpoints that must not carry the token** (login, register, refresh): use the `@PublicApi` client `:core:network` already provides — same base URL, no `Authorization` header, no refresh-on-401 (otherwise a 401 there triggers a token refresh, which loops or logs the user out).
- Retrofit (`di/<Feature>Module.kt` companion, under `// tezca-android:provides`):
  ```kotlin
  @Provides
  @Singleton
  fun provideAuthApi(@PublicApi retrofit: Retrofit): AuthApi = retrofit.create()   // import com.acme.shop.core.network.PublicApi
  ```
- Ktor: `internal class AuthApi @Inject constructor(@PublicApi private val client: HttpClient)`.

**401 on login means wrong credentials, not an expired session.** Translate it in the remote data source:
```kotlin
internal object AuthErrorCodes { const val INVALID_CREDENTIALS = "invalid_credentials" }

override suspend fun login(email: String, password: String): DataResult<Session> =
    when (val result = safeApiCall { api.login(LoginRequestDto(email, password)) }) {
        is DataResult.Failure if result.error == AppError.Network.Unauthorized ->
            DataResult.Failure(AppError.Domain(AuthErrorCodes.INVALID_CREDENTIALS))
        else -> result.mapDto { it.toDomain() }
    }
```
Put `AuthErrorCodes` in `domain/model/` (the ViewModel reads it). The ViewModel maps `invalid_credentials` to a feature string with the feature prefix, e.g. `R.string.onboarding_login_error_invalid_credentials`.

**Server error body → domain error.** Backend returns `{ "code": "coupon_expired", "message": "…" }` with a 4xx:
```kotlin
@Serializable internal data class ErrorBodyDto(val code: String, val message: String? = null)

internal fun <T> DataResult<T>.withDomainErrors(): DataResult<T> {
    val error = (this as? DataResult.Failure)?.error as? AppError.Network.Client ?: return this
    val body = error.body?.let { runCatching { NetworkJson.decodeFromString<ErrorBodyDto>(it) }.getOrNull() } ?: return this
    return DataResult.Failure(AppError.Domain(body.code, body.message))
}
// safeApiCall { api.redeem(RedeemRequestDto(code)) }.withDomainErrors().mapDto { it.toDomain() }
```
If every endpoint uses the same error shape, move `withDomainErrors` to `:core:network`.

**Multipart upload.**
- Retrofit:
  ```kotlin
  @Multipart
  @POST("orders/{id}/receipt")
  suspend fun uploadReceipt(@Path("id") id: Long, @Part file: MultipartBody.Part): Response<OrderDto>

  // data source
  val part = MultipartBody.Part.createFormData("file", "receipt.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
  safeApiCall { api.uploadReceipt(id, part) }
  ```
- Ktor: `client.submitFormWithBinaryData(url = "orders/$id/receipt", formData = formData { append("file", bytes, Headers.build { append(HttpHeaders.ContentType, "image/jpeg"); append(HttpHeaders.ContentDisposition, "filename=\"receipt.jpg\"") }) })` inside `safeCall`.

Read bytes from a `Uri` in the data layer (`contentResolver.openInputStream` on the injected IO dispatcher), never in the ViewModel.

**Pagination.** The data source returns `Page<T>`; offline-first paging (page 1 replaces the cache, next pages append) is make-repository's section E. For endless lists without caching, Paging 3 with a `PagingSource` over the remote data source is fine — tell the user it's online-only.

**Another host.** Retrofit: `@GET` with a full URL (`@GET("https://cdn.acme.com/catalog.json")`) or `@Url url: String`. Ktor: `client.get("https://cdn.acme.com/catalog.json")`.

**Headers on every request** (app version, locale): add an interceptor in `:core:network`'s `provideOkHttpClient` (Retrofit) or `defaultRequest { header(...) }` in `HttpClientFactory` (Ktor). It's shared infrastructure, so tell the user.
