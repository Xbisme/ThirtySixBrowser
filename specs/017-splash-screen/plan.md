# Implementation Plan: Splash Screen

**Branch**: `017-splash-screen` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/017-splash-screen/spec.md`

## Summary

Add a branded launch screen and replace the last of the project-template artwork with the app's own mark — a filled disc in the brand gradient carrying solid numerals "36", approved by the product owner on 2026-09-15.

The technical approach is deliberately small: **one dependency, a pair of theme entries, a set of drawable resources, and a single call in `MainActivity.onCreate`.** There is no data layer, no ViewModel, no new state (see [data-model.md](./data-model.md)). The launch screen is drawn by the system; the app's only jobs are to declare what it looks like and to hand control back to its own theme at the right moment.

Two findings shape the plan and are worth stating up front:

1. **The splash theme must NOT have an AppCompat parent** — the opposite of what the pre-spec brief assumed. The library's theme descends from `android:Theme.DeviceDefault` by design, and the app stays safe because `installSplashScreen()` swaps in `Theme.ThirtySix` via the `postSplashScreenTheme` attribute before AppCompat needs it. Established from the AAR and from decompiled bytecode in [research.md](./research.md) R3.
2. **The per-density bitmaps are load-bearing.** minSdk is 24 but adaptive icons only apply from API 26, so on API 24–25 the `.webp` files *are* the icon. Replacing only the adaptive icon would leave the template robot shipping on the minimum supported version while every visible check passed (R7, FR-004a).

## Technical Context

**Language/Version**: Kotlin 2.3.21 / AGP 9.1.1 / Gradle 9.5.0 / Java 11
**Primary Dependencies**: `androidx.core:core-splashscreen` **1.2.0** — the one addition. Verified 2026-09-15 against `dl.google.com` maven-metadata: `<release>` points at the stable 1.2.0 (unlike Spec 016's webkit, no alpha to avoid), released 2025-11-05, library minSdk 21.
**Storage**: **None.** Room stays at v2; no migration. No DataStore key is added, and FR-016 forbids reading the existing first-run flag.
**Testing**: No new unit or instrumented tests — the feature has no Kotlin seam a JVM test can observe (see Constitution Check gate 6). Existing suites must stay green: 557 unit, 109 instrumented. Verification is by device gates G1–G10 in [quickstart.md](./quickstart.md).
**Target Platform**: Android 7.0 (API 24) – Android 16 (API 36); compileSdk 36, targetSdk 36
**Project Type**: Android mobile app, single module
**Performance Goals**: Cold start no more than 5% slower than baseline (SC-004), measured as `am start -W` `TotalTime` median over ≥10 runs, before vs after, same device and build type.
**Constraints**: Fully offline (FR-013); no new strings (FR-017, confirmed — the mark is decorative per FR-018); APK ≤ 3,326,915 B (SC-010); zero new `.so` (§IX); no artificial launch delay (FR-008 — no `setKeepOnScreenCondition`).
**Scale/Scope**: ~11 files touched, **net file count −5** (the round icon variant is deleted). One new folder (`res/drawable-v31/`). One line added to `MainActivity`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| # | Principle | Verdict | Evidence |
|---|---|---|---|
| I | Privacy & Security First | ✅ PASS | The launch screen reads, displays and transmits no user data (FR-014) and touches no network (FR-013). It does not even read the first-run flag (FR-016). |
| II | Google Play Compliance | ✅ PASS | No new permission. Uses an official AndroidX library named in Constitution Technical Standards. **Replacing the template icon is a Play prerequisite** — a template icon cannot be published. |
| III | Code Quality & Safety (No-Hardcode) | ✅ PASS | Brand hexes become named `<color>` resources (vector XML cannot reference `Color.kt`); dimensions come from the library's own `@dimen`; no magic numbers in Kotlin. The single added call takes no literal argument. |
| IV | Clean Architecture | ✅ PASS | **No layer is touched.** No repository, use case, ViewModel or domain model. `MainActivity` gains one call and one import; nothing crosses a layer boundary. |
| V | Immutable State / MVVM | ✅ PASS | No state is introduced. The library's returned handle is deliberately discarded (Contract 2). |
| VI | Testing Discipline | ⚠️ PASS with note | No new automated test — justified in gate 6 note below rather than waved through. |
| VII | Data & Backup | ✅ PASS | No persisted data, so nothing to include or exclude from backup. |
| VIII | Localization & Accessibility | ✅ PASS | **Zero new strings** (FR-017), so 8-locale parity is trivially preserved. The mark is decorative and explicitly unannounced per §VIII's "decorative-only icons" rule — FR-018 was *reversed* during clarification because it originally contradicted this principle. Contrast is unchanged (off-white on brand gradient, as approved). |
| IX | Dependency Currency & 16 KB | ✅ PASS | Version looked up 2026-09-15 at the moment of planning, not remembered (R1). **Zero `.so` confirmed by inspecting the AAR's 39 entries directly**, not by trusting release notes. Re-verified against the built APK at G1. |
| X | Simplicity & Build Order | ✅ PASS | Spec 017 is next in mandatory phase order (Phase 4, after 016). No speculative code: FR-016 explicitly refuses to do Spec 018's routing work. |
| XI | Build & Signing | ✅ PASS | No change to build types or signing. |

**Result: 11/11 PASS, zero deviations.** Complexity Tracking is therefore empty.

**Gate VI note — why no new automated test.** This feature's behaviour is (a) resource selection by qualifier and (b) one call ordering. Neither is observable from a JVM test: there is no return value to assert, no branch to exercise, no injectable seam. A test asserting `installSplashScreen()` was called would pin the implementation, not the behaviour, and would pass even if the theme handoff were misconfigured — the actual failure mode. The behaviour is genuinely verified by G4 (both drawable paths on real API levels) and G6 (10 launches × 2 AVDs × 2 build types, which is exactly what catches a broken handoff). Existing suites must stay green at 557/109 to prove nothing regressed. This is a reasoned exemption for a resource-only feature, not a gap.

**Post-Phase-1 re-check**: unchanged, **11/11 PASS**. The design added no Kotlin beyond the single call, so no principle's exposure changed.

## Project Structure

### Documentation (this feature)

```text
specs/017-splash-screen/
├── plan.md              # This file
├── spec.md              # Feature specification (21 FR, 13 SC, 10 assumptions)
├── research.md          # Phase 0 — R1–R10
├── data-model.md        # Phase 1 — resource entities + 14 invariants
├── quickstart.md        # Phase 1 — gates G1–G10
├── contracts/
│   └── README.md        # Phase 1 — 4 resource/ordering contracts (no .kt: no Kotlin seam exists)
├── assets/
│   └── brand-mark-approved.svg  # The approved mark — single source for ALL representations (INV-1)
├── checklists/
│   └── requirements.md  # Spec quality checklist — 16/16
└── tasks.md             # Phase 2 — NOT created by /speckit-plan
```

### Source Code (repository root)

```text
gradle/
└── libs.versions.toml                    # + coreSplashscreen version + library entry

