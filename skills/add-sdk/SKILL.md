---
name: add-sdk
description: Integrate a third-party library or service SDK into a native Android app from its documentation URL — Firebase (Crashlytics, Analytics, Messaging, Remote Config), RevenueCat / Play Billing, AdMob, Sentry, Mixpanel/Amplitude, Maps, Stripe, Coil, WorkManager, In-App Review/Updates, etc. Reads the docs, adds version-catalog entries, Gradle plugins, manifest entries, R8 rules and initialization, wraps the SDK behind a vendor-neutral interface (CrashReporter, AnalyticsTracker, PurchaseManager…) bound with Hilt in a :core module, adds a fake for tests, and hands the user a checklist of console/account steps. Use it whenever the user wants to add, install, set up, integrate or configure a library or SDK, pastes an SDK's docs link, or asks "how do I add X to the app" — even if they only name the product.
---

# add-sdk: integrate a library behind an interface

Features depend on **one capability interface** (`CrashReporter`, `AnalyticsTracker`, `PurchaseManager`), never on the vendor SDK, so it can be faked in tests and swapped later. Vendor code lives in one implementation class in a `:core:<capability>` module (or the single feature that needs it).

## 1. Gather inputs
1. **Docs URL(s)** — the official Android guide. Only a name given → find the official docs (web search / context7) and confirm the URL with the user.
2. **What they need from it** ("non-fatal logging + user id", "paywall + entitlement check"). Wrap only that, not the whole SDK.
3. **Keys / config files** they already have (API key, `google-services.json`). Never ask them to paste secrets into the chat; explain where each value goes (`local.properties` / env → `buildConfigField`, CI secret, `app/` folder).
4. Read `.tezca-android.json` and `../android-architecture/SKILL.md`. In kmp-sdk mode, check whether the SDK already ships this capability (e.g. the KMP SDK uses Firebase): don't add a second copy with conflicting versions — reuse the BOM the SDK expects.

## 2. Read the docs
Extract: artifact coordinates + latest **stable** version (verify on Maven Central / Google Maven — `../start-android/references/versions.md` shows how), Gradle plugins, manifest entries/permissions/meta-data, R8 rules, initialization code and where it must run, minSdk, and the API calls for what the user needs. Check compatibility with the project's AGP/Kotlin/compileSdk.

## 3. Integrate
`references/recipes.md` has worked examples (Crashlytics, RevenueCat, AdMob consent+banner, In-App Review, WorkManager with Hilt).
- **Catalog** — entries in `gradle/libs.versions.toml`, grouped and commented with the library name; BOMs where the vendor provides one. Never hardcode versions in build files.
- **Plugins** — root `build.gradle.kts` `alias(...) apply false`, applied in `app/build.gradle.kts` (google-services, crashlytics…). Firebase plugins fail without `google-services.json`: tell the user, and apply them only once the file exists (or guard on `file("google-services.json").exists()`).
- **Module** — capability used by several features → `:core:<capability>` (`tezca.android.library` + `tezca.android.hilt`; register it in `settings.gradle.kts`, `:app`, and `CORE_MODULES` in `AndroidFeatureConventionPlugin` if features need it). Used by one feature → in that feature's `data/`.
- **Interface + implementation** — `interface CrashReporter { fun log(message: String); fun recordNonFatal(t: Throwable); fun setUserId(id: String?) }` + `internal class FirebaseCrashReporter @Inject constructor(...) : CrashReporter`; `@Binds` in the module; `FakeCrashReporter` in `:core:testing`. Vendor callbacks → `suspendCancellableCoroutine` / `callbackFlow`; Play-services `Task`s → `kotlinx-coroutines-play-services` `.await()`.
- **Initialization** — in `Application.onCreate()` (after `super`) only if the docs require early init; otherwise lazily on first use or with App Startup. Never block the main thread (Q7 in the quality checklist): heavy init goes to `@ApplicationScope` on the IO dispatcher. Privacy-sensitive SDKs (ads, analytics) start only after consent when the docs/regulations require it.
- **Manifest / permissions** — only what the docs require; runtime permissions are requested in context by the screen that needs them.
- **R8** — the vendor's keep rules if the AAR doesn't ship consumer rules; verify with `./gradlew :app:assembleProdRelease` (or `minifyDevRelease`) when possible.
- **Secrets** — `local.properties`/env → `buildConfigField` in `app/build.gradle.kts` (dev/prod values per flavor); update `docs/ci-secrets.md` and the workflows' `env` if CI builds need them.

## 4. Verify
```bash
./gradlew :app:assembleDevDebug testDebugUnitTest
./gradlew :app:assembleDevRelease        # R8 rules, when the SDK has reflection/serialization
```
If something can't be verified here (console setup, device-only features like push or billing), say so explicitly.

## 5. Hand over a checklist
A short numbered list of what the user must do outside the code, with links from the docs (create the project/app in the vendor console for `<appId>` and `<appId>.dev`, download config files, add CI secrets, test accounts…), then the one-line usage from a ViewModel/use case (`crashReporter.recordNonFatal(e)`).
