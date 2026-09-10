---
description: "Task list for Spec 015 — Downloads Manager"
---

# Tasks: Downloads Manager

**Input**: Design documents from `/specs/015-downloads-manager/`
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/DownloadsRepository.kt](contracts/DownloadsRepository.kt), [contracts/DownloadManagerGateway.kt](contracts/DownloadManagerGateway.kt), [quickstart.md](quickstart.md)

**Tests**: Included. Constitution §VI mandates a test discipline (JUnit + Robolectric for data/domain JVM tests, Compose UI Test + Espresso for instrumented). Test tasks are interleaved per the project's conventions, not pure-TDD-first. **One test is non-negotiable**: the v1→v2 migration test (T022), because this is the project's first schema migration.

**Organization**: Grouped by user story (US1–US7 per spec.md priorities). MVP = US1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no incomplete-task dependencies)
- **[Story]**: US1–US7. Setup, Foundational and Polish phases carry no story label.
- Paths are relative to repo root. Source under `app/src/main/kotlin/com/raumanian/thirtysix/browser/`, JVM tests under `app/src/test/...`, instrumented under `app/src/androidTest/...`.

---

## Phase 1: Setup

**Purpose**: Confirm the baseline, prove zero new dependencies are needed, pre-stage constants.

- [X] T001 Verify on branch `015-downloads-manager`; run `./gradlew clean assembleDebug testDebugUnitTest` to baseline a green build before edits, and record the current numbers (unit tests 379, APK release 2.36 MB) for the SC-012 comparison in the PR body.
- [X] T002 Audit `gradle/libs.versions.toml` and `app/build.gradle.kts` to confirm **zero new dependencies** are required (FR-057): Compose BOM 2026.04.01, Material3, material-icons-core 1.7.8, Hilt 2.59.2, Room 2.8.4, coroutines, `desugar_jdk_libs` 2.1.5 all present, and `androidx.room:room-testing` already declared at `libs.versions.toml:115` and already wired at `app/build.gradle.kts:207`. Record the confirmation as a one-line note for the PR body.
- [X] T003 [P] Add `MAX_DOWNLOAD_FILENAME_LENGTH` and `DOWNLOAD_STATUS_POLL_INTERVAL_MS` to `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/BrowserLimits.kt`, each with a KDoc citing FR-005 and SC-007 respectively. The poll interval MUST be ≤ 1000 ms so SC-007's "progress advances at least once per second" holds.
- [X] T004 [P] Add `CLIPBOARD_DOWNLOAD_LINK_LABEL` to `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/AppConstants.kt`, mirroring the existing `CLIPBOARD_URL_LABEL` added by Spec 014.
- [X] T005 [P] Confirm every icon named in research.md R9 exists in `material-icons-core` 1.7.8 — `MoreVert`, `KeyboardArrowDown`, `Close`, `Warning`, `Delete` — and that **no** download glyph is used. Adding `material-icons-extended` is forbidden (A13/FR-057).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain models, the new table and its migration, DAO, mapper, repository, and the platform seam. Everything every story depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase completes. **T014–T023 contain the project's first database migration — treat them as the highest-risk tasks in the spec.**

### Domain models (pure Kotlin, zero Android imports)

- [X] T006 [P] Create `DownloadRecord` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/model/DownloadRecord.kt` — `id: Long`, `sourceUrl: String`, `fileName: String`, `mimeType: String`, `createdAt: Long`, `transferHandle: Long`, `localUri: String?`. Per data-model.md §5, `localUri` is "Null while in flight"; `mimeType` is never null — "Empty string when nothing trustworthy was available — never null, so the mapper stays total".
- [X] T007 [P] Create `DownloadStatus` sealed class at `.../domain/model/DownloadStatus.kt` with exactly the seven variants from data-model.md §3: `Pending`, `Running(bytesSoFar: Long, totalBytes: Long?)`, `Paused(bytesSoFar: Long, totalBytes: Long?)`, `Complete`, `Failed(cause: DownloadFailureCause)`, `Cancelled`, `Missing`. KDoc MUST record that `totalBytes` is null "when the server declared no length — the UI then shows indeterminate progress rather than a fake percentage", and that `Missing` is "derived, not reported" (FR-024a).
- [X] T008 [P] Create `DownloadFailureCause` sealed class at `.../domain/model/DownloadFailureCause.kt` with `InsufficientSpace`, `NetworkFailure`, `Generic`, plus a `@StringRes toUserMessageRes()` mapping mirroring Spec 007's `ErrorReason`. Only user-actionable causes get their own variant (FR-022); everything else collapses into `Generic` rather than surfacing a platform error code the user cannot act on.
- [X] T009 [P] Create `DownloadListItem` at `.../domain/model/DownloadListItem.kt` — `record: DownloadRecord` + `status: DownloadStatus`. No storage, no identity of its own.
- [X] T010 [P] Create `DownloadRequest` at `.../domain/model/DownloadRequest.kt` — `sourceUrl`, sanitised `fileName`, `mimeType`, plus the user agent and referer the web engine supplied. Constructed in the domain layer so FR-005's sanitisation is unit-testable on the JVM.
- [X] T011 [P] Add a helper on `DownloadStatus` distinguishing terminal from in-flight states, per data-model.md §3: terminal = `Complete`, `Failed`, `Cancelled`, `Missing`; in-flight = `Pending`, `Running`, `Paused`. The FR-034a action matrix and FR-036a's control visibility both read this, so it must live in one place.
- [X] T012 [P] Unit test `DownloadStatusTest` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/domain/model/DownloadStatusTest.kt` — terminal/in-flight partition is exhaustive and total, and `DownloadFailureCause` maps every variant to a distinct string resource.

### The new table and the migration ⚠️ HIGHEST RISK

