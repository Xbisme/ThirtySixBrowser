---

description: "Task list for Spec 019 — UI Redesign"
---

# Tasks: UI Redesign

**Input**: Design documents from `specs/019-ui-redesign/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/README.md](contracts/README.md), [quickstart.md](quickstart.md)
**Design sources**: [design system](https://claude.ai/artifact/K4WG7e6faSAipieBwJHMys) · [screens canvas](https://claude.ai/artifact/FRRCWBmGa85kmLSFT8tmak) · `specs/019-ui-redesign/assets/tokens.json`

**Tests**: Included. Constitution §VI requires unit tests for ViewModels and use cases, and Constitution v1.4.0 Testing Gates 9–10 require compiler reports and recomposition counts for UI specs (research R10).

**Organization**: Grouped by user story. Order follows research R18 (theme → chrome → Home → tabs → libraries → incognito/onboarding → gates), not strictly the P-order, so the measurement baseline is captured against the old UI first.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1–US6 from spec.md

## Path Conventions

- `MAIN` = `app/src/main/kotlin/com/raumanian/thirtysix/browser`
- `TEST` = `app/src/test/kotlin/com/raumanian/thirtysix/browser`
- `ATEST` = `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser`
- `RES` = `app/src/main/res`
- Every Gradle call is prefixed with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Build plumbing and the "before" measurements.

- [X] T001 Add `compose_stability.conf` at the repository root listing `kotlin.collections.*`, `java.time.*` and `com.raumanian.thirtysix.browser.domain.model.*` (research R9)
- [X] T002 In `app/build.gradle.kts` add a `composeCompiler { }` block: always `stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability.conf"))`; `reportsDestination` / `metricsDestination` = `layout.buildDirectory.dir("compose_reports")` **only** when `project.hasProperty("composeReports")` (research R9)
- [X] T003 Verify `./gradlew assembleRelease -PcomposeReports` writes `app/build/compose_reports/*-composables.txt` and that a plain `assembleRelease` writes nothing there; record the baseline count of non-skippable restartable composables per file in "Device Gate Results" below
- [ ] T004 Record the **before** Layout Inspector recomposition counts on `main` (TA016_API36_16K, debug) for the six hot interactions of quickstart G13 (page load, page scroll, new-tab load as the Home baseline, overflow-menu open as the hub baseline, tab grid scroll, download progress) — the hub and Home do not exist yet, so these are the stated stand-ins (analysis A2) in "Device Gate Results"
- [X] T005 [P] Record baseline release APK size and cold-start median (10 launches) on TA016_API36_16K for SC-010/SC-011 in "Device Gate Results"

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Tokens, theme, icons and shared components every screen is built from.

**⚠️ CRITICAL**: No user story work begins until this phase is complete.

### Theme

- [X] T006 Rewrite `MAIN/presentation/theme/Color.kt` with the light and dark values of every M3-mapped token from `specs/019-ui-redesign/assets/tokens.json` per the mapping table in research R2 (primary `#6c4cf1`/`#b6a3ff`, surface `#ffffff`/`#0e0d12`, …); remove every Deep Teal / Cyan value
- [X] T007 [P] Create `MAIN/presentation/theme/ThirtySixColors.kt`: `@Immutable data class ThirtySixColors` with fabStart, fabEnd, heroDeep, heroViolet, heroRose, glass, glassStroke, onGlass, onGlassMuted, surfaceIncognito, onIncognito, positive, tileSky/tileSkyInk, tileSun/tileSunInk, tileMint/tileMintInk, tilePeach/tilePeachInk; light and dark instances; `LocalThirtySixColors = staticCompositionLocalOf` (data-model §5; INV-3: `surfaceIncognito` identical in both themes)
- [X] T008 [P] Rewrite `MAIN/presentation/theme/Shape.kt`: `Shapes(extraSmall = 6.dp, small = 10.dp, medium = 14.dp, large = 20.dp, extraLarge = 28.dp)` plus `ThirtySixShapes(sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), full = CircleShape)` (research R3)
- [X] T009 [P] Update `MAIN/presentation/theme/Type.kt`: keep Poppins/Inter; set letterSpacing −0.5sp on headlineLarge, −0.3sp on headlineSmall, −0.2sp on titleLarge (tokens.json type group)
- [X] T010 [P] Create `MAIN/presentation/theme/Elevation.kt`: `Modifier.softShadow()`, `Modifier.barShadow()`, `Modifier.fabGlow()` drawn in `drawBehind` with a `setShadowLayer` paint on API 28+ and `Modifier.shadow` fallback on 24–27 (research R3)
- [X] T011 Update `MAIN/presentation/theme/Theme.kt` (`ThirtySixTheme`): provide `LocalThirtySixColors` and the shapes object; under dynamic color take primary, primaryContainer and surface roles from the dynamic scheme and derive `heroViolet`, `fabStart`, `fabEnd` from dynamic `primary`, keeping heroDeep/heroRose/tiles/positive/incognito fixed (FR-005, INV-2); expose `object ThirtySixTheme { colors; shapes }` (contracts C7)
- [X] T012 [P] Update `RES/values/colors.xml`, `RES/values/themes.xml`, `RES/values-night/themes.xml`: window and launch backgrounds to the new `surface` values (`#ffffff` / `#0e0d12`); keep AppCompat NoActionBar parents and the splash theme as Spec 017 left them
- [X] T013 [P] Unit test `TEST/presentation/theme/ThirtySixColorsTest.kt`: light/dark instances differ where tokens differ, `surfaceIncognito` equal in both (INV-3), and a WCAG contrast helper asserts the text/surface pairs of SC-009 ≥ 4.5:1 in both themes

