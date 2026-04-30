---
description: "Task list for Spec 001 — Project Init & Build Config"
---

# Tasks: Project Init & Build Config

**Input**: Design documents from [specs/001-project-init-build-config/](.)
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [quickstart.md](quickstart.md)

**Tests**: Spec does NOT request new test code. Existing template tests (`ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`) are migrated as-is. Test infrastructure (JUnit + Compose UI Test deps + Espresso) is wired so future specs can add tests without scaffolding work.

**Organization**: Tasks grouped by user story so each can be implemented and validated independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different file, no incomplete dependency — safe to parallelize
- **[Story]**: `US1`–`US5` maps to user stories from [spec.md](spec.md)
- File paths are repo-root-relative

## Path Conventions

Single-module Android app. Repo root is `/Users/xbism3/Documents/Code/jetpack/ThirdtySixBrowser/` (paths below omit this prefix).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Mechanical preparation that does not affect Gradle wiring yet — safe baseline before any user story.

- [X] T001 [P] Bump Gradle wrapper to 9.5.0: edit `gradle/wrapper/gradle-wrapper.properties` setting `distributionUrl=https\://services.gradle.org/distributions/gradle-9.5-bin.zip`; run `./gradlew wrapper --gradle-version 9.5 --distribution-type bin` to regenerate `gradle-wrapper.jar` if needed.
- [X] T002 [P] Update `gradle.properties` to add the following lines (preserving existing template content): `android.bundle.enableUncompressedNativeLibs=true`, `org.gradle.java.installations.auto-download=true`, `org.gradle.caching=true`, `org.gradle.parallel=true`, `kotlin.code.style=official`.
- [X] T003 Migrate Kotlin source files from `app/src/main/java/com/raumanian/thirtysix/browser/` to `app/src/main/kotlin/com/raumanian/thirtysix/browser/` (preserve package declarations; move `MainActivity.kt`, `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/theme/Type.kt`); delete the now-empty `app/src/main/java/com/raumanian/thirtysix/browser/` directory tree.
- [X] T004 [P] Migrate unit test sources: move `app/src/test/java/com/raumanian/thirtysix/browser/ExampleUnitTest.kt` to `app/src/test/kotlin/com/raumanian/thirtysix/browser/ExampleUnitTest.kt`; delete now-empty `app/src/test/java/com/raumanian/thirtysix/browser/` tree.
- [X] T005 [P] Migrate androidTest sources: move `app/src/androidTest/java/com/raumanian/thirtysix/browser/ExampleInstrumentedTest.kt` to `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/ExampleInstrumentedTest.kt`; delete now-empty `app/src/androidTest/java/com/raumanian/thirtysix/browser/` tree.

