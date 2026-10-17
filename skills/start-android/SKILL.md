---
name: start-android
description: Set up a native Android project (Kotlin, Jetpack Compose, Material 3) with Google's recommended architecture and all the base dependencies wired — Hilt (KSP), Navigation 3, Coroutines/Flow, Lifecycle + ViewModel, Room 3, DataStore, Retrofit+OkHttp or Ktor, kotlinx-serialization — as a multi-module build (build-logic convention plugins, version catalog, :app + :core:* modules, dev/prod flavors with BASE_URL), ready to make network requests and store data locally, plus optional GitHub Actions CI/CD. First asks whether the app consumes a Kotlin Multiplatform SDK (then the SDK provides use cases/ViewModels and the app only gets DI, navigation and UI) or is fully native (Room + DataStore + Retrofit or Ktor). Use it whenever the user wants to start, bootstrap, scaffold or initialize an Android app, "add all the dependencies", prepare a new or empty Android Studio project, convert a single-module project to this architecture, or connect an app to a KMP SDK — even if they only say "prepare the project".
---

# start-android: set up the project

The result builds, tests and lints green, with networking, database, preferences, DI and navigation already wired. Every other skill (`new-feature`, `make-*`, `add-tests`, `add-sdk`, `android-quality-check`) builds on what this one generates, so follow `../android-architecture/SKILL.md`.

The deterministic work is done by `scripts/scaffold.py` (renders `assets/templates/`). Your job: ask the right questions, run it, merge files that already existed, refresh versions, verify, and explain what's left for the user (signing, Firebase, secrets).

## 1. Inspect the project
- Look for `settings.gradle.kts`, `gradle/libs.versions.toml`, `gradlew`, `app/`, `.tezca-android.json`.
- **`.tezca-android.json` exists** → already set up. Offer to refresh versions or add CI; don't re-scaffold over it.
- **Android Studio "Empty Activity" project** → fine: its `settings.gradle.kts`, `build.gradle.kts`, `libs.versions.toml`, `app/build.gradle.kts`, manifest, `MainActivity.kt` and theme files will be reported as conflicts and merged (step 4).
- **Empty folder** → also fine: the script generates everything except the Gradle wrapper; afterwards run `gradle wrapper --gradle-version <gradle from references/versions.md>` (or copy the wrapper from another project), and create `local.properties` with `sdk.dir=…` (`$ANDROID_HOME`, or `~/Library/Android/sdk` on macOS).
- **Existing app with its own architecture** → don't scaffold over it. Explain the gap, offer `android-quality-check`, and only add pieces the user approves.
- Note the existing package (`namespace`) and app name; offer them as defaults.

## 2. Ask (one AskUserQuestion round, defaults pre-filled)
1. **Project type** — the key question:
   - **Full native**: the app owns its data layer — Room, DataStore, and Retrofit or Ktor.
   - **Consumes a Kotlin Multiplatform SDK**: the SDK provides use cases (and maybe ViewModels); the app gets DI bridges, navigation and UI only. Ask for the SDK's Maven coordinates (`group:artifact:version`), its repository URL (e.g. `https://maven.pkg.github.com/<owner>/<repo>`), whether it exposes use cases, ViewModels or both, and how to get an instance (e.g. `SetupDi.getUseCase<T>()`).
2. **Network library** (native only): **Retrofit** (default — Retrofit 3 + OkHttp 5 + kotlinx-serialization converter) or **Ktor** (client with OkHttp engine; easier to move to KMP later).
3. **Base package** (e.g. `com.acme.shop`) and **app name** (e.g. `Acme Shop`); applicationId defaults to the package.
4. **API base URLs** for prod and dev (`api.acme.com/v1/`); placeholders are fine.
5. **CI/CD** with GitHub Actions: yes/no (CI on PRs; `dev` → Firebase App Distribution; GitHub Release → Play internal track).
6. Start with the placeholder welcome screen, or create the first feature right away (runs `new-feature` afterwards).

## 3. Resolve versions
Read `references/versions.md`. If its matrix is recent (≈ a month) keep the pins; otherwise look up the latest **stable** releases with the commands there and check the compatibility rules (Kotlin ↔ KSP ↔ Compose compiler, AGP ↔ Gradle ↔ compileSdk, Hilt ↔ AGP, Robolectric ↔ targetSdk). Edit the rendered `gradle/libs.versions.toml` after step 4 if versions changed.

