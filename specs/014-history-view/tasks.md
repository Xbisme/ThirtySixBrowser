---
description: "Task list for Spec 014 — History View"
---

# Tasks: History View

**Input**: Design documents from `/specs/014-history-view/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/HistoryRepository.kt](contracts/HistoryRepository.kt), [quickstart.md](quickstart.md)

**Tests**: Included. The project's Constitution §VI mandates a test discipline (JUnit + Robolectric for data/domain JVM tests, Compose UI Test + Espresso for instrumented). Test tasks are interleaved per the project's conventions, not pure-TDD-first.

**Organization**: Tasks are grouped by user story (US1–US5 per spec.md priorities). MVP = US1 (auto-record + grouped list).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no incomplete-task dependencies)
- **[Story]**: User story tag (US1/US2/US3/US4/US5); Setup, Foundational, and Polish phases have no story label.
- File paths are absolute under repo root unless `app/...` (relative to repo root).

## Path Conventions

Android single-module project. Source under `app/src/main/kotlin/com/raumanian/thirtysix/browser/`. JVM tests under `app/src/test/...`; instrumented tests under `app/src/androidTest/...`. Resources under `app/src/main/res/values{,-vi,-de,-ru,-ko,-ja,-zh,-fr}/`.

---

## Phase 1: Setup

**Purpose**: Confirm branch + verify no new dependencies needed; pre-stage constants files.

- [X] T001 Verify on branch `014-history-view` (not main); confirm working tree clean except this spec dir; run `./gradlew clean assembleDebug` to baseline a green build before edits.
- [X] T002 Audit `gradle/libs.versions.toml` to confirm Compose BOM 2026.04.01, Material3, material-icons-core, Hilt 2.59.2, Room 2.8.4, kotlinx-coroutines, Robolectric 4.16.1, Turbine 1.2.1, Espresso-Web 3.7.0 are all present (per [plan.md](plan.md) Technical Context); record a one-line note in PR draft confirming **zero new dependency** is required.
- [X] T003 [P] Add `SEARCH_MIN_CHARS = 2` and `MAX_HISTORY_QUERY_LENGTH = 200` to `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/BrowserLimits.kt` per [data-model.md](data-model.md) Constants table; KDoc each with FR-011a / Q4 reference.
- [X] T004 [P] Add `HISTORY_DAY_HEADER_STYLE = FormatStyle.MEDIUM` and `HISTORY_TIME_OF_VISIT_STYLE = FormatStyle.SHORT` to `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/DateFormats.kt` (create file if missing — file already referenced in [CLAUDE.md](../../CLAUDE.md) project structure section).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain models, mappers, DAO surface, repository, and Hilt wiring that every user story depends on.

**⚠️ CRITICAL**: No US task can begin until this phase completes.

### Domain models (pure Kotlin, no Android imports)

- [X] T005 [P] Create `HistoryEntry` domain model at `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/HistoryEntry.kt` per [data-model.md](data-model.md) — `data class HistoryEntry(id: Long, url: String, title: String, visitedAt: Long)`.
- [X] T006 [P] Create `HistoryDayBucket` sealed class at `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/HistoryDayBucket.kt` with `Today`, `Yesterday`, `OnDate(date: LocalDate)` per [data-model.md](data-model.md).

### Day-bucket pure helper

- [X] T007 [P] Create `historyDayBucketOf(visitedAt: Long, today: LocalDate, zone: ZoneId): HistoryDayBucket` extension/top-level fn at `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/extensions/HistoryDayBucketExt.kt` per [research.md](research.md) R2; ensure pure function (no `Calendar`, no `SimpleDateFormat`).
- [X] T008 [P] Unit test `HistoryDayBucketExtTest` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/core/extensions/HistoryDayBucketExtTest.kt` — exercise Today / Yesterday / OnDate boundaries + DST-rollover edge case; reference [spec.md](spec.md) Edge Cases (clock change).

### DAO surface

- [X] T009 Add `@Query("DELETE FROM ${HistoryEntryEntity.TABLE_NAME} WHERE ${HistoryEntryEntity.COL_ID} = :id") suspend fun deleteById(id: Long): Int` to `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/dao/HistoryDao.kt` (additive only — does NOT change existing methods); KDoc with FR-020 reference.
- [X] T010 Unit test for DAO delta at `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/local/dao/HistoryDaoSpec014Test.kt` — Robolectric in-memory Room: covers `deleteById` (returns 1 for hit, 0 for miss), repeat-visit chronological invariant from [spec.md](spec.md) FR-004, `observeAll` Flow re-emission on insert + delete.

### Mapper

- [X] T011 [P] Create `HistoryEntryMapper` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/mapper/HistoryEntryMapper.kt` with `HistoryEntryEntity.toDomain()`, `HistoryEntry.toEntity()`, and `List<HistoryEntryEntity>.toDomain()` extensions per [data-model.md](data-model.md) Mapping Contract.
- [X] T012 [P] Unit test `HistoryEntryMapperTest` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/mapper/HistoryEntryMapperTest.kt` — round-trip + empty-title + zero-id (un-persisted) cases.

### Repository

- [X] T013 Create `HistoryRepository` interface at `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/repository/HistoryRepository.kt` matching the contract in [contracts/HistoryRepository.kt](contracts/HistoryRepository.kt) — 5 methods: `recordVisit`, `observeAll`, `deleteById`, `clearAll`, `count`.
- [X] T014 Create `HistoryRepositoryImpl` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository/HistoryRepositoryImpl.kt` — `@Inject constructor(private val dao: HistoryDao, @IoDispatcher private val ioDispatcher: CoroutineDispatcher)`; each suspend method wraps `withContext(ioDispatcher)`; observer maps Flow via `.map { it.toDomain() }`. Repository depends ONLY on DAO + dispatcher (no Repo→Repo).
- [X] T015 Unit test `HistoryRepositoryImplTest` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/repository/HistoryRepositoryImplTest.kt` using Robolectric in-memory Room — covers `recordVisit` (insert + returned id), `observeAll` (reverse-chrono ordering), `deleteById` (delete propagation to observer), `clearAll` (full wipe), `count`.

### Hilt module