**Checkpoint**: Source files in canonical `kotlin/` location; gradle properties primed; wrapper at 9.5. Project does NOT build yet (Foundational phase wires the catalog).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Wire the version catalog + minimal Gradle scripts so `assembleDebug` can succeed. This phase blocks ALL user stories — without it, no Gradle command works.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T006 Create `gradle/libs.versions.toml` with all baseline versions verified in [research.md](research.md): write `[versions]` block (agp=9.2.0, kotlin=2.3.21, composeBom=2026.04.01, coreKtx=1.18.0, lifecycleRuntimeKtx=2.10.0, activityCompose=1.13.0, junit=4.13.2, junitExt=1.3.0, espressoCore=3.7.0, detekt=1.23.8, ktlint=14.2.0); `[libraries]` referencing each version (NOT pinning compose.ui/material3 individually — let BOM resolve them); `[plugins]` for `android-application`, `kotlin-android`, `kotlin-compose`, `detekt`, `ktlint`; `[bundles]` for `compose` aggregating ui + ui-graphics + ui-tooling-preview + material3.
- [X] T007 Create/update `settings.gradle.kts` at repo root: set `rootProject.name = "ThirtySixBrowser"`; configure `pluginManagement { repositories { gradlePluginPortal(); google(); mavenCentral() } }`; configure `dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }`; include `":app"`.
- [X] T008 Update root `build.gradle.kts` (repo root) to declare top-level plugins via catalog aliases with `apply false`: `alias(libs.plugins.android.application) apply false`, `alias(libs.plugins.kotlin.android) apply false`, `alias(libs.plugins.kotlin.compose) apply false`, `alias(libs.plugins.detekt) apply false`, `alias(libs.plugins.ktlint) apply false`. Remove all existing version literals from this file.
- [X] T009 Rewrite `app/build.gradle.kts` with foundational configuration: `plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }`; `android { namespace = "com.raumanian.thirtysix.browser"; compileSdk = 36; compileSdkExtension = 1; defaultConfig { applicationId = "com.raumanian.thirtysix.browser"; minSdk = 24; targetSdk = 36; versionCode = 1; versionName = "1.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }; buildFeatures { compose = true } }`; `kotlin { jvmToolchain(11) }`; `dependencies { implementation(libs.androidx.core.ktx); implementation(libs.androidx.lifecycle.runtime.ktx); implementation(libs.androidx.activity.compose); implementation(platform(libs.androidx.compose.bom)); implementation(libs.bundles.compose); testImplementation(libs.junit); androidTestImplementation(libs.androidx.junit); androidTestImplementation(libs.androidx.espresso.core); androidTestImplementation(platform(libs.androidx.compose.bom)); androidTestImplementation(libs.androidx.compose.ui.test.junit4); debugImplementation(libs.androidx.compose.ui.tooling); debugImplementation(libs.androidx.compose.ui.test.manifest) }`. KEEP existing `buildTypes { debug { applicationIdSuffix = ".debug"; isDebuggable = true } release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }` — signing wired in US3.
- [X] T010 Sanity build: run `./gradlew clean assembleDebug`; expect success. If Detekt-related errors surface (CV-05) document but proceed — Detekt not yet wired in this phase.

**Checkpoint**: `./gradlew assembleDebug` succeeds end-to-end. Foundational ready — user story phases can begin.

---

## Phase 3: User Story 1 — Build & Run với câu lệnh tối thiểu (Priority: P1) 🎯 MVP

**Goal**: Developer chạy `./gradlew clean assembleDebug` thành công với version catalog là single source of truth — không còn version literal trong bất kỳ `*.gradle.kts` nào.

**Independent Test**: From [quickstart.md](quickstart.md) §1 + §3 — run the version-literal grep + `./gradlew clean assembleDebug` on a primed cache; both pass.

### Implementation for User Story 1

- [X] T011 [US1] Audit `app/build.gradle.kts`, root `build.gradle.kts`, and `settings.gradle.kts` for any leftover version literals (numeric strings like `"1.2.3"`); replace each with a catalog reference. Run `grep -rE '"[0-9]+\.[0-9]+\.[0-9]+"' --include="*.gradle.kts" .` from repo root; expect zero hits (excluding `applicationIdSuffix`, `compileSdkExtension`, comments).
- [X] T012 [US1] Verify Gradle Toolchain auto-provision works: run `./gradlew --no-daemon assembleDebug` (forces fresh JVM provision check); expect exit 0; build log should mention provisioning JDK 11 or using existing toolchain. Confirm output APK at `app/build/outputs/apk/debug/app-debug.apk`.

**Checkpoint**: US1 complete — build foundation works. MVP demo: clone-fresh + `./gradlew assembleDebug` succeeds.

---

## Phase 4: User Story 2 — Static analysis chạy được với một lệnh (Priority: P1)

**Goal**: `./gradlew lintDebug detekt ktlintCheck` cả ba pass với zero violation (Detekt baseline accepted nếu cần cho code template; Lint strict, không baseline).

**Independent Test**: From [quickstart.md](quickstart.md) §3 + §7 — run the three static-analysis commands on clean tree (all pass); add a deliberate `MagicNumber` violation, re-run `detekt` (must fail) → cleanup → re-run (passes).

### Implementation for User Story 2