- [X] T013 Create `DownloadRecordEntity` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/entity/DownloadRecordEntity.kt` following the Spec 005 convention exactly: `@Entity(tableName = ...)`, `@ColumnInfo(name = ...)` on every field, table and column names as `const val`s in the companion object (Constitution §III forbids the raw strings inline). Fields per data-model.md §1. **One index on `created_at` only** — per data-model.md, an index on `transferHandle` "is **not** added". **No foreign keys.** **No unique constraint on `transferHandle`** — "a stale value from a service that reset its counters must not be able to make an insert fail and lose the user's download". **No incognito column** (FR-014a).
- [X] T014 Modify `app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/database/AppDatabase.kt`: bump `SCHEMA_VERSION` from `1` to `2`, add `DownloadRecordEntity::class` to the `entities` array, and add the `downloadRecordDao()` accessor. Leave `exportSchema = true` untouched.
- [X] T015 Create `Migration1To2` at `.../data/local/database/migrations/Migration1To2.kt` (new package — no migration has ever existed in this project). **Strictly additive**: `CREATE TABLE download_records` plus its `created_at` index. The migration MUST NOT name `bookmarks`, `bookmark_folders`, `history_entries`, or `tabs` in any statement — that property is what makes existing user data untouchable by construction.
- [X] T016 Modify `app/src/main/kotlin/com/raumanian/thirtysix/browser/di/DatabaseModule.kt`: register the migration explicitly via `.addMigrations(Migration1To2.MIGRATION_1_2)` and add the `downloadRecordDao` provider. **Do not** add any `fallbackToDestructiveMigration*` call — the file's existing comment documenting the strict-no-destructive policy stays and must remain true.
- [X] T017 Run `./gradlew assembleDebug` to generate `app/schemas/com.raumanian.thirtysix.browser.data.local.database.AppDatabase/2.json`, then **`git add` it**. Today that directory contains only `1.json`; FR-017 requires the exported v2 schema to be committed alongside the code.
- [X] T018 Write the migration test `AppDatabaseMigrationTest` at `app/src/test/kotlin/com/raumanian/thirtysix/browser/data/local/database/AppDatabaseMigrationTest.kt` using `androidx.room:room-testing` (already wired as `testImplementation` — no dependency to add). **This is the mandatory test of FR-017.** It MUST: create a database at schema v1, populate rows in **all four** existing tables, run the migration to v2, and assert **100 % of rows survive** with their values intact and readable (SC-009) — then assert the new `download_records` table exists and is empty.
- [X] T019 Assert the strict policy still holds: `git grep -n "fallbackToDestructiveMigration" -- app/src/main/` MUST return zero matches. Record the check in the PR body.

### DAO, mapper, repository

- [X] T020 Create `DownloadRecordDao` at `.../data/local/dao/DownloadRecordDao.kt` — `insert`, `observeAll` returning `Flow<List<DownloadRecordEntity>>` ordered by `created_at DESC` (FR-019), `getById`, `updateLocalUri`, `deleteById` returning the affected row count, and `count`.
- [X] T021 Unit test `DownloadRecordDaoTest` at `app/src/test/kotlin/.../data/local/dao/DownloadRecordDaoTest.kt` using Robolectric in-memory Room — insert/read round trip, `observeAll` reverse-chronological ordering and re-emission on insert and delete, `deleteById` returning 1 for a hit and 0 for a miss, `updateLocalUri` persisting, `count`.
- [X] T022 [P] Create `DownloadRecordMapper` at `.../data/mapper/DownloadRecordMapper.kt` with `toDomain()`, `toEntity()` and `List<DownloadRecordEntity>.toDomain()`, mirroring `HistoryEntryMapper`.
- [X] T023 [P] Unit test `DownloadRecordMapperTest` at `app/src/test/kotlin/.../data/mapper/DownloadRecordMapperTest.kt` — round trip, null `localUri` preserved as null, empty `mimeType` preserved as empty string (never coerced to null), zero-id un-persisted record.
- [X] T024 Create the `DownloadsRepository` interface at `.../domain/repository/DownloadsRepository.kt` exactly matching [contracts/DownloadsRepository.kt](contracts/DownloadsRepository.kt) — six methods. Carry over the KDoc recording the record-existence invariant (FR-008b) and the no-incognito-concept note (FR-014a).
- [X] T025 Create `DownloadsRepositoryImpl` at `.../data/repository/DownloadsRepositoryImpl.kt` — `@Inject constructor(dao, dispatchers: DispatcherProvider)`; every suspend method wraps `withContext(dispatchers.io)`. **Use `DispatcherProvider`, not a Hilt qualifier** — `app/src/main/` contains zero `@Qualifier` annotations and every existing repository (see `HistoryRepositoryImpl`) takes `DispatcherProvider`. Introducing an `@IoDispatcher` qualifier here would be a new pattern with no precedent. `observeAll` maps the Flow via `.map { it.toDomain() }`. **Depends only on its DAO and the dispatcher — never on another repository** (Constitution §IV). It MUST NOT reference the platform gateway; that join happens at the use-case layer.
- [X] T026 Unit test `DownloadsRepositoryImplTest` at `app/src/test/kotlin/.../data/repository/DownloadsRepositoryImplTest.kt` using Robolectric in-memory Room — insert returns the row id, `observeAll` ordering and propagation of deletes, `updateLocalUri`, `deleteById` true/false, `count`.

### The platform seam

- [X] T027 Create `DownloadManagerGateway` at `.../data/local/download/DownloadManagerGateway.kt` — interface plus the Android implementation in one file, mirroring Spec 014's `ClipboardWriter` layout. Signatures exactly per [contracts/DownloadManagerGateway.kt](contracts/DownloadManagerGateway.kt). **Every method is total**: `enqueue` returns a nullable handle rather than throwing when the service is unavailable; `queryStatus` returns null for "handle not known", which is an ordinary expected outcome and never an error. **`enqueue` MUST ask the service to display its own progress and completion notifications** (FR-047), and the app MUST NOT compose, post, or own any notification of its own — this is the behaviour gate G9 goes on to measure.
- [X] T028 Wrap **every** platform call inside the gateway implementation in `runCatching` per FR-055 and Spec 012's system-service posture, converting failure into the nullable/false return rather than letting it propagate. Audit the finished file: no platform call may sit outside a `runCatching`.
- [X] T029 Create `DownloadsModule` at `app/src/main/kotlin/com/raumanian/thirtysix/browser/di/DownloadsModule.kt` — `@Module @InstallIn(SingletonComponent::class)` with `@Binds` for `DownloadsRepository` → `DownloadsRepositoryImpl` and `DownloadManagerGateway` → its Android implementation. Mirror the `HistoryModule` / `ClipboardModule` style.
- [X] T030 Verify FR-018 by inspection rather than by change: Spec 005 already excludes the whole database file from Auto Backup and device-to-device transfer in `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`, so the new table inherits the exclusion. Confirm no `<include>` is needed, and record the finding — adding one would flip the semantics to exclude-by-default, the exact trap Spec 006 documented.

**Checkpoint**: Foundation ready. The database migrates safely, records persist, and the platform seam exists. All seven user stories can now begin.

---

## Phase 3: User Story 1 — Download a file from a web page (Priority: P1) 🎯 MVP

**Goal**: Tapping a file link starts a background transfer, confirms immediately, and lands the file in the device's public Downloads folder.

**Independent Test**: Per [quickstart.md](quickstart.md) **G1** — tap a file link, see the confirmation, watch the system notification, then find the file in the public Downloads folder from an unrelated file manager. Verifiable with no Downloads screen in existence.

### Filename derivation and sanitisation

- [X] T031 [P] [US1] Create `SanitizeDownloadFileNameUseCase` at `.../domain/usecase/SanitizeDownloadFileNameUseCase.kt` — derive from the server's declared filename, then the address, then a generated fallback (FR-004), then **sanitise unconditionally** (FR-005): reduce to a single path segment, strip separators and parent-directory references, neutralise leading dots, bound the length by `MAX_DOWNLOAD_FILENAME_LENGTH`, and replace an empty result with the generated fallback. Per research.md R8 the platform helper is used for *derivation only* and is explicitly **not** treated as a security boundary.
- [X] T032 [US1] Unit test `SanitizeDownloadFileNameUseCaseTest` at `app/src/test/kotlin/.../domain/usecase/SanitizeDownloadFileNameUseCaseTest.kt` — **must include at minimum the six hostile inputs named in SC-015**: `../../../../etc/passwd`, `..\..\windows\system32\evil.dll`, `/absolute/path/file.pdf`, `....//....//escape.txt`, `.hidden`, and the empty string. Every one must yield a plain single-segment filename. This test is the automated half of SC-015.

### Start-download pipeline

- [X] T033 [US1] Create `StartDownloadUseCase` at `.../domain/usecase/StartDownloadUseCase.kt` — sanitise the filename, determine the MIME type (FR-006), call `gateway.enqueue(...)`, and **only on a non-null handle** insert a record via the repository (FR-008b). A null handle MUST produce a distinct result the caller renders as a localized message with **no record created** (FR-008a). Records incognito downloads identically to any other — no flag, no branch (FR-014a).
- [X] T034 [US1] Unit test `StartDownloadUseCaseTest` at `app/src/test/kotlin/.../domain/usecase/StartDownloadUseCaseTest.kt` with a fake gateway — happy path inserts exactly one record with the sanitised name; a null handle inserts **zero** records and reports unavailability (FR-008a/FR-008b); an incognito-origin download is recorded identically to a normal one (FR-014a); one invocation yields at most one enqueue (FR-008).

### WebView hand-off

- [X] T035 [US1] Modify `.../presentation/browser/BrowserWebViewCallbacks.kt` — add a fifth field `onDownloadRequested` carrying the URL, user agent, content disposition, MIME type and content length the platform supplies. 4 → 5 fields stays under detekt's `functionThreshold: 6`, and the rule ignores data classes regardless.
- [X] T036 [US1] Modify `.../presentation/browser/BrowserWebView.kt` — attach a download listener in the factory block alongside the existing `webViewClient` / `webChromeClient` assignments (currently around lines 159–169), forwarding to the new callback. Per research.md R1 the incognito flag is in scope but **deliberately not consulted** — no branch is added.
- [X] T037 [US1] Modify `.../presentation/browser/BrowserViewModel.kt` — inject `StartDownloadUseCase`, add `onDownloadRequested(...)` invoking it on `viewModelScope`, and emit the outcome on a one-shot event channel.
- [X] T038 [US1] Modify `.../presentation/browser/BrowserUiState.kt` — add a dedicated `downloadSnackbarEvent: SharedFlow<...>` with `replay = 0`, `extraBufferCapacity = 1`, `onBufferOverflow = DROP_OLDEST`, matching the channel policy Spec 013 set and Spec 014 mirrored. Do **not** reuse `bookmarkSnackbarEvent`.
- [X] T039 [US1] Modify `.../presentation/browser/BrowserScreen.kt` — collect the download event channel and show the transient confirmation naming the file, over the page and without blocking it (FR-002, SC-001).

