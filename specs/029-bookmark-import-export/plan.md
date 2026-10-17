# Implementation Plan: Bookmark Import and Export

**Branch**: `029-bookmark-import-export` | **Date**: 2026-10-17 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/029-bookmark-import-export/spec.md`

## Summary

Import and export the Netscape bookmark file through the system file picker.

- **Parsing** (R1): a pure-Kotlin tolerant tag scanner reads only `DL`, `H3` and `A`.
- **Import** (R5): written in one Room transaction under a new dated top-level folder. Link order is kept through `sortOrder`. The existing 10,000-bookmark and 1,000-folder limits are enforced for the import (R4).
- **Export** (R6): a single-transaction tree read, streamed in the Chrome-compatible layout.
- **UI** (R9): a ⋮ button on the Bookmarks top bar opens a sheet with Import and Export. Settings gets a row that opens the Bookmarks screen with that sheet showing. Results and progress use the Spec 019 dialogs.
- No schema change and no new dependency.

## Technical Context

**Language/Version**: Kotlin 2.3.21, AGP 9.1.1 (Android Studio JBR)
**Primary Dependencies**: Compose BOM 2026.04.01, Hilt 2.59.2 (KSP), Room 2.8.4, activity-compose (activity-result contracts, already present). No new dependency.
**Storage**: Room v6 unchanged. The existing `bookmarks` and `bookmark_folders` tables are used.
**Testing**: JUnit 4, coroutines-test, Turbine, Robolectric, in-memory Room, Compose UI, Konsist. Text fixtures go under `app/src/test/resources/bookmarks/`.
**Target Platform**: Android 7.0–16, 16 KB pages
**Project Type**: single-module Android app
**Performance Goals**:
- import 5,000 links in ≤ 5 s and export them in ≤ 3 s on the A55 release build;
- the main thread does no I/O or parsing;
- cold start unchanged.

**Constraints**:
- all or nothing;
- 10 MiB file cap;
- no network;
- no permission;
- APK ≤ +60 KB;
- 8 locales.

**Scale/Scope**: at most 10,000 bookmarks and 1,000 folders. Files touched:

- **core**: the `BookmarkRepository` contract and the tree model, plus their fake;
- **feature/bookmarks**: data, domain and presentation;
- **feature/settings**: one row and one destination;
- **navigation**: one argument.

## Constitution Check

*Gated against constitution **2.1.0**.*

| Principle | Status | Note |
|---|---|---|
| I Feature-first | ✅ | Everything lives in `feature/bookmarks` except two things:<br>• the contract additions and `BookmarkTreeNode` in `core/domain`, because `BookmarkRepository` is already core;<br>• the Settings row, which is reached only through `SettingsDestinations.onBookmarkTransfer`, filled by `AppNavHost`. |
| II One home | ✅ | Placement:<br>• `BookmarkFileRepository` in `feature/bookmarks/domain/repository`, with its `Impl` in `data/repository`;<br>• the parser, the writer and the file data source in `data/datasource/local`;<br>• the use cases in `domain/usecase`. |
| III Unidirectional flow | ✅ | New input goes through `BookmarksAction`, and the picker launches are `BookmarksEvent`s. The ViewModel calls the two use cases and the repository counts directly, since a use case for the counts would only forward the call. All I/O runs on the injected IO dispatcher. |
| IV Compose | ✅ | The sheet and dialogs read only the `transfer` field. The launchers stay in the route. The Compose report and the Layout Inspector count for opening the sheet are attached. |
| V No hardcoded values | ✅ | The limits are in `BookmarkTransferLimits` and `BrowserLimits`, and the file name pattern is a named constant. The new strings and plurals are in all 8 locales. The format markup lives inside the writer as named constants. |
| VI Comments | ✅ | The policy applies. The tolerant-parser rules are the only non-obvious part to note. |
| VII Build and data | ✅ | No migration and no dependency. R8: no reflection, so no keep rule. |
| VIII Tests | ✅ | Parser, writer and round trip; repository on in-memory Room, including a rollback; use cases; ViewModel; an instrumented import from a test URI through `FileProvider` in `androidTest`. |
| Data and privacy | ✅ | No network: the parser never resolves URLs, and icons are ignored. Only the URI the person picked is touched, once. Incognito is not involved. |
| Permissions | ✅ | None added. The activity-result contracts need none. |

**Post-design re-check**: no exemption and no allowlist entry. **Gate: PASS.**

## Project Structure

### Documentation

```text
specs/029-bookmark-import-export/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/bookmark-transfer.md
├── checklists/requirements.md
└── tasks.md                                    # /speckit-tasks
```

### Source Code

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/domain/
│   ├── model/BookmarkTreeNode.kt, model/ImportOutcome.kt            new
│   └── repository/BookmarkRepository.kt                             + getBookmarkTree, counts, importTree
├── feature/bookmarks/
│   ├── data/
│   │   ├── datasource/local/BookmarkFileDataSource.kt               new (ContentResolver, byte cap)
│   │   ├── datasource/local/NetscapeBookmarkParser.kt               new
│   │   ├── datasource/local/NetscapeBookmarkWriter.kt               new
│   │   └── repository/BookmarkRepositoryImpl.kt                     + the four methods
│   │       repository/BookmarkFileRepositoryImpl.kt                 new
│   ├── domain/
│   │   ├── model/ParsedBookmarkFile.kt, BookmarkImportResult.kt,
│   │   │   BookmarkExportResult.kt, BookmarkTransferLimits.kt       new
│   │   ├── repository/BookmarkFileRepository.kt                     new
│   │   └── usecase/ImportBookmarksUseCase.kt, ExportBookmarksUseCase.kt   new
│   ├── di/BookmarkModule.kt                                         + binding
│   └── presentation/
│       ├── bookmarks/BookmarksRoute (BookmarksScreen.kt)            + launchers, openTransfer
│       ├── bookmarks/BookmarksAction/Event/UiState/ViewModel        + transfer
│       ├── bookmarks/component/BookmarkTransferSheet.kt             new
│       ├── bookmarks/component/BookmarkTransferDialogs.kt           new (progress + result)
│       ├── bookmarks/component/BookmarksTopBar.kt                   + ⋮ button
│       └── navigation/BookmarksNavigation.kt                        + optional argument
├── feature/settings/presentation/settings/
│   ├── SettingsDestinations.kt                                      + onBookmarkTransfer
│   └── SettingsScreen.kt                                            + Bookmarks section, one row
└── navigation/AppNavHost.kt                                         fills onBookmarkTransfer
app/src/main/res/drawable/ic_file_open.xml, ic_file_save.xml         new
app/src/main/res/values*/strings.xml                                 + strings and plurals × 8
app/src/test/resources/bookmarks/*.html                              fixtures
```

