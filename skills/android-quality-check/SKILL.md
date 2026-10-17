---
name: android-quality-check
description: Audit an Android project (or a module, feature or PR diff) against Google's architecture recommendations and core app quality guidelines — layering and single source of truth, error handling, hardcoded dispatchers, Context in ViewModels, collectAsState vs collectAsStateWithLifecycle, business logic in composables, exposed mutable state, request races, kapt, duplicated build config, Room schema/migrations, DataStore misuse, retrying non-idempotent requests, logged secrets, edge-to-edge/insets, hardcoded strings and colors, accessibility, targetSdk, missing tests — and produce a prioritized report with file:line evidence, then fix what the user approves. Use it whenever the user asks to review, audit, assess, health-check or "find issues in" an Android app or module, asks whether code follows best practices or the architecture guide, prepares a release or Play submission, or inherits an old project — even if they don't say "audit".
---

# android-quality-check: audit and fix

Evidence first, then fixes. Never "fix" style the project deliberately chose differently unless it breaks a rule in the checklist.

## 1. Scope
- What to audit: whole project (default), one module/feature, or the current diff (`git diff <base>...HEAD`).
- Read `.tezca-android.json` if present, and the project's `CLAUDE.md`/`AGENTS.md` (project conventions win over generic ones when they conflict — note the conflict instead of flagging it).
- Ignore build outputs and vendored copies: `build/`, `.gradle/`, `.claude/worktrees/`, generated sources.

## 2. Scan
Walk `../android-architecture/references/quality-checklist.md` (checks D1–D10, U1–U4, V1–V8, B1–B7, Q1–Q11). Each row has a **Detect** column; use fast greps first, then read the hits to confirm (a grep hit is a lead, not a finding). Useful greps (run from the project root, Kotlin/Gradle/XML only):
```bash
rg -n 'Dispatchers\.(IO|Main|Default)' --type kotlin -g '!**/DispatchersModule.kt' -g '!**/test/**'
rg -n 'collectAsState\(' --type kotlin
rg -n 'GlobalScope|runBlocking' --type kotlin -g '!**/test/**'
rg -n 'catch \((e|t|ex): (Throwable|Exception)\)' --type kotlin
rg -n 'Context' --type kotlin -g '*ViewModel.kt'
rg -n 'kapt|dataBinding = true|viewBinding = true|exportSchema = false|allowMainThreadQueries' -g '*.kts' -g '*.kt' -g '*.gradle'
rg -n 'Text\("|contentDescription = "' --type kotlin
rg -n 'Color\(0x|Color\.(White|Black|Red)' --type kotlin -g '!**/theme/**'
rg -n 'preferencesDataStore\(' --type kotlin
rg -n 'HttpLoggingInterceptor|LogLevel\.(ALL|BODY)' --type kotlin
rg -n 'targetSdk|minSdk|compileSdk' -g '*.kts' -g '*.toml'
```
For each confirmed issue record: check id, severity (P0/P1/P2), `path:line`, one-line evidence, the fix. Group repeats ("Dispatchers.IO hardcoded in 14 files" + the list) instead of 14 findings.

Also run what exists: `./gradlew :app:lintDevDebug` (or `lint`), the unit tests, and note failures as findings.

## 3. Report
```
# Android quality report — <project/scope>
## Summary
<2–4 lines: overall health, biggest risks, counts per severity>
## P0 — fix before release
- [D6] Non-idempotent POSTs are retried — core/network/.../HttpClientProvider.kt:42 — `retryOnServerErrors` applies to all methods → retry only GET/PUT/DELETE
## P1 — architecture / quality rules
…
## P2 — maintainability
…
## What's already good
<short list — keeps the report honest and tells the user what to preserve>
## Suggested plan
<ordered fixes, each sized S/M/L, grouped into PR-sized chunks>
```
Offer to publish it as a page or doc if the user wants to share it with a team.

## 4. Fix (only what the user approves)
- One PR-sized group at a time, smallest risk first (build hygiene, dispatchers, lifecycle-aware collection) before structural refactors (moving logic out of composables, splitting modules).
- Follow the git rules of the project (branch + PR, never direct pushes to shared branches; read CLAUDE.md / memory for the target branch).
- Use the make-* skills' templates for replacements (e.g. a `DataResult`-returning data source, a `stateIn` ViewModel).
- After each group: build, unit tests, lint — and say exactly what was verified. Anything not verifiable locally (device-only behaviour, Play policy) is reported as such.
- Root causes in an external SDK the app consumes are reported with evidence for the SDK's owners, not patched around in the app.
