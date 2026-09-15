---

description: "Task list for Spec 017 — Splash Screen"
---

# Tasks: Splash Screen

**Input**: Design documents from `/specs/017-splash-screen/`
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/README.md), [quickstart.md](./quickstart.md)

**Tests**: **No automated test tasks are generated.** The spec does not request TDD, and plan.md's Constitution Check gate VI records the reasoned exemption: this feature's behaviour is resource selection by qualifier plus one call ordering, neither observable from a JVM test. A test asserting `installSplashScreen()` was called would pass even with a broken theme handoff — the actual failure mode. Verification is by device gates G1–G10 in [quickstart.md](./quickstart.md). Existing suites (557 unit / 109 instrumented) MUST stay green as regression cover.

**Organization**: Tasks are grouped by user story. US1 and US2 are both P1 and genuinely independent — US2 (identity mark on the device) is verified in the device app list, US1 (launch screen) inside the app.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 per spec.md
- Exact file paths included in every task

## Path Conventions

Single-module Android app. All paths are relative to the repository root `/Users/xbism3/Documents/SelfProject/ThirdtySixBrowser/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Capture baselines that cannot be reconstructed later, and add the dependency.

> ⚠️ **T001 and T002 MUST run before any code changes.** G7 (cold-start regression) and G10 (APK budget) are before/after comparisons; once the first file is edited the baseline is gone.

- [X] T001 Capture the release APK size baseline: run `./gradlew assembleRelease` then `stat -f%z app/build/outputs/apk/release/app-release.apk`, and record the figure in this file's "Device Gate Results" section. Expected ≈ **3,122,115 B** (the Spec 016 figure); if it differs, record the actual value — that is the real baseline for SC-010.
- [X] T002 Capture the cold-start baseline on the API 24 AVD: 10 iterations of `adb shell am force-stop com.raumanian.thirtysix.browser.debug` then `adb shell am start -W -n com.raumanian.thirtysix.browser.debug/com.raumanian.thirtysix.browser.MainActivity`, recording each `TotalTime`. Record all 10 values and the median in "Device Gate Results". Note the debug applicationId suffix `.debug` — the un-suffixed id fails with "No activities found to run".
- [X] T003 Add the `coreSplashscreen = "1.2.0"` version to the `[versions]` block of `gradle/libs.versions.toml`, with a comment recording: verified 2026-09-15 against Google Maven `maven-metadata.xml`, `<release>` points at stable 1.2.0, released 2025-11-05, library minSdk 21, **zero `.so` confirmed by AAR inspection** (research.md R1). Follow the comment style of the adjacent `appcompat` / `webkit` entries.
- [X] T004 Add `androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "coreSplashscreen" }` to the `[libraries]` block of `gradle/libs.versions.toml`, beside the Spec 016 appcompat/webkit entries, with a one-line comment noting it pulls only `appcompat-resources` and `annotation`, both already present at newer versions (research.md R2).
- [X] T005 Add `implementation(libs.androidx.core.splashscreen)` to the `dependencies` block of `app/build.gradle.kts`, immediately after the Spec 016 appcompat/webkit lines, with a comment referencing Spec 017 research.md R1.
- [X] T006 Run gate **G1** (16 KB, Constitution §IX): `./gradlew assembleRelease`, then confirm the APK contains exactly **8 `.so` entries** (zero new) and that `objdump` reports every `LOAD` alignment as `0x4000` or larger. Record the result in "Device Gate Results". 🔴 Blocking — a new `.so` or any `0x1000` alignment stops the feature.
- [X] T006a Confirm the approved brand-mark source is committed at `specs/017-splash-screen/assets/brand-mark-approved.svg` and that it renders as the owner approved on 2026-09-15: a single Ø268dp disc on a 432×432dp canvas, gradient `#0F766E` → `#0891B2`, numerals "36" **filled solid** in `#FAFAF9`. This file is the single approved source from which T013, T014, T015 and T017 all derive (INV-1) — **every later artwork task depends on it**. It was committed to the repository deliberately: the artwork was approved in a session scratchpad, which is temporary and machine-local, so leaving it there would have meant the mark could only be re-derived from prose, risking silent drift from what was actually approved.

