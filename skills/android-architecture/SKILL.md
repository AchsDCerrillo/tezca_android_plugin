---
name: android-architecture
description: Architecture conventions for native Android apps (Kotlin, Jetpack Compose, Hilt, Navigation 3, Room, DataStore, Retrofit/Ktor) built with the tezca-android-development skills. Covers multi-module layout (:app, :core:*, one :feature:* per feature), layer rules (Api/Dao → DataSource → Repository → UseCase → ViewModel → Route/Screen), naming, DataResult/AppError, Hilt wiring, injected dispatchers, offline-first sync, UiState/Action/Event, strings and theming, and the KMP-SDK consumer mode. Use it whenever you write or review code in an Android project that has a .tezca-android.json file, and whenever the user asks where something belongs, how layers talk to each other, how to name a class, how errors reach the UI, or how to follow Google's architecture recommendations — even if they don't say "architecture". Every make-* skill, new-feature, add-tests and android-quality-check reads it first.
---

# Android architecture conventions

This file is the single source of truth that keeps the other skills consistent: `start-android`, `new-feature`, `make-api`, `make-database`, `make-preferences`, `make-repository`, `make-usecase`, `make-viewmodel`, `make-screen`, `add-tests`, `add-sdk` and `android-quality-check`. When code you write disagrees with this file, change the code, not the convention, unless the user explicitly asks for something else.

It implements Google's [guide to app architecture](https://developer.android.com/topic/architecture) and its [recommendations](https://developer.android.com/topic/architecture/recommendations): unidirectional data flow, a data layer that is the single source of truth, an optional domain layer of single-purpose use cases, and ViewModels exposing immutable UI state as `StateFlow`. `references/quality-checklist.md` maps each recommendation and the [core app quality](https://developer.android.com/docs/quality-guidelines/core-app-quality) items to concrete checks.

## 1. Find out what kind of project this is

Read `.tezca-android.json` at the project root (written by `start-android`):

```json
{ "mode": "native", "network": "retrofit", "basePackage": "com.acme.shop", "appId": "com.acme.shop",
  "di": "hilt", "navigation": "navigation3", "features": ["onboarding"], "pluginVersion": "0.1.0",
  "sdk": { "coordinates": "com.acme:sdk:1.4.0", "repo": "https://maven.pkg.github.com/acme/sdk",
           "exposes": "usecases", "locator": "SdkLocator.get<T>()" } }
```

If it's missing, infer the same facts (`tezca.basePackage` in `gradle.properties`, modules in `settings.gradle.kts`: `:core:sdk` means kmp-sdk mode) and tell the user the project wasn't set up with `start-android`; offer to run it, or adapt to the project's existing conventions if they decline.

| | **native** | **kmp-sdk** (the app consumes a Kotlin Multiplatform SDK) |
|---|---|---|
| Data layer | in the app: `:core:network` (Retrofit or Ktor), `:core:database` (Room 3), `:core:datastore`, feature `data/` packages | inside the SDK. The app has no APIs, DAOs or repositories |
| Domain layer | feature `domain/` packages with use cases | the SDK's use cases, bridged into Hilt by `:core:sdk` (`SdkUseCaseModule`) |
| ViewModels | always in the app | in the app; if the SDK ships ViewModels, wrap or bridge them (make-viewmodel) |
| make-api / make-database / make-repository | available | not applicable: say the change belongs in the SDK (the `tezca-kmp-development` plugin builds it) |

## 2. Modules and the dependency rule

```
:app ──▶ :feature:* ──▶ :core:{common, ui, navigation, network, database, datastore | sdk}
                       (features never depend on each other)
```

- `:core:common` — `DataResult`, `AppError`, `AppConfig`, dispatcher qualifiers, `@ApplicationScope`.
- `:core:ui` — `AppTheme`, `Dimens`, `UiText`, `AppError.toUiText()`, `ObserveAsEvents`, `LoadingContent/ErrorContent/EmptyContent`.
- `:core:navigation` — `Navigator` (wraps the Navigation 3 back stack).
- `:core:network` — `NetworkJson`, `safeApiCall`/`safeCall`, `mapDto`, `TokenProvider` (optional binding), auth + retry. 
- `:core:database` — `DatabaseFactory`, `SyncMetadataStore`. `:core:datastore` — the single `DataStore<Preferences>`.
- `:core:testing` — `MainDispatcherRule`, MockWebServer / Ktor MockEngine, Turbine.
- `:core:sdk` (kmp-sdk) — `SdkInitializer`, `SdkUseCaseModule`.

