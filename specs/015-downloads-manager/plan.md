# Implementation Plan: Downloads Manager

**Branch**: `015-downloads-manager` | **Date**: 2026-09-10 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/015-downloads-manager/spec.md`

## Summary

Wire the browser's first write-to-disk feature. A download listener on the `WebView` hands file responses to the Android system download service; a new `download_records` table keeps identity and durable metadata while live transfer state stays where it belongs — in the platform — and is joined at display time. A rewritten `DownloadsScreen` (today still a Spec 002 placeholder) lists records newest-first with live progress, a one-tap cancel control on in-flight rows, and a long-press action sheet gated by an explicit per-state matrix. Files land in the device's **public Downloads folder** on every supported version, which costs a version-ceilinged legacy storage permission on API 24–28 — the spec's one Constitution deviation. The bottom bar sheds Bookmarks and History into a new overflow menu, making room for Downloads and reserving the slot Settings needs in Spec 016. **Zero new dependencies**: the platform supplies the download service, and `room-testing` — needed for the project's first schema migration — turns out to be already declared and already wired.

## Technical Context

**Language/Version**: Kotlin 2.3.21 (per `gradle/libs.versions.toml`, set at Spec 001)
**Primary Dependencies**: Compose BOM 2026.04.01, Material3, material-icons-core 1.7.8, Hilt 2.59.2, KSP 2.3.7, Room 2.8.4, kotlinx-coroutines/Flow, `desugar_jdk_libs` 2.1.5 — **no new dependency added** (FR-057; `androidx.room:room-testing` was already in the catalog and already wired as `testImplementation`)
**Storage**: Existing Room DB (`thirtysix_browser.db`) — **schema v1 → v2**, one new table, additive migration, no destructive fallback
**Testing**: JUnit 4 + Robolectric 4.16.1 + Turbine 1.2.1 (JVM); `androidx.room:room-testing` for the migration test; Espresso-Web 3.7.0 + Hilt-android-testing + Compose UI Test (instrumented)
**Target Platform**: Android 7.0 (minSdk 24) → Android 16 (targetSdk 36, compileSdk 36 with `minorApiLevel = 1`)
**Project Type**: Mobile-app (single Android module under `app/`)
**Performance Goals**: SC-006 — frame budget ≤ 16 ms p99 with 500 records, ≥ 5 in flight, on a **release** build on Pixel 5-class hardware; SC-007 — in-flight progress advances ≥ 1 Hz
**Constraints**: SC-012 APK delta ≤ +200 KB vs the 2.36 MB Spec 014 baseline; SC-013 every native lib `align 0x4000`; zero new packages (FR-057); icon set limited to `material-icons-core`'s 48 filled glyphs (A12)
**Scale/Scope**: A14 envelope = 500 records (downloads accrue by deliberate user act, unlike history); v1.0 ships one rewritten screen, one bottom-bar refactor, one new table

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.2.0 — **11/11 PASS pre-implementation**, with **one documented deviation** under Principle II (see Complexity Tracking).

| # | Principle | Status | Evidence |
|---|-----------|--------|----------|
| I | Privacy & Security First | ✅ PASS | All data on-device; downloads excluded from cloud backup and device transfer on the same terms as the rest (FR-018). Path-traversal defence with a six-case proof obligation (FR-005, SC-015). No analytics. The legacy storage permission is requested **at point of first use, only on the API levels that require it** — satisfying "MUST NOT request runtime permissions it does not actively use" (FR-010, FR-011). §I's incognito clause enumerates history, cookies and cache; downloads are not among them, and FR-014a's reading was confirmed by the product owner during clarification. |
| II | Google Play Compliance | ⚠️ **PASS WITH DOCUMENTED DEVIATION** | `MANAGE_EXTERNAL_STORAGE` explicitly forbidden and never used (FR-013); Scoped Storage respected on API 29+. **Deviation**: a fourth permission (legacy external-storage write, `maxSdkVersion="28"`) against §II's three-row permission table — justified in Complexity Tracking. |
| III | Code Quality & Safety / No-Hardcode | ✅ PASS | All strings via `stringResource(R.string.*)`; new constants (`MAX_DOWNLOAD_FILENAME_LENGTH`, `DOWNLOAD_STATUS_POLL_INTERVAL_MS`) go to `core/constants/BrowserLimits.kt`, clipboard label to `AppConstants`; table and column names as `const val`s in the entity companion, per the Spec 005 convention. |
| IV | Clean Architecture (MVVM) | ✅ PASS | Compose → ViewModel → UseCase → Repository interface → Impl → DAO. `DownloadsRepository` depends only on its DAO and a dispatcher — never on another repository. **The platform join happens at the use-case layer**, not inside the repository, precisely so that rule holds (FR-053, FR-054). |
| V | Performance Excellence | ✅ PASS | SC-006 at 500 records; `LazyColumn` with stable `key { record.id }`; polling gated on *screen visible **and** something in flight*, so a list of finished downloads polls zero times (R7). |
| VI | Testing Discipline | ✅ PASS | JVM unit tests for the sanitiser, mapper, DAO surface, repository, 8 use cases, and the ViewModel; the **migration test is mandatory** (FR-017); instrumented tests for the screen, the action-sheet matrix, and the overflow menu. |
| VII | Offline-First Architecture | ✅ PASS | Room atomic writes; no cloud; downloads data excluded from Auto Backup and device transfer (FR-018), extending the Spec 005 posture to the new table. |
| VIII | Localization & Accessibility | ✅ PASS | All 8 locales (FR-049), lint at error severity for missing/extra translations; sizes, dates and times locale-formatted (FR-050); `contentDescription` on every interactive element including the inline cancel control (FR-051). |
| IX | Dependency Currency / 16KB | ✅ PASS | **Zero new dependencies** — verified during research that `room-testing` is already declared (`libs.versions.toml:115`) and already wired (`build.gradle.kts:207`). No new `.so`; SC-013 gate stays green by construction, and is still run. |
| X | Simplicity & Build Order | ✅ PASS | Depends only on shipped Specs (002/005/007/008/011/012/013/014). Phase 3's final spec, in mandatory order. YAGNI honoured — no pause/resume, no retention policy, no search, no in-app viewer, no "clear all" (13 out-of-scope items). |
| XI | Build Configuration | ✅ PASS | No signing-config change; debug + release only; `targetSdk = 36` unchanged. The only manifest edits are the permission lines. |

## Project Structure

### Documentation (this feature)

```text
specs/015-downloads-manager/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 — 12 R-items, zero NEEDS CLARIFICATION
├── data-model.md        # Phase 1 — 1 new table + migration + 2 computed shapes
├── quickstart.md        # Phase 1 — 12 manual gates G1–G12 (G9 blocking)
├── spec.md              # /speckit-specify output + 9 clarifications
├── checklists/
│   └── requirements.md  # Spec quality checklist — 16/16 across two iterations
├── contracts/
│   ├── DownloadsRepository.kt      # Phase 1 — persisted-half repository interface
│   └── DownloadManagerGateway.kt   # Phase 1 — platform seam (FR-054)
└── tasks.md             # Phase 2 output of /speckit-tasks (NOT created here)
```

### Source Code (repository root — Android single-module under `app/`)

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/constants/
│   ├── BrowserLimits.kt                              # MOD  +MAX_DOWNLOAD_FILENAME_LENGTH, +DOWNLOAD_STATUS_POLL_INTERVAL_MS
│   └── AppConstants.kt                               # MOD  +CLIPBOARD_DOWNLOAD_LINK_LABEL
├── data/
│   ├── local/
│   │   ├── database/AppDatabase.kt                   # MOD  SCHEMA_VERSION 1→2, +entity, +dao accessor
│   │   ├── database/migrations/Migration1To2.kt      # NEW  additive; creates download_records + index
│   │   ├── dao/DownloadRecordDao.kt                  # NEW
│   │   ├── entity/DownloadRecordEntity.kt            # NEW  companion const column names (Spec 005 convention)
│   │   └── download/DownloadManagerGateway.kt        # NEW  interface + Android impl in one file (ClipboardWriter pattern)
│   ├── mapper/DownloadRecordMapper.kt                # NEW
│   └── repository/DownloadsRepositoryImpl.kt         # NEW
├── domain/
│   ├── model/                                        # NEW  DownloadRecord, DownloadStatus, DownloadFailureCause,
│   │                                                 #      DownloadListItem, DownloadRequest,
│   │                                                 #      DownloadActionAvailability (FR-034a matrix)
│   ├── repository/DownloadsRepository.kt             # NEW
│   └── usecase/                                      # NEW  StartDownload, ObserveDownloads, ResolveDownloadStatus,
│                                                     #      CancelDownload, OpenDownloadedFile, RemoveDownloadRecord,
│                                                     #      DeleteDownloadedFile, SanitizeDownloadFileName
├── di/
│   ├── DatabaseModule.kt                             # MOD  +downloadRecordDao provider, +addMigrations(MIGRATION_1_2)
│   └── DownloadsModule.kt                            # NEW  @Binds repository + gateway
└── presentation/
    ├── browser/
    │   ├── BrowserWebView.kt                         # MOD  +setDownloadListener alongside the existing clients
    │   ├── BrowserWebViewCallbacks.kt                # MOD  4 → 5 fields (+onDownloadRequested)
    │   ├── BrowserViewModel.kt                       # MOD  +StartDownloadUseCase, +onDownloadRequested
    │   ├── BrowserUiState.kt                         # MOD  +downloadSnackbarEvent channel
    │   ├── BrowserScreen.kt                          # MOD  overflow callbacks, storage-permission launcher, snackbar
    │   └── components/
    │       ├── NavigationBottomBar.kt                # MOD  7 affordances → 5 + overflow
    │       ├── NavigationBottomBarCallbacks.kt       # MOD  8 → 7 fields; stale KDoc corrected (R10)
    │       └── BrowserOverflowMenu.kt                # NEW  M3 DropdownMenu: Bookmarks · History · Downloads
    └── downloads/
        ├── DownloadsScreen.kt                        # REWRITTEN (was the Spec 002 placeholder Text)
        ├── DownloadsViewModel.kt                     # NEW
        ├── DownloadsUiState.kt                       # NEW
        ├── DownloadsEvent.kt                         # NEW
        └── components/
            ├── DownloadsTopBar.kt                    # NEW
            ├── DownloadRow.kt                        # NEW  incl. the inline cancel control (FR-036)
            ├── EmptyDownloadsState.kt                # NEW
            ├── DownloadActionSheet.kt                # NEW  gated by the FR-034a matrix
            └── DeleteDownloadedFileConfirmDialog.kt  # NEW

app/src/main/AndroidManifest.xml                      # MOD  +WRITE_EXTERNAL_STORAGE maxSdkVersion=28; notification line pending G9
app/src/main/res/values{,-vi,-de,-ru,-ko,-ja,-zh,-fr}/strings.xml   # MOD  new keys ×8; downloads_screen_placeholder REMOVED
app/schemas/…/2.json                                  # NEW  generated, committed to git (FR-017)
app/src/debug/kotlin/…/dev/DownloadSeeder.kt          # NEW  debug source set only (Spec 014 HistorySeeder precedent)
```