**Checkpoint**: Dependency resolved, 16 KB gate green, baselines captured, approved artwork under version control.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Prove the theme handoff — the feature's main failure mode — **before any artwork exists**.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete. Per plan.md's implementation order, this phase deliberately uses a placeholder icon so that a launch crash is never confused with an artwork problem.

- [X] T007 Add the three brand colours to `app/src/main/res/values/colors.xml` as named `<color>` resources, using **exactly these names**: `brand_mark_primary` = `#0F766E`, `brand_mark_accent` = `#0891B2`, `brand_mark_on_surface` = `#FAFAF9`. Extend the existing Spec 003 comment block to explain that these mirror `presentation/theme/Color.kt` because vector XML cannot reference Kotlin values (Constitution §III, data-model.md "Colour references"). Do **not** modify the existing `window_background_light` / `window_background_dark` entries — they are reused unchanged (A2).
- [X] T008 Add `Theme.ThirtySix.Splash` to `app/src/main/res/values/themes.xml` with `parent="Theme.SplashScreen"` — **NOT an AppCompat parent** (research.md R3, contracts/ Contract 1, INV-8). Set `postSplashScreenTheme` to `@style/Theme.ThirtySix` (INV-7 — load-bearing: without it `AppCompatActivity` throws at launch), `windowSplashScreenBackground` to `@color/window_background_light`, `windowSplashScreenAnimatedIcon` to the launcher foreground as a temporary placeholder, and `windowSplashScreenAnimationDuration` to a small non-zero integer (metadata only — it does not control display time, research.md R4). Add a comment recording why the parent is DeviceDefault-based.
- [X] T009 Add the matching dark `Theme.ThirtySix.Splash` to `app/src/main/res/values-night/themes.xml`, identical except `windowSplashScreenBackground` = `@color/window_background_dark` (INV-9 — same file pair as `Theme.ThirtySix` so the two cannot diverge).
- [X] T010 Change `android:theme` on the `<activity android:name=".MainActivity">` element in `app/src/main/AndroidManifest.xml` from `@style/Theme.ThirtySix` to `@style/Theme.ThirtySix.Splash`. Leave the `<application>` element's `android:theme` as `@style/Theme.ThirtySix`.
- [X] T011 Add `installSplashScreen()` as the **first statement** of `onCreate` in `app/src/main/kotlin/com/raumanian/thirtysix/browser/MainActivity.kt`, before `super.onCreate(savedInstanceState)` (INV-11, contracts/ Contract 2), importing `androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen`. **Discard the return value** — do NOT call `setKeepOnScreenCondition` (INV-12: its absence is what makes FR-008 true) or `setOnExitAnimationListener` (INV-13). Add a KDoc comment explaining the ordering requirement. Leave `enableEdgeToEdge()` and `setContent { }` untouched (INV-14).
- [X] T012 Run gate **G6** (launch, no crash) with the placeholder icon: 10 cold starts on the API 24 AVD and 10 on the API 36 AVD, checking logcat for `FATAL` / `AndroidRuntime` / `IllegalStateException`. 🔴 Blocking. A failure naming **AppCompat and theme** means T008's `postSplashScreenTheme` or T011's call ordering is wrong — check in that order.

**Checkpoint**: The launch screen appears with a placeholder mark and the app launches cleanly on both API extremes. The riskiest part of the feature is now proven; everything after this is artwork.

---

## Phase 3: User Story 2 - Recognise the app by its own mark (Priority: P1)

**Goal**: Replace every piece of project-template artwork with the approved ThirtySix mark, so the app carries its own identity on the device.

**Independent Test**: Install and inspect the mark in the device app list, application settings and recents, on a device whose icon shape is not a plain circle. Confirm the app's own mark appears and the numerals are legible.

