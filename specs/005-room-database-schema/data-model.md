# Data Model: Room Database Schema (v1)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)
**Schema version**: 1
**Database file**: `thirtysix_browser.db` (app-private storage, framework-default path)
**Journal mode**: Write-Ahead Logging (Room default)
**Schema export**: `app/schemas/com.raumanian.thirtysix.browser.data.local.database.AppDatabase/1.json`
**Backup**: Excluded (FR-016, see [research.md R3](research.md#r3--backup-exclusion-xml-format-q4-clarification-implementation))

---

## Entity overview

```
┌─────────────────────┐       ┌──────────────────────┐
│   bookmark_folders  │◄──┐   │      bookmarks       │
│ ─────────────────── │   │   │ ──────────────────── │
│ id            PK    │◄──┼───┤ parent_folder_id FK  │
│ name                │   │   │ id            PK     │
│ parent_id    FK ──┐ │   │   │ title                │
│ created_at        ├─┘   │   │ url                  │
└───────────────────┘     │   │ created_at           │
       SET NULL self-ref  │   │ sort_order           │
                          │   └──────────────────────┘
                          │              SET NULL
                          │
                          │
┌──────────────────────┐  │   ┌──────────────────────┐
│   history_entries    │  │   │         tabs         │
│ ──────────────────── │  │   │ ──────────────────── │
│ id            PK     │  │   │ id            PK     │
│ url                  │  │   │ url                  │
│ title                │  │   │ title                │
│ visited_at  IDX DESC │  │   │ position    IDX      │
└──────────────────────┘  │   │ created_at           │
                          │   │ last_active_at       │
                          │   └──────────────────────┘
                          │
                          (no FK, no relation to bookmarks)
```

Four entities, two foreign-key relationships (both `ON DELETE SET NULL`):

1. `bookmarks.parent_folder_id` → `bookmark_folders.id`
2. `bookmark_folders.parent_id` → `bookmark_folders.id` (self-reference for nesting)

`history_entries` and `tabs` are independent (no foreign key to bookmark tables).

---

## Entity 1 — `bookmarks`

| Column | Type | Null | Default | Notes |
|--------|------|------|---------|-------|
| `id` | INTEGER (Long) | No | auto-increment | Primary key, system-assigned on insert |
| `title` | TEXT (String) | No | — | Display title; non-empty enforced at app layer |
| `url` | TEXT (String) | No | — | Target URL; **NO unique constraint** — same URL may appear multiple rows (Q3 clarification) |
| `parent_folder_id` | INTEGER (Long) | **Yes** | NULL | FK to `bookmark_folders.id`; NULL = root level |
| `created_at` | INTEGER (Long) | No | — | Unix epoch milliseconds, UTC |
| `sort_order` | INTEGER (Long) | No | — | Within-parent ordering index; ties fall back to insertion order |

**Foreign keys**:

- `parent_folder_id` → `bookmark_folders(id)` `ON DELETE SET NULL` `ON UPDATE NO ACTION`

**Indexes**:

- `index_bookmarks_parent_folder_id` on `(parent_folder_id)` — folder content browse (Spec 013); also satisfies Room's auto-warning on FK columns lacking index.
- (Implicit primary-key index on `id`.)

**Compose constants** (declared on `BookmarkEntity.Companion`):

```kotlin
const val TABLE_NAME = "bookmarks"
const val COL_ID = "id"
const val COL_TITLE = "title"
const val COL_URL = "url"
const val COL_PARENT_FOLDER_ID = "parent_folder_id"
const val COL_CREATED_AT = "created_at"
const val COL_SORT_ORDER = "sort_order"
const val INDEX_PARENT_FOLDER_ID = "index_bookmarks_parent_folder_id"
```

---

## Entity 2 — `bookmark_folders`

| Column | Type | Null | Default | Notes |
|--------|------|------|---------|-------|
| `id` | INTEGER (Long) | No | auto-increment | Primary key |
| `name` | TEXT (String) | No | — | Display name; non-empty enforced at app layer |
| `parent_id` | INTEGER (Long) | **Yes** | NULL | Self-FK to `bookmark_folders.id`; NULL = root level |
| `created_at` | INTEGER (Long) | No | — | Unix epoch ms, UTC |

**Foreign keys**:

- `parent_id` → `bookmark_folders(id)` `ON DELETE SET NULL` `ON UPDATE NO ACTION` (self-reference)

**Indexes**:

- `index_bookmark_folders_parent_id` on `(parent_id)` — folder-tree traversal; satisfies FK auto-warning.
- (Implicit primary-key index on `id`.)

**Compose constants** (`BookmarkFolderEntity.Companion`):

```kotlin
const val TABLE_NAME = "bookmark_folders"
const val COL_ID = "id"
const val COL_NAME = "name"
const val COL_PARENT_ID = "parent_id"
const val COL_CREATED_AT = "created_at"
const val INDEX_PARENT_ID = "index_bookmark_folders_parent_id"
```

---

## Entity 3 — `history_entries`

| Column | Type | Null | Default | Notes |
|--------|------|------|---------|-------|
| `id` | INTEGER (Long) | No | auto-increment | Primary key |
| `url` | TEXT (String) | No | — | Visited URL |
| `title` | TEXT (String) | No | — | Page title at time of visit |
| `visited_at` | INTEGER (Long) | No | — | Unix epoch ms, UTC; **each visit = new row** (Q2 clarification) |

**Foreign keys**: none.

**Indexes**:

- `index_history_entries_visited_at` on `(visited_at)` — newest-first paginated history list (Spec 014). Room emits queries with `ORDER BY visited_at DESC LIMIT ?` which use the index efficiently in either direction.
- (Implicit primary-key index on `id`.)

**Compose constants** (`HistoryEntryEntity.Companion`):

```kotlin
const val TABLE_NAME = "history_entries"
const val COL_ID = "id"
const val COL_URL = "url"
const val COL_TITLE = "title"
const val COL_VISITED_AT = "visited_at"
const val INDEX_VISITED_AT = "index_history_entries_visited_at"
```

**Important invariant**: revisiting the same URL produces a new row. No deduplication, no `visit_count` column, no `last_visited_at` column. Consumers wanting "unique URLs visited" must `GROUP BY url` at query time.

---

## Entity 4 — `tabs`

| Column | Type | Null | Default | Notes |
|--------|------|------|---------|-------|
| `id` | INTEGER (Long) | No | auto-increment | Primary key |
| `url` | TEXT (String) | No | — | Current URL of tab |
| `title` | TEXT (String) | No | — | Page title |
| `position` | INTEGER (Int) | No | — | Position index in tab list (0-based, no enforced uniqueness) |
| `created_at` | INTEGER (Long) | No | — | Unix epoch ms, UTC |
| `last_active_at` | INTEGER (Long) | No | — | Unix epoch ms, UTC; updated when user switches to this tab |

**Foreign keys**: none.

**Indexes**:

- `index_tabs_position` on `(position)` — tab list ordering (Spec 011).
- (Implicit primary-key index on `id`.)

**Compose constants** (`TabEntity.Companion`):

```kotlin
const val TABLE_NAME = "tabs"
const val COL_ID = "id"
const val COL_URL = "url"
const val COL_TITLE = "title"
const val COL_POSITION = "position"
const val COL_CREATED_AT = "created_at"
const val COL_LAST_ACTIVE_AT = "last_active_at"
const val INDEX_POSITION = "index_tabs_position"
```

**Critical out-of-scope**: there is no `is_incognito` column. Incognito tabs are NEVER persisted (Q3 pre-specify clarification). Persistence-by-omission is enforced by the consumer (Spec 011 / 012), not the schema.

---

## State transitions

This is a passive data layer; "state transitions" are the lifecycle events that mutate rows. Documented per entity:

### Bookmark

| Event | Effect |
|-------|--------|
| User saves a page | `INSERT INTO bookmarks(...)` — `id` auto-assigned; `parent_folder_id` may be NULL (root) or a valid folder id |
| User edits title / URL | `UPDATE bookmarks SET title = ?, url = ? WHERE id = ?` |
| User moves to another folder | `UPDATE bookmarks SET parent_folder_id = ?, sort_order = ? WHERE id = ?` |
| User deletes | `DELETE FROM bookmarks WHERE id = ?` |
| Parent folder deleted | `parent_folder_id ← NULL` (FK SET NULL); bookmark itself preserved |

### BookmarkFolder

| Event | Effect |
|-------|--------|
| User creates folder | `INSERT INTO bookmark_folders(...)` |
| User renames | `UPDATE bookmark_folders SET name = ? WHERE id = ?` |
| User moves folder under another | `UPDATE bookmark_folders SET parent_id = ? WHERE id = ?` |
| User deletes folder | `DELETE FROM bookmark_folders WHERE id = ?` — child folders' `parent_id ← NULL`, child bookmarks' `parent_folder_id ← NULL` |

### HistoryEntry

| Event | Effect |
|-------|--------|
| WebView completes a page load | `INSERT INTO history_entries(...)` — every load is a row, including reloads of the same URL |
| User clears history (range or all) | `DELETE FROM history_entries WHERE visited_at BETWEEN ? AND ?` (or no WHERE for all) |
| User deletes one entry | `DELETE FROM history_entries WHERE id = ?` |
| Updates | NEVER — history rows are immutable; revisits insert new rows |

### Tab

| Event | Effect |
|-------|--------|
| User creates new tab | `INSERT INTO tabs(...)` — `position` set to current max + 1 |
| User navigates within tab | `UPDATE tabs SET url = ?, title = ?, last_active_at = ? WHERE id = ?` |
| User switches active tab | `UPDATE tabs SET last_active_at = ? WHERE id = ?` |
| User reorders tabs | `UPDATE tabs SET position = ? WHERE id = ?` (multiple updates in transaction) |
| User closes tab | `DELETE FROM tabs WHERE id = ?` |
| Incognito tab activity | NEVER touches the `tabs` table — incognito state is in-memory only |

---

## Validation rules

The schema enforces structural validation only. Higher-layer validation (e.g., URL format) is a consumer concern.

| Rule | Enforcement | Source |
|------|------------|--------|
| `id` non-null, auto-assigned | SQLite `INTEGER PRIMARY KEY AUTOINCREMENT` semantics | All four entities |
| FK target exists or NULL | SQLite FK constraint (Room enables automatically) | bookmarks.parent_folder_id, bookmark_folders.parent_id |
| FK orphan-to-root on parent delete | `ON DELETE SET NULL` clause | Both FKs |
| Non-null text columns | SQLite `NOT NULL` constraint | All `String` columns |
| Non-null timestamp columns | `NOT NULL` constraint | created_at, visited_at, last_active_at |

NOT enforced at schema level (consumer responsibility):

- URL format / validity → Spec 009 omnibox + Spec 013 bookmark add
- Title non-empty → Spec 013 bookmark UI
- Reasonable timestamp range → consumer
- Position uniqueness across tabs → Spec 011 reorder logic
- sort_order uniqueness within folder → Spec 013 reorder logic
- Maximum row count per FR-017 envelope → not a hard cap, design target only

---

## Migration strategy v1 (FR-009)

- Database is opened with NO `fallbackToDestructiveMigration*` call.
- Future schema bumps (v1 → v2 → ...) MUST declare a `Migration(from, to)` instance and pass it to the database builder.
- Missing migration → Room throws `IllegalStateException` at `Room.databaseBuilder().build()` with the message "A migration from N to M was required but not found."; SC-004 negative-path test asserts this.
- Schema JSON for v1 lives in `app/schemas/.../1.json`; v2 will produce `2.json` allowing diff-based migration authoring + `MigrationTestHelper` use.

---

## Hilt wiring (DatabaseModule)

Single Hilt module installed in `SingletonComponent` provides:

| Provider | Returns | Scope |
|----------|---------|-------|
| `provideAppDatabase(@ApplicationContext context)` | `AppDatabase` | `@Singleton` |
| `provideBookmarkDao(db: AppDatabase)` | `BookmarkDao` | (transient — DAOs are cheap) |
| `provideBookmarkFolderDao(db: AppDatabase)` | `BookmarkFolderDao` | (transient) |
| `provideHistoryDao(db: AppDatabase)` | `HistoryDao` | (transient) |
| `provideTabDao(db: AppDatabase)` | `TabDao` | (transient) |

Consumers receive DAOs via constructor `@Inject`; the DI graph never exposes `AppDatabase` directly to ViewModels.

---

## Open issues for future schema versions

These are NOT addressed in v1; tracked for migration awareness:

- `favicon_url` on `history_entries` (and possibly `bookmarks`) — when favicon-loading infra ships.
- Composite index `(parent_folder_id, sort_order)` on `bookmarks` — likely Spec 013.
- Full-text search index for bookmark/history search — Spec 013/014.
- `is_pinned` on `tabs` — if Spec 011 introduces pinned tabs.
- Soft-delete columns / trash semantics — if a "trash" feature is added later.

Each of these requires a `Migration` class authored at the time of introduction.
