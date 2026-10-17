---
name: make-viewmodel
description: Create or update a Jetpack ViewModel in a native Android app the way Google's architecture guide recommends — @HiltViewModel, a single immutable UiState exposed as StateFlow (stateIn WhileSubscribed), user input through onAction(Action), one-off Events (navigation, snackbars) through a Channel, data only from use cases, Navigation 3 key arguments via assisted injection, flatMapLatest for search/filters, pagination state, and error mapping to UiText. Also handles KMP-SDK use cases (ResultState → UiState) and SDK-provided ViewModels. Use it whenever the user wants a ViewModel, screen state, presentation logic, to wire use cases to a screen, handle loading/error/empty states, form validation state, or "connect the screen to the data" — even if they don't say ViewModel. make-screen calls it for every screen.
---

# make-viewmodel: UiState + Action + Event

A ViewModel turns use-case data into one immutable `UiState`, receives user intent through `onAction`, and emits one-off `Event`s. It holds no Android framework references, so it survives configuration changes and is unit-testable on the JVM.

## Before you start
1. Read `../android-architecture/SKILL.md` (§9 is the presentation contract) and `.tezca-android.json`.
2. Identify the screen and its feature (`presentation/<screen>/`), what it shows, what the user can do, where it navigates, and its navigation arguments (the screen's `NavKey`).
3. Make sure every use case it needs exists (native: make-usecase; kmp-sdk: bridged in `SdkUseCaseModule`). Missing ones → create them first; never inject a repository or data source.
4. Existing ViewModel → update it in place (add state fields with defaults, new actions), keeping the public contract the screen uses.

## Steps
Templates: `references/templates.md` — pick the closest pattern: **list** (observe + refresh + pull-to-refresh), **detail** (NavKey argument via assisted injection), **form** (fields, validation, submit → event), **search** (`MutableStateFlow` query + `debounce` + `flatMapLatest`), **paginated list**, **SDK** variants.

1. **Contract** — `presentation/<screen>/<Screen>UiState.kt` holds three types:
   - `data class <Screen>UiState(...)` with defaults for everything (`<Screen>UiState()` is the initial state): data, `isLoading`/`isRefreshing`, `error: UiText?`, form fields and per-field errors.
   - `sealed interface <Screen>Action` — everything the user can do (`Refresh`, `Retry`, `OrderClicked(id)`, `NotesChanged(text)`, `Submit`).
   - `sealed interface <Screen>Event` — one-off effects (`NavigateToDetail(id)`, `ShowMessage(UiText)`, `Finished`).
2. **ViewModel** — `presentation/<screen>/<Screen>ViewModel.kt`:
   - `@HiltViewModel class <Screen>ViewModel @Inject constructor(...)`; with a NavKey argument: `@HiltViewModel(assistedFactory = <Screen>ViewModel.Factory::class)` + `@AssistedInject constructor(@Assisted private val key: <Screen>Key, …)` + nested `@AssistedFactory interface Factory { fun create(key: <Screen>Key): <Screen>ViewModel }`.
   - `val state: StateFlow<<Screen>UiState>` = combine of use-case flows and private `MutableStateFlow`s (UI-only state) → `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), <Screen>UiState())`. Kick off the first refresh in the flow's `onStart {}`, not in `init`.
   - `private val _events = Channel<<Screen>Event>(Channel.BUFFERED)`; `val events = _events.receiveAsFlow()`.
   - `fun onAction(action: <Screen>Action)` with an exhaustive `when`; long work in `viewModelScope.launch`.
   - Errors: map `AppError` → `UiText` here (feature-specific `AppError.Domain` codes → feature strings, everything else → `error.toUiText()`). Refresh failures while cached data exists go to a `ShowMessage` event (or a non-blocking banner), not a full-screen error.
   - Queries/filters that change: `MutableStateFlow` + `flatMapLatest` (+ `debounce(300)` for typed text) so stale requests are cancelled instead of racing.
   - Never: `Context`, `Dispatchers.*`, repositories, `LiveData`, exposing `MutableStateFlow`, boolean "navigate now" flags in state.
3. **No module entry** — Hilt discovers `@HiltViewModel`s.
4. **Verify** — `./gradlew :feature:<name>:compileDebugKotlin`. Offer a ViewModel test (add-tests: fakes + `MainDispatcherRule` + Turbine) covering initial load, refresh failure with cached data, and the main action.

## SDK mode notes
- Inject the bridged SDK use cases exactly like app use cases.
- Map the SDK's result wrapper (e.g. `ResultState.Success/Failure`) in the ViewModel; when the SDK emits cached data first and then a failure, keep the data and surface the error separately (template "SDK").
- If the SDK ships its **own ViewModels** (`sdk.exposes` = `viewmodels`/`both`): don't wrap them in another ViewModel. If the class extends `androidx.lifecycle.ViewModel`, provide it to Compose with a factory (`viewModel(factory = viewModelFactory { initializer { SdkLocator.get<SdkOrdersViewModel>() } })`) inside the Route and adapt its state to the screen there. If it doesn't (plain class with flows), wrap it in a thin `@HiltViewModel` that owns its lifecycle (`onCleared()` → `sdkVm.clear()`).