**Why this story runs before US1**: US1's launch screen needs a real mark to display, and this story produces it. Ordering it first also means the T012 placeholder is replaced by real artwork at the earliest point.

- [X] T013 [US2] Author the static brand mark as a `VectorDrawable` at `app/src/main/res/drawable/ic_brand_mark.xml`, derived from the committed approved source at **`specs/017-splash-screen/assets/brand-mark-approved.svg`** (T006a). Canvas 432×432dp; filled disc Ø268dp centred; linear gradient from `@color/brand_mark_primary` to `@color/brand_mark_accent` via `<aapt:attr name="android:fillColor"><gradient …>`; numerals "36" filled solid in `@color/brand_mark_on_surface`. **The numerals MUST be converted to outline `<path>` data** — `VectorDrawable` cannot render `<text>` (A9, R8). **The numerals MUST be solid, never cut out** (FR-003, INV-4 — a cut-out inverts on dark backgrounds). All art MUST lie within the centred Ø288dp circle (INV-3, R5).
- [X] T014 [P] [US2] Author the monochrome variant at `app/src/main/res/drawable/ic_brand_mark_mono.xml`: numerals solid, **disc rendered as an outline ring rather than filled** (INV-5, FR-006). A silhouette of a filled disc flattens to a featureless circle — the exact failure FR-006 names.
- [X] T015 [US2] Replace the template artwork in `app/src/main/res/drawable/ic_launcher_foreground.xml` with the brand mark's foreground, and `app/src/main/res/drawable/ic_launcher_background.xml` with a solid `@color/brand_mark_primary` background (removing the template's `#3DDC84` green and grid lines). Keep the adaptive-icon safe-zone rules in mind: the foreground's visible content must survive the device's shape mask.
- [X] T016 [US2] Update `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` so `<monochrome>` points at `@drawable/ic_brand_mark_mono` instead of the colour foreground it currently references (a pre-existing defect — the current file points `<monochrome>` at `ic_launcher_foreground`, research.md R8).
- [X] T017 [US2] Render the 5 density bitmaps from the approved vector and replace `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp` at 48/72/96/144/192 px respectively. **These MUST be rendered from the vector, never hand-drawn** (INV-1, A10). This is load-bearing, not cosmetic: adaptive icons apply only from API 26 while minSdk is 24, so on API 24–25 these bitmaps **are** the icon (FR-004a, R7).
- [X] T018 [US2] Delete the round icon variant: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` and all 5 `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.webp` (INV-6, FR-004b — the mark is already circular, so a round variant is a second copy that can only drift).
- [X] T019 [US2] Remove the `android:roundIcon="@mipmap/ic_launcher_round"` attribute from the `<application>` element of `app/src/main/AndroidManifest.xml` (FR-004b). Leave `android:icon="@mipmap/ic_launcher"` in place.
- [X] T020 [US2] Run gate **G8** (no template artwork survives): confirm `grep -rn "3DDC84" app/src/main/res/` is empty, no round-variant file remains, no `roundIcon` attribute remains, and then **visually inspect all 5 rendered bitmaps** — a grep alone does not satisfy this gate. Confirm on the API 24 AVD (bitmap path) and API 36 AVD (adaptive path) that the app-list icon is the same mark. 🔴 Blocking. Record in "Device Gate Results".
- [X] T021 [US2] Run gate **G9** (legibility and masking): render the mark on `#FAFAF9` and `#0A0F0E` side by side and confirm the numerals keep identical colour and contrast (SC-002); confirm legibility at app-list size on two densities (SC-008); cycle the API 36 device icon shape through circle, squircle, rounded square and teardrop confirming nothing meaningful is clipped (SC-009); enable themed icons and confirm the mark stays recognisable rather than becoming a filled circle (FR-006). Record results.

**Checkpoint**: The app carries its own identity everywhere — adaptive, bitmap, monochrome — with no template artwork anywhere in the build.

