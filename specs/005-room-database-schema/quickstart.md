# Quickstart: Room Database Schema — verification playbook

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Schema version**: 1

This document is the verification gate for Spec 005. All gates must pass before marking the spec complete and merging.

---

## Gate 1 — Build green

```bash
./gradlew clean
./gradlew assembleDebug
```

**Expected**: BUILD SUCCESSFUL with zero errors. Generated Kotlin sources visible under `app/build/generated/ksp/debug/kotlin/com/raumanian/thirtysix/browser/data/local/dao/` (Room-generated DAO impls) and `app/build/generated/hilt/component_sources/debug/` (Hilt-generated module bindings).

**Failure mode to watch**: `Cannot find implementation for AppDatabase` → KSP not running or `room-compiler` artifact ID wrong (must be `room-compiler` not `room-compiler-common`).

---

## Gate 2 — Schema export committed

```bash
ls app/schemas/com.raumanian.thirtysix.browser.data.local.database.AppDatabase/
```

**Expected**: `1.json` present. Open the file and verify:

- `formatVersion: 1`
- `database.version: 1`
- 4 entries in `database.entities` array (bookmarks, bookmark_folders, history_entries, tabs)
- 2 entries in `database.entities[].foreignKeys` for bookmarks and bookmark_folders (`onDelete: "SET NULL"`)
- 3 declared `index_*` entries on the relevant entities + 2 implicit FK indexes

```bash
git status
# Expected: app/schemas/.../1.json shown as new tracked file (NOT in .gitignore)
```

---

## Gate 3 — Unit tests pass

```bash
./gradlew testDebugUnitTest
```

**Expected**: BUILD SUCCESSFUL with N+13 tests passing where N = current count from Spec 003 (12) + Spec 002 (8) baseline. Net new: ≥ 13 from Spec 005 (4 DAO suites + 1 WAL concurrency + 1 config/migration + 1 DI smoke).

Per-DAO test coverage:

| Test file | Tests |
|-----------|-------|
| `BookmarkDaoTest` | insert+getById; insert→observeByFolder emits new snapshot (Turbine); update; delete; FK orphan-to-root when parent folder deleted; count |
| `BookmarkFolderDaoTest` | insert nested folder; self-FK orphan-to-root; observeChildren ordering |
| `HistoryDaoTest` | insert produces new row each call (chronological log invariant); observeAll DESC ordering; deleteInRange; deleteAll |
| `TabDaoTest` | insert auto-assigns id; maxPosition; observeAll position ordering; update last_active_at |
| `WalConcurrencyTest` | two coroutines insert concurrently into BookmarkDao + HistoryDao under WAL — both rows persist within a 1-second budget (SC-006) |
| `AppDatabaseConfigTest` | open with v1 fixture; verify journal_mode = "wal" via PRAGMA; negative-path: a v2 schema mismatch fixture without Migration class fails fast with `IllegalStateException` (SC-004) |
| `DatabaseModuleSmokeTest` | structural smoke — module providers invoke without errors + each DAO round-trips one insert/getById (SC-010) |

---

## Gate 4 — Static analysis clean

```bash
./gradlew lintDebug
./gradlew detekt
./gradlew ktlintCheck
```

**Expected**:

- Lint: zero warnings, zero errors. The `MissingTranslation` / `ExtraTranslation` gates from Spec 004 still active but no new strings introduced this spec.
- Detekt: zero violations. `MagicNumber` baseline UNCHANGED — no new entries (the schema version literal `1` is on the trivial whitelist; column-name literals are `const val` strings, not magic numbers).
- ktlint: zero violations.

---

## Gate 5 — APK release size delta

```bash
./gradlew assembleRelease
ls -lh app/build/outputs/apk/release/app-release.apk
```

**Expected**: APK size ≤ 2.49 MB (Spec 004 baseline 1.49 MB + 1 MB SC-007 budget). Actual delta from Room runtime + KSP-generated code is typically ~700 KB R8-shrunk; comfortably within budget.

```bash
unzip -p app/build/outputs/apk/release/app-release.apk lib/arm64-v8a/lib*.so 2>/dev/null | \
  objdump -p - | grep LOAD | awk '{print $NF}'
# Expected: every value 0x4000 (16KB) — same set of native libs as Spec 004
# (libandroidx.graphics.path.so only — no new natives added by Room)
```

---

## Gate 6 — Backup exclusion verified

```bash
cat app/src/main/res/xml/backup_rules.xml
cat app/src/main/res/xml/data_extraction_rules.xml
```

**Expected**: Both files contain the three `<exclude domain="database" path="thirtysix_browser.db..."/>` lines (or equivalent `*-wal`, `*-shm` siblings). Manual review at PR time per SC-011.

Optional manual verification on device:

1. Install debug build, create one bookmark, force-stop app.
2. Use `adb shell bmgr enable true && adb shell bmgr backupnow com.raumanian.thirtysix.browser.debug` to trigger Auto Backup.
3. Use `adb shell bmgr listtransports` and check the `app-backup` output — the `databases/thirtysix_browser.db` entry MUST be absent from the backup contents.

(This manual gate is OPTIONAL for v1.0 spec acceptance; rule-file content review at PR time is sufficient.)

---

## Gate 7 — Hilt graph valid

```bash
./gradlew assembleDebug
./gradlew :app:hiltJavaCompileDebug 2>&1 | grep -i "error\|missing"
```

**Expected**: empty output (no Hilt graph errors). The `DatabaseModule` provides `AppDatabase` + 4 DAOs; the existing `DispatcherModule` (Spec 002) is untouched. A `@HiltAndroidApp`-annotated `ThirtySixApplication` from Spec 002 already exists, so the graph is wired end-to-end.

A simple smoke test class under `app/src/test/.../SmokeHiltTest.kt` (optional, NOT required for v1.0) can request `@Inject lateinit var bookmarkDao: BookmarkDao` and verify the binding resolves.

---

## Gate 8 — Performance probe (deferred to consumer specs)

SC-001 (cold-start DB open < 200 ms on Pixel 5) and SC-001b (indexed queries < 100 ms under power-user load) are **not blocking gates for Spec 005 acceptance** — there is no UI consumer to measure. They become enforcement points when:

- Spec 011 first opens the database from `MainActivity` (cold-start DB open joins the cold-start budget).
- Spec 014 first issues `getRecent(50, 0)` against a populated history table.

Spec 005 marks these criteria as design targets met by:

- WAL journal mode active (verified by PRAGMA test in Gate 3).
- Three indexes shipped (verified by schema JSON in Gate 2).

A benchmark harness (Macrobenchmark / Microbenchmark) is OUT OF SCOPE for v1.0 Phase 1; introduced when Phase 2 specs need the data.

---

## Summary table

| Gate | Type | Required for spec accept? |
|------|------|---------------------------|
| 1. Build green | Automated | YES |
| 2. Schema export committed | Automated + manual review | YES |
| 3. Unit tests pass (12 new) | Automated | YES |
| 4. Static analysis clean | Automated | YES |
| 5. APK release size delta ≤ 1 MB + 16KB CI gate | Automated | YES |
| 6. Backup exclusion XML content | Manual review at PR | YES (rule-file content); optional adb verification |
| 7. Hilt graph valid | Automated | YES |
| 8. Performance probe | Deferred to consumer | NO (design target only) |

**Spec acceptance**: 1–7 PASS. Gate 8 inherits to consumer specs.
