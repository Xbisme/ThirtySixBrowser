# Feature Specification: Room Database Schema

**Feature Branch**: `005-room-database-schema`
**Created**: 2026-05-01
**Status**: ✅ Implemented 2026-05-01 (33/33 tasks; 51/51 unit tests pass; 16KB CI green; APK 1.4 MB)
**Input**: User description: "Spec 005 — room-database-schema. Mục tiêu: thiết lập tầng persistence on-device cho ThirtySixBrowser bằng Room với 4 entity (Bookmark, BookmarkFolder, History, Tab non-incognito) + DAO + AppDatabase + WAL + Hilt DatabaseModule. Chốt scope chặt — chỉ data layer; Repository/Mapper/Domain model deferred sang Spec 013/014. favicon defer; incognito KHÔNG persist. Migration strict-no-destructive ngay từ v1.0, schema export bật."

## Clarifications

### Session 2026-05-01 (pre-specify scope discussion)

- Q: Bookmark folder support trong v1.0? → A: **Có** — `BookmarkFolderEntity` riêng với `parent_id` self-reference cho phép nest folder; bookmark trỏ về folder qua `parent_folder_id` nullable (null = root level).
- Q: Cột `favicon_url` trong `HistoryEntity`? → A: **Defer** — chưa có favicon-loading infrastructure (Coil chưa thêm); cột sẽ được thêm trong spec sau khi infra sẵn sàng. Tránh schema deadweight.
- Q: Tab incognito persistence? → A: **Không persist** — `TabEntity` không có cột `is_incognito`; chỉ tab thường (non-incognito) được lưu vào DB. Incognito session = in-memory only, được handle ở Spec 011 (tabs-management) / 012 (incognito).
- Q: Scope ranh giới — có bao gồm Repository/Mapper/Domain model không? → A: **Không** — chỉ data layer (Entity + DAO + AppDatabase + DatabaseModule). Repository interface, RepositoryImpl, Entity↔Domain mapper, Domain model defer sang spec consumer (013-bookmarks, 014-history, 011-tabs) theo nguyên tắc incremental scope.
- Q: Migration strategy v1.0? → A: **Strict-no-destructive** — không dùng `fallbackToDestructiveMigration*`; schema export bật từ ngày 1; v1.0 = schema version 1. Mọi schema bump tương lai phải kèm `Migration` class declared.

### Session 2026-05-01 (clarify pass)

- Q: Entity identifier strategy across all four entities? → A: **Auto-assigned numeric (integer) identifier, system-generated on insert.** Idiomatic with the chosen persistence library, smallest storage footprint, simplest queries. Constitution forbids cloud sync so collision-resistant identifiers (UUID/ULID) would be premature optimization for a non-existent feature.
- Q: History deduplication model when the same URL is visited multiple times? → A: **Each visit is a separate row (chronological log).** Same URL visited on different occasions yields multiple rows distinguished by `visited_at` timestamp; no `visit_count` aggregation column. Matches Spec 014's "group history by date" UI, allows simple range deletion for clear-by-date, defers retention to consuming spec.
- Q: Bookmark URL uniqueness — same URL may be bookmarked multiple times? → A: **No uniqueness constraint on bookmark URL.** A given URL may be bookmarked any number of times (same folder or different folders); duplicates are permitted at the persistence layer. Consumer specs (e.g., Spec 013) may surface a soft "already bookmarked" notice via query but MUST NOT enforce it at the schema level.
- Q: Auto Backup policy for the database file? → A: **Excluded from Android Auto Backup and Device-to-Device Transfer.** The database file `thirtysix_browser.db` MUST NOT be uploaded to Google Drive or carried over device migration. Privacy-first stance: user data lives on-device only, matching the "no cloud sync, no account" positioning. Cost: device reset / migration loses bookmarks, history, tabs — acceptable for v1.0.
- Q: Scale envelope for v1.0 (informs which indexes ship at schema version 1)? → A: **Power-user envelope — up to 10,000 bookmarks, 100,000 history rows, 500 tabs.** v1.0 ships indexes on the hot lookup paths required by Spec 011 / 013 / 014 query patterns: `history.visited_at` (history list newest-first), `bookmark.parent_folder_id` (bookmark folder browse), `tab.position` (tab list ordering). This avoids a schema migration solely for index addition once consuming specs ship.

### Session 2026-05-01 (post-analyze remediation)

