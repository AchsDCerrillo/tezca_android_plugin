---
name: make-repository
description: Create or update a repository in a native Android app that combines the feature's remote data source (Retrofit/Ktor) and local data source (Room / DataStore). Offline-first by default — Room is the single source of truth, observe…() returns Flow, refresh(force) syncs from the server with a freshness TTL (SyncMetadataStore), writes go to the server then the cache — or remote-only when the user says "don't do this offline" / "no offline support" / "online only", or local-only. Generates the domain interface, the Impl in data/, and the Hilt @Binds. Use it whenever the user wants a repository, wants to sync or cache server data, make a feature work offline, combine API and database, add pagination with a cache, or "connect the data layer" — even if they don't say "repository".
---

# make-repository: offline-first repository

The repository is the data layer's public face. Use cases depend on its **interface** in `domain/repository`; the **implementation** in `data/repository` decides where data comes from.

## Before you start
1. Read `../android-architecture/SKILL.md` (§8 is the offline-first contract) and `.tezca-android.json`. In `kmp-sdk` mode repositories live in the SDK: say so and stop (the `tezca-kmp-development` plugin's `make-repository` builds them).
2. Pick the **strategy** from what the user said, per operation, and state it in one line so they can correct it:

   | User says | Strategy | Template |
   |---|---|---|
   | nothing / "offline" / "cache" | **offline-first** (default) | A |
   | "don't do this offline", "no offline support", "online only", "always fresh", "real-time" | **remote-only** | B |
   | "local only", "no API", "just on the device" | **local-only** | C |
   | "edit while offline", "queue changes" | offline writes (opt-in) | D |
   | lists with pages | paginated offline-first | E |

   Example: payment confirmation is remote-only while payment history is offline-first.
3. Check the data sources (`data/remote/*RemoteDataSource`, `data/local/*LocalDataSource`, `*PreferencesDataSource`). Missing one → run make-api / make-database first (or ask). Existing repository → add operations to it instead of creating another.

## Steps
1. **Interface** — `domain/repository/<X>Repository.kt` (`internal interface`):
   - Reads the UI watches: `fun observe…(): Flow<…>`.
   - Sync: `suspend fun refresh…(force: Boolean = false): DataResult<Unit>`.
   - One-shot reads and commands: `suspend fun …(): DataResult<…>`.
   - Only domain models in signatures.
2. **Implementation** — `data/repository/<X>RepositoryImpl.kt`, `internal class … @Inject constructor(...)`, from `references/templates.md`. Offline-first rules:
   - `observe…` returns the local data source's `Flow`, never the network.
   - `refresh…` checks `SyncMetadataStore.isStale(key, ttl)` (unless `force`), fetches remotely, saves with one transactional local call (`replaceAll`), and calls `markSynced(key)` **only on success**. Keys are stable strings per resource (`"orders.list"`, `"orders.item:$id"`). TTL defaults to 15 min for lists, 5 min for single items; ask if data is very dynamic.
   - Commands (`create…`, `update…`, `delete…`) call the remote first and mirror a success locally so the observed flow updates.
   - Wrap each function body in `withContext(io)` with the injected `@Dispatcher(AppDispatcher.IO)` dispatcher: callers (ViewModels) can call it from Main safely, and tests swap the dispatcher.
   - Errors come back as `DataResult.Failure`; nothing throws, and `observe…` flows never emit errors.
   - A repository that keeps in-memory state (pagination cursor, `Mutex`) must be `@Singleton`.
3. **Hilt** — under `// tezca-android:binds` in `di/<Feature>Module.kt`: `@Binds abstract fun bind<X>Repository(impl: <X>RepositoryImpl): <X>Repository` (add `@Singleton` to the Impl class if it holds state). `SyncMetadataStore` and the dispatchers already come from core.
4. **Verify** — `./gradlew :feature:<name>:compileDebugKotlin :app:assembleDevDebug`. Offer `add-tests` for the repository: fakes for both data sources, plus cache-hit / cache-miss / refresh-failure-keeps-cache tests with a fake clock in `SyncMetadataStore`.

## Output to the user
List the repository functions with their strategy, and the use cases that would naturally sit on top (make-usecase creates them).
