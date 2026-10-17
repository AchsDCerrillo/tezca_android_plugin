---
name: make-usecase
description: Create or update a domain use case in a native Android app — one class, one responsibility, one operator fun invoke — built on one or more repositories (Observe… returns Flow, Get…/commands are suspend and return DataResult), injected with Hilt. In KMP-SDK mode it bridges an SDK-provided use case into Hilt (:core:sdk SdkUseCaseModule) instead of writing one. Use it whenever the user wants a use case or interactor, business logic or a business rule (validation, combining data from two repositories, calculations, permissions checks), wants to expose an SDK use case to a ViewModel, or says "domain layer" — even if they don't say "use case". Also used by make-viewmodel and make-screen when a ViewModel needs data.
---

# make-usecase: single-responsibility use cases

ViewModels never touch repositories (android-architecture §2): every read and command goes through a use case. A use case does **one** thing, named after it — `ObserveOrdersUseCase`, `PlaceOrderUseCase`, `ValidateCouponUseCase` — so business rules have one home, can be reused by several ViewModels, and are trivial to fake.

## Before you start
1. Read `../android-architecture/SKILL.md` and `.tezca-android.json`.
2. **kmp-sdk mode** → don't write a use case; bridge the SDK's (step "SDK bridge" below). Only write an app-side use case if it combines several SDK use cases with app-only logic, and then it injects those SDK use cases instead of repositories.
3. **native mode** → confirm the repository functions exist (make-repository first if not). Look in `domain/usecase/`: if a use case with the same responsibility exists, update it instead of adding a near-duplicate.

## Steps (native)
Templates: `references/templates.md`.
1. **Name** — verb first: `Observe<X>UseCase` (returns `Flow`), `Get<X>UseCase` (one-shot `suspend` → `DataResult`), `Refresh<X>UseCase`, or the business verb (`PlaceOrder`, `Login`, `CancelOrder`). If the request bundles two jobs ("load orders and mark them seen"), make two use cases.
2. **Class** — `domain/usecase/<Name>UseCase.kt`: `internal class <Name>UseCase @Inject constructor(private val repo: <X>Repository, …)` with a single public `operator fun invoke(...)`. No module entry needed: `@Inject constructor` classes are unscoped and Hilt builds them on demand.
3. **Body rules:**
   - Business logic lives here: validation (return `DataResult.Failure(AppError.Domain("invalid_email"))`, not exceptions), combining repositories (`combine(...)`), choosing what to refresh, computing derived values.
   - No Android/UI types (`Context`, `R`, `Color`, `UiText`), no DTOs/entities, no `Dispatchers.*` — the data layer is already main-safe. Use an injected `@Dispatcher(AppDispatcher.Default)` only for genuinely CPU-heavy work (sorting thousands of items, parsing).
   - Don't model loading (`Loading` states belong to the ViewModel); don't catch and swallow errors.
   - Time and randomness come from injected `Clock` / providers so tests are deterministic.
4. **Verify** — `./gradlew :feature:<name>:compileDebugKotlin`. Offer a unit test (add-tests): fake repositories, `runTest`, Turbine for Flow use cases. Use cases with rules always deserve one.

## SDK bridge (kmp-sdk mode)
1. Find the SDK use case: search the SDK artifact's classes (`./gradlew :core:sdk:dependencies` for the coordinate, then list classes from the AAR/JAR in `~/.gradle/caches`, or read the SDK repo if available) or ask the user for the exact class.
2. Add one provider under `// tezca-android:sdk-usecases` in `core/sdk/src/main/kotlin/<base>/core/sdk/di/SdkUseCaseModule.kt`, using the locator recorded in `.tezca-android.json` (`sdk.locator`):
   ```kotlin
   @Provides
   fun provideGetSoundUseCase(): GetSoundUseCase = SetupDi.getUseCase()
   ```
   Keep `ViewModelComponent` for use cases (stateless); stateful SDK services (session, player) go in a `SingletonComponent` module in the same package with `@Singleton`.
3. Verify with `./gradlew :app:assembleDevDebug`, and tell the user which ViewModels can now inject it.

## Output to the user
The use case signature(s), which repositories they use, and where to call them from (make-viewmodel).
