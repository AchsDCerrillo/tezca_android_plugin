---
name: add-tests
description: Add or update unit tests for any layer of a native Android app built with the tezca-android-development conventions — Retrofit APIs with MockWebServer, Ktor APIs with MockEngine, Room DAOs and local data sources with an in-memory database under Robolectric, DataStore preferences on a temp file, offline-first repositories with fakes and a fake clock, use cases, ViewModels with MainDispatcherRule + Turbine, Compose screens with the Compose test rule, and Room migrations. Use it whenever the user asks for tests, coverage, "make sure this works", a regression test for a bug, TDD, or after make-* skills finish and offer tests — even if they just say "test this ViewModel/repository/screen".
---

# add-tests: tests for every layer

All tests are JVM unit tests in `src/test/kotlin` (fast, run in CI with `./gradlew testDevDebugUnitTest testDebugUnitTest`). Robolectric gives Room, DataStore and Compose a real Android runtime on the JVM; the convention plugins already run unit tests on a Java 21 launcher with the JVM flags Robolectric needs.

## Before you start
1. Read `../android-architecture/SKILL.md` §11 and `.tezca-android.json` (`network` decides MockWebServer vs MockEngine).
2. Identify what to test from the user's request or the files just created. Priorities: ViewModels and use cases with logic → repositories (cache rules) → DAOs with non-trivial queries/migrations → APIs (DTO parsing) → screens (critical interactions).
3. Look at existing tests and fakes in the module (`src/test/.../fake/`) and reuse them.

## What each test covers
Templates: `references/templates.md`.

| Layer | Tool | Must cover |
|---|---|---|
| API / remote data source | MockWebServer (Retrofit) / MockEngine (Ktor) | method, path, query, body; a **real** response sample parses; error status → `AppError` |
| DAO / local data source | Robolectric + `Room.inMemoryDatabaseBuilder` (`applyDefaults(testDispatcher)`) | Flow re-emits after writes, `replaceAll` is atomic, queries filter/sort correctly |
| Migration | `MigrationTestHelper` (room3-testing) + exported schemas | data survives `N → N+1` |
| Preferences | `PreferenceDataStoreFactory.create(scope = TestScope(testScheduler)) { File(tmp.root, …) }` | defaults, write → read, clear |
| Repository | fakes for remote + local data sources, real `SyncMetadataStore` over in-memory Room **or** a fake store with a controllable clock | cache hit skips network, stale/force refreshes, refresh failure keeps cached data and returns `Failure`, writes mirror locally |
| Use case | fake repositories | the business rule, each `AppError.Domain` code |
| ViewModel | fakes (MockK for final SDK use cases) + `MainDispatcherRule` + Turbine | initial state, loading → content, error with/without cached data, each `Action`, `Event`s emitted |
| Screen | `createComposeRule()` under Robolectric (`@RunWith(AndroidJUnit4::class)`) | renders each state; clicks call `onAction` with the right action (test the stateless `Screen`, not the Route) |

## Rules
- **Fakes over mocks** for your own interfaces: `internal class FakeOrdersRepository : OrdersRepository` backed by `MutableStateFlow`, with knobs (`var refreshResult: DataResult<Unit>`, `val created = mutableListOf<…>()`). Put shared fakes in the module's `src/test/.../fake/` (or `:core:testing` when several modules need them). MockK only for third-party types you can't wrap.
- **One dispatcher per test**: `MainDispatcherRule` replaces Main; pass `mainRule.testDispatcher` wherever an `@Dispatcher` is injected, and `runTest(mainRule.testDispatcher)`, so delays and launches share one scheduler.
- **Flows with Turbine**: `viewModel.state.test { assertEquals(…, awaitItem()) … cancelAndIgnoreRemainingEvents() }`. `stateIn(WhileSubscribed)` only starts when collected, so collect (Turbine or `backgroundScope.launch { state.collect {} }`) before asserting.
- Names describe behaviour: `` `refresh failure keeps cached orders and reports the error` ``.
- Robolectric tests need `@RunWith(AndroidJUnit4::class)`; plain JVM tests don't (faster).
- A bug fix gets a test that fails before the fix.

## Steps
1. Write fakes (if missing), then the tests from the templates.
2. Run them: `./gradlew :feature:<name>:testDebugUnitTest` (or `:core:<module>:testDebugUnitTest`; `:app:testDevDebugUnitTest` for app tests). Fix failures — in the test if the expectation was wrong, in the code if it's a real bug (tell the user).
3. Report what's covered, what isn't (and why), and the command to run them.
