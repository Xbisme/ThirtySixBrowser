# Tasks: Post-031 Audit Fixes

**Input**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/download-requests.md](contracts/download-requests.md), [quickstart.md](quickstart.md)

**Tests**: FR-030 and Constitution §VIII require them. Write each test first and see it **fail on the unfixed code**, then apply the fix.

**Paths**:

| Short | Path |
|---|---|
| `M/` | `app/src/main/kotlin/com/raumanian/thirtysix/browser/` |
| `T/` | `app/src/test/kotlin/com/raumanian/thirtysix/browser/` |
| `A/` | `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/` |
| `BR` | `feature/browser/presentation/browser` |

**Gate G1** runs after every story phase: `detekt ktlintCheck testDebugUnitTest compileDebugAndroidTestKotlin lintDebug`, prefixed with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. Everything must be green before the phase's commit.

**Commits**: one commit per story phase, with the FRs in the message body. Source code never names an FR or task (Constitution §VI).

**Devices**: emulators only, always with an explicit serial. Never use the owner's A55.

## Phase 1: Setup

- [X] T001 Record the baseline in "Gate Results" at the end of this file: unit test count, instrumented `@Test` count, release APK size, and the `main` Kotlin line count. Use `032-post-031-fixes` at `29fe731`.

## Phase 2: Foundational

- [X] T002 [P] Update the "gesture" paragraph of `docs/adr/0032-external-app-handoff.md`. A redirect counts as tapped only when the main-frame navigation that started its chain had a gesture. Finger lifts are not tracked.

## Phase 3: User Story 1 – Pages still cannot open other apps on their own (P1) 🎯 MVP

**Goal**: only a tapped navigation and its redirects may hand off; `intent:` links never re-enter this app. **Independent test**: quickstart Q1, Q1b, Q2.

- [X] T003 [P] [US1] In `T/feature/browser/domain/usecase/ExternalLinkPolicyTest.kt`, replace the time-window cases with failing ones:
  - `isTapped(hasGesture=false, isRedirect=true, navigationTapped=true)` is true;
  - `(false, true, false)` is false;
  - `(false, false, true)` is false;
  - `(true, *, *)` is true.
- [X] T004 [US1] In `M/feature/browser/domain/usecase/ExternalLinkPolicy.kt`, change `isTapped` to take `navigationTapped: Boolean`, with no time. Delete `EXTERNAL_TAP_WINDOW_MS` from `M/core/domain/util/BrowserLimits.kt`, and delete `lastTapUptimeMs` with its `ACTION_UP` stamping from `M/BR/BrowserPageView.kt`. Make T003 green.
- [X] T005 [US1] In `M/BR/BrowserWebView.kt` `BrowserWebViewClient`:
  - add `private var navigationTapped = false`;
  - in `shouldOverrideUrlLoading`, for `request.isForMainFrame && !request.isRedirect`, set it to `request.hasGesture()` before any branch, on the web and non-web paths alike;
  - pass it to `isTapped`;
  - reset it to `false` before every app-issued `loadProtected`, through a small reset hook on the client that `wireActions` and the initial load call. A typed or restored address that redirects to an app is then offered, not launched.
- [X] T006 [US1] In `A/BR/ExternalAppHandoffTest.kt`, add a failing case with a new asset page `scroll-redirect.html`. Its `touchend` handler sets `location` to `/redirect?to=tel%3A1`. Swipe up on it, then check that no `ACTION_VIEW tel:1` is intended and the offer snackbar shows. The existing tapped-redirect case must stay green.
- [X] T007 [P] [US1] In `T/BR/page/ExternalAppLauncherTest.kt`, add failing cases. `intent://x#Intent;scheme=https;package=<applicationId>;end` returns `"https://x"` and starts nothing. `intent://y#Intent;scheme=https;end` returns `"https://y"` and starts nothing.
- [X] T008 [US1] In `M/BR/page/ExternalAppLauncher.kt`, after parsing: if the intent data scheme is `http(s)`, or `intent.package == context.packageName`, return the data URL as the fallback (when it is http(s)) without starting anything. Make T007 green.
- [X] T009 [US1] Remove the duplicated decision from `M/BR/page/ExternalAppEffects.kt`. On "Open", call the same hand-off used by `BrowserWebViewClient`: expose `fun handOffTapped(url)` on `PageSession` (`M/BR/page/PageSession.kt`), which re-runs `ExternalLinkPolicy.decide` with `hasGesture = true`. Keep the existing offer tests green.
- [X] T010 [US1] Run G1. Run `ExternalAppHandoffTest` and `ExternalIntentCrashSafetyInstrumentedTest` on `emulator-5554`. Commit `fix(browser): only a tapped navigation hands off, intent links stay in the tab (032 US1)`, with the body naming FR-001, FR-002 and FR-029 (partly).

