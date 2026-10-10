# Implementation Plan: UI Redesign

**Branch**: `019-ui-redesign` | **Date**: 2026-10-10 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/019-ui-redesign/spec.md`

## Summary

Rebuild every screen from the approved [design system](https://claude.ai/artifact/K4WG7e6faSAipieBwJHMys) and [screens canvas](https://claude.ai/artifact/FRRCWBmGa85kmLSFT8tmak): violet brand, white surfaces, large soft corners, thin Material Symbols, a dark mesh hero with glass controls, a native Home page inside a tab, compact browsing chrome that slides away and respects the system bars, a five-slot bottom bar without Back, a hub sheet replacing the overflow menu, and restyled tabs, libraries, settings, dialogs, incognito page and onboarding.

The technical spine is Constitution v1.4.0 §V: page-load progress leaves `BrowserUiState` for its own flow read only in `drawBehind`; bar sliding, hero collapse and tab swipe are read in `offset {}` / `graphicsLayer {}`; UI models are immutable with a stability configuration; compiler reports sit behind `-PcomposeReports`. The Home page lives in a tab through a reserved address (`thirtysix:home`) carried by the existing default-URL binding, so **Room stays at v2 and no dependency is added** (one is removed: `material-icons-core`).

## Technical Context

**Language/Version**: Kotlin 2.3.21, Java 11 target, AGP 9.1.1, Compose BOM 2026.04.01 (Material3), Compose compiler with strong skipping (default)
**Primary Dependencies**: existing only — Compose, Material3, Navigation Compose, Hilt, Room 2.8.4, DataStore, androidx.webkit 1.17.0, appcompat 1.8.0, core-splashscreen 1.2.0. **Added: none. Removed: `androidx.compose.material:material-icons-core`.**
**Storage**: Room v2 unchanged (two new `SELECT … LIMIT` queries); DataStore unchanged
**Testing**: JUnit + Turbine (JVM), Robolectric where already used, Compose UI tests + Espresso-Web (instrumented), Layout Inspector (manual recomposition counts)
**Target Platform**: Android 7.0 (API 24) to Android 16 (API 36), 16 KB page size
**Project Type**: mobile app (single Android module, Clean Architecture + MVVM)
**Performance Goals**: chrome rebuilt 0× per progress tick; no per-frame rebuild of chrome during scroll, hub drag, tab swipe; p95 frame ≤ 16 ms on Pixel 5-class release builds (SC-005); new tab → Home ≤ 300 ms; cold start ≤ +5% vs Spec 018
**Constraints**: offline-first; no new permission; no new native library; release APK ≤ 3,544,576 B (+400 KB); 8 locales; 360dp minimum width; WCAG contrast in both themes and under dynamic color
**Scale/Scope**: ~100 presentation files touched or replaced; 15 canvas screens plus non-canvas sheets/dialogs; ≈45 icons; ≈30 new string keys × 8 locales; History lists up to 10,000 entries

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Evidence |
|---|---|---|---|
| I | Privacy & Security First | ✅ | Home uses only on-device tabs/bookmarks/history; incognito tabs never feed Home (FR-015); no network, no new permission |
| II | Google Play Compliance | ✅ | Permission set unchanged; no camera/mic (voice/camera search out of scope) |
| III | Code Quality & No-Hardcode | ✅ | All colours in `Color.kt` / `ThirtySixColors.kt`; shapes in `Shape.kt`; limits in `BrowserLimits`; strings in resources; reserved URL in `UrlConstants` |
| IV | Clean Architecture | ✅ | `HomeViewModel` → `ObserveHomeContentUseCase` → repositories; new DAO queries behind repository interfaces; no ViewModel touches a DAO |
| V | Performance + Compose Phase Discipline | ✅ | R7–R11 map each frame-rate value to its phase (contracts C6); progress isolated (R8); stability file + reports (R9); measurement (R10) |
| VI | Testing Discipline | ✅ | JVM tests for progress isolation, `ChromeScrollState`, `GreetingPeriod`, `ObserveHomeContentUseCase`, back-to-Home logic; existing UI tests updated for new visuals only |
| VII | Offline-First | ✅ | No remote data; Home renders from local flows |
| VIII | Localization & Accessibility | ✅ | ≈30 keys × 8 locales, lint parity; 48dp targets; labels on icon-only controls; contrast checked incl. dynamic color |
| IX | Dependency Currency & 16 KB | ✅ | No new dependency; one removed; 16 KB gate unchanged |
| X | Simplicity & Build Order | ✅ | Spec 019 is Phase 5 per v1.4.0; designed and approved before specify; Pin a site deferred to roadmap |
| XI | Build Configuration | ✅ | Reports behind a Gradle property, no new build type or flavour |

**Testing Gates 9–10** (UI specs) are planned: compiler report (R9) and Layout Inspector counts (R10, quickstart G13).

*Post-design re-check (after Phase 1)*: still 11/11. The design adds no stored data, dependency or permission; the one behaviour change outside presentation (Home replaces the home web address, clarified with the owner) is a product decision recorded in the spec, not a principle exception.

## Project Structure

### Documentation (this feature)

```text
specs/019-ui-redesign/
├── plan.md              # This file
├── research.md          # R1–R18
├── data-model.md        # reserved URL, state, Home models, tokens, icons, strings
├── quickstart.md        # gates G1–G15
├── contracts/README.md  # C1–C7
├── assets/              # approved mark SVG + tokens.json copied from the design system
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
compose_stability.conf                       # NEW — stability configuration (R9)
app/build.gradle.kts                         # composeCompiler { stability file; reports behind -PcomposeReports }; drop icons-core
gradle/libs.versions.toml                    # remove material-icons-core