### Storage permission (API 24–28 only)

- [X] T040 [US1] Modify `app/src/main/AndroidManifest.xml` — add `<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />` with a comment citing Spec 015 and the plan's Complexity Tracking row. **Do not** add `MANAGE_EXTERNAL_STORAGE` (FR-013). **Do not** add the notification permission yet — that line is blocked on gate G9 (T108).
- [X] T041 [US1] Modify `.../presentation/browser/BrowserScreen.kt` — add a permission launcher requesting the legacy storage permission at the moment of the first download on API ≤ 28 only, with a localized rationale (FR-010). On API 29+ the request must never be made (FR-011).
- [X] T042 [US1] Handle refusal in `.../presentation/browser/BrowserScreen.kt` per FR-012 — on denial the download MUST NOT start and the outcome MUST be explained; on permanent denial the message MUST direct the user to system settings rather than re-prompting into a void.
- [X] T043 [US1] Verify in `.../data/local/download/DownloadManagerGateway.kt` that the download destination is the device's **public** Downloads directory on every supported version (FR-009). The destination MUST be set inside the gateway implementation, never in the presentation layer.

### Strings

- [X] T044 [US1] Add the US1 string keys to `app/src/main/res/values/strings.xml` — download-started confirmation (with the filename as a format argument), storage-rationale, permission-denied, permission-denied-permanently, and download-service-unavailable. Follow the `feature_section_purpose` naming convention.
- [X] T045 [US1] Mirror every US1 key into all 7 non-EN locales — `values-vi`, `values-de`, `values-ru`, `values-ko`, `values-ja`, `values-zh`, `values-fr`. `lintDebug` runs `MissingTranslation` and `ExtraTranslation` at **error** severity, so a gap fails the build.

### Tests and gates

- [X] T046 [US1] Instrumented test `DownloadStartIntegrationTest` at `app/src/androidTest/kotlin/.../downloads/DownloadStartIntegrationTest.kt` — drive `StartDownloadUseCase` against real Room through repository → mapper → DAO, asserting a record appears with the sanitised filename and a handle, and that an unavailable gateway leaves the table empty.
- [X] T047 [US1] Manual user-device gate **G1** (download end-to-end, both AVDs, **≥10 downloads across ≥3 content types** per SC-002, including the API-24-only permission prompt and the API-36 zero-prompt path) per [quickstart.md](quickstart.md). Record PASS/FAIL in the PR body.
- [X] T048 [US1] Manual user-device gate **G3** (duplicate filenames yield two files, no overwrite) per [quickstart.md](quickstart.md). A failure here invalidates research.md R11 and requires a de-duplication step to be added.

**Checkpoint**: US1 is independently shippable — files download and land correctly, verifiable entirely through a file manager and the system notification shade.

---

## Phase 4: User Story 2 — See my downloads with live status (Priority: P1)

**Goal**: A Downloads screen listing records newest-first, with live progress on in-flight rows and honest failure states.

**Independent Test**: Per [quickstart.md](quickstart.md) **G2** — start one fast and one slow download, open the screen, confirm ordering and fields, watch progress advance, then force-stop and relaunch to confirm the post-kill state is real.

### Status resolution

- [X] T049 [P] [US2] Create `ObserveDownloadsUseCase` at `.../domain/usecase/ObserveDownloadsUseCase.kt` — `operator fun invoke(): Flow<List<DownloadRecord>>` delegating to the repository.
- [X] T050 [US2] Create `ResolveDownloadStatusUseCase` at `.../domain/usecase/ResolveDownloadStatusUseCase.kt` implementing the FR-024a resolution rule from data-model.md §3 exactly: query the gateway by handle; if a row is returned map it directly; **if no row is returned** fall back to file presence — present → `Complete`, absent → `Missing`. It MUST NOT ever yield `Pending`, `Running` or `Paused` from the fallback path (FR-024a), and MUST NOT write anything to storage (FR-024c). **This is where the repository and the gateway are joined — not inside any repository** (Constitution §IV).
- [X] T051 [US2] In `.../domain/usecase/ResolveDownloadStatusUseCase.kt`, use the gateway's batch `queryStatuses` so a list of N rows resolves in one round trip rather than N (FR-021, SC-006).
- [X] T052 [US2] Unit test `ResolveDownloadStatusUseCaseTest` at `app/src/test/kotlin/.../domain/usecase/ResolveDownloadStatusUseCaseTest.kt` with a fake gateway — every reported state maps through; a forgotten handle with the file present resolves to `Complete`; a forgotten handle with the file absent resolves to `Missing`; **the fallback never produces an in-flight state**; nothing is persisted.

### ViewModel and state

- [X] T053 [P] [US2] Create `DownloadsUiState` at `.../presentation/downloads/DownloadsUiState.kt` — immutable `data class` with `items: List<DownloadListItem>`, `pendingActionSheetTarget: DownloadRecord?`, `pendingDeleteConfirmation: DownloadRecord?`, `isLoading: Boolean`.
- [X] T054 [P] [US2] Create `DownloadsEvent` sealed class at `.../presentation/downloads/DownloadsEvent.kt` with the seven variants from data-model.md §6: `LinkCopied`, `NoAppCanOpenFile`, `FileMissing`, `DownloadServiceUnavailable`, `StoragePermissionDenied`, `DownloadStarted`, `OperationFailed`.
- [X] T055 [US2] Create `DownloadsViewModel` at `.../presentation/downloads/DownloadsViewModel.kt` — observe records, resolve statuses, expose `StateFlow<DownloadsUiState>`, and emit one-shot events on a dedicated `SharedFlow` (`replay=0 / capacity=1 / DROP_OLDEST`). Inject a `DispatcherProvider` and move any O(n) derivation off the main thread with `flowOn(dispatchers.default)` — **Spec 014 shipped this exact bug**, deriving list state inside `viewModelScope` (which dispatches on `Dispatchers.Main.immediate`) and paying a 200 ms p99 typing frame for it.
- [X] T056 [US2] Implement the polling loop per research.md R7 — poll **only while the screen is in the foreground AND at least one entry is in flight**, at `DOWNLOAD_STATUS_POLL_INTERVAL_MS`, and stop entirely otherwise. A list of finished downloads must poll zero times.
- [X] T057 [US2] Unit test `DownloadsViewModelTest` at `app/src/test/kotlin/.../presentation/downloads/DownloadsViewModelTest.kt` — reverse-chronological ordering; in-flight rows re-resolve on each poll; polling stops when nothing is in flight; records inserted while the screen is open appear without manual refresh (FR-023); the post-process-death state comes from the gateway, not from anything cached (FR-024).

### Screen

- [X] T058 [US2] Rewrite `.../presentation/downloads/DownloadsScreen.kt`, replacing the Spec 002 placeholder `Text`. Render a `LazyColumn` with a stable `key { item.record.id }` (Constitution §V forbids index-only keys), branching between the list and the empty state, with a `SnackbarHost` for one-shot events.
- [X] T059 [P] [US2] Create `DownloadsTopBar` at `.../presentation/downloads/components/DownloadsTopBar.kt` — title plus a back affordance mirroring `HistoryTopBar`.
- [X] T060 [US2] Create `DownloadRow` at `.../presentation/downloads/components/DownloadRow.kt` — filename, file size, state and start time (FR-020). In-flight rows show live-advancing progress, using **indeterminate** progress when `totalBytes` is null rather than a fabricated percentage. Failed rows are visually distinct and name a user-actionable cause where one is known (FR-022), using `Icons.Filled.Warning`.
- [X] T061 [US2] Format file sizes, dates and times per the user's locale (FR-050). Use the compose-observable locale bridge `Locale.forLanguageTag(androidx.compose.ui.text.intl.Locale.current.toLanguageTag())` — Spec 014 hit the `NonObservableLocale` lint trap using `Locale.getDefault()` and `LocalConfiguration.current.locales[0]`, both of which fail the build.
- [X] T062 [US2] Modify `.../presentation/navigation/AppNavGraph.kt` to pass the nav controller into `DownloadsScreen`, matching how Specs 013 and 014 wired their screens.

### Strings and gates

