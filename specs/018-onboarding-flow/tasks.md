---

description: "Task list for Spec 018 — Onboarding Flow"
---

# Tasks: Onboarding Flow

**Input**: Design documents from `/specs/018-onboarding-flow/`
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: **Unit tests ARE generated for this feature**, unlike Spec 017. The flow has genuinely unit-testable logic — slide advancement, the last-slide boundary, which exits write the first-run flag, and the no-op language guard are all observable from a JVM test against the ViewModel with fakes. Device gates cover only what a device can show. Existing suites (557 unit / 109 instrumented) MUST stay green as regression cover.

**Organization**: Tasks are grouped by user story. US1, US2 and US3 are all P1; US4 is P2.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 / US4 per spec.md
- Exact file paths included in every task

## Path Conventions

Single-module Android app. Paths are relative to the repository root. Kotlin sources live under `app/src/main/kotlin/com/raumanian/thirtysix/browser/`, abbreviated below as **`<pkg>/`**.

---

## Phase 1: Setup

- [X] T001 Capture the release APK size baseline: `./gradlew assembleRelease` then `stat -f%z app/build/outputs/apk/release/app-release.apk`, and record it in this file's "Device Gate Results". Expected **3,126,212 B** (the Spec 017 figure); if it differs, record the actual value — that is the real baseline for SC-013. Must run before any code change, because G11 is a before/after comparison.
- [X] T002 Add the new string keys to `app/src/main/res/values/strings.xml`: four slide titles, four slide bodies, the forward control in **two** states (a "next" key and a "done" key — two separate resources, never one string with a placeholder, per FR-002b and INV-13), a Skip label, and a slide-position indicator. Do **not** add a privacy-statement string: FR-004 reuses the existing `settings_about_privacy_statement` verbatim (R8, INV-10, A9). Do **not** add option labels: those come from Spec 016's public `themeModeLabel` / `searchEngineLabel` / `appLanguageLabel` (INV-8).
- [X] T003 Add a `BrowserLimits`-style named constant for the slide count (4) and the slide indices in `<pkg>/core/constants/`, so no composable carries a literal 0–3 or 4 (Constitution §III). Follow the existing constants-file conventions in that package.

**Checkpoint**: Baseline captured, strings and constants in place.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Add the one-shot settings read and prove start-up routing — **before any onboarding UI exists**.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete. Per plan.md's implementation order, routing is proven against the existing placeholder screen so that a routing bug is never mistaken for a UI bug.

- [X] T004 Add a one-shot read to `<pkg>/domain/repository/SettingsRepository.kt` returning the current `UserSettings`, per [contracts/SettingsRepositoryAmendment.kt](./contracts/SettingsRepositoryAmendment.kt). It MUST suspend until a real persisted value is available and MUST NOT return `UserSettings.DEFAULT` as a placeholder. The interface today has one observer and five setters and no way to read once — this is the single addition.
- [X] T005 Implement it in `<pkg>/data/repository/SettingsRepositoryImpl.kt`, reading the first value from the existing settings flow through the existing mapper. Do **not** bypass the repository from the presentation layer (Constitution §IV — explicitly rejected in the contract).
- [X] T006 [P] Add `<pkg>/domain/usecase/GetUserSettingsUseCase.kt` wrapping T004, following the operator-invoke convention of the adjacent `ObserveUserSettingsUseCase`.
- [X] T007 [P] Add unit tests for T005 and T006 in `app/src/test/kotlin/.../` covering: a fresh install returns the documented defaults (where that IS the truth), and a persisted snapshot returns the persisted values rather than the defaults.
- [X] T008 In `<pkg>/MainActivity.kt`, resolve the first-run flag **before** `setContent` and pass the resulting route as `startDestination` to `AppNavGraph`. ⚠️ **This is the feature's single most likely failure** (research.md R2, INV-5): `UserSettings.DEFAULT` carries `isOnboardingCompleted = false`, and `NavHost` captures `startDestination` at first composition, so reading the flag from the settings *flow* inside `setContent` routes **every** launch to onboarding as the graph's actual start route — not as a one-frame flash. ⚠️ `installSplashScreen()` MUST remain the first statement before `super.onCreate()`, and `setKeepOnScreenCondition` MUST NOT be called (Spec 017 INV-12) — the read happens between `super.onCreate()` and `setContent`.
- [X] T009 Run gate **G1** (routing) with the placeholder screen still in place: on a cleared install (`adb shell pm clear com.raumanian.thirtysix.browser.debug`) the placeholder appears; after setting the flag, relaunching shows the browser with no onboarding frame. 🔴 Blocking. Record in "Device Gate Results".

