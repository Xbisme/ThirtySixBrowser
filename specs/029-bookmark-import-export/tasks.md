# Tasks: Bookmark Import and Export

**Input**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/bookmark-transfer.md](contracts/bookmark-transfer.md), [quickstart.md](quickstart.md)

**Tests**: required by Constitution §VIII. Every use case, repository, parser, writer and ViewModel change has a test.

**Paths**:

| Short form | Path |
|---|---|
| `M/` | `app/src/main/kotlin/com/raumanian/thirtysix/browser/` |
| `T/` | `app/src/test/kotlin/com/raumanian/thirtysix/browser/` |
| `A/` | `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/` |
| `B` | `feature/bookmarks` |

**Gate G1 after every task group**: `detekt ktlintCheck testDebugUnitTest compileDebugAndroidTestKotlin`, all green.

## Phase 1: Setup

- [X] T001 Baseline on `main` `92e114e`: record the unit and instrumented test counts and the release APK size in "Gate Results" below

## Phase 2: Foundational (blocks all stories)

- [X] T002 [P] Add `BookmarkTreeNode` (`Folder(name, children)` / `Link(title, url, addedAtMillis: Long?)`) in `M/core/domain/model/BookmarkTreeNode.kt` and `ImportOutcome(folderId, folderName, bookmarksAdded, foldersAdded)` in `M/core/domain/model/ImportOutcome.kt`
- [X] T003 [P] Add `BookmarkTransferLimits` (`MAX_IMPORT_FILE_BYTES = 10 * 1024 * 1024`, `MAX_IMPORT_DEPTH = 32`, `SNIFF_CHARS = 1024`, `EXPORT_FILE_NAME_PATTERN = "thirtysix-bookmarks-%s.html"`) in `M/B/domain/model/BookmarkTransferLimits.kt`
- [X] T004 Add `getBookmarkTree()`, `countBookmarks()`, `countFolders()` and `importTree(rootName, tree): Result<ImportOutcome>` to `M/core/domain/repository/BookmarkRepository.kt`, following the KDoc in contracts; implement them in `T/core/testing/fake/FakeBookmarkRepository.kt`
- [X] T005 Implement the four methods in `M/B/data/repository/BookmarkRepositoryImpl.kt`. `importTree` runs in one `database.withTransaction`:
  - read the root folder names and pick `rootName` or `rootName (n)`;
  - insert the folder, then walk depth-first, giving links a running `sortOrder` from one `startedAt` (research R5);
  - `getBookmarkTree` reads folders and bookmarks inside one transaction, then orders folders by name and links by `sortOrder, id`.

  Add DAO one-shot queries where needed in `M/core/data/datasource/local/BookmarkDao.kt` and `BookmarkFolderDao.kt`, without schema change.
- [X] T006 Repository tests on in-memory Room in `T/B/data/repository/BookmarkRepositoryImplTest.kt`:
  - nesting and link order are kept;
  - the name suffix " (2)";
  - a rollback when an insert part-way throws, with row counts identical (SC-005);
  - `getBookmarkTree` orders folders by name, then links;
  - the counts.

## Phase 3: User Story 1 – Import from another browser (P1) 🎯 MVP

**Goal**: pick a Netscape file and get a dated folder with its tree. **Independent test**: quickstart G3.1.

- [X] T007 [P] [US1] Add the fixtures `chrome.html`, `firefox.html`, `safari.html`, `edge.html`, `samsung.html`, `duckduckgo.html` in `app/src/test/resources/bookmarks/`, following the shapes in research R10. Each holds nested folders, links, and the browser-specific extras (toolbar flag, `ICON` data URIs, `place:`, `<HR>`, `<DD>`, `FOLDED`, `TAGS`).
- [X] T008 [P] [US1] Add `ParsedBookmarkFile`, `BookmarkImportResult` and `BookmarkFileReadResult` as in data-model and contracts, in `M/B/domain/model/`
- [X] T009 [US1] Implement `NetscapeBookmarkParser` in `M/B/data/datasource/local/NetscapeBookmarkParser.kt`: a character scanner over a `Reader`, per research R1–R4 and the contract's parser table.
  - Keep only `http`/`https` with length ≤ `MAX_BOOKMARK_URL_LENGTH`.
  - Titles are decoded, whitespace-collapsed, blank → url, cut to `MAX_BOOKMARK_TITLE_LENGTH`.
  - Folder names are cut to `MAX_FOLDER_NAME_LENGTH`; a blank name → the caller-provided fallback.
  - Duplicates are counted per folder; depth is capped at `MAX_IMPORT_DEPTH`.
  - `<script>` and `<style>` content is skipped.