- Q: SC-009 "within 50 ms" timing assertion — testable in a JVM unit-test environment? → A: **No — relax to "within Turbine default timeout (1 s)".** The 50 ms threshold was overly tight for a cold-class-loaded JVM unit test and would produce flaky CI runs without measuring a meaningful production behavior. The invariant under test is "emission happens after table change, not polling" — that is what `awaitItem()` verifies. Sub-second timing on observer wakeup is not a privacy or correctness concern.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Bookmarks, History, and Tabs Survive App Restart (Priority: P1)

A user opens ThirtySixBrowser and uses it normally over multiple sessions. They save pages they want to return to, browse to many sites, and keep multiple tabs open. When they later relaunch the app — whether after a regular close, a system kill due to memory pressure, or a device reboot — every saved page is still there, their browsing history reflects what they actually visited, and their open tabs are restored exactly as they were left (with the exception of private/incognito tabs, which are intentionally not retained).

**Why this priority**: Persistence is the foundation of every data-bearing feature in the browser. Without on-device storage, bookmarks evaporate on restart, history is meaningless, and the multi-tab experience resets every cold start. P1 because every other Phase 2/3 spec (007 WebView wrapper, 011 tabs-management, 013 bookmarks-CRUD, 014 history-view) consumes this layer; nothing in those phases can ship without it.

**Independent Test**: After this layer is implemented, automated unit tests can insert sample records into each of the four entity tables, close and reopen an in-memory database instance, and verify all records are returned identically. The tests run without any UI or other higher-layer code, proving the persistence layer functions independently of the rest of the app.

**Acceptance Scenarios**:

1. **Given** the app's storage is empty, **When** sample records are inserted into each entity (Bookmark, BookmarkFolder, History, Tab) and the database is closed and reopened, **Then** every inserted record is retrievable with identical attribute values.
2. **Given** a Bookmark record references a BookmarkFolder by id, **When** the parent BookmarkFolder is deleted, **Then** the child Bookmark's parent reference becomes null (the bookmark is orphaned to the root level rather than cascade-deleted) so user data is not silently destroyed.
3. **Given** the app has open tabs persisted, **When** the app process is killed and relaunched, **Then** the persisted tab list returns in the original position order and reflects the last-active timestamp accurately.
4. **Given** any collection observer is attached to a table, **When** a record in that table is inserted, updated, or deleted, **Then** the observer emits a fresh snapshot containing the change without polling.

---

### User Story 2 - Schema Evolution Is Safe (Priority: P2)

As the app grows past v1.0, future feature specs will need to add columns, tables, and indexes. The persistence layer must guarantee that any user data already on the device survives a schema upgrade — the app must never silently destroy a user's saved bookmarks, history, or open tabs because the developer forgot to write a migration. If the developer ships an incompatible schema change without a migration, the database refuses to open with a clear error rather than wiping data.

**Why this priority**: A privacy-first, account-less browser holds the user's only copy of their data. Loss-of-data on app upgrade is the single most damaging trust-breaker. P2 because the failure mode only manifests on a future schema bump (no v1.0 user-visible behavior depends on it), but the discipline must be set at v1.0 so it survives every future spec.

**Independent Test**: Verifiable by inspecting the build configuration and declaring a deliberate schema mismatch in a test fixture. The test boots the database against the fixture, asserts that the open call fails with a missing-migration error, and that no rows are deleted.

**Acceptance Scenarios**:

1. **Given** the project's schema is version 1, **When** the app builds, **Then** a JSON snapshot of version 1 is exported to a tracked directory under version control.
2. **Given** a future schema version 2 is declared without an accompanying migration class, **When** the app attempts to open an existing version 1 database, **Then** the open call fails fast with an explicit missing-migration error and does not delete any rows.
3. **Given** a future schema version 2 ships with a correct migration class, **When** the app opens an existing version 1 database, **Then** every row is preserved and the database reports schema version 2.

---

### User Story 3 - Future Features Can Plug Into the Database Without Knowing Its Internals (Priority: P3)

A future feature spec (e.g., bookmarks-CRUD, history-view, tabs-management) needs to read or write data. The future implementer asks the dependency-injection container for the relevant data-access object and uses it; they never directly construct the database, never know the database file name, never need to coordinate database lifecycle (open/close), and never need to reimplement transaction handling. This isolation lets each future spec stay focused on its own concern (UI, business logic) rather than re-solving persistence plumbing.

