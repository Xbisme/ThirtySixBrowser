# Implementation Plan: History View

**Branch**: `014-history-view` | **Date**: 2026-05-08 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/014-history-view/spec.md`

## Summary

Add a user-facing history screen plus the auto-recording pipeline that feeds it. Successful page loads in **non-incognito** tabs are recorded into the existing `HistoryEntryEntity` (Spec 005) by hooking `BrowserViewModel`'s page-finish path; incognito loads are silently dropped. A new `HistoryScreen` reachable from the bottom-bar lists entries reverse-chronologically, grouped under "Today" / "Yesterday" / explicit-date headers, with one row per visit. Search filters live (≥2 chars) on title-or-URL substring. Long-press opens an action sheet (Open in new tab — always normal · Delete entry · Copy URL); clear-all wipes the database after a confirm dialog. Zero new packages: Room/Compose/Material-icons-core/Coroutines/Flow/system clipboard service cover everything. Schema unchanged — current entity already has `id/url/title/visited_at`; favicon rendering reuses Spec 011's hostname-keyed `FaviconCache`.

## Technical Context

**Language/Version**: Kotlin 2.3.21 (per `gradle/libs.versions.toml`, set at Spec 001)
**Primary Dependencies**: Compose BOM 2026.04.01, Material3, material-icons-core, Hilt 2.59.2, KSP 2.3.7, Room 2.8.4, kotlinx-coroutines/Flow, DataStore Preferences 1.2.1 — **no new dependency added**
**Storage**: Existing Room DB (`thirtysix_browser.db`) — no schema migration; reuses `HistoryEntryEntity` shipped in Spec 005
**Testing**: JUnit 4 + Robolectric 4.16.1 + Turbine 1.2.1 (data-layer/domain unit tests, JVM); Espresso-Web 3.7.0 + Hilt-android-testing + Compose UI Test for instrumented tests
**Target Platform**: Android 7.0 (minSdk 24) → Android 16 (targetSdk 36, compileSdk 36 with `minorApiLevel = 1`)
**Project Type**: Mobile-app (single Android module under `app/`)
**Performance Goals**: SC-005 — frame budget ≤ 16 ms p99 during initial layout on a Pixel 5-class device with 10K rows; SC-006 — clear-all completes in ≤ 1 s on the same case
**Constraints**: SC-008 APK delta ≤ +200 KB; SC-009 every native lib `align 0x4000` (Constitution §IX); zero new packages preferred (A11)
**Scale/Scope**: Spec 005 envelope = 100K history rows worst-case; SC-005 measurable target = 10K rows; v1.0 ships single screen + recorder hook

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.2.0 — **11/11 PASS pre-implementation**.

| # | Principle | Status | Evidence |
|---|-----------|--------|----------|
| I | Privacy & Security First | ✅ PASS | FR-002 ensures incognito → no recording (matches §I incognito invariant); zero analytics added; data stays on-device in existing Room DB; no new permission |
| II | Google Play Compliance | ✅ PASS | No new manifest permission; `WebView` API surface unchanged; clipboard via `ClipboardManager` (allowed) |
| III | Code Quality & Safety / No-Hardcode | ✅ PASS | All strings via `stringResource(R.string.*)`; new constants for `SEARCH_MIN_CHARS`, `MAX_HISTORY_QUERY_LENGTH` go in `core/constants/BrowserLimits.kt`; date formats in `core/constants/DateFormats.kt`; magic numbers banned by Detekt baseline |
| IV | Clean Architecture (MVVM) | ✅ PASS | Strict layering: Compose → ViewModel → UseCase → Repository interface → RepositoryImpl → DAO; ViewModels never access `HistoryDao` directly; Repository depends only on DAO + dispatcher (no Repo→Repo); single feature ViewModel per screen |
| V | Performance Excellence | ✅ PASS | SC-005 60 fps target with 10K rows; LazyColumn + `key { entry.id }` keeps recomposition bounded; client-side filter is O(N) per keystroke and well within 16 ms for the SC-005 envelope (R3 below) |
| VI | Testing Discipline | ✅ PASS | 6 unit-test files planned (mapper, DAO surface, repo impl, 4 use cases, ViewModel — Robolectric JVM); 4 instrumented files (recorder integration, screen browse, search, clear-all) |
| VII | Offline-First Architecture | ✅ PASS | Room atomic writes per `insert`/`deleteAll`; no cloud; DB excluded from Auto Backup (Spec 005 default carries over) |
| VIII | Localization & Accessibility | ✅ PASS | All 8 locales (FR-028); `contentDescription` on every interactive element (FR-030); date/time follow locale (FR-029) via `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` + `ofLocalizedTime(FormatStyle.SHORT)` |
| IX | Dependency Currency / 16KB | ✅ PASS | **Zero new dependencies**; SC-009 enforces 16KB CI gate stays green; no new `.so` because the existing classpath suffices |
| X | Simplicity & Build Order | ✅ PASS | Depends only on Specs already shipped (005/007/011/012/013); no future-spec dependencies; YAGNI honored — no pagination, no retention, no per-site grouping for v1.0 |
| XI | Build Configuration | ✅ PASS | No signing-config changes; debug + release only; `targetSdk = 36` unchanged |

No deviations to track.

## Project Structure

### Documentation (this feature)

```text
specs/014-history-view/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 — 6 R-items, all "no NEEDS CLARIFICATION" remaining
├── data-model.md        # Phase 1 — entity + domain-model shapes; no DB migration
├── quickstart.md        # Phase 1 — 7 manual user-device gates G1–G7
├── spec.md              # Spec output of /speckit-specify + clarifications session
├── checklists/
│   └── requirements.md  # Spec quality checklist (Phase pre-plan)
├── contracts/
│   └── HistoryRepository.kt   # Phase 1 — repository interface contract
└── tasks.md             # Phase 2 output of /speckit-tasks (NOT created here)
```

### Source Code (repository root — Android single-module under `app/`)

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/constants/
│   ├── BrowserLimits.kt                             # +SEARCH_MIN_CHARS, MAX_HISTORY_QUERY_LENGTH
│   └── DateFormats.kt                               # +HISTORY_DAY_HEADER pattern hint (locale-aware)
├── data/
│   ├── local/dao/HistoryDao.kt                      # +deleteById(id: Long): Int (additive)
│   ├── mapper/HistoryEntryMapper.kt                 # NEW — Entity ↔ HistoryEntry
│   └── repository/HistoryRepositoryImpl.kt          # NEW
├── domain/
│   ├── model/
│   │   ├── HistoryEntry.kt                          # NEW — pure Kotlin
│   │   └── HistoryDayBucket.kt                      # NEW — sealed: Today | Yesterday | OnDate(LocalDate)
│   ├── repository/HistoryRepository.kt              # NEW interface
│   └── usecase/
│       ├── RecordHistoryEntryUseCase.kt             # NEW — invoked by BrowserViewModel; suppresses incognito
│       ├── ObserveHistoryEntriesUseCase.kt          # NEW — Flow<List<HistoryEntry>>
│       ├── DeleteHistoryEntryUseCase.kt             # NEW — single-row delete by id
│       └── ClearAllHistoryUseCase.kt                # NEW — DELETE FROM history_entries
├── di/HistoryModule.kt                              # NEW — @Binds HistoryRepository
├── presentation/
│   ├── browser/
│   │   ├── BrowserViewModel.kt                      # MODIFIED — injects RecordHistoryEntryUseCase, hooks onPageLoaded
│   │   └── BrowserNavigationCallbacks.kt            # MODIFIED — +onPageLoaded(url, title) field if not present
│   ├── history/
│   │   ├── HistoryScreen.kt                         # REWRITTEN
│   │   ├── HistoryViewModel.kt                      # NEW
│   │   ├── HistoryUiState.kt                        # NEW
│   │   ├── HistoryErrorEvent.kt                     # NEW — sealed: ClipboardCopied | TabCapReached | DeletionFailed
│   │   └── components/
│   │       ├── HistoryTopBar.kt                     # NEW — search field + clear-all icon (visible iff entries>0)
│   │       ├── HistoryDayHeader.kt                  # NEW — section header
│   │       ├── HistoryRow.kt                        # NEW — favicon + title + hostname + time
│   │       ├── HistoryActionSheet.kt                # NEW — long-press 3-action bottom sheet
│   │       ├── ClearAllHistoryConfirmDialog.kt      # NEW
│   │       ├── EmptyHistoryState.kt                 # NEW
│   │       └── NoHistoryMatchesState.kt             # NEW
│   ├── tabs/                                        # UNCHANGED (CreateTabUseCase reused as-is)
│   ├── navigation/AppNavGraph.kt                    # MODIFIED — passes HistoryViewModel + nav controller into HistoryScreen
│   └── browser/components/NavigationBottomBar.kt    # MODIFIED — +6th icon button → onHistoryClick
└── presentation/browser/components/NavigationBottomBarCallbacks.kt # MODIFIED — +onHistoryClick

app/src/main/res/values{,-vi,-de,-ru,-ko,-ja,-zh,-fr}/strings.xml
                                                     # +25 keys × 8 locales ≈ 200 entries

app/src/test/kotlin/com/raumanian/thirtysix/browser/
├── data/local/dao/HistoryDaoSpec014Test.kt          # NEW — covers new deleteById + chronological invariant under repeat-visit
├── data/mapper/HistoryEntryMapperTest.kt            # NEW
├── data/repository/HistoryRepositoryImplTest.kt     # NEW — uses Robolectric in-memory Room
├── domain/usecase/{Record,Observe,Delete,ClearAll}HistoryUseCaseTest.kt   # NEW — 4 files
└── presentation/history/HistoryViewModelTest.kt     # NEW — uses MainDispatcherRule + Turbine

app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/
├── presentation/history/HistoryScreenBrowseTest.kt          # NEW — instrumented (Hilt + Compose UI Test)
├── presentation/history/HistoryScreenSearchTest.kt          # NEW
├── presentation/history/HistoryScreenLongPressTest.kt       # NEW
└── presentation/history/HistoryRecorderIntegrationTest.kt   # NEW — page load → DB row appears
```