Features get every existing core module automatically through the `tezca.android.feature` convention plugin. State that several features need (the signed-in session, the current user) goes in a new `:core:session` module (`tezca.android.library` + `tezca.android.hilt`), never in one feature that others import. Cross-feature navigation is a callback parameter of `<feature>Entries(...)` wired in `:app`'s `AppNavDisplay`.

Inside a feature the layers point inward:

```
Route/Screen ─▶ ViewModel ─▶ UseCase ─▶ Repository (interface, domain)
                                             ▲
                         RepositoryImpl (data) ├─▶ RemoteDataSource ─▶ Api (Retrofit service / Ktor)
                                               └─▶ LocalDataSource  ─▶ Dao (Room) / DataStore
```

- Domain (`model`, `repository` interfaces, `usecase`) imports nothing from Retrofit, Ktor, Room, DataStore, Compose or Android.
- A ViewModel never injects a repository or data source: even trivial reads go through a use case, so business rules have one home.
- DTOs (`@Serializable`) and entities (`@Entity`) never leave `data/`; mappers turn them into domain models at the data-source boundary.

## 3. Package layout of a feature

```
feature/<name>/src/main/kotlin/<base>/feature/<pkg>/
├── data/
│   ├── remote/     <X>Dto.kt, <X>Api.kt (Retrofit service or Ktor class), <X>RemoteDataSource.kt (+Impl)
│   ├── local/      <X>Entity.kt, <X>Dao.kt, <Feature>Database.kt, <X>LocalDataSource.kt (+Impl), <X>PreferencesDataSource.kt (+Impl)
│   ├── mapper/     <X>Mappers.kt
│   └── repository/ <X>RepositoryImpl.kt
├── domain/
│   ├── model/      <X>.kt
│   ├── repository/ <X>Repository.kt
│   └── usecase/    <Verb><X>UseCase.kt
├── presentation/<screen>/  <Screen>UiState.kt (state + Action + Event), <Screen>ViewModel.kt, <Screen>Screen.kt
├── navigation/<Feature>Navigation.kt   (@Serializable NavKeys + EntryProviderScope<NavKey>.<feature>Entries)
└── di/<Feature>Module.kt               (Hilt: @Binds + companion @Provides)
```

`<pkg>` = feature name lowercased without dashes (`user-profile` → `userprofile`). Resources: `src/main/res/values*/strings.xml` with keys prefixed `<feature_snake>_`.

## 4. Naming

| Thing | Name | Notes |
|---|---|---|
| Network model | `OrderDto` | `@Serializable`, `internal`, mirrors JSON exactly |
| API | `OrdersApi` | Retrofit: interface with `suspend fun …(): Response<Dto>`; Ktor: class over `HttpClient` |
| Remote data source | `OrdersRemoteDataSource` + `…Impl` | wraps calls in `safeApiCall`/`safeCall`, maps DTO → domain with `mapDto` |
| Entity / DAO | `OrderEntity`, `OrderDao` | `Flow` reads, `suspend` writes, `@Upsert`, `@Transaction` |
| Database | `<Feature>Database` | one per feature that persists data, built with `DatabaseFactory` |
| Local data source | `OrdersLocalDataSource` + `…Impl` | maps entity ↔ domain, `Flow<Domain>` reads, `DataResult` writes |
| Preferences | `<X>PreferencesDataSource` + `…Impl` | shared `DataStore<Preferences>`, keys `"<feature>.<key>"` |
| Repository | `OrdersRepository` (domain) + `OrdersRepositoryImpl` (data) | |
| Use case | `ObserveOrdersUseCase`, `GetOrderUseCase`, `RefreshOrdersUseCase`, `PlaceOrderUseCase` | verb first, one `operator fun invoke` |
| ViewModel | `OrderListViewModel` | named after the screen |
| UI contract | `OrderListUiState`, `OrderListAction`, `OrderListEvent` | |
| Composables | `OrderListRoute` (stateful), `OrderListScreen` (stateless) | |
| Navigation key | `@Serializable data object OrderListKey : NavKey`, `data class OrderDetailKey(val id: Long) : NavKey` | |
| Hilt module | `OrdersModule` (`di/`) | |