app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/constants/
│   ├── UrlConstants.kt                      # + HOME_PAGE_URL; DEFAULT_HOME_URL removed when unused
│   └── BrowserLimits.kt                     # + HOME_SHORTCUT_LIMIT (8), HOME_RECENT_LIMIT (5), TAB_SWIPE_CLOSE_FRACTION
├── di/
│   ├── UrlConfigModule.kt                   # default_home_url → HOME_PAGE_URL
│   └── ClockModule.kt                       # NEW — provides java.time.Clock
├── data/local/dao/{BookmarkDao,HistoryDao}.kt      # + observeRecent(limit)
├── data/repository/{Bookmark,History}RepositoryImpl.kt
├── domain/
│   ├── repository/{Bookmark,History}Repository.kt  # + observeRecent
│   ├── model/{HomeContent,HomeShortcut,GreetingPeriod}.kt   # NEW
│   └── usecase/ObserveHomeContentUseCase.kt        # NEW
└── presentation/
    ├── theme/      Color.kt · ThirtySixColors.kt (NEW) · Shape.kt · Type.kt · Theme.kt · Elevation.kt (NEW)
    ├── components/ NEW shared: GlassIconButton, PillButton, Chip, QuickTile, FeatureBanner,
    │               SectionHeader, ListRow, ProgressLine, ConfirmDialog, ChoiceDialog, SheetFrame
    ├── browser/    BrowserScreen (rewritten), BrowserViewModel (+loadProgress, home flags),
    │               ChromeScrollState (NEW), components/{BrowserTopBar, BrowserBottomBar, HubSheet,
    │               PageLoadProgressLine} (NEW), BrowserWebView (+scroll callback);
    │               NavigationBottomBar*, BrowserOverflowMenu, BrowserLoadingIndicator → deleted
    ├── home/       HomePage, HomeHero, ShortcutCard, HomeViewModel, HomeUiState (NEW)
    ├── incognito/  IncognitoPage (NEW)
    ├── tabs/       TabsScreen + TabCard (swipe), chips, create card (rewritten)
    ├── history/ · bookmarks/ · downloads/ · settings/   screens and rows restyled on shared components
    └── onboarding/ OnboardingScreen + slides restyled on the hero

app/src/main/res/
├── drawable/       ic_*.xml (≈45 Material Symbols), ic_brand_mark*.xml, ic_launcher_*.xml (regenerated)
├── drawable-v31/   ic_brand_mark.xml (animated, regenerated)
├── mipmap-*/       ic_launcher.webp × 5 (regenerated)
├── values/colors.xml, values*/themes.xml      # window/launch backgrounds to new surface tokens
└── values*/strings.xml                         # ≈30 new keys × 8

app/src/test/…  BrowserViewModelProgressIsolationTest, ChromeScrollStateTest, GreetingPeriodTest,
                ObserveHomeContentUseCaseTest, BrowserViewModelHomeNavigationTest
app/src/androidTest/…  existing UI tests updated to new test tags; HomePageTest, HubSheetTest, TabSwipeTest
```

**Structure Decision**: Single existing Android module; new code follows the established `core / data / domain / presentation / di` layout. Shared visual components move to `presentation/components/` so each feature screen is assembled from the design system's components rather than re-styling Material defaults screen by screen.

## Complexity Tracking

No Constitution violations to justify.

Noted risk (not a violation): **Home-inside-a-tab back handling** (R1) adds two in-memory flags to `BrowserUiState` and one branch in the back handler. It is covered by `BrowserViewModelHomeNavigationTest` and device gate G5; the alternative (a schema column or a separate route) was rejected in research R1.
