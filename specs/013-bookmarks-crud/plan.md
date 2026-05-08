# Implementation Plan: Bookmarks CRUD

**Branch**: `013-bookmarks-crud` | **Date**: 2026-05-07 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/013-bookmarks-crud/spec.md`

## Summary

Ship the first Phase-3 (Data Features) spec by adding a complete Clean-Architecture data slice on top of the bookmarks/folders Room schema already shipped in Spec 005. Deliver: (1) a star icon in the browser top bar that toggles a "canonical" bookmark for the current page; (2) a full bookmarks screen with folder browsing, breadcrumbs, real-time global search showing folder path inline, manual add/edit/delete, unlimited-depth folder management with cascade delete and cycle-prevention. Zero schema migration, zero new third-party packages, zero new permissions. The four `/speckit-clarify` resolutions (search scope = global; tap = replace active tab; folder delete = cascade; star toggle = canonical-most-recent) drive the algorithm choices in [research.md](research.md).

## Technical Context

**Language/Version**: Kotlin (version pinned in `gradle/libs.versions.toml`, currently 2.3.21)
**Primary Dependencies**: Jetpack Compose (Material3, Compose BOM 2026.04.01), Hilt 2.59.2 + KSP, Room 2.8.4 (already on classpath via Spec 005), Navigation Compose, kotlinx-coroutines + Flow, `androidx.compose.material:material-icons-core` (already on classpath via Spec 007). **No new packages.**
**Storage**: Room (existing `AppDatabase`, existing `BookmarkEntity` + `BookmarkFolderEntity` from Spec 005). Zero schema migration.
**Testing**: JUnit + Compose UI Test + Espresso + Truth + Turbine (all on classpath); Robolectric 4.16.1 for Room JVM unit tests (pinned to SDK 33 per Spec 005's `app/src/test/resources/robolectric.properties`).
**Target Platform**: Android (minSdk 24 / targetSdk 36)
**Project Type**: Mobile app (Android single-module)
**Performance Goals**: Star toggle visual feedback ≤ 200 ms (SC-001); bookmark open ≤ 500 ms to begin URL load (SC-002); search 1,000 bookmarks ≤ 100 ms per keystroke (SC-003); 5-level-deep folder tree with ~50 items per level renders without scroll-frame stutter (SC-007); 60 fps overall per Constitution §V.
**Constraints**: APK release size delta ≤ 100 KB vs Spec 012 baseline 2.1 MB (SC-008). 16 KB CI gate green (SC-007 in plan: zero new `.so`). Constitution 11/11 PASS pre + post-design (SC-008 in plan).
**Scale/Scope**: 18 production code files added + 6 modified (browser top-bar / VM / state for star icon, plus DAO extensions). ~33 new EN string keys + 1 plurals × 8 locales ≈ 272 new translation entries (one obsolete key `bookmarks_screen_placeholder` is removed across 8 locales). Tests deferred to Copilot Pro per `.claude/claude-app/dev-workflow.md` (Claude=Planner, Copilot=Implementer split — mirrors Spec 011 / Spec 012 pattern).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Evaluated against `.specify/memory/constitution.md` v1.2.0 — 11 principles. **All 11 PASS pre-design.**

| # | Principle | Verdict | Evidence |
|---|---|---|---|
| I | Privacy & Security First | ✅ PASS | Bookmarks live in `thirtysix_browser.db` Room only; FR-004 explicitly disables star in incognito; no analytics, no network, no telemetry; star + manual add never log URLs anywhere. |
| II | Google Play Compliance | ✅ PASS | Zero new permissions; reuses existing `INTERNET` + `ACCESS_NETWORK_STATE`; no new WebView APIs; no `addJavascriptInterface`. |
| III | Code Quality & Safety + No-Hardcode Rule | ✅ PASS | All new strings via `stringResource(R.string.bookmarks_*)` in 8 locales (FR-029); colors via `MaterialTheme.colorScheme.*`; typography via `MaterialTheme.typography.*`; spacing via `Spacing.*`. New constants extracted to `core/constants/BrowserLimits.kt` (input length caps). No magic numbers in feature code. |
| IV | Clean Architecture (MVVM) | ✅ PASS | New `domain/model/Bookmark.kt` + `BookmarkFolder.kt` are pure Kotlin (zero Android imports); `BookmarkRepository` interface in `domain/repository/`; `BookmarkRepositoryImpl` in `data/repository/`; 14 use cases in `domain/usecase/`; ViewModels coordinate use cases without touching DAOs. **No Repo→Repo dependency** — single `BookmarkRepository` owns both `BookmarkDao` and `BookmarkFolderDao` (one bounded context); see [research.md R1](research.md#r1-repository-shape-combined-vs-split). |
| V | Performance Excellence | ✅ PASS | Search uses `LIKE` against indexed columns (no FTS migration); SC-003 budgets verified by [research.md R3](research.md#r3-search-implementation-strategy). Cascade delete batched in single Room `@Transaction` per [research.md R7](research.md#r7-cascade-delete-algorithm-correctness-vs-fk-set-null). 60 fps preserved via `LazyColumn` with stable keys. |
| VI | Testing Discipline | ✅ PASS | Unit tests planned for: 14 use cases, mapper × 2, repo impl, both ViewModels, BrowserViewModel star-path additions. Compose tests planned for: BookmarksScreen happy path, search filter, folder navigation breadcrumb, cascade-delete confirm dialog, edit dialog validation. **Tests authored by Copilot Pro implementer track** per dev-workflow.md (mirrors Spec 011 / 012 deferred-tests pattern). |
| VII | Offline-First | ✅ PASS | Bookmarks 100 % on-device; Room WAL active (Spec 005); writes atomic via single Room transactions. Folder cascade delete is one transactional unit. |
| VIII | Localization & Accessibility | ✅ PASS | All ~33 new EN keys + 1 plurals translated to 7 non-EN locales (~272 entries total); every icon-only affordance has `contentDescription = stringResource(...)` (FR-030); 48 dp touch targets via M3 `IconButton` defaults; WCAG-AA contrast inherits from existing M3 theme tokens; folder-row indentation respects RTL (no manual `Arrangement.Start`). |
| IX | Dependency Currency + 16 KB Page Size | ✅ PASS | **Zero new packages.** Spec 012 baseline `8/8 native lib entries align 2**14` carries forward unchanged. CI gate auto-passes. |
| X | Simplicity & Build Order | ✅ PASS | Spec 013 is next on Phase 3 per [sdd-roadmap.md](../../.claude/claude-app/sdd-roadmap.md) (parallel-available with 014/015 after Spec 007). All deps merged. No speculative code: long-press-link bookmark, drag-reorder, import/export, tags, bulk-delete, favicons all DEFERRED in spec Assumptions. |
| XI | Build Configuration | ✅ PASS | No new flavors; no new BuildConfig fields; no inline literals; signing config untouched. |

**Verdict: pre-design PASS 11/11. No Complexity Tracking entries required.**

## Project Structure

### Documentation (this feature)

```text
specs/013-bookmarks-crud/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── bookmark-repository.md  # BookmarkRepository interface contract
├── checklists/
│   └── requirements.md  # Spec quality checklist (already created in /speckit-specify)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/constants/
│   └── BrowserLimits.kt                                 # MODIFY (+MAX_BOOKMARK_TITLE_LENGTH, +MAX_FOLDER_NAME_LENGTH, +SEARCH_DEBOUNCE_MS)
├── data/
│   ├── local/dao/
│   │   ├── BookmarkDao.kt                               # MODIFY (+observeAll, +observeByUrl, +getMostRecentByUrl, +searchByTitleOrUrl, +countByUrl)
│   │   └── BookmarkFolderDao.kt                         # MODIFY (+observeAll, +observeAncestorChain helper not needed — done in repo)
│   ├── mapper/
│   │   ├── BookmarkMapper.kt                            # NEW
│   │   └── BookmarkFolderMapper.kt                      # NEW
│   └── repository/
│       └── BookmarkRepositoryImpl.kt                    # NEW (combines both DAOs in one repo per R1)
├── domain/
│   ├── model/
│   │   ├── Bookmark.kt                                  # NEW (pure Kotlin)
│   │   ├── BookmarkFolder.kt                            # NEW (pure Kotlin)
│   │   └── BookmarkSearchResult.kt                      # NEW (Bookmark + folderPath: List<BookmarkFolder>)
│   ├── repository/
│   │   └── BookmarkRepository.kt                        # NEW (interface — see contracts/bookmark-repository.md)
│   └── usecase/
│       ├── AddBookmarkUseCase.kt                        # NEW (manual add + star-tap-add path)
│       ├── UpdateBookmarkUseCase.kt                     # NEW
│       ├── DeleteBookmarkUseCase.kt                     # NEW
│       ├── MoveBookmarkUseCase.kt                       # NEW
│       ├── ToggleBookmarkUseCase.kt                     # NEW (FR-001..003 — star icon path)
│       ├── IsUrlBookmarkedUseCase.kt                    # NEW (FR-002 — drives star fill state)
│       ├── ObserveBookmarksByFolderUseCase.kt           # NEW (returns combined Flow<List<BookmarkFolder> + List<Bookmark>>)
│       ├── SearchBookmarksUseCase.kt                    # NEW (returns Flow<List<BookmarkSearchResult>>)
│       ├── ObserveFolderPathUseCase.kt                  # NEW (breadcrumb chain for current folder ID)
│       ├── CreateFolderUseCase.kt                       # NEW
│       ├── RenameFolderUseCase.kt                       # NEW
│       ├── MoveFolderUseCase.kt                         # NEW (cycle-prevention per R6)
│       ├── DeleteFolderUseCase.kt                       # NEW (cascade per R7)
│       └── CountFolderDescendantsUseCase.kt             # NEW (powers FR-020 confirm dialog)
├── di/
│   └── BookmarkModule.kt                                # NEW (@Binds BookmarkRepository → impl)
└── presentation/
    ├── browser/
    │   ├── BrowserUiState.kt                            # MODIFY (+isBookmarked: Boolean = false)
    │   ├── BrowserViewModel.kt                          # MODIFY (+IsUrlBookmarkedUseCase + ToggleBookmarkUseCase injected; +observe star state from current URL flow)
    │   └── BrowserScreen.kt                             # MODIFY (BrowserTopBar +star IconButton; FR-001..004)
    └── bookmarks/
        ├── BookmarksScreen.kt                           # REWRITE (placeholder → real screen)
        ├── BookmarksViewModel.kt                        # NEW
        ├── BookmarksUiState.kt                          # NEW
        ├── BookmarksErrorEvent.kt                       # NEW (sealed: InvalidUrl, FolderCycle, etc.)
        └── components/
            ├── BookmarksTopBar.kt                       # NEW (back, title=current folder name, search, "new folder" overflow, FAB-add anchor)
            ├── BreadcrumbBar.kt                         # NEW (FR-014)
            ├── BookmarkRow.kt                           # NEW (title + hostname + long-press → action sheet)
            ├── FolderRow.kt                             # NEW (folder icon + name + child count + long-press → action sheet)
            ├── BookmarkSearchResultRow.kt               # NEW (BookmarkRow + folder path label per FR-026)
            ├── EmptyBookmarksState.kt                   # NEW (FR-011)
            ├── NoSearchMatchesState.kt                  # NEW (FR-028)
            ├── AddOrEditBookmarkDialog.kt               # NEW (one dialog used for both manual-add + edit, mode flag)
            ├── CreateOrRenameFolderDialog.kt            # NEW (one dialog used for both)
            ├── FolderPickerSheet.kt                     # NEW (M3 ModalBottomSheet, hierarchical with indentation, used by Move + Add)
            ├── BookmarkActionSheet.kt                   # NEW (long-press: Open / Edit / Move / Delete)
            ├── FolderActionSheet.kt                     # NEW (long-press: Open / Rename / Move / Delete)
            └── DeleteFolderConfirmDialog.kt             # NEW (FR-020 — shows total descendant count)