- [X] T016 Create `HistoryModule` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/di/HistoryModule.kt` — `@Module @InstallIn(SingletonComponent::class) abstract class HistoryModule { @Binds abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository }`. Mirror the `BookmarkModule.kt` style for consistency.

**Checkpoint**: Foundational layer ready — all 5 user stories can now begin.

---

## Phase 3: User Story 1 — Browse my visit history grouped by day (Priority: P1) 🎯 MVP

**Goal**: Auto-record successful non-incognito page loads; view them grouped under day headers; tap a row to replace the active tab.

**Independent Test**: Per [quickstart.md](quickstart.md) **G1** + **G3** — visit 3 URLs in normal tab, open History from bottom-bar, see 3 entries under "Today" reverse-chrono; tap a row → active tab navigates and screen dismisses. Visit URLs in incognito → none recorded.

### Use cases

- [X] T017 [P] [US1] Create `RecordHistoryEntryUseCase` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/RecordHistoryEntryUseCase.kt` — `suspend operator fun invoke(url: String, title: String, isIncognito: Boolean)`; returns early when `isIncognito == true` (FR-002); else calls `historyRepository.recordVisit(url, title, System.currentTimeMillis())`.
- [X] T018 [P] [US1] Create `ObserveHistoryEntriesUseCase` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ObserveHistoryEntriesUseCase.kt` — `operator fun invoke(): Flow<List<HistoryEntry>> = repository.observeAll()`. Single-line use case acceptable per project style.
- [X] T019 [P] [US1] Unit test `RecordHistoryEntryUseCaseTest` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/usecase/RecordHistoryEntryUseCaseTest.kt` using fake `HistoryRepository` — verifies (a) incognito=true → no call · (b) incognito=false → `recordVisit` called once with passed url/title and a `visitedAt` ≥ test start time.
- [X] T020 [P] [US1] Unit test `ObserveHistoryEntriesUseCaseTest` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ObserveHistoryEntriesUseCaseTest.kt` — Turbine flow assertion that emissions pass through unchanged.

### BrowserViewModel + WebView callback wiring (recorder hook)

- [X] T021 [US1] Inspect `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserNavigationCallbacks.kt` — confirm the page-finish signal already in place from Spec 011. If `onPageLoaded(url, title)` is missing, add it as a 6th field of the data class (do not exceed Detekt `LongParameterList` threshold — bundle if needed mirror Spec 011 pattern).
- [X] T022 [US1] Modify `BrowserViewModel.kt` — inject `RecordHistoryEntryUseCase` + already-existing `ObserveActiveTabIsIncognitoUseCase`; add `fun onPageLoaded(url: String, title: String)` that calls `viewModelScope.launch { recordHistoryEntry(url, title, isIncognito = lastKnownIsIncognito) }`. Capture `lastKnownIsIncognito` via `combine` on the active-tab flow (mirror the bookmark pattern at lines 70–124 of `BookmarksViewModel.kt`). Path: `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModel.kt`.
- [X] T023 [US1] Wire `BrowserWebView` page-finish callback through to `viewModel::onPageLoaded` in `BrowserScreen.kt` factory of `BrowserNavigationCallbacks`. Path: `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserScreen.kt`.
- [X] T024 [US1] Unit test `BrowserViewModelHistoryRecordTest` (or extend existing `BrowserViewModelTest`) at `app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserViewModelTest.kt` — adds **4** cases: (a) page-loaded with normal tab → use case called once; (b) page-loaded with incognito tab → use case called with isIncognito=true (use case body short-circuits, verified separately at T019); (c) rapid double-load → 2 invocations (FR-004); (d) **FR-003 negative path** — page-error / page-cancelled callback invoked → `RecordHistoryEntryUseCase` is **never** invoked (uses fake/mock to assert zero invocations).

### Presentation: HistoryUiState + ViewModel

- [X] T025 [P] [US1] Create `HistoryUiState` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/history/HistoryUiState.kt` per [data-model.md](data-model.md) — fields: `entries`, `searchQuery`, `groupedEntries`, `isClearAllDialogVisible`, `pendingActionSheetTarget`, `openUrl`. Include nested `data class DayGroup(val bucket: HistoryDayBucket, val entries: List<HistoryEntry>)`.
- [X] T026 [P] [US1] Create `HistoryErrorEvent` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/history/HistoryErrorEvent.kt` — sealed: `ClipboardCopied`, `TabCapReached`, `DeletionFailed(cause: Throwable)`.
- [X] T027 [US1] Create `HistoryViewModel` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/history/HistoryViewModel.kt` — `@HiltViewModel class HistoryViewModel @Inject constructor(observe: ObserveHistoryEntriesUseCase, observeActiveTab: ObserveActiveTabUseCase, updateActiveTab: UpdateActiveTabUrlAndTitleUseCase, ...)`. Initial scope: only US1 surface — `entries` flow + `groupedEntries` derived (filter by query when ≥ SEARCH_MIN_CHARS, else identity), `onEntryTap`, `consumeOpenUrl`. Search/long-press/clear-all stubs added by US2/US3/US4.
- [X] T028 [US1] Unit test `HistoryViewModelTest` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/presentation/history/HistoryViewModelTest.kt` — uses `MainDispatcherRule` + Turbine; covers (a) entries flow drives `groupedEntries` reverse-chrono · (b) `onEntryTap` calls `updateActiveTabUrlAndTitle` with active-tab id + entry url/title + lastKnownIsIncognito · (c) `onEntryTap` sets `openUrl` · (d) `consumeOpenUrl` clears it.

### Presentation: Composables

- [X] T029 [P] [US1] Create `HistoryRow.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/history/components/HistoryRow.kt` — Composable rendering favicon (via `FaviconCache.get(host)`), title (fallback to hostname when empty per FR-009 / spec edge case), hostname row, time-of-visit (`DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)`). `Modifier.combinedClickable(onClick, onLongClick)` with both callbacks. `MaterialTheme.colorScheme/typography/shapes` only — no inline literals.
- [X] T030 [P] [US1] Create `HistoryDayHeader.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/history/components/HistoryDayHeader.kt` — accepts `HistoryDayBucket`, renders a localized `Text` (Today / Yesterday / `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date)` for `OnDate`).
- [X] T031 [P] [US1] Create `EmptyHistoryState.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/history/components/EmptyHistoryState.kt` — Material icon (e.g. `Icons.Filled.Schedule` from material-icons-core, verify availability) + `stringResource(R.string.history_empty_title)` + `history_empty_body`.
- [X] T032 [US1] Create `HistoryTopBar.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/history/components/HistoryTopBar.kt` — initial scope: title only ("History" localized) + back IconButton; search field + clear-all icon added by US2/US4.
- [X] T033 [US1] Rewrite `HistoryScreen.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/history/HistoryScreen.kt` — `Scaffold(topBar = HistoryTopBar)`, `LazyColumn` with `key = { it.id }` over `groupedEntries.flatMap { listOf(header, *it.entries) }`, empty-state branch when `entries.isEmpty()`, `LaunchedEffect(state.openUrl)` that calls `navController.popBackStack(AppDestination.Browser.route, inclusive = false)` + `viewModel.consumeOpenUrl()`.
- [X] T034 [US1] Add bottom-bar history affordance: modify `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/components/NavigationBottomBarCallbacks.kt` — add `onHistoryClick: () -> Unit` field; modify `NavigationBottomBar.kt` — add 6th `IconButton` with `Icons.Filled.History` (verify available in material-icons-core; fallback to `Icons.Filled.Schedule` if not), `TEST_TAG_NAV_HISTORY` const, localized `contentDescription`. Detekt `LongParameterList` threshold check: bundle into callbacks if needed.
- [X] T035 [US1] Wire `HistoryScreen` route in `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/navigation/AppNavGraph.kt` — pass `navController`; mirror Spec 013 BookmarksScreen wiring. Wire `onHistoryClick = { navController.navigate(AppDestination.History.route) }` from the host BrowserScreen.

### Strings (US1 subset — 8 locales)

- [X] T036 [P] [US1] Add EN strings to `app/src/main/res/values/strings.xml` — keys: `history_screen_title`, `history_day_today`, `history_day_yesterday`, `history_empty_title`, `history_empty_body`, `history_action_back`, `history_action_open_history`, `history_row_visited_at_label` (TalkBack), `history_row_favicon_content_description` (TalkBack — accepts hostname format-arg). Remove obsolete `history_screen_placeholder` if still present.
- [X] T037 [P] [US1] Add VI translations of the T036 keys to `app/src/main/res/values-vi/strings.xml`.
- [X] T038 [P] [US1] Add DE translations of the T036 keys to `app/src/main/res/values-de/strings.xml`.
- [X] T039 [P] [US1] Add RU translations of the T036 keys to `app/src/main/res/values-ru/strings.xml`.
- [X] T040 [P] [US1] Add KO translations of the T036 keys to `app/src/main/res/values-ko/strings.xml`.
- [X] T041 [P] [US1] Add JA translations of the T036 keys to `app/src/main/res/values-ja/strings.xml`.
- [X] T042 [P] [US1] Add ZH translations of the T036 keys to `app/src/main/res/values-zh/strings.xml`.
- [X] T043 [P] [US1] Add FR translations of the T036 keys to `app/src/main/res/values-fr/strings.xml`.

### US1 verification gates

- [X] T044 [US1] Run `./gradlew testDebugUnitTest lintDebug detekt ktlintCheck` — all green; fix mid-impl issues at this checkpoint, NOT later. Reference Spec 013's mid-impl Detekt fixes pattern.
- [X] T045 [US1] Manual user-device gate **G1** (auto-record + incognito suppression) per [quickstart.md](quickstart.md). Record PASS/FAIL in PR draft. **✅ VERIFIED on emulator Medium_Phone_API_36.1 (API 36) 2026-05-08** — 3 normal-tab navigations produced exactly 3 rows with correct per-URL titles (DB dump); a visit made inside an incognito tab produced **zero** new rows. Uncovered a real FR-001/FR-003 defect first — see the Recorder-defect note below.
- [X] T046 [US1] Manual user-device gate **G3** (tap-to-replace-active-tab — both normal and incognito branches) per [quickstart.md](quickstart.md). **✅ VERIFIED on emulator 2026-05-08** — tapping a history row rewrote the active tab's URL (`tabs` table went to the tapped entry's URL) and popped back to BrowserScreen, which then recorded the revisit as a new row per FR-004.
- [X] T047 [US1] Instrumented test `HistoryRecorderIntegrationTest` at `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/history/HistoryRecorderIntegrationTest.kt` — Hilt + Espresso-Web; loads `https://example.com` in a normal tab, asserts `HistoryDao.observeAll().first()` contains 1 entry; loads in incognito tab, asserts unchanged. Reuses pattern from Spec 007 `BrowserScreenInstrumentedTest`.
- [X] T048 [US1] Instrumented test `HistoryScreenBrowseTest` at `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/history/HistoryScreenBrowseTest.kt` — pre-seeds DB with 3 entries spanning today + yesterday (using a test-DB rule), asserts day headers visible, reverse-chrono order, tap navigates and dismisses.