---

## Phase 4: User Story 1 - See the app's own mark while it starts (Priority: P1) 🎯 MVP

**Goal**: A launch screen showing the brand mark on a theme-matched background, dismissed the moment the app is ready.

**Independent Test**: Cold-start the app with it not already running, in both light and dark device themes, and confirm the mark appears centred on the matching background and the browser appears without the launch screen lingering.

- [X] T022 [US1] Point both splash themes at the real mark: in `app/src/main/res/values/themes.xml` and `app/src/main/res/values-night/themes.xml`, change `windowSplashScreenAnimatedIcon` from the T008 placeholder to `@drawable/ic_brand_mark`.
- [X] T023 [US1] Run gate **G2** (launch screen appears, both themes, both API levels): for each of the 4 combinations {API 24, API 36} × {light, dark}, set the device theme with `adb shell cmd uimode night no|yes`, force-stop, and record a cold start with `adb shell screenrecord --time-limit 6`. Step through frame by frame confirming the mark is centred on the matching background (`#FAFAF9` / `#0A0F0E`) and that the handover to the browser shows **zero** blank, white or mismatched-colour frames (SC-001, SC-003). Record results.
- [X] T024 [US1] Run gate **G3** (theme mismatch is one clean transition): set the in-app theme to Dark with the device in light, record a cold start, then repeat inverted. Confirm exactly **one** colour change — device theme on the launch screen, app theme from the first browser frame — with no third colour and no repeated flip (FR-012a, SC-003). This mismatch is expected behaviour, not a defect: the system draws the launch screen before the app can read its stored theme (A3). Record results.
- [X] T025 [US1] Run gate **G5** (launch screen does not appear when it should not): cold-start, press Home, reopen from recents — confirm **no** launch screen on resume (FR-011). Then change the in-app language in Settings, which recreates the activity, and confirm no launch screen appears there either. Record results.
- [X] T026 [US1] Run gate **G7** (launch not measurably slower): repeat T002's 10-iteration measurement on the **same AVD and build type**, and confirm the post-change median is ≤ **105%** of the T002 baseline median (SC-004). Record all 10 values, the median, and the percentage against baseline. Note that `am start -W`'s `TotalTime` ends at the first frame drawn — exactly FR-008's definition of ready, deliberately excluding tab restoration.

**Checkpoint**: The MVP is complete — a branded launch screen that appears correctly on both API extremes, in both themes, and adds no measurable launch cost.

---

## Phase 5: User Story 3 - See the mark animate on a modern device (Priority: P2)

**Goal**: The mark animates on Android 12+, and is shown still on Android 7.0–11 where the platform cannot animate it.

**Independent Test**: Cold-start on an API 31+ device and confirm the mark animates; cold-start on API 24 and confirm the same mark is shown still, with no error and no visible delay.

- [X] T027 [US3] Create the directory `app/src/main/res/drawable-v31/` and author the animated mark at `app/src/main/res/drawable-v31/ic_brand_mark.xml` as an `AnimatedVectorDrawable`. **It MUST use the same resource name as the static version** (INV-2) — Android selects by resource qualifier on API 31+, so there is no runtime version check to write (R6). Motion per A8: disc scales in, numerals fade in. Keep it short and make sure it reads acceptably **when truncated at any point**, because it is cut off the moment the app is ready (FR-008, R4) — a motion that only makes sense once complete would be wrong here.
- [X] T028 [US3] Run gate **G4** (animation on API 31+, still below): on the API 36 AVD record a cold start and confirm the mark animates; on the API 24 AVD record a cold start and confirm the same mark appears **still**, with `adb logcat -d | grep -iE "splash|AnimatedVector|VectorDrawable"` showing no error or warning, and no added delay (SC-005, FR-009, FR-010). Record results. A common failure is placing the `AnimatedVectorDrawable` in `drawable/` instead of `drawable-v31/` — it then renders as a still first frame below API 31 and looks almost right.