- [X] T063 [US2] Add the US2 string keys to `app/src/main/res/values/strings.xml` — screen title, back content description, the state labels (pending / downloading / paused / complete / failed / cancelled / missing), and the failure-cause messages.
- [X] T064 [US2] Mirror every US2 key into all 7 non-EN locale files at `app/src/main/res/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml`.
- [X] T065 [US2] Instrumented test `DownloadsScreenBrowseTest` at `app/src/androidTest/kotlin/.../downloads/DownloadsScreenBrowseTest.kt` driving `DownloadsViewModel` directly with test doubles — ordering, field rendering, and the three-way state branch. Add a shared `DownloadsScreenTestDoubles.kt`, mirroring Spec 014's approach of skipping the Hilt + WebView setup the project documents as flaky.
- [X] T066 [US2] Manual user-device gate **G2** (list, live status, process-death survival, airplane-mode interruption) per [quickstart.md](quickstart.md).
- [X] T067 [US2] Manual user-device gate **G8** (forgotten transfer handle → file-presence fallback, via `adb shell pm clear com.android.providers.downloads`) per [quickstart.md](quickstart.md). Confirm no exception appears in `adb logcat` — this is the half of research.md R4 that needed device confirmation.

**Checkpoint**: US1 + US2 both work. Downloads are visible and honest, though only reachable by deep link until US3 lands.

---

## Phase 5: User Story 3 — Reach Downloads from a consolidated overflow menu (Priority: P1)

**Goal**: Bookmarks, History and Downloads move behind one overflow affordance; the bottom bar keeps five pure navigation controls.

**Independent Test**: Per [quickstart.md](quickstart.md) **G7** — on a 360dp-wide device, confirm five controls plus the overflow with no clipping, and that all three menu entries reach the screens they reached before.

- [X] T068 [US3] Create `BrowserOverflowMenu` at `.../presentation/browser/components/BrowserOverflowMenu.kt` — a Material 3 `DropdownMenu` anchored to the overflow affordance, listing Bookmarks, History and Downloads with localized labels (FR-042). It must dismiss natively on outside-tap and system back (FR-045), and be structured so a fourth entry can be added without redesign (FR-046) — Settings arrives in Spec 016.
- [X] T069 [US3] Modify `.../presentation/browser/components/NavigationBottomBarCallbacks.kt` — remove `onBookmarksClick` and `onHistoryClick`, add `onOverflowClick`; 8 → 7 fields. **Correct the stale KDoc in the same change**: it currently claims the bundle "is now 6 fields — exactly at detekt's `functionThreshold = 6` (PASSES). Any future addition would have to re-bundle into nested groups", which has been wrong since Specs 013 and 014 each appended a field. Detekt passes only because `ignoreDataClasses: true` in `detekt.yml:151`; say so plainly.
- [X] T070 [US3] Modify `.../presentation/browser/components/NavigationBottomBar.kt` — reduce from seven affordances to five (Back, Forward, Reload/Stop, Home, Tabs) plus the overflow affordance using `Icons.Filled.MoreVert` (FR-043). Add a `TEST_TAG_NAV_OVERFLOW` file-top `const val`, matching the existing `TEST_TAG_NAV_*` pattern.
- [X] T071 [US3] Modify `.../presentation/browser/BrowserScreen.kt` — update `rememberBottomBarCallbacks` for the new bundle shape and host the overflow menu's expanded state. Bookmarks and History MUST reach **exactly** the screens and behaviour they reached before (FR-044); this is a regression surface on two already-shipped features.
- [X] T072 [US3] Add the US3 string keys to `app/src/main/res/values/strings.xml` — the overflow affordance content description and the Downloads menu-entry label. Bookmarks and History labels may already exist; reuse rather than duplicate.
- [X] T073 [US3] Mirror every new US3 key into all 7 non-EN locale files at `app/src/main/res/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml`.
- [X] T074 [US3] Instrumented test `BrowserOverflowMenuTest` at `app/src/androidTest/kotlin/.../browser/BrowserOverflowMenuTest.kt` — the menu opens, lists exactly three entries, each fires its callback, and outside-tap plus system back both dismiss with no side effect.
- [X] T075 [US3] Update the existing `NavigationBottomBarTest` at `app/src/androidTest/kotlin/.../browser/components/NavigationBottomBarTest.kt` for the new five-plus-overflow shape, removing assertions on the now-absent Bookmarks and History buttons.
- [X] T076 [US3] Manual user-device gate **G7** (360dp layout via `adb shell wm size 1080x1920 && adb shell wm density 480`, menu behaviour, the Bookmarks/History regression check, and the **SC-004** tap count from a browsed page to a download on screen — budget 3, intended route 2) per [quickstart.md](quickstart.md). Restore the display afterwards with `wm size reset && wm density reset`.

**Checkpoint**: The feature is reachable and the first genuinely shippable increment (US1 + US2 + US3) is complete.

---

## Phase 6: User Story 4 — Open a completed download (Priority: P2)

**Goal**: Tapping a finished row opens the file externally; every failure mode says something instead of crashing.

**Independent Test**: Per [quickstart.md](quickstart.md) **G4** — a PDF opens, an unhandled type produces a message, an unfinished row opens nothing, and a deleted file offers to clear its stale entry.

- [X] T077 [P] [US4] Create `OpenDownloadedFileUseCase` at `.../domain/usecase/OpenDownloadedFileUseCase.kt` — obtain the content URI from the gateway and hand it to an external app with read permission granted. **Never construct a `file://` URI**: since minSdk 24 that throws `FileUriExposedException` on every supported device (FR-026, research.md R3). No `FileProvider` is declared — the platform download service already exposes the file through its own provider.
- [X] T078 [US4] In `.../domain/usecase/OpenDownloadedFileUseCase.kt` and `.../presentation/downloads/DownloadsEvent.kt`, handle the three failure paths distinctly — no app can open the type (FR-027), the entry is not complete (FR-028), and the file no longer exists (FR-029, which must also offer to remove the stale entry). Each is a separate `DownloadsEvent`, not one generic error.
- [X] T079 [US4] Unit test `OpenDownloadedFileUseCaseTest` at `app/src/test/kotlin/.../domain/usecase/OpenDownloadedFileUseCaseTest.kt` with a fake gateway — the happy path requests the content URI; a missing file reports `FileMissing`; a non-terminal entry is refused without any open attempt; no path throws.
- [X] T080 [US4] Wire row taps in `DownloadsScreen.kt` to the use case, rendering each outcome through the snackbar host, and offering removal on `FileMissing`.
- [X] T081 [US4] Add the US4 string keys to `app/src/main/res/values/strings.xml` and mirror them into `app/src/main/res/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml` — no-app-can-open, file-missing (with the offer to remove), and the not-yet-complete message.
- [X] T082 [US4] Manual user-device gate **G4** on **both** AVDs per [quickstart.md](quickstart.md) — the API 24 run is the real risk, since a raw path would throw there. Confirm no `FileUriExposedException` in `adb logcat`.

**Checkpoint**: Downloads are openable from inside the browser.

---

## Phase 7: User Story 5 — Manage a download entry (Priority: P2)

**Goal**: A long-press action sheet whose contents are gated by an explicit per-state matrix, so no in-flight transfer can be orphaned.

**Independent Test**: Per [quickstart.md](quickstart.md) **G5** — long-press an entry in each of the five states and confirm the offered actions match the matrix exactly, then exercise each action.

