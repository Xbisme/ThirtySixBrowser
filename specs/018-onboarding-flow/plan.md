# Implementation Plan: Onboarding Flow

**Branch**: `018-onboarding-flow` | **Date**: 2026-09-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/018-onboarding-flow/spec.md`

## Summary

A four-slide first-run experience — welcome, language, theme, search engine — shown once per install, skippable from any slide, with every choice saved and applied the moment it is made. **The last spec of Phase 4 and of v1.0.**

The feature stores nothing of its own: it is a second presentation of settings that already exist plus one flag that already exists. Its difficulty is not in what it saves but in **when things are read and when they survive**:

1. **The flag must be resolved before the navigation graph is built.** `UserSettings.DEFAULT` carries `isOnboardingCompleted = false`, and `NavHost` captures `startDestination` at first composition — so reading the flag from the settings flow inside `setContent` sends **every** launch to onboarding, not for a frame, but as the graph's actual start route ([research.md](./research.md) R2).
2. **The slide position must survive activity recreation, not just rotation.** Applying a language recreates the activity, which clears the ViewModel store — so a plain ViewModel field lands the user back on slide 1, the exact failure FR-015 exists to prevent (R3). A rotation test would not catch it.

One finding contradicts the source's own documentation: Spec 016's three choosers carry KDoc saying onboarding "can reuse it unchanged", but all three are `AlertDialog`s and their option row is `private`. The **label functions are genuinely public and reusable**; the dialogs are not (R1).

## Technical Context

**Language/Version**: Kotlin 2.3.21 / AGP 9.1.1 / Gradle 9.5.0 / Java 11
**Primary Dependencies**: **None added.** Verified against the version catalog — a slide surface, saved state, the settings use cases, the language controller, the label functions and the option enums all already exist (R7). A7 is settled by inspection, not assumed.
**Storage**: **No new persistence.** Room stays at v2, no migration, no new DataStore key. One *read* is added to the settings repository — the interface currently has one observer and five setters and no way to read once (R2, [contracts/SettingsRepositoryAmendment.kt](./contracts/SettingsRepositoryAmendment.kt)).
**Testing**: Unlike Spec 017, this feature **does** have unit-testable logic — slide advancement, the last-slide boundary, which exits write the flag, the no-op language guard. Those belong in ViewModel unit tests. Existing suites must stay green: 557 unit, 109 instrumented.
**Target Platform**: Android 7.0 (API 24) – Android 16 (API 36)
**Project Type**: Android mobile app, single module, single activity
**Performance Goals**: No launch regression. The added read is a suspend call awaited off the main thread; Spec 017's SC-004 cold-start figure must not move materially.
**Constraints**: Fully offline (FR-020); no account, sign-in or permission (FR-021); 8-locale parity (FR-017); APK ≤ 3,331,012 B (SC-013); no `setKeepOnScreenCondition` (Spec 017 INV-12, still binding).
**Scale/Scope**: 4 slides, 1 ViewModel, 1 repository read + its use case, ~10 new strings × 8 locales, one placeholder replaced.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| # | Principle | Verdict | Evidence |
|---|---|---|---|
| I | Privacy & Security First | ✅ PASS | Collects nothing, transmits nothing, asks for no permission or account (FR-021). The welcome slide **states** the privacy promise, reusing the existing wording so the claim cannot drift (R8, A9). |
| II | Google Play Compliance | ✅ PASS | No new permission. No new API surface. This spec completes Phase 1–4, which is the precondition for store submission. |
| III | Code Quality & Safety (No-Hardcode) | ✅ PASS | All strings via resources; option labels reused from Spec 016 rather than retyped (INV-8); slide count and indices are named constants, not literals scattered across composables. |
| IV | Clean Architecture | ✅ PASS | The new read goes **behind the repository**, not straight to DataStore from a ViewModel — explicitly rejected in the contract. Presentation talks to use cases only. |
| V | Immutable State / MVVM | ✅ PASS | Immutable `UiState` as `StateFlow`, mutated via `update { }`. ⚠️ One deliberate exception: **slide position lives in saved instance state**, not an ordinary field — required by FR-015 (INV-2). Documented, not silent. |
| VI | Testing Discipline | ✅ PASS | Real unit-testable logic exists here and is tested. Device gates cover what only a device shows (G1–G12). |
| VII | Data & Backup | ✅ PASS | No new persisted data. The existing flag's backup posture is unchanged. |
| VIII | Localization & Accessibility | ✅ PASS | New strings in all 8 locales, lint-enforced. Every control labelled; options announce selected state; 48dp targets; slides usable at smallest width and largest font (FR-019, SC-011). |
| IX | Dependency Currency & 16 KB | ✅ PASS | **No dependency added** (R7). The 16 KB gate is still re-run on the built artifact, because the rule is about what ships, not about intent. |
| X | Simplicity & Build Order | ✅ PASS | Spec 018 is last in mandatory phase order. No speculative code: re-running from Settings is explicitly out of scope. |
| XI | Build & Signing | ✅ PASS | No change to build types or signing. |

**Result: 11/11 PASS, zero deviations.** Complexity Tracking is empty.

**Gate V note — why slide position is not an ordinary field.** Principle V asks for state in an immutable `UiState` behind a `StateFlow`. The slide index is still exposed that way; what differs is where it is *retained*. Applying a language recreates the activity and clears the ViewModel store, so an ordinary field resets to 0 and the user is thrown back to slide 1 (R3). Saved instance state — `rememberSaveable` or a `SavedStateHandle` — is the only retention that survives both rotation and recreation. This is a retention decision, not an escape from unidirectional state.

**Post-Phase-1 re-check**: unchanged, **11/11 PASS**. The design added one repository method, one use case and one screen; no principle's exposure changed.

## Project Structure

### Documentation (this feature)

```text
specs/018-onboarding-flow/
├── plan.md              # This file
├── spec.md              # 27 FR · 15 SC · 10 assumptions · 4 user stories
├── research.md          # Phase 0 — R1–R10
├── data-model.md        # Phase 1 — 4 entities + 13 invariants
├── quickstart.md        # Phase 1 — gates G1–G12
├── contracts/
│   ├── SettingsRepositoryAmendment.kt  # the one interface addition, + rejected alternatives
│   └── OnboardingViewModel.kt          # screen contract + the three ⚠️ traps
├── checklists/
│   └── requirements.md  # 16/16
└── tasks.md             # Phase 2 — NOT created by /speckit-plan
```

### Source Code (repository root)

```text
app/src/main/
├── kotlin/com/raumanian/thirtysix/browser/
│   ├── MainActivity.kt                    # resolve the flag BEFORE setContent; pass startDestination
│   ├── domain/
│   │   ├── repository/SettingsRepository.kt   # + one-shot read
│   │   └── usecase/                           # + use case wrapping it
│   ├── data/
│   │   ├── repository/SettingsRepositoryImpl.kt   # + its implementation
│   │   └── local/datastore/SettingsDataStore.kt   # + first-value read if not reachable
│   └── presentation/
│       ├── navigation/AppNavGraph.kt      # pass nav controller into OnboardingScreen
│       └── onboarding/
│           ├── OnboardingScreen.kt        # REPLACE the placeholder
│           ├── OnboardingViewModel.kt     # NEW
│           ├── OnboardingUiState.kt       # NEW
│           └── components/                # NEW — 4 slides, option row, forward control
└── res/values{,-vi,-de,-ru,-ko,-ja,-zh,-fr}/strings.xml   # + new keys × 8
```

**Structure Decision**: the existing single-module layout, unchanged. `AppDestination.Onboarding` and the `OnboardingScreen` slot already exist and are **reused rather than recreated** — the placeholder is replaced in place. No new package outside `presentation/onboarding/`.

## Implementation Order

Ordered so the two hard problems are proven before any slide content exists.

| Step | Work | Verifies |
|---|---|---|
| 1 | Capture the APK baseline | G11 needs a before |
| 2 | Add the one-shot settings read: repository, impl, use case, unit test | contract |
| 3 | `MainActivity` resolves the flag before `setContent`; pass `startDestination`. Keep the placeholder screen | **G1** — routing proven before any UI exists |
| 4 | Skeleton flow: 4 blank slides, forward/back/skip, position in **saved state** | **G4, G5, G7** — the retention problem, proven before content |
| 5 | Leaving pops the route inclusively | **G8** |
| 6 | Welcome slide, reusing the existing privacy string | FR-004 |
| 7 | Three choice slides: new option rows + reused labels; save-on-select | G3 |
| 8 | Language no-op guard | FR-015a |
| 9 | Strings × 8 locales; accessibility labels | G10 |
| 10 | Full gate sweep G1–G12 on both AVDs; record in tasks.md | all |

**Steps 3 and 4 before any content is deliberate.** Both hard problems — start-up routing and position retention — are independent of what a slide says, and both fail in ways that are easy to misread as content bugs. Proving them against blank slides separates "the flow routes and remembers correctly" from "the slides look right".

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Flag read from the settings flow inside `setContent`, so every launch starts at onboarding | **High** — it is the obvious place, and the existing code reads settings exactly there | R2 + INV-5 + the contract's rejected-alternatives block; **G1** catches it |
| Slide position in a plain ViewModel field — survives rotation, dies on language change | **High** — passes G5, fails G4, and the difference is invisible without trying a locale change | INV-2, contract's ⚠️ block; **G4** is the only gate that separates them |
| Flag written from disposal / `onStop` — smaller code, silently breaks FR-012 | Medium | INV-6; **G7** catches it (back out, relaunch, flow must return) |
| Leaving without popping inclusively, so Back returns to onboarding | Medium | R6; **G8** — note a "browser is showing" assertion misses it |
| Reusing Spec 016's dialogs because the KDoc says to | Medium — the KDoc is explicit and wrong about presentation | R1 settles it with visibility evidence; breaks FR-009 and SC-008 if attempted |
| A second privacy wording invented for the welcome slide | Low | R8/A9 — reuse `settings_about_privacy_statement` verbatim |
| Reaching for `setKeepOnScreenCondition` to await the flag | Low | Spec 017 INV-12 forbids it; the contract records the rejection |

## Complexity Tracking

> No Constitution Check violations. This section is intentionally empty.