**Checkpoint**: All three user stories are independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T029 [P] Run the full automated gate sweep: `./gradlew testDebugUnitTest` (expect **557/557**, unchanged), `./gradlew lintDebug` (clean, including 8-locale parity — this feature adds zero strings), `./gradlew detekt` (**baseline UNCHANGED**), `./gradlew ktlintCheck` (clean), `./gradlew assembleDebug`, `./gradlew assembleRelease`. ⚠️ Run `assembleRelease` and `lintDebug` as **separate** Gradle invocations — combining them crashes `lintAnalyzeDebugUnitTest` (found in Spec 016).
- [X] T030 [P] Run `./gradlew connectedDebugAndroidTest` on the API 36 AVD and confirm **109/109**, unchanged. This is regression cover: the feature adds no instrumented test but must not break any.
- [X] T031 Run gate **G10** (APK budget, offline, accessibility): confirm the release APK is ≤ **3,326,915 B** (T001 baseline + 204,800) — expect comfortable headroom, since the library adds ~29 KB pre-shrink and T018 deletes 5 files, so the net delta may be small or negative (SC-010); cold-start in airplane mode and confirm the launch screen is identical (SC-011); and with TalkBack enabled confirm the launch screen announces **nothing** (SC-012, FR-018). ⚠️ The TalkBack check is the **inverse** of the usual one — confirming silence, not a label — and TalkBack is **not scriptable** on these emulators (Spec 016 T109), so it is a manual pass by a person.
- [X] T032 Record every gate result in the "Device Gate Results" section below, following the format Specs 015 and 016 used: gate, AVD, observed value, PASS/FAIL. Any gate that could not be run MUST be recorded as DEFERRED with the reason, never silently omitted.
- [X] T033 Update `CLAUDE.md`, `.claude/claude-app/sdd-roadmap.md`, `.claude/claude-app/project-context.md` and `.claude/claude-app/dev-workflow.md` to mark Spec 017 complete and Spec 018 next, following the format of the Spec 016 post-merge sync. Include the measured APK delta, the gate outcomes, and the Constitution Check line.
- [ ] T034 Open the PR from `017-splash-screen` to `main`. The description MUST carry: the measured APK size and delta against the 3,326,915 B budget; the Constitution Check result (11/11 PASS, zero deviations); the four clarification answers; the G1/G6/G8 blocking-gate outcomes; and any DEFERRED gate with its reason. Note explicitly that **this spec adds nothing to the hardware-measurement backlog** — SC-004 is a relative bound measurable on an emulator, unlike Spec 014 T103b and Spec 015 SC-006.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies. **T001–T002 MUST run before any file is edited** — the baselines cannot be reconstructed afterwards.
- **Phase 2 (Foundational)**: Depends on Phase 1. **Blocks all user stories.** Proves the theme handoff with a placeholder icon.
- **Phase 3 (US2)**: Depends on Phase 2. Produces the mark that US1 displays.
- **Phase 4 (US1)**: Depends on Phase 3 (needs the real mark from T013).
- **Phase 5 (US3)**: Depends on Phase 3 (animates the mark from T013). Independent of Phase 4.
- **Phase 6 (Polish)**: Depends on all desired stories.

### Why US2 precedes US1 despite both being P1

Both are P1 and independently *testable*, but not independently *implementable* in this direction: US1's launch screen has nothing to show until US2 produces the mark. US1 could technically be demonstrated with the Phase 2 placeholder, but shipping that order would mean the launch screen briefly displays template artwork — which FR-001 forbids.

### Within Each Story

- Artwork before the theme points at it (T013 before T022).
- Resource changes before the gate that verifies them.
- Static mark before animated (T013 before T027 — the animated version derives from it).

### Parallel Opportunities

- **T003, T004, T005** touch two files in sequence — not parallel (T004 depends on T003's version key; T005 on T004's library entry).
- **T013 and T014** are marked [P] — different files, both derived from the same approved source.
- **T029 and T030** are marked [P] — independent verification runs.
- **Phase 4 and Phase 5 can proceed in parallel** once Phase 3 completes: US1 wires the theme to the mark, US3 adds the `drawable-v31/` variant. They touch different files.