## Phase 4: User Story 2 – Sign-ins and private data stay put (P1)

**Goal**: no cookie leaks, minimal referrer, robust Fire, photos swept, clearing finishes. **Independent test**: quickstart Q3, plus the unit tests.

- [X] T011 [P] [US2] Write `T/feature/browser/domain/DownloadReferrerTest.kt` first. It covers each rule of the contract in order: incognito, a `data:` page, https → http, same origin with a `#frag` removed, cross origin giving the origin with a port kept.
- [X] T012 [US2] Create `M/feature/browser/domain/DownloadReferrer.kt`, an `object` with `fun of(pageUrl: String?, downloadUrl: String, isIncognito: Boolean): String?`, pure Kotlin with `java.net.URI`. Use it in `M/BR/BrowserWebView.kt` (download listener) and `M/BR/BrowserCallbacks.kt` (long press). Make T011 green.
- [X] T013 [US2] In `M/BR/BrowserCallbacks.kt` (long-press `onDownload`, around lines 93-102), pass `cookies = null` and drop the jar lookup. Add a one-line comment: the download service follows redirects with the same headers. Add a test, in the place that builds the long-press `DownloadRequestAction.Requested` (extract a pure builder if needed), that asserts `cookies == null`.
- [X] T014 [P] [US2] In `T/feature/privacy/domain/usecase/RunFireUseCaseTest.kt`, add failing tests:
  - (a) when `FakeFireproofSiteRepository.observeAll()` throws, `invoke()` returns normally; `failed` contains `CookiesAndSiteData`, the favicon step and the site-permission step; history and tabs are still cleared;
  - (b) `restoreSnapshot()` returning `false` puts `CloseIncognito` in `failed`, and the cookie step calls `clearCookiesAndSiteData()` instead of keep-set clearing;
  - (c) `removeSites` returning `Result.Error` puts the site-permission step in `failed`.
- [X] T015 [US2] Make the domain changes:
  - `M/core/domain/repository/CookieJarSnapshotManager.kt`: `restoreSnapshot(): Boolean`;
  - `M/feature/privacy/data/datasource/local/CookieJarSnapshotManagerImpl.kt`: return `false` when `removeAllCookiesAwait` times out or throws, and keep the write-back;
  - update `T/core/testing/fake/FakeCookieJarSnapshotManager.kt` and every caller;
  - `M/core/domain/repository/SitePermissionRepository.kt`: add `suspend fun removeSites(origins: Collection<String>): Result<Unit>`;
  - `M/core/data/datasource/local/SitePermissionDao.kt`: `@Query("DELETE FROM … WHERE origin IN (:origins)") suspend fun deleteOrigins(origins: List<String>): Int`, chunked by the existing 500 limit pattern if origins can exceed 999;
  - implement in `M/feature/sitepermission/data/repository/SitePermissionRepositoryImpl.kt`, and update its fake.
- [X] T016 [US2] In `M/feature/privacy/domain/usecase/RunFireUseCase.kt`:
  - read the keep set with `runCatching`;
  - on failure, add the three dependent steps to `failed` and skip them; never use an empty set;
  - use `restoreSnapshot()`'s result (surfaced through `IncognitoTabRepository.closeAll(): Boolean`) for `CloseIncognito`; a false result only marks that step failed, and the cookie step keeps its normal keep-set path, as FR-006 says;
  - replace the per-origin `removeSite` loop with one `removeSites`, throwing inside `run` on `Result.Error`.

  In `FireCoordinatorImpl` (`M/feature/privacy/…/FireCoordinator*.kt`), wrap the run so any throwable becomes a failed result and the marker is cleared. Add a test that a throwing use case clears the marker. Make T014 green.
