# add-sdk recipes

Base package `com.acme.shop`. Versions shown are examples — always look up the current stable release.

## Contents
1. Creating `:core:<capability>`
2. Firebase Crashlytics (BOM + plugins + interface)
3. RevenueCat (purchases behind PurchaseManager)
4. Play In-App Review (Activity-bound API)
5. WorkManager with Hilt (background sync)
6. Ads with consent (AdMob + UMP) — outline

## 1. Creating `:core:<capability>`
```
core/crash/build.gradle.kts
core/crash/src/main/kotlin/com/acme/shop/core/crash/{CrashReporter.kt, FirebaseCrashReporter.kt, di/CrashModule.kt}
```
```kotlin
plugins {
    alias(libs.plugins.tezca.android.library)
    alias(libs.plugins.tezca.android.hilt)
}
dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
}
```
`settings.gradle.kts`: `include(":core:crash")`; `app/build.gradle.kts`: `implementation(projects.core.crash)`; add `":core:crash"` to `CORE_MODULES` in `AndroidFeatureConventionPlugin.kt` if features report errors.

## 2. Firebase Crashlytics
Catalog:
```toml
[versions]
firebase-bom = "34.14.0"
firebase-crashlytics-plugin = "3.0.6"
[libraries]
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebase-bom" }
firebase-crashlytics = { module = "com.google.firebase:firebase-crashlytics" }
[plugins]
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version.ref = "firebase-crashlytics-plugin" }
```
Root: `alias(libs.plugins.firebase.crashlytics) apply false` (google-services is already declared). App:
```kotlin
plugins {
    // …
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebase.crashlytics)
}
```
Interface and implementation:
```kotlin
interface CrashReporter {
    fun log(message: String)
    fun recordNonFatal(throwable: Throwable)
    fun setUserId(id: String?)
}

internal class FirebaseCrashReporter @Inject constructor() : CrashReporter {
    private val crashlytics get() = FirebaseCrashlytics.getInstance()
    override fun log(message: String) = crashlytics.log(message)
    override fun recordNonFatal(throwable: Throwable) = crashlytics.recordException(throwable)
    override fun setUserId(id: String?) = crashlytics.setUserId(id.orEmpty())
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class CrashModule {
    @Binds abstract fun bindCrashReporter(impl: FirebaseCrashReporter): CrashReporter
}
```
Disable collection in debug: `<meta-data android:name="firebase_crashlytics_collection_enabled" android:value="${crashlyticsEnabled}" />` with `manifestPlaceholders["crashlyticsEnabled"] = false` in the debug build type and `true` in release.

Checklist: Firebase project → register `com.acme.shop` and `com.acme.shop.dev` → `google-services.json` (both clients) into `app/` → force a test crash on a release build.

## 3. RevenueCat
```kotlin
interface PurchaseManager {
    val isPremium: Flow<Boolean>
    suspend fun offerings(): DataResult<List<Plan>>
    suspend fun purchase(activity: Activity, planId: String): DataResult<Unit>   // Activity comes from the Route, never stored
    suspend fun restore(): DataResult<Unit>
}
```
- Configure in `Application.onCreate()` with the public API key from BuildConfig (`buildConfigField("String", "REVENUECAT_KEY", "\"${System.getenv("REVENUECAT_KEY") ?: ""}\"")`).
- `isPremium` = `callbackFlow` over `Purchases.sharedInstance.updatedCustomerInfoListener`, mapped to the entitlement id.
- `purchase` needs an `Activity`: the Route passes `LocalActivity.current` into an action handled by a small UI-layer helper, or the ViewModel emits an event the Route fulfils (`PurchaseRequested(planId)` → Route calls `purchaseManager.purchase(activity, planId)` via an injected launcher). Never keep an Activity in a ViewModel.
- Map vendor errors to `AppError.Domain("purchase_cancelled")` etc.

## 4. Play In-App Review
Activity-bound, so it's a UI-layer helper, not a repository:
```kotlin
class ReviewLauncher @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = ReviewManagerFactory.create(context)
    suspend fun launch(activity: Activity) {
        val info = runCatching { manager.requestReviewFlow().await() }.getOrNull() ?: return
        runCatching { manager.launchReviewFlow(activity, info).await() }
    }
}
```
The ViewModel decides *when* (use case with the rules: N orders placed, not asked in 90 days — stored with make-preferences) and emits `AskForReview`; the Route calls `reviewLauncher.launch(activity)`. This replaces passing `Context` and dispatchers from composables into ViewModels.

## 5. WorkManager with Hilt
Catalog: `androidx-work-runtime` (`androidx.work:work-runtime-ktx`), `androidx-hilt-work` (`androidx.hilt:hilt-work`) + `ksp(androidx.hilt:hilt-compiler)`.
```kotlin
@HiltWorker
internal class SyncOrdersWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val refreshOrders: RefreshOrdersUseCase,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (refreshOrders(force = true)) {
        is DataResult.Success -> Result.success()
        is DataResult.Failure -> if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
```
Application implements `Configuration.Provider` with the injected `HiltWorkerFactory`, and the manifest removes the default initializer (`androidx.work.WorkManagerInitializer` `tools:node="remove"` inside the `androidx.startup.InitializationProvider`). Enqueue unique periodic work with a `NetworkType.CONNECTED` constraint.

## 6. Ads with consent (outline)
UMP consent first (`UserMessagingPlatform.getConsentInformation`), `MobileAds.initialize` on a background thread only after `canRequestAds()`, ad unit ids per flavor in BuildConfig (test ids in dev), a `BannerAd` composable in `:core:ui` taking the unit id, and a premium check (`PurchaseManager.isPremium`) to hide ads. Keep the App ID in `manifestPlaceholders` per flavor rather than hardcoding the production id in the manifest.
