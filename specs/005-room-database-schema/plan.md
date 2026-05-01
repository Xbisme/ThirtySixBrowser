# Implementation Plan: Room Database Schema

**Branch**: `005-room-database-schema` | **Date**: 2026-05-01 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/005-room-database-schema/spec.md`

## Summary

Spec 005 introduces the on-device persistence layer for ThirtySixBrowser using **Room 2.8.4** (verified `developer.android.com/jetpack/androidx/releases/room` 2026-05-01) with strict-no-destructive migration policy and schema export tracked in version control. Scope is intentionally chunked **data-layer only** — four `@Entity` classes (Bookmark, BookmarkFolder, HistoryEntry, Tab), four `@Dao` interfaces, one `@Database`-annotated `AppDatabase`, one Hilt `DatabaseModule`, plus the Android backup-rule files updated to exclude the database from cloud transfer.

Repository / Mapper / Domain-model authoring is deferred to consuming specs (013 bookmarks, 014 history, 011 tabs) per the user's standing **incremental scope** preference. Spec 005 ships **no `core/constants/`, `domain/`, or `data/repository/` files** — those layers materialize when the first consumer feature lands.

Eight concrete deliverables:

1. **Add** Room dependencies (`room-runtime`, `room-ktx`, `room-compiler` via KSP, `room-testing`) to `gradle/libs.versions.toml` + wire in `app/build.gradle.kts`
2. **Add** Room schema-export Gradle DSL (`ksp { arg("room.schemaLocation", "$projectDir/schemas") }`) and create the `app/schemas/` directory tracked in git
3. **Add** four entity files under `app/src/main/kotlin/.../data/local/entity/` — `BookmarkEntity.kt`, `BookmarkFolderEntity.kt`, `HistoryEntryEntity.kt`, `TabEntity.kt`
4. **Add** four DAO interface files under `data/local/dao/` — `BookmarkDao.kt`, `BookmarkFolderDao.kt`, `HistoryDao.kt`, `TabDao.kt`
5. **Add** `data/local/database/AppDatabase.kt` (`@Database(version = 1, entities = [...], exportSchema = true)`) and database file-name + journal-mode constants
6. **Add** top-level `app/.../di/DatabaseModule.kt` (Hilt `@InstallIn(SingletonComponent::class)`) providing `AppDatabase` singleton + four DAO instances
7. **Add** unit test class per DAO under `app/src/test/.../data/local/dao/` using `Room.inMemoryDatabaseBuilder` + `kotlinx-coroutines-test` + Turbine for Flow assertions
8. **Modify** `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml` to add `<exclude domain="database" path="thirtysix_browser.db"/>` (and matching exclude lines for WAL sidecar files `*-wal`, `*-shm`)

No new packages with native shared libraries → 16KB page size CI gate auto-passes (Room is pure Kotlin/Java; the platform `android.database.sqlite` engine ships as part of the Android OS, not the APK).

## Technical Context

**Language/Version**: Kotlin 2.3.21 (compiled via AGP 9.1.1's built-in Kotlin support); Java 11 target; KSP 2.3.7 (decoupled from Kotlin since KSP 2.3.0).
**Primary Dependencies**: Room 2.8.4 (released 2025-11-19, verified `developer.android.com` 2026-05-01) — four artifacts: `room-runtime`, `room-ktx`, `room-compiler` (KSP processor), `room-testing` (test scope). Hilt 2.59.2 (already in catalog from Spec 002). `kotlinx-coroutines-test` 1.10.2 (already in catalog). `app.cash.turbine:turbine` ⏳ NEW for Flow assertions in DAO tests — version locked at plan time per Constitution §IX (see [research.md R6](research.md)).
**Storage**: On-device app-private SQLite database file `thirtysix_browser.db` under the framework-default path (`/data/data/com.raumanian.thirtysix.browser.{debug?}/databases/`); journal-mode WAL auto-enabled by Room ≥ 2.0 on Android 5.0+; schema export JSON snapshots committed under `app/schemas/com.raumanian.thirtysix.browser.data.local.database.AppDatabase/1.json` per Room convention.
**Testing**: Unit tests via `Room.inMemoryDatabaseBuilder<AppDatabase>(...).allowMainThreadQueries().build()` (test fixture pattern), JUnit 4.13.2, `kotlinx-coroutines-test` `runTest { }` blocks, Turbine for `Flow` `awaitItem()` assertions, Truth (already in catalog) for fluent matchers. **No instrumented tests added by this spec** — instrumented-test job in `.github/workflows/ci.yml` remains `if: false` until Spec 007 / 011.
**Target Platform**: Android 7.0+ (`minSdk = 24`); Room 2.8.x raised its own minSdk to 23 since 2.8.0-rc02 — project minSdk 24 is comfortably above the floor.
**Project Type**: Mobile (single-module Android app, package `com.raumanian.thirtysix.browser`).
**Performance Goals**: SC-001 cold-start DB open < 200 ms on Pixel 5; SC-001b indexed list queries < 100 ms under power-user envelope (10K bookmarks / 100K history / 500 tabs); SC-006 concurrent insert latency < 1 s for two-coroutine WAL test; SC-009 Flow observer emission < 50 ms after table change.
**Constraints**: Constitution §IX 16KB page size — Room artifacts contain zero `.so` files; the existing 16KB CI gate continues to find only the pre-existing `libandroidx.graphics.path.so` from Compose. Constitution §VII offline-first — DB excluded from cloud backup per Q4 clarification (stricter than the constitutional minimum which says backup MAY be opt-in; we choose to default-off entirely). Constitution §III no-hardcode rule — DB filename, journal mode, schemas-directory path, table names, column names all live in compile-time `const val` / companion `object` constants on the entity / database classes.
**Scale/Scope**: Schema version 1 — 4 entities, ~3 indexes (`history.visited_at DESC`, `bookmark.parent_folder_id`, `tab.position`); 4 DAO interfaces × ~6–7 query methods each = ~25 DAO method signatures; 1 Hilt module; ~13 unit tests (4 DAO suites + 1 WAL concurrency test + 1 config/migration test + 1 DI smoke test) authored across 7 test files; ~8 source files net; expected APK release-size delta well under SC-007's 1 MB budget (Room runtime classes compile to ~700 KB typical, dexed and R8-shrunk further).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.2.0 — all 11 principles evaluated.

| # | Principle | Status | Notes |
|---|-----------|--------|-------|
| I | Privacy & Security First | ✅ PASS — **stricter than minimum** | All entity data lives on-device only. DB explicitly **excluded** from Android Auto Backup (FR-016, SC-011) and Device-to-Device Transfer — Q4 clarification chose Option B over the constitutional baseline that merely says "MAY be backed up" (Constitution §VII). No analytics, no transmission, no PII. Search-query stance unaffected (search bar is Spec 009/010). |
| II | Google Play Compliance | ✅ PASS | Room is an AndroidX official library — Play-approved. No new permissions. App stays in Tools/Productivity. WebView and `addJavascriptInterface` unrelated to this spec. |
| III | Code Quality & Safety / No-Hardcode Rule | ✅ PASS | DB filename `thirtysix_browser.db` + journal-mode + schemas-directory string are declared as `const val` on a single companion object (`AppDatabase.Companion`). Table names, column names declared on each `@Entity` companion. **No `core/constants/` files added** — these are entity-internal constants per the user's incremental-scope preference; if a third consumer needs them they'll be promoted. Detekt MagicNumber baseline unchanged (no new numeric literals — version `1` for schema is the trivial whitelist). |
| IV | Clean Architecture (MVVM) | ✅ PASS | All new files live under `data/local/{entity,dao,database}/` (data layer). `domain/` untouched (no model/repository/usecase added). `presentation/` untouched. Hilt `@Module` placed in top-level `app/.../di/` (DatabaseModule), respecting Constitution §IV "ALL @Module annotations live in `di/` package". |
| V | Performance Excellence | ✅ PASS | SC-001 (200ms cold open) + SC-001b (100ms indexed queries) are explicit performance criteria. WAL journal mode + the three indexes ensure the power-user envelope is achievable. No 60fps surface affected (no UI). |
| VI | Testing Discipline | ✅ PASS | ≥ 70% data-layer coverage target — this spec's `data/local/` is 100% testable via in-memory DAO unit tests (no Android system dependencies). 12+ unit tests planned across the 4 DAOs covering CRUD + observer + FK orphan-to-root. Lint, Detekt, ktlint gates unaffected. Negative-path migration test (SC-004) covered by a single fixture-based unit test. |
| VII | Offline-First | ✅ PASS — **stricter than minimum** | All four entities operate fully offline (data is on-device only by definition). WAL journal mode enabled (Room default — verified). Constitution §VII allows backup as opt-in; we choose the stricter exclude-from-backup default (FR-016) consistent with the privacy posture. Process-death recovery via Room/WAL is intrinsic. |
| VIII | Localization & Accessibility | ✅ PASS | No user-facing strings introduced. No UI added. Accessibility n/a at the data layer. |
| IX | Dependency Currency & 16KB Page Size | ✅ PASS | Room 2.8.4 verified latest stable on `developer.android.com/jetpack/androidx/releases/room` on 2026-05-01 ([research.md R1](research.md)). All four Room artifacts are pure Kotlin/Java — **zero `.so` files** introduced. The existing 16KB CI gate continues to verify only `libandroidx.graphics.path.so` from Compose, already aligned 0x4000. Turbine 1.x verification scheduled in [research.md R6](research.md). |
| X | Simplicity & Build Order | ✅ PASS | Spec 005 is in Phase 1 (Foundation), parallel-available with 006 after Spec 002 — chosen first per user direction (sequential 005 → 006 → 007). YAGNI: NO Repository/Mapper/Domain layer scaffolded; NO favicon column; NO incognito column; NO encryption layer; NO data-seeding logic; NO ContentProvider. Each is explicitly out of scope per spec Assumptions. |
| XI | Build Configuration | ✅ PASS | No flavor changes. New deps live in `gradle/libs.versions.toml` under existing keys + new `room` version key. No `BuildConfig` field changes. R8 / minify / shrink rules unaffected (Room generates Kotlin code; default Room ProGuard rules ship with the artifact and are auto-included by AGP). |

**Result**: 11/11 PASS. No Complexity Tracking entries required.

## Project Structure

### Documentation (this feature)

```text
specs/005-room-database-schema/
├── plan.md                       # This file
├── spec.md                       # Feature spec + Clarifications (10 Q&A across two sessions)
├── research.md                   # Phase 0 — Room version, schema-export path, backup-rules format, FK behavior, indexes, Turbine, KSP processor ID
├── data-model.md                 # Phase 1 — Entity tables (columns, types, constraints, FKs, indexes), ER diagram
├── quickstart.md                 # Phase 1 — verification playbook (8 gates)
├── contracts/
│   ├── BookmarkDao.kt            # DAO method signatures (interface contract)
│   ├── BookmarkFolderDao.kt
│   ├── HistoryDao.kt
│   ├── TabDao.kt
│   ├── AppDatabase.kt            # Database class signature
│   └── DatabaseModule.kt         # Hilt module signature
├── checklists/
│   └── requirements.md           # Quality checklist (16/16 PASS post-clarify)
└── tasks.md                      # Phase 2 — generated by /speckit-tasks (NOT this command)
```

### Source Code (repository root)

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── data/                                # NEW directory — first time data/ is materialized
│   └── local/
│       ├── database/
│       │   └── AppDatabase.kt           # NEW — @Database(version = 1, entities = [...], exportSchema = true)
│       ├── entity/
│       │   ├── BookmarkEntity.kt        # NEW — @Entity(tableName = "bookmarks", foreignKeys = [...], indices = [...])
│       │   ├── BookmarkFolderEntity.kt  # NEW — @Entity(tableName = "bookmark_folders", foreignKeys = [self-FK], indices = [...])
│       │   ├── HistoryEntryEntity.kt    # NEW — @Entity(tableName = "history_entries", indices = [...])
│       │   └── TabEntity.kt             # NEW — @Entity(tableName = "tabs", indices = [...])
│       └── dao/
│           ├── BookmarkDao.kt           # NEW — @Dao interface
│           ├── BookmarkFolderDao.kt     # NEW
│           ├── HistoryDao.kt            # NEW
│           └── TabDao.kt                # NEW
├── di/                                  # NEW directory — first top-level di/ module (Spec 002 used core/di only)
│   └── DatabaseModule.kt                # NEW — @Module @InstallIn(SingletonComponent::class), provides AppDatabase + 4 DAOs
├── core/                                # UNCHANGED
├── domain/                              # NOT CREATED YET — deferred per spec Assumptions
└── presentation/                        # UNCHANGED

app/src/test/kotlin/com/raumanian/thirtysix/browser/
└── data/
    └── local/
        └── dao/                         # NEW
            ├── BookmarkDaoTest.kt       # NEW — in-memory DB, CRUD + observer + FK orphan-to-root
            ├── BookmarkFolderDaoTest.kt # NEW — self-FK orphan + nesting
            ├── HistoryDaoTest.kt        # NEW — chronological log invariant + range delete
            ├── TabDaoTest.kt            # NEW — position ordering + last_active_at
            └── AppDatabaseMigrationTest.kt # NEW — negative-path: schema mismatch fails fast (SC-004)

app/src/main/res/xml/
├── backup_rules.xml                     # MODIFY — add <exclude domain="database" path="thirtysix_browser.db"/> + WAL sidecars
└── data_extraction_rules.xml            # MODIFY — same exclude rules under <cloud-backup> and <device-transfer>

app/schemas/                             # NEW — tracked in git, contains JSON schema exports per version
└── com.raumanian.thirtysix.browser.data.local.database.AppDatabase/
    └── 1.json                           # AUTO-GENERATED at build time by Room compiler

app/build.gradle.kts                     # MODIFY — add Room deps + ksp arg("room.schemaLocation", "$projectDir/schemas")
gradle/libs.versions.toml                # MODIFY — add room = "2.8.4" + 4 library coordinates + turbine version
.gitignore                               # CHECK — `app/schemas/` MUST be tracked (not ignored)
```

