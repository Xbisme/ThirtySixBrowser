# Phase 1 Data Model: Bookmarks CRUD

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-05-07

> **Schema reuse**: The persisted schema for bookmarks and folders is unchanged from Spec 005 (see [BookmarkEntity](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/entity/BookmarkEntity.kt) and [BookmarkFolderEntity](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/entity/BookmarkFolderEntity.kt)). **Zero migration.** This document only defines the new **domain models** and the **mapper / DAO surface additions**.

---

## 1. Domain Models (pure Kotlin, no Android imports)

### `domain/model/Bookmark.kt`

```kotlin
data class Bookmark(
    val id: Long,
    val title: String,
    val url: String,
    val parentFolderId: Long?,   // null = root level
    val createdAt: Long,         // epoch millis
    val sortOrder: Long,         // currently equal to createdAt; reserved for future drag-reorder
)
```

| Field | Source | Constraints |
|---|---|---|
| `id` | `BookmarkEntity.id` | autoGenerate; `0L` indicates "not yet persisted" — used at insert time |
| `title` | `BookmarkEntity.title` | non-blank after validation; max 200 chars (`BrowserLimits.MAX_BOOKMARK_TITLE_LENGTH`) |
| `url` | `BookmarkEntity.url` | non-blank; matches `http(s)://...` after `BookmarkUrlValidator.normalize()`; max 2048 chars |
| `parentFolderId` | `BookmarkEntity.parent_folder_id` | references `BookmarkFolder.id`; null = root |
| `createdAt` | `BookmarkEntity.created_at` | epoch millis at insert time |
| `sortOrder` | `BookmarkEntity.sort_order` | initially `= createdAt`; in v1.0 used only for ordering ties; reserved for future drag-reorder spec |

**Identity**: `id`. Two bookmarks with identical `(title, url, parentFolderId)` are distinct rows (no UNIQUE constraint, matches Spec 005 — supports manual-duplicate-add and Q4 star-toggle semantics).

---

### `domain/model/BookmarkFolder.kt`

```kotlin
data class BookmarkFolder(
    val id: Long,
    val name: String,
    val parentId: Long?,         // null = root level
    val createdAt: Long,
)
```

| Field | Source | Constraints |
|---|---|---|
| `id` | `BookmarkFolderEntity.id` | autoGenerate |
| `name` | `BookmarkFolderEntity.name` | non-blank; max 100 chars (`BrowserLimits.MAX_FOLDER_NAME_LENGTH`) |
| `parentId` | `BookmarkFolderEntity.parent_id` | references another folder's `id`; null = root; cycle prevention enforced in `MoveFolderUseCase` (R6) |
| `createdAt` | `BookmarkFolderEntity.created_at` | epoch millis |

**Identity**: `id`. Folder names are NOT unique within a parent — users may have two "Notes" folders side-by-side if they wish.

---

### `domain/model/BookmarkSearchResult.kt`

```kotlin
data class BookmarkSearchResult(
    val bookmark: Bookmark,
    val folderPath: List<BookmarkFolder>,   // empty for root-level bookmarks
)
```

The display row renders `folderPath.joinToString(" / ") { it.name }` (or the localized "Root" label from `bookmarks_breadcrumb_root_label` when empty). Order is root → leaf.

---

### `domain/model/BookmarkDescendantCount.kt`

```kotlin
data class BookmarkDescendantCount(
    val bookmarks: Int,
    val folders: Int,
) {
    val total: Int get() = bookmarks + folders
}
```

Used by FR-020 confirm dialog (R8). Computed by `CountFolderDescendantsUseCase` walking the subtree.

---

## 2. Mapper Specifications

### `data/mapper/BookmarkMapper.kt`

```kotlin
object BookmarkMapper {
    fun toDomain(e: BookmarkEntity): Bookmark = Bookmark(
        id = e.id,
        title = e.title,
        url = e.url,
        parentFolderId = e.parentFolderId,
        createdAt = e.createdAt,
        sortOrder = e.sortOrder,
    )

    fun toEntity(b: Bookmark): BookmarkEntity = BookmarkEntity(
        id = b.id,
        title = b.title,
        url = b.url,
        parentFolderId = b.parentFolderId,
        createdAt = b.createdAt,
        sortOrder = b.sortOrder,
    )
}
```

Total round-trip property — `toEntity(toDomain(e)) == e` for any valid entity. Test asserts this property for representative cases.

### `data/mapper/BookmarkFolderMapper.kt`

```kotlin
object BookmarkFolderMapper {
    fun toDomain(e: BookmarkFolderEntity): BookmarkFolder = BookmarkFolder(
        id = e.id, name = e.name, parentId = e.parentId, createdAt = e.createdAt,
    )

    fun toEntity(f: BookmarkFolder): BookmarkFolderEntity = BookmarkFolderEntity(
        id = f.id, name = f.name, parentId = f.parentId, createdAt = f.createdAt,
    )
}
```

---

## 3. DAO Surface Additions

### `BookmarkDao.kt` — new methods (in addition to existing CRUD + `observeByFolder` + `count`)

