# Implementation Plan: Swipe the Address Bar to Switch Tabs

**Branch**: `030-omnibar-tab-swipe` | **Date**: 2026-10-17 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/030-omnibar-tab-swipe/spec.md`

## Summary

A sideways drag on the address pill, or on the Home search field in its place, slides the current tab away and brings in the next or previous tab of the same kind.

- **Order** (R1): a pure `TabOrder` sorts by `position`, the opening order that is already persisted. The Tabs screen shows the same order (R6). The repositories keep "the newest `lastActiveAt` is active".
- **Close** (R2): closing the active tab hands its `lastActiveAt` to the tab beside it, so that tab becomes active.
- **Gesture** (R3): a `Modifier.tabSwipe` reads the Initial pass. It claims only drags that are clearly sideways while the field is unfocused, so taps still focus the field. The decisions are in a pure, tested `TabSwipeTracker`.
- **Visuals** (R4): the pill lane and the page area move by an offset read only in `graphicsLayer`. The neighbour is drawn from its screenshot, its mesh, the Home look or the incognito surface. The preview holds until the new tab's first frame.
- **Plumbing** (R5): `ObserveTabNeighboursUseCase` feeds `TabSwipeViewModel`, and the switch reuses `SwitchToTabUseCase`.
- **Access and settings** (R7, R8): two accessibility actions on the pill, a reduced-motion helper, and a Settings switch.
- No schema change and no new dependency.

## Technical Context

**Language/Version**: Kotlin 2.3.21, AGP 9.1.1 (Android Studio JBR)
**Primary Dependencies**: Compose BOM 2026.04.01, which provides `pointerInput`, `VelocityTracker` and `Animatable`; Hilt 2.59.2 (KSP); Room 2.8.4; DataStore. No new dependency.
**Storage**: Room v6 unchanged, using `tabs.position`. One new DataStore boolean.
**Testing**: JUnit 4, coroutines-test, Turbine, Robolectric, in-memory Room, Compose UI (`performTouchInput { swipeLeft() }`, `SemanticsActions.CustomActions`), Konsist.
**Target Platform**: Android 7.0–16, 16 KB pages
**Project Type**: single-module Android app
**Performance Goals**:
- a drag recomposes nothing per frame;
- ≤ 1 % janky frames during swipes on the A55 release build;
- cold start within 5 %.

**Constraints**:
- normal and incognito tabs are never crossed;
- no incognito image or title is shown or spoken;
- taps on the pill behave exactly as before;
- APK ≤ +40 KB;
- 8 locales.

**Scale/Scope**: at most `BrowserLimits.MAX_TABS` normal tabs and `MAX_INCOGNITO_TABS` incognito tabs. Files touched:

- **core**: `TabOrder`, `TabNeighbours`, the `Tab` KDoc, the `TabRepository` KDoc, the settings model and keys, and `rememberAnimationsEnabled`;
- **feature/tabs**: both repositories' `closeTab`, the incognito position counter, and the `TabsViewModel` sort with a scroll to the active card;
- **feature/browser**: the use case, the holder, the tracker, the modifier, the lane, the preview, and the screen wiring;
- **feature/settings**: one row.

## Constitution Check

*Gated against constitution **2.1.0**.*

| Principle | Status | Note |
|---|---|---|
| I Feature-first | ✅ | `TabOrder` and `TabNeighbours` go in `core/domain`, because both the tabs and browser features use them. The browser never imports `feature/tabs`. The preview uses `SeededMeshArt` and `rememberFileImage`, which are already in `core/presentation`. |
| II One home | ✅ | Placement:<br>• `ObserveTabNeighboursUseCase` in `feature/browser/domain/usecase`;<br>• `TabSwipeViewModel` with its `UiState`, `Action` and `Event` in `feature/browser/presentation/browser/`;<br>• the limits in a named object. |
| III Unidirectional flow | ✅ | The holder takes `onAction(TabSwipeAction)` and emits `TabSwipeEvent.Announce`. The switch calls the existing `SwitchToTabUseCase`. Neighbours come through a use case, since it combines two sources and applies the ordering. Dispatchers are injected. |
| IV Compose | ✅ | `offsetPx` is read only in `graphicsLayer`, and composition reads `incoming` once per drag. Effect keys are the active tab id and `incoming`. The new composables take `@Immutable TabPreview`. Attached: the Compose report, and a Layout Inspector count for one drag (or DEFERRED with a reason, as in 028). |
| V No hardcoded values | ✅ | `TabSwipeLimits` holds the fraction, velocity, resistance, ratio and hold time. The strings are in all 8 locales. |
| VI Comments | ✅ | Only the Initial-pass claim and the inherited `lastActiveAt` need a "why". |
| VII Build and data | ✅ | No migration and no dependency. R8: no reflection. |
| VIII Tests | ✅ | Unit tests for every new type and changed repository; an instrumented `BrowserTabSwipeTest`; an update to the existing Tabs order tests. |
| Data and privacy | ✅ | Nothing new is stored about incognito. The incognito position counter is in memory. No network. |
| Permissions | ✅ | None. |
| Performance budgets | ✅ | Frame timing on the A55 release build (not an emulator, ADR 0017); cold start interleaved on the AVD. |

**Post-design re-check**: no exemption and no allowlist entry. **Gate: PASS.**

**ADR**: `docs/adr/0030-tabs-in-opening-order.md` records the decision. Tabs are shown and swiped in opening order, while "active" stays the most recent. Closing hands the active mark to the neighbour.

## Project Structure

### Documentation

```text
specs/030-omnibar-tab-swipe/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/tab-swipe.md
├── checklists/requirements.md
└── tasks.md                                    # /speckit-tasks
```

### Source Code

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/
│   ├── domain/model/TabNeighbours.kt                                   new
│   ├── domain/model/Tab.kt, UserSettings.kt                            KDoc; + isTabSwipeEnabled
│   ├── domain/repository/TabRepository.kt, SettingsRepository.kt       closeTab KDoc; + setTabSwipeEnabled
│   ├── domain/util/TabOrder.kt, AppDefaults.kt                         new; + TAB_SWIPE_ENABLED
│   ├── data/datasource/local/datastore/StorageKeys.kt, SettingsDataStore.kt   + key, read/write
│   └── presentation/util/AnimationsEnabled.kt                          new
├── feature/tabs/
│   ├── data/repository/TabRepositoryImpl.kt                            closeTab heir
│   ├── data/repository/IncognitoTabRepositoryImpl.kt                   closeTab heir, position counter
│   └── presentation/tabs/TabsViewModel.kt, TabsScreen.kt               opening order, scroll to active
├── feature/browser/
│   ├── domain/usecase/ObserveTabNeighboursUseCase.kt                   new
│   └── presentation/browser/
│       ├── TabSwipeViewModel.kt, TabSwipeUiState.kt (+ TabPreview),
│       │   TabSwipeAction.kt, TabSwipeEvent.kt, TabSwipeLimits.kt      new
│       ├── TabSwipeTracker.kt, TabSwipeState.kt, TabSwipeModifier.kt   new
│       ├── component/TabSwipeLane.kt, component/TabSwipePreview.kt     new
│       ├── component/BrowserTopBar.kt, AddressBar.kt                   lane around the pill, custom actions
│       ├── BrowserContent.kt, BrowserScreen.kt, BrowserRoute.kt,
│       │   BrowserScreenInputs.kt                                      page offset, preview, holder wiring
│       └── ../home/HomeHero.kt                                         HomeSearchBar takes the modifier and actions
├── feature/settings/presentation/settings/
│   ├── SettingsAction.kt, SettingsViewModel.kt, SettingsScreen.kt      + TabSwipeToggled, row
app/src/main/res/values*/strings.xml                                    + 7 strings × 8
docs/adr/0030-tabs-in-opening-order.md                                  new
```