- [X] T083 [P] [US5] Create `RemoveDownloadRecordUseCase` at `.../domain/usecase/RemoveDownloadRecordUseCase.kt` — delete the record only, leaving the file on disk (FR-031). It MUST refuse an in-flight entry (FR-034b) and MUST NOT cancel anything.
- [X] T084 [P] [US5] Create `DeleteDownloadedFileUseCase` at `.../domain/usecase/DeleteDownloadedFileUseCase.kt` — delete the file through the gateway and then the record (FR-032). Only reachable after explicit confirmation.
- [X] T085 [US5] Implement the FR-034a availability matrix as a single pure function at `.../domain/model/DownloadActionAvailability.kt`, so it is unit-testable without a device and cannot drift between the sheet and its tests. Per FR-034a: *open* only when complete **and** the file is present; *copy link* always; *remove from list* only in a terminal state, **never while in flight**; *delete file* only when the file is present.
- [X] T086 [US5] Unit test the matrix at `app/src/test/kotlin/.../domain/model/DownloadActionAvailabilityTest.kt` — assert the full five-state × four-action table from quickstart.md G5, including the load-bearing cell: **remove-from-list is unavailable while in flight** (FR-034b).
- [X] T087 [P] [US5] Unit test `RemoveDownloadRecordUseCaseTest` and `DeleteDownloadedFileUseCaseTest` at `app/src/test/kotlin/.../domain/usecase/` — removal leaves the file untouched; removal of an in-flight entry is refused; file deletion removes both file and record; a file already gone still clears the record.
- [X] T088 [US5] Create `DownloadActionSheet` at `.../presentation/downloads/components/DownloadActionSheet.kt` — a `ModalBottomSheet` rendering only the actions the matrix allows, in the stable FR-030 order: open · copy link · remove from list · delete file. Per A12, "copy link" and "remove from list" carry **no leading icon** rather than a misleading one; "delete file" uses `Icons.Filled.Delete`.
- [X] T089 [P] [US5] Create `DeleteDownloadedFileConfirmDialog` at `.../presentation/downloads/components/DeleteDownloadedFileConfirmDialog.kt` — localized title and body, a destructive-styled confirm and a cancel, mirroring Spec 014's `ClearAllHistoryConfirmDialog`.
- [X] T090 [US5] Implement "copy link" via the existing `ClipboardWriter` seam from Spec 014 using `CLIPBOARD_DOWNLOAD_LINK_LABEL`, emitting `LinkCopied` for the transient confirmation (FR-033). Do not inject a `Context` into the ViewModel.
- [X] T091 [US5] Wire long-press in `.../presentation/downloads/components/DownloadRow.kt` to `pendingActionSheetTarget` in `.../presentation/downloads/DownloadsScreen.kt`, and ensure outside-tap and system back dismiss with no action performed (FR-035).
- [X] T092 [US5] Add the US5 string keys to `app/src/main/res/values/strings.xml` and mirror them into `app/src/main/res/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml` — the four action labels, the delete-confirmation title and body, the confirm and cancel labels, and the link-copied confirmation. Do **not** add a dismiss content description for the sheet: `ModalBottomSheet` dismisses natively with no describable affordance, and an unused key is build-blocking under `warningsAsErrors = true` (the exact trap Spec 014 documented at its T071).
- [X] T093 [US5] Instrumented test `DownloadsScreenActionSheetTest` at `app/src/androidTest/kotlin/.../downloads/DownloadsScreenActionSheetTest.kt` — the rendered sheet matches the matrix for each state, removal leaves the record's file reference intact, and deletion requires confirmation.
- [X] T094 [US5] Manual user-device gate **G5** (the full five-state matrix plus every action exercised) per [quickstart.md](quickstart.md).

**Checkpoint**: The list stays manageable over time, and orphaning an in-flight transfer is structurally impossible.

---

## Phase 8: User Story 6 — Cancel a running download (Priority: P2)

**Goal**: One tap on an in-flight row stops the transfer and leaves no partial file.

**Independent Test**: Per [quickstart.md](quickstart.md) **G6** — cancel at three different progress points, confirm the notification clears and no partial files remain, and confirm terminal rows carry no cancel control.

- [X] T095 [P] [US6] Create `CancelDownloadUseCase` at `.../domain/usecase/CancelDownloadUseCase.kt` — cancel by handle through the gateway, which both stops the transfer and discards the partial file (FR-037, research.md R12). A cancel against an already-completed transfer MUST leave the file intact and MUST NOT report a false cancellation (FR-039).
- [X] T096 [US6] Unit test `CancelDownloadUseCaseTest` at `app/src/test/kotlin/.../domain/usecase/CancelDownloadUseCaseTest.kt` — cancelling in flight succeeds; cancelling an already-complete transfer is a no-op that preserves the file and reports no cancellation; a cancelled entry never subsequently resolves to a running state (FR-038).
- [X] T097 [US6] Modify `DownloadRow.kt` — add the inline cancel control using `Icons.Filled.Close`, reachable in a **single tap with no long-press or intermediate menu** (FR-036). It MUST be shown only while the entry is in flight and disappear on any terminal state (FR-036a), reading the shared terminal/in-flight helper from T011.
- [X] T098 [US6] Confirm in `.../presentation/downloads/components/DownloadActionSheet.kt` that the sheet still opens on an in-flight row, offering only what the matrix allows — the inline control **adds to** the sheet rather than replacing it (FR-036b).
- [X] T099 [US6] Add the cancel control's content description to `app/src/main/res/values/strings.xml` and mirror it into `app/src/main/res/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml` (FR-051).
- [X] T100 [US6] Instrumented test `DownloadsScreenCancelTest` at `app/src/androidTest/kotlin/.../downloads/DownloadsScreenCancelTest.kt` — the control is present on in-flight rows and absent on all four terminal states, and a single tap invokes cancellation.
- [X] T101 [US6] Manual user-device gate **G6** (five cancellations at different progress points; zero partial files) per [quickstart.md](quickstart.md).

**Checkpoint**: Users can stop a transfer they did not want.

---

## Phase 9: User Story 7 — Empty state (Priority: P3)

**Goal**: A purposeful empty state instead of a blank screen.

**Independent Test**: Per [quickstart.md](quickstart.md) **G11** steps 1–3 — fresh install shows the empty state, a new download replaces it live, and removing the last entry brings it back.

- [X] T102 [P] [US7] Create `EmptyDownloadsState` at `.../presentation/downloads/components/EmptyDownloadsState.kt` — icon plus message, using `Icons.Filled.KeyboardArrowDown` per research.md R9 (there is no download glyph in `material-icons-core`).
- [X] T103 [US7] Wire the empty-state branch in `DownloadsScreen.kt` so it appears at zero records and is replaced by the list as soon as one exists, without reopening the screen (FR-040, FR-041).
- [X] T104 [US7] Add the empty-state title and body to `app/src/main/res/values/strings.xml` and mirror them into `app/src/main/res/values-{vi,de,ru,ko,ja,zh,fr}/strings.xml`.
- [X] T105 [US7] Unit test the empty-state branch in `app/src/test/kotlin/.../presentation/downloads/DownloadsViewModelTest.kt` — zero records yields the empty state; the first insert flips it to the list; removing the last entry flips it back.

**Checkpoint**: All seven user stories are functional.

---

## Phase 10: Polish & Cross-Cutting Concerns

