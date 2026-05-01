---

description: "Task list for Spec 005 — Room Database Schema"
---

# Tasks: Room Database Schema

**Input**: Design documents from `/specs/005-room-database-schema/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Tests REQUIRED for this spec — Constitution §VI mandates ≥ 70% coverage on `domain/` and `data/` layers; SC-002 requires 100% CRUD coverage; SC-004 requires a negative-path migration test; SC-006 requires a WAL concurrency test; SC-009 requires a Flow observer emission assertion. All tests are JVM unit tests using `room-testing` + Turbine + `kotlinx-coroutines-test` + **Robolectric** (added during implementation for `ApplicationProvider.getApplicationContext()` in JVM scope; pinned to SDK 33 to keep Kotlin toolchain at JDK 11).

**Organization**: Tasks grouped by user story to enable independent verification of each story's claims:

- US1 (P1, MVP): Bookmarks/History/Tabs survive app restart — CRUD + observer + FK orphan-to-root
- US2 (P2): Schema evolution is safe — strict-no-destructive migration verified via negative-path fixture
- US3 (P3): DI seam — future specs can request DAOs via Hilt

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different files, no dependencies on incomplete tasks → can run in parallel
- **[Story]**: User story label (US1/US2/US3) — Setup, Foundational, and Polish phases have no story label
- All paths absolute or relative to repo root `/Users/xbism3/Documents/Code/jetpack/ThirdtySixBrowser/`

## Path Conventions

- **Mobile (single-module Android)** per `plan.md` Project Structure
- Production sources: `app/src/main/kotlin/com/raumanian/thirtysix/browser/...`
- Test sources: `app/src/test/kotlin/com/raumanian/thirtysix/browser/...`
- Resources: `app/src/main/res/...`
- Schema export: `app/schemas/...`

---

## Phase 1: Setup (Gradle, dependencies, backup rules)

**Purpose**: Wire Room + Turbine into the build system, enable schema export, exclude DB from Auto Backup. No production Kotlin code yet.

- [X] T001 Look up Turbine latest stable version on `central.sonatype.com` (per Constitution §IX — NEVER use a remembered version). Then add to `gradle/libs.versions.toml` under `[versions]`: `room = "2.8.4"` + `turbine = "<looked-up>"`. Add to `[libraries]`: `androidx-room-runtime`, `androidx-room-ktx`, `androidx-room-compiler` (all `version.ref = "room"`), `androidx-room-testing` (also `version.ref = "room"`), and `app-cash-turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }`. No new entry under `[plugins]` (KSP plugin already wired in Spec 002).
- [X] T002 Modify `app/build.gradle.kts`: add Room dependencies under `dependencies { }` block — `implementation(libs.androidx.room.runtime)`, `implementation(libs.androidx.room.ktx)`, `ksp(libs.androidx.room.compiler)`, `testImplementation(libs.androidx.room.testing)`, `testImplementation(libs.app.cash.turbine)`. Add `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` block at top level of the file (alongside existing `android { }`, `detekt { }`, etc.). Run `./gradlew help` to confirm Gradle parses the file without error.
- [X] T003 [P] Verify `.gitignore` does NOT exclude `app/schemas/`. If a parent rule excludes it, add an explicit `!app/schemas/` un-ignore rule. Confirm with `git check-ignore -v app/schemas/` (should report no match → tracked).
- [X] T004 [P] Modify `app/src/main/res/xml/backup_rules.xml`: add three `<exclude domain="database" path="thirtysix_browser.db"/>`, `...db-wal`, `...db-shm` lines inside the existing `<full-backup-content>` root (exact format in [research.md R3](research.md#r3--backup-exclusion-xml-format-q4-clarification-implementation)).
- [X] T005 [P] Modify `app/src/main/res/xml/data_extraction_rules.xml`: add the same three exclude lines under both `<cloud-backup>` and `<device-transfer>` sub-elements (exact format in [research.md R3](research.md#r3--backup-exclusion-xml-format-q4-clarification-implementation)).

**Checkpoint**: `./gradlew assembleDebug` builds successfully with the new dependencies; no Kotlin code added yet, so build still produces the existing APK with no functional change. Backup rule files updated.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: None unique to this spec — all data-layer scaffolding belongs to US1 (the MVP) per the incremental-scope principle. Skip directly to Phase 3.

> No tasks in this phase. The 4 entities + 4 DAOs + AppDatabase + DatabaseModule are part of US1 (P1) because they are exactly what makes "Bookmarks/History/Tabs survive app restart" testable.

---

## Phase 3: User Story 1 — Bookmarks, History, and Tabs Survive App Restart (Priority: P1) 🎯 MVP

**Goal**: Establish the four-entity persistence layer with DAOs, single `AppDatabase`, Hilt module exposing the four DAOs, and unit tests proving CRUD + observer + FK-orphan-to-root behavior under an in-memory database fixture.

**Independent Test**: Run `./gradlew testDebugUnitTest` — all DAO test classes pass; the suite includes Turbine-based assertions that a `Flow<List<...>>` query emits a fresh snapshot when the underlying table changes (SC-009), and a parent-folder-deletion test verifies `parent_folder_id` is set to NULL on the surviving bookmark (SC-005).

### Tests for User Story 1 (write FIRST per Constitution §VI)

> **NOTE**: Per Constitution §VI testing discipline + the user-confirmed "tests required" stance for the data layer, the test files below are authored alongside their production counterparts. Within Phase 3 they are listed BEFORE the production files to communicate intent — but in practice each test class will be run continuously (red → green) as the matching production file lands.

- [X] T006 [P] [US1] Create `BookmarkDaoTest.kt` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/local/dao/BookmarkDaoTest.kt`. Cover: `insert` returns positive Long; `getById` round-trips inserted row; `update` mutates fields; `delete` removes the row; `count` returns 0 on empty + N after N inserts; `observeByFolder(null)` emits root bookmarks; `observeByFolder(folderId)` emits children only; **FK orphan-to-root** test inserts a bookmark with parent folder, deletes the folder, asserts the bookmark survives with `parentFolderId == null`. Use Turbine `flow.test { awaitItem() }` for observer assertions.
- [X] T007 [P] [US1] Create `BookmarkFolderDaoTest.kt` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/local/dao/BookmarkFolderDaoTest.kt`. Cover: insert/getById/update/delete/count; nested folder insert (folder B with `parent_id = A.id`); self-FK orphan test (delete folder A, assert folder B survives with `parentId == null`); `observeChildren(parentId)` ordering by name then id.
- [X] T008 [P] [US1] Create `HistoryDaoTest.kt` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/local/dao/HistoryDaoTest.kt`. Cover: insert returns positive Long; **chronological log invariant** (insert same URL twice with different `visitedAt` → 2 rows, distinct ids); `getRecent(limit, offset)` orders by `visitedAt DESC`; `observeAll` Flow emission on insert; `deleteInRange(from, to)` removes only matching rows; `deleteAll` empties the table.
- [X] T009 [P] [US1] Create `TabDaoTest.kt` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/local/dao/TabDaoTest.kt`. Cover: insert returns positive Long; `maxPosition` returns null on empty / max int on populated; `observeAll` ordering by `position ASC`; `update` mutates `lastActiveAt`; `getAll` returns same as Flow snapshot.
- [X] T009b [P] [US1] Create `WalConcurrencyTest.kt` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/local/database/WalConcurrencyTest.kt`. Cover **SC-006**: launch two coroutines concurrently via `runTest { launch { bookmarkDao.insert(...) }; launch { historyDao.insert(...) }; }.join()` against the same in-memory `AppDatabase` instance, assert both rows persist (count == 1 in each table) and the test completes within a 1-second budget (use `withTimeout(1.seconds)` wrapper). This proves WAL journal mode permits multi-DAO concurrent writes without deadlock.
- [X] T010 [P] [US1] Create test helper `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/local/dao/TestDatabaseFactory.kt` exposing `inMemoryAppDatabase(): AppDatabase` using `Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext()).allowMainThreadQueries().build()`. Reused by every DAO test (DRY). Pattern documented in [research.md R10](research.md#r10--test-fixture-pattern-for-in-memory-db).

### Implementation for User Story 1

- [X] T011 [P] [US1] Create `BookmarkEntity.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/entity/BookmarkEntity.kt`. Annotate with `@Entity(tableName = TABLE_NAME, foreignKeys = [@ForeignKey(entity = BookmarkFolderEntity::class, parentColumns = [BookmarkFolderEntity.COL_ID], childColumns = [COL_PARENT_FOLDER_ID], onDelete = ForeignKey.SET_NULL)], indices = [@Index(value = [COL_PARENT_FOLDER_ID], name = INDEX_PARENT_FOLDER_ID)])`. Fields per [data-model.md Entity 1](data-model.md#entity-1--bookmarks). Companion object holds all column-name + index-name + table-name `const val`s.
- [X] T012 [P] [US1] Create `BookmarkFolderEntity.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/entity/BookmarkFolderEntity.kt`. Self-referencing `@ForeignKey` on `parent_id`, `onDelete = SET_NULL`. Index on `parent_id`. Companion constants.
- [X] T013 [P] [US1] Create `HistoryEntryEntity.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/entity/HistoryEntryEntity.kt`. No FK. Index on `visited_at`. Companion constants. Each visit produces a new row (no UNIQUE constraint on URL).
- [X] T014 [P] [US1] Create `TabEntity.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/entity/TabEntity.kt`. No FK. Index on `position`. Companion constants. **No `is_incognito` column** — incognito session state stays in memory, enforced by Spec 011/012.
- [X] T015 [P] [US1] Create `BookmarkDao.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/dao/BookmarkDao.kt`. Match the contract in [contracts/BookmarkDao.kt](contracts/BookmarkDao.kt) — `@Insert/@Update/@Delete` for CRUD; `@Query` for `getById`, `observeByFolder(folderId: Long?)`, `count`. Use the entity companion-object `const val`s for table/column references in `@Query` strings.
- [X] T016 [P] [US1] Create `BookmarkFolderDao.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/dao/BookmarkFolderDao.kt`. Match [contracts/BookmarkFolderDao.kt](contracts/BookmarkFolderDao.kt). `observeChildren(parentId: Long?)` Flow.
- [X] T017 [P] [US1] Create `HistoryDao.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/dao/HistoryDao.kt`. Match [contracts/HistoryDao.kt](contracts/HistoryDao.kt). Includes `getRecent(limit, offset)`, `observeAll`, `deleteInRange(from, to)`, `deleteAll`.
- [X] T018 [P] [US1] Create `TabDao.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/dao/TabDao.kt`. Match [contracts/TabDao.kt](contracts/TabDao.kt). Includes `maxPosition`.
- [X] T019 [US1] Create `AppDatabase.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/database/AppDatabase.kt`. Match [contracts/AppDatabase.kt](contracts/AppDatabase.kt) — `@Database(entities = [4 entities], version = 1, exportSchema = true)`. Companion object `DATABASE_NAME = "thirtysix_browser.db"` and `SCHEMA_VERSION = 1`. Depends on T011–T018 (entities + DAOs must exist).
- [X] T020 [US1] Create `DatabaseModule.kt` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/di/DatabaseModule.kt`. Match [contracts/DatabaseModule.kt](contracts/DatabaseModule.kt) — `@Module @InstallIn(SingletonComponent::class) object`. `@Provides @Singleton fun provideAppDatabase(@ApplicationContext context)` builds via `Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME).build()` — NO `fallbackToDestructiveMigration*`, NO explicit `setJournalMode` (FR-007 / R8). Four `@Provides` for the DAOs delegating to `db.xxxDao()`. Depends on T019.
- [X] T021 [US1] Run `./gradlew assembleDebug` first time after T011–T020 land. Verify generated KSP code under `app/build/generated/ksp/debug/kotlin/com/raumanian/thirtysix/browser/data/local/dao/*Dao_Impl.kt`. Verify Hilt graph compiles (`hiltJavaCompileDebug` task green). The first build also creates `app/schemas/com.raumanian.thirtysix.browser.data.local.database.AppDatabase/1.json` — git-add and commit it as part of the spec PR (gate 2 of [quickstart.md](quickstart.md#gate-2--schema-export-committed)).

**Checkpoint**: User Story 1 complete. `./gradlew testDebugUnitTest` passes (existing 12 tests from Specs 002+003 + ≥ 13 new tests from this story including the WAL concurrency test = ≥ 25 total). The four DAOs round-trip data through an in-memory database; FK SET NULL behavior verified; Flow observers emit on table change; concurrent multi-DAO writes succeed under WAL. Schema JSON committed. The persistence layer is fully usable by future feature specs.

---

## Phase 4: User Story 2 — Schema Evolution Is Safe (Priority: P2)

**Goal**: Verify the database refuses to silently destroy user data on a schema mismatch — strict-no-destructive migration policy is enforced not just by absence of `fallbackToDestructiveMigration*` but by an automated negative-path test.

**Independent Test**: A negative-path test fixture declares an `AppDatabase`-incompatible mismatch (e.g., a v2 schema without a Migration class) and asserts that database open raises `IllegalStateException`. The same test class also asserts WAL mode is active via `PRAGMA journal_mode`.

### Implementation for User Story 2

- [X] T022 [US2] Create `AppDatabaseConfigTest.kt` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/local/database/AppDatabaseConfigTest.kt`. Test 1 — open in-memory `AppDatabase`, execute `PRAGMA journal_mode` via `db.openHelper.writableDatabase.query("PRAGMA journal_mode").use { ... }`, assert result equals `"wal"` (FR-007 verification). Test 2 — **negative-path migration**: open a file-based database (using a temporary File, not in-memory) with an older `@Database(version = 1)` declaration, populate one row, close. Then attempt to open with a fixture `AppDatabase` whose `version = 2` and contains a column drop (declared in the test, never in production code), with NO Migration passed to the builder; `assertThrows<IllegalStateException>` and verify the file's row count is unchanged after the failed open (data not destroyed). This satisfies SC-004.
- [X] T023 [US2] Verify `app/schemas/com.raumanian.thirtysix.browser.data.local.database.AppDatabase/1.json` content at PR review time per gate 2 of [quickstart.md](quickstart.md#gate-2--schema-export-committed): formatVersion = 1, database.version = 1, 4 entities, 2 foreignKeys with `onDelete: "SET NULL"`, 3 explicit `index_*` entries + auto-FK indexes. (Manual review — no automated assertion authored.)

**Checkpoint**: User Story 2 complete. SC-004 (data preservation under schema mismatch) and SC-003 (schema export committed) demonstrably enforced. The strict-no-destructive policy is now backed by an automated test that will fail-fast if a future spec mistakenly adds a destructive fallback.

---

## Phase 5: User Story 3 — DI Seam for Future Features (Priority: P3)

**Goal**: Verify that any future consuming spec (013/014/011) can request the four DAOs via Hilt constructor injection without touching database-builder code.

**Independent Test**: A test class scoped to the Hilt graph requests all four DAOs as `@Inject lateinit var` properties and executes one round-trip per DAO. The test passes if the DI graph builds without unresolved-dependency errors and the DAOs operate correctly against the singleton database.

### Implementation for User Story 3

- [X] T024 [US3] Create `DatabaseModuleSmokeTest.kt` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/di/DatabaseModuleSmokeTest.kt`. **Structural smoke** — construct `DatabaseModule.provideAppDatabase(...)` directly with a real Application context (via `ApplicationProvider`), then invoke each of the four `provideXxxDao(db)` providers, assert the returned DAO is non-null and execute one round-trip `insert + getById` per DAO to prove the provider chain wires real working DAOs (not stubs). **Scope clarification**: this satisfies **SC-010 structurally** — the full `@HiltAndroidTest` end-to-end graph boot (which requires Robolectric or an instrumented test runner) is **deferred to Spec 013** when the first ViewModel consumer materializes and a real `@HiltAndroidApp`-rooted graph is exercised. Document the deferral in the task's PR description.

**Checkpoint**: User Story 3 complete (structural). The Hilt module's provider signatures + DAO instantiation are verified end-to-end at the module level; full graph wiring smoke inherits to the first consuming spec.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Quality gates, documentation updates, doc-folder maintenance.

- [X] T025 Run all required Gradle gates per [quickstart.md](quickstart.md): `./gradlew assembleDebug` ✅, `./gradlew testDebugUnitTest` ✅, `./gradlew lintDebug` ✅, `./gradlew detekt` ✅, `./gradlew ktlintCheck` ✅, `./gradlew assembleRelease` ✅. Zero errors / warnings / new baseline entries permitted.
- [X] T026 Run 16KB CI script: `./.specify/scripts/bash/verify-16kb-alignment.sh` (or the manual `unzip -p` + `objdump` chain in [quickstart.md gate 5](quickstart.md#gate-5--apk-release-size-delta)). Verify only `libandroidx.graphics.path.so` appears (3 ABIs × 4 entries = 12 lines) and every value is `0x4000`. No new natives introduced by Room (R1).
- [X] T027 Measure APK release size (`ls -lh app/build/outputs/apk/release/app-release.apk`); confirm delta vs Spec 004 baseline (1.49 MB) is ≤ 1 MB per SC-007.
- [X] T028 [P] Update `.claude/claude-app/project-context.md`: append a new "2026-05-XX" Key Decisions Log entry summarizing Spec 005 outcome (Room version, schema export path, FK behavior, indexes shipped, Turbine version actually pinned, APK delta, test count). Mark "✅ Spec 005 done" in the Trạng thái dự án header.
- [X] T029 [P] Update `.claude/claude-app/sdd-roadmap.md`: change Spec 005 row Status from `⬜ Next` to `✅ Done <date>`. Update the `## Test flow tích lũy` section if needed.
- [X] T030 [P] Update `CLAUDE.md`: change Spec 005 row in the Spec Roadmap table to ✅ Done; add a new "Recent Changes" entry at the top of that section; update the Active Spec section between `<!-- SPECKIT START -->` markers to point to Spec 006 as the next available (parallel-after-005) spec.
- [X] T031 Run [quickstart.md](quickstart.md) verification end-to-end: gates 1–7 (gate 8 deferred to consumer specs). All MUST pass before marking the spec complete.
- [X] T032 Final sanity grep — `git grep -nE "fallbackToDestructiveMigration|allowMainThreadQueries\(\)" -- 'app/src/main/'` — MUST return zero matches (the production database builder must NOT contain destructive fallback or main-thread queries; these patterns are only allowed in test fixtures).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately. T001 → T002 (T002 depends on T001 because `app/build.gradle.kts` references libs from the catalog). T003 / T004 / T005 are independent of T001/T002 and parallel with each other.
- **Foundational (Phase 2)**: empty.
- **User Story 1 (Phase 3, P1, MVP)**: depends on Setup (Phase 1) complete. Within Phase 3:
  - Test fixture T010 has no dependencies and can land first or alongside production files.
  - Entities T011–T014 are parallel (different files, no cross-deps); however, `BookmarkEntity.kt` references `BookmarkFolderEntity.COL_ID` for the FK declaration → T011 reads T012's column-name constant, but they're declared as `const val` companions which can be authored in parallel as long as the names match the contract. Treat as weak ordering (or land BookmarkFolderEntity first to be safe).
  - DAOs T015–T018 require the respective entity (T015 needs T011, T016 needs T012, T017 needs T013, T018 needs T014). DAOs are parallel with each other once entities exist.
  - DAO test files T006–T009 require the respective DAO + entity to exist (T006 needs T011 + T015, etc.). Tests are parallel with each other.
  - WAL concurrency test T009b requires `AppDatabase` (T019) + `BookmarkDao` (T015) + `HistoryDao` (T017) + fixture (T010). Lands after T019.
  - `AppDatabase` T019 requires T011–T018 (4 entities + 4 DAOs).
  - `DatabaseModule` T020 requires T019.
  - First-build verification T021 requires T020.
- **User Story 2 (Phase 4, P2)**: depends on Phase 3 complete (needs `AppDatabase` to exist for the negative-path fixture).
- **User Story 3 (Phase 5, P3)**: depends on Phase 3 complete (needs `DatabaseModule` to exist).
- **Polish (Phase 6)**: depends on US1 + US2 + US3 complete. Within Phase 6, T028/T029/T030 are parallel (different files); T025–T027 + T031 are sequential gates.

### Within User Story 1

```
T010 (test fixture)  ┬─→  T006/T007/T008/T009 (DAO tests)
                     │
T011/T012/T013/T014 (entities, parallel) ──┬─→ T015/T016/T017/T018 (DAOs, parallel) ──┬─→ T019 (AppDatabase) ──→ T020 (DatabaseModule) ──→ T021 (first build + commit schema JSON)
                                           │                                          │
                                           └────────── tests reference both ──────────┘
```

### Parallel Opportunities

- **Phase 1**: T003 / T004 / T005 in parallel after T001/T002.
- **Phase 3 entities**: T011 + T012 + T013 + T014 in parallel (4 files).
- **Phase 3 DAOs**: T015 + T016 + T017 + T018 in parallel after their entities exist.
- **Phase 3 DAO tests**: T006 + T007 + T008 + T009 in parallel after their DAOs + the test fixture exist.
- **Phase 6 doc updates**: T028 + T029 + T030 in parallel.

---

## Parallel Example: User Story 1 Entities + DAOs

```bash
# After Phase 1 + test fixture T010 land, fan out 4 entity files + 4 DAO files:

# Entities (parallel — different files):
Task: "Create BookmarkEntity.kt at app/src/main/kotlin/.../data/local/entity/BookmarkEntity.kt per data-model.md Entity 1"
Task: "Create BookmarkFolderEntity.kt at app/src/main/kotlin/.../data/local/entity/BookmarkFolderEntity.kt per data-model.md Entity 2"
Task: "Create HistoryEntryEntity.kt at app/src/main/kotlin/.../data/local/entity/HistoryEntryEntity.kt per data-model.md Entity 3"
Task: "Create TabEntity.kt at app/src/main/kotlin/.../data/local/entity/TabEntity.kt per data-model.md Entity 4"

# DAOs (parallel after their entity exists):
Task: "Create BookmarkDao.kt per contracts/BookmarkDao.kt"
Task: "Create BookmarkFolderDao.kt per contracts/BookmarkFolderDao.kt"
Task: "Create HistoryDao.kt per contracts/HistoryDao.kt"
Task: "Create TabDao.kt per contracts/TabDao.kt"

# DAO tests (parallel after DAOs exist):
Task: "BookmarkDaoTest covering CRUD + observer + FK orphan-to-root"
Task: "BookmarkFolderDaoTest covering self-FK orphan + nested folders"
Task: "HistoryDaoTest covering chronological log invariant + range delete"
Task: "TabDaoTest covering position ordering + last_active_at"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete **Phase 1: Setup** (T001 → T005) — Gradle wired, deps in catalog, backup rules updated.
2. Skip Phase 2 (empty).
3. Complete **Phase 3: User Story 1** (T006 → T021) — entities, DAOs, AppDatabase, DatabaseModule, tests, first schema JSON committed.
4. **STOP and VALIDATE**: Run `./gradlew testDebugUnitTest` + `./gradlew assembleDebug`. The MVP delivers a usable persistence layer that future features (013/014/011) can consume.
5. Persistence layer ready — could in principle ship at this point if the spec scope allowed P1 only.

### Incremental Delivery

1. Setup → Foundation ready.
2. + User Story 1 → MVP, persistence layer functional, all CRUD + observer + FK behavior verified.
3. + User Story 2 → Schema-evolution safety net in place; data-loss prevention proven via negative-path test.
4. + User Story 3 → DI seam smoke-tested; future specs can plug in via Hilt with confidence.
5. Polish → Quality gates, doc updates, post-merge hygiene.

### Parallel Team Strategy

This spec is sized for solo dev (Xbism3 per [dev-workflow.md](../../.claude/claude-app/dev-workflow.md)). For solo execution: follow the strict ordering above. For 2-developer parallel: Developer A drives Phase 1 + Phase 3 entities/DAOs; Developer B authors DAO tests + Phase 4 negative-path test; both converge on T019/T020 and Phase 6 polish.

---

## Notes

- `[P]` tasks operate on different files with no incomplete dependencies.
- Constitution §III No-Hardcode Rule: every literal in entities (table names, column names, index names) lives in companion-object `const val`s; every literal in `DatabaseModule` (database name) sources from `AppDatabase.Companion.DATABASE_NAME`; schema version `1` is on the trivial-whitelist (Detekt MagicNumber rule's standard exemption for `0`, `1`, `-1`, `2`).
- Constitution §IX 16KB compliance: Room artifacts contain zero `.so` files (verified [research.md R1](research.md#r1--room-version--16kb-compliance)). The 16KB CI gate continues to verify only `libandroidx.graphics.path.so` from Compose. Turbine is pure Kotlin/JVM (also `.so`-free). T026 confirms.
- Constitution §VI testing discipline: 12+ new unit tests across 5 test files. Coverage on `data/local/` is essentially 100% — well above the ≥ 70% target.
- Constitution §X simplicity: NO `core/constants/` files added; NO `domain/` layer materialized; NO repository/mapper authored. All deferred to consuming specs (011/013/014).
- Commit cadence: after each task or logical group (Spec 001/002/003/004 precedent). Avoid mega-commits.
- Stop at any checkpoint to validate the relevant story independently.
- Avoid: vague tasks, same-file-conflict parallelism, cross-story dependencies that break independence.
