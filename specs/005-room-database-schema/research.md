# Research: Room Database Schema

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date verified**: 2026-05-01

Phase 0 research consolidating decisions for the persistence layer implementation. Each entry follows the format: **Decision** / **Rationale** / **Alternatives considered**.

---

## R1 — Room version + 16KB compliance

**Decision**: Adopt **Room 2.8.4** (released 2025-11-19), pulled in via four Maven coordinates:

| artifact | scope | reason |
|---|---|---|
| `androidx.room:room-runtime:2.8.4` | `implementation` | core API |
| `androidx.room:room-ktx:2.8.4` | `implementation` | Kotlin coroutines + `Flow` extensions |
| `androidx.room:room-compiler:2.8.4` | `ksp` | annotation processor (KSP2) |
| `androidx.room:room-testing:2.8.4` | `testImplementation` | in-memory DB + migration test helpers |

**Rationale**:

- Latest stable per `developer.android.com/jetpack/androidx/releases/room` verified 2026-05-01.
- Compatible with project's existing **Kotlin 2.3.21 + KSP 2.3.7 + AGP 9.1.1**: Room targets Kotlin 2.0+ since 2.7.0-alpha13; KSP2 supported and recommended; minimum AGP 8.4 (project at 9.1.1 — comfortably above).
- Kotlin codegen ON by default since Room 2.7.0 — generated DAOs are `.kt`, not `.java`. No `.java` reflection action items.
- minSdk floor raised to API 23 since 2.8.0-rc02 — project minSdk 24 is unaffected.
- **All four artifacts are pure Kotlin/Java with zero native shared libraries.** Room 2.x runtime delegates to the platform's `android.database.sqlite` engine which ships as part of the Android OS, not the APK. The optional `androidx.sqlite:sqlite-bundled` artifact would ship native code, but it is NOT pulled by `room-runtime`/`room-ktx`/`room-compiler`/`room-testing`. Constitution §IX 16KB CI gate auto-passes.
- Bound all four artifacts to a single `room` version key in `gradle/libs.versions.toml` so they stay in lockstep across future bumps.

**Alternatives considered**:

- **Room 3.0 alpha** (announced March 2026 under new `androidx.room3:room3-*` coordinates): rejected — alpha-only as of 2026-05-01, violates Constitution §IX hard requirement of "latest **stable** version". Room 2.x is in maintenance mode with continued patch releases; v1.0 will remain on 2.8.x and re-evaluate when Room 3.x reaches stable.
- **Room 2.7.x** (last 2.7 release 2.7.5): rejected — 2.8.4 is one patch line newer with security/perf updates; no breaking-change migration cost since the project is greenfield for persistence.
- **`room-paging:2.8.4`**: NOT included — paging is unused at v1.0 (lists are bounded by power-user envelope FR-017; Spec 014 history-view will decide when/if to introduce pagination).
- **SQLDelight**: rejected — Constitution names Room directly in §VII / `project-context.md` packages-dự-kiến table. Switching engine families is out of charter.