app/src/main/res/
├── values/strings.xml                                   # MODIFY (+ ~33 new EN keys + 1 plurals; remove `bookmarks_screen_placeholder`)
├── values-vi/strings.xml                                # MODIFY (+~33 VI translations + 1 plurals)
├── values-de/strings.xml                                # MODIFY (+~33 DE translations + 1 plurals)
├── values-ru/strings.xml                                # MODIFY (+~33 RU translations + 1 plurals)
├── values-ko/strings.xml                                # MODIFY (+~33 KO translations + 1 plurals)
├── values-ja/strings.xml                                # MODIFY (+~33 JA translations + 1 plurals)
├── values-zh/strings.xml                                # MODIFY (+~33 ZH translations + 1 plurals)
└── values-fr/strings.xml                                # MODIFY (+~33 FR translations + 1 plurals)
```

**Structure Decision**: Single Android module rooted at `app/src/main/kotlin/com/raumanian/thirtysix/browser/`, organized per the Constitution §IV layered layout (`core/`, `data/`, `domain/`, `presentation/`, `di/`). Existing `presentation/bookmarks/BookmarksScreen.kt` placeholder (Spec 002) is replaced; the existing `bookmarks_screen_placeholder` resource key is removed from all 8 locale files (mirrors how Spec 007 removed `browser_screen_placeholder`). All 18 new production files plus 6 modified files stay within the established package layout — no new packages or layer-crossing.

## Complexity Tracking

> **No constitutional violations to justify.**

The single combined `BookmarkRepository` (owning both `BookmarkDao` and `BookmarkFolderDao`) is **not** a Constitution §IV deviation — `BookmarkRepository` is one repository with two data sources, not a Repo→Repo dependency. Cascade delete spans both DAOs but lives transactionally inside a single Repo method. See [research.md R1](research.md#r1-repository-shape-combined-vs-split) for the detailed rationale and the alternative (split into `BookmarkRepository` + `BookmarkFolderRepository`) considered and rejected.

The `Spec 011 → Spec 013` use-case dependency where `BookmarksViewModel` injects `UpdateActiveTabUrlAndTitleUseCase` (to load the tapped bookmark's URL into the active tab per FR-010) is **not** a §IV violation — it's a ViewModel coordinating two use cases from different domains, which §IV explicitly allows. Mirrors how `BrowserViewModel` already coordinates `BuildSearchUrlUseCase` (Spec 010) + tabs use cases (Spec 011) + incognito flag (Spec 012).

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| _none_ | — | — |