- [X] T017 [P] [US2] Add `T/core/data/datasource/local/SitePermissionDaoTest.kt` cases (in-memory Room): `deleteOrigins` removes exactly those origins.
- [X] T018 [US2] Sweep the upload photos:
  - create `M/core/domain/repository/CaptureCache.kt` (`suspend fun clear()`) and `M/feature/browser/data/repository/CaptureCacheImpl.kt`, which empties the same capture folder on the injected IO dispatcher (data may not import `CaptureFiles`, which is in presentation);
  - bind it in `M/feature/browser/di/`;
  - inject it into `RunFireUseCase` and call it in its cleanup step, inside `run`;
  - in `M/BR/page/BrowserPageRequests.kt` `rememberPageSession`, call `CaptureFiles.clear(context)` once when the session is created, in a `LaunchedEffect` on `LocalIoDispatcher`; the clear on dispose stays.

  Add `T/feature/browser/data/repository/CaptureCacheImplTest.kt` (Robolectric).
- [X] T019 [P] [US2] In `T/feature/settings/presentation/settings/SettingsViewModelTest.kt`, add a failing test: start clearing, cancel `viewModelScope` (clear the VM), and check that every chosen category still ran on the fake use case.
- [X] T020 [US2] In `M/feature/settings/presentation/settings/SettingsViewModel.kt`, inject `@ApplicationScope CoroutineScope` and launch `clearBrowsingData` there. Deliver the result event through the existing channel, ignoring it if the VM is cleared. Make T019 green.
- [X] T021 [P] [US2] In `T/feature/settings/domain/usecase/ClearBrowsingDataUseCaseTest.kt`, add a failing test: the History category calls `HttpsUpgradeFailures.clear()`. Then inject `HttpsUpgradeFailures` into `M/feature/settings/domain/usecase/ClearBrowsingDataUseCase.kt` and call `clear()` in the History step.
- [X] T022 [US2] Run G1. Run `FireDataTest`, `CookieRestoreInstrumentedTest`, `IncognitoIsolationTest` and the settings clear-data instrumented tests on `emulator-5554`. Commit `fix(privacy): no leaked cookies or referrer, sturdier Fire, photos swept (032 US2)` (FR-003 – FR-010).

## Phase 5: User Story 3 – Tabs and errors behave (P1)

**Goal**: history kept across a tab switch after an error, redirect certificate errors shown, titles frozen while failed, Retry shows loading, no lost updates. **Independent test**: quickstart Q4–Q7.

- [ ] T023 [P] [US3] In `T/BR/PageViewModelTest.kt`, add failing tests:
  - (a) the state is `Failed` and the active tab changes to another id, so `loadingState` becomes `Idle`;
  - (b) while `Failed`, `TitleReceived` changes neither the page title nor calls the history title update;
  - (c) while `Failed`, the Retry action gives `Loading` at once;
  - (d) `ScreenshotReady` for a tab id not among the open tabs is not saved.
- [ ] T024 [US3] Make the `M/BR/PageViewModel.kt` changes:
  - reset `loadingState` to `Idle` when the active tab id changes (lines 89-97);
  - in `onTitleReceived`, return when `Failed`;
  - add or extend the Retry/Reload action to set `Loading` before calling the WebView reload, wiring it where the error screen's Retry button calls reload (`M/BR/BrowserContent.kt` / `BrowserErrorState`);
  - in `onScreenshotReady`, skip the save when `tabId` is not in the latest open-tab ids (the VM already observes tabs; add a `StateFlow` of ids if needed);
  - delete the wrong `@Suppress("UNUSED_PARAMETER")` and its comment;
  - inline the pass-through `activeTab` property.

  Make T023 green.
- [ ] T025 [US3] In `M/BR/BrowserWebView.kt`:
  - in `shouldOverrideUrlLoading`'s web-scheme branch, when `request.isForMainFrame` and the protection override returns false, set `mainFrameUrl = request.url.toString()`, redirects included;
  - remove the assignment in `onPageStarted`, and keep the one in `shouldInterceptRequest`.
- [ ] T026 [US3] Instrumented checks:
  - in `A/BR/BrowserScreenOfflineErrorTest.kt`, or a new `A/BR/ErrorRecoveryTest.kt`, cover tab A with two pages, tab B failing, a switch to A and Back, which shows A's first page (Q5);
  - Retry on a stalled server shows the progress indicator within 1 s (Q7);
  - a redirect to a local self-signed HTTPS server shows the error screen (Q4). Reuse the self-signed approach from Spec 031's T047 check, and if it cannot run in androidTest, record a manual run in Gate Results.