- [X] T013 [P] [US2] Create `detekt.yml` at repo root using `./gradlew detektGenerateConfig` as a starting point, then enable `MagicNumber` rule and configure `MagicNumber.excludes: ['**/src/test/**', '**/src/androidTest/**']`. Verify `MagicNumber.ignoreNumbers` retains the default whitelist `['-1', '0', '1', '2']` per Constitution §III trivial whitelist (do NOT shrink). Keep all other default rules at their template values.
- [X] T014 [P] [US2] Create `.editorconfig` at repo root with the standard ktlint defaults: `[*.{kt,kts}]` `ktlint_standard_no-wildcard-imports = enabled`, `ktlint_code_style = android_studio`, `indent_size = 4`, `max_line_length = 140`. Add `[*]` `end_of_line = lf`, `charset = utf-8`, `insert_final_newline = true`.
- [X] T015 [US2] Apply Detekt + ktlint plugins in `app/build.gradle.kts` via catalog aliases: add `alias(libs.plugins.detekt)` and `alias(libs.plugins.ktlint)` to the module `plugins {}` block. Configure `detekt { config.setFrom(files("$rootDir/detekt.yml")); buildUponDefaultConfig = true; allRules = false }`. Configure `ktlint { android.set(true); ignoreFailures.set(false) }`.
- [X] T016 [US2] Configure strict Android Lint in `app/build.gradle.kts` `android { lint { abortOnError = true; warningsAsErrors = true; checkReleaseBuilds = true; checkDependencies = false } }`. Per Q4 — NO `lintBaseline` file allowed.
- [X] T017 [US2] Run `./gradlew lintDebug`; if any warning surfaces from migrated template code, fix it directly in the offending source file (do not silence with `@Suppress` unless absolutely justified with a comment). Re-run until exit 0.
- [X] T018 [US2] Run `./gradlew ktlintCheck`; if violations surface from migrated template code, run `./gradlew ktlintFormat` to auto-fix and commit the formatting changes. Re-run `ktlintCheck` until exit 0.
- [X] T019 [US2] Run `./gradlew detekt`; if violations surface from template, generate baseline once: `./gradlew detektBaseline`; commit the resulting `app/detekt-baseline.xml` (or repo-root `detekt-baseline.xml` per plugin config). Add a git-tracked comment in `detekt.yml` noting the date the baseline was generated and that it is one-time-only for template code. **If Detekt task crashes per CV-05** (Kotlin 2.3 ↔ Detekt 1.23.8 incompat): try setting `detekt { toolVersion = "1.23.8" }`; if still crashes, escalate as blocker.
- [X] T020 [US2] Verify strict guard works: create temporary file `app/src/main/kotlin/com/raumanian/thirtysix/browser/_temp_violation.kt` with content `package com.raumanian.thirtysix.browser` + `val deliberateMagicNumber = 4242`. Run `./gradlew detekt`; expect FAIL with `MagicNumber` violation reported. Delete the temp file. Re-run `detekt`; expect pass.

**Checkpoint**: US2 complete — `lintDebug detekt ktlintCheck` all pass; strict guard against future violations confirmed.

---

## Phase 5: User Story 3 — Release build pass dù chưa có signing key (Priority: P1)

**Goal**: `./gradlew assembleRelease` exit 0 trên máy không có release keystore; output APK ký bằng debug keystore với warning rõ ràng.

**Independent Test**: From [quickstart.md](quickstart.md) §3 last command + §4 — assembleRelease succeeds with the literal debug-fallback warning; resulting APK installs via `adb install`.

### Implementation for User Story 3

