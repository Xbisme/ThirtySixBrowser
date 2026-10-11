# Implementation Plan: Page Tools

**Branch**: `022-page-tools` | **Date**: 2026-10-11 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/022-page-tools/spec.md`

## Summary

This spec adds five page tools to the hub — **Find in page**, **Share**, **Desktop site**, **Text size** and **Print** — and **offline address suggestions** from bookmarks and history. Everything is built on the system WebView and Android's own share and print screens. No dependency is added, no permission is added, and Room stays at v3. The only persisted change is one DataStore key, `text_zoom_percent`.

How each part is built:

- **Desktop mode** is per tab and in memory only: a set of tab ids in `BrowserViewModel`, because the WebView is rebuilt on every tab switch (research R1).
- **Find in page** drives `findAllAsync` through new `WebViewActionsHandle` actions. Its state is a separate `StateFlow`, so per-keystroke results never recompose the browser chrome (R3, §V rule 3).
- **Suggestions** come from a snapshot loaded once per editing session and filtered in memory with literal, Unicode case-insensitive matching. There is no SQL `LIKE` (R5).

## Technical Context

**Language/Version**: Kotlin 2.3.21, Java 11 target, AGP 9.1.1
**Primary Dependencies**:
- Existing only: Jetpack Compose (BOM 2026.04.01) / Material3, Hilt 2.59.2, Room 2.8.4, DataStore 1.2.1;
- `androidx.webkit` 1.17.0, already present, used for UA metadata (R2);
- `android.webkit.WebView`, `android.print.PrintManager`, `Intent.ACTION_SEND`.

**Storage**: DataStore Preferences gains one key (`text_zoom_percent`). Room is unchanged at **v3**: two new read-only DAO queries, no schema change.
**Testing**: JUnit + Turbine + Robolectric (unit), Compose UI test + Espresso-Web (instrumented), MockWebServer/local fixture pages.
**Target Platform**: Android 7.0 (API 24) – Android 16 (API 36); 16 KB AVD for gates.
**Project Type**: mobile-app (single `app` module, Clean Architecture + MVVM).
**Performance Goals**:
- SC-002: find 50 matches within 1 s.
- SC-003: suggestions within 200 ms per keystroke at 10,000 history rows.
- SC-005: text size applied within 1 s with 0 reloads.
- §V: no per-keystroke recomposition of the chrome.

**Constraints**:
- No network on the suggestion path (FR-015).
- No new permission, no schema change (FR-037).
- 8 locales.
- APK ≤ +100 KB (SC-009).
- Zero new `.so`.

**Scale/Scope**:
- 6 user stories, 37 FRs.
- About 14 new production files and about 16 modified.
- About 20 new string keys × 8 locales.

## Constitution Check

*GATE: checked before Phase 0 and re-checked after Phase 1.*

| Principle | Status | How |
|---|---|---|
| I Privacy & Security | ✅ | Suggestions read local data only; nothing typed is sent or stored (FR-015, FR-022, SC-004 gate G7). Share and Print are explicit user actions and the app records neither. Desktop mode stores nothing. |
| II Play Compliance | ✅ | No new permission. No `addJavascriptInterface`. No injected script: find uses the WebView's native search. |
| III Code Quality / No-Hardcode | ✅ | The text-size levels, suggestion limit and find-text limit live in `BrowserLimits`. The new key is in `StorageKeys`. Desktop UA tokens are named constants in `DesktopUserAgent`. All text is in string resources. |
| IV Clean Architecture | ✅ | The WebView stays in Compose (`WebViewActionsHandle`). Repositories gain only one-shot reads; combining bookmarks and history happens in a use case (no repository → repository dependency). `TextSize` and `AddressSuggestion` are pure domain models. |
| V Performance / Compose Phase Discipline | ✅ | Find state and suggestions are separate `StateFlow`s, each collected by one consumer (rule 3). The suggestion `LazyColumn` has a `key` and `contentType` (rule 6). The find bar and suggestion list consume insets (rule 7). `LaunchedEffect(textZoomPercent)` is keyed honestly (rule 8). Compose report and Layout Inspector counts are gates (rules 5 and 9, G17). |
| VI Testing | ✅ | Unit tests cover the ViewModel state machines, the use cases, the DataStore key and the pure helpers. Instrumented tests cover find, UA, text zoom, the suggestion list and the two DAO queries (research R11). |
| VII Offline-First | ✅ | Every tool works offline except loading a page. Suggestions are purely local. |
| VIII Localization & A11y | ✅ | 8 locales. The find count is a live region. The desktop tile has toggle semantics. Suggestions are read with title and address. |
| IX Dependencies & 16 KB | ✅ | No new dependency. `androidx.webkit` 1.17.0 is already verified with zero `.so`. |
| X Simplicity & Build Order | ✅ with waiver | Phase 6, in order: 020 ✅ → 021 ✅ → **022**. **"Design before spec" is waived by the owner (2026-10-11)**, as for 020 and 021; the UI uses Spec 019 components. Out-of-scope ideas are listed in the spec, not built. |
| XI Build Config | ✅ | No change. |

**Result: 11/11 PASS** (X with the owner's recorded design waiver). The post-design re-check below is unchanged.

## Project Structure

### Documentation (this feature)

```text
specs/022-page-tools/
├── spec.md
├── plan.md              # this file
├── research.md          # R1–R11
├── data-model.md        # TextSize, desktop set, FindInPageState, suggestions, helpers
├── quickstart.md        # automated gates + G1–G17
├── contracts/PageToolsContracts.kt
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