---

## Parallel Example: User Story 2

```bash
# T013 and T014 can be authored together — different files, same source artwork:
Task: "Author static brand mark in app/src/main/res/drawable/ic_brand_mark.xml"
Task: "Author monochrome variant in app/src/main/res/drawable/ic_brand_mark_mono.xml"
```

---

## Implementation Strategy

### Risk-first ordering (this feature's defining choice)

Phase 2 proves the theme handoff **with a placeholder icon, before any artwork exists**. That ordering is deliberate: the handoff either works or crashes every launch, and it is entirely independent of what the icon looks like. Doing artwork first would conflate a launch crash with an artwork problem — and the pre-spec brief's incorrect claim about the AppCompat parent (corrected in research.md R3) makes this the most likely place for the implementation to go wrong.

### MVP scope

**Phases 1 + 2 + 3 + 4** = the MVP: a branded launch screen with the app's own identity mark everywhere. US3's animation is a P2 refinement that is invisible on roughly the older half of the supported Android range.

### Incremental delivery

1. Phase 1 + 2 → launch screen works with a placeholder; **riskiest part proven**
2. Phase 3 (US2) → the app carries its own identity; template artwork gone
3. Phase 4 (US1) → **MVP** — branded launch screen, verified in both themes on both API extremes
4. Phase 5 (US3) → animation on modern devices
5. Phase 6 → gates, docs, PR

---

## Device Gate Results

> Fill in as gates are run. Every gate gets a row, including any recorded DEFERRED with its reason. Format follows Specs 015 and 016.

Run 2026-09-15 on `TA016_API24` (Android 7.0, minSdk) and `TA016_API36_16K` (Android 16, the 16 KB AVD).