```kotlin
@Dao
interface BookmarkDao {
    /* …existing methods unchanged… */

    /** Spec 013: returns bookmarks whose URL matches exactly. Powers IsUrlBookmarkedUseCase. */
    @Query("SELECT * FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.COL_URL} = :url")
    fun observeByUrl(url: String): Flow<List<BookmarkEntity>>

    /** Spec 013: count for synchronous gate inside ToggleBookmarkUseCase (avoids race). */
    @Query("SELECT COUNT(*) FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.COL_URL} = :url")
    suspend fun countByUrl(url: String): Int

    /** Spec 013: deletes the most recently created bookmark with the given URL. Q4 toggle path. */
    @Query(
        """
        DELETE FROM ${BookmarkEntity.TABLE_NAME}
         WHERE ${BookmarkEntity.COL_URL} = :url
           AND ${BookmarkEntity.COL_ID} = (
             SELECT ${BookmarkEntity.COL_ID} FROM ${BookmarkEntity.TABLE_NAME}
              WHERE ${BookmarkEntity.COL_URL} = :url
              ORDER BY ${BookmarkEntity.COL_CREATED_AT} DESC, ${BookmarkEntity.COL_ID} DESC
              LIMIT 1
           )
        """,
    )
    suspend fun deleteMostRecentByUrl(url: String): Int   // returns row count deleted (0 or 1)

    /** Spec 013: cascade-delete leaf path for DeleteFolderUseCase (R7). */
    @Query("DELETE FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.COL_PARENT_FOLDER_ID} = :folderId")
    suspend fun deleteByParentFolder(folderId: Long): Int

    /** Spec 013: substring search over title + url, case-insensitive. */
    @Query(
        """
        SELECT * FROM ${BookmarkEntity.TABLE_NAME}
         WHERE LOWER(${BookmarkEntity.COL_TITLE}) LIKE '%' || LOWER(:query) || '%'
            OR LOWER(${BookmarkEntity.COL_URL})   LIKE '%' || LOWER(:query) || '%'
         ORDER BY ${BookmarkEntity.COL_CREATED_AT} DESC, ${BookmarkEntity.COL_ID} DESC
        """,
    )
    fun searchByTitleOrUrl(query: String): Flow<List<BookmarkEntity>>

    /** Spec 013: count under a folder for the descendant tally. */
    @Query("SELECT COUNT(*) FROM ${BookmarkEntity.TABLE_NAME} WHERE ${BookmarkEntity.COL_PARENT_FOLDER_ID} = :folderId")
    suspend fun countByFolder(folderId: Long): Int
}
```

### `BookmarkFolderDao.kt` — new methods

```kotlin
@Dao
interface BookmarkFolderDao {
    /* …existing methods unchanged… */

    /** Spec 013: synchronous version of observeChildren — used by depth-first cascade-delete walk. */
    @Query(
        """
        SELECT * FROM ${BookmarkFolderEntity.TABLE_NAME}
         WHERE (:parentId IS NULL AND ${BookmarkFolderEntity.COL_PARENT_ID} IS NULL)
            OR ${BookmarkFolderEntity.COL_PARENT_ID} = :parentId
        """,
    )
    suspend fun getDirectChildren(parentId: Long?): List<BookmarkFolderEntity>

    /** Spec 013: leaf delete used at the end of cascade walk. */
    @Query("DELETE FROM ${BookmarkFolderEntity.TABLE_NAME} WHERE ${BookmarkFolderEntity.COL_ID} = :id")
    suspend fun deleteById(id: Long): Int
}
```

---

## 4. Lifecycle / State Transitions

### Bookmark lifecycle

```
            ┌───────────────────┐
            │     (none)        │
            └────────┬──────────┘
                     │ Add (star toggle / manual / star toggle)
                     ▼
            ┌───────────────────┐
            │     existing      │◄─────────┐
            └────┬─────┬─────┬──┘          │
       Edit     │     │     │  Move        │ rename / move folder
                ▼     │     ▼              │ contains me
            ┌────────┐│  ┌──────────────┐  │
            │ edited ││  │ in new folder│──┘
            └────────┘│  └──────────────┘
                      │
        Delete / star-toggle-off / cascade-from-folder-delete
                      ▼
            ┌───────────────────┐
            │   (deleted)       │
            └───────────────────┘
```

### BookmarkFolder lifecycle

```
            ┌───────────────────┐
            │     (none)        │
            └────────┬──────────┘
                     │ CreateFolderUseCase
                     ▼
            ┌───────────────────┐
            │     existing      │◄────┐
            └─┬─────┬─────┬─────┘     │
       Rename │  Move │   │           │ rename of ancestor
                      │   │           │
                      ▼   │           │
              ┌─────────┐ │           │
              │ moved   │─┘           │
              └─────────┘             │
                                      │
            Delete (cascade — R7)     │
                     │                │
                     ▼                │
            ┌───────────────────┐     │
            │  (deleted        )│ ◄───┘  (chain bubbles up)
            └───────────────────┘
```

Cascade delete is atomic — within one Room `@Transaction` the entire subtree (target folder + all descendant folders + all descendant bookmarks) transitions from existing → deleted in one step.

---

## 5. Validation Rules (cross-references)

| Rule | Where enforced | Spec FR |
|---|---|---|
| Bookmark title non-blank | `BookmarkUrlValidator` + use cases | FR-006, FR-022 |
| Bookmark URL valid http(s) | `BookmarkUrlValidator` (R5) | FR-006, FR-022 |
| Folder name non-blank | `CreateFolderUseCase`, `RenameFolderUseCase` | FR-012, FR-016 |
| Folder cycle prevention | `MoveFolderUseCase` (R6) | FR-018 |
| Star icon disabled in incognito / on errored URL | `BrowserViewModel` derived `canBookmark` | FR-004 |
| Cascade delete atomicity | `BookmarkRepositoryImpl.deleteFolderCascade` (`@Transaction`) | FR-020 |

---

## 6. Counts / scale envelope

Reuses Spec 005 power-user envelope: 10K bookmarks, 1K folders, 5–10 nesting depth typical, 1000-deep is an edge case (handle at use-case level; defer optimization).

Search is the only hot path: SC-003 budget 100 ms / keystroke at 1K bookmarks. R3 verifies LIKE query meets this comfortably.