### Icons

- [X] T014 Import the Material Symbols in data-model §6 from `google/material-design-icons` (pin the commit hash in this task when done; Apache-2.0) — Rounded, weight 300, grade −25, 24px, outlined plus listed `F` filled variants — as `RES/drawable/ic_<name>.xml` / `ic_<name>_filled.xml`; set `android:autoMirrored="true"` on `arrow_back`, `arrow_forward`, `chevron_right` (research R4)
- [X] T015 [P] Create `MAIN/presentation/theme/AppIcons.kt`: one `@DrawableRes` constant per imported icon so feature code never spells resource names twice

### Shared components (`MAIN/presentation/components/`)

- [X] T016 [P] `GlassIconButton.kt` and `RingIconButton.kt` — 44dp visual / 48dp touch target, glass = `glass` fill + 1dp `glassStroke` ring, no blur (research R5); mandatory `contentDescription`
- [X] T017 [P] `PillButton.kt` — filled, tonal, outline, text, danger-filled variants, 44dp, `ThirtySixShapes.full`, no fixed width
- [X] T018 [P] `ThirtySixChip.kt` — 34dp pill, hairline ring, selected = `primaryContainer` with leading icon
- [X] T019 [P] `QuickTile.kt` — places (white well + hairline + outline icon) and pastel (sky/sun/mint/peach, square well, filled icon); label wraps to two lines
- [X] T020 [P] `FeatureBanner.kt` — stage banner (chiplets around a hero chip + `primaryContainer` strip) and row banner (kicker, note, gradient coin)
- [X] T021 [P] `ListRow.kt` + `SectionHeader.kt` — 44dp leading square (`radius-md`), title 15sp medium one line ellipsized, secondary line `onSurfaceVariant`, trailing slot; no card, no divider
- [X] T022 [P] `ProgressLine.kt` — `ProgressLine(progress: () -> Float, thickness)` drawing the `fabStart→fabEnd` gradient and reading `progress()` **only inside `drawBehind`** (contracts C6)
- [X] T023 [P] `ChoiceDialog.kt` and `ConfirmDialog.kt` — `radius-xl`; choice rows tinted `primaryContainer` when selected, apply-on-tap, no OK; confirm = pastel glyph, title, one sentence, full-width danger button above Cancel (FR-045)
- [X] T024 [P] `SheetFrame.kt` — wraps M3 `ModalBottomSheet` with `ThirtySixShapes.sheet`, `surfaceContainerLow`, a 36×4dp `outlineVariant` handle and navigation-bar insets; used by the hub and every action sheet
- [X] T025 [P] `ShortcutArt.kt` — mesh background from a colour seed (three radial gradients) drawn in one `drawBehind`; used by shortcut cards and tab cards without a screenshot

**Implementation notes (Phase 2)**:
- T014 pinned commit: `google/material-design-icons@49d4db35df873165d6bd6ba09b063c7dafbac2f4`. 53 vectors (41 outlined + 12 filled) converted by a one-off script; `auto_awesome` exists only in the legacy 24-viewport format and is imported as such.
- T016 shipped as one `RoundIconButton` with `RoundIconStyle.{Ringed, Bare, Glass}` rather than two files; T025 `ShortcutArt` shipped as `MeshArt` / `SeededMeshArt`, reused by the Home hero. Component sizes live in a new `presentation/theme/Dimens.kt` (Constitution §III) and shadow colours in `ThirtySixColors.shadow` / `glow`.
- T012/T026–T028 overlap: the brand vectors read `@color/brand_mark_*`, so recolouring `colors.xml` recoloured them with geometry untouched.

**Checkpoint**: theme, icons and components compile; `./gradlew testDebugUnitTest lintDebug detekt ktlintCheck` green; screens still the old layout but recoloured.

---

## Phase 3: User Story 1 — The browser looks and feels new from the first launch (Priority: P1) 🎯 MVP

**Goal**: Violet brand everywhere, new launcher/launch assets, chrome clear of the system bars.

**Independent Test**: Fresh install on 360dp, light then dark: launcher icon, launch screen, onboarding, a loaded page — no teal, nothing under a system bar (quickstart G1, G2).

- [X] T026 [P] [US1] Regenerate `RES/drawable/ic_brand_mark.xml` and `RES/drawable-v31/ic_brand_mark.xml` (same name, animated) from `specs/019-ui-redesign/assets/thirtysix-mark.svg` with gradient `#6C4CF1 → #8F6FFF`, numerals as solid outline paths, 432 viewport / 108dp intrinsic (Spec 017 R3, R8) (research R15)
- [X] T027 [P] [US1] Regenerate `RES/drawable/ic_brand_mark_mono.xml` from `thirtysix-mark-mono.svg` (outline ring, solid numerals)
- [X] T028 [P] [US1] Regenerate `RES/drawable/ic_launcher_foreground.xml` and `RES/drawable/ic_launcher_background.xml` from the violet mark
- [X] T029 [US1] Regenerate the five `RES/mipmap-*/ic_launcher.webp` bitmaps from the violet SVG (API 24–25 fallback, Spec 017 R7) and inspect each visually
- [X] T030 [US1] Create `MAIN/presentation/browser/components/BrowserTopBar.kt`: `windowInsetsPadding(WindowInsets.statusBars)`, compact address well (`surfaceContainer`, lock or mask + hostname, hint when empty), bookmark toggle, Reload/Stop; incognito variant on `surfaceIncognito` with the mask instead of the lock (FR-051); replace the current `topBar` content in `MAIN/presentation/browser/BrowserScreen.kt` with it (FR-008, FR-023)
- [X] T031 [US1] In `MAIN/presentation/browser/BrowserScreen.kt` set the `Scaffold`'s `contentWindowInsets = WindowInsets(0)` and give the current bottom bar `WindowInsets.navigationBars` padding until US3 replaces it (research R16)
- [X] T032 [US1] Run SC-003: `grep -rniE "0f766e|0891b2|5eead4|67e8f9" app/src/main` returns nothing; fix any hit
- [ ] T033 [US1] Device gate G1 (brand) and G2 (insets on API 24, 29, 36; gesture and three-button navigation); record in "Device Gate Results"