**Checkpoint**: Start-up routing is correct and proven, with no onboarding UI written yet.

---

## Phase 3: User Story 1 - Set up the browser on first launch (Priority: P1) 🎯 MVP

**Goal**: A working four-slide flow that appears on first launch, moves forward and back, finishes, and never appears again.

**Independent Test**: On a fresh install, step through all four slides making a choice on each, finish, and confirm the browser opens with those choices and that relaunching goes straight to the browser.

- [X] T010 [US1] Create `<pkg>/presentation/onboarding/OnboardingUiState.kt` as an immutable `data class` per [contracts/OnboardingViewModel.kt](./contracts/OnboardingViewModel.kt): `currentSlide` (**0..3, clamped on restore** per INV-3), `isLastSlide`, and the three current selections. The selections are values **read back** from the settings flow and the language controller, never a local copy (INV-1).
- [X] T011 [US1] Create `<pkg>/presentation/onboarding/OnboardingViewModel.kt` exposing `StateFlow<OnboardingUiState>` mutated only via `MutableStateFlow.update { }`. ⚠️ **The slide position MUST be held in a `SavedStateHandle`, not an ordinary field** (INV-2, R3) — applying a language recreates the activity and clears the ViewModel store, so an ordinary field resets to slide 1, which is the exact failure FR-015 exists to prevent. ⚠️ Equally, it MUST NOT be written to DataStore or any other durable store (**INV-4**): the index is session state, not a setting, so a user who force-quits mid-flow starts over rather than resuming on slide 3 (the FR-012 edge case). `SavedStateHandle` satisfies both constraints; DataStore satisfies the first and silently violates the second. Implement `onForward` (advancing, and **completing on the last slide** per FR-002b) and `onBack`.
- [X] T012 [US1] Implement completion in the ViewModel: write the flag through the existing `SetOnboardingCompletedUseCase`. ⚠️ **Exactly two write sites exist — finishing and skipping (T019).** The flag MUST NOT be written from `onCleared()`, `DisposableEffect` disposal, `onStop`, or any lifecycle hook (INV-6, FR-012, FR-002a): those fire when the user backs out or leaves, and a user who backed out on slide 1 must still be asked next launch.
- [X] T013 [US1] Replace the placeholder body of `<pkg>/presentation/onboarding/OnboardingScreen.kt` with the flow host: a pager or equivalent over 4 slides driven by `currentSlide`, the forward control, and the position indicator (FR-003). Accept a `NavHostController` as `SettingsScreen` and the other screens do.
- [X] T014 [US1] Create the forward control in `<pkg>/presentation/onboarding/components/`. ⚠️ **One control in one position across all four slides**, with the label chosen by `isLastSlide` from the two string resources added in T002 (FR-002b, INV-12, INV-13). Not two controls with one hidden — that moves focus order and breaks the predictability a screen-reader user relies on.
- [X] T015 [US1] Create the welcome slide in `<pkg>/presentation/onboarding/components/`. It MUST state the privacy promise using the **existing** `settings_about_privacy_statement` resource verbatim (FR-004, INV-10, A9) — do not write a second sentence saying the same thing, because two wordings of the app's central privacy claim will drift.
- [X] T016 [US1] Wire `<pkg>/presentation/navigation/AppNavGraph.kt` to pass the nav controller into `OnboardingScreen`, matching how `BrowserScreen` and `SettingsScreen` already receive it.
- [X] T017 [US1] Implement leaving the flow: navigate to the browser **popping the onboarding route inclusively** (FR-010a, R6). ⚠️ A test asserting only "the browser is showing" will not catch a missing pop — SC-015 presses Back, and that is the check that matters.
- [X] T018 [US1] Add ViewModel unit tests in `app/src/test/kotlin/.../OnboardingViewModelTest.kt`: forward advances; forward on the last slide completes; back moves back; back on slide 0 does **not** complete and does **not** write the flag; the restored slide index is clamped to 0..3; and **advancing slides writes nothing to the settings store** (INV-4) — the fake settings repository records no write when only navigation happens.

