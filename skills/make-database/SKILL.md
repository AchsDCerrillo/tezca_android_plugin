---
name: make-database
description: Create or update the local database of an Android feature with Room 3 (androidx.room3, KSP): @Entity, @Dao with Flow reads / @Upsert / @Transaction replace-all, the feature's own @Database built through DatabaseFactory, entity↔domain mappers, a LocalDataSource interface + Impl, Hilt wiring, and schema migrations. Use it whenever the user wants to store, cache or persist data on the device, save something for offline use, add a table, column, index, relation or query, change a Room schema, write a migration, or "create the DAO / local data source" in an Android app — even if they don't say Room. Also use it when make-repository or make-screen needs a local data source that doesn't exist yet. For key-value settings (flags, tokens, user preferences) use make-preferences instead.
---

# make-database: Room DAO and local data source

Room 3 (package `androidx.room3`, KSP) is the local database. Each feature owns its **own** `@Database`, built with `DatabaseFactory` from `:core:database` (platform SQLite driver, queries on the injected IO dispatcher). One database per feature keeps modules independent: Room needs every entity of a database at compile time, so a shared app database would make `:core:database` depend on every feature. Cross-feature bookkeeping (sync timestamps) lives in `CoreDatabase`.

## Before you start
1. Read `../android-architecture/SKILL.md` and `.tezca-android.json`. In `kmp-sdk` mode the SDK owns persistence: say so and stop unless the user wants app-only local data (then confirm, and add `:core:database` from `start-android`'s `native` overlay).
2. Find out what to store: the domain model (often already created by make-api), the queries the UI needs (list, by id, search, sort, paging), and how stale data is replaced. Ask only what you can't infer.
3. Look for an existing `data/local/` in the feature. If a `<Feature>Database` exists, **update** it: add the entity to `entities`, the DAO accessor, bump `version`, add a migration. Never a second database in the same feature.

## Steps
Templates: `references/templates.md`.
1. **Room plugin** (first database in the feature): add `alias(libs.plugins.tezca.android.room)` to the `plugins {}` of `feature/<name>/build.gradle.kts`. It applies KSP + Room 3 and exports schemas to `feature/<name>/schemas/` — commit that folder; migrations are verified against it.
2. **Entity** — `data/local/<X>Entity.kt`: `@Entity(tableName = "<plural_snake>") internal data class`. Columns are primitives/`String` (`Instant` → epoch-ms `Long`, enums → `String`) so no TypeConverters are needed unless the user wants them. Index columns used in `WHERE`/`ORDER BY`. Foreign keys with `onDelete = CASCADE` only where data is truly relational.
3. **DAO** — `data/local/<X>Dao.kt`, `@Dao internal interface`:
   - UI reads return `Flow<List<XEntity>>` / `Flow<XEntity?>` (Room re-emits on every change: that's the single source of truth).
   - One-shot reads used inside sync logic are `suspend`.
   - Writes: `@Upsert suspend fun upsert(…)`, `@Query("DELETE …") suspend fun`.
   - "Replace the cached list" is one `@Transaction open suspend fun replaceAll(…)` (delete + upsert in a body), never two calls from the data source — otherwise observers briefly see an empty list and a crash midway loses the cache.
4. **Database** — `data/local/<Feature>Database.kt`: `@Database(entities = [...], version = N) internal abstract class <Feature>Database : RoomDatabase()` with one abstract accessor per DAO. No `exportSchema = false`.
5. **Mappers** — `data/mapper/<X>Mappers.kt`: `XEntity.toDomain()`, `X.toEntity()` (next to the DTO mappers from make-api).
6. **Local data source** — `data/local/<X>LocalDataSource.kt`: interface + `internal class <X>LocalDataSourceImpl @Inject constructor(private val dao: <X>Dao)`. Exposes `Flow<Domain>` reads and `suspend` writes taking domain models; writes return `DataResult` through `localCall { }` (maps SQLite exceptions to `AppError.Local.Database`, rethrows cancellation).
7. **Hilt** — in `di/<Feature>Module.kt`:
   - under `// tezca-android:provides`: `@Provides @Singleton fun provide<Feature>Database(factory: DatabaseFactory): <Feature>Database = factory.create("<feature>.db")` and `@Provides fun provide<X>Dao(db: <Feature>Database): <X>Dao = db.<x>Dao()`;
   - under `// tezca-android:binds`: `@Binds abstract fun bind<X>LocalDataSource(impl: <X>LocalDataSourceImpl): <X>LocalDataSource`.
8. **Schema change on an existing database:** bump `version`; prefer `autoMigrations = [AutoMigration(from = N-1, to = N)]` (added table/column with default, index); renames/deletes need an `AutoMigrationSpec` (`@RenameColumn`, `@DeleteColumn`) or a manual `Migration` passed through `factory.create("<feature>.db") { addMigrations(MIGRATION_1_2) }`. Use `fallbackToDestructiveMigration(dropAllTables = true)` only if the user explicitly accepts losing local data (sometimes fine for a pure cache — say so).

## Verify
- `./gradlew :feature:<name>:compileDebugKotlin` (KSP errors such as "Cannot find getter" or "not annotated with @Database" appear here; fixes in the templates' last section), then `:app:assembleDevDebug` for the Hilt graph.
- Offer DAO tests through `add-tests` (Robolectric + in-memory database, same pattern as `:core:database`'s `SyncMetadataStoreTest`), plus a `MigrationTestHelper` test when you added a migration.
- Report the files changed and the new database version.