**Source**: [developer.android.com/jetpack/androidx/releases/room](https://developer.android.com/jetpack/androidx/releases/room) — verified 2026-05-01.

---

## R2 — Schema export directory + git tracking

**Decision**: Schema JSON snapshots exported to **`app/schemas/`** (tracked in git), wired via Gradle `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` block in `app/build.gradle.kts`. The exported file path follows Room convention:

```
app/schemas/com.raumanian.thirtysix.browser.data.local.database.AppDatabase/1.json
```

**Rationale**:

- Default convention for Room — review tools, future migrations, and `MigrationTestHelper` all expect this layout.
- `$projectDir` resolves to `<repo>/app/` since the Gradle DSL block runs in `app/build.gradle.kts`'s context — produces `app/schemas/` at the repo level.
- Tracked in git (NOT in `.gitignore`) so reviewers can audit schema evolution at PR time and future migration code can compare against the known prior version on disk (FR-010, SC-003).
- For migration tests, `MigrationTestHelper` consumes the same JSON files via `assets` route in instrumented tests OR via `$projectDir/schemas` for unit tests with `roomCompiler` configured to also copy to `androidTest/assets`. Spec 005 ships only unit-test usage; instrumented migration tests deferred until first real schema bump (Spec post-v1.0).

**Alternatives considered**:

- **`build/schemas/`** (default if `room.schemaLocation` not set): rejected — `build/` is git-ignored; schemas would not survive clean builds and reviewers couldn't see them.
- **Per-module schemas (e.g., `app/src/main/schemas/`)**: rejected — `room.schemaLocation` is a global processor argument, not per-source-set; one location per module is idiomatic.

**Action items**:

- Confirm `.gitignore` does NOT exclude `app/schemas/`.
- The 1.json file appears after the first build; commit it as part of Spec 005's PR.

---

## R3 — Backup exclusion XML format (Q4 clarification implementation)

**Decision**: Update `app/src/main/res/xml/backup_rules.xml` and `app/src/main/res/xml/data_extraction_rules.xml` to explicitly exclude the database file and its WAL sidecars from both cloud backup and device-to-device transfer.

**`backup_rules.xml`** (Android < 12 Auto Backup format):

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="database" path="thirtysix_browser.db"/>
    <exclude domain="database" path="thirtysix_browser.db-wal"/>
    <exclude domain="database" path="thirtysix_browser.db-shm"/>
</full-backup-content>
```

**`data_extraction_rules.xml`** (Android 12+ format with separate cloud-backup and device-transfer scopes):

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="thirtysix_browser.db"/>
        <exclude domain="database" path="thirtysix_browser.db-wal"/>
        <exclude domain="database" path="thirtysix_browser.db-shm"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" path="thirtysix_browser.db"/>
        <exclude domain="database" path="thirtysix_browser.db-wal"/>
        <exclude domain="database" path="thirtysix_browser.db-shm"/>
    </device-transfer>
</data-extraction-rules>
```

**Rationale**:

- The Android Auto Backup framework follows two file formats depending on minSdk window: Android 6–11 honor `backup_rules.xml` (the `<full-backup-content>` schema); Android 12+ honor `data_extraction_rules.xml` (which separates cloud backup from device transfer). Both must declare exclusion to fully cover the 24+ minSdk range.
- `domain="database"` resolves to `/data/data/<package>/databases/` — the framework-default DB path used by Room.
- WAL journal mode produces two sidecar files (`-wal`, `-shm`) that are recreated on database open but contain transient transaction data. Excluding them prevents partial-database backup edge cases.
- Q4 clarification chose Option B (exclude from Auto Backup) for privacy-first stance.

**Alternatives considered**:

- **Disable Auto Backup entirely** (`android:allowBackup="false"` in manifest): rejected — too broad; future opt-in features (e.g., user-triggered export of bookmarks) still want the rest of the app's storage to behave normally. Targeted exclusion is more surgical.
- **Encrypt-then-allow-backup**: rejected — adds SQLCipher dependency, out of v1.0 scope per spec Assumptions.

**Verification**: SC-011 — a unit test asserts the rule file content includes the three exclude lines per format; a Lint custom check is overkill for v1.0 — review-time gate is sufficient.

---

## R4 — Foreign key cascade rules

**Decision**: All foreign keys use **`onDelete = ForeignKey.SET_NULL`** with the column declared nullable.

- `BookmarkEntity.parent_folder_id` → `BookmarkFolderEntity.id` `ON DELETE SET NULL`
- `BookmarkFolderEntity.parent_id` → `BookmarkFolderEntity.id` (self-reference) `ON DELETE SET NULL`

**Rationale**:

- Q (spec edge cases) chose orphan-to-root over cascade-delete to **preserve user data** — the privacy-first browser holds the user's only copy. A user clicking "delete folder" expecting to remove the container without realizing it nukes contents is a single-action data loss; orphan-to-root preserves the underlying bookmarks at the root level.
- Room enables foreign-key constraint enforcement automatically when the entity is annotated with `@ForeignKey` — no additional `setForeignKeyConstraintsEnabled(true)` call needed.
- `SET NULL` is part of standard SQLite FK actions; no custom trigger code required.

**Alternatives considered**:

- **`CASCADE`**: rejected — silent data loss risk.
- **`RESTRICT`**: rejected — UX nightmare ("you can't delete this folder because it has bookmarks"); forces user to manually empty before delete.
- **`NO ACTION`**: equivalent to RESTRICT for SQLite immediate-mode FK; same rejection.
- **Soft-delete with `deleted_at` column**: rejected — out of scope; consuming spec (013) can layer a "trash" feature later if needed.

---

## R5 — Indexes shipped at schema version 1

**Decision**: Three explicit indexes ship with v1.0 per Q5 power-user envelope:

| Table | Column(s) | Reason | Used by |
|-------|-----------|--------|---------|
| `history_entries` | `visited_at` (DESC) | History list newest-first paginated query | Spec 014 |
| `bookmarks` | `parent_folder_id` | Folder content browse (`SELECT * FROM bookmarks WHERE parent_folder_id = ?`) | Spec 013 |
| `tabs` | `position` | Tab list ordering (`SELECT * FROM tabs ORDER BY position`) | Spec 011 |

Plus the implicit primary-key indexes on `id` for all four entities (auto-created by SQLite).

Plus the implicit foreign-key indexes auto-created by Room when `@ForeignKey` is declared (Room emits warnings if a FK column lacks an index — declaring them explicitly silences the warning and makes the intent visible).

**Rationale**:

- Three queries above are the hot lookup paths for the consuming specs already on the roadmap.
- Without these indexes, queries against the power-user envelope (100K history rows, 10K bookmarks, 500 tabs) degrade to full-table scans; SC-001b would not be achievable.
- Adding indexes after the fact requires a schema migration even if no column shape changes — costs are paid up front.

**Alternatives considered**:

- **No indexes (PK/FK only)**: rejected — Q5 chose Option B; PK/FK indexes alone would not satisfy SC-001b under power-user load.
- **Composite index** `(parent_folder_id, sort_order)` for bookmarks: candidate but DEFERRED until Spec 013 confirms the actual SELECT pattern (likely needs sort_order ASC tied to the folder filter — an additional index can be added in Spec 013 via a v2 schema migration); v1.0 ships only the parent_folder_id index to avoid over-indexing.
- **Full-text search index on `bookmark.title` / `history.title`**: rejected — search UX deferred to Specs 013/014; FTS adds non-trivial schema complexity (`@Fts4` virtual table, separate row IDs) that is premature at v1.0.

---

## R6 — Turbine for Flow assertions in DAO unit tests

**Decision**: Add **`app.cash.turbine:turbine`** to `gradle/libs.versions.toml` under `testImplementation` scope; version pinned at plan-time per Constitution §IX.

**Latest stable**: To be looked up at implementation time (Constitution §IX: "Before adding any package, the latest stable version MUST be looked up at that exact moment — NEVER use remembered, guessed, or previously researched version numbers, even from the same session"). Source: `https://github.com/cashapp/turbine/releases`.

**Rationale**:

- Room's `Flow<List<...>>` query return type emits a fresh snapshot when the underlying table changes. Asserting that emission in a test requires either:
  1. **Turbine** — `flow.test { awaitItem() shouldBe ... }` — clean, idiomatic, well-maintained
  2. Hand-rolled `runTest` + `launch` + `Channel` — boilerplate-heavy, easy to write subtly broken tests
- SC-009 (Flow observer emits within 50ms of table change) is much easier to assert with Turbine's `awaitItem()` + `expectNoEvents()` operators.
- Turbine is JVM-only (no `.so`) — Constitution §IX 16KB gate auto-passes. Pure Kotlin library.
- Used widely in Compose-MVVM projects; idiomatic for `kotlinx-coroutines-test`.

**Alternatives considered**:

- **No Turbine, use raw `runTest { collect { } }`**: feasible but error-prone; bug-prone test code is a worse trade than one extra test-scope dependency.
- **Truth's `Subject<Flow>`** (no such API exists): rejected.
- **MockK + slot capture on a Channel**: works but tests become coupled to the test fixture, not the production behavior.

**Action item**: Look up Turbine latest stable on `central.sonatype.com` at the moment of the `libs.versions.toml` edit during implementation.

---

## R7 — KSP processor artifact ID confirmation

**Decision**: KSP processor coordinates are **`androidx.room:room-compiler:2.8.4`** wired via `ksp(libs.androidx.room.compiler)` in `app/build.gradle.kts`.

**Rationale**:

- Confirmed against `developer.android.com/jetpack/androidx/releases/room` 2026-05-01.
- The kapt-era processor `androidx.room:room-compiler` (same artifact ID — Room uses one processor artifact for both kapt and ksp wiring; the build system distinguishes by configuration name) is the correct ID. Using `ksp(...)` configuration with this artifact triggers KSP code generation; using `annotationProcessor(...)` would trigger kapt.
- This mirrors the Spec 002 finding: Hilt's KSP artifact is `com.google.dagger:hilt-compiler` (NOT `hilt-android-compiler` which is kapt-only). Room is friendlier — same artifact ID for both wiring methods, KSP detection is automatic.

**Alternatives considered**:

- **kapt instead of KSP**: rejected — KSP is faster, already wired for Hilt in Spec 002, and Room officially recommends KSP since 2.5.x.
- **`room-runtime` only without compiler**: would fail at build time; entity / DAO compilation requires the compiler.

---

## R8 — WAL journal mode + `setJournalMode` invocation

**Decision**: Rely on Room's default WAL journal mode (active since Room 1.0 on Android 5.0+); do NOT call `setJournalMode(...)` explicitly.

**Rationale**:

- Room 2.x defaults to `JournalMode.WRITE_AHEAD_LOGGING` on API 16+ (the only platforms the project targets are 24+, well above the threshold).
- Explicit `setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)` would be redundant and clutter the database builder.
- FR-007 documents the requirement; the verification mechanism is a unit test that opens the database and asserts `db.openHelper.writableDatabase.execute("PRAGMA journal_mode")` returns `"wal"`.

**Alternatives considered**:

- **`JournalMode.TRUNCATE`** (legacy SQLite default): rejected — single-writer-blocks-readers semantics break SC-006 (concurrent insert test).
- **`JournalMode.AUTOMATIC`**: equivalent to leaving the default; explicit is no clearer than implicit.

---

## R9 — Database file name + path conventions

**Decision**: Database filename `thirtysix_browser.db`, stored under the **framework-default path** (`/data/data/<package>/databases/`). The package suffix differs between debug (`com.raumanian.thirtysix.browser.debug`) and release (`com.raumanian.thirtysix.browser`) builds — the path follows the package automatically. Filename declared as a `const val` on `AppDatabase.Companion`:

```kotlin
@Database(...)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        const val DATABASE_NAME = "thirtysix_browser.db"
    }
}
```

**Rationale**:

- Constitution §III "DB filename" row of the No-Hardcode table mandates the literal live in a constants location. The `AppDatabase.Companion` is the canonical home — both `DatabaseModule` (for the builder) and `backup_rules.xml` rule audits reference the same string indirectly via the codebase.
- Framework-default path keeps the file inside app-private storage automatically isolated from other apps (Constitution §I, FR-016).

**Alternatives considered**:

- **Custom path under `getNoBackupFilesDir()`**: rejected — backup exclusion is already handled by the rule files (R3); changing the path adds no benefit.
- **Adding the filename to `core/constants/AppConstants.kt`**: rejected per Spec 002 incremental-scope precedent — a constant used by exactly one production file (`DatabaseModule`) doesn't warrant a `core/constants/` entry. If a third consumer needs it, promote then.

---

## R10 — Test fixture pattern for in-memory DB

**Decision**: Each DAO test class follows this pattern using `room-testing` + JUnit:

```kotlin
@RunWith(JUnit4::class)
class BookmarkDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var folderDao: BookmarkFolderDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        bookmarkDao = db.bookmarkDao()
        folderDao = db.bookmarkFolderDao()
    }

    @After
    fun tearDown() = db.close()

    // tests …
}
```

**Rationale**:

- `inMemoryDatabaseBuilder` produces a fresh database per test class — no flaky state-leak between tests.
- `allowMainThreadQueries()` removes the off-main-thread enforcement for test simplicity (test threads are not the production main thread; unit-test JVM has no Looper).
- `ApplicationProvider.getApplicationContext()` from `androidx.test:core` (already in catalog as transitive of `androidx.test.ext:junit` v1.3.0).
- Pure JVM unit test — runs under `./gradlew testDebugUnitTest`, no emulator required.

**Alternatives considered**:

- **`Room.databaseBuilder` against a temp file**: rejected — slower, requires cleanup, no isolation guarantee.
- **Robolectric harness**: rejected — already covered by `androidx.test:core` Application context shim; Robolectric adds startup latency.

---

## R11 — Hilt module placement (top-level `app/.../di/` vs `core/di/`)

**Decision**: New `DatabaseModule.kt` lives at **top-level `app/src/main/kotlin/com/raumanian/thirtysix/browser/di/`** (NEW package).

**Rationale**:

- Constitution §IV says ALL `@Module` annotations live in `di/` package. Spec 002 placed `DispatcherModule.kt` under `core/di/` because at that time only the cross-cutting dispatcher abstraction needed wiring; nothing else was being introduced.
- Spec 005's `DatabaseModule` is feature-spanning (4 DAOs serving 3 future features) — the natural home is the application-level `di/` package, parallel to upcoming `RepositoryModule` (013/014) and `NetworkModule` (007).
- Both `core/di/` and `app/.../di/` satisfy the Constitution rule "live in `di/` package"; placement is semantic — `core/di/` for primitives that don't depend on any feature, top-level `di/` for application-wide composites.
- `DispatcherModule.kt` stays put under `core/di/` — no migration churn.

**Alternatives considered**:

- **Place `DatabaseModule` under `core/di/`**: rejected — would invert the layering intent (database wiring is application-scoped, not core primitive).
- **Place `DatabaseModule` under `data/local/database/`**: rejected — Constitution §IV says `@Module` MUST be in a `di/` directory.

---

## R12 — Date / time storage type

**Decision**: All timestamp columns (`created_at`, `visited_at`, `last_active_at`) stored as **`Long` Unix epoch milliseconds** (UTC), no `TypeConverter` for `Instant` / `LocalDateTime` at this spec.

**Rationale**:

- Simplest representation; SQLite has no native datetime type and stores everything as REAL or INTEGER under the hood anyway.
- `Long` epoch millis is portable, locale-independent, sortable as integers (cheap index lookups on `visited_at DESC`), and easy to convert at the consumer boundary (Spec 014 will format with `DateFormats.*` per Constitution §III when displaying).
- No `TypeConverter` needed → no `@TypeConverters` annotation on `AppDatabase`, no extra code surface, no extra unit tests for converters.

**Alternatives considered**:

- **`Instant` via `TypeConverter` to ISO-8601 String**: rejected — sorting by string is correct ISO-8601 (lexicographic = chronological) but indexing is more expensive than integer index.
- **`Instant` via `TypeConverter` to Long**: same on-disk shape as Decision but adds a converter file. Premature.

**Action item**: Spec 014 may introduce a `LocalDate` / `LocalDateTime` type converter when formatting, scoped to the consumer; the database column type stays `Long`.

---

## Summary — all questions resolved before Phase 1

| Question | Status | Resolution |
|----------|--------|-----------|
| Room version + 16KB compliance | ✅ | R1: 2.8.4, pure Kotlin/Java, zero `.so` |
| Schema export path | ✅ | R2: `app/schemas/`, tracked in git |
| Backup-rules format | ✅ | R3: both `backup_rules.xml` + `data_extraction_rules.xml` updated |
| FK action | ✅ | R4: `SET_NULL` orphan-to-root |
| Index plan v1.0 | ✅ | R5: 3 explicit indexes (history.visited_at, bookmark.parent_folder_id, tab.position) + FK indexes |
| Flow assertions in tests | ✅ | R6: Turbine (version looked up at implementation time) |
| KSP processor ID | ✅ | R7: `androidx.room:room-compiler:2.8.4` |
| WAL journal mode | ✅ | R8: rely on Room default; verify via PRAGMA assertion |
| DB filename + path | ✅ | R9: `thirtysix_browser.db`, framework-default, declared on `AppDatabase.Companion` |
| Test fixture | ✅ | R10: in-memory DB pattern with `room-testing` |
| Hilt module placement | ✅ | R11: top-level `app/.../di/DatabaseModule.kt` |
| Timestamp storage | ✅ | R12: `Long` epoch millis, no TypeConverter v1.0 |

Zero `[NEEDS CLARIFICATION]` markers remain. Phase 1 design proceeds.