**Checkpoint**: The flow appears, navigates, finishes, and does not return. MVP complete.

---

## Phase 4: User Story 3 - Skip the flow and start browsing (Priority: P1)

**Goal**: A Skip control on every slide that leaves immediately and is remembered.

**Independent Test**: On a fresh install, tap Skip on the first slide, confirm the browser opens, then relaunch and confirm the flow does not reappear.

**Why this precedes US2**: Skip shares T012's completion path and T017's pop, so it is a small addition once those exist — and it is one of the two ways to *leave* the flow, which US2's content work assumes already works.

- [X] T019 [US3] Add `onSkip` to the ViewModel: write the first-run flag and leave. ⚠️ Skip MUST change **no** setting (FR-011, A4, INV-7) — it records that the user was asked, not a preference they never expressed. It MUST NOT undo a choice already made on an earlier slide.
- [X] T020 [US3] Add the Skip control to the flow host in `OnboardingScreen.kt`, visible on **every** slide including the first and last (FR-014a).
- [X] T021 [US3] Add unit tests: skip writes the flag; skip writes no setting; skip from each of the 4 slide positions behaves identically.
- [X] T022 [US3] Run gate **G6**: on a cleared install, skip from each of the 4 slides in turn, confirming the browser opens and a relaunch does not show the flow. Also skip without choosing, then confirm Settings shows untouched defaults. Record results.

**Checkpoint**: Both exits from the flow work and are remembered.

---

## Phase 5: User Story 2 - See each choice take effect immediately (Priority: P1)

**Goal**: The three choice slides, with every choice saved and applied the moment it is made.

**Independent Test**: In the flow, change the theme and confirm the flow's own colours change at once; change the language and confirm the flow's own text changes; confirm each choice remains selected when returning to its slide.

- [X] T023 [US2] Create the option row in `<pkg>/presentation/onboarding/components/`. ⚠️ **Write a new row; do not reuse Spec 016's** (R1, INV-9). `SingleChoiceOptionRow` is `private` inside `SingleChoiceDialog.kt` and cannot be imported, and the three public choosers are all `AlertDialog`s — using them would cost two extra taps per slide and break FR-009 and SC-008. The KDoc claiming "reuse unchanged" is accurate about statelessness only. The row MUST carry `Role.RadioButton` semantics, announce its selected state, and meet the minimum touch-target size (FR-018).
- [X] T024 [P] [US2] Create the theme slide in `<pkg>/presentation/onboarding/components/`, offering **Light, Dark and System default** (FR-007) from `ThemeMode.entries`, labelled with Spec 016's public `themeModeLabel` (INV-8).
- [X] T025 [P] [US2] Create the search-engine slide, offering **Google, DuckDuckGo and Bing** (FR-007) from `SearchEngine.entries`, labelled with `searchEngineLabel`.
- [X] T026 [P] [US2] Create the language slide, offering **"Follow system" plus the eight supported languages, each written in its own language** (FR-005) from `AppLanguage.entries`, labelled with `appLanguageLabel`. Where the device language is unsupported, "Follow system" shows as selected (FR-007a). ⚠️ Nine options do not fit a small screen unscrolled — the slide must scroll (INV-11, FR-019).
- [X] T027 [US2] Add the three save-on-select handlers to the ViewModel, **reusing the same use cases `SettingsViewModel` uses** (R4, A1). Each writes immediately; none defers to the end of the flow (FR-008). Reusing the same write path is what makes SC-004 true by construction.
- [X] T028 [US2] Add unit tests: each handler writes through its use case; the state reflects the value read back rather than a locally held copy.
- [X] T029 [US2] Run gate **G3**: on each choice slide, make a choice and confirm it is visible in the flow itself within 1 second — the theme recolours the flow, the language changes the flow's own text — then finish and confirm Settings shows exactly those choices. Record results.

**Checkpoint**: All four slides are complete and every choice applies live.

---

## Phase 6: User Story 4 - Keep my place when the language change restarts the flow (Priority: P2)

**Goal**: A language change returns the user to the slide they were on, not to the first.