## 5. Results and errors

Fallible data operations return `DataResult<T>` (`core/common/result`) and never throw:

```kotlin
sealed interface DataResult<out T> {
    data class Success<out T>(val data: T) : DataResult<T>
    data class Failure(val error: AppError) : DataResult<Nothing>
}   // helpers: map, flatMap, onSuccess, onFailure, getOrNull, errorOrNull, asEmpty, localCall { }
```

`AppError` = `Network.{NoInternet, Timeout, Unauthorized, Forbidden, NotFound, Serialization, Client(code, body), Server(code, body)}`, `Local.{NotFound, Database(message)}`, `Domain(code, message)`, `Unknown(message)`.

- Mapping in happens in one place per source: `safeApiCall { }` (Retrofit) / `safeCall { }` (Ktor) for HTTP, `localCall { }` for Room/DataStore writes.
- Business-rule failures are `AppError.Domain(code = "coupon_expired")`; the ViewModel maps known codes to string resources and falls back to `toUiText()`.
- Never catch `CancellationException` (rethrow it if you catch `Exception`), and never swallow an error silently: return it, or log it and return it.

## 6. Visibility

Inside a feature everything is `internal` except what `:app` must reach: the `<feature>Entries` function and the NavKeys `:app` navigates to. The feature's Hilt module is `internal` too (Hilt aggregates it anyway). In `:core:*`, only what features use is public.

## 7. Hilt

- **One `di/<Feature>Module.kt`** (`@InstallIn(SingletonComponent::class)` abstract class): `@Binds` for interfaces implemented in the module (data sources, repositories), `@Provides` in its companion for library-built objects (Retrofit service: `retrofit.create<OrdersApi>()`; Room database and DAOs). The skills insert at the `// tezca-android:binds` and `// tezca-android:provides` markers — keep them.
- **Scopes:** `@Singleton` on things that hold state or connections (database, DataStore, `OkHttpClient`/`HttpClient`, repositories that cache in memory). Data sources and repositories without in-memory state may stay unscoped; use cases are always unscoped `@Inject constructor` classes (no module entry needed).
- **ViewModels:** `@HiltViewModel` + `@Inject constructor`. Navigation-key arguments use assisted injection (`@HiltViewModel(assistedFactory = …)`, `@AssistedInject constructor(@Assisted val key: OrderDetailKey, …)`), resolved with `hiltViewModel<VM, VM.Factory>(creationCallback = { it.create(key) })`.
- **Dispatchers:** inject `@Dispatcher(AppDispatcher.IO) io: CoroutineDispatcher` (also `Default`, `Main`). Never reference `Dispatchers.IO/Main/Default` outside `DispatchersModule` and `ObserveAsEvents` — tests can't control hardcoded dispatchers.
- **Never** pass `Context` into ViewModels or use cases. Anything needing it (DataStore, Room, file access, review/billing APIs) lives in the data layer behind an interface, built with `@ApplicationContext`.
- **Optional bindings:** `:core:network` declares `@BindsOptionalOf TokenProvider`; the session owner binds it (make-preferences). Use the same pattern for other "provided later" collaborators.

## 8. Offline-first data (default)

1. **Reads** return `Flow` from Room via the local data source: Room is the single source of truth and re-emits on every change.
2. **`refresh(force)`** fetches remotely and replaces the cache inside one `@Transaction`, gated by `SyncMetadataStore.isStale(key, ttl)` and followed by `markSynced(key)` on success only.
3. **Writes** go to the server first; on success the result is written locally so observers update. Optimistic or queued (WorkManager) writes only when the user asks for offline writes.
4. **Refresh errors** are returned as `DataResult`, never pushed into the data `Flow`. The UI keeps cached data visible and shows the error separately (snackbar / banner).

"Don't do this offline", "no offline support", "online only", "always fresh" → remote-only repository. "Local only", "no API" → local-only. The choice can differ per operation (payment confirmation remote-only, payment history offline-first). make-repository has the templates.

## 9. Presentation