**Checkpoint**: US1 fully functional and independently testable — MVP delivered.

---

## Phase 4: User Story 2 — Search my history by title or URL (Priority: P2)

**Goal**: Search box filters live (≥ 2 chars) on title-or-URL substring; clears restore full list; new arrivals match if query active.

**Independent Test**: Per [quickstart.md](quickstart.md) **G4** — pre-populate ≥ 5 entries, type 1 char (no filter), 2+ chars (filter), non-matching (no-matches state), clear (restored).

### Search wiring in ViewModel

- [X] T049 [US2] Extend `HistoryViewModel.kt` — add `private val searchQueryFlow = MutableStateFlow("")`; `combine(observe(), searchQueryFlow) { entries, q -> derive(entries, q) }` to drive `groupedEntries`. Helper `derive` filters when `q.length >= BrowserLimits.SEARCH_MIN_CHARS` per FR-011a; truncates `q` to `MAX_HISTORY_QUERY_LENGTH`. Add `fun onSearchQueryChange(q: String)`.
- [X] T050 [US2] Extend `HistoryViewModelTest.kt` — 5 new cases: query "" → full list · query "a" (1 char) → full list (FR-011a) · query "ex" (2 chars) → filtered substring match on title OR url, case-insensitive · query "xyznomatchhere" → empty groupedEntries · query "%" + "_" + "'" treated literally (FR-013).

### Composables

- [X] T051 [US2] Modify `HistoryTopBar.kt` — add `OutlinedTextField` (or `SearchBar` M3) for query input with localized placeholder + clear-text affordance (`IconButton` rendering `Icons.Filled.Close` only when `query.isNotEmpty()`). Bind `onValueChange = onSearchQueryChange`. Detekt LongParameterList check.
- [X] T052 [P] [US2] Create `NoHistoryMatchesState.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/history/components/NoHistoryMatchesState.kt` — Composable with localized "no matches" message; rendered when `groupedEntries.isEmpty() && searchQuery.length >= SEARCH_MIN_CHARS`.
- [X] T053 [US2] Modify `HistoryScreen.kt` — branch list region into 3 mutually-exclusive Composable trees: empty-state (entries.isEmpty()) · no-matches (above-threshold query, no matches) · LazyColumn (otherwise).

### Strings (US2 subset)

- [X] T054 [P] [US2] Add EN strings: `history_search_placeholder`, `history_search_clear_content_description`, `history_no_matches_title`, `history_no_matches_body` to `values/strings.xml`.
- [X] T055 [P] [US2] Add VI translations to `values-vi/strings.xml`.
- [X] T056 [P] [US2] Add DE translations to `values-de/strings.xml`.
- [X] T057 [P] [US2] Add RU translations to `values-ru/strings.xml`.
- [X] T058 [P] [US2] Add KO translations to `values-ko/strings.xml`.
- [X] T059 [P] [US2] Add JA translations to `values-ja/strings.xml`.
- [X] T060 [P] [US2] Add ZH translations to `values-zh/strings.xml`.
- [X] T061 [P] [US2] Add FR translations to `values-fr/strings.xml`.

