# Implementation Plan: Post-031 Audit Fixes

**Branch**: `032-post-031-fixes` | **Date**: 2026-10-21 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/032-post-031-fixes/spec.md`

## Summary

This plan fixes the 29 items of the second audit, mostly regressions from Spec 031, with the smallest change each:

- **Tap rule** (R1): a navigation-chain flag replaces the 1 s window. `intent:` links that point back at this app load in the tab.
- **Privacy** (R2, R5, R7): long-press downloads carry no cookies, and the referrer follows a pure `DownloadReferrer` rule. Fire survives an unreadable keep set and reports a cookie restore that timed out. Site permissions are removed in one query, and upload photos are swept. Clearing browsing data runs in the app scope and clears HTTPS-upgrade failures.
- **Tabs and errors** (R3, R4, R8): the loading state resets on a tab switch. The title is frozen while the page has failed, and Retry shows loading at once. The redirect target becomes the main-frame URL. Tab updates are narrow `UPDATE`s.
- **Files** (R6): deletes are confirmed by the download service first. Temp files get unique names, formatting characters are dropped, more types count as generic, names are capped at 245 bytes, statuses are kept per chunk, and late previews are dropped.
- **Import** (R9): parsing is linear, more address shapes are encoded or accepted, and a one-letter search shows no message.
- **Rendering and lean** (R10): no disk read in composition, one missing `contentType` added, three lean items.

There is no schema change and no new dependency. The only new DAO method is `SitePermissionDao.deleteOrigins`.

## Technical Context

**Language/Version**: Kotlin 2.3.21, AGP 9.1.1 (Android Studio JBR)
**Primary Dependencies**: Compose BOM 2026.04.01, Hilt 2.59.2 (KSP), Room 2.8.4, DataStore 1.2.1, androidx.webkit 1.17.0. No new dependency.
**Storage**: Room v6 unchanged (new `@Query` methods only), DataStore unchanged.
**Testing**: JUnit 4, coroutines-test, Turbine, Robolectric, in-memory Room, Konsist, Espresso-Intents, and Compose UI tests. The device checks are listed in [quickstart.md](quickstart.md).
**Target Platform**: Android 7.0–16, 16 KB pages
**Project Type**: single-module Android app
**Performance Goals**:
- a 10,000-link import parses in under 2 s on `TA016_API36_16K`, and a 10 MB file with no closing tags in under 5 s (SC-005);
- no change to cold start (±5 %).

**Constraints**:
- incognito sends no referrer and never reaches normal storage;
- no new string;
- no allowlist entry.

**Scale/Scope**: about 30 production files and 25 tests touched.

## Constitution Check

*Gated against constitution **2.2.0**.*

| Principle | Status | Note |
|---|---|---|
| I Feature-first | ✅ | `DownloadReferrer` lives in `feature/browser/domain/`, pure Kotlin. Fire reaches the capture folder through a new `core/domain/repository/CaptureCache` contract with an `Impl` in `feature/browser/data/` bound in `di/`, so privacy does not import browser. |
| II One home | ✅ | New `@Query` methods go in the existing DAOs. `CaptureCacheImpl` is in `data/repository`. |
| III Unidirectional flow | ✅ | `SettingsViewModel` launches into the injected `@ApplicationScope`. Dispatchers stay injected. |
| IV Compose | ✅ | `FileImage` moves its disk read off composition. `contentType` is added. Gate 6: no screen gains a recomposition, and the existing recomposition tests stay green. |
| V No hardcoded values | ✅ | The cap of 245 and the generic MIME set are named constants. No string is added. |
| VI Comments | ✅ | Only "why" comments: why a redirect inherits its chain's tap, why the cap leaves room for the suffix, and why long-press downloads are cookie-free. |
| VII Build and data | ✅ | No migration and no dependency. |
| VIII Tests | ✅ | One failing-first test per fix (see [data-model.md](data-model.md)), plus instrumented checks for FR-001, FR-003, FR-011, FR-012 and FR-013. |
| Data and privacy | ✅ | This spec closes leaks and opens none. |
| Permissions | ✅ | None added. |

**Post-design re-check**: no exemption. **Gate: PASS.** ADR 0032 gets a paragraph update (R11).

## Project Structure

```text
specs/032-post-031-fixes/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md
├── contracts/download-requests.md
├── checklists/requirements.md
└── tasks.md                                    # /speckit-tasks
```

Source files touched (under `app/src/main/kotlin/com/raumanian/thirtysix/browser/`):

```text
core/data/datasource/local/      SitePermissionDao, TabDao
core/data/datasource/local/cache/AtomicFile.kt
core/domain/repository/          CaptureCache (new), CookieJarSnapshotManager, SitePermissionRepository
core/domain/util/                BrowserLimits, BookmarkUrlValidator
core/presentation/designsystem/component/FileImage.kt
feature/browser/domain/          DownloadReferrer (new), ExternalLinkPolicy, SanitizeDownloadFileNameUseCase
feature/browser/data/repository/CaptureCacheImpl (new)
feature/browser/presentation/browser/
    BrowserWebView, BrowserPageView, BrowserCallbacks, PageViewModel, BrowserContent (retry)
    page/ ExternalAppLauncher, ExternalAppEffects, PageSession, BrowserPageRequests, CaptureFiles
feature/bookmarks/               NetscapeBookmarkParser, BookmarksScreen
feature/downloads/data/          AndroidDownloadManagerGateway, AndroidExternalFileOpener
feature/privacy/                 RunFireUseCase, FireCoordinatorImpl, CookieJarSnapshotManagerImpl
feature/settings/                ClearBrowsingDataUseCase, SettingsViewModel
feature/sitepermission/data/     SitePermissionRepositoryImpl
feature/tabs/data/repository/    TabRepositoryImpl, TabSessionRepositoryImpl
docs/adr/0032-external-app-handoff.md
```

## Order of work

1. **US1**: tap rule and self-targeting `intent:` links.
2. **US2**: privacy.
3. **US3**: tabs and errors. This shares `BrowserWebView.kt` and `PageViewModel.kt` with US1 and US2, so it runs after them.
4. **US4**: files.
5. **US5**: import and search.
6. **US6**: smaller fixes and lean.

## Complexity Tracking

No violations.