- [ ] T027 [P] [US3] In `T/feature/tabs/data/repository/TabRepositoryImplTest.kt` (in-memory Room), add a failing test: run `switchActiveTab(b)` and `updateTabUrlAndTitle(b, …)` concurrently 50 times; B is active and has the new title every time.
- [ ] T028 [US3] Add `setLastActiveAt(id, at)` and `setUrlAndTitle(id, url, title, updatedAt)` `@Query UPDATE` methods to `M/core/data/datasource/local/TabDao.kt`. Use them in `M/feature/tabs/data/repository/TabRepositoryImpl.kt` `switchActiveTab`, `updateTabUrlAndTitle` and `handActiveMark`. Make T027 green.
- [ ] T029 [P] [US3] In `T/feature/tabs/data/repository/TabSessionRepositoryImplTest.kt`, add a failing test: a `retainOnly` with a stale list does not mark an incognito tab that saved after the previous retain as closed. Then, in `M/feature/tabs/data/repository/TabSessionRepositoryImpl.kt`, add only `lastOpenIncognito - open` to `closedIncognito`.
- [ ] T030 [US3] Run G1, plus the T026 instrumented tests and `BrowserTabSwipeTest` on `emulator-5554`. Commit `fix(browser): keep tab history after an error, redirect certificate errors, frozen titles (032 US3)` (FR-011 – FR-017, FR-029).

## Phase 6: User Story 4 – Files are never lost, mixed or disguised (P1)

- [ ] T031 [P] [US4] In `T/feature/downloads/data/datasource/local/AndroidDownloadManagerGatewayTest.kt`, add failing tests:
  - when `remove` returns 0, an existing `Downloads/report.pdf` is not deleted;
  - with 1,200 handles where chunk 2's query throws, the result still has the statuses of chunks 1 and 3.
- [ ] T032 [US4] In `M/feature/downloads/data/datasource/local/AndroidDownloadManagerGateway.kt`:
  - `deleteFile` deletes by name only when `remove(handle) > 0`; otherwise it returns `true` with no file touched, so the row can be removed;
  - `queryStatuses` catches per chunk.

  Make T031 green.
- [ ] T033 [P] [US4] Write `T/core/data/datasource/local/cache/AtomicFileTest.kt` first: 20 concurrent `writeAtomically` calls on the same file with different full payloads leave exactly one whole payload and no `.tmp` files.
- [ ] T034 [US4] In `M/core/data/datasource/local/cache/AtomicFile.kt`, use `File.createTempFile(name, ".tmp", parentFile)`. Confirm that `DiskFaviconCache.retainOnly` and `clearAll`, and the screenshot equivalents, delete stray temp files; add one if not. Make T033 green.
- [ ] T035 [P] [US4] In `T/feature/browser/domain/usecase/SanitizeDownloadFileNameUseCaseTest.kt`, add failing tests:
  - `invoice‮fdp.apk` gives a name with no U+202E, ending in `.apk`;
  - a name of only format characters plus `.pdf` gives the default name with `.pdf`;
  - the cap is 245 bytes.
