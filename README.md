# tezca-android-development

Agent skills for **native Android** apps built on Google's [recommended architecture](https://developer.android.com/topic/architecture/recommendations) (MVVM + UDF, offline-first data layer, single-responsibility use cases), as a multi-module Jetpack Compose project. Two modes:

- **Full native** — the app owns its data layer: Room 3, DataStore, and Retrofit (default) or Ktor.
- **KMP-SDK consumer** — a Kotlin Multiplatform SDK provides use cases (and maybe ViewModels); the app gets Hilt bridges, Navigation 3 and the UI. (Build the SDK itself with the sibling plugin [tezca-kmp-development](https://github.com/AchsDCerrillo/tezca_kmp_plugin).)

Works as a Claude Code plugin; the same folders also install into Codex, Cursor, Antigravity, Gemini CLI, OpenCode, Copilot and OpenClaw.

**Version:** 0.1.0

## Skills
| Skill | What it does |
|---|---|
| `start-android` | Asks **KMP SDK or full native** (and Retrofit vs Ktor), then renders a verified multi-module setup: `build-logic` convention plugins, version catalog, `:app` (Hilt, edge-to-edge, Navigation 3 `NavDisplay`, dev/prod flavors with `BASE_URL`), `:core:{common,ui,navigation,testing}` + `:core:{network,database,datastore}` (native) or `:core:sdk` (SDK bridge + init), tests for the core modules, optional GitHub Actions (CI, Firebase App Distribution, Play internal). Then builds, tests and lints. |
| `new-feature <name>` | Creates `:feature:<name>` (data/domain/presentation, Hilt module, Navigation 3 entries, strings per locale) and registers it in settings, `:app` and the NavDisplay. |
| `make-api` | Retrofit service or Ktor Api, `@Serializable` DTOs, `safeApiCall`/`safeCall → DataResult`, a `RemoteDataSource` mapping DTOs to domain models. Auth-free clients for login, 401 → invalid credentials, server error bodies → domain errors, multipart, pagination. |
| `make-database` | Room 3 entity, DAO (Flow reads, `@Upsert`, transactional `replaceAll`), the feature's own database via `DatabaseFactory`, a `LocalDataSource`, migrations. |
| `make-preferences` | Typed DataStore preferences over the single DataStore; the session pattern (`:core:session`, `TokenProvider` for auth, logout, start destination from the session). |
| `make-repository` | **Offline-first** by default (Room = source of truth, TTL refresh via `SyncMetadataStore`, server-first writes), or remote-only ("don't do this offline"), local-only, offline writes, paginated. |
| `make-usecase` | One responsibility, one `operator fun invoke`, on one or more repositories. SDK mode: bridges SDK use cases into Hilt. |
| `make-viewmodel` | `@HiltViewModel`, single `UiState` via `stateIn(WhileSubscribed)`, `onAction`, one-off `Event`s through a `Channel`, Navigation 3 keys via assisted injection, `flatMapLatest` for search, SDK `ResultState` mapping. |
| `make-screen` | End to end: builds whatever is missing (api → database → repository → use cases → ViewModel), then Route + stateless Screen (Scaffold insets, loading/empty/error/pull-to-refresh), strings in every locale, previews, the NavKey + entry, and verifies. |
| `add-tests` | MockWebServer / MockEngine, Robolectric + in-memory Room, DataStore on a temp file, fakes, Turbine, `MainDispatcherRule`, Compose UI tests, migrations. |
| `add-sdk` | Integrates a third-party SDK from its docs URL behind a vendor-neutral interface (`CrashReporter`, `PurchaseManager`…) with Hilt, plus a console checklist. |
| `android-quality-check` | Audits a project against the architecture recommendations and core app quality checklist, reports P0/P1/P2 findings with `file:line` evidence, fixes what you approve. |
| `android-architecture` | The shared conventions every skill reads: layers, modules, naming, `DataResult`/`AppError`, Hilt rules, dispatchers, offline-first contract, presentation contract, strings/theming/a11y, SDK mode. |

## Typical flow
```
start-android                 → "full native, Retrofit, com.acme.shop, api.acme.com/v1/"
new-feature onboarding        → :feature:onboarding
make-screen login             → api (public client) + :core:session + LoginUseCase + LoginViewModel + LoginScreen
new-feature orders
make-screen order list        → offline-first list with pull to refresh, Room cache, detail screen
add-tests                     → ViewModel, repository and DAO tests
add-sdk https://firebase.google.com/docs/crashlytics/get-started?platform=android
android-quality-check         → report before a release
```
In KMP-SDK mode: `start-android` (SDK coordinates + repo) → `new-feature` → `make-screen` on top of the SDK's use cases.

## Architecture in one picture
```
Route/Screen ─▶ ViewModel ─▶ UseCase ─▶ Repository ─┬─▶ RemoteDataSource ─▶ Retrofit service / Ktor Api (safeApiCall → DataResult)
 (Compose)     (StateFlow)    (1 job)   (offline-1st) └─▶ LocalDataSource  ─▶ Room DAO / DataStore
:app ─▶ :feature:* ─▶ :core:{common, ui, navigation, network, database, datastore | sdk, testing}
```

## Install

### Claude Code (plugin, from the git marketplace)
```bash
claude plugin marketplace add https://github.com/AchsDCerrillo/tezca_android_plugin.git
claude plugin install tezca-android-development@tezca-android-marketplace                  # user scope (default)
claude plugin install tezca-android-development@tezca-android-marketplace --scope project  # this project only, shared via .claude/settings.json
```
Or inside Claude Code:
```
/plugin marketplace add AchsDCerrillo/tezca_android_plugin
/plugin install tezca-android-development@tezca-android-marketplace
```
Skills show up as `/tezca-android-development:start-android`, `/tezca-android-development:make-screen`, … and Claude also uses them automatically when a request matches. Update with `claude plugin marketplace update tezca-android-marketplace`, then restart Claude Code.

### Codex, Cursor, Antigravity, Gemini CLI, OpenCode, Copilot, OpenClaw (and Claude without the plugin)
```bash
git clone https://github.com/AchsDCerrillo/tezca_android_plugin.git && cd tezca_android_plugin
./install-skills.sh                                                           # interactive
./install-skills.sh --scope project --agents all --project-dir ~/code/my-android-app
./install-skills.sh --scope user --agents codex,gemini,antigravity
./install-skills.sh --scope user --agents codex --prefix android-             # alongside tezca-kmp-development
./install-skills.sh --scope user --agents openclaw --skills-dir ~/.openclaw/skills
./install-skills.sh --list-agents
git pull && ./install-skills.sh --scope user --agents codex --yes             # update later
./install-skills.sh --uninstall --scope project --agents all --project-dir .
```
| Agent | Project scope | User scope |
|---|---|---|
| Codex, OpenCode, Copilot, Cursor, Gemini, Antigravity | `<project>/.agents/skills/` | Codex / OpenCode / Copilot: `~/.agents/skills`; Gemini: `~/.gemini/skills`; Antigravity: `~/.gemini/config/skills`; Cursor: project only |
| Claude Code (plain folders) | `<project>/.claude/skills/` | `~/.claude/skills/` |

How the installer behaves:
- It only replaces folders it installed itself (each carries a `.tezca-android-install` marker) and never edits agent config files; `claude-plugin` goes through the official `claude plugin` CLI.
- All skills are installed together, because they reference each other (`../android-architecture/SKILL.md`).
- **Next to tezca-kmp-development:** both plugins have skills named `make-api`, `make-screen`, … In plain-folder installs to the same location, the installer stops before writing anything and asks for `--prefix android-` (folders become `android-make-api`, …, with names and cross-references rewritten). The Claude plugins are namespaced and never collide.

## Requirements
- JDK 17+ (Gradle provisions JDK 21 for unit tests through the foojay resolver; Robolectric needs it for SDK 36), Android SDK (`local.properties` or `ANDROID_HOME`), Gradle wrapper (generated by Android Studio, or `gradle wrapper`).
- `python3` for the scaffold scripts (standard library only, run as `python3 -I`).
- KMP-SDK mode: read access to the SDK's Maven repository (GitHub Packages: `GPR_USER` + `GPR_API_KEY` with `read:packages`).

## Pinned versions (verified together on 2026-10-17)
AGP 9.4.1 · Gradle 9.6.0 · Kotlin 2.4.21 · KSP 2.3.12 · Compose BOM 2026.09.00 · Hilt 2.60.1 · androidx.hilt 1.4.0 · Navigation 3 1.2.0 · Lifecycle 2.11.0 · Room 3.0.3 · DataStore 1.2.1 · Retrofit 3.0.0 · OkHttp 5.5.0 · Ktor 3.6.0 · Coroutines 1.11.0 · Serialization 1.11.0 · Robolectric 4.17 · compileSdk 37 / targetSdk 36 / minSdk 26.
`start-android` looks up newer stable releases and checks compatibility — see `skills/start-android/references/versions.md`.

## Repository layout
```
.claude-plugin/{plugin.json,marketplace.json}
skills/<skill>/SKILL.md (+ references/, scripts/, assets/templates/)
install-skills.sh
evals/evals.json            end-to-end prompts and assertions used to validate the skills
```

## Developing the skills
- `skills/start-android/assets/templates/{base,native,network-retrofit,network-ktor,kmp-sdk,ci}` are real, compilable files rendered by `scripts/scaffold.py` (placeholders `__PKG__`, `__APP_ID__`, `__APP_CLASS__`…; line blocks between `//@if <flag>` / `#@if <flag>` and `@endif` for `native`, `sdk`, `retrofit`, `ktor`, `ci`).
- After changing a template, render all three variants into a scratch folder and run `./gradlew :app:assembleDevDebug testDevDebugUnitTest testDebugUnitTest :app:lintDevDebug`.
- `evals/evals.json` holds the end-to-end prompts and assertions; run them with skill-creator.