In kmp-sdk mode, the app's `minSdk` must be ≥ the SDK's (read `minSdkVersion` from the SDK AAR's `AndroidManifest.xml` in `~/.gradle/caches/**/transforms/**/<artifact>-<version>/AndroidManifest.xml` after the first resolve, or ask) → pass `--min-sdk`.

## 4. Render
```bash
python3 -I <this-skill>/scripts/scaffold.py --dest <project> --mode native --network retrofit \
  --base-package com.acme.shop --app-name "Acme Shop" [--app-id com.acme.shop] \
  --base-url-prod api.acme.com/v1/ --base-url-dev dev.api.acme.com/v1/ [--min-sdk 26] [--ci] --dry-run

python3 -I <this-skill>/scripts/scaffold.py --dest <project> --mode kmp-sdk \
  --base-package com.acme.shop --app-name "Acme Shop" --min-sdk 29 \
  --sdk-coordinates com.acme:sdk:1.4.0 --sdk-repo https://maven.pkg.github.com/acme/acme-sdk \
  --sdk-exposes usecases --sdk-locator 'SdkLocator.get<T>()' [--ci] --dry-run
```
Show the user the dry-run plan (created / conflicts / appended), then run without `--dry-run`. The script never overwrites; for each **conflict**, diff the existing file with the staged one (path in the report's `staging`): if it's still the Android Studio default, take the staged version (`--force` writes `.bak` copies); if the user customized it, merge by hand — keep their dependencies, plugins, manifest entries and resources, adopt the template's structure (convention plugins, catalog aliases, markers). Android Studio puts sources in `app/src/main/java/<appId>/`, the templates in `app/src/main/kotlin/<appId>/`: same-named classes won't show up as conflicts, so delete the Studio `MainActivity.kt` (after moving anything custom into the generated one) and its `ui/theme/Color.kt`, `Theme.kt`, `Type.kt`, plus `ExampleUnitTest`, `ExampleInstrumentedTest`, the `themes.xml` `Theme.<Name>` duplicate if the names differ, and the `.bak` files. `.append` files (gradle.properties, .gitignore) are merged line by line automatically. `references/generated-layout.md` explains every generated file.

The script also writes `.tezca-android.json` (commit it; every skill reads it). Commit `core/database/schemas/` too (Room's exported schema; migrations are checked against it).

## 5. Mode specifics
- **kmp-sdk**:
  - Replace the `// tezca-android:sdk-init` line in `core/sdk/.../SdkInitializer.kt` with the SDK's real bootstrap (`SetupDi.start(context, …)`), passing `config.baseUrl`/`config.isDebug` if the SDK accepts them.
  - Credentials for GitHub Packages: env `GPR_USER`/`GPR_API_KEY`, or `gpr.user`/`gpr.key` in `~/.gradle/gradle.properties` (a token with `read:packages`). Never commit them.
  - If the SDK's transitive dependencies need a BOM (e.g. GitLive Firebase → `com.google.firebase:firebase-bom`), add it as `api(platform(...))` in `:core:sdk` (see `references/troubleshooting.md`).
  - `BASE_URL` per flavor: use the SDK's real environments (the URLs the SDK expects in its configuration), not the placeholder.
  - **Firebase / services the SDK uses internally:** check the SDK's dependencies (`./gradlew :core:sdk:dependencies`, look for `firebase`, `dev.gitlive`). If present, resolving any SDK use case needs a default FirebaseApp: apply `alias(libs.plugins.googleServices)` in `:app` once `app/google-services.json` exists (per flavor: `app/src/dev/` and `app/src/prod/`), and tell the user — without it the app builds but SDK screens crash when their ViewModel is created.
  - Tests stub SDK use cases with MockK (final classes); the feature convention plugin adds it in this mode.
  - Tell the user that make-api / make-database / make-repository belong to the SDK (the `tezca-kmp-development` plugin).
- **native**: `:core:network` is ready (`TokenProvider` is an optional binding until a session exists — make-preferences), `:core:database` has `DatabaseFactory` + `SyncMetadataStore`, `:core:datastore` the single DataStore.

## 6. CI/CD (if chosen)
`.github/workflows/{ci,dev,release}.yml` and `docs/ci-secrets.md`. Walk the user through `docs/ci-secrets.md`: keystore, Firebase app + service account, Play service account, first manual Play upload — steps that can't be automated from here.

## 7. Verify (don't skip)
```bash
./gradlew :app:assembleDevDebug
./gradlew testDevDebugUnitTest testDebugUnitTest      # includes the core modules' own tests
./gradlew :app:lintDevDebug
```
Unit tests run on a Java 21 launcher (Robolectric needs it for SDK 36); Gradle downloads it automatically through the foojay resolver. First builds take a few minutes; fix failures before reporting success (`references/troubleshooting.md`).

## 8. Update the project's agent docs
If the project has `CLAUDE.md` / `AGENTS.md`, add a short architecture summary: mode, modules, `.tezca-android.json`, the skills to use (`new-feature`, `make-screen`, …) and the verify commands. Otherwise offer to create one.

## 9. Report
Mode and module layout, versions chosen, what was verified and how, the manual follow-ups (signing, Firebase `google-services.json`, CI secrets, SDK credentials), and the next step: `new-feature <name>`, then `make-screen`.
