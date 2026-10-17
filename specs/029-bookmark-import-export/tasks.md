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

- [ ] T001 Baseline on `main` `92e114e`: record the unit and instrumented test counts and the release APK size in "Gate Results" below

## Phase 2: Foundational (blocks all stories)

- [ ] T002 [P] Add `BookmarkTreeNode` (`Folder(name, children)` / `Link(title, url, addedAtMillis: Long?)`) in `M/core/domain/model/BookmarkTreeNode.kt` and `ImportOutcome(folderId, folderName, bookmarksAdded, foldersAdded)` in `M/core/domain/model/ImportOutcome.kt`
- [ ] T003 [P] Add `BookmarkTransferLimits` (`MAX_IMPORT_FILE_BYTES = 10 * 1024 * 1024`, `MAX_IMPORT_DEPTH = 32`, `SNIFF_BYTES = 1024`, `EXPORT_FILE_NAME_PATTERN = "thirtysix-bookmarks-%s.html"`) in `M/B/domain/model/BookmarkTransferLimits.kt`
- [ ] T004 Add `getBookmarkTree()`, `countBookmarks()`, `countFolders()` and `importTree(rootName, tree): Result<ImportOutcome>` to `M/core/domain/repository/BookmarkRepository.kt`, following the KDoc in contracts; implement them in `T/core/testing/fake/FakeBookmarkRepository.kt`
- [ ] T005 Implement the four methods in `M/B/data/repository/BookmarkRepositoryImpl.kt`. `importTree` runs in one `database.withTransaction`:
  - read the root folder names and pick `rootName` or `rootName (n)`;
  - insert the folder, then walk depth-first, giving links a running `sortOrder` from one `startedAt` (research R5);
  - `getBookmarkTree` reads folders and bookmarks inside one transaction, then orders folders by name and links by `sortOrder, id`.

  Add DAO one-shot queries where needed in `M/core/data/datasource/local/BookmarkDao.kt` and `BookmarkFolderDao.kt`, without schema change.
- [ ] T006 Repository tests on in-memory Room in `T/B/data/repository/BookmarkRepositoryImplTest.kt`:
  - nesting and link order are kept;
  - the name suffix " (2)";
  - a rollback when an insert part-way throws, with row counts identical (SC-005);
  - `getBookmarkTree` orders folders by name, then links;
  - the counts.

## Phase 3: User Story 1 – Import from another browser (P1) 🎯 MVP

**Goal**: pick a Netscape file and get a dated folder with its tree. **Independent test**: quickstart G3.1.

- [ ] T007 [P] [US1] Add the fixtures `chrome.html`, `firefox.html`, `safari.html`, `edge.html`, `samsung.html`, `duckduckgo.html` in `app/src/test/resources/bookmarks/`, following the shapes in research R10. Each holds nested folders, links, and the browser-specific extras (toolbar flag, `ICON` data URIs, `place:`, `<HR>`, `<DD>`, `FOLDED`, `TAGS`).
- [ ] T008 [P] [US1] Add `ParsedBookmarkFile`, `BookmarkImportResult` and `BookmarkFileReadResult` as in data-model and contracts, in `M/B/domain/model/`
- [ ] T009 [US1] Implement `NetscapeBookmarkParser` in `M/B/data/datasource/local/NetscapeBookmarkParser.kt`: a character scanner over a `Reader`, per research R1–R4 and the contract's parser table.
  - Keep only `http`/`https` with length ≤ `MAX_BOOKMARK_URL_LENGTH`.
  - Titles are decoded, whitespace-collapsed, blank → url, cut to `MAX_BOOKMARK_TITLE_LENGTH`.
  - Folder names are cut to `MAX_FOLDER_NAME_LENGTH`; a blank name → the caller-provided fallback.
  - Duplicates are counted per folder; depth is capped at `MAX_IMPORT_DEPTH`.
  - `<script>` and `<style>` content is skipped.
- [ ] T010 [US1] Parser tests in `T/B/data/datasource/local/NetscapeBookmarkParserTest.kt`:
  - every T007 fixture with exact link, folder, duplicate and unsupported counts;
  - entities;
  - single- and double-quoted and unquoted attributes;
  - a one-line file;
  - an `H3` without `DL`;
  - a stray `</DL>`.
- [ ] T011 [US1] Implement `BookmarkFileDataSource` in `M/B/data/datasource/local/BookmarkFileDataSource.kt`.
  - It reads with `ContentResolver.openInputStream(Uri.parse(uri))` on the injected IO dispatcher.
  - A counting stream caps at `MAX_IMPORT_FILE_BYTES` and gives `TooLarge`.
  - UTF-8 decoding with replacement; a sniff of the first `SNIFF_BYTES` (research R2).
  - Exceptions map to `ReadFailed`.
