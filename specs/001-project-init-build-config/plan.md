# Implementation Plan: Project Init & Build Config

**Branch**: `001-project-init-build-config` | **Date**: 2026-04-30 (revised 2026-05-01 post-analyze) | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-project-init-build-config/spec.md`
**Constitution**: v1.2.0 (amended 2026-05-01 — §XI signing two-scope rule)

## Summary

Spec 001 lays the build foundation for ThirtySixBrowser: a Gradle Kotlin DSL configuration with a single version-catalog source of truth, two strict build types (debug + release with debug-keystore fallback), full static-analysis wiring (Lint zero-warning policy, Detekt + ktlint with MagicNumber on `src/main/**` only), 16KB-readiness gradle property + a fail-soft CI verification step, source migration from `java/` → `kotlin/`, and a stub `core/constants/AppConstants.kt` to anchor the constants namespace for downstream specs.

**Technical approach**: Catalog all baseline versions (looked up 2026-04-30 — see [research.md](research.md)) — AGP 9.2.0, Kotlin 2.3.21 (with bundled Compose Compiler plugin), Gradle 9.5, Compose BOM 2026.04.01. Java target 11 via Gradle Toolchain (auto-provisioned), launcher JDK 17+ required by AGP 9.x. CI runs five Gradle tasks per PR (assembleDebug, testDebugUnitTest, lintDebug, detekt, ktlintCheck) plus the fail-soft 16KB step. No new entities, no public contracts — pure infrastructure spec.

## Technical Context

**Language/Version**: Kotlin **2.3.21** / Java target **11** (compile via Gradle Toolchain) / Java launcher **17+** (Gradle daemon)
**Primary Dependencies**:
- Build: Android Gradle Plugin **9.1.1** (pinned to 9.1.x for Android Studio compat — IDE doesn't support 9.2 yet), Gradle **9.5.0**, Kotlin Compose plugin **2.3.21** (matches Kotlin)
- Static analysis: Detekt Gradle plugin **1.23.8**, ktlint Gradle plugin (`org.jlleitschuh.gradle.ktlint`) **14.2.0**, Android Lint (built into AGP)
- App baseline: Compose BOM **2026.04.01** (resolves Compose UI 1.11.0 + Material3 1.4.0), `androidx.core:core-ktx` **1.18.0**, `androidx.lifecycle:lifecycle-runtime-ktx` **2.10.0**, `androidx.activity:activity-compose` **1.13.0**
- Test baseline: `junit:junit` **4.13.2**, `androidx.test.ext:junit` **1.3.0**, `androidx.test.espresso:espresso-core` **3.7.0**, `androidx.compose.ui:ui-test-junit4` (BOM-managed), `androidx.compose.ui:ui-test-manifest` (BOM-managed), `androidx.compose.ui:ui-tooling` (BOM-managed)

**Storage**: N/A (Spec 001 is build foundation; Room + DataStore enter at Specs 005/006)
**Testing**: JUnit 4 unit tests (template defaults retained); Compose UI test harness wired but no new tests added in 001
**Target Platform**: Android — `minSdk = 24`, `targetSdk = 36`, `compileSdk = 36` with `compileSdkExtension = 1` (or release-block `minorApiLevel = 1`)
**Project Type**: Android single-module application (`app/`)
**Performance Goals**: SC-001 — clean assembleDebug under 5 min on a primed cache; SC-005 — full CI pipeline under 10 min on `ubuntu-latest`
**Constraints**:
- Constitution §III: zero hardcoded values; Detekt MagicNumber active on `src/main/**`
- Constitution §IX: AGP ≥ 8.5 (we choose 9.2), NDK r27+ (deferred), `android.bundle.enableUncompressedNativeLibs=true`
- Constitution §XI: only `debug` + `release` build types, no flavors
- Q1 clarification: source set is `kotlin/`, not `java/`
- Q2 clarification: missing release keystore → debug-keystore fallback with warning
- Q3 clarification: Gradle Toolchain JVM 11 (auto-provision)
- Q4 clarification: `warningsAsErrors = true` + `abortOnError = true` for **all** build types; no Lint baseline
- Q5 clarification: Detekt MagicNumber excludes `**/src/test/**` and `**/src/androidTest/**`

**Scale/Scope**: Single Gradle module (`app/`), ~5–10 Kotlin files migrated from template, single CI workflow file modified, ~30 functional requirements covered

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase-0 evaluation (recorded 2026-04-30)

| Principle | Verdict | Notes |
|-----------|---------|-------|
| I. Privacy & Security First | ✅ N/A | No user data flow in this spec |
| II. Google Play Compliance | ✅ Pass | targetSdk = 36 honored; no permission additions; package id correct |
| III. Code Quality & Safety | ✅ Pass (with caveat) | All linters wired strict; Detekt baseline allowed for template-only violations (FR-020); MagicNumber main-only per Q5; no Lint baseline (Q4 strict). **Caveat**: Detekt 1.23.8 ↔ Kotlin 2.3 compat risk (CV-05) — mitigation in research.md, must validate during implementation. |
| IV. Clean Architecture | ✅ Pass | `core/constants/` skeleton honors layer; no domain/data/presentation code added; future spec ownership respected |
| V. Performance Excellence | ✅ N/A | No runtime perf targets in 001 |
| VI. Testing Discipline | ✅ Pass | Test infrastructure (JUnit + Compose UI Test deps + Espresso) wired; no new test code expected; CI runs `testDebugUnitTest` |
| VII. Offline-First Architecture | ✅ N/A | No data persistence in 001 |
| VIII. Localization & Accessibility | ✅ N/A | No user-facing strings; locale resources begin Spec 004 |
| IX. Dependency Currency & 16KB Compliance | ✅ Pass | All versions verified 2026-04-30 in research.md with source URLs; AGP 9.2 + 16KB gradle property + fail-soft CI step ready; no `.so` deps in baseline |
| X. Simplicity & Build Order | ✅ Pass | First spec; phase-order honored (Spec 001 = foundation); YAGNI honored (no Hilt/Room/Nav added — those belong to 002+) |
| XI. Build Configuration | ✅ Pass | debug + release only, no flavors; Java 11 target via Toolchain; signing config per FR-013 (debug-fallback aligned with constitution v1.2.0 amendment); R8 + shrinkResources ENABLED from Spec 001 — intentional stricter posture than constitution's "may start with R8 disabled and enable in Spec 016 or earlier" allowance. Rationale: catch proguard issues at template stage rather than deferring; cost is minor (Spec 001 has only template code). |

**Gate result**: PASS. No deviations require Complexity Tracking entries.

### Post-Phase-1 re-evaluation (recorded 2026-04-30)

After designing the file layout (see Project Structure below) and writing quickstart.md, all gates remain green. The Detekt-Kotlin compat risk (CV-05) is a runtime risk to track, not a constitution violation. No additional complexity introduced beyond what the spec requires.

**Gate result**: PASS. Proceed to `/speckit.tasks`.

## Project Structure

### Documentation (this feature)

```text
specs/001-project-init-build-config/
├── plan.md                  # This file (/speckit.plan command output)
├── research.md              # Phase 0 output — version lookup + caveats
├── quickstart.md            # Phase 1 output — how to verify spec done
├── spec.md                  # Feature specification (already created)
└── checklists/
    └── requirements.md      # Spec quality checklist (already created)
```

> No `data-model.md` (no entities introduced).
> No `contracts/` (no public APIs / external interfaces in this spec).

### Source Code (repository root)

Spec 001 is a single-Gradle-module Android app. Below is the post-Spec-001 layout — file-system changes are minimal: rename `java/` → `kotlin/`, add three configuration files, write one stub Kotlin file.

```text
ThirtySixBrowser/
├── app/
│   ├── build.gradle.kts                  # ✏ MODIFIED — version catalog refs, signing fallback, Toolchain, lint config
│   ├── proguard-rules.pro                # (existing, untouched)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml       # (existing, untouched)
│       │   ├── kotlin/                   # ✨ NEW — migrated from java/
│       │   │   └── com/raumanian/thirtysix/browser/
│       │   │       ├── MainActivity.kt   # ↔ MIGRATED from src/main/java/
│       │   │       ├── ui/theme/
│       │   │       │   ├── Color.kt      # ↔ MIGRATED
│       │   │       │   ├── Theme.kt      # ↔ MIGRATED
│       │   │       │   └── Type.kt       # ↔ MIGRATED
│       │   │       └── core/constants/
│       │   │           └── AppConstants.kt  # ✨ NEW — empty stub `object AppConstants`
│       │   └── res/                      # (existing, untouched)
│       ├── test/
│       │   └── kotlin/                   # ✨ NEW — migrated from java/
│       │       └── com/raumanian/thirtysix/browser/
│       │           └── ExampleUnitTest.kt
│       └── androidTest/
│           └── kotlin/                   # ✨ NEW — migrated from java/
│               └── com/raumanian/thirtysix/browser/
│                   └── ExampleInstrumentedTest.kt
├── build.gradle.kts                       # ✏ MODIFIED — top-level plugins block via catalog aliases
├── settings.gradle.kts                    # ✏ MODIFIED — pluginManagement + dependencyResolutionManagement
├── gradle/
│   ├── libs.versions.toml                 # ✏ HEAVILY MODIFIED — full catalog (versions/libraries/plugins/bundles)
│   └── wrapper/
│       ├── gradle-wrapper.properties      # ✏ MODIFIED — wrapper distribution = 9.5.0
│       └── gradle-wrapper.jar             # (binary, regenerated when wrapper updated)
├── gradle.properties                      # ✏ MODIFIED — add android.bundle.enableUncompressedNativeLibs=true
├── detekt.yml                             # ✨ NEW — Detekt config with MagicNumber excludes
├── detekt-baseline.xml                    # ✨ NEW (conditional) — generated only if template code triggers Detekt
├── .editorconfig                          # ✨ NEW (conditional) — ktlint formatting if needed
├── proguard-rules.pro                     # (existing, untouched)
├── local.properties                       # (gitignored — used for keystore creds when present)
└── .github/
    └── workflows/
        └── ci.yml                         # ✏ MODIFIED — add detekt + ktlintCheck + 16KB-verify step
```

**Structure Decision**:

Single-module Android app (`app/`). The repository already follows this layout from the Android Studio template; Spec 001 retains it and only renames the `java/` source directories to `kotlin/` (Q1 clarification). No new modules introduced — Spec 002 will add the Clean Architecture skeleton inside `app/src/main/kotlin/...` without modularizing.

### File ownership matrix

| File | Owner spec | Action in 001 |
|------|------------|---------------|
| `gradle/libs.versions.toml` | 001 (created) | Heavily extended in every spec adding deps |
| `app/build.gradle.kts` | 001 (created), all specs append-only | Wire build types, signing, Toolchain, lint config |
| `build.gradle.kts` (root) | 001 (created) | Top-level plugin aliases only |
| `settings.gradle.kts` | 001 (created) | Plugin/repo management; project name |
| `gradle.properties` | 001 (created) | Add 16KB flag + standard performance flags |
| `detekt.yml` | 001 (created) | MagicNumber excludes; future specs may extend |
| `.github/workflows/ci.yml` | 001 (created) | Detekt + ktlint + 16KB step; Spec 007/011 re-enables instrumented job |
| `app/src/main/kotlin/.../MainActivity.kt` | template, migrated 001 | Same content, moved location |
| `app/src/main/kotlin/.../ui/theme/*.kt` | template, migrated 001 | Same content, moved location; will be replaced in Spec 003 |
| `app/src/main/kotlin/.../core/constants/AppConstants.kt` | 001 (created stub) | Extended by future specs as constants land |

## Complexity Tracking

> No constitution violations require justification.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none) | — | — |

## Risks & Rollback Path

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Detekt 1.23.8 incompatible with Kotlin 2.3.21 (CV-05) | Medium | Spec 001 acceptance fails (`detekt` task crashes/false positives) | Pin `detekt { toolVersion = "1.23.8" }` decoupled from project Kotlin; if rules crash, evaluate Detekt 2.x (check release status) or downgrade Kotlin to 2.0.x temporarily |
| AGP 9.2 regression on edge case | Low | Build fail | Fallback to AGP 8.13.x (last 8.x stable at lookup); Gradle 9.5 still compatible |
| 16KB CI step false-fail when no `.so` exists | Medium | CI red on every PR | Script written fail-soft (FR-016); explicit test of empty-libs APK during implementation |
| Template code triggers Detekt MagicNumber | High (template likely has small literals) | First detekt run fails | Generate `detekt-baseline.xml` once, commit; subsequent runs accept template state, strict on new code (FR-020) |
| Template code triggers Lint warning | Medium | First lintDebug fails | Per Q4 (no Lint baseline) — fix template directly; if persistent, escalate to spec discussion |
| ktlint default rules conflict with template formatting | Medium | First ktlintCheck fails | Run `./gradlew ktlintFormat` to auto-fix; commit fixes as part of spec 001 |

## Phase 2 Preview

`/speckit.tasks` will turn the above into an ordered task list. Expected groupings (informational, not authoritative — `/speckit.tasks` decides):

1. Wrapper + version catalog skeleton
2. Top-level + module Gradle scripts
3. Source migration `java/` → `kotlin/`
4. Constants stub
5. Detekt + ktlint config + baselines
6. Lint config (strict)
7. Signing config + fallback
8. CI workflow updates (detekt/ktlint/16KB step)
9. Acceptance verification (run all 6 Gradle commands clean)
10. Documentation updates (CLAUDE.md, project-context.md, sdd-roadmap.md per FR-028..030)