**Structure Decision**: The existing feature-first layout. The tree model goes in `core`, next to the core repository that consumes it.

## Step plan (one commit each, G1 green)

| Step | Content | Gate |
|---|---|---|
| 0 | Baseline on `main` `92e114e`: test counts, release APK size | — |
| 1 | `BookmarkTreeNode`, `NetscapeBookmarkParser` and the fixtures, with tests | G1 |
| 2 | `NetscapeBookmarkWriter` and the round-trip tests | G1 |
| 3 | Repository additions (`importTree`, `getBookmarkTree`, counts), the fake, and in-memory Room tests including the rollback | G1 |
| 4 | `BookmarkFileDataSource`, `BookmarkFileRepository(Impl)`, the binding, and tests (Robolectric `ContentResolver`) | G1 |
| 5 | The two use cases, with tests | G1 |
| 6 | The ViewModel transfer state, actions and events, with tests | G1 |
| 7 | UI: the ⋮ button, the sheet, the dialogs, the launchers, the icons and the strings × 8; the nav argument | G1 + AVD |
| 8 | The Settings row and destination, and `AppNavHost` | G1 |
| 9 | Instrumented import and export test; Compose report | G1 + AVD |
| 10 | Device walk G3/G4/G5, PR | G3–G5 |

## Risks

| Risk | Mitigation |
|---|---|
| Some provider gives `.html` a MIME type outside the list, so the file is greyed out in the picker | The list is wide (R7). If G3 or G4 finds one, add its type. |
| Another browser rejects the export | The layout copies Chrome's header. G3 and G4 import into Chrome and Firefox. |
| A 10 MiB file with no newlines | The scanner reads characters and never needs lines. A test uses a one-line file. |
| The transaction holds the database for long on 10,000 rows | Measure in G5. Inserts of this size take well under a second; history and tab writes simply wait. |

## Complexity Tracking

No constitution violation to justify.