- [X] T021 [US3] Add release signing config to `app/build.gradle.kts` per FR-013: `signingConfigs { create("release") { val keystorePath = (project.findProperty("KEYSTORE_PATH") as String?) ?: System.getenv("KEYSTORE_PATH"); if (keystorePath != null) { storeFile = file(keystorePath); storePassword = (project.findProperty("KEYSTORE_PASSWORD") as String?) ?: System.getenv("KEYSTORE_PASSWORD"); keyAlias = (project.findProperty("KEY_ALIAS") as String?) ?: System.getenv("KEY_ALIAS"); keyPassword = (project.findProperty("KEY_PASSWORD") as String?) ?: System.getenv("KEY_PASSWORD") } } }`. Wire `buildTypes.release.signingConfig` to either `signingConfigs.getByName("release")` if `keystorePath != null` else `signingConfigs.getByName("debug")`. Print warning `"⚠️ release built with DEBUG signature — NOT for distribution"` via `gradle.taskGraph.whenReady` or a `doFirst` block on the `:app:packageRelease` task when fallback is active.
- [X] T022 [US3] Read `local.properties` (if present) for keystore credentials before falling back to env vars. Use Gradle's built-in `Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }` pattern; precedence: env var > local.properties > none-→-fallback.
- [X] T023 [US3] Add `local.properties` entry to `.gitignore` if not already present (Android template usually does — verify).
- [X] T024 [US3] Run `./gradlew assembleRelease` with NO keystore configured: expect exit 0; expect log to contain `"⚠️ release built with DEBUG signature — NOT for distribution"`; expect output `app/build/outputs/apk/release/app-release.apk` to exist. If a device/emulator is connected, run `adb install -r app/build/outputs/apk/release/app-release.apk` and verify install succeeds.

**Checkpoint**: US3 complete — release build works graceful without keystore; ready for user to add real keystore later without script changes.

---

## Phase 6: User Story 4 — CI chạy đủ pipeline + 16KB step (Priority: P2)

**Goal**: PR mới mở → GitHub Actions chạy 5 Gradle task + 16KB-verify step (fail-soft); duration < 10 min trên `ubuntu-latest`.

**Independent Test**: From [quickstart.md](quickstart.md) §8 — open the PR for this branch, observe Actions run, confirm pipeline content + duration + 16KB skip log.

### Implementation for User Story 4

- [X] T025 [P] [US4] Create script `.specify/scripts/bash/verify-16kb-alignment.sh` that takes one argument (APK path), unzips `lib/*/*.so`, runs `objdump -p` (or `llvm-objdump -p`) on each, parses LOAD segment alignment, fails with exit 1 if any segment is < 0x4000, exits 0 with `[16kb] no native libraries to verify (skip)` if no `lib/` or no `.so` found, exits 0 with success message if all aligned. Make executable (`chmod +x`).
- [X] T026 [US4] Update `.github/workflows/ci.yml` job `build`: ensure these Gradle steps run in this order on every PR — `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, `./gradlew lintDebug`, `./gradlew detekt`, `./gradlew ktlintCheck`. Each step name/title clearly identifies it.
- [X] T027 [US4] Add to the same job a step `Verify 16KB alignment` that runs `./gradlew assembleRelease` (debug-signed fallback OK) followed by `./.specify/scripts/bash/verify-16kb-alignment.sh app/build/outputs/apk/release/app-release.apk`. Place after the linting steps; allow the step to be soft (the script itself is fail-soft for missing `.so`).
- [X] T028 [US4] Confirm `instrumented-test` job still has `if: false` (per FR-023); add a YAML comment above it: `# Re-enable at Spec 007 (webview-compose-wrapper) or Spec 011 (tabs-management) when first real UI test is added.`
- [X] T029 [US4] Verify workflow uses `actions/checkout@v4` and includes `android-actions/setup-android@v3` (for Android SDK API 36). KEEP default `ubuntu-latest` runner JDK (≥ 17) — do NOT pin JDK 11 launcher (FR-025).
- [X] T030 [US4] Push branch and observe the PR run (manual step). Confirm: 5 Gradle steps + 16KB step all green; duration logged < 10 min; instrumented-test shows skipped. **Verify FR-024 negative**: `grep -E '(release|sign|keystore)' .github/workflows/ci.yml` MUST return zero matches outside of comments — confirms no release-build job leaked into Spec 001 CI.

**Checkpoint**: US4 complete — CI is the durable quality gate for every PR.

---

## Phase 7: User Story 5 — Constants namespace skeleton (Priority: P3)