- [ ] T012 [US1] Add the `BookmarkFileRepository` contract in `M/B/domain/repository/BookmarkFileRepository.kt` and `BookmarkFileRepositoryImpl` (`read` only, for now) in `M/B/data/repository/BookmarkFileRepositoryImpl.kt`; bind it in `M/B/di/BookmarkModule.kt`
- [ ] T013 [US1] Add the fake `T/B/fake/FakeBookmarkFileRepository.kt`, and Robolectric tests for read, size cap and not-a-bookmark-file in `T/B/data/repository/BookmarkFileRepositoryImplTest.kt`
- [ ] T014 [US1] Implement `ImportBookmarksUseCase(uri, folderBaseName)` in `M/B/domain/usecase/ImportBookmarksUseCase.kt`, following the contract's rules:
  - `linkCount == 0` → `NothingToImport`;
  - `countBookmarks() + linkCount > MAX_BOOKMARKS` or `countFolders() + folderCount + 1 > MAX_FOLDERS` → `TooLarge`;
  - an `importTree` failure → `WriteFailed`.

  Test every branch in `T/B/domain/usecase/ImportBookmarksUseCaseTest.kt`.
- [ ] T015 [US1] ViewModel in `M/B/presentation/bookmarks/BookmarksViewModel.kt`, with `BookmarksAction.kt`, `BookmarksEvent.kt` and `BookmarksUiState.kt`:
  - `transfer: BookmarkTransferState` (`Idle`, `Importing`, `Exporting`, `ImportDone(result)`, `ExportDone(result)`) and `isTransferSheetOpen`;
  - actions `TransferMenuClick`, `ImportClick`, `ImportFilePicked(uri, folderBaseName)`, `TransferResultDismiss`, `OpenImportedFolder(folderId)`;
  - event `LaunchImportPicker`;
  - only one transfer at a time;
  - `OpenImportedFolder` navigates into the folder through the existing folder path.
- [ ] T016 [US1] ViewModel tests in `T/B/presentation/bookmarks/BookmarksViewModelTest.kt` (Turbine, `MainDispatcherRule`): state transitions, a second import ignored while one runs, `OpenImportedFolder`
- [ ] T017 [P] [US1] Add the drawables `ic_file_open.xml` and `ic_file_save.xml` (Material Symbols Rounded, same source as the existing icons) in `app/src/main/res/drawable/`, and `AppIcons.FileOpen` / `FileSave` in `M/core/presentation/designsystem/theme/AppIcons.kt`
- [ ] T018 [US1] UI in `M/B/presentation/bookmarks/`:
  - **Top bar** (`component/BookmarksTopBar.kt`): a `MoreVert` `RoundIconButton` (`bookmarks_action_more_cd`).
  - **Sheet** (`component/BookmarkTransferSheet.kt`): `SheetFrame` and two `SheetActionRow`s.
  - **Dialogs** (`component/BookmarkTransferDialogs.kt`): a progress dialog that cannot be dismissed, with `ProgressLine`, and a result `ConfirmDialog` with the counts in plurals, "Open folder" and "Done", announced with `liveRegion = Polite`.
  - **Route** (`BookmarksScreen.kt`): the `OpenDocument` launcher with the MIME list from research R7; a `null` URI does nothing. The folder base name comes from `stringResource(R.string.bookmarks_import_folder_name, mediumDate)`, in the app locale.
- [ ] T019 [US1] Strings in `app/src/main/res/values*/strings.xml`, all 8 locales:
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

- [ ] T020 [P] [US2] Implement `NetscapeBookmarkWriter` in `M/B/data/datasource/local/NetscapeBookmarkWriter.kt`, following the header and escaping in research R6. It escapes `& < > " '` in text and attributes, writes `ADD_DATE` in seconds, writes every folder including empty ones, and writes no `PERSONAL_TOOLBAR_FOLDER`.
- [ ] T021 [US2] Writer tests in `T/B/data/datasource/local/NetscapeBookmarkWriterTest.kt`:
  - the golden output for a small tree;
  - escaping;
  - the round trip `parse(write(tree)) == tree` over a generated tree of 1,000 links with markup characters, emoji and text in all 8 locales (SC-003).