**Structure Decision**: the existing feature-first layout. The ordering is a core rule, because the Tabs screen and the browser both need it.

## Step plan (one commit each, G1 green)

| Step | Content | Gate |
|---|---|---|
| 0 | Baseline on `8e5a0ca`: unit and instrumented counts, release APK size | — |
| 1 | `TabOrder` and `TabNeighbours`, with tests; the `Tab` KDoc; ADR 0030 | G1 |
| 2 | Repository close heir and incognito position counter, with tests (in-memory Room and the incognito repo) | G1 |
| 3 | `TabsViewModel` opening order and `TabsScreen` scroll to active; update the Tabs tests | G1 |
| 4 | Setting end to end: key, default, model, repository, DataStore, Settings row, strings × 8, tests | G1 |
| 5 | `ObserveTabNeighboursUseCase` and `TabSwipeViewModel`, with tests | G1 |
| 6 | `TabSwipeTracker` and `TabSwipeLimits`, with tests | G1 |
| 7 | `rememberAnimationsEnabled`, `TabSwipeState`, `Modifier.tabSwipe`, the lane, the preview and the screen wiring; Home search field | G1 + AVD |
| 8 | Accessibility actions and announcements; strings × 8 | G1 + AVD |
| 9 | Instrumented `BrowserTabSwipeTest`; Compose report | G1 + G4 |
| 10 | Device walk G3, measurements G5, PR | G3–G5 |

## Risks

| Risk | Mitigation |
|---|---|
| The Initial-pass claim swallows a tap that drifted a few pixels | The claim needs the full touch slop and the 2:1 sideways ratio. G3 does 20 taps and a tracker test drifts under slop. |
| The text field's own drag (scrolling a long host) fights the swipe | The field is unfocused, so it shows only the host, which fits. Focused fields never swipe. |
| A blank WebView flashes after the slide | The preview holds until the first frame, at most 600 ms (R4). If G3 still sees a flash, raise the cap. |
| A user expects the Tabs screen's old most-recent-first order | This is the owner's decision (clarification). Scrolling to the active card keeps it easy to find. |
| The neighbour changes because a page opens a tab mid-drag | The drag compares the neighbour id it started with and cancels on a change (R5). |
| Below API 26 there is no `ValueAnimator.areAnimatorsEnabled()` | Read `Settings.Global.ANIMATOR_DURATION_SCALE` there. |

## Complexity Tracking

No constitution violation to justify.
