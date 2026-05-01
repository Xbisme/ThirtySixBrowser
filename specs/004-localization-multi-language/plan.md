# Implementation Plan: Multi-Language Localization Foundation

**Branch**: `004-localization-multi-language` | **Date**: 2026-05-01 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/004-localization-multi-language/spec.md`

## Summary

Spec 004 establishes the Android resource-system foundation for 8-locale support (EN baseline + 7 translations: VI, DE, RU, KO, JA, ZH, FR). The implementation is **pure XML resources + manifest + Gradle lint config — zero Kotlin/Java code changes**. The seven placeholder Screens already consume strings via `stringResource(R.string.*_screen_placeholder)` (introduced in Spec 002), so this spec wires translations into the existing call sites without touching the Composables.

Six concrete deliverables:
1. **Modify** `res/values/strings.xml` — fix `app_name` typo `"ThirdtySixBrowser"` → `"ThirtySix Browser"` (Q1 clarification)
2. **Add** seven `res/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml` — full coverage of the 8 baseline keys (`app_name` + 7 `*_screen_placeholder`)
3. **Add** `res/xml/locales_config.xml` declaring 8 BCP-47 tags (`en`, `vi`, `de`, `ru`, `ko`, `ja`, `zh`, `fr`)
4. **Modify** `AndroidManifest.xml` — add `android:localeConfig="@xml/locales_config"` to `<application>`
5. **Modify** `app/build.gradle.kts` — escalate Android Lint check `MissingTranslation` from warning to error severity (Q2 clarification)
6. **Update** existing CI lint job to run on the new lint config (no workflow change expected; existing `./gradlew lintDebug` step already gates merge)

No new Gradle dependencies. No new modules. No new directories under `app/src/main/kotlin/`.

## Technical Context

**Language/Version**: Kotlin 2.3.21 (compiled), Java 11 target — but **this spec adds zero Kotlin/Java code**; all artifacts are XML resources and Gradle DSL.
**Primary Dependencies**: None new. Existing `gradle/libs.versions.toml` (AGP 9.1.1, Compose BOM 2026.04.01) is sufficient.
**Storage**: APK resource bundle (per-locale `strings.xml` files compiled into `resources.arsc`).
**Testing**: Unit tests — none added (no business logic). Lint — `./gradlew lintDebug` gated at error severity for `MissingTranslation`. Manual — emulator/device verification across 8 locales (see [quickstart.md](quickstart.md)).
**Target Platform**: Android 7.0+ (`minSdk = 24`); per-app language picker active only on Android 13+ (`API 33+`); older API levels respect device-wide locale via standard resource qualifier matching.
**Project Type**: Mobile (single-module Android app, package `com.raumanian.thirtysix.browser`).
**Performance Goals**: N/A — resource lookup is sub-millisecond and not on a measurable critical path.
**Constraints**: `minSdk = 24`, `targetSdk = 36`; offline-first (translations bundled in APK, no runtime network call); 16KB page size compliance not affected (no `.so` introduced).
**Scale/Scope**: 8 string keys × 8 locales = 64 individual translated strings; 1 `locales_config.xml`; 1 manifest attribute; 1 Gradle lint config update.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.2.0 — all 11 principles evaluated.

| # | Principle | Status | Notes |
|---|-----------|--------|-------|
| I | Privacy & Security First | ✅ PASS | No data collection, no transmission, no analytics. Strings are static APK content. |
| II | Google Play Compliance | ✅ PASS | `<locale-config>` + `android:localeConfig` is the Android-13+ official supported-locales API. No new permissions. App stays in Tools/Productivity category. |
| III | Code Quality & Safety / No-Hardcode Rule | ✅ PASS | All UI strings externalized in `strings.xml` (already so since Spec 002). The literal `"ThirtySix Browser"` in `values/strings.xml` IS the canonical constant per the No-Hardcode Rule's "User-facing strings" category. Locale tag literals in `locales_config.xml` are themselves the resource definition (no Kotlin code references them). |
| IV | Clean Architecture (MVVM) | ✅ PASS | No Kotlin code added. Existing layer separation preserved. |
| V | Performance Excellence | ✅ PASS | Resource lookup overhead is platform-level (microseconds). No 60fps or cold-start regression possible. |
| VI | Testing Discipline | ✅ PASS | Spec ships no business logic → no unit-test additions required. The `MissingTranslation` lint gate at error severity IS the automated test for translation completeness (FR-008/SC-005). Manual quickstart covers acceptance scenarios. |
| VII | Offline-First | ✅ PASS | All translations bundled in APK; zero network dependency. |
| VIII | Localization & Accessibility | ✅ PASS — **directly serves this principle**. 8 supported locales is the exact set Constitution VIII names. Locale switching is config-change-recreate on Android 12 and earlier (Android handles automatically), and `Activity.recreate()` triggered by the Android 13+ per-app picker (also OS-handled, Compose recomposes with new resources). The "no manual kill/relaunch" intent of Constitution VIII is satisfied without `AppCompatDelegate.setApplicationLocales` in this spec — that API is for the in-app switcher (deferred to Spec 016). |
| IX | Dependency Currency & 16KB | ✅ PASS automatically | No new packages → no version lookup needed → no `.so` introduced → 16KB CI gate unaffected. |
| X | Simplicity & Build Order | ✅ PASS | Spec 004 is in Phase 1 (Foundation); 002 ✅, 003 ✅; 004/005/006 are roadmap-parallel after 002 — choosing 004 next per user direction. YAGNI honored: zero speculative code, zero deferred-feature scaffolding. |
| XI | Build Configuration | ✅ PASS | No flavor changes. Lint config update lives in `app/build.gradle.kts` `android.lint { }` block (existing block, one new severity entry). |

**Result**: All 11 gates pass. No Complexity Tracking entries required.

## Project Structure

### Documentation (this feature)

```text
specs/004-localization-multi-language/
├── plan.md                     # This file
├── spec.md                     # Feature spec + Clarifications
├── research.md                 # Phase 0 — locale tag form, lint DSL, fallback chain decisions
├── data-model.md               # Phase 1 — Supported Locale Set + Baseline String Catalog
├── quickstart.md               # Phase 1 — 8-locale verification playbook
├── checklists/
│   └── requirements.md         # Quality checklist (16/16 pass)
└── tasks.md                    # Phase 2 — generated by /speckit-tasks (NOT this command)
```

No `contracts/` directory — Spec 004 ships no Kotlin interfaces, no public APIs, no UI contract definitions. Translation files are self-contained XML.

### Source Code (repository root)

```text
app/src/main/
├── res/
│   ├── values/
│   │   └── strings.xml                   # MODIFY: app_name typo fix; 8 keys remain
│   ├── values-vi/strings.xml             # NEW
│   ├── values-de/strings.xml             # NEW
│   ├── values-ru/strings.xml             # NEW
│   ├── values-ko/strings.xml             # NEW
│   ├── values-ja/strings.xml             # NEW
│   ├── values-zh/strings.xml             # NEW (Simplified Chinese; resolves zh-TW/zh-HK fallback)
│   ├── values-fr/strings.xml             # NEW
│   └── xml/
│       └── locales_config.xml            # NEW
├── AndroidManifest.xml                   # MODIFY: <application android:localeConfig="@xml/locales_config" ...>
└── kotlin/                               # UNCHANGED — zero Kotlin file edits in this spec

