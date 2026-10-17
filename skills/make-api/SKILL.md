---
name: make-api
description: Create or update REST API calls in a native Android app (Retrofit by default, or Ktor) and expose them as the feature's remote data source. Generates @Serializable DTOs, a Retrofit service interface (suspend + Response<T>) or a Ktor Api class, a RemoteDataSource interface + Impl that wraps calls in safeApiCall/safeCall → DataResult and maps DTOs to domain models, and the Hilt wiring. Use it whenever the user wants to call an endpoint, add or change a REST call, consume a backend/server API, map a JSON response, add query params, headers, multipart uploads or auth to a request, or "connect the feature to the server" in an Android project — even if they never say Retrofit or Ktor. Also use it when make-repository or make-screen needs a remote data source that doesn't exist yet.
---

# make-api: remote data source (Retrofit or Ktor)

Turns an endpoint description (method, path, request and response JSON) into DTOs, an API (Retrofit service or Ktor class), mappers and a remote data source. The data source returns domain models wrapped in `DataResult`, so nothing Retrofit/Ktor-specific leaves `data/remote`.

## Before you start
1. Read `../android-architecture/SKILL.md` (once per session) and `.tezca-android.json`.
   - `mode: kmp-sdk` → the app has no network layer. Tell the user the endpoint belongs in the SDK (the `tezca-kmp-development` plugin's `make-api` builds it) and stop, unless they want an app-only API: then ask before adding `:core:network` (render `start-android`'s `network-retrofit` overlay).
   - `network` = `retrofit` (default) or `ktor` picks the template section. Follow the project's existing choice even if the user mentions the other library; mention the mismatch.
2. Find the feature root `feature/<name>/src/main/kotlin/<basePath>/feature/<pkg>/`. If the feature doesn't exist, run `new-feature` first or ask which feature owns the endpoint.
3. Collect from the user, or from a curl command, Postman collection, OpenAPI file or backend code they point to: method + relative path, path/query params, request body, response body (one real example if possible), auth requirement, error responses. Don't invent fields: ask, or generate a clearly marked minimal DTO.
4. Look in `data/remote/` for an existing `<X>Api`, DTOs and remote data source. **Update in place**: add endpoints to the existing classes instead of parallel ones, keep signatures unless asked, and update every call site if you change one.

## Steps
Templates: `references/templates.md` (section R = Retrofit, K = Ktor, shared sections otherwise). Copy the shape, rename, adapt fields.

1. **DTOs** — `data/remote/<X>Dto.kt`: `@Serializable internal data class`, `@SerialName` for every JSON name that differs from the Kotlin name, nullable + default for optional fields, `@Serializable enum class` with `@SerialName` per value, separate `…RequestDto` for bodies, dates as `String` parsed in the mapper.
2. **API**
   - Retrofit: `internal interface <X>Api` with one `suspend fun` per endpoint returning `Response<Dto>` (`Response<Unit>` for no body). Paths are **relative** (`"orders/{id}"`, no leading `/`) so they resolve under `AppConfig.baseUrl`.
   - Ktor: `internal class <X>Api @Inject constructor(private val client: HttpClient)` with `@Resource` routes; each function is `safeCall<T> { client.get(Resource(...)) }` using the **resource** extensions (`io.ktor.client.plugins.resources.get`), not `io.ktor.client.request.get`.
3. **Domain model + mappers** — `domain/model/<X>.kt` (plain data class with real types: `Instant`, enums, value classes) and `data/mapper/<X>Mappers.kt` (`internal fun <X>Dto.toDomain()`, `<X>.toRequestDto()`).
4. **Remote data source** — `data/remote/<X>RemoteDataSource.kt`: interface + `internal class <X>RemoteDataSourceImpl @Inject constructor(...)`. Every function returns `DataResult<Domain>`: Retrofit `safeApiCall { api.call() }.mapDto { it.toDomain() }`, Ktor `api.call().mapDto { it.toDomain() }`. `mapDto` turns a throwing mapper into `AppError.Network.Serialization`.
5. **Hilt** — in `di/<Feature>Module.kt`:
   - Retrofit: under `// tezca-android:provides` → `@Provides @Singleton fun provide<X>Api(retrofit: Retrofit): <X>Api = retrofit.create()`.
   - both: under `// tezca-android:binds` → `@Binds abstract fun bind<X>RemoteDataSource(impl: <X>RemoteDataSourceImpl): <X>RemoteDataSource`.
   - Ktor's Api class is `@Inject`-constructed, so it needs no module entry.
   The feature's Hilt module is `internal`, so it can bind `internal` types.
6. **Special cases** (templates section S): auth endpoints that must not carry a token (use the `@PublicApi` client from `:core:network`), 401-on-login, server error body → `AppError.Domain`, multipart upload, pagination envelope, another host, per-request headers.

## Verify
- `./gradlew :feature:<name>:compileDebugKotlin`, then `:app:assembleDevDebug` if you touched Hilt wiring (Hilt validates the graph at compile time).
- Offer an API test through `add-tests` (MockWebServer for Retrofit, MockEngine for Ktor) that checks method, path, query and body against a real response sample — the cheapest way to catch `@SerialName` mistakes.
- Tell the user which files changed and which data-source functions `make-repository` can now use.