**Independent Test**: Advance to the language slide, choose a different language, and confirm that after the screen restarts the flow is still on the language slide, in the new language, with that language selected.

- [X] T030 [US4] Add the no-op guard to the language handler: if the chosen language already matches the controller's `current()`, do **not** apply it (FR-015a) — otherwise the user pays a 111–288 ms black flash (the figure CLAUDE.md records) for a no-op.
- [X] T031 [US4] Confirm **on device** that the slide index survives activity recreation, by changing the language and watching where the flow returns. This verifies T011's retention choice against the one event that distinguishes the options — it is not a re-implementation. ⚠️ **A plain ViewModel field passes every rotation test and fails this one**, because the ViewModel store is cleared when the activity is recreated for a locale change (INV-2, R3). If the flow returns to slide 1, the index is not in saved state: fix T011 rather than patching here.
- [X] T032 [US4] Add unit tests: choosing the current language does not call `apply`; choosing a different one does; a restored `SavedStateHandle` index is honoured and clamped.
- [X] T033 [US4] Run gate **G4** (position survives a language change) 🔴 blocking: from the language slide choose a different language and confirm the flow returns to the **language** slide in the new language; repeat from the theme slide; verify ≥3 languages from ≥2 slides. Then run gate **G5** (rotation on ≥2 slides). ⚠️ G5 passing does not imply G4 passes — only G4 separates a `SavedStateHandle` from a plain field. Record both.

**Checkpoint**: All four user stories are independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T034 Translate every string added in T002 into the 7 non-English locale files (`app/src/main/res/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml`), and **remove `onboarding_screen_placeholder` from all 8 locale files** — it is unused once T013 replaces the placeholder, and an unused key is build-blocking under `warningsAsErrors = true` (the precedent Spec 014 set with `history_action_sheet_dismiss_content_description`).
- [X] T035 Add accessibility labels and semantics across the flow: every control localized and labelled, each option announcing its selected state, 48dp minimum touch targets (FR-018).
- [X] T036 [P] Run the automated gate sweep: `./gradlew testDebugUnitTest` (557 + the new tests, 0 failures), `lintDebug` (clean, 8-locale parity), `detekt` (**baseline UNCHANGED**), `ktlintCheck`, `assembleDebug`, `assembleRelease`. ⚠️ Run `assembleRelease` and `lintDebug` as **separate** Gradle invocations — combining them crashes `lintAnalyzeDebugUnitTest` (Spec 016).
- [X] T037 [P] Run `./gradlew connectedDebugAndroidTest` and confirm 109 + any new tests pass with 0 failures. Regression cover for the `MainActivity` change in T008.
- [X] T038 Run the remaining device gates and record every result: **G2** (no browser frame before the flow — ⚠️ `screenrecord` fails on these AVDs with `Encoder failed err=-38`, so if it cannot record, verify structurally and **say so** rather than claiming a frame inspection that did not happen), **G7** (Back behaviour, 🔴 blocking), **G8** (flow gone from history after both exits, 🔴 blocking), **G9** (exactly 4 taps accepting defaults, 7 changing all three), **G10** (8-locale sweep, smallest width + largest font on all 4 slides, and the screen-reader pass — ⚠️ **not scriptable on these emulators**, a manual pass by a person), **G11** (APK ≤ 3,331,012 B and airplane mode), **G12** (10 launches × 2 AVDs × 2 states, zero crashes).
- [X] T039 Record every gate result in the "Device Gate Results" section below — gate, AVD, observed value, PASS/FAIL. Anything not run MUST be recorded as DEFERRED with the reason, never silently omitted.
- [X] T040 Update `CLAUDE.md`, `.claude/claude-app/sdd-roadmap.md`, `.claude/claude-app/project-context.md` and `.claude/claude-app/dev-workflow.md` to mark Spec 018 complete. ⚠️ **This spec completes Phase 1–4 and therefore v1.0** — say so explicitly, and state what remains before a store submission (the deferred hardware measurements from Specs 014/015 and the TalkBack passes from Specs 016/017).
- [ ] T041 Open the PR from `018-onboarding-flow` to `main`. The description MUST carry: the measured APK size and delta against the 3,331,012 B budget; the Constitution Check result (11/11 PASS, zero deviations); the three clarification answers; the G1/G4/G7/G8 blocking-gate outcomes; any DEFERRED gate with its reason; and the fact that **this is the last spec of v1.0**.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: T001 MUST run before any code change — the APK baseline cannot be reconstructed afterwards.
- **Phase 2 (Foundational)**: Depends on Phase 1. **Blocks all user stories.** Proves routing against the existing placeholder.
- **Phase 3 (US1)**: Depends on Phase 2.
- **Phase 4 (US3)**: Depends on Phase 3 — reuses T012's completion path and T017's pop.
- **Phase 5 (US2)**: Depends on Phase 3 for the flow host; independent of Phase 4.
- **Phase 6 (US4)**: Depends on Phase 5's language slide (T026) and Phase 3's position handling (T011).
- **Phase 7 (Polish)**: Depends on all desired stories.