app/
├── build.gradle.kts                      # + implementation(libs.androidx.core.splashscreen)
└── src/main/
    ├── AndroidManifest.xml               # splash theme on activity; REMOVE android:roundIcon
    ├── kotlin/com/raumanian/thirtysix/browser/
    │   └── MainActivity.kt               # + installSplashScreen() before super.onCreate()
    └── res/
        ├── values/
        │   ├── themes.xml                # + Theme.ThirtySix.Splash (light)
        │   └── colors.xml                # + 3 brand <color> entries
        ├── values-night/
        │   └── themes.xml                # + Theme.ThirtySix.Splash (dark)
        ├── drawable/
        │   ├── ic_launcher_background.xml    # REPLACE (template artwork)
        │   ├── ic_launcher_foreground.xml    # REPLACE (template artwork)
        │   └── ic_brand_mark.xml, ic_brand_mark_mono.xml  # NEW — static + monochrome
        ├── drawable-v31/                 # NEW FOLDER
        │   └── ic_brand_mark.xml          # NEW — animated, SAME resource name as the static one
        ├── mipmap-anydpi-v26/
        │   ├── ic_launcher.xml           # repoint foreground/background/monochrome
        │   └── ic_launcher_round.xml     # DELETE
        └── mipmap-{m,h,xh,xxh,xxxh}dpi/
            ├── ic_launcher.webp          # REPLACE ×5 (rendered from the vector)
            └── ic_launcher_round.webp    # DELETE ×5
```

**Structure Decision**: The existing single-module Android layout is used unchanged. This feature adds exactly one new directory — `res/drawable-v31/` — which exists so the animated mark is selected by resource qualifier on API 31+ and the static one below, with **no runtime version check in Kotlin** (R6). No package under `kotlin/` is created or modified except the one call site in `MainActivity`.

## Implementation Order

Ordered so the riskiest thing is provable earliest. G6 (launch crash) is the feature's main failure mode, and it becomes testable at step 3 — before any artwork exists.

| Step | Work | Verifies |
|---|---|---|
| 1 | Capture baselines: release APK size, cold-start median ×10 | G7, G10 need a before |
| 2 | Add dependency to catalog + `build.gradle.kts`; run G1 | §IX, G1 |
| 3 | Splash theme (light + dark) with `postSplashScreenTheme`; `installSplashScreen()` before `super.onCreate()`; placeholder icon | **G6** — the handoff, proved before artwork |
| 4 | Author the mark: static vector, monochrome, outline the numerals | Contract 3 |
| 5 | Animated vector in `drawable-v31/`, same resource name | G4 |
| 6 | Point the splash theme at the real mark | G2, G3 |
| 7 | Adaptive icon repointed; render 5 density bitmaps; delete round variant + `roundIcon` | **G8** |
| 8 | Full gate sweep G1–G10 on both AVDs; record results in tasks.md | all |

**Step 3 before step 4 is deliberate.** The theme handoff either works or crashes every launch, and it is independent of what the icon looks like. Proving it with a placeholder separates a launch crash from an artwork problem; doing artwork first would conflate the two.

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Splash theme given an AppCompat parent "for safety", per the brief's wrong claim | **High** — the brief says so explicitly | R3 records the bytecode evidence; Contract 1 states it as a constraint with rationale; G6 catches it |
| `installSplashScreen()` placed after `super.onCreate()` | Medium — natural reading order | INV-11, Contract 2; G6 catches it |
| Template robot left in the 5 bitmaps | Medium — invisible on API 26+ | FR-004a, G8 requires *looking at* each bitmap, and confirms on the API 24 AVD |
| `AnimatedVectorDrawable` placed in `drawable/` instead of `drawable-v31/` | Medium | Renders as a still first frame below API 31 — looks almost right; G4 checks API 36 for actual motion |
| Monochrome drawable derived as a silhouette | Medium | A filled disc flattens to a featureless circle — INV-5, G9 |
| Temptation to add `setKeepOnScreenCondition` to "polish" the launch | Low | INV-12 forbids it; it is the one way to violate FR-008 |

## Complexity Tracking

> No Constitution Check violations. This section is intentionally empty.