- [ ] T022 [US2] Add `write(uri, tree)` to `BookmarkFileRepository` and its Impl, using `ContentResolver.openOutputStream(uri, "wt")` and a buffered UTF-8 writer on IO; exceptions give a failed `Result`. Add a Robolectric test.
- [ ] T023 [US2] Add `BookmarkExportResult` in `M/B/domain/model/BookmarkExportResult.kt` and `ExportBookmarksUseCase(uri)` in `M/B/domain/usecase/ExportBookmarksUseCase.kt`; test it in `T/B/domain/usecase/ExportBookmarksUseCaseTest.kt`
- [ ] T024 [US2] ViewModel export:
  - `ExportClick` closes the sheet. When both counts are 0, it goes to `ExportDone(NothingToExport)` without a picker (FR-020); otherwise it emits `LaunchExportPicker(suggestedName)`, a name built from `EXPORT_FILE_NAME_PATTERN` and the ISO date.
  - `ExportFilePicked(uri)` goes `Exporting` → `ExportDone`.

  Test it in `BookmarksViewModelTest.kt`.
- [ ] T025 [US2] Route and dialogs: the `CreateDocument("text/html")` launcher; export result messages with a "saved N bookmarks" plural and write-failed. Strings × 8 in `values*/strings.xml`.

## Phase 5: User Story 3 – Bad files never hurt (P1)

**Goal**: every failure is clear and leaves data unchanged. **Independent test**: quickstart G3.3.

- [ ] T026 [P] [US3] Add the bad fixtures `empty.html`, `no-links.html`, `truncated.html`, `deep-100.html` and `photo.jpg` (random bytes) in `app/src/test/resources/bookmarks/`; tests generate the oversized file
- [ ] T027 [US3] Tests that cover the bad files end to end, through the parser, the data source and the use case:
  - each gives its `BookmarkImportResult`;
  - the fake repository records no write;
  - `deep-100.html` imports, with its links under the depth-32 folder.

  In `T/B/domain/usecase/ImportBookmarksUseCaseTest.kt` and `NetscapeBookmarkParserTest.kt`.
- [ ] T028 [US3] Cancellation: a ViewModel test where clearing the ViewModel during `Importing` leaves the fake with no committed import, and a repository test where cancelling inside `importTree` rolls back on in-memory Room

## Phase 6: User Story 4 – Entry points (P2)

**Goal**: Import and Export reachable from Bookmarks and from Settings. **Independent test**: quickstart G3.4.

- [ ] T029 [US4] Navigation in `M/B/presentation/navigation/BookmarksNavigation.kt`:
  - the route `bookmarks?openTransfer={openTransfer}`, a boolean with default `false`;
  - `navigateToBookmarks(openTransfer: Boolean = false)`;
  - the route sends `TransferMenuClick` once when the argument is true, guarded by `rememberSaveable` so rotation does not reopen the sheet.
- [ ] T030 [US4] Settings:
  - `onBookmarkTransfer: () -> Unit = {}` in `M/feature/settings/presentation/settings/SettingsDestinations.kt`;
  - a "Bookmarks" section with a `SettingsValueRow` ("Import and export bookmarks", summary "Netscape HTML, used by all major browsers", `AppIcons.Bookmarks`), with key, `contentType` and test tag, in `SettingsScreen.kt`;
  - strings × 8.
- [ ] T031 [US4] `M/navigation/AppNavHost.kt`: fill `onBookmarkTransfer = { navController.navigateToBookmarks(openTransfer = true) }`

## Phase 7: Polish and gates

- [ ] T032 Instrumented test in `A/feature/bookmarks/presentation/bookmarks/BookmarkTransferTest.kt`:
  - the result-handling path from `ImportFilePicked` and `ExportFilePicked`, with a test file under the app's cache and a `content://` URI from the existing `FileProvider` (or a file URI on the test path);
  - the summary appears;
  - "Open folder" lands in the folder;
  - an exported file parses back.
- [ ] T033 Settings instrumented test: the row exists and opens Bookmarks with the sheet, in the existing settings test class under `A/feature/settings/`
- [ ] T034 Compose compiler report (`-PcomposeReports`): zero unexplained non-skippable composables in the new components. Record the result below.
- [ ] T035 Quality gates: `detekt ktlintCheck testDebugUnitTest lintDebug`, `assembleRelease`, the 16 KB script, the APK size delta ≤ 60 KB (SC-008), and `connectedDebugAndroidTest` on `TA016_API36_16K`
- [ ] T036 Device walk, quickstart G3 (1–6), on `TA016_API36_16K`, plus the picker smoke on `TA016_API24`. Record the results below.
- [ ] T037 G4 and G5 with the owner on the A55: real exports, timings, proxy and cold start. Record DEFERRED for anything not run.
- [ ] T038 PR with the gate results; update the project memory

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
| T001 Baseline | | |
| G3 Device walk | | |
| G4 Real exports | | |
| G5 Perf / size / start | | |
| Compose report | | |
