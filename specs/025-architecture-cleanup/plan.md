# Implementation Plan: Architecture Cleanup

**Branch**: `025-architecture-cleanup` | **Date**: 2026-10-14 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/025-architecture-cleanup/spec.md`

## Summary

Move the app from a layer-first layout to the owner's feature-first template (`core/` + ten `feature/<f>/` + root `navigation/`) without changing any behaviour or stored data; add the template's checks (Konsist architecture/placement/hardcode/comment tests, detekt comment rules, compose-rules) with temporary allowlists that empty as each feature moves; cut comments to the policy; replace the 906-line constitution and 804-line `CLAUDE.md` with constitution 2.0.0, a short `AGENTS.md`, ADRs and an archive. Work happens on one branch, one green commit per step, merged once. Removing forwarding use cases and moving screens to `onAction`/`Event` are Spec 026.

Key research results: 31 domain types and 19 contracts are used by two or more features and go to `core/domain` (R2); the Room database, DAOs and entities go to `core/data` because `@Database` must name every entity (R3, a gap in the template); five contracts carry Android types or live in `data/` and are split (R4); cross-feature UI is joined only at the root through slots and callbacks (R5); Room's schema export folder is named after the database class and must be renamed with it (R12).

## Technical Context

**Language/Version**: Kotlin 2.3.21, AGP 9.1.1, Gradle 9.5.0 on JDK 21 daemon (toolchain 11)
**Primary Dependencies**: Compose BOM 2026.04.01, Hilt 2.59.2 on KSP 2.3.7, Room 2.8.4, DataStore 1.2.1, androidx.webkit 1.17.0 — all unchanged. Added, test/lint only: Konsist **0.18.1**, compose-rules ktlint **0.6.8** (Maven Central, 2026-10-14)
**Storage**: Room `thirtysix_browser.db` v5 and DataStore `thirtysix_settings` — unchanged (data-model invariants)
**Testing**: JUnit 4, Turbine, Robolectric, Compose UI test, Espresso-Web; 932 unit, 189 instrumented (16 KB AVD) + CI API 29
**Target Platform**: Android 7.0 (API 24) – 16 (API 36), 16 KB page size
**Project Type**: single-module Android app
**Performance Goals**: no change — release APK ±2 %, cold start ±5 % against the step-1 baseline
**Constraints**: zero user-visible change; no runtime dependency; every commit green; `git mv` for moves so history follows
**Scale/Scope**: 462 main files (31,885 lines), 173 unit-test files, 66 instrumented-test files, 5 debug files; ≈ 4,000 ID citations in 775 files

## Constitution Check

*Gate against constitution **1.6.0** (in force until this spec replaces it).*

| Principle (1.6.0) | Status | Note |
|---|---|---|
| I Privacy & Security | ✅ | no behaviour, permission or data change |
| II Play compliance | ✅ | manifest unchanged |
| III Code quality / No-Hardcode | ✅ | constants move, values unchanged; checks get stricter |
| IV Clean Architecture | ✅ fixes violations | 1.6.0 already says "organized by feature … domain pure Kotlin"; code breaks both today (9 use cases import `data/`, layer-first layout). This spec makes it true |
| V Performance / Compose phases | ✅ | no composable logic change; report must stay 0 non-skippable |
| VI Testing | ✅ | tests move with code; none deleted |
| VII Offline-first | ✅ | — |
| VIII Localization & a11y | ✅ | no string change |
| IX Dependencies / 16 KB | ✅ | Konsist and compose-rules looked up today, test/lint-only, no `.so` |
| X Simplicity & build order | ⚠️ | spec is outside Phases 1–6 and not in `sdd-roadmap.md` → Complexity Tracking |
| XI Build configuration | ✅ | build types unchanged |

**Post-design re-check** against the target constitution **2.0.0** (template principles I–VIII): every principle has its check wired by step 2 (contracts/architecture-checks.md); FR-011 (action/event) is a **Review** rule whose existing exceptions are listed below for Spec 026. Gate: **PASS**.

## Project Structure

### Documentation (this feature)

```text
specs/025-architecture-cleanup/
├── spec.md
├── plan.md                          # this file
├── research.md                      # R1–R12
├── data-model.md                    # target layout, placement rules, invariants
├── quickstart.md                    # G0–G10
├── contracts/
│   ├── architecture-checks.md       # every check, rule, failure, principle
│   └── governance-set.md            # constitution / AGENTS / ADR / archive contents
├── checklists/requirements.md
└── tasks.md                         # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── ThirtySixApplication.kt, MainActivity.kt
├── navigation/                      AppNavHost
├── core/{data, domain, presentation/{designsystem/{theme,component}, util}, di}
└── feature/
    ├── browser/  tabs/  bookmarks/  history/  downloads/
    ├── settings/ onboarding/ privacy/ sitepermission/ entrypoint/
    └── <each>/{data/{datasource/local, mapper, repository}, domain/{model, repository, usecase},
                presentation/{<screen>/, navigation/}, di/}