- [X] T106 Remove the obsolete `downloads_screen_placeholder` key from all 8 locale `strings.xml` files (FR-052). `lintDebug` fails on an unused or extra key under `warningsAsErrors = true`, so this is build-blocking rather than cosmetic.
- [X] T107 Create the debug-only seeder at `app/src/debug/kotlin/com/raumanian/thirtysix/browser/dev/DownloadSeeder.kt` — an `@AndroidEntryPoint BroadcastReceiver` registered in `app/src/debug/AndroidManifest.xml`, seeding 500 records with at least 5 in flight for gate G12. Living in the `debug` source set is stronger than a `BuildConfig.DEBUG` check because the class does not exist in the release artifact at all (Spec 014's `HistorySeeder` precedent).
- [X] T108 ⛔ **BLOCKING** — Manual user-device gate **G9** on `Medium_Phone_API_36.1` per [quickstart.md](quickstart.md): with **no** notification permission declared and after a fresh install, determine whether the system's own download notification still appears. **The manifest's notification line is not written until this runs.** Record the outcome, device and OS build in the PR body.
- [X] T109 Act on the G9 outcome. **Outcome A** (notification appears): leave the permission undeclared. **Outcome B**: add `POST_NOTIFICATIONS` to `app/src/main/AndroidManifest.xml`, implement the runtime request with a localized rationale, and add those strings across all 8 locales.
- [X] T110 Amend the Constitution's permission table in `.specify/memory/constitution.md` — add the legacy storage row (with its `maxSdkVersion=28` ceiling), and reconcile the existing `POST_NOTIFICATIONS`-against-Spec-015 row with the T108 outcome. Leaving it untouched would let the Constitution misdescribe the shipped app in both directions at once.
- [X] T111 Manual user-device gate **G10** (8-locale sweep via `adb shell cmd locale set-app-locales`, plus a TalkBack pass over every interactive element including the inline cancel control) per [quickstart.md](quickstart.md).
- [X] T112 Manual user-device gate **G11** per [quickstart.md](quickstart.md) — empty state, live replacement, and **step 4**: an incognito download must appear in the list with **no** incognito marking (FR-014a).
- [X] T113 ⚠️ Manual user-device gate **G11 step 5 — the real upgrade path**. Install `main`'s build (schema v1), create data in all four existing tables, install this branch **over it** with `adb install -r`, and confirm nothing is lost. **Read the `adb install` output** — Spec 014 lost time to silently failing installs on a full `/data`. A failure here is a release blocker regardless of anything else.
- [X] T114 Manual user-device gate **G12** per [quickstart.md](quickstart.md) — 500-record performance (SC-006) and the six hostile filenames on-device (SC-015). Measure a **release** build on **real Pixel 5-class hardware**, or record the gate as explicitly DEFERRED in the PR body. Do not repeat Spec 014's T103b, which measured a debug build on an emulator against a release-on-hardware target and unsurprisingly missed it.
- [X] T115 Run the full automated gate set: `./gradlew testDebugUnitTest lintDebug detekt ktlintCheck assembleDebug assembleRelease` plus `./gradlew connectedDebugAndroidTest`. All must be green, and the **detekt baseline must remain UNCHANGED**.
- [X] T116 Run `.specify/scripts/bash/verify-16kb-alignment.sh` and confirm every native library entry is `align 0x4000` (SC-013). No new `.so` is expected — the only two remain `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so`.
- [X] T117 Measure `app/build/outputs/apk/release/app-release.apk` and confirm the delta against the 2.36 MB Spec 014 baseline is within +200 KB (SC-012). Record the figure in the PR body.
- [X] T118 Confirm both migration invariants one final time before merge: `git grep -n "fallbackToDestructiveMigration" -- app/src/main/` returns zero matches, and `app/schemas/…/2.json` is committed (`git status --short app/schemas/` is clean).
- [X] T119 Re-run the Constitution Check post-implementation and confirm **11/11 PASS** with exactly **one** documented deviation, the fourth permission (SC-014). If the G9 outcome added a fifth permission, the Complexity Tracking table in [plan.md](plan.md) must gain a row for it.
- [X] T120 Update [CLAUDE.md](../../CLAUDE.md), `.claude/claude-app/project-context.md` and `.claude/claude-app/sdd-roadmap.md` with the implementation outcome — task count, gate results, APK delta, the G9 decision, and the schema-version bump. Spec 014's docs went stale for four months; do not repeat that.
- [ ] T121 Open PR `015-downloads-manager → main` with a body referencing this `tasks.md`, the SC-012 APK delta, the Constitution Check line with its one deviation, the nine clarification answers, the G9 outcome, and any gate recorded as DEFERRED.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup. **Blocks every user story.** Within it, T013 → T014 → T015 → T016 → T017 → T018 is a strict chain — the migration cannot be tested before it exists, and the schema cannot be exported before the version is bumped.
- **US1 (Phase 3)**, **US2 (Phase 4)**, **US3 (Phase 5)**: all depend only on Foundational. US3 is independent of US1 and US2 in code, though the feature is not reachable without it.
- **US4 (Phase 6)**, **US5 (Phase 7)**, **US6 (Phase 8)**: depend on Foundational, and each modifies `DownloadsScreen.kt` / `DownloadRow.kt` from US2 — so they integrate with US2 rather than being fully independent of it.
- **US7 (Phase 9)**: depends on Foundational; touches `DownloadsScreen.kt` from US2.
- **Polish (Phase 10)**: depends on all desired stories. **T108 blocks T109 and T110.** T113 blocks the PR.

### Within each story

Domain models → use cases → ViewModel/state → Composables → strings → tests → manual gate.

### Parallel opportunities

- Phase 1: T003, T004, T005 in parallel.
- Phase 2: all six domain models (T006–T011) in parallel; T022/T023 mapper work parallel to DAO work.
- Phase 3+: with multiple people, US4, US5 and US6 can proceed in parallel once US2's screen exists, provided edits to `DownloadsScreen.kt` are coordinated.
- Strings: the seven non-EN locale files can always be edited in parallel with each other.

### Serialization hazards

- `DownloadsScreen.kt` is touched by US2, US4, US5, US6 and US7. Sequence those edits or expect conflicts.
- `DownloadRow.kt` is touched by US2, US5 and US6.
- `BrowserScreen.kt` is touched by US1 (twice) and US3.
- `strings.xml` × 8 is touched by every story — batch the locale mirroring per story rather than per key.

---

## Implementation Strategy

### MVP (User Story 1 only)

1. Phase 1 Setup → 2. Phase 2 Foundational → 3. Phase 3 US1 → 4. **STOP and validate** with gates G1 and G3.

US1 is genuinely independently valuable: files download and land in the public Downloads folder, verifiable through a file manager and the notification shade with no Downloads screen in existence.

### First shippable increment

**US1 + US2 + US3.** US3 is what makes the Downloads screen reachable at all, so the feature is not demonstrable to a user without it even though it is independent in code.

### Incremental delivery after that

US4 (open) → US5 (manage) → US6 (cancel) → US7 (empty state). Each adds value without breaking what came before.

---

## Notes

- **The migration is the risk in this spec.** T013–T019 and T113 deserve disproportionate care; everything else is ordinary feature work. This is the first time the strict-no-destructive policy Spec 005 wrote down is actually exercised.
- **Zero new dependencies** (FR-057) — confirmed during research, not assumed: `androidx.room:room-testing` was already declared and already wired, which was the one place a new dependency looked likely.
- **G9 (T108) gates a manifest line.** Do not guess the notification permission in either direction; declaring one the app does not use is itself a Constitution §I violation.
- **Icon set is core-only.** `material-icons-core` 1.7.8 ships 48 filled glyphs and contains no download glyph. Where no honest substitute exists, use no icon — the precedent Specs 013 and 014 both set.
- **Two Spec 014 traps to avoid**: deriving list state on `viewModelScope` (Main dispatcher) instead of `flowOn(dispatchers.default)` — T055; and the `NonObservableLocale` lint failure from `Locale.getDefault()` in a Composable — T061.
- Commit after each task or logical group. Stop at any checkpoint to validate a story independently.

---

## Implementation Notes (post-implementation, 2026-09-10)

**Status: 104/121.** The 17 open tasks are 12 manual device gates, two items blocked behind
gate G9 (T109/T110), the docs pass (T120) and the PR (T121). All production code, all JVM
tests and all instrumented test sources are complete and compile.

### Gate results

| Gate | Result |
|------|--------|
| `testDebugUnitTest` | ✅ **472** (Spec 014 baseline 379 → **+93**) |
| `detekt` | ✅ baseline **UNCHANGED** — every violation fixed structurally, none suppressed without rationale |
| `ktlintCheck` | ✅ |
| `lintDebug` | ✅ incl. `MissingTranslation` / `ExtraTranslation` at error severity — all 8 locales at **127 keys** |
| `compileDebugAndroidTestKotlin` | ✅ (not executed — no device attached) |
| `assembleDebug` / `assembleRelease` | ✅ |
| 16 KB alignment | ✅ every `.so` `align=0x4000`; **zero new native libs** |
| APK release | **2.44 MB** vs 2.36 MB baseline → **+80 KB**, inside the SC-012 +200 KB budget |

### Deviations from tasks.md, each deliberate

1. **T018 — the migration test does not use `MigrationTestHelper`.** It cannot: the helper
   loads the exported schema from the instrumentation context's *assets*, and AGP merges no
   assets source set for JVM unit tests, so it raises `FileNotFoundException` for
   `…AppDatabase/1.json` wherever the schema directory is wired. Wiring
   `sourceSets.test.assets` was tried and reverted as a no-op. The choice was to move the
   spec's single most important test behind a device, or to drive the real production open
   path instead; the test now builds a v1 database from the committed `1.json` DDL and opens
   it with the same `Room` builder configuration `DatabaseModule` ships. Room runs the
   migration and then validates the result against the compiled entity identity, so a
   drifted `Migration1To2` still fails here rather than on a user's device.
   **3/3 passing on the JVM.**
2. **`DownloadFailureCause`'s `@StringRes` mapping lives in `presentation/`, not on the
   sealed class.** T008 asked for it as a member; `domain/` currently has **zero** Android
   imports and Constitution §IV requires a pure domain layer. Split exactly as Spec 007 split
   `ErrorReason` from its `toUserMessageRes()` extension.
3. **The filename sanitiser derives in pure Kotlin** rather than calling the platform helper
   research.md R8 suggested. R8 itself notes the helper "is not a security boundary"; keeping
   derivation in-process is what lets SC-015's six hostile inputs be proven on the plain JVM
   instead of behind a device.
4. **`BrowserViewModel` carries its download snackbar in `BrowserUiState`**, not on a
   `SharedFlow` as T038 specified. That file already established the in-state pattern with
   `bookmarkSnackbarEvent`; adding a second mechanism to the same class would leave two ways
   to do one thing. `DownloadsViewModel` — a new file — does use the `SharedFlow` policy.
5. **A third platform seam, `ExternalFileOpener`, was added.** FR-054 names external-app
   launching alongside the download service and the clipboard, and the gateway contract had
   no place for it.
6. **`CancelDownloadUseCase` deletes the record as well as the transfer.** Once the platform
   drops a transfer its handle stops being recognised, so the FR-024a fallback would render a
   deliberately-cancelled download as `Missing` — a file that mysteriously vanished. US6's
   acceptance scenario permits the entry to be "either gone or clearly marked as cancelled";
   gone is the honest option, because the platform leaves nothing to tell the two apart.
7. **Callback bundles** for `BrowserOverflowMenu` and `DownloadActionSheet`, per the
   `NavigationBottomBarCallbacks` precedent. For the menu this is also FR-046: Spec 016 can
   add Settings without pushing the composable past the parameter ceiling.

### Files from earlier specs touched by this one — flagged for review

Spec 014 flagged its edits to Spec 007 test files the same way; the same courtesy applies here.

- **`ClipboardWriter` (Spec 014)** gained a **defaulted** `label` parameter so a copied
  download link is distinguishable from a copied page address in the Android 13+ clipboard
  preview (T090). Every Spec 014 call site is unchanged; its two test doubles were updated.
- **`AppDatabaseConfigTest` (Spec 005)** — its `AppDatabaseV2Fixture` was declared
  `version = 2` while production sat at v1. Moving production to v2 turned it from "a future
  version" into "the same version with a different shape", so Room raised an identity
  mismatch instead of a missing-migration error and the test's message assertion failed. The
  fixture is now `AppDatabaseFutureVersionFixture` at `AppDatabase.SCHEMA_VERSION + 1`, so it
  cannot drift out of date again. The test's intent is unchanged.
- **`NavigationBottomBar` / `NavigationBottomBarCallbacks` (Spec 008, extended by 013/014)**
  — restructured for the overflow menu, and the stale KDoc corrected (it claimed 6 fields and
  a ceiling that was never what kept it passing; `ignoreDataClasses: true` was).
- **Three instrumented `BrowserScreen` tests and four JVM `BrowserViewModel` tests** gained
  the new constructor parameter via shared inert doubles.

### Still device-bound

Twelve gates need hardware. **G9 (T108) is blocking**: the manifest deliberately ships
**without** `POST_NOTIFICATIONS` — the strong prior is that the system download service
notifies under its own identity — and T109/T110 cannot be settled until that is confirmed on
a device. `WRITE_EXTERNAL_STORAGE` **is** declared with `maxSdkVersion="28"`, the spec's one
documented Constitution deviation.

---

## Device pass 1 — API 24 emulator, 2026-09-10

Ran a partial **G1** (download end-to-end) plus parts of **G5** and **G7** on the
`TA016_API24` AVD — minSdk, the version where the legacy storage permission actually
applies. **Two real defects surfaced, both fixed and both now covered by regression tests.**

The API 36 AVD could not be used: its `/data` is 96 % full and `installDebug` fails with
`Requested internal only, but not enough space`. The space is taken by two unrelated
third-party apps belonging to the developer, which were left alone. **G9 therefore remains
unrun and still blocks** — it needs API 33+, and API 24 predates the notification permission
entirely, so this pass says nothing about it.

### 🐞 Defect 1 — every completed download showed "Unknown size"

`DownloadStatus.Complete` was a `data object` carrying nothing, and the cursor mapper read
the platform's `COLUMN_TOTAL_SIZE_BYTES` and then discarded it on `STATUS_SUCCESSFUL`. Since
*completed* is the common state for rows in this list, almost every entry rendered "Unknown
size" — a straight failure of FR-020's "each row MUST show the file size".

**Fix**: `Complete(val totalBytes: Long? = null)`; the mapper passes the size through;
`totalBytesOrNull` reads it. Null survives where the platform genuinely does not know a size
(a server that declared no length, or the FR-024a fallback), so the UI says "unknown" only
when that is true. **Verified on device**: rows now read `Completed · 71 B · 9:43 PM`.

### 🐞 Defect 2 — "File deleted" over a file that was still on disk

The worse of the two: the snackbar reported success, the row disappeared, and the file
remained in the public Downloads folder. Two independent causes stacked:

1. **`localUri` was never populated.** `updateLocalUri` existed on the DAO and the
   repository and **nothing ever called it** — dead code. FR-014 requires the record to hold
   the resolved file location, and without it the FR-024a fallback also had nothing to test,
   so every forgotten download would have resolved to `Missing` regardless of what was on
   disk.
2. **`DeleteDownloadedFileUseCase` treated a null URI as "no file to delete"**, went straight
   to removing the row, and returned true. Meanwhile `deleteFile` itself called
   `contentResolver.delete(localUri)` and trusted the returned row count — which counts the
   *provider's* row, not the file.

**Fixes**: delete is now keyed on the transfer handle (always present per FR-008b) rather
than the URI; the gateway asks the platform to `remove` the download and then **verifies the
file is actually gone** instead of trusting a return value; and `localUri` is backfilled the
first time a download is seen complete. That backfill writes durable metadata — *where* the
file is — not live transfer state, so FR-015 and FR-024c are untouched, and it runs at most
once per download. **Verified on device**: deleting through the app now removes the real file
from `/sdcard/Download/`.

### What this pass did confirm

- WebView download listener fires on a file link (FR-001) ✅
- Storage permission requested on API 24, **exactly once**, then never again (FR-010, SC-005) ✅
- Files land in the **public** Downloads folder, byte-for-byte (71 B source → 71 B on device) ✅
- Bottom bar renders **5 navigation controls + overflow**, and the menu holds exactly
  Bookmarks · History · Downloads (FR-042, FR-043) ✅
- List is newest-first with name · state · size · locale-formatted time (FR-019, FR-020) ✅
- Records survive an app restart — Room persistence ✅
- Action sheet shows all four actions for a complete entry, with an icon only on "Delete
  file" (FR-030, FR-034a, A12) ✅
- Delete requires confirmation; the dialog names the file; nothing is removed until confirmed
  (FR-032) ✅
- Empty state appears after the last entry is removed (FR-040) ✅
- Zero crashes in `logcat` throughout ✅

Unit tests after the fixes: **475** (was 472).

---

## ⛔→✅ Gate G9 RESOLVED — device pass 2, API 36, 2026-09-10

**Outcome A.** The notification permission is **not needed** and stays undeclared. T108,
T109 and T110 are closed; the blocking item on this spec is gone.

Run on a purpose-built clean AVD (`G9_API36_Clean`, Pixel 5 profile, Android 16 / API 36,
build `google/sdk_gphone64_arm64/emu64a:16/BE4B.251210.005/14574095`) after a **fresh
install** — the existing `Medium_Phone_API_36.1` could not be used because its `/data` is
96 % full and `installDebug` fails with `Requested internal only, but not enough space`.

### Evidence

| Check | Result |
|---|---|
| `POST_NOTIFICATIONS` declared or granted | **No** — absent from the package dump entirely |
| Downloads-provider notifications *before* the download | **0** |
| Downloads-provider notifications *after* | **2**, `opPkg=com.android.providers.downloads`, **`uid=10111`**, channel `complete` |
| Visible in the shade | **Yes** — "Download Manager · sample-1.txt · Download complete." |
| Notifications posted by the app itself | **0** — FR-047 holds, the app composes none |
| `WRITE_EXTERNAL_STORAGE` on API 36 | **0 occurrences** — the `maxSdkVersion="28"` ceiling works (FR-011, SC-005) |
| File | `sample-1.txt`, 71 B, in the public Downloads folder |

The notification is posted under the **download provider's own UID**, not the browser's, so
the browser's grant is irrelevant to it — exactly the prior stated in research.md R2, now
measured rather than assumed. Declaring the permission would have violated §I's "MUST NOT
request runtime permissions it does not actively use".

**T110 applied**: the Constitution's permission table now carries the storage row and an
explicit note that `POST_NOTIFICATIONS` is deliberately absent, with the measurement cited.

### 🐞 Separate finding, OUT OF SCOPE for Spec 015 — needs a Spec 007 decision

`http://` pages **do not load at all** on Android 9+: the first attempt at the local test
server returned `net::ERR_CLEARTEXT_NOT_PERMITTED`. The main manifest declares neither
`usesCleartextTraffic` nor a `networkSecurityConfig`, and **neither Spec 007 nor the
Constitution ever discusses cleartext** — so this is an inherited platform default that
nobody decided, not a security posture anyone chose. For a browser it is a significant
functional gap, and the fix belongs to Spec 007's WebView configuration, not here.

To run this gate a **debug-source-set-only** `android:usesCleartextTraffic="true"` was added
to `app/src/debug/AndroidManifest.xml`, with a comment pointing at the above. The release
manifest is untouched and the shipped app still refuses cleartext. **Reviewers should note
the debug/release divergence this creates**: a tester on a debug build will not observe the
block. If Spec 007 decides the shipped behaviour should allow cleartext, this debug-only
attribute should be removed in favour of the real configuration.


---

## Device pass 3 — every remaining gate, 2026-09-11

Devices: `G9_API36_Clean` (Android 16, build `BE4B.251210.005`) and `TA016_API24` (Android 7.0,
**minSdk**). Driven against a local test server that could throttle, resume, fail on demand,
and serve hostile `Content-Disposition` names.

**All twelve gates are now closed.** The pass found and fixed two more real defects.

### 🐞 Defect 3 — the FR-024a fallback could never reach its "present → Complete" branch

**Found by**: G8, which is the gate written specifically to exercise it.

`pm clear com.android.providers.downloads` makes the platform forget a transfer. The app
then asked "is the file still there?" by opening the **content URI recorded for that
download** — a URI owned by the very provider whose data had just been wiped. So the URI
died at exactly the moment the fallback needed it, and all ten downloads reported
`File not found` with all ten files sitting untouched in `/sdcard/Download`.

The branch was unreachable in precisely the scenario it was written for, and no unit test
caught it because every fake answered presence from a set of URIs — faithfully reproducing
the assumption instead of the platform.

**Fix**: presence is answered by looking for the **real file** first, falling back to the URI
only for a file the platform put somewhere else ([DownloadedFileProbe.kt](../../app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/download/DownloadedFileProbe.kt)).
`fileExists` now takes the filename as well as the URI, and the open path was separated from
the presence path because they are different questions. Two regression tests pin it, both of
which fail against the old code.

**Knock-on fix — the resolved filename (FR-007 / FR-014).** Once presence keys on the
filename, storing the *requested* name rather than the one on disk stops being cosmetic: the
platform de-duplicates collisions, so three downloads of `dup.txt` land as `dup.txt`,
`dup-1.txt`, `dup-2.txt` while all three records still said `dup.txt` — displaying the same
name three times *and* probing for whichever file happened to exist. The resolved name is now
captured alongside the URI on first completion (`updateLocalUri` → `updateCompletionMetadata`).
Verified on both AVDs: rows read `dup-2.txt` / `dup-1.txt` / `dup.txt`, and `archive-1.zip`,
`report-1.pdf` on API 24.

### 🐞 Defect 4 — FR-029's "offer to remove the stale entry" was never wired up

**Found by**: G4 step 4.

`DownloadsEvent.FileMissing` carried the record *specifically* so removal could be offered,
and `DownloadsScreen` then collapsed every event to a bare message and dropped it. Tapping a
row whose file was gone said so and left the user holding a list entry they had just been
told was meaningless, with no way to act on it. FR-029 and FR-024b both require the offer.

Two fixes: the snackbar now carries a **Remove from list** action, and
`OpenDownloadedFileUseCase` routes a `Missing` entry to `FileMissing` rather than letting it
fall through to `NotComplete` — which had been telling the user to wait for a transfer that
finished long ago. Regression test added for the FR-024b branch.

### Gate results

| Gate | Device | Result |
|------|--------|--------|
| G1 download end-to-end, ≥10 downloads / ≥4 types | both | ✅ API 24 prompts once then never again; API 36 never prompts |
| G2 list, live status, process death ×5, airplane mode | API 36 | ✅ progress ≥1 Hz; 5/5 relaunches showed the platform's real state; `Paused` → resumed |
| G3 duplicate filenames | both | ✅ 3 files, none overwritten, 3 correctly-named rows |
| G4 opening a download (4 cases) | **both** | ✅ **zero `FileUriExposedException` on API 24** |
| G5 action-sheet matrix + every action | API 36 | ✅ 4 reachable states match exactly (see note) |
| G6 five cancellations | API 36 | ✅ 25/50/52/75/90 %, one tap each, **zero partial files** |
| G7 360dp layout, menu, regression, tap count | API 36 | ✅ 5 controls + overflow, no clipping; **SC-004 = 2 taps** (budget 3) |
| G8 forgotten transfer handle | API 36 | ✅ **after defect 3 was fixed** — Complete while present, Missing once deleted, no exception |
| G9 notification permission ⛔ | API 36 | ✅ Outcome A (closed in device pass 2) |
| G10 8 locales + accessibility | API 36 | ✅ all 8 translated; every element labelled; `20 Б` / `20 octet(s)` / `오후 11:55` |
| G11 empty state, live replace, incognito, upgrade | API 36 | ✅ incognito download listed with **no** marking (FR-014a) |
| G12 hostile filenames (SC-015) | both | ✅ all six land as plain single segments; nothing written outside Downloads |
| G12 performance (SC-006) | — | ⚠️ **DEFERRED** — see below |

### SC-006 is DEFERRED, deliberately

512 seeded records on a **debug** build on a **software-rendered emulator** measured p99
700 ms / 96 % janky. That number is **not** reported against SC-006, whose target is
p99 ≤ 16 ms on a **release** build on **Pixel 5-class hardware** — quickstart.md warns
explicitly against repeating Spec 014's T103b, which measured the wrong build on the wrong
hardware and missed by 20×.

The pipeline was re-read rather than guessed at: `itemsFlow` already carries
`.flowOn(dispatchers.default)`, so the O(n) resolve runs off the main thread, and the poll
loop stops the moment nothing is in flight (R7). Spec 014's pass-4 defect is not repeated
here. The gap is a measurement, not a suspected regression.

### Two findings recorded rather than fixed

- **`DownloadStatus.Cancelled` is unreachable.** `CancelDownloadUseCase` deletes the row on
  cancel — a deliberate reading of FR-038 documented on that class — and the cursor mapper
  never produces `Cancelled`. So G5's matrix row for it can never be exercised. The state is
  modelled but dead. Either the sealed member goes or FR-038's reading changes; both are
  larger than a gate pass and neither is a defect in shipped behaviour.
- **quickstart.md's FR-016 check gives a false positive.**
  `git grep fallbackToDestructiveMigration -- app/src/main/` returns 2 hits, both **comments
  documenting the ban**. The invariant holds; the check needs `-- ':!*.kt'` or an eyeball.

### Automated gates, re-run after both fixes

`testDebugUnitTest` ✅ **480** · `lintDebug` ✅ · `detekt` ✅ baseline UNCHANGED ·
`ktlintCheck` ✅ · `assembleDebug` ✅ · `assembleRelease` ✅ · 16 KB ✅ (every `.so`
`align=0x4000`) · APK release **2.44 MB** vs 2.36 MB baseline = **+80 KB**, inside SC-012's
+200 KB budget · `app/schemas/…/2.json` tracked (FR-017).

### Tooling notes for whoever runs these next

- The debug build's applicationId is `com.raumanian.thirtysix.browser.debug`. `am start` and
  `pm clear` against the un-suffixed id fail with "No activities found to run".
- `adb shell input text` silently went nowhere for a long stretch: a Gboard **"Try out your
  stylus"** tutorial dialog was on top, swallowing every keystroke. Screenshot before
  concluding the app is at fault.
- The address bar and its Clear affordance are drawn under the status bar strip; taps only
  register at y ≈ 140 on a 1080×2340 screen, and the field must be cleared from the keyboard
  rather than via Clear, or the old URL is silently appended to the new one.
- Revoking `WRITE_EXTERNAL_STORAGE` alone on API 24 does nothing — it shares a permission
  group with `READ_EXTERNAL_STORAGE` and is re-granted. Revoke both.
- A resumable download needs `Accept-Ranges` **and an `ETag`**; without a validator the
  platform reports `CANNOT_RESUME` and the airplane-mode recovery step cannot pass.