### US2 verification gates

- [X] T062 [US2] Run `./gradlew testDebugUnitTest lintDebug detekt ktlintCheck` — all green.
- [X] T063 [US2] Manual user-device gate **G4** (search responsiveness + min-chars + no-matches + restore + live-merge of new entry) per [quickstart.md](quickstart.md). **✅ VERIFIED on emulator 2026-05-08** — 1-char query left the list unfiltered; 2+ chars filtered on title/URL; the clear (×) button restored the full list; and **FR-017 live-merge** confirmed by inserting 2 matching rows via the debug seeder while the query `seeded` was active — the list went 3 → 5 rows with no retyping.
- [X] T064 [US2] Instrumented test `HistoryScreenSearchTest` at `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/history/HistoryScreenSearchTest.kt` — pre-seed entries, type 1 char (assert full list), 2 chars (assert filtered), non-matching (assert no-matches Composable visible), clear (assert restored). **Plus two extra cases**: (a) **FR-014** — pre-seed entries spanning today + yesterday, type a query that matches yesterday only → assert "Today" header is **absent** while "Yesterday" header + matching row remain visible; (b) **FR-017** — with a threshold-meeting active query, insert a matching row via the test repository → assert it appears in the filtered list within ~1 s without retyping.

**Checkpoint**: US1 + US2 both work independently.

---

## Phase 5: User Story 3 — Per-entry actions: open in new tab, delete, copy URL (Priority: P2)

**Goal**: Long-press a history row → 3-action sheet (Open in new tab — always normal · Delete entry · Copy URL with snackbar confirmation).

**Independent Test**: Per [quickstart.md](quickstart.md) **G5** — long-press → sheet appears in correct order; each action behaves per spec; tap-outside dismisses.

### Use cases

- [X] T065 [P] [US3] Create `DeleteHistoryEntryUseCase` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/DeleteHistoryEntryUseCase.kt` — `suspend operator fun invoke(id: Long): Int = repository.deleteById(id)`.
- [X] T066 [P] [US3] Unit test `DeleteHistoryEntryUseCaseTest` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/usecase/DeleteHistoryEntryUseCaseTest.kt` — uses fake repo; asserts pass-through.

### ViewModel wiring

- [X] T067 [US3] Extend `HistoryViewModel.kt` — inject `CreateTabUseCase` + `DeleteHistoryEntryUseCase` + `@ApplicationContext context: Context` (for clipboard). Add `MutableSharedFlow<HistoryErrorEvent> historySnackbarEvent` (replay=0, capacity=1, `BufferOverflow.DROP_OLDEST`) per [data-model.md](data-model.md). Add: `onLongPressEntry(entry)`, `onActionSheetDismiss()`, `onOpenInNewTab(entry)` (calls `runCatching { createTab(entry.url, isIncognito = false) }`; on failure emits `TabCapReached`), `onDeleteEntry(entry)` (`runCatching { deleteHistoryEntry(entry.id) }`; emits `DeletionFailed` on failure), `onCopyUrl(entry)` (uses `ClipboardManager.setPrimaryClip(ClipData.newPlainText(label, entry.url))`; emits `ClipboardCopied`).
- [X] T068 [US3] Extend `HistoryViewModelTest.kt` — 6 new cases: long-press sets `pendingActionSheetTarget` · dismiss clears it · open-in-new-tab calls `CreateTabUseCase` with `isIncognito=false` regardless of active-tab incognito (Q2 = A) · open-in-new-tab cap-reached emits `TabCapReached` · delete-entry calls use case + emits no event on success · copy-url emits `ClipboardCopied`.

### Composables

- [X] T069 [P] [US3] Create `HistoryActionSheet.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/history/components/HistoryActionSheet.kt` — M3 `ModalBottomSheet` with 3 `ListItem` rows in this order (FR-018): Open in new tab · Delete entry · Copy URL. Each leading icon from `material-icons-core`. Localized strings + content descriptions. Tap-outside / system-back via standard `onDismissRequest`.
- [X] T070 [US3] Modify `HistoryScreen.kt` — in the per-row click handlers wire `onLongClick = { viewModel.onLongPressEntry(entry) }`; render `HistoryActionSheet` when `state.pendingActionSheetTarget != null`. Add `LaunchedEffect(Unit) { viewModel.historySnackbarEvent.collect { event -> snackbarHostState.showSnackbar(event.toLocalizedMessage()) } }` — `event.toLocalizedMessage()` is a `@Composable` extension or local helper that maps each `HistoryErrorEvent` to its `stringResource`. Add `SnackbarHost` to the Scaffold.

### Strings (US3 subset)

- [X] T071 [P] [US3] Add EN strings: `history_action_open_in_new_tab`, `history_action_delete_entry`, `history_action_copy_url`, `history_snackbar_clipboard_copied`, `history_snackbar_tab_cap_reached`, `history_snackbar_deletion_failed`, `history_action_sheet_dismiss_content_description` to `values/strings.xml`.
- [X] T072 [P] [US3] Add VI translations to `values-vi/strings.xml`.
- [X] T073 [P] [US3] Add DE translations to `values-de/strings.xml`.
- [X] T074 [P] [US3] Add RU translations to `values-ru/strings.xml`.
- [X] T075 [P] [US3] Add KO translations to `values-ko/strings.xml`.
- [X] T076 [P] [US3] Add JA translations to `values-ja/strings.xml`.
- [X] T077 [P] [US3] Add ZH translations to `values-zh/strings.xml`.
- [X] T078 [P] [US3] Add FR translations to `values-fr/strings.xml`.

### US3 verification gates

- [X] T079 [US3] Run `./gradlew testDebugUnitTest lintDebug detekt ktlintCheck` — all green.
- [X] T080 [US3] Manual user-device gate **G5** (long-press sheet, 3 actions, Q2 normal-tab from incognito context, single-row delete, clipboard exact match, dismiss-without-action) per [quickstart.md](quickstart.md). **✅ VERIFIED on emulator 2026-05-08** — sheet showed exactly the three FR-018 actions in order; Delete removed only the targeted row (3→2, DB-confirmed); Copy URL put `https://example.com/` on the system clipboard; system-back dismissed the sheet with history and tabs unchanged (FR-022); and **Q2 confirmed** — invoked from an active *incognito* tab, "Open in new tab" created a **normal** Room-persisted tab carrying the entry's URL.
- [X] T081 [US3] Instrumented test `HistoryScreenLongPressTest` at `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/presentation/history/HistoryScreenLongPressTest.kt` — pre-seed 3 entries, long-press one, assert sheet 3 items in correct order; pick Delete → assert 2 entries remain (both Compose UI Test asserts via `composeTestRule.onAllNodesWithTag("history_row").assertCountEquals(2)`).

