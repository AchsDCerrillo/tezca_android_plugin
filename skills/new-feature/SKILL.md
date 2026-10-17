---
name: new-feature
description: Create a new feature module in an Android project set up with start-android (native or KMP-SDK mode) — a Gradle module :feature:<name> (tezca.android.feature convention plugin with Compose, Hilt, Navigation 3 and every :core module) with data/domain/presentation packages, a Hilt module, a Navigation 3 entries file and strings.xml for every locale — and register it in settings.gradle.kts, :app and the app's NavDisplay. Use it whenever the user wants to add a feature, module or area of the app ("create the onboarding module", "add a payments feature", "new module for user profile", "start the login → home flow"), passes a feature/module name, or when another skill needs a feature that doesn't exist yet — even if they don't say "module".
argument-hint: <feature-name>
---

# new-feature: add a feature module

A feature is a vertical slice that owns one area of the product — onboarding (registration → login → first-run), payments, profile, orders… It contains its own data, domain and presentation code and depends only on `:core:*` modules (android-architecture §2–3).

## Steps
1. **Read `.tezca-android.json`.** Missing → the project isn't set up: offer `start-android` (or, for an existing project with its own structure, follow its conventions by hand and say so).
2. **Agree on the name**: lowercase words separated by dashes (`onboarding`, `user-profile`, `payments`). It becomes module `:feature:user-profile`, package `<base>.feature.userprofile`, class prefix `UserProfile`, string prefix `user_profile_`.
   Suggest a better boundary when the request looks off:
   - Registration + login + forgot password belong together (`onboarding` or `auth`); home is its own feature reached through a callback.
   - A tiny settings toggle belongs in an existing feature, not a new module.
   - State several features need (the signed-in session, current user, cart) goes in a `:core:*` module (`:core:session` — make-preferences §2), never in a feature others import.
3. **Run the script** from the project root (Python 3, standard library):
   ```bash
   python3 -I <this-skill>/scripts/new_feature.py --project . --name <name> [--dry-run]
   ```
   It creates `feature/<name>/` (`build.gradle.kts`, `.gitignore`, `di/<Feature>Module.kt`, `navigation/<Feature>Navigation.kt`, `res/values*/strings.xml` for every locale `:core:ui` has — the `data/`, `domain/`, `presentation/` packages appear as the make-* skills add files), and inserts registrations below the `// tezca-android:` markers in `settings.gradle.kts` (`features`), `app/build.gradle.kts` (`feature-dependencies`) and `AppNavDisplay.kt` (`feature-entries` + import), then adds the feature to `.tezca-android.json`. Its JSON report lists `created`, `edited` and `manual`; do every `manual` item by hand (usually a marker was deleted).
   No Python? Reproduce it from `assets/templates/feature/__FEATURE__/` substituting `__FEATURE__` (user-profile), `__FPKG__` (userprofile), `__Feature__` (UserProfile), `__feature__` (userProfile), `__feature_snake__` (user_profile), `__Feature_Title__` (User Profile), `__PKG__`/`__PKG_PATH__` (base package), and add the three registrations by hand.
   In kmp-sdk mode the feature has no data layer: its Hilt module stays empty (delete it if nothing feature-specific needs binding) and SDK use cases are bridged in `:core:sdk`.
4. **Start destination.** If this is the first real feature and `AppNavDisplay` still starts at `WelcomeKey`, ask whether this feature's first screen should become the start (make-screen creates the key; then change `rememberNavigator(WelcomeKey)` and delete the `welcome/` placeholder). Session-dependent start (signed in → home, else login) needs both features' keys, so wire it after the second feature exists → make-preferences §6.
5. **Cross-feature navigation.** Hand-offs (onboarding → home) are callback parameters of `<feature>Entries(...)` (below `// tezca-android:entry-callbacks`) wired in `AppNavDisplay`:
   ```kotlin
   onboardingEntries(navigator = navigator, onSignedIn = { navigator.replaceAll(HomeKey) })
   ```
   Never add `project(":feature:other")` to a feature.
6. **Verify:** `./gradlew :feature:<name>:compileDebugKotlin :app:assembleDevDebug`.

## What next
Tell the user what was created, then suggest building the slice. **kmp-sdk:** `make-usecase` (SDK bridge) → `make-viewmodel` → `make-screen`. **native:** `make-api` (endpoints) → `make-database` / `make-preferences` (local data) → `make-repository` → `make-usecase` → `make-viewmodel` + `make-screen` per screen. `make-screen` can do the whole chain for one screen in a single pass.