- [X] T010 [US1] Parser tests in `T/B/data/datasource/local/NetscapeBookmarkParserTest.kt`:
  - every T007 fixture with exact link, folder, duplicate and unsupported counts;
  - entities;
  - single- and double-quoted and unquoted attributes;
  - a one-line file;
  - an `H3` without `DL`;
  - a stray `</DL>`.
- [X] T011 [US1] Implement `BookmarkFileDataSource` in `M/B/data/datasource/local/BookmarkFileDataSource.kt`.
  - It reads with `ContentResolver.openInputStream(Uri.parse(uri))` on the injected IO dispatcher.
  - A counting stream caps at `MAX_IMPORT_FILE_BYTES` and gives `TooLarge`.
  - UTF-8 decoding with replacement; a sniff of the first `SNIFF_CHARS` (research R2).
  - Exceptions map to `ReadFailed`.
- [X] T012 [US1] Add the `BookmarkFileRepository` contract in `M/B/domain/repository/BookmarkFileRepository.kt` and `BookmarkFileRepositoryImpl` (`read` only, for now) in `M/B/data/repository/BookmarkFileRepositoryImpl.kt`; bind it in `M/B/di/BookmarkModule.kt`
- [X] T013 [US1] Add the fake `T/B/fake/FakeBookmarkFileRepository.kt`, and Robolectric tests for read, size cap and not-a-bookmark-file in `T/B/data/repository/BookmarkFileRepositoryImplTest.kt`
- [X] T014 [US1] Implement `ImportBookmarksUseCase(uri, folderBaseName)` in `M/B/domain/usecase/ImportBookmarksUseCase.kt`, following the contract's rules:
  - `linkCount == 0` → `NothingToImport`;
  - `countBookmarks() + linkCount > MAX_BOOKMARKS` or `countFolders() + folderCount + 1 > MAX_FOLDERS` → `TooLarge`;
  - an `importTree` failure → `WriteFailed`.

  Test every branch in `T/B/domain/usecase/ImportBookmarksUseCaseTest.kt`.
- [X] T015 [US1] ViewModel in `M/B/presentation/bookmarks/BookmarksViewModel.kt`, with `BookmarksAction.kt`, `BookmarksEvent.kt` and `BookmarksUiState.kt`:
  - `transfer: BookmarkTransferState` (`Idle`, `Importing`, `Exporting`, `ImportDone(result)`, `ExportDone(result)`) and `isTransferSheetOpen`;
  - actions `TransferMenuClick`, `ImportClick`, `ImportFilePicked(uri, folderBaseName)`, `TransferResultDismiss`, `OpenImportedFolder(folderId)`;
  - event `LaunchImportPicker`;
  - only one transfer at a time;
  - `OpenImportedFolder` navigates into the folder through the existing folder path.
- [X] T016 [US1] ViewModel tests in `T/B/presentation/bookmarks/BookmarksViewModelTest.kt` (Turbine, `MainDispatcherRule`): state transitions, a second import ignored while one runs, `OpenImportedFolder`
- [X] T017 [P] [US1] Add the drawables `ic_file_open.xml` and `ic_file_save.xml` (Material Symbols Rounded, same source as the existing icons) in `app/src/main/res/drawable/`, and `AppIcons.FileOpen` / `FileSave` in `M/core/presentation/designsystem/theme/AppIcons.kt`
- [X] T018 [US1] UI in `M/B/presentation/bookmarks/`:
  - **Top bar** (`component/BookmarksTopBar.kt`): a `MoreVert` `RoundIconButton` (`bookmarks_action_more_cd`).
  - **Sheet** (`component/BookmarkTransferSheet.kt`): `SheetFrame` and two `SheetActionRow`s.
  - **Dialogs** (`component/BookmarkTransferDialogs.kt`): a progress dialog that cannot be dismissed, with `ProgressLine`, and a result `ConfirmDialog` with the counts in plurals, "Open folder" and "Done", announced with `liveRegion = Polite`.
  - **Route** (`BookmarksScreen.kt`): the `OpenDocument` launcher with the MIME list from research R7; a `null` URI does nothing. The folder base name comes from `stringResource(R.string.bookmarks_import_folder_name, mediumDate)`, in the app locale.