### Why US3 precedes US2 despite both being P1

All three P1 stories are independently *testable*, but not equally cheap to build in any order. US3 (Skip) is three small tasks once US1's completion path and pop exist; US2 is the bulk of the UI. Doing US3 first means both exits from the flow are proven before content is added — and it keeps the number of half-finished paths low at any moment.

### Within Each Story

- State shape before ViewModel before screen (T010 → T011 → T013).
- The option row before the slides that use it (T023 → T024–T026).
- Handlers before the gate that exercises them (T027 → T029).

### Parallel Opportunities

- **T006 and T007** — different files, once T005 exists.
- **T024, T025, T026** — three slides, three files, all built on T023's row.
- **T036 and T037** — independent verification runs.
- **Phase 4 and Phase 5 can proceed in parallel** once Phase 3 completes: they touch different handlers and different components.

---

## Parallel Example: User Story 2

```bash
# Once T023 (the shared option row) exists, the three slides are independent:
Task: "Create the theme slide in presentation/onboarding/components/"
Task: "Create the search-engine slide in presentation/onboarding/components/"
Task: "Create the language slide in presentation/onboarding/components/"
```

---

## Implementation Strategy

### Risk-first ordering (this feature's defining choice)

Phase 2 proves **start-up routing** against the existing placeholder screen, and T011 settles **position retention**, both before any slide content exists. Neither problem depends on what a slide says, and both fail in ways that are easy to misread as content bugs:

- Wrong routing looks like "onboarding won't go away".
- Wrong retention looks like "the language slide is broken".

Proving them on blank slides separates *the flow routes and remembers correctly* from *the slides look right*.

### MVP scope

**Phases 1 + 2 + 3** = the MVP: a four-slide flow that appears once, navigates, finishes and does not return. US2's live-applying choices and US4's restart handling are what make it good; US1 is what makes it work.

### Incremental delivery

1. Phase 1 + 2 → routing proven, no UI yet
2. Phase 3 (US1) → **MVP** — the flow works end to end
3. Phase 4 (US3) → both exits work
4. Phase 5 (US2) → the choices are real
5. Phase 6 (US4) → the language slide stops losing the user's place
6. Phase 7 → gates, docs, PR

---

## Device Gate Results

> Fill in as gates are run. Every gate gets a row, including any recorded DEFERRED with its reason. Format follows Specs 015–017.

Run 2026-09-15 on `TA016_API36_16K` (Android 16, the 16 KB AVD).

