---
name: make-preferences
description: Create or update typed key-value storage in an Android app with Preferences DataStore — a feature's settings, flags (onboarding completed, dark mode, last selected filter) or the signed-in session (access/refresh tokens, user id) — as a PreferencesDataSource interface + Impl over the app's single DataStore instance, with Hilt wiring. Includes the session pattern: a :core:session module, a TokenProvider bound for Retrofit/Ktor auth (in-memory token, refresh on 401), logout that clears tokens, and choosing the start destination from the session. Use it whenever the user wants to remember a setting, save a flag or small value, persist a token or login state, keep the user signed in, implement logout, or "save this locally" for small data — even if they don't say DataStore. Use make-database instead for lists or anything you query.
---

# make-preferences: typed DataStore preferences

`:core:datastore` owns the **single** `DataStore<Preferences>` (`app_preferences`), provided by Hilt as a `@Singleton`. Two DataStore instances on the same file throw "multiple DataStores active" at runtime, so never call `preferencesDataStore(...)` / `PreferenceDataStoreFactory.create` anywhere else. Features add typed data sources on top of it.

DataStore is for small key-value state; anything you list, query or relate goes to Room (make-database). Tokens go in plain DataStore by default (app-private storage); offer encryption (a Tink/Keystore-backed `Serializer` or `EncryptedFile`) when the user handles sensitive data, and say what it costs.

## Steps
1. Read `../android-architecture/SKILL.md` and `.tezca-android.json`. In `kmp-sdk` mode, the SDK usually owns preferences (check for a preferences/session use case first); app-only UI settings are still fine here — add `:core:datastore` from `start-android`'s `native` overlay if missing.
2. Pick the owner:
   - A feature's own settings live in the feature's `data/local/`.
   - **The session** (tokens + who is signed in) is read by many features → a `:core:session` module (`tezca.android.library` + `tezca.android.hilt`, depends on `:core:common`, `:core:datastore`, `:core:network`, `:core:database`). Create it if missing (templates §2): `include(":core:session")` in `settings.gradle.kts` and `implementation(projects.core.session)` in `:app`; features get it automatically (it's in `CORE_MODULES`). The login feature's repository writes it through `SessionPreferencesDataSource` (templates "Writing the session"); everything else reads it through `ObserveSessionUseCase`.
3. List the values: name, type (`Boolean`, `Int`, `Long`, `Float`, `Double`, `String`, `Set<String>`), default. Group values that change together into one small domain model; enums as `String`; small structured values as a JSON `String` (kotlinx.serialization).
4. Write `<X>PreferencesDataSource.kt` (interface + `internal …Impl @Inject constructor(private val dataStore: DataStore<Preferences>)`) from `references/templates.md`:
   - **Keys** in a private object, **prefixed with the owner** (`"session.access_token"`, `"orders.last_filter"`): every module shares one file.
   - **Reads** are `Flow`s: `dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { … }.distinctUntilChanged()`.
   - **Writes** are `suspend`, `dataStore.edit { }` wrapped in `localCall { }` → `DataResult<Unit>`.
   - Existing data source → add keys in place. Never rename an existing key string (users silently lose the value); migrate old → new if a rename is unavoidable.
5. **Hilt** — `@Binds abstract fun bind<X>PreferencesDataSource(impl: <X>PreferencesDataSourceImpl): <X>PreferencesDataSource` under `// tezca-android:binds` (feature) or in `:core:session`'s `SessionModule`.
6. **Session extras** (templates §3–§5):
   - **`SessionTokenProvider`** implements `:core:network`'s `TokenProvider` and is bound with `@Binds` (that fills the `@BindsOptionalOf`). It keeps the access token **in memory** (a `StateFlow` hydrated from DataStore in the application scope) because OkHttp interceptors call it synchronously on every request. Variants: **no refresh endpoint** (default when the backend doesn't mention one: on 401 clear the session → user signs in again) or **refresh endpoint** (calls the auth remote data source on the *public* client from make-api; clears the session if refresh fails).
   - **Logout** clears the session (and `SyncMetadataStore.clear()` plus feature caches when they hold per-user data). Ktor only: also call `httpClient.clearAuthTokens()`, because Ktor's bearer provider caches tokens.
   - **Start destination from the session:** `:app` waits for the first session value (splash screen kept on screen) and keys the `AppNavDisplay` on `signedIn`, so login, logout and an expired session all restart the back stack at the right root (templates §6). Wire it once both the login feature and the first signed-in feature exist.
7. Verify with `./gradlew :<module>:compileDebugKotlin` and `:app:assembleDevDebug` (Hilt graph). Offer tests through `add-tests` (real DataStore on a temp file, like `:core:datastore`'s `PreferencesDataStoreTest`).