**Goal**: `core/constants/AppConstants.kt` exists with empty stub so future specs extend the namespace consistently.

**Independent Test**: From [quickstart.md](quickstart.md) §2 — file exists; `assembleDebug` and `detekt` both still pass with the file present.

### Implementation for User Story 5

- [X] T031 [US5] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/AppConstants.kt` with content: `package com.raumanian.thirtysix.browser.core.constants` + a single `object AppConstants` with one comment line `// Constants for the spec-001 scope; future specs extend this namespace via separate files.` and one placeholder `const val APP_NAME = "ThirtySix Browser"` (the only actual app-name string already canonical from Constitution / CLAUDE.md). No other content.
- [X] T032 [US5] Re-run `./gradlew assembleDebug detekt ktlintCheck`; all three must still pass with the new file in place.

**Checkpoint**: US5 complete — namespace anchor in place.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Documentation updates and end-to-end verification before marking spec done.

- [X] T033 Run the full quickstart.md acceptance checklist (steps §1–§7 locally, §8 after PR push). Resolve any failures. Capture pass evidence (terminal output) in PR description.
- [X] T034 [P] Update [CLAUDE.md](../../CLAUDE.md) "Recent Changes" section per FR-028: prepend `- 2026-04-30: Spec 001 done — version catalog wired (AGP 9.2.0, Kotlin 2.3.21, Gradle 9.5, Compose BOM 2026.04.01); java/→kotlin/ source migrated; Detekt + ktlint + Lint strict; debug-signed release fallback; CI 5-task pipeline + 16KB fail-soft step.` Update the SPECKIT block status to `🟢 Implemented`.
- [X] T035 [P] Update [.claude/claude-app/project-context.md](../../.claude/claude-app/project-context.md) "Key Decisions Log" per FR-029: add an entry with date, AGP/Kotlin/Gradle/BOM versions, JDK 17 launcher requirement, debug-fallback signing decision (Q2), and CV-05 status (resolved or tracked).
- [X] T036 [P] Update [.claude/claude-app/sdd-roadmap.md](../../.claude/claude-app/sdd-roadmap.md) per FR-030: change Spec 001 status from `⬜ Next` to `✅ Done`; update last-updated date.
- [X] T037 Verify `gradle/libs.versions.toml` is the *only* place version literals live: run `grep -rE '"[0-9]+\.[0-9]+\.[0-9]+"' --include="*.gradle.kts" .` from repo root; expect zero hits (other than allowed exceptions like `compileSdkExtension = 1` which is a numeric literal, not a version string).
- [X] T038 Final clean-build sanity sweep from a fresh Gradle daemon. Wrap with `time` to capture wall-clock for SC-001 / SC-003 verification: `./gradlew --stop && time ./gradlew clean assembleDebug testDebugUnitTest lintDebug detekt ktlintCheck assembleRelease` — every task exit 0; release log includes the debug-fallback warning; record total wall-clock in PR description (SC-001 target: under 5 min for `assembleDebug` alone on a primed cache; full sweep no formal limit but should be < 10 min). Note: SC-008 (future-spec proofing) is observational and validated only when Spec 002 begins — not a blocker for marking 001 done.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — can start immediately. T001/T002/T004/T005 are [P]; T003 sequential due to physical file moves.
- **Phase 2 (Foundational)**: Depends on Phase 1. **BLOCKS** all user stories. T006 → T007 → T008 → T009 → T010 strictly sequential (each writes the file the next reads).
- **Phase 3 (US1)**: Depends on Phase 2. T011 → T012.
- **Phase 4 (US2)**: Depends on Phase 2 (independent of Phase 3). T013/T014 [P]; then T015 → T016 → T017 → T018 → T019 → T020 sequential.
- **Phase 5 (US3)**: Depends on Phase 2 (independent of Phase 3, 4). T021 → T022 → T023 → T024 sequential.
- **Phase 6 (US4)**: Depends on Phase 2 + ideally Phase 4, 5 working (CI runs them). T025 [P with anything in US4]; then T026 → T027 → T028 → T029 → T030 sequential.
- **Phase 7 (US5)**: Depends on Phase 2. T031 → T032.
- **Phase 8 (Polish)**: Depends on Phase 3 + 4 + 5 + 6 + 7 complete. T033 first; then T034/T035/T036 [P]; then T037 → T038.