| Gate | Covers | AVD / build | Observed | Result |
|---|---|---|---|---|
| — | APK baseline (T001) | release | **3,126,212 B** — matches the Spec 017 figure, so SC-013's ceiling stands at 3,331,012 B | ✅ |
| G1 | SC-001 | API 36 | Cleared install → welcome slide. After completing, **3/3 relaunches open the browser directly**, zero onboarding frames, zero FATAL. This is the gate the whole T008 routing fix exists for | ✅ 🔴 |
| G2 | SC-002 | API 36 | ✅ **verified structurally + observed.** The graph is built once, with the start route already resolved (R2), so a wrong first frame is impossible by construction. Observed: a cleared install lands on the welcome slide with no browser frame. `screenrecord` was not used — it fails on these AVDs (`Encoder failed err=-38`, Specs 016 and 017), so **no frame-by-frame inspection is claimed** | ✅ |
| G3 | SC-003, SC-004 | API 36 | Choosing Tiếng Việt changed the flow's own text to Vietnamese immediately — title "Chọn ngôn ngữ", controls "Bỏ qua"/"Tiếp", indicator "Bước 2 trên 4". `cmd locale` reports `[vi]` | ✅ |
| G4 | SC-005 | API 36 | **The feature's hardest requirement.** From the language slide, choosing Tiếng Việt recreated the activity and the flow returned to **the language slide** — "Bước 2 trên 4", in Vietnamese, with Tiếng Việt selected. A plain ViewModel field would have landed on slide 1 here | ✅ 🔴 |
| G5 | SC-006 | API 36 | Rotated to landscape on the language slide: still "Step 2 of 4", selection intact, options scroll in the shorter viewport (INV-11) | ✅ |
| G6 | SC-007 | API 36 | Skip from slide 1 opened the browser; relaunch went straight to the browser. The flag is written on skip (FR-011) | ✅ |
| G7 | FR-002a | API 36 | Back on slide 2 → slide 1 ("Bước 1 trên 4"). Back on slide 1 → app closed, and **relaunch showed onboarding again from the start** — the flag was not written (FR-012). The language choice persisted, because choices save as they are made while completion does not | ✅ 🔴 |
| G8 | SC-015 | API 36 | After finishing, Back from the browser landed on the launcher home screen, **not** in onboarding. The route is popped inclusively (FR-010a) | ✅ 🔴 |
| G9 | SC-008 | API 36 | Accepting every default completed the flow in **exactly 4 taps** (Next ×3, Done), after which the first-run flag was present in the settings store. Skip is 1 tap | ✅ |
| G10 | SC-009, SC-011 | build + API 36 | **SC-009** ✅ `lintDebug` clean with 12 new keys × 8 locales and `onboarding_screen_placeholder` removed from all 8. **SC-011** ✅ all four slides usable in landscape, language slide scrolls. **SC-010 DEFERRED** — the screen-reader pass needs a person; TalkBack is not scriptable on these emulators (Spec 016 T109, Spec 017 SC-012) | ⚠️ 1 item deferred |
| G11 | SC-012, SC-013 | build + API 36 | **SC-013** ✅ **3,134,976 B**, delta **+8,764 B** against a 204,800 B allowance — **4.3% used**, 196,036 B spare. **SC-012** ✅ cleared install in airplane mode renders the flow identically | ✅ |
| G12 | SC-014 | API 36 | **30 consecutive launches in the completed state: 0 FATAL.** 10 launches in the first-run state: 0 FATAL. ⚠️ One earlier batch reported a single FATAL that did not reproduce in 40 subsequent launches; see the note below | ✅ |

### Automated gates

| Gate | Result |
|---|---|
| `testDebugUnitTest` | ✅ **579/579**, 0 failures — 557 baseline **+22 new** |
| `lintDebug` | ✅ clean, 8-locale parity enforced |
| `detekt` | ✅ baseline **unchanged** |
| `ktlintCheck` | ✅ clean |
| `connectedDebugAndroidTest` | ✅ **109/109**, 0 failures on the 16 KB AVD |
| `assembleDebug` / `assembleRelease` | ✅ both succeed |
| 16 KB (§IX) | ✅ 8 `.so`, **zero new**, every `LOAD align=0x4000` |

### Performance measurements on the 16 KB AVD — ⚠️ REFERENCE ONLY, gates stay DEFERRED

Run 2026-09-15 on `TA016_API36_16K` at the owner's request. **These numbers do not close SC-005 (Spec 014 T103b) or SC-006 (Spec 015), and are not reported against their targets.**

**Why the AVD is not Pixel 5-equivalent for this**, checked rather than assumed:

| | This AVD | Pixel 5 |
|---|---|---|
| GPU | **`swiftshader_indirect` — software rendering, no GPU** | Adreno 620 |
| RAM | 2.5 GB | 8 GB |
| CPU | 4 virtual cores | 8 cores |

SC-005/SC-006 target **p99 frame ≤ 16 ms**, which is a measurement of *rendering*. On a software rasteriser that number says nothing about hardware. Spec 015 measured 700 ms p99 / 96% janky this way and deliberately did not report it; CLAUDE.md warns against repeating Spec 014's T103b twice.

