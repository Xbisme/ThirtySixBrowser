# Data Model: Bookmark Import and Export

No schema change. Room stays at v6. The existing `bookmarks` and `bookmark_folders` tables are written through a new repository method.

## Domain models

They live in `core/domain/model/`, because `BookmarkRepository` is a core contract.

### BookmarkTreeNode

```kotlin
sealed interface BookmarkTreeNode {
    data class Folder(val name: String, val children: List<BookmarkTreeNode>) : BookmarkTreeNode
    data class Link(val title: String, val url: String, val addedAtMillis: Long?) : BookmarkTreeNode
}
```

- **Import**: `children` keeps the file's order.
- **Export**: folders by name, then links by sort order.
- **Root**: a `Folder` whose name is ignored.
- **Validation** (done by the parser before a tree reaches the repository):
  - `url` is `http(s)` and at most `MAX_BOOKMARK_URL_LENGTH` long;
  - `title` is not blank and at most `MAX_BOOKMARK_TITLE_LENGTH` long;
  - `name` is at most `MAX_FOLDER_NAME_LENGTH` long;
  - depth is at most 32.

### ImportOutcome

`data class ImportOutcome(val folderId: Long, val folderName: String, val bookmarksAdded: Int, val foldersAdded: Int)`. It is what the transaction wrote.

## Feature models

They live in `feature/bookmarks/domain/model/`.

### ParsedBookmarkFile

`data class ParsedBookmarkFile(val root: BookmarkTreeNode.Folder, val linkCount: Int, val folderCount: Int, val duplicatesSkipped: Int, val unsupportedSkipped: Int)`

### BookmarkImportResult

```kotlin
sealed interface BookmarkImportResult {
    data class Imported(val folderId: Long, val folderName: String, val imported: Int,
                        val duplicatesSkipped: Int, val unsupportedSkipped: Int) : BookmarkImportResult
    data object NotABookmarkFile : BookmarkImportResult
    data object NothingToImport : BookmarkImportResult
    data object TooLarge : BookmarkImportResult        // file > 10 MiB or app limits exceeded
    data object ReadFailed : BookmarkImportResult
    data object WriteFailed : BookmarkImportResult     // transaction failed (for example, disk full)
}
```

### BookmarkExportResult

```kotlin
sealed interface BookmarkExportResult {
    data class Exported(val bookmarks: Int, val folders: Int) : BookmarkExportResult
    data object NothingToExport : BookmarkExportResult
    data object WriteFailed : BookmarkExportResult
}
```

## State of the transfer

`BookmarkTransferUiState(isSheetOpen, progress)` holds the state. `progress` is one of:

- `Idle`;
- `Importing`;
- `Exporting`;
- `Imported(result)`.

```text
Idle ──file picked──▶ Importing ──▶ Imported ──Done / Open folder──▶ Idle
                                └─(failure)──▶ Idle + message
Idle ──file picked──▶ Exporting ──▶ Idle + message
```

- While the state is `Importing` or `Exporting`, sheet actions and picks are ignored.
- `NothingToExport` is decided before the picker opens.

## Limits

These are new constants in `feature/bookmarks/domain/model/BookmarkTransferLimits`:

| Name | Value |
|---|---|
| `MAX_IMPORT_FILE_BYTES` | 10 × 1024 × 1024 |
| `MAX_IMPORT_DEPTH` | 32 |
| `SNIFF_CHARS` | 1024 |
| `EXPORT_FILE_NAME_PATTERN` | `thirtysix-bookmarks-%s.html` |

The existing limits, `BrowserLimits.MAX_BOOKMARKS` and `BrowserLimits.MAX_FOLDERS`, are reused.