app/src/test/kotlin/com/raumanian/thirtysix/browser/
├── architecture/                    ArchitectureTest, PlacementTest, NoHardcodeTest, CommentPolicyTest, KonsistScope
├── core/testing/                    MainDispatcherRule and shared rules (from testdoubles/)
└── feature/<f>/{data, domain, presentation, fake/, fixture/}

app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/
├── HiltTestRunner.kt, di/
└── core/…, feature/<f>/…            mirrors main

app/src/debug/kotlin/…               unchanged (HiltTestActivity, dev/*Seeder)
app/schemas/com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase/   renamed folder, same JSON
config/detekt/detekt.yml, config/compose/stability.conf
docs/adr/, docs/constitution-history.md, docs/archive/
AGENTS.md, CLAUDE.md (@AGENTS.md)
```

**Structure Decision**: the template's single-module feature-first layout, with the database and shared contracts in `core/` (research R2–R3). Full mapping rules and invariants: [data-model.md](data-model.md).

## Step plan (one commit each, research R9)

| Step | Content | Gate |
|---|---|---|
| 0 | Baseline numbers (G0) recorded in tasks.md | — |
| 1 | `config/` moves; Konsist + compose-rules + template detekt rules; allowlists generated from the current code | G1, G2 |
| 1b | (after the moves) compose-rules findings fixed, signature-only, one commit per folder group | G1 |
| 2 | `core/` built: util, design system, database/DAOs/entities/migrations + schema folder rename, DataStore, shared models/contracts/use cases, contract splits (R4) | G1 |
| 3–12 | One feature each, leaf first: settings, onboarding, sitepermission, downloads, history, bookmarks, tabs, entrypoint, privacy, browser — main + tests + DI, its allowlist entries removed | G1 |
| 13 | Root `navigation/` with per-feature graphs; slots and Fire host (R5, R6) | G1, G10 |
| 14–23 | Comment pass, one commit per feature + core; ADRs added as reasons move out | G1, G5 |
| 24 | Governance set + archive; memory notes updated | G9 |
| 25 | Allowlists deleted; device gates | G3–G8, G10 |

## Spec 026 hand-off

Produced during steps 2–12 and recorded in tasks.md: the list of use cases whose body forwards one call (input to Spec 026's removal), and the list of screens whose state holder does not yet take `onAction` (Spec 026's scope).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Constitution 1.6.0 §X: spec outside Phases 1–6 | Owner asked for the cleanup after Phase 6 completed (2026-10-14); task T002 adds Specs 025 and 026 to `sdd-roadmap.md` with that approval, and the same spec retires §X with ADR 0005 (owner decision Q2) | Adding a Phase 7 to 1.6.0 first would amend a document this spec deletes |
| Shared database and contracts in `core/` instead of in features (template README shows DAOs in features) | `@Database` must reference every entity and `core` may not import features | A root `database/` package adds a top-level folder outside the template; per-feature databases change the schema |
| Screens keep method-per-input state holders (template rule III action/event) | Owner moved it to Spec 026 to keep this spec a structural move | Doing it here mixes behaviour-shaped edits with 460 file moves |
| Temporary allowlists (Konsist, detekt baseline, ktlint baseline) | Checks must be on from step 1 while code still moves | Turning checks on last leaves 25 steps unchecked |