**Checkpoint**: US1 complete — the app is violet and nothing overlaps a system bar.

---

## Phase 4: User Story 3 — Browsing chrome and the hub (Priority: P1)

**Goal**: Sliding bars, isolated progress line, five-slot bottom bar without Back, hub sheet replacing the overflow menu.

**Independent Test**: quickstart G6 and G7.

### Tests for User Story 3

- [X] T034 [P] [US3] `TEST/presentation/browser/BrowserViewModelProgressIsolationTest.kt`: 50 `onProgressChanged` calls → 50 `loadProgress` values and **0** new `uiState` emissions (Turbine); progress resets to 0 on load start and finish (contracts C1)
- [X] T035 [P] [US3] `TEST/presentation/browser/ChromeScrollStateTest.kt`: offset clamps to `[0, max]`, positive dy hides, negative shows, `show()` returns 0 (contracts C2)
- [X] T036 [P] [US3] `ATEST/presentation/browser/HubSheetTest.kt`: every tile and button invokes its callback; Back/Forward enabled flags respected; downloads button absent when `hasActiveDownloads = false`; replaces `ATEST/presentation/browser/BrowserOverflowMenuTest.kt` (delete it)

### Implementation for User Story 3

- [X] T037 [US3] In `MAIN/presentation/browser/LoadingState.kt` remove `progress` from `Loading`; in `MAIN/presentation/browser/BrowserViewModel.kt` add `loadProgress: StateFlow<Float>` updated by `onProgressChanged` and reset on start/finish, and stop copying progress into `uiState` (research R8, contracts C1)
- [X] T038 [US3] Create `MAIN/presentation/browser/ChromeScrollState.kt` with `rememberChromeScrollState(maxOffsetPx)` per contracts C2 (`@Stable`, one `mutableFloatStateOf`, no per-delta allocation)
- [X] T039 [US3] In `MAIN/presentation/browser/BrowserWebView.kt` add `onScrollDelta` to `BrowserNavigationCallbacks` and report deltas from `View.setOnScrollChangeListener` (contracts C3)
- [X] T040 [US3] Create `MAIN/presentation/browser/components/PageLoadProgressLine.kt` using `ProgressLine(progress = …)`; in `BrowserScreen.kt` collect `loadProgress` with `collectAsStateWithLifecycle()` into a held `State<Float>` (**no `by`**) and pass `{ progress.value }`; delete `MAIN/presentation/browser/components/BrowserLoadingIndicator.kt` and update `ATEST/presentation/browser/BrowserScreenLoadingIndicatorTest.kt` to the new test tag
- [X] T041 [US3] Create `MAIN/presentation/browser/components/BrowserBottomBar.kt`: Home · Search · raised gradient New tab (`fabGlow`) · Tabs (count in rounded square) · Menu; 76dp, top corners `radius-lg`, `barShadow`, navigation-bar insets, incognito variant on `surfaceIncognito`; **no Back** (FR-020); delete `MAIN/presentation/browser/components/NavigationBottomBar.kt`, `NavigationBottomBarCallbacks.kt` and their tests under `ATEST/presentation/browser/components/`, replacing them with `BrowserBottomBarTest.kt`
- [X] T042 [US3] Apply `ChromeScrollState` in `BrowserScreen.kt`: top and bottom bars use `Modifier.offset { IntOffset(0, …) }` reading the state inside the lambda; the WebView host fills the screen and is shifted with `graphicsLayer { translationY = topBarVisiblePx }`; call `show()` on load start, on error, when `WindowInsets.ime` is visible, and when the page is not scrollable (FR-025, research R7)
- [X] T043 [US3] Wire Search on the bottom bar to focus the address field and open the keyboard — or, while the Home page is shown, the Home search bar (FR-022)
- [X] T044 [US3] Create `MAIN/presentation/browser/components/HubSheet.kt` on `SheetFrame`: Settings ring button; Back and Forward ring buttons (enabled from `canGoBack`/`canGoForward`); downloads-in-progress button only while `hasActiveDownloads`; privacy stage banner → About; incognito row banner → new incognito tab; Downloads/History/Bookmarks place tiles; Theme/Language/Search/Clear data pastel tiles opening the existing choosers from `MAIN/presentation/settings/components/`; closes on drag, back, scrim tap or any choice (FR-030–FR-032, research R12)
- [X] T045 [US3] Expose `hasActiveDownloads: StateFlow<Boolean>` on `BrowserViewModel`, derived from the existing Spec 015 downloads observation and built with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)`; collect it **only inside `HubSheet`** so the platform status is polled only while the hub is open (contracts C1, analysis U2)
- [X] T046 [US3] Delete `MAIN/presentation/browser/components/BrowserOverflowMenu.kt`; route Menu to `HubSheet`; remove orphaned strings (`browser_action_more`, overflow labels) from all 8 `RES/values*/strings.xml`
- [X] T047 [US3] Add strings `nav_search`, `nav_menu`, `hub_privacy_banner`, `hub_incognito_banner_title`, `hub_incognito_banner_body`, `hub_quick_settings_title`, `hub_tile_theme`, `hub_tile_language`, `hub_tile_search`, `hub_tile_clear_data`, `hub_downloads_in_progress_cd` to all 8 `RES/values*/strings.xml`
- [ ] T048 [US3] Update `ATEST/presentation/browser/BrowserScreenInstrumentedTest.kt` and `BrowserScreenOfflineErrorTest.kt` for the new bars (test tags, no Back button); behaviour assertions unchanged
- [ ] T049 [US3] Device gates G6 and G7; record

**Implementation notes (US3)**:
- T034 tests live in `BrowserViewModelTest` (its `newViewModel` builder is private) rather than a new file; T040's progress line is drawn by `AddressBar` itself via the shared `ProgressLine` — no separate `PageLoadProgressLine` file. It sits **outside** the text field: inside `decorationBox` the field merged its tag and its TalkBack label away (caught by `BrowserScreenLoadingIndicatorTest`). `browser_loading_a11y` is reused as its spoken label.
- T045: `hasActiveDownloads` comes from a new `ObserveHasActiveDownloadsUseCase` (+ test) exposed by a small `HubViewModel` (`WhileSubscribed`, collected only inside `HubSheet`), not from `BrowserViewModel` — that kept `BrowserViewModel`'s constructor, and every test that builds it, unchanged. `HubViewModel` also owns "Browse without a trace" (CreateIncognitoTabUseCase) and reports the Spec 012 incognito cap as a snackbar event.
- Quick-setting tiles open Spec 016's choosers through `QuickSettingsHost`, which reuses `SettingsDialogHost` / `SettingsSnackbarEffect` (now `internal`) and a lazily created `SettingsViewModel`.
- Found on device: the hub did not scroll — on a 640dp screen it would have been cut off (now `verticalScroll`); the "Search engine" tile label clipped instead of wrapping (label now fills the tile).
- Strings: 10 new keys ×8 (`nav_tabs_count_cd` as plurals per lint `PluralsCandidate`); `browser_action_more` and `browser_action_tabs_switcher` removed ×8.

**Checkpoint**: Browsing works with the new chrome; overflow menu gone; progress isolated.

---

## Phase 5: User Story 6 — Smooth while it moves (Priority: P1)

**Goal**: Evidence that frame-rate state never rebuilds chrome (Constitution §V rules 1–9, Testing Gates 9–10).

**Independent Test**: quickstart G13.

- [X] T050 [US6] Audit every composable touched so far against contracts C6: no frame-rate value passed as a plain `Float`/`Dp`, no `by` delegation of `loadProgress`, every lazy list has `key` **and** `contentType`; fix findings in place
- [X] T051 [US6] Mark new UI models `@Immutable` (`HubState`, bottom-bar state) and confirm with `-PcomposeReports` that no touched file has an unexplained non-skippable restartable composable
- [ ] T052 [US6] Layout Inspector **after** counts for page load, page scroll and hub drag on TA016_API36_16K; record beside T004's before counts

**US6 audit result (2026-10-10, after US3)**: `-PcomposeReports` — 144 composables, 130 restartable, **0 restartable non-skippable**. Remaining "unstable" parameters are ViewModels, `NavHostController`, `WebViewActionsHandle` and a `StateFlow` — all passed as the same remembered instance, so strong skipping still skips them. `java.io.File` (tab-card images) added to `compose_stability.conf`. No component under `presentation/components/` has an unstable parameter. T052 (Layout Inspector) needs a person, as T004.

*(Home scroll, tab swipe, History scroll and download progress counts are recorded in T068, T082 and T079 when those screens exist; final roll-up in T094.)*

---

## Phase 6: User Story 2 — A new tab opens on a Home page made of my own things (Priority: P1)

**Goal**: Native Home page inside a tab (research R1, R6, R13, R14).

**Independent Test**: quickstart G4 and G5.

### Tests for User Story 2

- [X] T053 [P] [US2] `TEST/domain/model/GreetingPeriodTest.kt`: boundaries 04:59 → Evening, 05:00 → Morning, 11:59 → Morning, 12:00 → Afternoon, 17:59 → Afternoon, 18:00 → Evening (data-model §3)
- [X] T054 [P] [US2] `TEST/domain/usecase/ObserveHomeContentUseCaseTest.kt`: order Tab → Saved → Recent; incognito tabs excluded; active tab excluded; duplicate URLs dropped keeping the first; `HOME_PAGE_URL` dropped; shortcuts ≤ 8, recent ≤ 5
- [X] T055 [P] [US2] `TEST/presentation/browser/BrowserViewModelHomeNavigationTest.kt`: new tab is at `HOME_PAGE_URL`; `onHomeSubmit` produces the same URL as `onAddressBarSubmit` for a URL and for a query and sets `cameFromHome`; blank input is a no-op; `onHomeShortcut` with kind Tab activates `tabId` without loading or setting `cameFromHome`, with Saved/Recent loads and sets it; `onBackWithoutHistory()` returns true and restores Home only when `cameFromHome` and not incognito; `onHomePressed` sets `homeOverlay` without changing the tab URL; Back clears the overlay (contracts C1, data-model §2) — *Done in `TEST/presentation/browser/BrowserViewModelTest.kt` (5 Home navigation cases) rather than a separate file; Tab shortcuts are routed through `HomeViewModel.onTabShortcut`, not `BrowserViewModel.onHomeShortcut`.*
- [X] T056 [P] [US2] `ATEST/presentation/home/HomePageTest.kt`: greeting text per period; empty state shows no "Recently visited" and no card row; tapping a card/row invokes its callback

### Implementation for User Story 2

- [X] T056a [P] [US2] `TEST/presentation/home/HomeViewModelTest.kt` (Constitution §VI): state combines shortcuts, recent and engine from fakes; greeting recomputed by `onResume()` with an injected fixed `Clock`; derived work runs on the default dispatcher (`MainDispatcherRule` + `advanceUntilIdle`) (analysis C1)
- [X] T057 [US2] Add `HOME_PAGE_URL = "thirtysix:home"` to `MAIN/core/constants/UrlConstants.kt`; point `@Named("default_home_url")` in `MAIN/di/UrlConfigModule.kt` at it; add `HOME_SHORTCUT_LIMIT = 8` and `HOME_RECENT_LIMIT = 5` to `MAIN/core/constants/BrowserLimits.kt` (data-model §1)
- [X] T058 [P] [US2] Add `observeRecent(limit: Int)` to `MAIN/data/local/dao/BookmarkDao.kt` (`ORDER BY created_at DESC LIMIT :limit`) and `MAIN/data/local/dao/HistoryDao.kt` (`ORDER BY visited_at DESC LIMIT :limit`), then to `MAIN/domain/repository/{Bookmark,History}Repository.kt` and their `MAIN/data/repository/*RepositoryImpl.kt`; confirm `app/schemas/…/2.json` unchanged (data-model §4)
- [X] T058a [P] [US2] Tests for the new queries (Constitution §VI): extend the Robolectric DAO tests under `TEST/data/local/dao/` (`BookmarkDaoTest`, `HistoryDaoTest`) with `observeRecent` ordering and `LIMIT`, and the repository tests under `TEST/data/repository/` with mapping of `observeRecent` (analysis C1)
- [X] T059 [P] [US2] Create `MAIN/domain/model/GreetingPeriod.kt`, `HomeShortcut.kt` (`stableKey`, `kind`, `title`, `url`, `host`, `tabId`, `screenshotKey`, `colorSeed`), `HomeContent.kt` (data-model §3)
- [X] T060 [US2] Create `MAIN/domain/usecase/ObserveHomeContentUseCase.kt` with the build rule of data-model §3; compute favicon colour seeds (average of opaque pixels) off the main thread, memoised per host (research R13) — *Deviation from R13: the card mesh is seeded from a hash of the host (`seedColorFor`), not a favicon pixel average — same site, same colour, with no bitmap decode per card.*
- [X] T061 [P] [US2] Create `MAIN/di/ClockModule.kt` providing `java.time.Clock.systemDefaultZone()` (research R14)
- [X] T062 [US2] Create `MAIN/presentation/home/HomeViewModel.kt` + `HomeUiState.kt` (`@Immutable`); greeting recomputed in `onResume()`; derived list work under `flowOn(dispatchers.default)` (Spec 014 lesson)
- [X] T063 [US2] Create `MAIN/presentation/home/HomeHero.kt` (mesh in one `drawBehind` from `heroDeep/heroViolet/heroRose`, brand mark, incognito glass button, greeting `headlineLarge`, privacy line, floating search bar with `softShadow` and `imePadding`, `safeDrawing` insets) and `ShortcutCard.kt` (128×148dp, `radius-xl`, screenshot or `ShortcutArt`, glass tag Tab/Saved/Recent, title + host)
- [X] T064 [US2] Create `MAIN/presentation/home/HomePage.kt`: one `LazyColumn` — hero item, shortcut `LazyRow` (`key = stableKey`, `contentType = kind`), "Recently visited" header with "See all", `ListRow`s; hero collapse reads `listState` inside `graphicsLayer { }` only (research R6, contracts C6)
- [X] T065 [US2] In `MAIN/presentation/browser/BrowserViewModel.kt` add `homeOverlay`, `cameFromHome`, derived `isHomeContent`, `onHomePressed`, `onBackFromHomeOverlay`, `onHomeSubmit` (through `AddressBarInputClassifier` and `BuildSearchUrlUseCase`, FR-012), `onHomeShortcut` (Tab kind activates the tab), `onHomeRecent`, `onBackWithoutHistory` (contracts C1); in `BrowserScreen.kt` render `HomePage` when `isHomeContent` for a normal tab, never pass `HOME_PAGE_URL` to the WebView, keep the WebView alive under the overlay, and extend the back handler with `onBackFromHomeOverlay` / `onBackWithoutHistory` while keeping predictive back (FR-010, FR-021)
- [X] T066 [US2] Add a defensive skip of `HOME_PAGE_URL` in `MAIN/domain/usecase/RecordHistoryEntryUseCase.kt`; remove `DEFAULT_HOME_URL` from `UrlConstants.kt` once unused (data-model §1)
- [X] T066a [US2] Audit every consumer of `Tab.url` for `HOME_PAGE_URL` tolerance per contracts C1a — `CookieJarSnapshotManager` origin enumeration, favicon/host extraction, `ScreenshotCache` capture, external-intent handling in `BrowserWebView`, the address well — skipping the sentinel in each; add a case to `TEST/data/local/cookies/CookieJarSnapshotManagerImplTest.kt` proving a Home tab contributes no origin (analysis U3) — *The C1a test lives in `TEST/data/repository/IncognitoTabRepositoryImplTest.kt` #11 (a Home tab contributes no origin); host extraction in the use case uses `java.net.URI` to keep the domain pure.*
- [X] T067 [US2] Add strings `home_greeting_morning`, `home_greeting_afternoon`, `home_greeting_evening`, `home_privacy_line`, `home_recent_title`, `home_recent_see_all`, `home_shortcut_tag_tab`, `home_shortcut_tag_saved`, `home_shortcut_tag_recent` to all 8 locales
- [X] T068 [US2] Device gates G4 and G5; Layout Inspector count for Home scroll (hero must not recompose per frame); record — *G4/G5 PASS on `TA016_API36_16K` 2026-10-10: fresh-install Home with no empty sections; search from Home loads example.com; Home again shows the Recent card + Recently visited; Back from the overlay returns to the page; Back on a page opened from Home restores Home; the hero's incognito button opens the private-tab page (read via the accessibility tree — FLAG_SECURE blacks screenshots). Defect found and fixed: `painterResource(ic_brand_mark)` crashed on API 31+ because `drawable-v31/` resolves to Spec 017's animated-vector — the static mark is now `ic_brand_mark_static`, and `ic_brand_mark` is a default-config alias of it, so the splash's qualifier selection is unchanged (verified with `aapt2 dump resources`). **Layout Inspector count for Home scroll: needs a person** (T004/T052 precedent).*

**Checkpoint**: New tabs and Home open the Home page; Back behaves per data-model §2.

---

## Phase 7: User Story 4 — Libraries, tabs and settings in the new style (Priority: P2)

**Goal**: Tabs (with swipe and chip modes), History, Bookmarks, Downloads, Settings, dialogs restyled; every Spec 011–016 behaviour kept.

**Independent Test**: quickstart G8 and G9.

### Tabs

- [X] T069 [P] [US4] `ATEST/presentation/tabs/TabSwipeTest.kt`: swipe past 50% closes (close callback once), short swipe springs back (no callback), vertical grid scroll unaffected
- [X] T070 [P] [US4] `TEST/presentation/tabs/TabsViewModelChipModeTest.kt`: initial chip = active tab's kind; Incognito chip hidden with no incognito tabs; create action and close-all apply to the selected kind only (clarification Q3)
- [X] T071 [US4] Add `TAB_SWIPE_CLOSE_FRACTION = 0.5f` to `MAIN/core/constants/BrowserLimits.kt`; in `MAIN/presentation/tabs/TabsViewModel.kt` add `selectedKind` state, initialise from the active tab, hide Incognito when empty, route create/close-all by kind — *`TAB_SWIPE_CLOSE_FRACTION` already existed from US2; mode is `TabsMode` on `TabsUiState` with a derived `visibleTabs`.*
- [X] T072 [US4] Rewrite `MAIN/presentation/tabs/components/TabSwitcherCard.kt` as an image card (preview fills, `ShortcutArt` fallback, a tab at `HOME_PAGE_URL` shows `tabs_default_title` over the hero mesh with the brand mark as favicon per data-model §3, dark fade, favicon + title, glass close, active = 2dp `primary` ring with 2dp gap, incognito on `surfaceIncognito`, never a screenshot); swipe via `Animatable` offset read in `graphicsLayer { translationX; alpha }` (research R11) — *Swipe offset is a `mutableFloatStateOf` written by `draggable` and read only in `graphicsLayer`, settled with the suspend `animate()` (an `Animatable` + per-delta `snapTo` launches read a stale value in `onDragStopped`, caught by T069). `TabPlaceholderColor` + its test deleted; the mesh fallback uses `seedColorFor(host)`.*
- [X] T073 [US4] Rewrite `MAIN/presentation/tabs/TabsScreen.kt`: large title, close-all icon, chips (`ThirtySixChip`), `LazyVerticalGrid(GridCells.Adaptive(160.dp))` — two columns at 360dp, more when wider (FR-040, analysis I1) — with `key`+`contentType`, single create card per mode; merge `TabSwitcherNewTabCard.kt` and `TabSwitcherNewIncognitoTabCard.kt` into one `TabCreateCard.kt`; restyle the two close-all dialogs on `ConfirmDialog` — *Close-all dialogs on `ConfirmDialog`; their per-button tags were unused by any test and removed. One close-all icon acts on the mode on screen, keeping both existing action tags.*
- [X] T074 [US4] Add strings `tabs_chip_open`, `tabs_chip_incognito` to all 8 locales; update `ATEST/presentation/tabs/*` and `ATEST/presentation/tabs/components/*` for new tags only — *`tabs_a11y_new_incognito_tab` became unused and was removed from all 8 locales.*

### History, Bookmarks, Downloads

- [X] T075 [P] [US4] Restyle `MAIN/presentation/history/HistoryScreen.kt` and `components/{HistoryTopBar,HistoryDayHeader,HistoryRow,EmptyHistoryState,NoHistoryMatchesState,HistoryActionSheet,ClearAllHistoryConfirmDialog}.kt` on `ListRow`, `SectionHeader`, `SheetFrame`, `ConfirmDialog`: large title, search well, clear-all icon; `items(key = id, contentType = …)` for headers and rows (FR-041) — *Done: no new strings/icons; `ConfirmDialog` has no per-button modifier, so `TEST_TAG_HISTORY_CLEAR_ALL_CONFIRM` is kept but no longer attached (no test used it); row favicons now decode off-main via `rememberFileImage`.*
- [X] T076 [P] [US4] Restyle `MAIN/presentation/bookmarks/BookmarksScreen.kt` and all files in `components/` on the shared components; add top-level folder chips at the root (FR-042); keep breadcrumb inside folders; dialogs on `ChoiceDialog`/`ConfirmDialog`/`SheetFrame` — done: large title + search well, open ListRows under Folders/Pages headers, root folder chips (FR-042), chevron breadcrumb, SheetFrame action sheets, ConfirmDialog delete, AlertDialog inputs at radius 28 with PillButtons; +2 strings × 8 locales
- [ ] T077 [P] [US4] Restyle `MAIN/presentation/downloads/DownloadsScreen.kt` and `components/{DownloadRow,DownloadsTopBar,DownloadActionSheet,EmptyDownloadsState,DeleteDownloadedFileConfirmDialog}.kt`: state as words + colour (`positive`/`error`), progress via `ProgressLine(progress = { … })` read in `drawBehind`, cancel button; **no retry action** (FR-001, FR-043)
- [ ] T078 [US4] Update History/Bookmarks/Downloads instrumented tests under `ATEST/presentation/{history,bookmarks,downloads}/` for new tags only; no behaviour assertion removed (SC-007)
- [ ] T079 [US4] Layout Inspector counts for History scroll (10,000 seeded rows via `HistorySeeder`) and download progress; record

### Settings and dialogs

- [ ] T080 [P] [US4] Restyle `MAIN/presentation/settings/SettingsScreen.kt`, `components/SettingsRows.kt`, `SettingsSectionHeader.kt`, `SettingsTopBar.kt`, `AboutSection.kt`: hairline groups Appearance / Language and search / Privacy / About, pastel icon per row matching its hub tile (Theme sky, Language sun, Search mint, Clear data peach, others primary-soft), large title (FR-044)
- [ ] T081 [P] [US4] Rebuild `MAIN/presentation/settings/components/{SingleChoiceDialog,ThemeModeChooserDialog,AppLanguageChooserDialog,SearchEngineChooserDialog,HistoryRetentionChooserDialog}.kt` on `ChoiceDialog` and `{ConfirmShortenRetentionDialog,ClearBrowsingDataDialog}.kt` on `ConfirmDialog` (FR-045); keep public label functions (`themeModeLabel`, …) used by onboarding
- [ ] T082 [US4] Update `ATEST/presentation/settings/*` for new tags only; device gates G8 and G9; Layout Inspector count for tab swipe (grid and other cards must not recompose per frame); quickstart G16 — Tabs with 10 tabs renders ≤ 200 ms (Constitution §V), DEFERRED to hardware if the emulator is not representative (analysis H1); record

**Checkpoint**: Every library and settings screen matches the canvas; all prior behaviour tests green.

---

## Phase 8: User Story 5 — Incognito and first run (Priority: P2)

**Goal**: Plum incognito chrome and page; onboarding restyled on the hero with Spec 018 behaviour intact.

**Independent Test**: quickstart G10.

- [ ] T083 [P] [US5] Create `MAIN/presentation/incognito/IncognitoPage.kt`: mask coin, title, subtitle, glass panel with the three points, footnote (FR-050); `BrowserScreen.kt` shows it for an incognito tab at `HOME_PAGE_URL` (FR-015)
- [ ] T084 [US5] Add strings `incognito_page_title`, `incognito_page_subtitle`, `incognito_page_section_title`, `incognito_page_point_history`, `incognito_page_point_cookies`, `incognito_page_point_screenshots`, `incognito_page_footnote` to all 8 locales; delete `MAIN/presentation/browser/components/IncognitoIndicator.kt` if the plum chrome makes it redundant, updating its references
- [ ] T085 [US5] Verify bars use `surfaceIncognito` in light and dark for incognito tabs (FR-051) and that FLAG_SECURE behaviour (Spec 012) is unchanged
- [ ] T086 [US5] Restyle `MAIN/presentation/onboarding/OnboardingScreen.kt` and `components/OnboardingSlideContent.kt` on the hero: "Step N of 4", page dots, Skip (glass) on every slide, Back from slide 2, choices as glass/raised rows (language list, three theme preview cards, engine rows), last forward control "Start browsing" with the gradient; welcome slide keeps `settings_about_privacy_statement` (FR-052)
- [ ] T087 [US5] Add `onboarding_action_start_browsing` to all 8 locales (replacing the Spec 018 finish label if its wording differs); re-run Spec 018's onboarding instrumented tests and device gates G1–G12 of `specs/018-onboarding-flow/quickstart.md`; record as G10

**Checkpoint**: All six stories complete.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T088 Remove every remaining `androidx.compose.material.icons.*` import; delete `material-icons-core` from `gradle/libs.versions.toml` and `app/build.gradle.kts`; build green (research R4)
- [ ] T089 [P] Run `./gradlew lintDebug` — 8-locale parity at error severity; remove any orphaned keys (FR-055, research R17)
- [ ] T090 [P] Locale sweep G11: every screen in German and Russian at 360dp, all 8 locales spot-checked; record
- [ ] T091 [P] Dynamic color gate G12 on Android 12+ with three contrasting wallpapers; record
- [ ] T092 Canvas comparison G3: each of the 15 artboards against the built screen, light + dark, 360dp; fix or list differences until 0 open (SC-001)
- [ ] T093 Final `-PcomposeReports` run: compare with T003 baseline; every non-skippable restartable composable in touched files explained in "Device Gate Results" (Testing Gate 9)
- [ ] T094 Layout Inspector roll-up G13: before/after table for all six hot interactions (Testing Gate 10, SC-004); quickstart G17 — with *Remove animations* on, tab swipe release, bar show and hero collapse jump to their end states (analysis G1)
- [ ] T095 [P] G15: release APK ≤ 3,544,576 B, cold start ≤ +5% vs T005, new tab → Home ≤ 300 ms; 16 KB script green, zero new `.so`
- [ ] T096 G14 / SC-005: release build on Pixel 5-class hardware, or record **DEFERRED** with the release-seeding blocker named (never an emulator figure)
- [ ] T097 Full suite: force-stop the app, then `testDebugUnitTest`, `lintDebug`, `detekt`, `ktlintCheck`, `assembleRelease`, `connectedDebugAndroidTest` on TA016_API29_CI and TA016_API36_16K; record counts
- [ ] T098 TalkBack pass: record **DEFERRED** to a person (as Specs 016–018); confirm every icon-only control has a label by code review
- [ ] T099 Update `CLAUDE.md` (Active Spec, Recent Changes, carry-forward facts), `.claude/claude-app/sdd-roadmap.md`, `project-context.md`, `dev-workflow.md` to the delivered state
- [ ] T100 Open the PR `019-ui-redesign` → `main` with the gate table, Constitution Check line, SC-005 status, before/after recomposition table and APK delta

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (1)** → **Foundational (2)** → everything else.
- **US1 (3)** before **US3 (4)**: US3 replaces the bars US1 inset-fixes.
- **US6 (5)** after US3: it audits and measures what US3 built; its later counts land with each screen.
- **US2 (6)** after US3: Home lives inside `BrowserScreen` and uses the new bars and back handler.
- **US4 (7)** after Foundational; independent of US2 except the shared `ShortcutArt` (Phase 2).
- **US5 (8)** after US3 (incognito chrome) and US2 (the `HOME_PAGE_URL` routing).
- **Polish (9)** last.

### Within Each Story

Tests first where listed and failing, then models/use cases, then UI, then strings, then device gate.

### Parallel Opportunities

- Phase 2: T007–T010, T013, T015–T025 are separate files.
- US1: T026–T028 in parallel.
- US2: T053–T056 tests in parallel; T058, T059, T061 in parallel.
- US4: History (T075), Bookmarks (T076), Downloads (T077), Settings (T080–T081) in parallel once Phase 2 is done.

## Parallel Example: User Story 4

```text
T075 History restyle      ─┐
T076 Bookmarks restyle     ├─ separate packages, shared components only
T077 Downloads restyle     │
T080 Settings restyle     ─┘
```

## Implementation Strategy

### MVP scope

Phases 1–3 (Setup, Foundational, US1): the app is violet, uses the new tokens and icons everywhere through the theme, and the status-bar defect is fixed. Shippable on its own.

### Incremental delivery

1. MVP (US1) → 2. Chrome + hub (US3) with progress isolation measured (US6) → 3. Home (US2) → 4. Tabs, libraries, settings (US4) → 5. Incognito + onboarding (US5) → 6. Polish gates.

Each increment leaves every Spec 007–018 behaviour working and all tests green.

---

## Device Gate Results

*(Filled during implementation — AVD, build type and date per gate; DEFERRED stated explicitly.)*

| Gate | Result | Notes |
|---|---|---|
| Baseline T003 compiler report | ✅ 2026-10-10 | `assembleRelease -PcomposeReports --rerun-tasks` (without `--rerun-tasks` the up-to-date compile writes no report). 116 composables, 105 restartable, **0 restartable non-skippable** (strong skipping), 40 parameters inferred unstable (mostly ViewModels and lambdas) |
| Baseline T004 Layout Inspector | ⏸ needs a person | Layout Inspector is GUI-only in Android Studio; not drivable from the CLI. To be recorded by a person on `main` before the redesign merges; JVM state-isolation tests (T034) stand in meanwhile |
| Baseline T005 size / start | ✅ 2026-10-10, TA016_API36_16K, release | APK **3,134,976 B** (matches Spec 018). Cold start, onboarding state, 10 runs: 1981–4082 ms, median ~2.9 s — **emulator under heavy load, far above Spec 018's 331 ms on the same AVD**; the SC-010 comparison will be re-measured back-to-back with the redesign build under identical conditions |
| G1 Brand | ✅ partial 2026-10-10, TA016_API36_16K debug | Adaptive launcher icon violet (beside the still-installed teal release build from `main`, confirming the change); top bar violet with dynamic color off. API 24 bitmap path and themed-icon check still to run |
| G2 Insets | ✅ partial 2026-10-10, API 36 gesture nav | Address well sits fully below the status bar (the Spec 009–018 defect is gone). API 24/29 and three-button navigation still to run |
| G3 Canvas match | — | |
| G4 Home | — | |
| G5 Home navigation | — | |
| G6 Chrome | ✅ partial 2026-10-10, API 36 | Progress line grows on the well's bottom edge, Reload becomes Stop while loading; bars slide on scroll pending a long-page manual check |
| G7 Hub | ✅ partial 2026-10-10, API 36 | Hub matches the canvas; Clear data tile opened Spec 016's chooser over the browser; HubSheetTest 7/7, BrowserBottomBarTest 5/5 |
| G8 Tabs | — | |
| G9 Libraries & settings | — | |
| G10 Incognito & onboarding | — | |
| G11 Locales | — | |
| G12 Dynamic color | — | |
| G13 Recomposition | — | |
| G14 Frame time | — | |
| G15 Size & start | — | |
| G16 Tab switcher ≤ 200 ms | — | |
| G17 Reduced motion | — | |
| TalkBack | — | |

## Notes

- `[P]` = different files, no dependency on an incomplete task.
- Commit after each task or logical group; never commit with a red gate.
- Never report SC-005 from an emulator (Spec 014 T103b lesson).
- Force-stop the app before every `connectedDebugAndroidTest` run (Spec 018 finding).