**Structure Decision**: Single-module Android app; new `data/local/{database,entity,dao}/` packages materialize for the first time in this spec. The top-level `app/.../di/` package is also new — Spec 002 placed Hilt's `DispatcherModule` under `core/di/` because at that point only the core dispatcher abstraction needed wiring. Per Constitution §IV "ALL @Module annotations live in `di/` package" the new `DatabaseModule` may live in either location; chosen at top level (`app/.../di/`) so Spec 005's data-layer module is discoverable alongside future `RepositoryModule.kt` (Specs 013/014) and `NetworkModule.kt` (Spec 007). The `core/di/` placement of `DispatcherModule` is preserved untouched.

The `app/schemas/` directory MUST be checked into git so reviewers can audit schema evolution at PR time and so future migrations can be authored against a known prior schema (FR-010, SC-003).

## Complexity Tracking

> *Only filled when Constitution Check has violations to justify.*

No violations. Table omitted.

## Post-Design Constitution Re-check

After Phase 1 design artifacts ([research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)) were written, all 11 principles re-evaluated:

- **No new packages with `.so`**: Room 2.8.4 + Turbine 1.x verified pure Kotlin/Java. Principle IX gate passes automatically; no `objdump` action item beyond confirming the existing CI script still finds only `libandroidx.graphics.path.so`.
- **Hardcoded values audit**: All literals (DB filename, journal mode, schema directory, table names, column names, index names, schema version `1`, FK action `SET_NULL`) are declared as `const val` on entity / database companion objects. Detekt baseline unchanged. Principle III compliant.
- **DI scope boundary**: `DatabaseModule` is `@InstallIn(SingletonComponent::class)` — single instance per process. ViewModels never see the database directly; future Repositories will inject DAOs (Specs 013/014). Principle IV layer separation honored.
- **Backup-rules audit**: `backup_rules.xml` and `data_extraction_rules.xml` updated to exclude `thirtysix_browser.db` + `thirtysix_browser.db-wal` + `thirtysix_browser.db-shm` from both cloud backup and device-to-device transfer. Stricter than Constitution §VII baseline (which permits opt-in backup); aligns with Q4 clarification choice. Principle I & VII strengthened.
- **Test surface**: 12 planned unit tests across 5 test files. ≥ 70% coverage target on the data layer comfortably met (the layer is essentially 100% testable). Principle VI satisfied.
- **No speculative scaffolding**: Repository/Mapper/Domain explicitly NOT introduced this spec; will materialize when Specs 011/013/014 consume them. Principle X simplicity honored.
- **Build configuration**: `room.schemaLocation` ksp arg passed via `app/build.gradle.kts` `ksp { arg(...) }` block; `app/schemas/` tracked in git. No new build flavors, no new build types, no new BuildConfig fields. Principle XI compliant.

**Re-check result**: 11/11 PASS. No new violations surfaced during design. No Complexity Tracking entries required.