- [ ] T036 [US4] In `M/feature/browser/domain/usecase/SanitizeDownloadFileNameUseCase.kt`, drop `Character.getType(c) == Character.FORMAT`. Set `BrowserLimits.MAX_FILE_NAME_BYTES = 245` with a one-line reason (room for the system's " (1)" suffix). Make T035 green.
- [ ] T037 [P] [US4] In `T/feature/downloads/data/datasource/local/AndroidExternalFileOpenerTest.kt`, add failing cases for `binary/octet-stream`, `application/force-download`, `application/x-download` and `application/download`. Then extend the generic set in `M/feature/downloads/data/datasource/local/AndroidExternalFileOpener.kt` as a named constant set.
- [ ] T038 [US4] Run G1 and the downloads instrumented tests on `emulator-5554`. Commit `fix(downloads): confirmed deletes, unique temp files, honest names (032 US4)` (FR-018 – FR-023).

## Phase 7: User Story 5 – Import and search stay quick and keep every bookmark (P2)

- [ ] T039 [P] [US5] In `T/feature/bookmarks/data/datasource/local/NetscapeBookmarkParserTest.kt`, add failing tests:
  - a generated flat file of 10,000 links parses in under 1,500 ms on the JVM (loose bound; it fails at about 8 s today);
  - a 2 MB `<A HREF=…>` stream with no `</A>` parses in under 2,000 ms;
  - `https://a.example/#x#y`, `https://a.example/p[1]`, `https://münchen.de/` and `https://my_site.example.com/` are all imported, and their stored URLs pass `BookmarkUrlValidator`.
- [ ] T040 [US5] In `M/feature/bookmarks/data/datasource/local/NetscapeBookmarkParser.kt`:
  - `textUntilClose` searches entry boundaries only between `pos` and `close`, with an index-bounded case-insensitive scan, not a substring copy;
  - it applies boundaries only for `h3` and `a`;
  - it caches "no `</tag` after offset X" per tag, so later calls do not rescan;
  - `encodeUnsafe` encodes every `#` after the first, `[` and `]` after the authority, and converts the host with `IDN.toASCII(host, IDN.ALLOW_UNASSIGNED)`, catching `IllegalArgumentException` and treating it as skipped.
- [ ] T041 [US5] In `M/core/domain/util/BookmarkUrlValidator.kt`, accept a host with `_`: when `URI.host` is null, read the host from `rawAuthority` without userinfo or port, and validate it with the existing host rules plus `_`. Add a case to `T/core/domain/util/BookmarkUrlValidatorTest.kt`. Make T039 green.
- [ ] T042 [P] [US5] Add `A/feature/bookmarks/data/BookmarkImportScaleTest.kt`. Parse the 10k file from `LargeBookmarkFile` and a generated 10 MB unclosed file on the emulator, assert under 2 s and under 5 s (SC-005), and record the medians in Gate Results.
- [ ] T043 [US5] In `M/feature/bookmarks/presentation/bookmarks/BookmarksScreen.kt` (line 196), show `NoSearchMatchesState` only when `searchQuery.trim().length >= BrowserLimits.SEARCH_MIN_CHARS`. Add a Compose test in `A/feature/bookmarks/presentation/bookmarks/` (or extend an existing one): one character shows no "no match" text.
- [ ] T044 [US5] Run G1, plus `BookmarkTransferTest`, `BookmarkSearchScaleTest` and T042 on `emulator-5554`. Commit `fix(bookmarks): linear import, more addresses kept, quiet one-letter search (032 US5)` (FR-024 – FR-026).

## Phase 8: User Story 6 – Smaller fixes (P3)

- [ ] T045 [P] [US6] In `M/core/presentation/designsystem/component/FileImage.kt`, move `lastModified()` into the IO-side producer keyed by the path, so composition reads no disk. Keep `rememberFileImage` callers unchanged.
- [ ] T046 [P] [US6] In `M/feature/sitepermission/presentation/sitepermissions/SitePermissionsScreen.kt` (line 94), add `contentType` to the empty item.
- [ ] T047 [US6] Run G1. Commit `fix: off-main-thread file metadata, list content types (032 US6)` (FR-027, FR-028).

## Phase 9: Polish

- [ ] T048 Run the full gates from [quickstart.md](quickstart.md): `connectedDebugAndroidTest` on `TA016_API36_16K` and `TA016_API29_CI`, assembleRelease with the 16 KB script, and the compose report.
- [ ] T049 Run quickstart Q1–Q8 on `emulator-5554` and record each result.
- [ ] T050 Run `python3 scripts/comment-check.py` on every changed `.kt` file. Grep the added source lines for `FR-`, task IDs, `Spec 03` and `ADR`, and expect none.
- [ ] T051 Fill in Gate Results and write `specs/032-post-031-fixes/pr-description.md`: one line per audit item → FR → commit, the Q results, the gates and any deviations, ending with the attribution line. Push and open the PR only after the user confirms.

## Dependencies

- **Shared files**: US1 → US2 → US3 all edit `BrowserWebView.kt`, so run them in that order. US3 also edits `PageViewModel.kt`. US2 and US3 both touch `RunFireUseCase` and the tabs code only in different functions; still run US2 before US3.
- **Independent stories**: US4, US5 and US6 can run in any order after US3.
- **Within a story**: the test comes before its fix. `[P]` tasks touch different files.

## Parallel examples

- **US2**: T011, T014, T017, T019 and T021 can start together.
- **US3**: T023, T027 and T029 can start together.
- **US4**: T031, T033, T035 and T037 can start together.

## Implementation strategy

The MVP is US1, because it is the security regression. Then US2 (leaks and crash) and US3 (lost history). US4–US6 follow. Each phase is one commit with a green build.

## Gate Results

| Gate | Baseline (T001) | Final (T048–T049) |
|---|---|---|
| Unit tests | 1147, 0 failures | |
| Instrumented tests | 225 `@Test` | |
| Release APK size | 3,860,303 B | |
| `main` Kotlin lines | 31,763 | |
| Quickstart Q1–Q8 | n/a | |
| Import 10k / 10 MB unclosed | n/a | |
