# Contract: Bookmark transfer

## Core: `BookmarkRepository` additions

```kotlin
/** Every folder and bookmark, read in one transaction; folders by name, then links by sort order. */
suspend fun getBookmarkTree(): BookmarkTreeNode.Folder

suspend fun countFolders(): Int
suspend fun countBookmarks(): Int

/**
 * Writes [tree] under a new top-level folder named [rootName], or [rootName] plus " (n)" when
 * that name is taken, in one transaction. Links keep the order of [tree]. Fails as a whole.
 */
suspend fun importTree(rootName: String, tree: BookmarkTreeNode.Folder): Result<ImportOutcome>
```

`FakeBookmarkRepository` in `core/testing/fake/` implements all four.

## Feature: `BookmarkFileRepository` (`feature/bookmarks/domain/repository/`)

```kotlin
interface BookmarkFileRepository {
    /** Reads and parses the file at [uri]. Never throws. */
    suspend fun read(uri: String): BookmarkFileReadResult
    /** Writes [tree] to [uri], replacing its content. */
    suspend fun write(uri: String, tree: BookmarkTreeNode.Folder): Result<Unit>
}

sealed interface BookmarkFileReadResult {
    data class Parsed(val file: ParsedBookmarkFile) : BookmarkFileReadResult
    data object NotABookmarkFile : BookmarkFileReadResult
    data object TooLarge : BookmarkFileReadResult
    data object ReadFailed : BookmarkFileReadResult
}
```

The implementation is `BookmarkFileRepositoryImpl` in `data/repository`. It is built from three parts in `data/datasource/local/`:

- `BookmarkFileDataSource` (`ContentResolver`, IO dispatcher, byte cap);
- `NetscapeBookmarkParser`, pure Kotlin;
- `NetscapeBookmarkWriter`, pure Kotlin.

## Use cases (`feature/bookmarks/domain/usecase/`)

| Use case | Input | Output | Rules |
|---|---|---|---|
| `ImportBookmarksUseCase` | `uri`, `folderBaseName` | `BookmarkImportResult` | 1. Read the file. A `Parsed` result with `linkCount == 0` → `NothingToImport`.<br>2. Count against `MAX_BOOKMARKS` and `MAX_FOLDERS` → `TooLarge`.<br>3. `importTree`; a failure → `WriteFailed`. |
| `ExportBookmarksUseCase` | `uri` | `BookmarkExportResult` | `getBookmarkTree`. Empty → `NothingToExport`. Otherwise `write`; a failure → `WriteFailed`. |

The ViewModel checks "anything to export?" with the repository counts before opening the picker. A one-call use case is not written for it.

## Parser behaviour (`NetscapeBookmarkParser.parse(reader): ParseOutcome`)

| Input | Result |
|---|---|
| Doctype or (`<DL>` and `<A HREF`) present | tree |
| Neither | not a bookmark file |
| `HREF` with a scheme other than http(s), empty, or too long | counted in `unsupportedSkipped` |
| Same URL twice in one folder | second counted in `duplicatesSkipped` |
| `<H3>` with no `<DL>` after it | empty folder |
| Depth > 32 | links attached to the depth-32 ancestor |
| EOF inside nesting | open levels closed |
| `<script>`/`<style>` content | ignored |
| Entities `&amp; &lt; &gt; &quot; &#39; &apos; &nbsp; &#NNN; &#xHH;` | decoded |

## Writer behaviour (`NetscapeBookmarkWriter.write(tree, writer)`)

- The header follows research R6, and the output is UTF-8.
- `&<>"'` are escaped in text and in attributes.
- `ADD_DATE` is in seconds.
- Every folder is written, including empty ones.
- Round trip: `parse(write(tree)) == tree` for any valid tree, which a property-style test checks.

## UI (`feature/bookmarks/presentation/bookmarks/`)

| Action | Effect |
|---|---|
| `TransferMenuClick` | open `BookmarkTransferSheet` |
| `ImportClick` | close sheet; event `LaunchImportPicker` |
| `ExportClick` | close sheet; if counts are 0 → `transfer = ExportDone(NothingToExport)`, else event `LaunchExportPicker(suggestedName)` |
| `ImportFilePicked(uri, folderBaseName)` | `transfer = Importing` → `ImportDone(result)` |
| `ExportFilePicked(uri)` | `transfer = Exporting` → `ExportDone(result)` |
| `TransferResultDismiss` | `transfer = Idle` |
| `OpenImportedFolder(folderId)` | `transfer = Idle`; navigate into the folder |

- The route owns the two launchers.
- A `null` URI from a launcher sends nothing.
- When the nav argument `openTransfer=true` is present, the route sends `TransferMenuClick` once, on first composition.

## Navigation

- `navigateToBookmarks(openTransfer: Boolean = false)`. The route pattern becomes `bookmarks?openTransfer={openTransfer}`, with a default of `false`. Existing callers are unchanged.
- `SettingsDestinations.onBookmarkTransfer: () -> Unit`, filled by `AppNavHost`.
