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

Pick a **scope** first — it decides who gets the skills and where they're recorded:

| Scope | Who gets the skills | Where it's recorded | Commit it? |
|---|---|---|---|
| **user** | you, in every project on this machine | Claude: `~/.claude/settings.json`; other agents: your home folder | no |
| **project** | everyone who clones the project | Claude: `<project>/.claude/settings.json`; other agents: `<project>/.agents/skills/` | yes — that's how the team gets it |
| **local** (Claude Code only) | only you, only in this project | `<project>/.claude/settings.local.json` (git-ignored) | no |

### Claude Code (plugin, from the git marketplace)
Two steps per scope: declare the marketplace, then install the plugin. Use the **same** `--scope` for both.

**User scope** — available in all your projects:
```bash
claude plugin marketplace add https://github.com/AchsDCerrillo/tezca_android_plugin.git --scope user
claude plugin install tezca-android-development@tezca-android-marketplace --scope user
```

**Project scope** — shared with the team through git:
```bash
cd ~/code/my-android-app
claude plugin marketplace add https://github.com/AchsDCerrillo/tezca_android_plugin.git --scope project
claude plugin install tezca-android-development@tezca-android-marketplace --scope project
git add .claude/settings.json && git commit -m "Add tezca-android-development plugin"
```
Teammates who open the project in Claude Code are asked to trust the marketplace and install the plugin.

**Local scope** — only you, only this project (nothing to commit):
```bash
cd ~/code/my-android-app
claude plugin marketplace add https://github.com/AchsDCerrillo/tezca_android_plugin.git --scope local
claude plugin install tezca-android-development@tezca-android-marketplace --scope local
```

Inside Claude Code you can do the same with `/plugin marketplace add AchsDCerrillo/tezca_android_plugin` and `/plugin install tezca-android-development@tezca-android-marketplace` (the `/plugin` menu asks for the scope).

Skills show up as `/tezca-android-development:start-android`, `/tezca-android-development:make-screen`, … and Claude also uses them automatically when a request matches.

| Task | Command (use the scope you installed with) |
|---|---|
| Check it's installed | `claude plugin list` |
| Update | `claude plugin marketplace update tezca-android-marketplace`, then restart Claude Code |
| Uninstall | `claude plugin uninstall tezca-android-development@tezca-android-marketplace --scope <user\|project\|local>` |
| Remove the marketplace | `claude plugin marketplace remove tezca-android-marketplace` |

### Codex, Cursor, Antigravity, Gemini CLI, OpenCode, Copilot, OpenClaw (and Claude without the plugin)
Clone once, then run `install-skills.sh` with the scope you want. Run it with no arguments for an interactive prompt.
```bash
git clone https://github.com/AchsDCerrillo/tezca_android_plugin.git && cd tezca_android_plugin
./install-skills.sh --list-agents          # codex, opencode, gemini, antigravity, copilot, cursor, openclaw, claude, claude-plugin
```

**User scope** — every project on this machine:
```bash
./install-skills.sh --scope user --agents codex,opencode,copilot,gemini,antigravity
./install-skills.sh --scope user --agents openclaw --skills-dir ~/.openclaw/skills   # OpenClaw needs its folder
```
Cursor has no user-level skills folder: install it at project scope.

**Project scope** — one project, shared through git:
```bash
./install-skills.sh --scope project --agents all --project-dir ~/code/my-android-app
cd ~/code/my-android-app && git add .agents/skills && git commit -m "Add tezca-android skills"
```
`all` = Claude plugin + Codex, OpenCode, Gemini, Antigravity, Copilot and Cursor (they all read `.agents/skills/`, so one copy serves them).

**Preview, update, uninstall** (same `--scope`/`--agents` you installed with):
```bash
./install-skills.sh --scope user --agents codex --dry-run                     # show what would change
git pull && ./install-skills.sh --scope user --agents codex --yes             # update
./install-skills.sh --uninstall --scope user --agents codex                   # remove
./install-skills.sh --uninstall --scope project --agents all --project-dir ~/code/my-android-app
```

**Next to tezca-kmp-development** (same folder): add `--prefix android-`, e.g. `./install-skills.sh --scope user --agents codex --prefix android-`.

Where the skills land:

| Agent | Project scope | User scope |
|---|---|---|
| Codex, OpenCode, Copilot, Cursor, Gemini, Antigravity | `<project>/.agents/skills/` | Codex / OpenCode / Copilot: `~/.agents/skills`; Gemini: `~/.gemini/skills`; Antigravity: `~/.gemini/config/skills`; Cursor: project only |
| Claude Code (plain folders, `--agents claude`) | `<project>/.claude/skills/` | `~/.claude/skills/` |
| OpenClaw | `--skills-dir` | `--skills-dir` |

Restart the agent after installing or updating so it picks up the skills. Gemini CLI may need Agent Skills enabled in its settings.

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