**Structure Decision**: Mirror Spec 013's layout 1:1 (single feature folder under `presentation/history/` with `components/` subfolder). The data + domain slices follow the project's standard Clean Architecture pattern. No new top-level directories.

## Phase 0 — Research

Detailed in [research.md](research.md). Six R-items, none of which carry NEEDS CLARIFICATION (the spec's clarification session resolved all decision-shaping questions ahead of plan).

| R# | Topic | Decision (preview) |
|----|-------|--------------------|
| R1 | Where does the recorder hook live? | `BrowserViewModel.onPageLoaded(url, title)` — gated by `currentTab.isIncognito == false`. Reuses existing `BrowserNavigationCallbacks` plumbing from Spec 011. |
| R2 | Day-bucketing implementation | `LocalDate.now(ZoneId.systemDefault())` at view-time + `Instant.ofEpochMilli(visitedAt)` per row → bucket into `HistoryDayBucket`. Pure Kotlin, no extra lib. |
| R3 | Client-side filter perf at 10K rows | Substring scan at 10K × ~80 char average completes in <2 ms on Pixel 5 in benchmarks; well within the 16 ms frame budget. No DAO-side query needed v1.0 (deferred safety net documented). |
| R4 | Favicon rendering without schema migration | Reuse existing hostname-keyed `FaviconCache` (Spec 011) — UI extracts hostname from `entry.url`, looks up cached PNG. Generic globe placeholder fallback. |
| R5 | Tab-cap surfacing for "Open in new tab" | Reuse existing tab-cap error from Spec 011's `CreateTabUseCase`; `HistoryViewModel` catches the existing exception type, emits `HistoryErrorEvent.TabCapReached`. No new error type added. |
| R6 | "Tap = replace active tab" wiring (matches Spec 013 Q2) | Reuse `UpdateActiveTabUrlAndTitleUseCase` + signal pop-back via `HistoryUiState.openUrl: String?` — exactly the pattern Spec 013 `BookmarksViewModel.onBookmarkTap` already uses. |

## Phase 1 — Design & Contracts

Detailed in [data-model.md](data-model.md), [contracts/HistoryRepository.kt](contracts/HistoryRepository.kt), [quickstart.md](quickstart.md).

### Entities

- **`HistoryEntry`** (domain): `id: Long`, `url: String`, `title: String`, `visitedAt: Long` — pure Kotlin, no Android imports. Mirror of `HistoryEntryEntity` (1:1 mapper).
- **`HistoryDayBucket`** (domain, sealed): `Today` | `Yesterday` | `OnDate(date: LocalDate)` — UI maps to localized header string at render time; the bucket is locale-agnostic so the test surface stays simple.
- **`HistoryUiState`** (presentation, immutable `data class`): `entries: List<HistoryEntry>`, `searchQuery: String`, `groupedEntries: List<DayGroup>` (computed once per (entries, query) tuple), `isClearAllDialogVisible: Boolean`, `pendingActionSheetTarget: HistoryEntry?`, `openUrl: String?` (consumed-once signal mirroring Spec 013 pattern).
- **`HistoryErrorEvent`** (presentation, sealed): `ClipboardCopied` | `TabCapReached` | `DeletionFailed(throwable: Throwable)` — emitted via a dedicated `historySnackbarEvent: SharedFlow<HistoryErrorEvent>` channel (mirror Spec 013's `bookmarkSnackbarEvent`).

### Contracts

`contracts/HistoryRepository.kt` defines the 5-method interface (`recordVisit`, `observeAll`, `deleteById`, `clearAll`, `count`) — see file for full signatures.

### Quickstart (manual user-device gates)

`quickstart.md` defines 8 gates G1–G8:
- **G1**: Auto-recording — visit 3 URLs in normal tab → all 3 appear; visit 3 URLs in incognito → none appear (negative gate for FR-002); + Q1 incognito-context visibility check (open History from incognito tab → list still visible).
- **G2**: Day-grouping — set device clock to mock yesterday, visit URLs, restore clock, visit more → "Today" + "Yesterday" headers correct.
- **G3**: Tap-to-replace-active-tab — tap a row → active tab navigates to that URL, History dismisses.
- **G4**: Search — type ≥2 chars filters live; clear → restored. 1-char does NOT filter (proves FR-011a).
- **G5**: Long-press action sheet — Open in new tab (always normal, even from incognito context — proves Q2), Delete entry (single-row), Copy URL (clipboard contains exact URL).
- **G6**: Clear all — confirm dialog → wiped → empty state visible.
- **G7**: 8-locale visual sweep + TalkBack content descriptions present.
- **G8**: 10K-row performance benchmark (SC-005 + SC-006) — debug-only seeder bulk-inserts 10K rows; verify scroll jank-free + clear-all ≤ 1 s.

### Agent context update

The `<!-- SPECKIT START -->` / `<!-- SPECKIT END -->` block in [CLAUDE.md](../../CLAUDE.md) gets the new "Active Spec" section pointing at this plan and listing the 4 clarifications (Q1–Q4) plus the Constitution 11/11 PASS line.

## Constitution Check (Post-Design Re-Check)

After data-model + contracts + quickstart drafted: **11/11 still PASS**. No new deviations introduced by the contract-level decisions:

- The single-method per use-case pattern is preserved (no Repo→Repo dependency anywhere; Repository depends only on `HistoryDao` and the io dispatcher).
- The recorder hook is in `RecordHistoryEntryUseCase`, not in `BrowserViewModel`'s body — VM only calls the use case after the existing page-finish callback and passes the active tab's `isIncognito` flag.
- Zero new dependency means SC-008 / SC-009 carry no new risk.
- All hardcoded values that surfaced during design (search-min-chars, max-query-length, date-format hint) routed to `core/constants/`.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations. Section intentionally empty.