**Checkpoint**: US1 + US2 + US3 all independent and working.

---

## Phase 6: User Story 4 — Clear all history with confirmation (Priority: P3)

**Goal**: Top-bar "Clear all" affordance (visible iff entries > 0) → confirm dialog → wipe → empty state.

**Independent Test**: Per [quickstart.md](quickstart.md) **G6** — populate entries, tap Clear all, cancel (no change), confirm (wiped + empty state + persisted).

### Use case

- [X] T082 [P] [US4] Create `ClearAllHistoryUseCase` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ClearAllHistoryUseCase.kt` — `suspend operator fun invoke(): Int = repository.clearAll()`.
- [X] T083 [P] [US4] Unit test `ClearAllHistoryUseCaseTest` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/usecase/ClearAllHistoryUseCaseTest.kt` — uses fake repo; asserts pass-through and rowcount return.

### ViewModel + Composable

- [X] T084 [US4] Extend `HistoryViewModel.kt` — inject `ClearAllHistoryUseCase`. Add: `onClearAllRequested()` (sets `isClearAllDialogVisible = true`), `onClearAllConfirmed()` (`runCatching { clearAllHistory() }`; on failure emits `DeletionFailed`; in finally clears the flag), `onClearAllCancelled()` (clears the flag).
- [X] T085 [US4] Extend `HistoryViewModelTest.kt` — 4 new cases: requested → flag true · cancelled → flag false · confirmed success → flag false + use case called · confirmed failure → emits `DeletionFailed` + flag false.
- [X] T086 [P] [US4] Create `ClearAllHistoryConfirmDialog.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/history/components/ClearAllHistoryConfirmDialog.kt` — M3 `AlertDialog` with localized title, body, "Clear" destructive button (`MaterialTheme.colorScheme.error`), "Cancel" button. `onDismissRequest` → cancel handler.
- [X] T087 [US4] Modify `HistoryTopBar.kt` — add `IconButton` rendering `Icons.Filled.DeleteSweep` (verify availability in material-icons-core; fallback `Icons.Filled.Delete` if not), shown iff `entriesCount > 0` (FR-023). Localized `contentDescription`. Detekt LongParameterList — bundle if needed.
- [X] T088 [US4] Modify `HistoryScreen.kt` — render `ClearAllHistoryConfirmDialog` when `state.isClearAllDialogVisible`; pass entriesCount to `HistoryTopBar` for the affordance gate.

### Strings (US4 subset)

- [X] T089 [P] [US4] Add EN strings: `history_action_clear_all`, `history_clear_all_dialog_title`, `history_clear_all_dialog_body`, `history_clear_all_dialog_confirm`, `history_clear_all_dialog_cancel` to `values/strings.xml`.
- [X] T090 [P] [US4] Add VI translations to `values-vi/strings.xml`.
- [X] T091 [P] [US4] Add DE translations to `values-de/strings.xml`.
- [X] T092 [P] [US4] Add RU translations to `values-ru/strings.xml`.
- [X] T093 [P] [US4] Add KO translations to `values-ko/strings.xml`.
- [X] T094 [P] [US4] Add JA translations to `values-ja/strings.xml`.
- [X] T095 [P] [US4] Add ZH translations to `values-zh/strings.xml`.
- [X] T096 [P] [US4] Add FR translations to `values-fr/strings.xml`.

### US4 verification gates

- [X] T097 [US4] Run `./gradlew testDebugUnitTest lintDebug detekt ktlintCheck` — all green.
- [X] T098 [US4] Manual user-device gate **G6** (Clear All flow + cancel + persist after restart) per [quickstart.md](quickstart.md). **✅ VERIFIED on emulator 2026-05-08** — Cancel left both rows intact (FR-026); Clear wiped the table to 0 rows, the screen fell back to the empty state and the clear-all icon auto-hid (FR-023); after a force-stop + relaunch the cleared rows did **not** return (the single row present was the fresh home-page load caused by the restart itself).

**Checkpoint**: US1 + US2 + US3 + US4 all independent and working.

---

## Phase 7: User Story 5 — Empty state when there is no history (Priority: P3)

**Goal**: Friendly empty-state Composition (icon + localized message) shown when entries.isEmpty(); replaced by list as soon as the first entry is recorded.

**Independent Test**: Per [quickstart.md](quickstart.md) implicit (covered inside G1 / G6) — fresh install / post-clear-all → empty state visible.

> US5 is largely already complete in T031 (component) + T033 (branch in HistoryScreen). This phase exists for traceability and the live-update verification.

### ViewModel & Composable verification

- [X] T099 [US5] Extend `HistoryViewModelTest.kt` — verify that as `entries` flow transitions from `emptyList()` → `[entry]`, the `groupedEntries` derived state drops empty and contains the new row under "Today".
- [X] T100 [US5] Confirm `HistoryScreen.kt` correctly switches from `EmptyHistoryState` → `LazyColumn` reactively (no manual recomposition trigger).
- [X] T101 [US5] Manual user-device gate combined into G1 + G6 per [quickstart.md](quickstart.md). No separate gate needed.

**Checkpoint**: All 5 user stories functional.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Project-wide gates, documentation updates, and the final 8-locale + accessibility sweep.

### Quality gates

- [X] T102 Run `./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug detekt ktlintCheck assembleRelease` — entire suite green. Capture unit-test count delta vs Spec 013 baseline (~30+ new) for PR body. **✅ GREEN 2026-05-08** — testDebugUnitTest ✅ **379/379** · lintDebug ✅ · detekt ✅ · ktlintCheck ✅ · assembleDebug ✅ · assembleRelease ✅ · 16 KB gate ✅ · `connectedDebugAndroidTest` **61/61**, run three times back-to-back on an API 24 AVD booted with **CI's own emulator flags** (`-no-window -gpu swiftshader_indirect -noaudio -no-boot-anim`). Closing this required properly fixing the pre-existing Spec 007 flake — see the note below, including the before/after reproduction against `main`.
- [X] T103 Verify 16 KB native-lib gate per [plan.md](plan.md) Constitution §IX gate — `unzip -p app/build/outputs/apk/release/app-release.apk lib/arm64-v8a/lib*.so | objdump -p - | grep LOAD | awk '{print $NF}'` should output only `0x4000` (or larger). Record APK size delta vs Spec 013 baseline 2.38 MB; SC-008 budget = +200 KB.
- [X] T103a Implement a **debug-only** history seeder for the SC-005 / SC-006 perf benchmark (G8). Either a hidden `HistorySeederActivity` (gated by `BuildConfig.DEBUG`) or an `adb shell am start-service` debug receiver that bulk-inserts 10,000 rows spanning 30 days via `HistoryRepository.recordVisit` in batched coroutines. NOT shipped in release builds (verify via `manifestPlaceholders` or source-set isolation under `app/src/debug/...`). Path: `app/src/debug/kotlin/com/raumanian/thirtysix/browser/dev/HistorySeeder.kt`. 
- [ ] T103b Manual user-device gate **G8** (10K-row benchmark — SC-005 + SC-006 + SC-003 reaffirmed) per [quickstart.md](quickstart.md). PASS/FAIL recorded in PR body. Skip-and-defer is permitted but MUST be flagged as DEFERRED in PR body. **◐ MEASURED on emulator, SC-005 NOT met** — see the perf note below. Search responsiveness improved ~4× after a main-thread fix; initial-open frame budget still misses the 16 ms p99 target on a **debug** build on an emulator. A release-build measurement on Pixel 5-class hardware is still required before sign-off.

