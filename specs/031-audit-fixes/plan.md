# Implementation Plan: Audit Fixes

**Branch**: `031-audit-fixes` | **Date**: 2026-10-20 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/031-audit-fixes/spec.md`

## Summary

This plan fixes the 29 audit items with the smallest change each, and adds two small prompts to the external-app fix.

- **Links to other apps** (R1): a pure `ExternalLinkPolicy` decides Drop, Offer, Confirm or Launch from the main-frame and gesture flags. One `ExternalAppLauncher` builds safe intents (`BROWSABLE`, no component or selector), understands `intent:` and returns its web fallback. The untapped offer reuses the blocked-pop-up snackbar pattern. The incognito confirmation is a new `PageRequest` on the existing queue.
- **Address bar** (R2): only `http`, `https` and `about` count as schemes, and input with whitespace and no scheme is searched.
- **Privacy** (R3, R5, R9, R12): Fire reads the fireproof list from Room, and kept cookies are restored even after a timeout. `app_webview/` is excluded from backup. The incognito flag moves into the same page-store update as the tab. Cookie logs lose their values, and cookie waits get a timeout.
- **Errors** (R4, R6): `Failed` stays until a new load starts. HTTP status errors no longer fail the page. SSL errors count only for the main-frame URL. DataStore gets a corruption handler.
- **Files** (R10, R11): the download name probe is trusted only with a known file address, and the open type is taken from the extension. Downloads carry cookies and a referrer, and non-`http(s)` downloads report "not supported". Names are capped in bytes and keep `+`. Captures stay until the session ends, and cache writes go through a temp file.
- **Search and import** (R7, R8, R13): bookmark search is limited and builds folder paths from one folder map. History and Bookmarks open in the incognito tab. The parser recovers from unclosed tags and validates URLs.
- **Limits and cleanup** (R13, R14): the remaining limit fixes, and about 180 lines of unused or duplicated code removed.
- No schema change and no new dependency.

## Technical Context

**Language/Version**: Kotlin 2.3.21, AGP 9.1.1 (Android Studio JBR)
**Primary Dependencies**: Compose BOM 2026.04.01, Hilt 2.59.2 (KSP), Room 2.8.4, DataStore 1.2.1 (`ReplaceFileCorruptionHandler`), androidx.webkit 1.17.0. No new dependency.
**Storage**: Room v6 unchanged. DataStore file unchanged; only its handler changes. Backup rules XML changes.
**Testing**: JUnit 4, coroutines-test, Turbine, Robolectric (`Intent.parseUri`, `MimeTypeMap`, file caches), in-memory Room, Konsist, Compose UI tests for the snackbar and the dialog. The device checks are in [quickstart.md](quickstart.md).
**Target Platform**: Android 7.0–16, 16 KB pages
**Project Type**: single-module Android app
**Performance Goals**:
- bookmark search keystroke < 300 ms with 10,000 bookmarks on `TA016_API36_16K` (SC-006);
- no change to cold start (±5 %);
- the new snackbar and dialog add no recomposition to the page on load.

**Constraints**:
- incognito never reaches normal storage;
- nothing new is logged that names a site or cookie;
- APK ≤ +10 KB;
- up to 5 new strings (contract), each in all 8 locales.

**Scale/Scope**: about 45 production files touched, roughly 25 tests added or changed, net −150 lines or better (SC-009).

## Constitution Check

*Gated against constitution **2.2.0**.*

| Principle | Status | Note |
|---|---|---|
| I Feature-first | ✅ | Placement:<br>• `ExternalLinkPolicy` in `feature/browser/domain/`, pure Kotlin;<br>• `ExternalAppLauncher` in `feature/browser/presentation/browser/page/` (it uses `Intent`);<br>• the shared `runIoCatching` in `core/data/util/`;<br>• `FireproofSites.originsFor` in `core/domain/model/`, used by privacy and tabs.<br>No feature imports another. |
| II One home | ✅ | `DownloadLimits` in `feature/downloads/domain/model/`. `PageRequest.OpenExternalApp` joins the sealed type. No new Dao, entity or repository. |
| III Unidirectional flow | ✅ | The three forwarding use cases are removed; view models call the repository contract, as III allows. The offer and confirm go through `PageRequestSink` and `PageRequestQueue` like the existing page requests. Dispatchers stay injected; `runIoCatching` takes the dispatcher as a parameter. |
| IV Compose | ✅ | The snackbar is an effect with no state of its own. The dialog reuses `SiteDialogFrame`. Gate 6: the Compose report is attached, and the recomposition count for a load is unchanged (`TabSwipeRecompositionTest` stays green; the dialog's own test asserts ≤ 2 recompositions of the page host while it is shown). |
| V No hardcoded values | ✅ | `BOOKMARK_SEARCH_MAX_RESULTS`, `DownloadLimits.STATUS_QUERY_CHUNK` and `MAX_FILE_NAME_BYTES` are named constants. The new strings (contract) are in all 8 locales. |
| VI Comments | ✅ | "Why" comments only where the code cannot show it: gesture across redirects, the temp-file rename, and the HTTP-error choice (the reason stated in the comment, with no ADR or spec reference). No IDs. |
| VII Build and data | ✅ | No migration and no dependency. No keep rule needed. The backup XML is reviewed. |
| VIII Tests | ✅ | One failing-first unit test per non-trivial fix (see data-model "Tests by fix"). Instrumented: `ExternalAppHandoffTest` (snackbar and incognito dialog) and an updated `BrowserScreenOfflineErrorTest` that uses a real failing load. |
| Data and privacy | ✅ | This spec closes four privacy gaps and opens none. The cookie string for downloads exists only in memory, for the length of one `enqueue`. |
| Permissions | ✅ | None added. No `<queries>` element (R1 rejected `queryIntentActivities`). |
| Performance budgets | ✅ | Cold start measured interleaved on the AVD. No frame-time target changes. |

**Post-design re-check**: no exemption, no new allowlist entry; `KNOWN_VIOLATIONS` can only shrink with the cleanup. **Gate: PASS.**

**ADR**: `docs/adr/0032-external-app-handoff.md` (R15).

## Project Structure

### Documentation

```text
specs/031-audit-fixes/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/external-app-handoff.md
├── checklists/requirements.md
└── tasks.md                                    # /speckit-tasks
```

### Source Code

```text
app/src/main/
├── res/xml/backup_rules.xml, data_extraction_rules.xml            # R5
├── res/values*/strings.xml                                        # new strings × 8 locales
└── kotlin/com/raumanian/thirtysix/browser/
    ├── navigation/AppNavHost.kt                                   # launchSingleTop
    ├── ThirtySixApplication.kt                                    # @ApplicationScope
    ├── core/
    │   ├── data/datasource/local/cache/   DiskFaviconCache, DiskScreenshotCache, AtomicFile.kt (new)
    │   ├── data/datasource/local/         BookmarkDao (LIMIT), HistoryDao, TabSessionDao (dead code)
    │   ├── data/util/RunIoCatching.kt (new)
    │   ├── di/SettingsDataStoreProviderModule.kt                  # corruption handler
    │   └── domain/
    │       ├── model/  AddressBarInputClassifier, DownloadOrder (+cookies), FireproofSites (new)
    │       ├── usecase/ ResolveDownloadStatusUseCase, ObserveActiveTabIsIncognitoUseCase (kept; dropped by 3 VMs)
    │       └── util/BrowserLimits.kt, UrlConstants.kt
    └── feature/
        ├── browser/
        │   ├── domain/ ExternalLinkPolicy (new), StartDownloadUseCase, SanitizeDownloadFileNameUseCase
        │   └── presentation/browser/
        │       ├── BrowserWebView.kt, PageViewModel.kt, ErrorReason.kt, BrowserEffects.kt, DownloadSnackbarEvent.kt
        │       └── page/ ExternalAppLauncher (new), ExternalAppEffects (new), ExternalAppDialog (new),
        │                 PageRequest, PageRequestSink, PageSession, CaptureFiles, PopupWebViewRegistry
        ├── bookmarks/  NetscapeBookmarkParser, SearchBookmarksUseCase, BookmarksViewModel, BookmarkTransferViewModel, BookmarkRepositoryImpl
        ├── downloads/  AndroidDownloadManagerGateway, AndroidExternalFileOpener, DownloadsRepositoryImpl, DownloadLimits (new)
        ├── history/    HistoryViewModel
        ├── privacy/    RunFireUseCase, CookieJarSnapshotManagerImpl, FireproofSiteRepositoryImpl, ProtectionExceptionRepositoryImpl, Remove*UseCase (deleted)
        ├── settings/   AndroidWebDataCleaner, di/ (CookieManager provider)
        ├── sitepermission/ SitePermissionRepositoryImpl, RemoveSitePermissionsUseCase (deleted)
        └── tabs/       TabRepositoryImpl, TabSessionRepositoryImpl, IncognitoSnapshotOrigins, IncognitoTabRepositoryImpl
```

Tests mirror each touched file under `app/src/test/…`. Instrumented tests go in `app/src/androidTest/…/feature/browser/`.

**Structure Decision**: The existing feature-first layout is used. The only new files are the policy, launcher, effect and dialog for R1, the two shared helpers, and `DownloadLimits`.

## Order of work

The stories are independent. The order below goes by risk, and each step ends green on gates 1–3:

1. **US1 + ADR**: hand-off to other apps.
2. **US3**: privacy (Fire, backup, logs, timeouts, the incognito flag).
3. **US4**: files.
4. **US5**: errors and DataStore. Its device checks run together with US1's.
5. **US2**: the address bar.
6. **US6**: History and Bookmarks in incognito, search, import.
7. **US7**: limits.
8. **US8**: cleanup, last, so earlier fixes do not touch code that is being deleted.

## Complexity Tracking

No violations.