**Structure Decision**: Mirror Specs 013/014 exactly — one feature folder under `presentation/downloads/` with a `components/` subfolder, the data and domain slices following the project's standard Clean Architecture layering. Two departures from those specs, both forced by this feature's nature: a `data/local/download/` package for the platform seam (parallel to Spec 014's `data/local/clipboard/`), and a `data/local/database/migrations/` package that does not exist yet because no migration has ever been written.

## Phase 0 — Research

Detailed in [research.md](research.md). Twelve R-items, **zero NEEDS CLARIFICATION** — the spec's nine clarifications settled every product question before planning began.

| R# | Topic | Decision (preview) | Basis |
|----|-------|--------------------|-------|
| R1 | Where the download hand-off is intercepted | Download listener in `BrowserWebView`'s factory, beside the existing clients; `BrowserWebViewCallbacks` 4 → 5 fields | verified-here |
| R2 | **Notification permission necessity (FR-048)** | Start undeclared; **G9 blocks** the manifest line. Strong prior: not needed, since the provider notifies under its own identity | **gate** |
| R3 | Handing a file to an external app (FR-026) | Content URI from the service + read grant; never a `file://` URI (fatal since minSdk 24). No `FileProvider` needed | documented |
| R4 | Detecting a forgotten handle (FR-024a) | Empty query result = "not known"; the design never models the pruning schedule | documented |
| R5 | The v1 → v2 migration | Additive, one new table, explicitly registered. **`room-testing` already declared and wired** — zero new deps | verified-here |
| R6 | Legacy storage permission (API 24–28) | Declared with `maxSdkVersion=28`, requested at first use only. The one Constitution deviation | documented |
| R7 | Observing live progress | Poll only while the screen is visible **and** something is in flight; steady state polls zero times | documented |
| R8 | Filename derivation and sanitisation | Platform helper derives; **we sanitise unconditionally afterwards** — it is not a security boundary | documented |
| R9 | Icon pivot | `MoreVert` · `KeyboardArrowDown` · `Close` · `Warning` · `Delete`; no icon where none is honest | verified-here |
| R10 | Overflow menu + bottom-bar refactor | M3 dropdown; callbacks 8 → 7; the stale `NavigationBottomBarCallbacks` KDoc corrected in the same change | verified-here |
| R11 | Duplicate filenames | Delegated to the platform's numeric suffixing; no de-duplication of our own | documented |
| R12 | Cancellation | Remove-by-handle; the service owns partial-file cleanup | documented |

## Phase 1 — Design & Contracts

Detailed in [data-model.md](data-model.md), [contracts/DownloadsRepository.kt](contracts/DownloadsRepository.kt), [contracts/DownloadManagerGateway.kt](contracts/DownloadManagerGateway.kt), [quickstart.md](quickstart.md).

### Entities

- **`DownloadRecordEntity`** (data, new table): `id`, `sourceUrl`, `fileName`, `mimeType`, `createdAt`, `transferHandle`, `localUri: String?`. One index on `createdAt`. No foreign keys. No incognito column (FR-014a). No unique constraint on the handle — a stale platform value must never make an insert fail and lose the user's download.
- **`DownloadStatus`** (domain, sealed, never persisted): `Pending` · `Running` · `Paused` · `Complete` · `Failed(cause)` · `Cancelled` · `Missing`. `Missing` is **derived, not reported** — it is what the FR-024a fallback yields when the handle is forgotten and the file is gone.
- **`DownloadListItem`** (presentation): record + freshly-resolved status. What a row renders and what the action matrix is evaluated against.
- **`DownloadsUiState`** (immutable `data class`) + **`DownloadsEvent`** (sealed, one-shot on a `SharedFlow` with `replay=0 / capacity=1 / DROP_OLDEST` — the Spec 013 channel policy).

### Contracts

Two interfaces, and the split between them is the design's load-bearing decision:

- **`DownloadsRepository`** — the persisted half only. Room-backed, no platform dependency, so Constitution §IV's "repository depends only on its DAO and a dispatcher" holds without strain.
- **`DownloadManagerGateway`** — the platform seam (FR-054), mirroring Spec 014's `ClipboardWriter`. **Every method is total**: failure is expressed in the return type, never thrown. `enqueue` returns a nullable handle; `queryStatus` returns null for "not known" — an ordinary expected outcome, not an error.

The two are joined at the **use-case** layer, never inside a repository. That is what keeps the hybrid source-of-truth decision from becoming a Constitution violation.

### Quickstart (manual gates)

[quickstart.md](quickstart.md) defines 12 gates. Beyond the per-story gates, three carry unusual weight:

- **G9 ⛔ BLOCKING** — determines whether the notification permission is declared at all. The manifest line is not written until it runs, and either outcome has a follow-up: keep the Constitution's permission table as written, or amend it.
- **G11 step 5** — the **real upgrade path**: install `main`'s build, create data in all four existing tables, install this branch over it, confirm nothing is lost. The automated migration test proves the mechanism; this proves the shipped artifact. A failure here is a release blocker regardless of anything else.
- **G12** — carries an explicit warning not to repeat Spec 014's T103b mistake of measuring a debug build on an emulator against a release-on-real-hardware target. Measure the release build on real hardware, or mark the gate DEFERRED honestly.

### Agent context update

The `<!-- SPECKIT START -->` / `<!-- SPECKIT END -->` block in [CLAUDE.md](../../CLAUDE.md) gets its "Active Spec" section repointed at this plan, recording the nine clarifications, the Constitution 11/11-with-one-deviation line, and the schema-version bump — the first time that block has had to carry a database migration.

## Constitution Check (Post-Design Re-Check)

After data-model, contracts and quickstart were drafted: **11/11 still PASS**, deviation count unchanged at one. Design-level decisions that were checked rather than assumed:

- **§IV survived the hybrid design.** The obvious implementation — having `DownloadsRepositoryImpl` call the platform gateway to fill in live status — would have made a repository depend on a platform service and blurred the layer. Splitting the two contracts and joining them in a use case keeps the rule intact with no exception needed. This is the one place the design could have quietly drifted, and it did not.
- **§III held under pressure from the new constants.** Every value that surfaced during design — filename length bound, poll interval, clipboard label, table and column names — has a home in `core/constants/` or an entity companion. None is inline.
- **§IX is stronger than expected.** The migration test was the one place a new dependency looked likely; `room-testing` turned out to be already declared and already wired, so FR-057 holds literally rather than by exemption.
- **§VI gained a mandatory test that has no precedent in this project.** The v1→v2 migration test is the first of its kind here, and both an automated test (FR-017) and a device gate (G11 step 5) are required — the mechanism and the artifact are verified separately.
- **§I's incognito reading was confirmed, not assumed.** FR-014a rests on a product-owner decision recorded in the spec's clarification session, not on the planner's interpretation.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| **Fourth Android permission** — legacy external-storage write, declared with `maxSdkVersion="28"`, against Constitution §II's three-row permission table | Writing to the device's **public** Downloads folder required this permission before Scoped Storage landed in API 29, and this project's minSdk is 24. Without it, downloads on Android 7.0–9.0 cannot reach the folder every user and file manager expects. The version ceiling means the permission is absent from the effective manifest on API 29+, so modern users never see it in the Play listing and it can never be requested there; it is requested at point of first use, never at launch. `MANAGE_EXTERNAL_STORAGE` remains forbidden (FR-013). | **App-private storage on API ≤ 28** — rejected during clarification: files would vanish on uninstall and never appear in the device's Downloads folder, contradicting the most basic expectation of a browser download and producing behaviour inconsistent across Android versions. **Raising minSdk to 29** — rejected as reversing a foundational Constitution decision to spare one feature one permission. **Skipping API ≤ 28 support for downloads** — rejected as shipping a visibly broken feature on supported devices. |
| **Constitution permission-table amendment pending** (bookkeeping, not a violation) | §II's table lists `POST_NOTIFICATIONS` against Spec 015. Gate **G9** determines empirically whether the app needs it for the *system's* download notifications. If it does not, the table row must be amended rather than left describing a permission the app does not ship — and the storage row must be added either way. | Leaving the table untouched — rejected because the Constitution would then misdescribe the shipped app in both directions at once, which is worse than either error alone. |