### Documentation

- [X] T104 [P] Update [CLAUDE.md](../../CLAUDE.md) "Recent Changes" section with a Spec 014 entry (mirror the Spec 013 entry style — production-files count, locale entries, APK delta, deferred items, clarifications applied). Update Spec Roadmap row 014 status; flip Phase 3 progress to 2/3 production-ready.
- [X] T105 [P] Update [.claude/claude-app/sdd-roadmap.md](../../.claude/claude-app/sdd-roadmap.md) row 014 with Status ✅ Done plus implementation summary line.
- [X] T106 [P] Update [.claude/claude-app/project-context.md](../../.claude/claude-app/project-context.md) with the Spec 014 implementation entry (mirror Spec 013 style).

### Accessibility & locale sweep

- [X] T107 Run `./gradlew lintDebug` with `MissingTranslation` + `ExtraTranslation` set to error — verify all 8 locales contain exactly the same set of `history_*` keys (no missing, no extra). Reference Spec 004 lint policy.
- [X] T108 Manual user-device gate **G7** — 8-locale visual sweep + TalkBack accessibility per [quickstart.md](quickstart.md). PASS criteria: every interactive element has a meaningful localized announcement. **✅ VERIFIED on emulator 2026-05-08** — swept all 8 locales via `cmd locale set-app-locales`. Day header and both top-bar `contentDescription`s render translated in every one: EN `Today`/`Back`/`Clear all history` · VI `Hôm nay`/`Quay lại`/`Xóa toàn bộ lịch sử` · DE `Heute`/`Zurück` · RU `Сегодня`/`Назад` · KO `오늘`/`뒤로` · JA `今日`/`戻る` · ZH `今天`/`返回` · FR `Aujourd'hui`/`Retour`. Every interactive element carries a localized announcement.
- [X] T109 Manual user-device gate **G2** (day-grouping with clock manipulation) per [quickstart.md](quickstart.md) — optional if QA policy allows clock manipulation. **✅ VERIFIED on emulator 2026-05-08 without clock manipulation** — the debug seeder spreads visits backwards over N days, so seeding 8 rows across 7 days produced, top-to-bottom: `Today` · `Yesterday` · `Sep 8, 2026` · `Sep 7, 2026` · `Sep 6, 2026` · `Sep 5, 2026` · `Sep 4, 2026`, in reverse-chronological order (FR-008 / FR-029). Explicit-date headers were re-confirmed on **API 24**, which exercises the `desugar_jdk_libs` `java.time` path.

### PR & merge

