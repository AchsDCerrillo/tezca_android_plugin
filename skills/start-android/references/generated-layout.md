# What start-android generates

`<base>` = base package as a path, `<pkg>` = base package, `<appId>` = applicationId. Files tagged *(conflict)* usually exist in an Android Studio template project and are merged instead of written fresh.

## Both modes
| Path | Purpose |
|---|---|
| `settings.gradle.kts` *(conflict)* | `includeBuild("build-logic")`, foojay toolchain resolver (provisions JDK 21 for tests), typesafe project accessors, core modules, `// tezca-android:features` marker; kmp-sdk: the SDK's Maven repo with env/gradle-property credentials |
| `build.gradle.kts` *(conflict)* | `apply false` for every plugin; nothing else |
| `gradle/libs.versions.toml` *(conflict)* | the catalog (see versions.md) + convention plugin aliases `libs.plugins.tezca.android.*` |
| `gradle.properties` (appended) | `tezca.basePackage=<pkg>` (convention plugins derive namespaces from it), configuration cache, build cache, parallel, non-transitive R |
| `.gitignore` (appended) | build outputs, `local.properties`, keystores |
| `build-logic/convention` | `tezca.android.application` (SDK levels, Java 17, BuildConfig), `tezca.android.library` (namespace from path, test stack, JDK 21 test launcher + Robolectric flags, consumer rules if present), `tezca.android.compose` (compiler plugin, BOM, Material 3, tooling, UI test), `tezca.android.hilt` (KSP + Hilt + Hilt testing), `tezca.android.room` (native: KSP + Room 3 + schemas), `tezca.android.feature` (library + compose + hilt + serialization + Navigation 3 + Hilt ViewModel + every existing `:core:*`) |
| `app/build.gradle.kts` *(conflict)* | `dev`/`prod` flavors with `BASE_URL` BuildConfig field, release signing from env (`ANDROID_KEYSTORE_PATH`…), versionCode/Name from `-P`, R8 on release, `// tezca-android:feature-dependencies` marker |
| `app/src/main/AndroidManifest.xml` *(conflict)* | `INTERNET`, Hilt application, single activity with `adjustResize` |
| `app/.../<App>Application.kt` | `@HiltAndroidApp` (kmp-sdk: calls `SdkInitializer`) |
| `app/.../MainActivity.kt` *(conflict)* | `@AndroidEntryPoint`, `enableEdgeToEdge()`, `AppTheme { AppNavDisplay() }` |
| `app/.../navigation/AppNavDisplay.kt` | the only `NavDisplay`: `rememberNavigator(start)` (`// tezca-android:start-destination`), saveable-state + ViewModel-store entry decorators, `// tezca-android:feature-entries` |
| `app/.../di/AppConfigModule.kt` | provides `AppConfig(baseUrl, isDebug, flavor)` from BuildConfig |
| `app/.../welcome/WelcomeScreen.kt` | placeholder start destination (`WelcomeKey`), removed once a real screen exists |
| `core/common` | `DataResult` (+ `localCall`), `AppError`, `AppConfig`, `@Dispatcher(AppDispatcher.IO/Default/Main)`, `@ApplicationScope`, `DispatchersModule`, `DataResultTest` |
| `core/ui` | `AppTheme` (light/dark/dynamic color), `Dimens`, `UiText` + `rememberUiTextResolver`, `AppError.toUiText()`, `ObserveAsEvents`, `LoadingContent`/`ErrorContent`/`EmptyContent`, strings in `values` + `values-es` |
| `core/navigation` | `Navigator` (navigate, back, replaceAll, popUpTo) over a saveable Navigation 3 back stack |
| `core/testing` | `MainDispatcherRule`; exposes JUnit, coroutines-test, Turbine, MockWebServer (Retrofit) or Ktor MockEngine |
| `.tezca-android.json` | project state read by every skill |

## Native mode
| Path | Purpose |
|---|---|
| `core/network` (Retrofit) | `NetworkJson`, `safeApiCall`/`toDataResult`/`mapDto`, `TokenProvider` (`@BindsOptionalOf`), `AuthInterceptor` (in-memory token, no blocking), `TokenAuthenticator` (single-flight refresh on 401), `RetryInterceptor` (idempotent methods only), `NetworkModule` (OkHttp with debug-only redacted logging, Retrofit) + `SafeApiCallTest` |
| `core/network` (Ktor) | `NetworkJson`, `HttpClientFactory` (timeouts, idempotent-only retry, debug-only logging with sanitized Authorization, bearer Auth from `TokenProvider`), `safeCall`/`mapDto`, `clearAuthTokens()`, `NetworkModule` + `SafeCallTest` |
| `core/database` | `CoreDatabase` (sync metadata), `DatabaseFactory` (per-feature databases, platform driver, injected IO query dispatcher), `SyncMetadataStore` (TTL freshness), `DatabaseModule` + `SyncMetadataStoreTest` (Robolectric) |
| `core/datastore` | single `DataStore<Preferences>` (`app_preferences`) + `PreferencesDataStoreTest` |

## kmp-sdk mode
| Path | Purpose |
|---|---|
| `core/sdk` | `api(libs.sdk)`, `SdkInitializer` (`// tezca-android:sdk-init`), `SdkUseCaseModule` (`// tezca-android:sdk-usecases`, ViewModelComponent) |

## CI (`--ci`)
| Path | Purpose |
|---|---|
| `.github/workflows/ci.yml` | PRs and pushes to main/dev: unit tests, lint, assemble |
| `.github/workflows/dev.yml` | push to `dev`: dev release APK → Firebase App Distribution |
| `.github/workflows/release.yml` | published GitHub Release `vX.Y.Z`: prod AAB → Play internal track |
| `docs/ci-secrets.md` | every secret, where it comes from |