**Why this priority**: The spec ships only the data layer — there is no consumer in v1.0. P3 because the value lands when later specs consume the contract, but the contract has to be defined here. Establishing the DI seam at v1.0 prevents future specs from creating ad-hoc database connections that would fragment the architecture.

**Independent Test**: A test class annotated with the project's DI scope can request any of the four DAOs as constructor parameters and use them directly, without calling any database-builder code. The test passes if the DI graph builds without unresolved-dependency errors and the DAOs operate on a real database instance.

**Acceptance Scenarios**:

1. **Given** the dependency-injection container is wired, **When** a consumer requests the BookmarkDao (or any of the other three DAOs), **Then** the DI graph supplies a working DAO instance backed by the singleton database.
2. **Given** two consumers request the database, **When** they each insert a record concurrently, **Then** both writes succeed under WAL journal mode without deadlock or data corruption (multi-reader + single-writer guarantee).
3. **Given** a consumer outside the data layer (e.g., a ViewModel in a future spec), **When** they need to read or write entities, **Then** they obtain the DAO via dependency injection without reaching into data-layer internals or constructing the database directly.

---

### Edge Cases

- **Cold start with empty database**: First-launch queries on every entity table return an empty result without error and without populating sample data.
- **Concurrent reads during a write**: WAL journal mode permits unbounded concurrent readers alongside one writer — readers do not block writers, writers do not block readers.
- **Process death mid-write**: A transaction interrupted by process kill is rolled back at the next open; only committed transactions are visible.
- **Foreign key violation**: Inserting a Bookmark whose `parent_folder_id` references a non-existent BookmarkFolder is rejected by the database engine.
- **Folder deletion with children**: When a BookmarkFolder is deleted, its child bookmarks have their parent reference set to null (orphaned to root), preserving user data. Cascade-delete is explicitly not chosen for this reason.
- **Storage full / file corruption**: Out of scope for v1.0 — these are device-level conditions; the app surfaces the underlying database error without custom recovery logic.
- **Sort order conflicts**: Two Bookmark records under the same parent with the same `sort_order` value are permitted — display order in such ties falls back to insertion order. (UI-side reordering will assign distinct values.)
- **Repeat visit to the same URL**: Each visit produces a new history row; ten visits to the same page yields ten rows. Consumers that want a "unique URLs visited" view must aggregate at query time (e.g., `GROUP BY url`).
- **Same URL bookmarked multiple times**: Permitted at the schema level; both rows persist with distinct identifiers. Consumer UI may detect this via a query and warn the user, but cannot rely on the database to reject the second insert.
- **Null vs missing optional fields**: Optional attributes (e.g., a folder's parent_id at root level) are stored as null, distinguished from absence; queries treat null parent_id as "root folder."
- **Incognito tab attempts**: Persistence layer has no concept of incognito; any caller that attempts to persist an incognito tab does so by simply not calling Tab insert — enforcement lives in the consuming spec, not at the schema level.
- **Database file size growth over time**: History and tabs accumulate; v1.0 ships no automatic pruning. Pruning policy (e.g., 90-day history retention) is a follow-up concern owned by Spec 014 (history-view) and Spec 011 (tabs-management).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST persist bookmark records with attributes: auto-assigned numeric identifier (system-generated on insert), title, URL, optional parent folder reference, creation timestamp, and sort-order index within parent. The bookmark URL MUST NOT carry a uniqueness constraint at the schema level — the same URL may be bookmarked multiple times, in the same folder or across different folders.
- **FR-002**: System MUST persist bookmark folder records with attributes: auto-assigned numeric identifier (system-generated on insert), display name, optional parent folder reference (for hierarchical nesting; null = root), and creation timestamp.
- **FR-003**: System MUST persist browsing history records with attributes: auto-assigned numeric identifier (system-generated on insert), URL, page title at time of visit, and visit timestamp. Each visit MUST be persisted as a separate row — visiting the same URL multiple times produces multiple rows distinguished by `visited_at`, with no aggregation, deduplication, or visit-count column. (Favicon storage is intentionally deferred to a later spec when favicon-loading infrastructure exists.)
- **FR-004**: System MUST persist non-incognito browser tab records with attributes: auto-assigned numeric identifier (system-generated on insert), URL, page title, position index in the tab list, creation timestamp, and last-active timestamp. The persistence layer MUST NOT model incognito tabs (incognito session state lives in memory only and is handled by a separate spec).
- **FR-005**: System MUST provide a data-access contract for each entity covering: insert, update, delete by record, query by identifier, query collection (with appropriate ordering — e.g., bookmarks ordered by sort index, history newest-first, tabs by position, folders by parent), and a reactive collection observer that emits a fresh snapshot when the underlying table changes.
- **FR-006**: System MUST run all database operations off the application main thread; main-thread reads and writes are forbidden by the database engine and must produce an error rather than blocking the UI.
- **FR-007**: System MUST configure the database engine to use Write-Ahead Logging (WAL) journal mode for concurrent reader / single-writer semantics.
- **FR-008**: System MUST set the database file name to `thirtysix_browser.db` and store it in the operating system's standard app-private storage location (the framework's default — no custom path).
- **FR-009**: System MUST start at schema version 1 and MUST NOT permit destructive fallback on schema mismatch (no `fallbackToDestructiveMigration*`-style escape hatch, neither in debug nor release builds).
- **FR-010**: System MUST export the schema as a JSON snapshot per version into a tracked directory under version control on every build, so future migrations can be authored against a known prior schema.
- **FR-011**: Bookmark records MUST reference a parent folder via a foreign-key relationship; deleting a parent folder MUST set child bookmarks' parent reference to null rather than cascade-delete them, preserving user data.
- **FR-012**: BookmarkFolder records MUST be able to reference another BookmarkFolder as parent (self-referencing foreign key) to form a folder hierarchy; deleting a parent folder MUST set its child folders' parent reference to null.
- **FR-013**: System MUST expose data-access objects via the project's existing dependency-injection mechanism; a singleton database instance MUST be shared by all consumers within the same process.
- **FR-014**: Consumers (ViewModels, repositories, use cases) MUST NOT construct the database directly — they MUST request data-access objects through the dependency-injection container.
- **FR-015**: System MUST NOT introduce any package containing native shared libraries that are not 16KB-page-size verified (Constitution §IX); the chosen persistence library is Kotlin/Java-only and inherently compliant.
- **FR-016**: System MUST NOT expose the database to other apps, must not back up its file to any cloud service (including Android Auto Backup to the user's Google Drive), and must not register itself for cross-device sync or device-to-device transfer. The database file MUST be explicitly listed as excluded in the project's Android backup rules (`backup_rules.xml` and `data_extraction_rules.xml`). Constitutional offline-first, on-device-only privacy stance.
- **FR-017**: System MUST be designed to operate within the v1.0 power-user scale envelope: up to 10,000 bookmark records, 100,000 history records, and 500 persisted tab records. v1.0 schema MUST ship indexes covering the hot lookup paths consumed by future feature specs — at minimum: an index on `HistoryEntry.visited_at` (descending history queries by recency), an index on `Bookmark.parent_folder_id` (folder content browse), and an index on `Tab.position` (tab list ordering). Additional indexes MAY be added if the plan phase identifies further query patterns; indexes added later than schema version 1 require a migration.

### Key Entities

- **Bookmark**: a URL the user has explicitly saved for later. Attributes: auto-assigned numeric identifier, display title, target URL, optional reference to a containing folder (null = root level), creation timestamp, sort-order index within its parent.
- **BookmarkFolder**: a named container that organizes bookmarks and can itself be nested inside another folder. Attributes: auto-assigned numeric identifier, display name, optional reference to a parent folder (null = root level), creation timestamp.
- **HistoryEntry**: a single visit event — the user visited a particular URL at a particular time. Each visit is its own row; revisiting the same URL produces a new row rather than updating an existing one. Attributes: auto-assigned numeric identifier, URL, page title at time of visit, visit timestamp.
- **Tab**: a persisted, non-incognito browser tab in the user's open-tab list. Attributes: auto-assigned numeric identifier, URL, page title, position index in the tab list, creation timestamp, last-active timestamp. (Incognito tabs are explicitly out of scope for persistence.)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Cold-start database open (empty database, fresh install) completes in under 200 ms on a Pixel 5-class device, measured from data-layer construction call to first successful query.
- **SC-001b**: Under the v1.0 power-user scale envelope (10,000 bookmarks, 100,000 history rows, 500 tabs), the following indexed queries each return results in under 100 ms on a Pixel 5-class device, verified by benchmark or instrumented test once the consuming spec ships: history list newest-first paginated 50 rows; bookmarks-by-folder list (any folder, up to 200 children); tab list ordered by position.
- **SC-002**: 100% of CRUD operations across all four entities pass unit tests against an in-memory database instance — minimum 1 happy-path test per DAO method covering insert/update/delete/query-by-id/query-collection/observer-emission.
- **SC-003**: Schema version 1 JSON export is committed to the project's version-controlled schema directory; the file's content is reproducible from a clean build.
- **SC-004**: A deliberate schema mismatch fixture (e.g., declaring schema version 2 with a column drop and no migration) causes database open to fail with an explicit migration-missing error, verified by a negative-path test; no rows are deleted in the fixture run.
- **SC-005**: Foreign-key behavior verified: deleting a BookmarkFolder leaves child Bookmarks with null `parent_folder_id` (orphan-to-root), verified by unit test; the same applies to nested folder deletion.
- **SC-006**: Concurrent insert from two coroutines on different DAOs (one bookmark, one history) under WAL journal mode succeeds without deadlock or data loss within a 1-second test budget.
- **SC-007**: APK release size delta versus Spec 004 baseline is at most 1 MB (Constitution §IX 16KB-safe budget unaffected — chosen persistence library is Kotlin/Java-only with zero new native shared libraries).
- **SC-008**: Project's static analysis suite (Lint + Detekt + ktlint) reports zero new violations and zero net new entries to the Detekt baseline file after this spec lands.
- **SC-009**: Reactive collection observers emit a fresh snapshot following any insert / update / delete on the underlying table within the default Turbine assertion timeout (1 second) in a unit test environment, verified by `awaitItem()` rather than polling. Sub-millisecond timing assertions are explicitly NOT required — the invariant under test is *that emission happens*, not how fast the JVM unit-test environment delivers it.
- **SC-010**: A consumer outside the data layer (a test class annotated with the project's DI scope) can obtain any of the four data-access objects via dependency injection and execute one round-trip insert/query without touching database-builder code, verified by an integration smoke test.
- **SC-011**: Android backup rule files (`backup_rules.xml` and `data_extraction_rules.xml`) explicitly exclude the database file from both cloud backup and device-to-device transfer; verified by reviewing the rule files at PR time and asserted by a build-time check (e.g., a unit test or lint rule that fails if the database file becomes implicitly backed up).

## Assumptions

- **Persistence library choice is Room (already named in project context)**: This spec assumes the project will adopt Room as the persistence engine, consistent with prior architectural decisions. Spec 005's plan phase will pin the exact version against `central.sonatype.com` per Constitution §IX (no remembered version numbers).
- **No Repository/Mapper/Domain layer in this spec**: The Clean Architecture domain layer (`domain/model/`, `domain/repository/`, mappers) and data-layer Repository implementations are explicitly out of scope. They will be authored in the consuming feature specs (013 bookmarks, 014 history, 011 tabs) under the principle of incremental scope (per user's standing preference: "files added only when current spec needs them").
- **No favicon storage**: `favicon_url` on HistoryEntry is deferred to a later spec when favicon-loading infrastructure (image pipeline) is introduced. Adding the column now would create schema deadweight.
- **No incognito persistence**: Incognito tabs are explicitly excluded from the schema; persistence-by-omission is the policy, enforced by the consuming spec rather than a column-level flag.
- **Schema export directory under version control**: The exported schema JSON files live in a tracked path so reviewers can see schema evolution at PR time. The exact directory path is an implementation detail for the plan phase.
- **No data seeding**: First-launch database is empty. Onboarding (Spec 018) decides whether to seed default bookmarks; this spec ships only the empty schema.
- **No encryption at rest**: SQLCipher / encrypted storage is out of scope for v1.0 — Android's full-disk encryption is treated as sufficient for a no-account browser. Future encryption can be layered without schema changes.
- **No cross-process database access**: A single application process owns the database. ContentProvider exposure to other apps is out of scope and explicitly disallowed by Constitution privacy stance.
- **No data retention / pruning policy in this spec**: History and tab tables grow without automatic trimming in v1.0; pruning is the consuming spec's concern. The power-user scale envelope (FR-017) is a design target, not an enforced cap — the schema does not reject inserts past those numbers.
- **Test environment uses in-memory database**: Unit tests rely on an in-memory database fixture; instrumented tests against a real on-device database are deferred (in alignment with the project-wide instrumented-test job being disabled until Spec 007 / 011).
- **Constitution §IX 16KB compliance verified by library-only review**: Room ships as Kotlin/Java code (no native shared libraries); the existing 16KB CI gate will continue to find only the pre-existing `libandroidx.graphics.path.so` from Compose, which is already aligned. No new native libraries will be introduced by this spec.