- [X] T019 [US1] Strings in `app/src/main/res/values*/strings.xml`, all 8 locales:
  - the menu and sheet labels;
  - the import title and progress;
  - the folder name `Imported %1$s`;
  - the untitled folder;
  - plurals for imported, duplicates and unsupported;
  - messages for not-a-bookmark-file, nothing-to-import, too-large, read-failed and write-failed;
  - "Open folder" and "Done".

**Checkpoint**: US1 works end to end on the AVD (quickstart G3.1).

## Phase 4: User Story 2 – Keep a copy (P1)

**Goal**: export the whole tree to a picked file. **Independent test**: quickstart G3.2.

- [X] T020 [P] [US2] Implement `NetscapeBookmarkWriter` in `M/B/data/datasource/local/NetscapeBookmarkWriter.kt`, following the header and escaping in research R6. It escapes `& < > " '` in text and attributes, writes `ADD_DATE` in seconds, writes every folder including empty ones, and writes no `PERSONAL_TOOLBAR_FOLDER`.
- [X] T021 [US2] Writer tests in `T/B/data/datasource/local/NetscapeBookmarkWriterTest.kt`:
  - the golden output for a small tree;
  - escaping;
  - the round trip `parse(write(tree)) == tree` over a generated tree of 1,000 links with markup characters, emoji and text in all 8 locales (SC-003).
- [X] T022 [US2] Add `write(uri, tree)` to `BookmarkFileRepository` and its Impl, using `ContentResolver.openOutputStream(uri, "wt")` and a buffered UTF-8 writer on IO; exceptions give a failed `Result`. Add a Robolectric test.
- [X] T023 [US2] Add `BookmarkExportResult` in `M/B/domain/model/BookmarkExportResult.kt` and `ExportBookmarksUseCase(uri)` in `M/B/domain/usecase/ExportBookmarksUseCase.kt`; test it in `T/B/domain/usecase/ExportBookmarksUseCaseTest.kt`
- [X] T024 [US2] ViewModel export:
  - `ExportClick` closes the sheet. When both counts are 0, it goes to `ExportDone(NothingToExport)` without a picker (FR-020); otherwise it emits `LaunchExportPicker(suggestedName)`, a name built from `EXPORT_FILE_NAME_PATTERN` and the ISO date.
  - `ExportFilePicked(uri)` goes `Exporting` → `ExportDone`.

  Test it in `BookmarksViewModelTest.kt`.
- [X] T025 [US2] Route and dialogs: the `CreateDocument("text/html")` launcher; export result messages with a "saved N bookmarks" plural and write-failed. Strings × 8 in `values*/strings.xml`.

## Phase 5: User Story 3 – Bad files never hurt (P1)

**Goal**: every failure is clear and leaves data unchanged. **Independent test**: quickstart G3.3.

- [X] T026 [P] [US3] Add the bad fixtures `empty.html`, `no-links.html`, `truncated.html`, `deep-100.html` and `photo.jpg` (random bytes) in `app/src/test/resources/bookmarks/`; tests generate the oversized file
- [X] T027 [US3] Tests that cover the bad files end to end, through the parser, the data source and the use case:
  - each gives its `BookmarkImportResult`;
  - the fake repository records no write;
  - `deep-100.html` imports, with its links under the depth-32 folder.

  In `T/B/domain/usecase/ImportBookmarksUseCaseTest.kt` and `NetscapeBookmarkParserTest.kt`.