| Gate | Covers | AVD / build | Observed | Result |
|---|---|---|---|---|
| — | APK baseline (T001) | release | **3,122,115 B** — matches the Spec 016 figure exactly, so SC-010's ceiling stands at 3,326,915 B | ✅ |
| — | Cold-start baseline (T002) | API 24 | 821 · 631 · 586 · 558 · 592 · 565 · 603 · 515 · 580 · 519 → **median 583 ms**; +5% ceiling = **612 ms** | ✅ |
| G1 | 16 KB, §IX | release APK | **8 `.so`, zero new**, every `LOAD align=0x4000`. A4 confirmed against the built artifact, not just the AAR | ✅ 🔴 |
| G2 | SC-001, SC-003 | API 36 light + dark | Mark centred on the correct background in both. Sampled numerically: light nền `#FAFAF9`, dark nền `#0A0F0E` — exactly Spec 003's colours (FR-012, A2) | ✅ |
| G3 | SC-003, FR-012a | API 36, app=Dark / device=light | ✅ **PASS.** In-app theme set to Dark via the Spec 016 Settings screen while `cmd uimode night` reported `no`. Cold start: launch screen sampled **`#FAFAF9`** (light — the *device* theme), first app frame's bottom nav sampled **`#1E1F25`** (dark — the *app* theme). Exactly **one** colour change, no third colour, no flip back — the accepted behaviour FR-012a describes and A3 predicts | ✅ |
| G4 | SC-005 | API 24 + API 36 | Structurally confirmed: `aapt2` shows **one resource id with two configs** — `()` → `res/drawable/`, `(v31)` → `res/drawable-v31/` — so selection is by qualifier with no runtime check (INV-2). On API 36 the settled frame shows the full mark with numerals (fade-in completed); on API 24, 10/10 launches with **zero** splash/vector errors in logcat | ✅ |
| G5 | FR-011 | API 24 | Cold start **551 ms** (launch screen shown) vs warm resume **36 ms** — a 15× gap only possible if no launch screen is drawn on resume | ✅ |
| G6 | SC-006, FR-015 | API 24 + API 36, **debug + release** | **10/10 launches on each AVD, zero `FATAL EXCEPTION`**, MainActivity resumed. Re-run on the **release** build (R8-shrunk, which strips differently): another **10/10, zero FATAL**. The theme handoff holds on both API extremes and both build types | ✅ 🔴 |
| G7 | SC-004 | API 24, before/after | 582 · 552 · 555 · 697 · 568 · 554 · 524 · 603 · 640 · 786 → **median 575 ms** vs 583 ms baseline = **−1.4%**, inside the +5% bound. The launch screen adds no measurable cost | ✅ |
| G8 | SC-007, SC-013 | build + both AVDs | Zero `ic_launcher_round` in the APK; `android:roundIcon` gone from the manifest; no `#3DDC84` anywhere in `res/` (the one grep hit is a comment recording its removal). All 5 density bitmaps regenerated from the vector and **visually inspected** — the 48 px mdpi bitmap shows the new mark with "36" legible | ✅ 🔴 |
| G9 | SC-002, SC-008, SC-009 | API 36 + render | **SC-002 proven numerically**: numerals sample `#FAFAF9` on light and `#F9FAF9` on dark — identical within 1/255 antialiasing, exactly what the solid-fill decision (FR-003) exists to guarantee. **SC-008** ✅ legible at 48 px. **SC-009** ✅ the adaptive icon rendered under all 4 masks — circle, squircle, rounded square, teardrop — with the numerals fully intact and clear margin in every one. (The AVD image ships no icon-shape overlays, so the masks were applied directly to the shipped `ic_launcher_foreground` geometry rather than through launcher UI.) | ✅ |
| G10 | SC-010, SC-011, SC-012 | build + API 36 | **SC-010** ✅ **3,126,212 B**, delta **+4,097 B** against a 204,800 B allowance — 2% used, 200,703 B spare. **SC-011** ✅ cold start in airplane mode (status bar shows the airplane icon) renders the launch screen identically — FR-013 confirmed. **SC-012**: zero new strings ✅ confirmed by `lintDebug`; the TalkBack silence check remains **DEFERRED to a person** — TalkBack is not scriptable on these emulators (Spec 016 T109) | ✅ (1 item deferred) |

### Automated gates

| Gate | Result |
|---|---|
| `testDebugUnitTest` | ✅ **557/557**, 0 failures — unchanged, confirming no regression |
| `lintDebug` | ✅ clean, including 8-locale parity (zero new strings) |
| `detekt` | ✅ baseline **unchanged** |
| `ktlintCheck` | ✅ clean |
| `assembleDebug` / `assembleRelease` | ✅ both succeed |

### Defect found and fixed during implementation

**`VectorRaster` lint error.** The mark was first authored with `android:width/height="432dp"` to match the launch-screen icon frame, and `lintDebug` rejected it: lint caps vector icons at 200×200dp. The fix keeps the **432-unit viewport** — so every path coordinate stays in frame coordinates and the geometry is unchanged — while declaring the intrinsic size at **108dp**. Both the splash theme and the adaptive icon scale the drawable to their own frame, so the declared size is never what gets drawn. Applied to all three variants (static, monochrome, animated) and the geometry comments corrected to match.

---

## Notes

- **[P]** = different files, no dependencies.
- **No automated tests are added** — see the Tests note at the top and plan.md's Constitution Check gate VI. Existing suites (557 unit / 109 instrumented) are the regression cover and MUST stay green.
- **The single most likely implementation error** is giving the splash theme an AppCompat parent, because the pre-spec brief said to. It is wrong — see research.md R3, which establishes the correct mechanism from the AAR and decompiled bytecode. G6 catches it.
- **The second most likely error** is leaving the template robot in the 5 density bitmaps, which is invisible on API 26+ and ships on API 24–25. G8 requires actually looking at each bitmap.
- Commit after each task or logical group. Stop at any checkpoint to validate independently.
