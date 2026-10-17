---
name: make-screen
description: Build or update a complete screen in a native Android app end to end — whatever is missing in the chain (API/remote data source → Room/DataStore local data source → offline-first repository → use cases → ViewModel) plus the Jetpack Compose UI connected to the ViewModel — a stateful Route and stateless Screen with Scaffold/edge-to-edge insets, loading/empty/error/pull-to-refresh states, string resources in every locale, light/dark previews, the Navigation 3 key + entry, and Hilt wiring. Works in full-native mode and in KMP-SDK mode (UI on top of SDK use cases or ViewModels). Use it whenever the user asks for a screen, page, view, form, list, detail, dashboard, login/registration/profile/settings screen, "the UI for X", or a whole user flow — even if they only describe what the user should see and do. Prefer it over calling the make-* skills one by one when the request is a screen.
---

# make-screen: one screen, end to end

The orchestrator. It reuses what already exists, creates what's missing in dependency order, and finishes with the Compose UI. Each layer follows its own skill, so read those as you reach them.

## 1. Understand the screen
1. Read `../android-architecture/SKILL.md` and `.tezca-android.json`.
2. Pin down, asking only what you can't infer (from the request, a Figma link, a screenshot, an existing similar screen, or the API docs):
   - **Feature**: which `:feature:<name>` owns it. Doesn't exist → run `new-feature` first.
   - **What it shows** and from where (endpoint? local only? SDK use case?), **what the user can do**, **where it navigates** (inside the feature, or a hand-off to another feature = callback).
   - **Navigation arguments** (ids) and whether it's the feature's entry point.
   - **Offline**: offline-first unless the user says otherwise (make-repository strategy table).
   - **Pattern**: list, detail, form, search, paginated list (footer template), or a mix.
3. Write a short plan (one line per layer: reuse / create / update) and continue unless something is ambiguous enough to change the result.

## 2. Build the chain (skip what exists)
- **native:** `make-api` → `make-database` and/or `make-preferences` → `make-repository` → `make-usecase` (one per responsibility) → `make-viewmodel`.
- **kmp-sdk:** bridge each SDK use case (`make-usecase`, SDK bridge) → `make-viewmodel` (SDK variant). If the SDK ships a ViewModel for this screen, use it (make-viewmodel "SDK mode notes") and skip writing one. No API/database/repository in the app — if the SDK lacks the data, say so and offer to build it in the SDK (`tezca-kmp-development`).
Compile after each layer (`./gradlew :feature:<name>:compileDebugKotlin`) so errors stay small.

## 3. UI
Templates: `references/templates.md`.
1. **NavKey** — under `// tezca-android:keys` in `navigation/<Feature>Navigation.kt`: `@Serializable data object <Screen>Key : NavKey` or `@Serializable data class <Screen>Key(val id: Long) : NavKey`. Imports: `kotlinx.serialization.Serializable`.
2. **Route + Screen** — `presentation/<screen>/<Screen>Screen.kt`:
   - `internal fun <Screen>Route(onNavigateX: …, viewModel: <Screen>ViewModel = hiltViewModel())` — collects `state` with `collectAsStateWithLifecycle()`, observes `events` with `ObserveAsEvents` (navigation callbacks, snackbars via `SnackbarHostState`), passes `viewModel::onAction` down. With a NavKey argument the Route receives the key and resolves `hiltViewModel<VM, VM.Factory>(creationCallback = { it.create(key) })`.
   - `internal fun <Screen>Screen(state, onAction, modifier, snackbarHostState)` — stateless: `Scaffold` (top bar, snackbar host) whose `innerPadding` is applied to the content; loading → `LoadingContent`, full error → `ErrorContent(onRetry)`, empty → `EmptyContent`, data → content; `PullToRefreshBox` for refreshable lists; `imePadding()` on forms; `LazyColumn` with stable `key`s.
   - Small, reusable pieces become private composables in the same file, or `presentation/<screen>/components/` when large. Reused by several features → `:core:ui`.
   - `@Preview` light + dark (`uiMode = UI_MODE_NIGHT_YES`) of the data state, plus loading/error/empty if they differ meaningfully, each wrapped in `AppTheme`, with sample domain objects.
3. **Strings** — every visible text and content description in `feature/<name>/src/main/res/values/strings.xml` under `<!-- tezca-android:strings -->`, keys prefixed `<feature_snake>_<screen>_…`, **and** in every `values-<locale>/strings.xml` the module has (translate them; if you can't translate reliably, copy the English and tell the user which keys need translation). Never a literal in `Text(...)`, `contentDescription`, or a ViewModel message.
4. **Entry** — under `// tezca-android:entries` in `<feature>Entries(...)`: `entry<<Screen>Key> { key -> <Screen>Route(…) }`. Navigation inside the feature uses `navigator.navigate(OtherKey(id))` / `navigator.back()`; hand-offs to another feature become a new callback parameter below `// tezca-android:entry-callbacks` and are wired in `:app`'s `AppNavDisplay` (`ordersEntries(navigator = navigator, onCheckout = { navigator.navigate(CheckoutKey) })`).
5. **Start destination** — if this is the app's first real screen, ask whether it should be the start (if nobody can answer, make it the start — it beats the placeholder): change `rememberNavigator(WelcomeKey)` in `AppNavDisplay`, and delete the `welcome/` placeholder once unused. Session-dependent start (signed in → home, else login): make-preferences §6.

## 4. Verify (don't skip)
```bash
./gradlew :feature:<name>:compileDebugKotlin :app:assembleDevDebug   # Hilt graph + navigation compile
./gradlew :feature:<name>:testDebugUnitTest                         # if tests exist
./gradlew :app:lintDevDebug       # checkDependencies=true: also lints features (MissingTranslation, HardcodedText…)
```
Translation keys must match across locales — quick check for the feature:
```bash
cd feature/<name>/src/main/res && for f in values-*/strings.xml; do diff <(grep -o 'name="[^"]*"' values/strings.xml | sort) <(grep -o 'name="[^"]*"' $f | sort) >/dev/null || echo "keys differ: $f"; done
```
Then offer `add-tests` for the ViewModel (and the repository/use cases you created). If an emulator or device is connected and the user wants it, `./gradlew :app:installDevDebug` and open the screen.

## 5. Report
Layers created/updated (file list), the navigation key and how to reach the screen, strings that still need translation, and the next suggestion (tests, the next screen of the flow).