- [X] T028 [US3] Cancellation: a ViewModel test where clearing the ViewModel during `Importing` leaves the fake with no committed import, and a repository test where cancelling inside `importTree` rolls back on in-memory Room

## Phase 6: User Story 4 – Entry points (P2)

**Goal**: Import and Export reachable from Bookmarks and from Settings. **Independent test**: quickstart G3.4.

- [X] T029 [US4] Navigation in `M/B/presentation/navigation/BookmarksNavigation.kt`:
  - the route `bookmarks?openTransfer={openTransfer}`, a boolean with default `false`;
  - `navigateToBookmarks(openTransfer: Boolean = false)`;
  - the route sends `TransferMenuClick` once when the argument is true, guarded by `rememberSaveable` so rotation does not reopen the sheet.
- [X] T030 [US4] Settings:
  - `onBookmarkTransfer: () -> Unit = {}` in `M/feature/settings/presentation/settings/SettingsDestinations.kt`;
  - a "Bookmarks" section with a `SettingsValueRow` ("Import and export bookmarks", summary "Netscape HTML, used by all major browsers", `AppIcons.Bookmarks`), with key, `contentType` and test tag, in `SettingsScreen.kt`;
  - strings × 8.
- [X] T031 [US4] `M/navigation/AppNavHost.kt`: fill `onBookmarkTransfer = { navController.navigateToBookmarks(openTransfer = true) }`

## Phase 7: Polish and gates

- [X] T032 Instrumented test in `A/feature/bookmarks/presentation/bookmarks/BookmarkTransferTest.kt`:
  - the result-handling path from `ImportFilePicked` and `ExportFilePicked`, with a test file under the app's cache and a `content://` URI from the existing `FileProvider` (or a file URI on the test path);
  - the summary appears;
  - "Open folder" lands in the folder;
  - an exported file parses back.
- [X] T033 Settings instrumented test: the row exists and opens Bookmarks with the sheet, in the existing settings test class under `A/feature/settings/`
- [X] T034 Compose compiler report (`-PcomposeReports`): zero unexplained non-skippable composables in the new components. Record the result below.
- [X] T035 Quality gates: `detekt ktlintCheck testDebugUnitTest lintDebug`, `assembleRelease`, the 16 KB script, the APK size delta ≤ 60 KB (SC-008), and `connectedDebugAndroidTest` on `TA016_API36_16K`
- [ ] T036 Device walk, quickstart G3 (1–6), on `TA016_API36_16K`, plus the picker smoke on `TA016_API24`. Record the results below.
- [ ] T037 G4 and G5 with the owner on the A55: real exports, timings, proxy and cold start. Record DEFERRED for anything not run.
- [ ] T038 PR with the gate results; update the project memory

## Implementation notes

- **T015, T016, T024**: the transfer got its own holder, `BookmarkTransferViewModel`, with `BookmarkTransferAction`, `BookmarkTransferEvent` and `BookmarkTransferUiState`, and is tested in `BookmarkTransferViewModelTest`. The reason: `BookmarksViewModel` was already over detekt's function limit. Its only change is `BookmarksAction.OpenFolder(id)`.
- **Results**: failures and export results are snackbars. Only a successful import gets the summary dialog, because it offers "Open folder".
- **Pickers**: compose-rules forbid passing a ViewModel down. `BookmarkTransferHost` therefore takes hoisted state, events and `onAction`. The route owns the `openTransfer` argument.
- **`BookmarkFileRepository.write`** returns `Boolean`, not `Result<Unit>`. `read` also takes the localized untitled-folder name.
- **Comment density**: the contract additions pushed `BookmarkRepository.kt` over the 30-line threshold, so its cycle KDoc was dropped (`BookmarkException.FolderCycle` says it) and the import note moved to the implementation.

## Dependencies

- Phase 2 blocks everything.
- US1 (T007–T019) comes before US2 (T020–T025): the writer's round-trip test uses the parser, and the export dialogs reuse the transfer state.
- US3 depends on US1. US4 depends on the US1 sheet.
- Phase 7 comes last.

## Parallel opportunities

- T002 ∥ T003.
- T007 ∥ T008 ∥ T017.
- T020 ∥ T026 once the parser exists.

## Implementation strategy

