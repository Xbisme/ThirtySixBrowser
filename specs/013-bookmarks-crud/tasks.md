---

description: "Task list for Spec 013 — Bookmarks CRUD"
---

# Tasks: Bookmarks CRUD

**Input**: Design documents from `/specs/013-bookmarks-crud/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/bookmark-repository.md](contracts/bookmark-repository.md), [quickstart.md](quickstart.md)

**Tests**: Tests are INCLUDED. Per `.claude/claude-app/dev-workflow.md` Claude=Planner / Copilot=Implementer split, tests are listed as explicit tasks for the implementer track to author (mirrors Spec 011 / 012 pattern). Constitution §VI sets the gate.

**Organization**: Grouped by user story for independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]** = parallelizable (different files, no dependency on incomplete tasks)
- **[Story]** = which user story (US1–US7) the task belongs to; setup / foundational / polish phases have no story label

## Path Conventions

Single-module Android app rooted at `app/src/main/kotlin/com/raumanian/thirtysix/browser/`. Test sources at `app/src/test/kotlin/.../` (JVM/Robolectric) and `app/src/androidTest/kotlin/.../` (instrumented). Resource files at `app/src/main/res/values{,-vi,-de,-ru,-ko,-ja,-zh,-fr}/`.

---

## Phase 1: Setup

**Purpose**: Confirm branch and prepare shared constants used by foundational + every user story.

- [X] T001 Confirm branch `013-bookmarks-crud` checked out, `./gradlew clean assembleDebug` green on `main` baseline before any code changes.
- [X] T002 [P] Add five new constants to [app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/BrowserLimits.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/BrowserLimits.kt): `MAX_BOOKMARK_TITLE_LENGTH = 200`, `MAX_BOOKMARK_URL_LENGTH = 2048`, `MAX_FOLDER_NAME_LENGTH = 100`, `MAX_BOOKMARKS = 10_000`, `MAX_FOLDERS = 1_000`. Per [research.md R13](research.md#r13-constants-additions--coreconstantsbrowserlimitskt). Constitution §III No-Hardcode Rule.
- [X] T003 [P] Add three new keys per locale to `app/src/main/res/values/strings.xml` and the 7 non-EN files (`values-vi`, `values-de`, `values-ru`, `values-ko`, `values-ja`, `values-zh`, `values-fr`): `bookmark_action_star_add_cd`, `bookmark_action_star_remove_cd`, `bookmark_added_snackbar`, `bookmark_removed_snackbar` — used by US1. Translations follow the brand-name preservation rule from Spec 004 ("ThirtySix" stays Latin).

---

## Phase 2: Foundational (Blocking — must be complete before any user story)

**Purpose**: Domain models, mappers, DAO surface extensions, repository interface + impl + Hilt module + the cross-cutting validator and tests. Every user story depends on this phase.

**⚠️ CRITICAL**: No US-phase task may begin until the entire Phase 2 is green (all foundational unit tests pass).

### Domain layer

- [X] T004 [P] Create [app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/Bookmark.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/Bookmark.kt) per [data-model.md §1](data-model.md). Pure Kotlin, no Android imports.
- [X] T005 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/BookmarkFolder.kt` per [data-model.md §1](data-model.md).
- [X] T006 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/BookmarkSearchResult.kt` per [data-model.md §1](data-model.md).
- [X] T007 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/BookmarkDescendantCount.kt` per [data-model.md §1](data-model.md).
- [X] T008 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/error/BookmarkExceptions.kt` containing sealed exception types: `BookmarkNotFoundException(id: Long)`, `FolderNotFoundException(id: Long)`, `FolderCycleException(folderId: Long, attemptedParentId: Long?)`, `BookmarkUrlValidationException(reason: BookmarkUrlValidationError)`. See [contracts/bookmark-repository.md §Error semantics](contracts/bookmark-repository.md).
- [X] T009 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/validator/BookmarkUrlValidator.kt` (pure-Kotlin object) implementing [research.md R5](research.md#r5-url-validation--at-what-layer-with-what-rules) — trim → auto-prepend `https://` if missing scheme → `URI.create` → reject blank host or non-http(s) scheme. Returns `Result<String>` with normalized URL on success. Sealed `BookmarkUrlValidationError`: `Empty`, `InvalidScheme`, `MissingHost`.
- [X] T010 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/BookmarkRepository.kt` interface with the full surface from [contracts/bookmark-repository.md](contracts/bookmark-repository.md) (15 methods).

### Data layer

- [X] T011 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/mapper/BookmarkMapper.kt` per [data-model.md §2](data-model.md). `object` with `toDomain` and `toEntity` round-trip property.
- [X] T012 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/mapper/BookmarkFolderMapper.kt` per [data-model.md §2](data-model.md).
- [X] T013 Modify [app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/dao/BookmarkDao.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/dao/BookmarkDao.kt) — add 6 new methods per [data-model.md §3](data-model.md): `observeByUrl(url)`, `countByUrl(url)`, `deleteMostRecentByUrl(url)`, `deleteByParentFolder(folderId)`, `searchByTitleOrUrl(query)`, `countByFolder(folderId)`. Existing methods remain untouched.
- [X] T014 Modify `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/dao/BookmarkFolderDao.kt` — add 2 new methods: `getDirectChildren(parentId)` (synchronous suspend, NOT Flow — used by cascade walk), `deleteById(id)`. Existing methods unchanged.
- [X] T015 Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository/BookmarkRepositoryImpl.kt` implementing the full `BookmarkRepository` interface. `@Singleton` `@Inject` constructor with `BookmarkDao`, `BookmarkFolderDao`, `AppDatabase` (for `withTransaction { ... }`), `DispatcherProvider`. Implement all 15 methods per [contracts/bookmark-repository.md](contracts/bookmark-repository.md). Cascade delete (R7) uses depth-first DFS inside one `database.withTransaction { ... }`. Folder cycle prevention (R6) uses ancestor walk via `getAncestorChain`. Star semantics (R9) implemented via `countByUrl + deleteMostRecentByUrl` calls.

### Hilt wiring

- [X] T016 [P] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/di/BookmarkModule.kt` — `@Module @InstallIn(SingletonComponent::class) abstract class` with `@Binds @Singleton bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository`. `AppDatabase` injection comes from existing Spec 005 `DatabaseModule` — no DI changes needed there.

### Foundational tests

- [X] T017 [P] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/validator/BookmarkUrlValidatorTest.kt` covering: trim, auto-prepend, valid http URL, valid https URL, malformed scheme, missing host, blank input, edge cases (`example.com`, `https://`, `ftp://example.com`, `https://例え.テスト/`).
- [X] T018 [P] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/mapper/BookmarkMapperTest.kt` — round-trip property + null handling for `parentFolderId`.
- [X] T019 [P] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/mapper/BookmarkFolderMapperTest.kt` — round-trip property.
- [X] T020 Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/repository/BookmarkRepositoryImplTest.kt` (Robolectric + real Room). Cover: insert/update/delete/query happy paths, cycle prevention rejection, cascade delete on a 3-level tree (verify atomicity by sabotaging mid-walk), `deleteMostRecentByUrl` correctness when 3 bookmarks share a URL, `getAncestorChain` over 5-level chain. Pin Robolectric SDK 33 per Spec 005 `app/src/test/resources/robolectric.properties`. ~15 test cases.
- [X] T021 [P] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/local/dao/BookmarkDaoSpec013Test.kt` extending Spec 005's `BookmarkDaoTest` for the 5 new methods (`observeByUrl`, `countByUrl`, `deleteMostRecentByUrl`, `deleteByParentFolder`, `searchByTitleOrUrl`). Use Turbine for the `Flow` assertions.
- [X] T022 [P] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/local/dao/BookmarkFolderDaoSpec013Test.kt` for the 2 new methods.

### Test fakes for upper layers

- [X] T023 [P] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/testdoubles/FakeBookmarkRepository.kt` — in-memory map-backed fake honoring the contract in [contracts/bookmark-repository.md](contracts/bookmark-repository.md). Used by all 14 use-case tests + ViewModel tests (mirrors `FakeIncognitoTabRepository` from Spec 012). Cycle prevention + cascade-delete logic mirrors the real impl.

**Checkpoint**: Foundation green when T004–T023 all pass `./gradlew testDebugUnitTest` + `lintDebug` + `detekt` + `ktlintCheck`. User-story phases unblock here.

---

## Phase 3: User Story 1 — Bookmark current page (Priority: P1) 🎯 MVP

**Goal**: User taps the star icon in the browser top bar to save / un-save the current page as a bookmark.

**Independent Test**: Cold-start app, load any http(s) page in a non-incognito tab, tap star → bookmark persists across process kill (verified via direct Room query). Tap star again → bookmark removed. Manual gate G1 in [quickstart.md](quickstart.md).

### Use cases for US1

- [X] T024 [P] [US1] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/AddBookmarkUseCase.kt`. Validates inputs via `BookmarkUrlValidator`, generates `createdAt = System.currentTimeMillis()`, sets `sortOrder = createdAt`, calls `repo.addBookmark`. Returns `Result<Long>` (the new bookmark id).
- [X] T025 [P] [US1] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/IsUrlBookmarkedUseCase.kt`. Returns `Flow<Boolean>` derived from `repo.observeBookmarksByUrl(url).map { it.isNotEmpty() }` per [research.md R9](research.md#r9-star-icon-canonical-bookmark-semantics--implementation).
- [X] T026 [US1] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ToggleBookmarkUseCase.kt`. Wraps `AddBookmarkUseCase` + `repo.deleteMostRecentBookmarkByUrl` + `repo.countBookmarksByUrl`. Title falls back to URL string when blank (FR-007). Returns sealed `ToggleResult { Added, Removed }` so the UI can pick the right snackbar message.

### US1 use case tests

- [X] T027 [P] [US1] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/usecase/AddBookmarkUseCaseTest.kt` — happy path + URL validation rejection + length-cap rejection + uses `FakeBookmarkRepository`.
- [X] T028 [P] [US1] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/usecase/IsUrlBookmarkedUseCaseTest.kt` — emits `false` then `true` after add then `false` after remove (Turbine).
- [X] T029 [P] [US1] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ToggleBookmarkUseCaseTest.kt` — covers Q4 canonical-most-recent: 3 manual-duplicate bookmarks for same URL, toggle off deletes only the newest, leaves the other 2 intact.

### Browser screen integration for US1

- [X] T030 [US1] Modify [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserUiState.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserUiState.kt) — add fields `val isBookmarked: Boolean = false` (FR-002) AND `val bookmarkSnackbarEvent: BookmarkSnackbarEvent? = null` (one-shot toast channel, sealed: `Added`, `Removed`). Defaults preserve binary compatibility with all existing call sites. Create new file `BookmarkSnackbarEvent.kt` next to `BrowserUiState.kt`. Update KDoc to mention Spec 013.
- [X] T031 [US1] Modify [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt) — inject `IsUrlBookmarkedUseCase` and `ToggleBookmarkUseCase` (constructor 8 → 10 params; bump `@Suppress("LongParameterList")` rationale comment if detekt threshold reached). Wire `IsUrlBookmarkedUseCase(currentUrl)` to a Flow that updates `_uiState.isBookmarked`. Add public `fun onStarTapped()` calling `viewModelScope.launch { ToggleBookmarkUseCase(...) }`. Add a new dedicated `bookmarkSnackbarEvent: BookmarkSnackbarEvent? = null` field on `BrowserUiState` (sealed: `Added`, `Removed`) — separate channel from the existing `tabsEvent`, mirrors Spec 012's `MaxIncognitoTabsReached` event-pattern. Provide `consumeBookmarkSnackbarEvent()` to clear after the snackbar is shown.
- [X] T032 [US1] Modify [app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt) — extend the private `BrowserTopBar` Composable to render a star `IconButton` per [research.md R11](research.md#r11-star-icon-placement-on-browsertopbar). Hide via `if (canBookmark)` where `canBookmark = !state.isIncognito && state.loadingState is Loaded && state.currentUrl.startsWith("http")`. Use `Icons.Filled.Bookmark` when `state.isBookmarked` and `Icons.Filled.BookmarkBorder` otherwise. `contentDescription` = `stringResource(R.string.bookmark_action_star_add_cd)` or `bookmark_action_star_remove_cd` accordingly. Wire to `viewModel::onStarTapped`. Snackbar host displays the result message.

### US1 ViewModel + instrumented tests

- [X] T033 [P] [US1] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelStarToggleTest.kt` — additive to existing `BrowserViewModelTest`. Cover: star state Flow updates on URL change; star state updates after ToggleBookmarkUseCase mutation; `onStarTapped` emits Added vs Removed events.
- [ ] T034 [US1] Create `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreenStarIconTest.kt` — instrumented Compose UI test. Star icon visibility gated by URL validity + incognito; tap toggles state; snackbar appears.
- [ ] T035 [US1] Manual user-device gate G1 from [quickstart.md](quickstart.md) — verify star round-trip across process kill on real device. Gate marked PASS by user comment on PR.

**Checkpoint**: US1 fully testable. App ships an MVP value: "I can save the current page with one tap". Bookmarks screen is still placeholder; saved bookmarks visible only via direct DB query. Phase 4 onwards adds the user-facing list.

---

## Phase 4: User Story 2 — Browse and open my bookmarks (Priority: P1)

**Goal**: User opens the bookmarks screen, sees a list of folders + bookmarks at the current level (root by default), navigates into folders via tap, returns up via back arrow + breadcrumb. Tapping a bookmark replaces the active tab's URL.

**Independent Test**: Pre-seed Room with 5 bookmarks across 2 folders, open bookmarks screen → list visible; tap folder → list updates to that folder's contents + breadcrumb shows path; tap bookmark → return to browser, active tab URL updates. Manual gate G2 in [quickstart.md](quickstart.md).

### Use cases for US2

- [X] T036 [P] [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ObserveBookmarksByFolderUseCase.kt`. Returns combined `Flow<BookmarksByFolder>` where `BookmarksByFolder` is a `data class(folders: List<BookmarkFolder>, bookmarks: List<Bookmark>)` — single emission per state. Internally uses `Flow.combine(repo.observeFoldersByParent(folderId), repo.observeBookmarksByFolder(folderId))`. Per [data-model.md](data-model.md) and FR-008 / FR-009.
- [X] T037 [P] [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ObserveFolderPathUseCase.kt`. Given a `folderId: Long?`, returns `Flow<List<BookmarkFolder>>` — root → leaf chain. Backed by `repo.getAncestorChain` re-emitted on `repo.observeFoldersByParent(parent.parentId)` upstream change (folder rename / move bubbles up). Powers breadcrumb (FR-014).
- [X] T038 [P] [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/DeleteBookmarkUseCase.kt`. Trivial wrapper over `repo.deleteBookmark(id)`. Used here so US2 list can support tap-and-hold-delete fast path; full long-press action sheet comes in US6.

### US2 use case tests

- [X] T039 [P] [US2] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ObserveBookmarksByFolderUseCaseTest.kt`.
- [X] T040 [P] [US2] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ObserveFolderPathUseCaseTest.kt`.
- [X] T041 [P] [US2] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/usecase/DeleteBookmarkUseCaseTest.kt`.

### BookmarksScreen rewrite

- [X] T042 [US2] Remove the obsolete string key `bookmarks_screen_placeholder` from `app/src/main/res/values/strings.xml` and the 7 non-EN locale files. (Mirrors how Spec 007 removed `browser_screen_placeholder`.)
- [X] T043 [P] [US2] Add new EN keys to `app/src/main/res/values/strings.xml` then translate to 7 non-EN locales: `bookmarks_screen_title`, `bookmarks_empty_title`, `bookmarks_empty_body`, `bookmarks_action_back_to_parent_cd`, `bookmarks_breadcrumb_root_label`. Brand-name preservation rule per Spec 004.
- [X] T044 [P] [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/BookmarksUiState.kt`. Fields: `currentFolderId: Long?` (null = root), `breadcrumb: List<BookmarkFolder>` (empty at root), `folders: List<BookmarkFolder>`, `bookmarks: List<Bookmark>`, `isLoading: Boolean = false`, `errorEvent: BookmarksErrorEvent? = null`. `data class`, immutable. Defaults preserve binary compat.
- [X] T045 [P] [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/BookmarksErrorEvent.kt`. Sealed: `InvalidUrl(reason)`, `FolderCycle`, `BookmarkNotFound`, `FolderNotFound`, plus future-extensible. Each maps to a `@StringRes` ID via `toUserMessageRes()` extension.
- [X] T046 [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/BookmarksViewModel.kt` — `@HiltViewModel`. Inject `ObserveBookmarksByFolderUseCase`, `ObserveFolderPathUseCase`, `DeleteBookmarkUseCase`, `UpdateActiveTabUrlAndTitleUseCase` (Spec 011 — for "open bookmark" path per FR-010), `DispatcherProvider`. Public methods: `onFolderTap(folderId)`, `onBackToParent()`, `onBreadcrumbSegmentTap(folderId)`, `onBookmarkTap(bookmark)`. Internal: maintains `currentFolderIdFlow: MutableStateFlow<Long?>`, derives folder children + breadcrumb via `flatMapLatest`. Uses `BaseViewModel.launchSafely` per Spec 002.
- [X] T047 [P] [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/components/BookmarkRow.kt` — Material3 `ListItem` with leading icon `Icons.Outlined.Bookmark` tinted `MaterialTheme.colorScheme.primary` (no per-bookmark favicon in v1.0 per spec Assumptions), headline = title, supporting = hostname (extracted via `URI.create(url).host` or via existing `UrlPatterns` helper). `onClick` lambda. No long-press in US2 — added in US3+.
- [X] T048 [P] [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/components/FolderRow.kt` — Material3 `ListItem` with leading folder icon, headline = name, supporting = "%d items" (placeholder until US3 adds real child count). `onClick` lambda.
- [X] T049 [P] [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/components/EmptyBookmarksState.kt` — centered Composable with bookmark-outline icon + `bookmarks_empty_title` headline + `bookmarks_empty_body` body.
- [X] T050 [P] [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/components/BreadcrumbBar.kt` — horizontal `Row` of clickable `Text` segments separated by " / ". Leading "Root" segment uses `bookmarks_breadcrumb_root_label`. `onSegmentClick(folderId: Long?)` lambda.
- [X] T051 [P] [US2] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/components/BookmarksTopBar.kt` — Material3 `TopAppBar` with title = `bookmarks_screen_title` (or current folder name if not root), navigationIcon = up-arrow when not root + `bookmarks_action_back_to_parent_cd`, plus action-slots that are stubbed in US2 and filled in US3 (new folder), US4 (search), US7 (FAB anchor — actually FAB is a Scaffold property, not a TopBar slot).
- [X] T052 [US2] Rewrite `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/BookmarksScreen.kt` — `Scaffold` with `BookmarksTopBar` + body that combines `BreadcrumbBar` + `LazyColumn` rendering folders first then bookmarks (or empty state). Hilt-injected `BookmarksViewModel` via `hiltViewModel()`. Tap-bookmark → ViewModel's `onBookmarkTap` → triggers navigation back to `AppDestination.Browser` (via existing nav graph). Tap-folder → updates internal state, no navigation.
- [X] T053 [US2] Modify `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/navigation/AppNavGraph.kt` — pass a `popBackStackToBrowser: () -> Unit` lambda or use `NavController.popBackStack(AppDestination.Browser.route, false)` from inside `BookmarksScreen` after `onBookmarkTap`. Result: tapping a bookmark closes the bookmarks screen and returns to browser.

### US2 ViewModel + instrumented tests

- [X] T054 [P] [US2] Create `app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/BookmarksViewModelTest.kt` — covers folder navigation, breadcrumb derivation, bookmark tap → calls `UpdateActiveTabUrlAndTitleUseCase`. Includes a dedicated test case for FR-010a: when the active tab is incognito, the use-case is invoked with the active tab's `isIncognito = true` so the URL load routes to the incognito repo (verified via fake `UpdateActiveTabUrlAndTitleUseCase` capturing args). Uses `FakeBookmarkRepository` + a fake `UpdateActiveTabUrlAndTitleUseCase`.
- [ ] T055 [US2] Create `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/BookmarksScreenInstrumentedTest.kt` — boots BookmarksScreen with a Hilt-overriden fake repo; verifies list rendering, folder navigation, breadcrumb tap, empty state.
- [ ] T056 [US2] Manual user-device gate G2 from [quickstart.md](quickstart.md) (folder navigation + breadcrumb). User-marked PASS on PR.

**Checkpoint**: US1 + US2 ship the closed loop "save → list → open" — the genuinely demoable MVP.

---

## Phase 5: User Story 3 — Organize bookmarks into folders (Priority: P2)

**Goal**: User creates folders, renames them, moves them (with cycle prevention), and uses a folder picker to move bookmarks. Long-press on a row opens an action sheet.

**Independent Test**: Create `Work` at root, create `Work/Project A` inside, attempt to move `Work` into `Work/Project A` and observe the cycle-prevention error, rename `Work` to `Personal`. Manual gate G2 + G5 in [quickstart.md](quickstart.md).

### Use cases for US3

- [X] T057 [P] [US3] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/CreateFolderUseCase.kt` — validates name non-blank + length cap; calls `repo.createFolder`.
- [X] T058 [P] [US3] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/RenameFolderUseCase.kt`.
- [X] T059 [P] [US3] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/MoveFolderUseCase.kt` — cycle prevention via `repo.moveFolder` which already enforces R6 internally. Maps `FolderCycleException` → `BookmarksErrorEvent.FolderCycle`.
- [X] T060 [P] [US3] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/MoveBookmarkUseCase.kt`.
- [X] T061 [P] [US3] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/UpdateBookmarkUseCase.kt` — used here for move (the move dialog re-uses the edit dialog flow); full edit support comes in US5.

### US3 use case tests

- [X] T062 [P] [US3] Create `CreateFolderUseCaseTest`, `RenameFolderUseCaseTest`, `MoveFolderUseCaseTest`, `MoveBookmarkUseCaseTest`, `UpdateBookmarkUseCaseTest` test files in `app/src/test/.../domain/usecase/`. Cycle prevention test in `MoveFolderUseCaseTest` builds a 5-deep chain. Five separate files for parallel execution.

### US3 strings + UI components

- [X] T063 [P] [US3] Add 10 EN keys + 7 locale translations: `bookmarks_action_new_folder_cd`, `bookmarks_dialog_create_folder_title`, `bookmarks_dialog_rename_folder_title`, `bookmarks_dialog_folder_picker_title`, `bookmarks_picker_new_folder_inline`, `bookmarks_field_folder_label`, `bookmarks_action_rename`, `bookmarks_action_move`, `bookmarks_validation_folder_name_required`, `bookmarks_error_folder_cycle`.
- [X] T064 [P] [US3] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/components/CreateOrRenameFolderDialog.kt` — Material3 `AlertDialog` with single text field + validation inline error + Save/Cancel buttons. Mode flag: `Create` vs `Rename(currentName)`.
- [X] T065 [P] [US3] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/components/FolderPickerSheet.kt` — `ModalBottomSheet` per [research.md R10](research.md#r10-folder-picker-ui--hierarchical-list-pattern). Hierarchical `LazyColumn` with depth-based indentation. Synthetic "Root / Top-level" pinned at index 0. "+ New folder here" inline button at the bottom. Returns selected `parentId: Long?` to caller.
- [X] T066 [P] [US3] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/components/FolderActionSheet.kt` — `ModalBottomSheet` with action items: Open / Rename / Move / Delete (Delete wired in US6). Material3 list items with leading icons + content descriptions.
- [X] T067 [P] [US3] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/components/BookmarkActionSheet.kt` — same pattern: Open / Edit (US5) / Move / Delete (US6).
- [X] T068 [US3] Modify `BookmarksTopBar.kt` (T051) — add overflow menu item "New folder" with `bookmarks_action_new_folder_cd` content description. Wired to `viewModel::onCreateFolderClick`.
- [X] T069 [US3] Modify `BookmarksUiState.kt` (T044) — add `pendingDialog: PendingDialog?` field where `PendingDialog` is sealed: `CreateFolder(parentId)`, `RenameFolder(folderId, currentName)`, `MoveBookmark(bookmarkId)`, `MoveFolder(folderId)`, `EditBookmark(bookmark)`, `AddBookmark`, `ConfirmDeleteFolder(folderId, count)`. Allows the screen to render any one dialog at a time.
- [X] T070 [US3] Modify `BookmarksViewModel.kt` (T046) — inject `CreateFolderUseCase`, `RenameFolderUseCase`, `MoveFolderUseCase`, `MoveBookmarkUseCase`, `UpdateBookmarkUseCase`. Add public methods: `onCreateFolderClick()`, `onRenameFolderClick(folderId)`, `onMoveFolderClick(folderId)`, `onMoveBookmarkClick(bookmarkId)`, `onLongPressBookmark(bookmarkId)`, `onLongPressFolder(folderId)`, `onDialogConfirm(dialog, ...)`, `onDialogDismiss()`.
- [X] T071 [US3] Modify `BookmarksScreen.kt` (T052) — render the active dialog from `state.pendingDialog`; long-press on `BookmarkRow` and `FolderRow` triggers the appropriate action sheet.
- [X] T072 [US3] Modify `BookmarkRow.kt` (T047) and `FolderRow.kt` (T048) — add `onLongPress: () -> Unit` parameter wired to `combinedClickable`.

### US3 instrumented test

- [ ] T073 [US3] Create `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/BookmarksScreenFolderManagementTest.kt` — covers create folder, rename, move into another folder via picker, attempt cycle and observe rejection.

**Checkpoint**: US1 + US2 + US3 deliver the full folder-organized bookmarks experience minus delete and search. App is fully usable for organization.

---

## Phase 6: User Story 4 — Search bookmarks by title or URL (Priority: P2)

**Goal**: Real-time global search showing folder path inline.

**Independent Test**: Pre-seed 10 bookmarks across folders, type query, observe filtered results with path label; clear query → return to current folder. Manual gate G3 in [quickstart.md](quickstart.md).

### Use case for US4

- [X] T074 [P] [US4] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/SearchBookmarksUseCase.kt`. Returns `Flow<List<BookmarkSearchResult>>`. Uses `repo.searchBookmarks(query)` then maps each `Bookmark` to a `BookmarkSearchResult` by walking ancestor chain (cached map of folders to amortize cost) per [research.md R3 implementation note](research.md#r3-search-implementation-strategy--like-vs-fts).
- [X] T075 [P] [US4] Create `SearchBookmarksUseCaseTest` — case-insensitive substring match, both title and url, folder path correctness, empty query handling, ~1 K-bookmark perf smoke (assert <50 ms on Robolectric).

### US4 strings + UI

- [X] T076 [P] [US4] Add 2 EN keys + 7 locale translations: `bookmarks_action_search_cd`, `bookmarks_no_search_matches`.
- [X] T077 [P] [US4] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/components/BookmarkSearchResultRow.kt` — extends `BookmarkRow` with an inline path label below the hostname. Empty `folderPath` renders the localized "Root" label.
- [X] T078 [P] [US4] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/components/NoSearchMatchesState.kt` — distinct empty state with `bookmarks_no_search_matches` showing the typed query inline.
- [X] T079 [US4] Modify `BookmarksUiState.kt` (T044) — add `searchQuery: String = ""`, `isSearchActive: Boolean = false`, `searchResults: List<BookmarkSearchResult> = emptyList()`. When `isSearchActive` is true the screen renders `searchResults` instead of the folder children list.
- [X] T080 [US4] Modify `BookmarksViewModel.kt` (T046) — inject `SearchBookmarksUseCase`. Add `onSearchOpenClick()`, `onSearchClose()`, `onSearchQueryChange(query)`. Internally: `MutableStateFlow<String>` for query → `flatMapLatest { searchUseCase(it) }` per [research.md R4](research.md#r4-real-time-search--debounce-or-no).
- [X] T081 [US4] Modify `BookmarksTopBar.kt` (T051) — add search `IconButton` with `bookmarks_action_search_cd`. Tap opens an inline search field that replaces the title; clear-X dismisses search.
- [X] T082 [US4] Modify `BookmarksScreen.kt` (T052) — when `state.isSearchActive`, render `LazyColumn` of `BookmarkSearchResultRow` (or `NoSearchMatchesState` if empty + non-empty query).

### US4 instrumented test

- [ ] T083 [US4] Create `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/BookmarksScreenSearchTest.kt` — pre-seed 10 bookmarks across 3 folders, navigate into deepest folder, open search, type query, verify global results with path labels; clear → folder view restored.

**Checkpoint**: US1–US4 deliver save + browse + organize + search.

---

## Phase 7: User Story 5 — Edit a saved bookmark (Priority: P2)

**Goal**: Long-press bookmark → action sheet → Edit → dialog with title + URL + folder picker → save.

**Independent Test**: Long-press any bookmark → tap Edit → change title and observe immediate update in list; change URL to malformed → observe inline validation error. Manual gate G6 covers validation.

### US5 strings

- [X] T084 [P] [US5] Add 9 EN keys + 7 locale translations: `bookmarks_action_open`, `bookmarks_action_edit`, `bookmarks_action_delete`, `bookmarks_dialog_edit_title`, `bookmarks_field_title_label`, `bookmarks_field_url_label`, `bookmarks_validation_title_required`, `bookmarks_validation_url_required`, `bookmarks_validation_url_invalid`.

### US5 UI

- [X] T085 [P] [US5] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/components/AddOrEditBookmarkDialog.kt` — Material3 `AlertDialog` with two text fields (title + URL) + folder picker chip (opens `FolderPickerSheet` from T065) + Save/Cancel. Mode flag: `Add(parentId)` or `Edit(bookmark)`. Validation runs `BookmarkUrlValidator` on URL field; inline errors per FR-006.
- [X] T086 [US5] Modify `BookmarkActionSheet.kt` (T067) — wire the Edit item to `onEditClick(bookmark)`.
- [X] T087 [US5] Modify `BookmarksViewModel.kt` (T046) — add `onEditBookmarkClick(bookmark)` (sets `pendingDialog = EditBookmark(bookmark)`); `onDialogConfirm(EditBookmark, newTitle, newUrl, newParentId)` calls `UpdateBookmarkUseCase` (T061).

### US5 instrumented test

- [ ] T088 [US5] Create `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/BookmarksScreenEditBookmarkTest.kt` — long-press → edit dialog opens with current values; change title → observe update; submit malformed URL → observe inline error.

---

## Phase 8: User Story 6 — Delete bookmarks and folders safely (Priority: P2)

**Goal**: Single-tap delete bookmark; delete empty folder no prompt; delete non-empty folder with cascade-confirm dialog showing total descendant count.

**Independent Test**: Build folder tree with mixed contents → delete non-empty folder → confirm dialog shows correct count → confirm → all descendants gone atomically. Manual gate G4 in [quickstart.md](quickstart.md).

### Use cases for US6

- [X] T089 [P] [US6] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/DeleteFolderUseCase.kt`. Wraps `repo.deleteFolderCascade(folderId)`. Returns `Result<BookmarkDescendantCount>`. Translates `FolderNotFoundException` → `BookmarksErrorEvent.FolderNotFound`.
- [X] T090 [P] [US6] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/CountFolderDescendantsUseCase.kt`. Computes `BookmarkDescendantCount` by walking the tree depth-first via `repo.observeFoldersByParent(...).first()` + `repo.countByFolder(...)`. Per [research.md R8](research.md#r8-confirm-delete-dialog--count-of-descendants).

### US6 use case tests

- [X] T091 [P] [US6] Create `DeleteFolderUseCaseTest` — verifies cascade deletes 3-level tree with mixed bookmarks; rejects when folder doesn't exist; preserves siblings.
- [X] T092 [P] [US6] Create `CountFolderDescendantsUseCaseTest` — verifies count over a 4-level tree with 7 bookmarks total → returns `(7, 3)`.

### US6 strings + UI

- [X] T093 [P] [US6] Add 4 EN keys + 7 locale translations: `bookmarks_confirm_delete_folder_title`, `bookmarks_confirm_delete_folder_button`, `bookmarks_confirm_delete_folder_body` (plurals resource — needs `<plurals name="...">` per locale; brand-name preservation rule applies). EN body string template: "This will permanently delete %1$d bookmark(s) and %2$d folder(s)."
- [X] T094 [P] [US6] Create `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/components/DeleteFolderConfirmDialog.kt` — Material3 `AlertDialog` rendering localized title + plurals body with `bookmarks_confirm_delete_folder_body` substituted with the descendant count + danger-styled "Delete folder and contents" button. Cancel button. `containerColor = MaterialTheme.colorScheme.errorContainer` for visual emphasis.
- [X] T095 [US6] Modify `BookmarkActionSheet.kt` (T067) — wire Delete item to `onDeleteBookmarkClick(bookmarkId)` (no confirm dialog for individual bookmarks per FR-019).
- [X] T096 [US6] Modify `FolderActionSheet.kt` (T066) — wire Delete item to `onDeleteFolderClick(folderId)`.
- [X] T097 [US6] Modify `BookmarksViewModel.kt` (T046) — inject `DeleteFolderUseCase` + `CountFolderDescendantsUseCase`. `onDeleteFolderClick`: call CountFolderDescendantsUseCase; if total == 0 → call DeleteFolderUseCase directly + emit success; else set `pendingDialog = ConfirmDeleteFolder(folderId, count)`. `onDialogConfirm(ConfirmDeleteFolder)` → call DeleteFolderUseCase + clear pendingDialog. `onDeleteBookmarkClick(id)` calls DeleteBookmarkUseCase (T038).
- [X] T098 [US6] Modify `BookmarksScreen.kt` (T052) — render `DeleteFolderConfirmDialog` when `state.pendingDialog is ConfirmDeleteFolder`.

### US6 instrumented test

- [ ] T099 [US6] Create `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/BookmarksScreenDeleteTest.kt` — empty-folder delete flow (no confirm), non-empty folder delete flow (confirm dialog with correct count → confirm → atomic delete).
- [ ] T100 [US6] Manual user-device gate G4 from [quickstart.md](quickstart.md). User-marked PASS on PR.

**Checkpoint**: Full CRUD on bookmarks + folders. Cascade delete safe and atomic.

---

## Phase 9: User Story 7 — Add a bookmark manually (Priority: P3)

**Goal**: FAB on BookmarksScreen → AddOrEditBookmarkDialog in `Add(parentId)` mode.

**Independent Test**: Tap FAB → dialog appears with empty fields → fill title + URL → save → bookmark appears at current folder.

### US7 strings + UI

- [X] T101 [P] [US7] Add 2 EN keys + 7 locale translations: `bookmarks_action_add_cd`, `bookmarks_dialog_add_title`.
- [X] T102 [US7] Modify `BookmarksScreen.kt` (T052) — add `Scaffold` `floatingActionButton = { FloatingActionButton(...) }` with `Icons.Filled.Add` + `bookmarks_action_add_cd`. Tap → `viewModel::onAddBookmarkClick`.
- [X] T103 [US7] Modify `BookmarksViewModel.kt` (T046) — `onAddBookmarkClick()` sets `pendingDialog = AddBookmark(currentFolderId)`. `onDialogConfirm(AddBookmark, title, url, parentId)` calls `AddBookmarkUseCase` (T024); on validation failure emits `BookmarksErrorEvent`.
- [X] T104 [US7] Verify `AddOrEditBookmarkDialog.kt` (T085) handles `Add(parentId)` mode with empty fields and the existing folder picker selection.

### US7 instrumented test

- [ ] T105 [US7] Create `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/bookmarks/BookmarksScreenManualAddTest.kt` — tap FAB → dialog → fill valid + invalid title/URL combinations → verify each row from G6 outcome table in [quickstart.md](quickstart.md).
- [ ] T106 [US7] Manual user-device gate G6 from [quickstart.md](quickstart.md). User-marked PASS.

**Checkpoint**: All 7 user stories complete and independently testable.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Final quality gates, manual verification, doc updates, PR.

- [X] T107 [P] Verify Constitution Check 11/11 PASS post-implementation. Spot-check:
   - §III: grep new diffs for inline `Color(0x`, `dp)` repeated literals, `"http`, `Text("`, `stringPreferencesKey("`. Zero hits expected.
   - §IV: confirm `domain/` package has zero Android imports (`grep -r "import android" app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/`).
   - §VIII: confirm every new icon-button has a non-empty `contentDescription` from a string resource.
- [X] T108 [P] Run `./gradlew testDebugUnitTest` — verify all foundational + use case + ViewModel + mapper + validator + repo tests green; record total count vs Spec 012 baseline 201.
- [X] T109 [P] Run `./gradlew lintDebug detekt ktlintCheck` — zero warnings, zero violations, baseline UNCHANGED. Detekt threshold knobs (LongParameterList, LongMethod) likely require `BrowserViewModel` constructor and `BookmarksViewModel` to be checked — adjust by extracting parameter classes per Spec 008/011/012 patterns if needed; do NOT add baseline entries.
- [ ] T110 [P] Run `./gradlew connectedDebugAndroidTest` on emulator API 35 (16 KB page size enabled). All instrumented tests from US1/US2/US3/US4/US5/US6/US7 green.
- [X] T111 [P] Run `./gradlew assembleRelease` — APK size delta against Spec 012 baseline 2.1 MB. Target ≤ +80 KB (SC-008 budget 100 KB). Record actual.
- [X] T112 [P] Run the project's 16 KB CI script (`unzip -p ... | objdump -p - | grep LOAD`) — verify 8/8 native lib entries `align 2**14`. Zero new `.so` expected per [research.md R14](research.md#r14-apk-size-budget-verification).
- [ ] T113 Manual user-device gate G3 from [quickstart.md](quickstart.md) (search across folders with path inline). User-marked PASS.
- [ ] T114 Manual user-device gate G5 from [quickstart.md](quickstart.md) (cycle prevention). User-marked PASS.
- [ ] T115 Manual user-device gate G7 from [quickstart.md](quickstart.md) (incognito disables star). User-marked PASS.
- [ ] T116 Manual user-device gate G8 from [quickstart.md](quickstart.md) (8-locale visual sweep). User-marked PASS.
- [ ] T117 Manual user-device gate G9 from [quickstart.md](quickstart.md) (TalkBack accessibility). User-marked PASS.
- [ ] T117a Manual user-device gate G10 from [quickstart.md](quickstart.md) (5-level × ~50 items rendering perf — SC-007). User-marked PASS.
- [X] T118 Update `.claude/claude-app/project-context.md` — add Spec 013 entry under Recent Changes with: APK size measured, test count delta, list of files added/modified, manual gates passed.
- [X] T119 Update `.claude/claude-app/sdd-roadmap.md` — Spec 013 row → ✅ Done, with date.
- [X] T120 Update [CLAUDE.md](../../CLAUDE.md) Active Spec block: 🟡 → ✅ Spec 013 done; bump Recent Changes; suggest next = Spec 014 (`history-view`) or Spec 015 (`downloads-manager`).
- [ ] T121 Open PR `013-bookmarks-crud → main` with body: link to plan.md (no Complexity Tracking entries to cite), summary of FRs delivered, links to all 9 manual gates with PASS confirmation.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 must complete before any other task. T002 + T003 parallelizable.
- **Foundational (Phase 2)**: Depends on Setup. T004–T010 parallelizable. T011, T012, T016 parallelizable. T013 + T014 modify different DAO files — parallelizable. T015 (`BookmarkRepositoryImpl`) depends on T004–T014. T020 (repo impl test) depends on T015. T023 (fake) depends on T010. **Blocks all user stories.**
- **User Stories (Phases 3–9)**: All depend on Foundational. Within each story: use-case tasks → use-case tests in parallel; ViewModel/UI tasks sequential because they share files.
- **Polish (Phase 10)**: depends on all desired user stories.

### User Story Dependencies

- **US1 (P1) — MVP**: depends only on Foundational. Pure additive — modifies BrowserScreen/VM/UiState but those compile/test in isolation.
- **US2 (P1)**: depends only on Foundational. Uses `UpdateActiveTabUrlAndTitleUseCase` from Spec 011 (already merged). Independent of US1.
- **US3 (P2)**: depends on US2 (rewrites of `BookmarksUiState`/`ViewModel`/`Screen` need a non-placeholder version to extend).
- **US4 (P2)**: depends on US2 (extends `BookmarksUiState`/`ViewModel`/`Screen`). Independent of US3.
- **US5 (P2)**: depends on US3 (action sheets created in US3 are extended here).
- **US6 (P2)**: depends on US3 (action sheets) + US2 (`DeleteBookmarkUseCase` already added there for fast path).
- **US7 (P3)**: depends on US5 (`AddOrEditBookmarkDialog` lives there; US7 only adds the FAB entry point).

### Parallel Opportunities

- **All `[P]` tasks within a phase** can run in parallel (different files, no dependency on incomplete tasks).
- **Most foundational tests** (T017–T022) can run in parallel after their corresponding production task is done.
- **Most use-case tests** within a story can run in parallel (e.g., T039 / T040 / T041 in US2; T062 in US3 if split per file).
- **Different stories cannot easily run in parallel** because most touch `BookmarksViewModel`, `BookmarksUiState`, `BookmarksScreen` — sequential within those files. With multiple developers, US3/US4 can split: developer A on US3 (folder mgmt), developer B on US4 (search) — they merge into the same VM file but on different methods, manageable with feature flags or careful merge.

---

## Parallel Example: Phase 2 Foundational

```bash
# After T001+T002+T003, launch the domain-layer block in parallel:
Task: "T004 Create domain/model/Bookmark.kt"
Task: "T005 Create domain/model/BookmarkFolder.kt"
Task: "T006 Create domain/model/BookmarkSearchResult.kt"
Task: "T007 Create domain/model/BookmarkDescendantCount.kt"
Task: "T008 Create domain/error/BookmarkExceptions.kt"
Task: "T009 Create domain/validator/BookmarkUrlValidator.kt"
Task: "T010 Create domain/repository/BookmarkRepository.kt"

# After domain layer + DAO modifications, launch data layer:
Task: "T011 Create data/mapper/BookmarkMapper.kt"
Task: "T012 Create data/mapper/BookmarkFolderMapper.kt"
Task: "T013 Modify BookmarkDao.kt (5 new methods)"
Task: "T014 Modify BookmarkFolderDao.kt (2 new methods)"
Task: "T016 Create di/BookmarkModule.kt"
# (T015 RepositoryImpl + T023 FakeBookmarkRepository depend on the above)

# After T015+T023, launch tests in parallel:
Task: "T017 BookmarkUrlValidatorTest"
Task: "T018 BookmarkMapperTest"
Task: "T019 BookmarkFolderMapperTest"
Task: "T020 BookmarkRepositoryImplTest"
Task: "T021 BookmarkDaoSpec013Test"
Task: "T022 BookmarkFolderDaoSpec013Test"
```

---

## Implementation Strategy

### MVP First (US1 only)

1. Phase 1 (Setup) → T001–T003.
2. Phase 2 (Foundational) → T004–T023 (entire foundation; ~25 tasks).
3. Phase 3 (US1) → T024–T035 (10 tasks).
4. **STOP and demo**: bookmarks invisible to user via the bookmarks screen but star icon works; verify via `adb shell sqlite3` that data is persisted. MVP shipped.

### Incremental Delivery

After MVP:
- US2 → list visible → user-facing demo.
- US3 → folders organized.
- US4 → search.
- US5 → edit.
- US6 → delete cascade.
- US7 → manual add.

Each increment is independently testable + deployable.

### Test gate per phase

After every phase completes its production tasks, run:

```bash
./gradlew testDebugUnitTest lintDebug detekt ktlintCheck
```

Block the next phase if any of these fail. Save instrumented tests for the end-of-story checkpoint to amortize emulator boot time.

---

## Notes

- `[P]` = different file, no incomplete dependency.
- `[Story]` label maps task to user story for traceability.
- Tests are NOT optional in this spec — Constitution §VI requires unit tests for repo/use cases/ViewModels/mappers + Compose UI tests for critical flows. Tests are written by the implementer track per dev-workflow.md.
- Manual gates (T035, T056, T100, T106, T113–T117) require physical device or emulator interaction; user marks PASS on PR.
- Avoid: cross-story dependencies that would break independent testability (e.g., US4 search affordance depending on US3 folder picker — they share `BookmarksViewModel` but operate on independent state slices).
- Each user story checkpoint is a stop-and-validate gate before next story begins.