app/build.gradle.kts                      # MODIFY: android.lint { error += "MissingTranslation" }
```

**Structure Decision**: Single-module Android app (Option 3 "Mobile + API" without the API tier — pure mobile). All paths are relative to repo root `/Users/xbism3/Documents/Code/jetpack/ThirdtySixBrowser/`. The `presentation/` Kotlin tree is untouched because Spec 002 already wired `stringResource()` references at every Screen — the resource files added here flow through automatically.

## Complexity Tracking

> *Only filled when Constitution Check has violations to justify.*

No violations. Table omitted.

## Post-Design Constitution Re-check

After Phase 1 design artifacts ([research.md](research.md), [data-model.md](data-model.md), [quickstart.md](quickstart.md)) were written, all 11 principles re-evaluated:

- **No new packages introduced by design** → Principle IX automatically satisfied (no version lookup, no `.so` 16KB check).
- **No new Kotlin source files** → Principle IV (Clean Architecture) and Principle III (No-Hardcode Rule) trivially satisfied for code; the 64 string values introduced ARE the canonical constants per the No-Hardcode Rule's "User-facing strings" category.
- **Lint config update** lives in `app/build.gradle.kts`'s existing `android.lint { }` block; no new build files; Principle XI (Build Configuration) compliant.
- **Quickstart gate 5** explicitly verifies device-locale fallback on Android 7–12 (pre-API-33), satisfying Principle VIII's intent that locale switching is automatic without manual app restart.
- **No measurable test coverage delta** — Spec 004 ships zero business logic, so the ≥70% coverage target on `domain/` and `data/` layers (Principle VI) is unaffected; Lint at error severity is the automated test for translation completeness.

**Re-check result**: 11/11 PASS. No new violations surfaced during design. No Complexity Tracking entries required.
