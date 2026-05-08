# Contract: BookmarkRepository

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md) | **Decision rationale**: [research.md R1](../research.md#r1-repository-shape-combined-vs-split)

This is the single repository abstraction for both bookmarks and folders. `domain/repository/BookmarkRepository.kt` is the **interface**; `data/repository/BookmarkRepositoryImpl.kt` is the **implementation** (annotated `@Singleton`, `@Inject` constructor receives `BookmarkDao` + `BookmarkFolderDao` + `DispatcherProvider`).

The repository is the only layer permitted to call DAOs directly. ViewModels MUST go through use cases; use cases call this interface.

---

## Interface signature (Kotlin pseudo-code)

```kotlin
interface BookmarkRepository {

    /* ───────── Bookmark queries ───────── */

    /** Bookmarks at the given folder (null = root), sorted recent-first. */
    fun observeBookmarksByFolder(folderId: Long?): Flow<List<Bookmark>>

    /** All bookmarks for a URL (any folder). Powers IsUrlBookmarked + star icon. */
    fun observeBookmarksByUrl(url: String): Flow<List<Bookmark>>

    /** Substring match across title and url, case-insensitive (R3). */
    fun searchBookmarks(query: String): Flow<List<Bookmark>>

    suspend fun getBookmark(id: Long): Bookmark?

    suspend fun countBookmarksByUrl(url: String): Int

    /* ───────── Bookmark mutations ───────── */

    suspend fun addBookmark(bookmark: Bookmark): Result<Long>

    suspend fun updateBookmark(bookmark: Bookmark): Result<Unit>

    suspend fun deleteBookmark(id: Long): Result<Unit>

    suspend fun deleteMostRecentBookmarkByUrl(url: String): Result<Int>

    suspend fun moveBookmarkToFolder(bookmarkId: Long, newParentId: Long?): Result<Unit>

    /* ───────── Folder queries ───────── */

    /** Direct children of a parent (null = root), sorted by name. */
    fun observeFoldersByParent(parentId: Long?): Flow<List<BookmarkFolder>>

    suspend fun getFolder(id: Long): BookmarkFolder?

    /** Walks the parent chain leaf → root. Empty list if leaf is at root.
     *  Used by ObserveFolderPathUseCase for breadcrumb (FR-014) and by
     *  SearchBookmarksUseCase for inline path display (FR-026). */
    suspend fun getAncestorChain(folderId: Long): List<BookmarkFolder>

    /** Total descendant count under a folder. Powers FR-020 confirm dialog. */
    suspend fun countDescendants(folderId: Long): BookmarkDescendantCount

    /* ───────── Folder mutations ───────── */

    suspend fun createFolder(name: String, parentId: Long?): Result<Long>

    suspend fun renameFolder(folderId: Long, newName: String): Result<Unit>

    /** Cycle prevention enforced inside (R6). Returns Result.Error(FolderCycle) if forbidden. */
    suspend fun moveFolder(folderId: Long, newParentId: Long?): Result<Unit>

    /** Cascade delete (R7) — single Room @Transaction. Removes folder + every descendant. */
    suspend fun deleteFolderCascade(folderId: Long): Result<BookmarkDescendantCount>
}
```

---

## Behavioural contract

### Concurrency / threading

- All `Flow`-returning methods MUST emit on `Dispatchers.IO` (Room default) and consumers MUST `.flowOn` if they need to render on Main.
- All `suspend` methods MUST be safe to call from any dispatcher; the implementation switches to IO internally via `withContext(dispatchers.io)`.

### Error semantics

- `Result<T>` is the existing `core/result/Result.kt` 2-state wrapper from Spec 002.
- Recoverable errors (validation, cycle, not-found) → `Result.Error(throwable)` carrying a typed exception:
  - `BookmarkNotFoundException(id)` for missing rows
  - `FolderNotFoundException(id)`
  - `FolderCycleException(folderId, attemptedParentId)` for R6 violations
  - `BookmarkUrlValidationException(reason)` for R5 violations (validator runs at use-case layer though — repository assumes pre-validated input)
- Unrecoverable / unexpected → `Result.Error` carrying the raw `Throwable` from Room (typically `SQLiteException`).
- `cancellationException` MUST propagate, not be wrapped.

### Cascade delete contract (R7 + FR-020)

`deleteFolderCascade(folderId)` MUST:

1. Run inside a single Room `@Transaction` (atomic — partial subtree deletion is forbidden).
2. Walk depth-first: recurse into each direct sub-folder before deleting any leaf bookmarks under the current node, before deleting the current node itself.
3. Return `Result.Success(BookmarkDescendantCount(b, f))` where `b` and `f` are counts of bookmarks and folders deleted **excluding** the target folder itself (the target folder is implicit — caller knows they're deleting one folder).
4. Be idempotent for the trivial case: deleting a folder that no longer exists returns `Result.Error(FolderNotFoundException)` rather than silently succeeding.

### Move folder contract (R6 + FR-018)

`moveFolder(folderId, newParentId)` MUST:

1. Reject if `folderId == newParentId` (self-parent) → `Result.Error(FolderCycleException)`.
2. Reject if `newParentId` is in `folderId`'s descendant set (would create a cycle) → `Result.Error(FolderCycleException)`. Implementation walks `newParentId`'s ancestor chain via `getAncestorChain()`; if `folderId` appears in the chain, reject.
3. On success update only the `parent_id` column; `name` and `created_at` are preserved.

### Star toggle contract (R9 + FR-001..003)

`addBookmark(bookmark)` is the insert path used by both manual-add (US7) and the star icon (US1). The "canonical" semantics live in `ToggleBookmarkUseCase`, NOT in this repository — the repo simply exposes:

- `countBookmarksByUrl(url)` — synchronous gate to decide insert vs delete.
- `deleteMostRecentBookmarkByUrl(url)` — atomic delete of "the latest" entry.

Use case orchestration:

```
ToggleBookmarkUseCase.invoke(url, fallbackTitle):
  if repo.countBookmarksByUrl(url) == 0:
    repo.addBookmark(Bookmark(... at root, title = title or url, createdAt = now))
  else:
    repo.deleteMostRecentBookmarkByUrl(url)
```

### Search contract (R3 + FR-024..028)

`searchBookmarks(query)` MUST:

1. Treat empty query as "no filter" — but in practice the use case won't call this with empty query (it returns to `observeBookmarksByFolder(currentFolderId)` instead).
2. Be case-insensitive substring match against title AND url (OR — either match wins).
3. Return results sorted recent-first (`createdAt DESC, id DESC`).
4. Run reactively via Room `Flow` so list updates in real-time as the user types AND as bookmarks change underneath.

The `SearchBookmarksUseCase` augments each `Bookmark` with its folder path (per FR-026) by:

```
results.map { b ->
  val path = if (b.parentFolderId == null) emptyList()
             else repo.getAncestorChain(b.parentFolderId)
  BookmarkSearchResult(b, path)
}
```

For 50 visible results × ~10-deep ancestor chain, this is ~500 reads per emission — well within budget.

### Pre-validation responsibilities

The repository ASSUMES validated input. The validator (`BookmarkUrlValidator` per R5) runs at the use-case layer. The repository:

- Does NOT trim, normalize, or auto-prepend `https://`.
- Does NOT check for empty title/url (use cases do).
- Does NOT enforce length caps from `BrowserLimits` (UI input fields enforce maxLength; use cases enforce again as a safety net before passing into repo).

---

## Hilt module

`di/BookmarkModule.kt`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class BookmarkModule {

    @Binds
    @Singleton
    abstract fun bindBookmarkRepository(impl: BookmarkRepositoryImpl): BookmarkRepository
}
```

`BookmarkRepositoryImpl` constructor:

```kotlin
@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val folderDao: BookmarkFolderDao,
    private val database: AppDatabase,            // for `withTransaction { ... }`
    private val dispatchers: DispatcherProvider,
) : BookmarkRepository { ... }
```

The `AppDatabase` injection is provided by the existing `DatabaseModule` from Spec 005 (no new DI wiring there).