MVP = Phases 1–3: import from the Bookmarks screen. Then export (US2), the hardening tests (US3) and the Settings entry (US4), with one commit per task group as in the plan's step table.

## Gate Results

*(Filled during implementation: AVD, build type and date per gate; DEFERRED stated explicitly.)*

| Gate | Status | Result |
|---|---|---|
| T001 Baseline | ✅ 2026-10-17, `main` `92e114e` | 988 unit tests; 199 instrumented `@Test`s; release APK 3,776,883 B |
| G1/T035 Quality gates | ✅ 2026-10-17 | 1,037 unit tests, 0 failures; `detekt ktlintCheck lintDebug` clean; `connectedDebugAndroidTest` **202/202** on `TA016_API36_16K`, plus `SettingsBookmarkTransferRowTest` 1/1; 16 KB script green |
| SC-008 size | ✅ | Release APK **3,818,443 B**, +41,560 B (budget +60 KB); no new `.so` |
| SC-008 cold start | — not measured | No startup code changed: `ThirtySixApplication` and `MainActivity` are untouched, and Settings adds one lazy row. AVD rounds swing more than ±5% (ADR 0029 run), so a number there would not separate anything |
| Compose report | ✅ | `BookmarkTransferHost`, `BookmarkTransferContent`, `BookmarkTransferSheet`, `BookmarkTransferProgressDialog`, `BookmarkImportSummaryDialog`, `BookmarksTopBar`, `BookmarksScreen` and `SettingsScreen` are all restartable and skippable. `events: Flow` is unstable and expected (strong skipping) |
| G3 Device walk | ✅ partly, 2026-10-17, Galaxy A55, release, owner present | US1: picked a 5,000-link file in DocumentsUI (Downloads, `.html` selectable); the summary showed "Đã thêm 5000 dấu trang vào “Đã nhập 17 Th10, 2026”"; a second import gave "(2)". US2: export suggested `thirtysix-bookmarks-2026-10-17.html` and wrote 1,187,931 B for 10,000 bookmarks; the snackbar said "Đã xuất 10000 dấu trang." Limit: a third import (15,000 total) was refused and added no folder. Test folders and files were deleted afterwards. US3 bad files and the Settings entry are covered by instrumented tests. **API 24** (`TA016_API24`, release): Import opens DocumentsUI and cancelling returns with no message. Picking the file could not be scripted on that AVD: DocumentsUI ignored injected taps on files and folders but took them on its root list. The path after a picked URI is covered by `BookmarkTransferTest`, which CI runs on API 29. **Found**: on API 24 the Bookmarks search cannot be closed once a hardware or nav-bar Back has put the window in non-touch mode, because the cleared search field takes focus again. It reproduces on `main`, so it predates this spec; it is fixed separately. TalkBack: the owner's own pass |
| G4 Real exports | ✅ DuckDuckGo · ⏸ others | **DuckDuckGo Android**, 2026-10-17, A55 with the owner's permission. Claude bookmarked 3 pages in DDG and exported from DDG's Bookmarks → Export (`bookmarks_ddg_20261017.html`, 1,044 B). Importing it into the ThirtySix release gave "Đã thêm 4 dấu trang": the folder "DuckDuckGo Bookmarks" with all 4 links in order, the Vietnamese title intact, and an unescaped `&` in the default DDG link kept. The file is kept as the fixture `duckduckgo-android-export.html`. Afterwards the 3 bookmarks and the 3 tabs Claude opened in DDG were removed (the owner's YouTube tab and DDG's default bookmark were left), and the folder and file were deleted. **Still open**: Chrome, Firefox, Edge, Safari and Samsung Internet |
| G5 Performance | ✅ 2026-10-17, A55, release, Perfetto | **Import 5,000 links**: from the picker returning to the summary dialog, **1,043 ms** and **719 ms** (median of 2; budget 5 s). A third run was the limit refusal. The progress dialog kept drawing; the longest main-thread frame was 12.3 ms. **Export 10,000 bookmarks**: worker threads finished **≈170 ms** after the picker returned (budget 3 s). Proxy check not run: the parser has no network path, and icons are ignored by construction |