- **`XUiState`:** immutable `data class` with defaults (`XUiState()` is the initial state). Use a sealed interface only when states are mutually exclusive and carry different data. Collections: `List`, or `kotlinx.collections.immutable` if the project already uses it.
- **State:** `val state: StateFlow<XUiState>` built with `combine`/`map` + `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), XUiState())`. Start loading in `onStart {}` of the flow, not in `init`, so nothing runs before the UI subscribes. Queries that change (search text, filters) are `MutableStateFlow`s fed into `flatMapLatest`, so stale requests are cancelled instead of racing.
- **Input:** one `fun onAction(action: XAction)`; `XAction` is a sealed interface.
- **One-off effects** (navigate, snackbar): `Channel<XEvent>(Channel.BUFFERED)` exposed as `val events = channel.receiveAsFlow()`, collected with `ObserveAsEvents`. Never as boolean flags in state.
- **Route / Screen:** `XRoute` gets the ViewModel (`hiltViewModel()`), collects with `collectAsStateWithLifecycle()`, observes events, and receives navigation callbacks. `XScreen(state, onAction, modifier)` is stateless, takes only immutable data and lambdas, and has `@Preview`s (light + dark) inside `AppTheme`. Business logic, formatting of domain data, filtering and sorting live in the ViewModel/use case, never in a composable.
- **Edge-to-edge:** `MainActivity` calls `enableEdgeToEdge()`. Every screen uses `Scaffold` and applies its `innerPadding` (or `WindowInsets.safeDrawing`) — content never sits under the system bars; IME insets use `imePadding()` on forms.
- **Strings:** every user-facing text is a resource in the module's `res/values/strings.xml` (`stringResource(R.string.x)`), translated in every `values-<locale>` folder the project has. ViewModels hold `UiText`, never `String` from resources, and never `Context`.
- **Theming:** colors from `MaterialTheme.colorScheme`, typography from `MaterialTheme.typography`, spacing from `Dimens`. No hardcoded `Color(0x…)`, font sizes or dp spacing in screens; light and dark both work.
- **Accessibility:** `contentDescription` on meaningful icons/images (null for decorative ones), touch targets ≥ 48dp, text that scales (`sp` from typography), semantics for custom controls.
- **Navigation 3:** keys are `@Serializable` `NavKey`s declared in the feature's `navigation/` file; `<feature>Entries(navigator, onX = …)` registers `entry<Key> { XRoute(…) }`; `AppNavDisplay` (in `:app`) owns the back stack, decorators (saveable state + ViewModel store per entry) and cross-feature callbacks.

## 10. kmp-sdk mode specifics

- `SdkInitializer.initialize(context)` (called from `Application.onCreate`) is the only place that touches the SDK's bootstrap / DI container.
- Each SDK use case a screen needs gets one `@Provides` in `:core:sdk`'s `SdkUseCaseModule` (`= <locator>`), so ViewModels inject them like app classes. SDK use cases are usually final: tests stub them with MockK (added to features in this mode) or through an app-side `fun interface`.
- If the SDK uses Firebase (or other services) internally, resolving its use cases needs that set up (`google-services.json` + the google-services plugin in `:app`); start-android step 5 covers it.
- Map the SDK's result type (e.g. `ResultState<T>`) to `UiState` in the ViewModel, errors through `Throwable?.toAppError()` (`:core:sdk`), and SDK models to small screen models so previews/tests don't build SDK classes. If the SDK emits cached data first and then fails, keep the cached data on screen and surface the error separately.
- Bugs whose root cause is in the SDK are reported with evidence and fixed in the SDK repository, not worked around in the app.

## 11. Testing conventions (details in add-tests)

- JVM unit tests in `src/test`; names `` `behaviour under condition` `` (backticks) or `method_condition_result`.
- Fakes over mocks: `FakeOrdersRepository` implementing the domain interface, with a `MutableStateFlow` behind it. MockK only for final third-party/SDK classes.
- MockWebServer (Retrofit) / MockEngine (Ktor) for APIs, in-memory Room under Robolectric for DAOs, a temp-file DataStore for preferences.
- `MainDispatcherRule` + `runTest` + Turbine for ViewModels; pass the rule's `testDispatcher` wherever an `@Dispatcher` is injected.

## 12. Before you finish any change

- `./gradlew :<module>:compileDebugKotlin` (or the module's tests) passes; for wiring changes run `./gradlew :app:assembleDevDebug` (Hilt validates the graph at compile time).
- No DTO/entity outside `data/`, no repository injected into a ViewModel, no `Dispatchers.*`, no `Context` in ViewModels.
- New strings exist in `values/` and every `values-<locale>/`.
- New screens handle insets and have previews.