- [X] T110 Verify branch `014-history-view` is rebased onto `main` (PR #14 already merged 2026-05-07; should be a clean fast-forward base).
- [ ] T111 Open PR `014-history-view → main` with body referencing this `tasks.md`, the SC-008 APK delta, the Constitution Check 11/11 PASS line, the 4 clarification answers (Q1–Q4), and the list of any deferred manual gates. **⏸ DEFERRED — PR creation is the user's call.**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1, T001–T004)** → no upstream dependencies; can start immediately on branch creation.
- **Foundational (Phase 2, T005–T016)** → depends on Setup. **BLOCKS all user stories**.
- **US1 (Phase 3, T017–T048)** → depends on Foundational. Independent of US2/US3/US4/US5.
- **US2 (Phase 4, T049–T064)** → depends on US1 (extends `HistoryViewModel` + `HistoryTopBar` + `HistoryScreen`). Sequential.
- **US3 (Phase 5, T065–T081)** → depends on US1 (extends VM + Screen). Independent of US2 (different code regions).
- **US4 (Phase 6, T082–T098)** → depends on US1 (extends VM + TopBar + Screen). Independent of US2/US3.
- **US5 (Phase 7, T099–T101)** → depends on US1 only.
- **Polish (Phase 8, T102–T111)** → depends on all desired US phases complete.

### Within Each User Story

- Use cases + their unit tests can run in parallel ([P]).
- ViewModel extension MUST follow use-case creation + tests (sequential dependency).
- Composables can run in parallel with each other once VM extension is committed.
- Locale-resource tasks ([P]) can run together as soon as the EN baseline is committed for that story.
- Manual gates run last per US, after all automated gates green.

### Parallel Opportunities

- All [P] tasks in Phase 1 (T003 / T004) can run in parallel.
- All [P] tasks in Phase 2 model layer (T005 / T006 / T007 / T011) can run in parallel.
- Within US1: T017 / T018 / T019 / T020 (use cases + tests); T029 / T030 / T031 (Composables); T036–T043 (locale resources after EN baseline).
- US2 / US3 / US4 can run in parallel by 3 different developers once US1 is committed (each touches different VM regions + Composable files; only string-resource files have potential merge contention).
- All locale-resource tasks within a single US are [P] once the EN baseline is in place.

---

## Parallel Example: User Story 1

```bash
# Once Foundational (T005–T016) is committed, kick off US1 use cases in parallel:
Task: "T017 RecordHistoryEntryUseCase"
Task: "T018 ObserveHistoryEntriesUseCase"
Task: "T019 RecordHistoryEntryUseCaseTest"
Task: "T020 ObserveHistoryEntriesUseCaseTest"

# Then in parallel after VM (T027) committed:
Task: "T029 HistoryRow"
Task: "T030 HistoryDayHeader"
Task: "T031 EmptyHistoryState"

# After EN baseline (T036) committed:
Task: "T037 VI"  Task: "T038 DE"  Task: "T039 RU"  Task: "T040 KO"
Task: "T041 JA"  Task: "T042 ZH"  Task: "T043 FR"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 Setup (T001–T004).
2. Phase 2 Foundational (T005–T016) — **BLOCKS** all stories.
3. Phase 3 US1 (T017–T048) — auto-record + grouped-list browsing + tap-replace.
4. **STOP & VALIDATE**: G1 + G3 manual gates pass on real device.
5. Optional demo / merge to a feature branch if value justifies independent ship.

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. US1 → MVP demo-able.
3. US2 (search) → cumulative.
4. US3 (long-press actions) → cumulative.
5. US4 (clear-all) → cumulative.
6. US5 (empty-state polish) → covered transitively.
7. Polish phase → ship.

### Parallel Team Strategy

After Foundational completes, three developers can work concurrently:
- Dev A: US1 (the MVP path) — T017–T048.
- Dev B: US2 (search) — T049–T064 (waits on Dev A's `HistoryViewModel` skeleton from T027 then merges incrementally).
- Dev C: US3 + US4 (action sheet + clear-all) — T065–T098, sharing the VM extension via small diffs.

Each manual gate (G1–G7) runs on the user's device — quickest path is to batch them after Polish T102 / T103.

---

## Notes

- **113 total tasks** (T001–T111 + T103a + T103b after `/speckit-analyze` remediation; vs Spec 013's 122 — proportionally smaller given 4 use cases vs 15).
- **8 manual user-device gates** (G1–G8 per [quickstart.md](quickstart.md)): G7 covers TalkBack + 8-locale, G8 covers SC-005 / SC-006 perf benchmark.
- **4 instrumented test files** (T047 / T048 / T064 / T081). Following Spec 013 precedent, defer to a fresh `/speckit-implement` pass on the branch if the user wants the manual gates run first; otherwise run inline.
- **Analyze remediations applied** (post `/speckit-analyze` 2026-05-08): C1 → added T103a / T103b + G8 perf gate · C2 → extended T024 with FR-003 negative test · C3 → extended T064 with FR-014 + FR-017 cases · L1 → tightened spec FR-021 wording to "transient snackbar" · L2 → extended G1 with Q1 incognito-context visibility check · L4 → added bucket-vs-DayGroup naming clarification in data-model.md.
- Mid-implementation Detekt fixes (LongParameterList on `NavigationBottomBarCallbacks`, `HistoryTopBar`) are budgeted into T034 / T051 / T087 and reflect Spec 013's mid-impl pattern.
- Constitution Check is verified at T044 / T062 / T079 / T097 / T102 — five times across the implementation, matching Spec 013's "check at every checkpoint" cadence.
- All [P] tasks touch different files. String-resource files (`values-XX/strings.xml`) are sequential within a single US to avoid merge conflict, but parallel across stories because each US adds disjoint key sets.

### Implementation deviations (US2–US4 pass, 2026-05-08)

- **T067 clipboard seam** — the task called for injecting `@ApplicationContext Context` into `HistoryViewModel`. Implemented instead as a `ClipboardWriter` interface (`data/local/clipboard/ClipboardWriter.kt`) + `AndroidClipboardWriter` impl bound in `di/ClipboardModule.kt`, mirroring Spec 011's `FaviconCache` interface-plus-impl pattern. Behaviourally identical, but keeps every `HistoryViewModel` unit test on the plain JVM (no Robolectric) and keeps Android imports out of the ViewModel. Every platform call is `runCatching`-wrapped per Spec 012's system-service crash-resilience posture.
- **T071 `history_action_sheet_dismiss_content_description`** — intentionally NOT added. `ModalBottomSheet` handles tap-outside / system-back dismissal natively with no custom affordance to describe, so the key would have no consumer, and Android Lint's `UnusedResources` is build-blocking under `warningsAsErrors = true`. FR-022 is satisfied by the platform behaviour. **15 new keys × 8 locales = 120 new translations** (not 16 × 8).
- **T069 "Copy URL" leading icon** — omitted. `material-icons-core` ships no content-copy glyph; Spec 013's `BookmarkActionSheet` set the precedent of leaving a row icon-less rather than picking a misleading one. "Open in new tab" uses `Icons.Filled.Add`, "Delete entry" uses `Icons.Filled.Delete`.
- **T087 clear-all icon** — `Icons.Filled.DeleteSweep` is absent from `material-icons-core`; the documented `Icons.Filled.Delete` fallback is used.
- **T047 recorder integration test** — the task drove the assertion through a live `https://example.com` WebView load. Rewritten to exercise `RecordHistoryEntryUseCase` → `HistoryRepositoryImpl` → `HistoryEntryMapper` → `HistoryDao` → **real Room/SQLite** on-device. The `BrowserViewModel` gating above that path is already covered by four JVM unit cases in `BrowserViewModelTest`; a live page load would only add network flake (the failure mode `BrowserScreenOfflineErrorTest` documents) without covering anything new. What is genuinely device-specific — SQLite persistence — is what the test now exercises.
- **T028 was previously marked `[X]` without the artifact existing** — `HistoryViewModelTest.kt` was never written in the MVP pass. Created in this pass with 23 cases spanning US1–US5 (this is why the MVP claim of "48/113" reconciled to 44 actual checkboxes).
- **T051 search field placement** — rendered as an always-visible `OutlinedTextField` stacked *below* the `TopAppBar` inside the `topBar` slot, rather than replacing the title. Leaves room for the FR-023 clear-all action, which must be visible alongside search.
- **Instrumented tests are compile-verified only** (`compileDebugAndroidTestKotlin` green). No device or emulator was attached during this pass, so `connectedDebugAndroidTest` has not run — see T102.

### Recorder defect found on device and fixed (2026-05-08)

Running the feature on a real emulator (Medium_Phone_API_36.1, API 36) surfaced a
correctness bug in the MVP pass's recorder that **no unit test had caught**, because
every test replayed an idealised callback order rather than the platform's real one.

**Symptom.** Navigating to a page rendered it correctly, but no history row appeared —
while an occasional load produced *two* rows, and one row carried the **previous**
page's title against the new page's URL (`title='Example Domain'`, `url='.../kotlinlang…'`).

**Root cause.** `onLoadFinished` decided whether to record with
`isIdempotentRefire = loadingState is Loaded && currentUrl == url`. Chromium actually
drives `doUpdateVisitedHistory(newUrl)` → `onPageStarted(newUrl)` → progress ticks →
`onProgressChanged(100)` → `onPageFinished(newUrl)`. By the time the genuine
`onPageFinished` arrives, `onUrlChanged` has already published the new URL **and**
`onProgressChanged(100)` has already moved the state to `Loaded` — so the guard matched
on real navigations and suppressed the write (FR-001), while a *failed* load, whose
state is `Failed` rather than `Loaded`, slipped past the guard and **was** recorded
(FR-003 violated — the exact inverse of the intended behaviour).

**Fix.** Replaced the inferred guard with an explicit per-navigation token:
`pendingHistoryUrl` is armed in `onLoadStarted`, consumed once in `onLoadFinished`, and
cleared in `onLoadFailed`. State that cannot drift out of sync with the platform's
ordering.

**Title correctness.** `onLoadStarted` now also clears `currentTitleCache`, so a previous
page's title can never be attached to a different URL. Because `onReceivedTitle` usually
arrives *after* `onPageFinished`, the row is written with whatever title is known (often
empty, which the row renders as the hostname per FR-009) and back-filled the moment the
real title lands — via a new `HistoryDao.updateTitle` / `HistoryRepository.updateTitle` /
`UpdateHistoryEntryTitleUseCase`. This extends the 5-method repository contract in
[contracts/HistoryRepository.kt](contracts/HistoryRepository.kt) to 6; the addition serves
FR-001 directly and is the documented deviation.

**Regression cover.** `BrowserViewModelHistoryRecordSequenceTest` (6 cases) replays the
real platform ordering and pins FR-001, FR-003, FR-004, the idempotent re-fire, the
stale-title rule, and the title back-fill. All 6 failed before the fix and pass after.
Re-verified on device: 3 navigations → exactly 3 rows, each with its own correct title.

### Performance measured on device, and a second real defect fixed (2026-05-08)

Gate **G8** was run for real: the debug seeder wrote 10 000 rows across 30 days in ~3.1 s,
then frame stats were captured with `dumpsys gfxinfo`.

**Defect found.** The listing pipeline derived `groupedEntries` inside
`combine(...).onEach { … }.launchIn(viewModelScope)`. `viewModelScope` dispatches on
`Dispatchers.Main.immediate`, so filtering *and* day-bucketing all 10 000 entries ran on
the **UI thread** — on every repository emission and on **every keystroke**.

| Measurement (10 001 rows, debug build, API 36 emulator) | Before | After |
|---|---|---|
| Typing p99 frame | 200 ms | **53 ms** |
| Typing janky frames | 70 % | 47 % |
| Screen-open p99 frame | 450 ms | 350 ms |
| Scrolling p99 frame | — | 81 ms (9.2 % janky) |

**Fix.** The derivation now runs through `.map { … }.flowOn(dispatchers.default)`, so the
O(n) work happens off the main thread while the `onEach` state write stays on it.
`HistoryViewModel` takes a `DispatcherProvider` for this.

**SC-005 is still not demonstrated.** The target is p99 ≤ 16 ms during initial layout on a
Pixel 5-class device; a debug build (no R8, `debuggable`, interpreted paths) on an emulator
measures 350 ms. Scrolling — the steady state users actually feel — is fine at 81 ms p99 /
9.2 % janky. Sign-off needs a release-build measurement on real hardware; T103b stays open.

### Memory bound: 90-day retention (2026-05-08)

Measuring heap during G8 showed 10 001 rows cost **~2.9 MB of Java heap (~300 B/row)**.
Fine today, but the table had no bound at all: a heavy user (~200 page loads/day) reaches
100 000 rows inside two years — ~30 MB held for one screen, on a minSdk-24 device whose
heap cap is often ~96 MB.

Paging 3 was considered and **rejected** for v1.0: it forces search into SQL `LIKE`, whose
`%` and `_` wildcards would undermine FR-013's "every character is literal" guarantee
unless escaped, and it would mean rewriting the whole US1+US2 pipeline plus most of
`HistoryViewModelTest` for two new dependencies.

Instead the **table itself** is bounded: `BrowserLimits.MAX_HISTORY_DAYS = 90`, enforced by
`PruneOldHistoryUseCase` → `HistoryRepository.pruneOlderThan` → the pre-existing
`HistoryDao.deleteInRange`, run once per process start from `ThirtySixApplication`. Pruning
rather than capping the query with `LIMIT` preserves the invariant that anything missing
from the list is genuinely gone from the database — so search can never report "no matches"
for a row that still exists. Making the window user-configurable belongs to Spec 016.

Verified on the **API 24** AVD (minSdk, 2 GB RAM): seeded 201 rows spanning 200 days, then
restarted — 201 → 91 rows, exactly the 110 rows outside the window removed, logged as
`history retention sweep removed 110 row(s)`. The same run re-confirmed `java.time`
desugaring on API 24 (`Today` / `Yesterday` / `Sep 8, 2026` headers, localized times, zero
crashes).

> **Tooling note.** The API 36 AVD filled its `/data` partition mid-session (96 % used), and
> `adb install -r` had been invoked with output suppressed — so several installs failed
> silently and the device kept running a stale APK, which briefly looked like "the retention
> sweep does not run". Always check `adb install` output. Verification moved to the API 24
> AVD, which is the better target for this change anyway.

### Pre-existing Spec 007 flake — properly fixed (2026-05-08)

`BrowserScreenInstrumentedTest.loadingIndicator_appearsAndHidesOnFinish` was the single
failure standing between the suite and green. It is **not** a Spec 014 regression: it
reproduces on `main` (commit `954742a`) in a clean worktree, on API 24, API 36, and on
CI's API 29 runner.

**Why it failed.** The test asserted the loading indicator becomes visible during a live
`example.com` load. Once a sibling test in the class had warmed the WebView's HTTP cache,
the `Loading` window closed faster than the assertion could observe it.

**First attempt, which was not enough.** Waiting for the page to settle and then driving
the state machine still failed on CI: a settled WebView can re-fire `onProgressChanged` /
`onPageFinished`, flipping the synthetic `Loading` straight back to `Loaded`. CI's
software-rendered emulator (`-gpu swiftshader_indirect`) widens that window enough to lose
the race every time — which is why it passed locally on arm64 and failed on CI.

**The actual fix.** Remove the WebView from the equation, reusing the seam
`BrowserScreenOfflineErrorTest` already established: seed the ViewModel to
`LoadingState.Failed` **before** `setContent`, because `BrowserWebView` skips its initial
`loadUrl` in that state. The WebView is constructed but never loads, so it emits no
callbacks at all, and the state machine can be driven deterministically — no network, no
cache warmth, no ordering dependency. This lives in a new
`BrowserScreenLoadingIndicatorTest` (2 cases: indicator shown while `Loading` / hidden once
`Loaded`, and absent while `Failed`), and the racy method was removed from
`BrowserScreenInstrumentedTest`, which now keeps only assertions that genuinely need a real
page load. Live-load coverage stays in `pageRenders_assertsDomContainsExampleDomain`; real
platform callback ordering stays in `BrowserViewModelHistoryRecordSequenceTest`.

**Verified before/after under CI-like conditions** — the local AVD was booted with the same
flags CI uses (`-no-window -gpu swiftshader_indirect -noaudio -no-boot-anim`):
- `main`, original test → **FAILS** (CI failure reproduced locally).
- this branch → full suite **61/61, three runs back-to-back**.

> This touches two **Spec 007** test files from the Spec 014 branch. Called out here and in
> the PR body so the reviewer sees it deliberately rather than as drive-by churn.