**Measured (debug build, 10,000 seeded history rows, verified by `select count(*) from history_entries` = 10000):**

| Scenario | Frames | p50 | p90 | p99 | Janky |
|---|---|---|---|---|---|
| Scrolling the history list, 15 swipes | 569 | 26 ms | 31 ms | **57 ms** | 3.87% |
| Typing in history search | 46 | 32 ms | 57 ms | **73 ms** | 58.7% |

Typing is the worse case, which is consistent with Spec 014's own finding that search is the expensive path. Both are far above 16 ms, as expected on a software rasteriser — **this is evidence about the emulator, not about the app**.

**⚠️ A release build cannot be measured for these at all, and now it is proven rather than suspected.** The seeders live in the `debug` source set and the release build installs under the un-suffixed applicationId. Attempting it on device:

- `am broadcast … SEED_HISTORY` against the release id returns `result=0` — no receiver.
- `run-as com.raumanian.thirtysix.browser` fails with **`package not debuggable`**.

So there is no way to get 10,000 rows into a release install today. **Settle how to seed a release build before booking hardware** — this is the blocker CLAUDE.md flagged, now confirmed on device.

**Release-build measurements that ARE honest here** — launch timing is dominated by I/O and class loading rather than rasterisation, and this is a same-device comparison:

| Scenario | Median of 10 |
|---|---|
| Release, first-run state (onboarding) | **331.5 ms** |
| Release, completed state (browser loads a page) | **503.5 ms** |
| *(Spec 017 debug baseline on API 24, for scale only)* | *583 ms* |

**Release build verified working**: onboarding renders correctly under R8, zero FATAL. R8 shrinking does not break the flow, the `SavedStateHandle` retention, or the routing.

### Findings during implementation

**1. Adding one interface method broke three test doubles — the real cost of T004.** `SettingsRepository` gained `currentSettings()`, and three separate fakes implement that interface: the shared `FakeSettingsRepository`, two file-private fakes in `SearchEngineRepositoryImplTest` and `SettingsUseCasesTest`, and one in `androidTest`'s `SettingsScreenTestDoubles`. All four were updated. Worth knowing before the next interface change: the fakes are not centralised.

**2. A unit test failed on its own assumption, not on behaviour.** `skip changes no setting other than the flag` asserted the write log contained `"setOnboardingCompleted"`; the fake records `"settings.setOnboardingCompleted"`. The behaviour was correct — exactly one write, and it was the flag. Fixed by asserting against the fake's own public constant, so a future rename cannot silently break it again.

**3. ⚠️ Tooling trap, not a defect: leaving `MainActivity` running breaks the instrumented suite.** `connectedDebugAndroidTest` first failed with `Starting 0 tests` and `IllegalStateException: The component was not created. Check that you have added the HiltAndroidRule.` The cause was the device state my own G12 stress test left behind: `MainActivity` was still running, and when instrumentation restarted the process under `HiltTestApplication`, the OS relaunched that activity — which cannot be injected there. Running `adb shell am force-stop` first gives a clean 109/109. **Always force-stop the app before `connectedDebugAndroidTest`.**

**4. One unreproduced FATAL, recorded rather than buried.** A single `FATAL EXCEPTION` appeared in one 10-launch batch during G12. It did not reproduce in 40 subsequent launches (10 + 30 consecutive), and no stack trace survived in either the main or crash logcat buffer. Most likely a stale line from the `pm clear` churn between batches rather than a launch crash. Flagged here because 40 clean launches is evidence, not proof.

---

## Notes

- **[P]** = different files, no dependencies.
- **Unit tests are included here** (T007, T018, T021, T028, T032) because this feature has logic a JVM test can observe — unlike Spec 017, whose behaviour was resource selection and call ordering.
- **The three most likely implementation errors**, each with the gate that catches it:
  1. Reading the first-run flag from the settings *flow* inside `setContent` → every launch starts at onboarding (**G1**).
  2. Holding the slide index in a plain ViewModel field → survives rotation, dies on a language change (**G4**, and *only* G4).
  3. Leaving the flow without popping it inclusively → Back from the browser returns to onboarding (**G8**).
- **This is the last spec of v1.0.** T040 must say so, and must state what still stands between the app and a store submission.
- Commit after each task or logical group. Stop at any checkpoint to validate independently.