### User Story Dependencies

- **US1, US2, US3, US5**: All independent of each other after Phase 2.
- **US4 (CI)**: Strictly speaking only depends on Phase 2, but CI verifies the work of US2 + US3 — so practically schedule US4 after US2 + US3.
- **US5**: Trivial; can interleave with US1.

### Within Each User Story

- US1: T011 must run before T012 (audit before re-run).
- US2: T013/T014 can run in parallel (different files); T015–T020 strictly sequential (each task makes the next runnable).
- US3: T021–T024 strictly sequential (signing config flow).
- US4: T025 can run before workflow edits; T026 → T027 → T028 → T029 sequential (same workflow file); T030 last (PR push).
- US5: T031 → T032.

### Parallel Opportunities

- Phase 1: T001, T002, T004, T005 all [P] (different files).
- Phase 4 start: T013 (`detekt.yml`) + T014 (`.editorconfig`) [P].
- Phase 6 start: T025 (verify script) [P with edits to `ci.yml`].
- Phase 8: T034, T035, T036 [P] (three different documentation files).
- Cross-story: After Phase 2, US1 / US2 / US3 / US5 can be picked up by different developers.

---

## Parallel Example: Phase 1 Setup

```bash
# All four tasks edit different files — safe to launch in parallel:
Task T001: bump Gradle wrapper to 9.5.0 in gradle/wrapper/gradle-wrapper.properties
Task T002: append 16KB + perf flags to gradle.properties
Task T004: migrate src/test/java/ → src/test/kotlin/ for ExampleUnitTest.kt
Task T005: migrate src/androidTest/java/ → src/androidTest/kotlin/ for ExampleInstrumentedTest.kt

# T003 (main source migration) is sequential because it moves multiple files in one logical commit
```

## Parallel Example: Phase 8 Documentation

```bash
# Three documentation files, no shared content lines:
Task T034: update CLAUDE.md Recent Changes + SPECKIT status
Task T035: update .claude/claude-app/project-context.md Key Decisions Log
Task T036: update .claude/claude-app/sdd-roadmap.md (mark 001 ✅ Done)
```

---

## Implementation Strategy

### MVP First (US1 only)

1. Complete Phase 1: Setup (5 tasks).
2. Complete Phase 2: Foundational (5 tasks) — **CRITICAL gate**.
3. Complete Phase 3: US1 (2 tasks).
4. **STOP and VALIDATE**: run `./gradlew assembleDebug` + grep version-literal check.
5. If all green → core build foundation works; remaining stories add quality gates and CI.

### Incremental Delivery

1. Setup + Foundational → Foundation ready.
2. + US1 → MVP: assembleDebug works (commit + push).
3. + US2 → static analysis green (commit + push).
4. + US3 → release build works (commit + push).
5. + US4 → CI gate active (commit + push, observe PR run).
6. + US5 → namespace anchor (commit + push).
7. + Polish → docs updated, full sweep passes (final commit + open PR for review).

### Risk-Driven Order Adjustment

If CV-05 (Detekt 1.23.8 ↔ Kotlin 2.3) materializes during T019:

- Pause Phase 4. Evaluate Detekt 2.x release status or pin `detekt { toolVersion }` decoupled.
- If unresolvable, escalate as spec blocker — may require pinning Kotlin to 2.0.x temporarily and re-recording the decision in research.md / spec.md.

---

## Notes

- `[P]` tasks = different files, no dependencies.
- `[Story]` label maps task to specific user story for traceability.
- Each user story is independently completable and verifiable per [quickstart.md](quickstart.md).
- Commit after each completed task or logical group (per dev-workflow.md Bước 6/7).
- Stop at any checkpoint to validate independently.
- Avoid: vague tasks, same-file conflicts within `[P]` group, cross-story dependencies that break independence.
