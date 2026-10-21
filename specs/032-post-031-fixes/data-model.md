# Data Model: Post-031 Audit Fixes

There is no schema change.

## New or changed types

| Type | Where | Change |
|---|---|---|
| `ExternalLinkPolicy.isTapped(hasGesture, isRedirect, navigationTapped)` | `feature/browser/domain/usecase/` | The time argument is replaced by the chain flag. |
| `DownloadReferrer.of(pageUrl: String?, downloadUrl: String, isIncognito: Boolean): String?` | `feature/browser/domain/` | New, pure. See the contract. |
| `CaptureCache` (`suspend fun clear()`) | `core/domain/repository/` | New. `CaptureCacheImpl` sits over `CaptureFiles`. |
| `CookieJarSnapshotManager.restoreSnapshot(): Boolean` | `core/domain/repository/` | It returned `Unit`. It is `false` on a timeout or failure. |
| `SitePermissionRepository.removeSites(origins): Result<Unit>` | `core/domain/repository/` | New, one transaction. |
| `SitePermissionDao.deleteOrigins(origins: List<String>): Int` | `core/data/datasource/local/` | New `@Query`. |
| `TabDao.setLastActiveAt(id, at)` and `setUrlAndTitle(id, url, title, updatedAt)` | `core/data/datasource/local/` | New `@Query UPDATE` methods. |
| `BrowserLimits.MAX_FILE_NAME_BYTES` | `core/domain/util/` | 255 → 245. |
| `BrowserLimits.EXTERNAL_TAP_WINDOW_MS` | | Removed. |
| `BrowserPageView.lastTapUptimeMs` | | Removed. |

## State transitions

**Page loading state**, as in Spec 031, with these additions:

```text
any     ──active tab id changes──▶ Idle
Failed  ──Retry action──▶ Loading          (before onPageStarted)
Failed  ──title received──▶ Failed         (title ignored)
```

**Navigation tap flag** (`BrowserWebViewClient`):

```text
main-frame request, not redirect ──▶ flag = hasGesture
main-frame redirect              ──▶ flag unchanged
app-issued load                  ──▶ flag = false
```

## Tests by fix (all failing first)

| FR | Test |
|---|---|
| 001 | `ExternalLinkPolicyTest`: a redirect counts as tapped only with the chain flag set. `ExternalAppHandoffTest`: a scroll that ends in a `touchend` redirect gives the offer and no launch. |
| 002 | `ExternalAppLauncherTest`: an own-package intent or an http(s) data intent returns a fallback and starts nothing. |
| 003 | Unit test on the long-press callback builder (or `DownloadRequestViewModelTest`): `cookies == null`. |
| 004 | `DownloadReferrerTest`: same origin, cross origin, https → http, `data:` page, incognito. |
| 005 | `RunFireUseCaseTest`: a throwing keep-set read gives no crash, the three steps are failed, and the others still run. `FireCoordinatorTest`: a throw clears the marker. |
| 006 | `CookieJarSnapshotManagerImplTest`: a timeout returns `false`. `RunFireUseCaseTest`: `false` marks CloseIncognito failed and takes the full-wipe path. |
| 007 | `SitePermissionDaoTest`: `deleteOrigins`. `RunFireUseCaseTest`: an error marks the step failed. |
| 008 | `CaptureCacheImplTest` (Robolectric). `RunFireUseCaseTest` calls `clear`. |
| 009 | `SettingsViewModelTest`: clearing continues after `viewModelScope` is cancelled. |
| 010 | `ClearBrowsingDataUseCaseTest`: History clears `HttpsUpgradeFailures`. |
| 011, 013, 014, 016 | `PageViewModelTest`: tab switch resets to Idle, the title is ignored while Failed, Retry gives Loading, a preview for a closed tab is not saved. |
| 012 | Instrumented: a redirect to a self-signed host shows the error screen. |
| 015 | `TabRepositoryImplTest`: a concurrent switch and title update both persist. |
| 017 | `TabSessionRepositoryImplTest`: a stale list does not mark a new open tab closed. |
| 018 | `AndroidDownloadManagerGatewayTest`: when `remove` returns 0, no file is deleted. |
| 019 | `AtomicFileTest`: two concurrent writes give one whole file. |
| 020, 022 | `SanitizeDownloadFileNameUseCaseTest`: U+202E is dropped, a name of only format characters falls back, and the cap is 245. |
| 021 | `AndroidExternalFileOpenerTest`: each generic type. |
| 023 | `AndroidDownloadManagerGatewayTest`: a failing chunk keeps the others. |
| 024 | `NetscapeBookmarkParserTest`: linear time, with 10k links under a bound. `BookmarkImportScaleTest` (instrumented): 10k under 2 s, and 10 MB unclosed under 5 s. |
| 025 | `NetscapeBookmarkParserTest`: the four address shapes. `BookmarkUrlValidatorTest`: an underscore host. |
| 026 | Compose test on `BookmarksScreen`: one character shows no "no match" text. |
| 027, 028, 029 | Checked by review and the existing tests. |
