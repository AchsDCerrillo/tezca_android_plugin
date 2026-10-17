# Quality checklist

Each item names the source (**R** = [architecture recommendations](https://developer.android.com/topic/architecture/recommendations), **Q** = [core app quality](https://developer.android.com/docs/quality-guidelines/core-app-quality)), how to detect a violation, and the fix. `android-quality-check` walks this list; the make-* skills follow it while writing code.

Severity: **P0** crash / data loss / security, **P1** architecture or quality rule broken, **P2** maintainability.

## Layers and data (R)

| # | Check | Detect | Fix | Sev |
|---|---|---|---|---|
| D1 | Data layer is the single source of truth; reads are observable | DAO `@Query` returning `List<…>` for data the UI shows; repositories caching in mutable fields | `Flow` from Room, repository returns it | P1 |
| D2 | Repositories expose suspend/Flow, are main-safe | `Dispatchers.Main` work or blocking calls in repositories; `runBlocking` anywhere outside OkHttp `Authenticator` | `withContext(io)` in data sources; suspend APIs | P0 |
| D3 | Multi-step writes are atomic | several DAO writes in a loop / use case without `@Transaction` or `withTransaction` | `@Transaction` DAO method or `database.withTransaction { }` | P0 |
| D4 | DTOs/entities don't leak | `Dto`/`Entity` imports in `domain/` or `presentation/` | mappers in `data/mapper` | P1 |
| D5 | Errors are typed and never swallowed | `catch (e: Throwable)` / `catch (e: Exception)` without rethrowing `CancellationException`; `.catch { }` that emits nothing; `runCatching` around suspend calls | `DataResult` + `AppError`, rethrow cancellation | P1 |
| D6 | Non-idempotent requests aren't retried automatically | retry interceptor / `HttpRequestRetry` applying to POST/PATCH | retry GET/HEAD/PUT/DELETE only | P0 |
| D7 | Secrets and tokens not logged | `HttpLoggingInterceptor` BODY / Ktor `LogLevel.ALL` in release; logging tokens | debug-only logging, `redactHeader("Authorization")` | P0 |
| D8 | One DataStore instance per file | `by preferencesDataStore(...)` declared inside a class; two delegates on one file | single `@Singleton` DataStore from Hilt | P0 |
| D9 | Room schemas exported, migrations tested | `exportSchema = false`; version bumps without `Migration`/`AutoMigration` | schema dir + migration + `MigrationTestHelper` test | P1 |
| D10 | Background work is structured | `GlobalScope`, `CoroutineScope(Dispatchers.IO).launch` in receivers/services, `AlarmManager` for deferrable work | `@ApplicationScope` scope, WorkManager (`@HiltWorker`), `goAsync()` | P1 |

## Domain (R)

| # | Check | Detect | Fix | Sev |
|---|---|---|---|---|
| U1 | Use cases have one responsibility and one `invoke` | use case with several public methods or `execute()/run()` names | split; `operator fun invoke` | P2 |
| U2 | Business rules live in domain/VM, not UI | validation, calculations, grouping/sorting inside composables | move to use case / ViewModel | P1 |
| U3 | Domain has no Android/UI types | `Context`, `R.string`, `Color`, Compose imports in `domain/` | plain Kotlin types, `AppError.Domain` codes | P1 |
| U4 | Loading isn't a domain concept | `Resource.Loading`/`Outcome.Loading` emitted from repositories/use cases | loading is UI state in the ViewModel | P2 |

## UI layer (R)

| # | Check | Detect | Fix | Sev |
|---|---|---|---|---|
| V1 | Single immutable UiState exposed as StateFlow | `MutableStateFlow` exposed publicly; several independent state flows per screen; `LiveData` in new code | private `MutableStateFlow` + `asStateFlow()`, or `stateIn` | P1 |
| V2 | Lifecycle-aware collection | `collectAsState()` | `collectAsStateWithLifecycle()` | P1 |
| V3 | No stale-request races | `launch { flow.collect { } }` started on every keystroke/filter change without cancelling | `MutableStateFlow` + `flatMapLatest` / `debounce` | P1 |
| V4 | No hardcoded dispatchers | `Dispatchers.IO/Main/Default` outside the dispatcher module | `@Dispatcher(AppDispatcher.IO)` injection | P1 |
| V5 | ViewModels don't hold Context / Views | `Context`, `Activity`, `View` params or fields in ViewModels; `AndroidViewModel` | move to data layer; `UiText` for strings | P1 |
| V6 | One-off events aren't state flags | `isSuccess`/`navigateTo` booleans consumed by `LaunchedEffect(state)` | `Channel` + `ObserveAsEvents` | P2 |
| V7 | Loading starts on subscription | heavy work in `init {}`; `LaunchedEffect(true) { vm.load() }` | `onStart {}` + `stateIn(WhileSubscribed)` | P2 |
| V8 | Stateless, previewable screens | composables that take a ViewModel deep in the tree; no `@Preview` | Route/Screen split, previews | P2 |

## Build & modularization (R)

| # | Check | Detect | Fix | Sev |
|---|---|---|---|---|
| B1 | KSP, not kapt | `kotlin-kapt` / `kapt(` | `ksp(` | P2 |
| B2 | Shared build config | the same `android { }` block in every module; buildSrc of constants | `build-logic` convention plugins | P2 |
| B3 | No unneeded build features | `dataBinding = true` / `viewBinding = true` in Compose-only modules | remove | P2 |
| B4 | Single version catalog, aligned versions | hardcoded versions in module scripts; two versions of one library family (lifecycle 2.9 + 2.10) | `libs.versions.toml`, one version ref | P2 |
| B5 | Features independent | `project(":feature:…")` in another feature | callbacks through `:app`, shared code to `:core:*` | P1 |
| B6 | targetSdk meets Play's requirement | `targetSdk` below the current Play minimum | raise and test behaviour changes | P0 |
| B7 | Release builds minified | `isMinifyEnabled = false` on the app's release | R8 + keep rules | P2 |

## Core app quality (Q)

| # | Check | Detect | Fix | Sev |
|---|---|---|---|---|
| Q1 | Edge-to-edge and insets | no `enableEdgeToEdge()`; content under status/navigation bar; forms covered by the IME | `enableEdgeToEdge()`, `Scaffold` padding, `imePadding()` | P1 |
| Q2 | Back navigation is predictable | custom back handling that swallows back; back stack that never shrinks | Navigation 3 back stack, `Navigator.back()` | P1 |
| Q3 | State survives config change and process death | screen data in `remember {}` only; nav args not saved | ViewModel + `SavedStateHandle`/serializable NavKeys, `rememberSaveable` for UI-only state | P1 |
| Q4 | Localized text | string literals in `Text("…")`, `contentDescription = "…"`, toasts, ViewModel messages | `strings.xml` + every `values-<locale>` | P1 |
| Q5 | Dark theme and dynamic color | hardcoded `Color(0x…)`/`Color.White` in screens | `MaterialTheme.colorScheme` roles | P1 |
| Q6 | Accessibility | icons/images without `contentDescription`; touch targets < 48dp; `fontSize = 12.sp` literals | descriptions, `Dimens.minTouchTarget`, typography | P1 |
| Q7 | No ANRs / main-thread I/O | DB/network/file work without `suspend`; `allowMainThreadQueries()`; SDK init that blocks `onCreate` | suspend + injected IO dispatcher; App Startup / lazy init | P0 |
| Q8 | Permissions requested in context, minimum set | permissions in the manifest that the code doesn't use; runtime requests at launch | remove; request when the feature needs it | P1 |
| Q9 | Secure storage and transport | tokens in plain SharedPreferences logged/committed; `usesCleartextTraffic="true"`; keystore or service JSON committed | DataStore (+ encryption if sensitive), HTTPS only, `.gitignore` secrets | P0 |
| Q10 | Notifications and background behave | foreground services without a type; exact alarms without need | correct FGS type, WorkManager | P1 |
| Q11 | Tests exist for logic | ViewModels/use cases/repositories without unit tests | add-tests | P2 |