New files are marked **+**, modified files **~**. The root is `app/src/main/kotlin/com/raumanian/thirtysix/browser/`.

```text
core/constants/
  ~ BrowserLimits.kt          # TEXT_SIZE_PERCENT_* (9), MAX_ADDRESS_SUGGESTIONS = 6, MAX_FIND_QUERY_LENGTH = 200
  ~ StorageKeys.kt            # TEXT_ZOOM_PERCENT
  ~ AppDefaults.kt            # TEXT_SIZE = TextSize.Percent100
domain/model/
  + TextSize.kt
  + AddressSuggestion.kt      # AddressSuggestion, SuggestionSource, AddressSuggestionSnapshot
  ~ UserSettings.kt           # + textSize
domain/repository/
  ~ SettingsRepository.kt     # + setTextSize
  ~ BookmarkRepository.kt     # + getAllBookmarks
  ~ HistoryRepository.kt      # + getLatestVisitPerUrl
domain/usecase/
  + SetTextSizeUseCase.kt
  + LoadAddressSuggestionsSourceUseCase.kt
  + FilterAddressSuggestionsUseCase.kt
data/local/dao/
  ~ BookmarkDao.kt            # + getAllNewestFirst()
  ~ HistoryDao.kt             # + getLatestVisitPerUrl()
data/local/datastore/, data/mapper/, data/repository/
  ~ SettingsDataStore / SettingsMapper / SettingsRepositoryImpl   # text_zoom_percent
  ~ BookmarkRepositoryImpl.kt, HistoryRepositoryImpl.kt
presentation/browser/
  ~ BrowserViewModel.kt       # find state flow, desktop set, textZoomPercent
  ~ BrowserUiState.kt         # + isDesktopMode
  ~ WebViewActionsHandle.kt   # + findAll, findNext, clearMatches, setDesktopMode, print
  ~ BrowserWebView.kt         # FindListener, desktop config before first load, textZoom, print wiring
  ~ BrowserWebViewCallbacks.kt # + onFindResult
  ~ BrowserScreen.kt          # find bar swap, bottom-bar hide, find BackHandler, suggestions overlay, share
  ~ QuickSettingsHost.kt      # + QuickSetting.TextSize
  + PageTools.kt              # PageTool, PageToolsAvailability, ShareContent, PrintJobName
  + DesktopUserAgent.kt
  + FindInPageState.kt
  components/
    + FindInPageBar.kt
    ~ HubSheet.kt, HubCallbacks.kt   # page-tools section (R9)
  suggestions/
    + AddressSuggestionsViewModel.kt
    + AddressSuggestionsUiState.kt
    + AddressSuggestionList.kt
presentation/home/
  ~ HomeHero.kt               # HomeSearchBar reports text + focus (R6)
  ~ HomePage.kt               # suggestion list under the search field
presentation/settings/
  ~ SettingsUiState.kt        # SettingsDialog.TextSizeChooser, textSize
  ~ SettingsViewModel.kt      # onTextSizeRowClick / onTextSizeChosen
  ~ SettingsScreen.kt + components/SettingsRows.kt
  + components/TextSizeChooserDialog.kt
presentation/components/
  ~ QuickTile.kt              # selected, value (defaulted)
res/values*/strings.xml       # ~20 keys × 8 locales
res/drawable/                 # + Material Symbols vectors: find, share, desktop, text size, print (via AppIcons)

app/src/test/…                # unit tests per research R11
app/src/androidTest/…         # find, UA echo, text zoom, suggestion list, DAO queries
specs/022-page-tools/fixtures/ # find.html, ua.html (local test pages)
```

**Structure Decision**: The existing single-module Clean Architecture layout is used. New presentation code sits beside the browser screen. Address suggestions get their own `suggestions/` package and ViewModel so `BrowserViewModel` does not grow further. The domain stays free of Android imports.

## Post-Design Constitution Re-check

After Phase 1 (data-model, contracts, quickstart): **11/11 PASS, unchanged.**
- The design adds one DataStore key and two read-only DAO queries; neither touches the schema.
- The only cross-repository need, bookmarks plus history, is met in a use case.
- Two flagged risks:
  - **Desktop UA metadata (R2)**: the API name must be confirmed against `androidx.webkit` 1.17.0 at implementation. The fallback (UA string + viewport) is already defined.
  - **Spec 013 bookmark search uses unescaped `LIKE`** (found in R5): pre-existing, outside this spec's scope, recorded as carry-forward debt.

## Complexity Tracking

No constitution violation needs justifying. The §X design waiver is an owner decision recorded in the spec's Assumptions, as for Specs 020 and 021.
