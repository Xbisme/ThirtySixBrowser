# Data Model: Audit Fixes

There is no schema change. These are the in-memory and domain types that change or appear.

## New types

| Type | Where | Shape | Rules |
|---|---|---|---|
| `ExternalNavigation` | `feature/browser/domain/model/` | `url: String`, `scheme: String`, `isMainFrame: Boolean`, `hasGesture: Boolean`, `isIncognito: Boolean` | Built for every scheme outside `http`, `https`, `about`, `data` and `blob`. The policy returns `Drop` for `javascript`, `file` and `content` (see the contract). |
| `ExternalLinkDecision` | same | `Drop` · `Offer` · `Confirm` · `Launch` | See the contract table. |
| `PageRequest.OpenExternalApp` | `…/presentation/browser/page/PageRequest.kt` | `tabId`, `generation`, `origin`, `onOpen: () -> Unit` | `cancel()` does nothing. It is settled once, and stale requests are dropped like the other `PageRequest` types. |
| `FireproofSites.originsFor(site)` | `core/domain/model/` | `(String) -> List<String>`, giving `[site, "www.$site"]` | One home for the three copies. |
| `DownloadLimits` | `feature/downloads/domain/model/` | `STATUS_QUERY_CHUNK = 500` | Under SQLite's 999 bound-argument limit. |

## Changed types

| Type | Change |
|---|---|
| `DownloadOrder` | `+ cookies: String?`. Never logged or persisted; `DownloadRecord` is unchanged. |
| `StartDownloadResult` | `+ Unsupported`. |
| `DownloadSnackbarEvent` | `+ Unsupported`. |
| `ErrorReason` | `− HttpError`. |
| `PageRequestSink` | `+ onExternalAppOffered(url: String, generation: Long) = Unit`. |
| `CurrentPageRepository` | `− setIncognito`. `setActiveTab` sets `isIncognito` from the tab. |
| `BrowserLimits` | `+ BOOKMARK_SEARCH_MAX_RESULTS = 50`, `+ MAX_FILE_NAME_BYTES = 255`. |
| `UrlConstants` | `+ ADDRESS_BAR_SCHEMES = setOf("http", "https", "about")`. |

## Removed

`ObserveActiveTabIsIncognitoUseCase` is **kept**: `MainActivity` and `DefaultBrowserInviteViewModel` still use it. Only `PageViewModel`, `HistoryViewModel` and `BookmarksViewModel` stop depending on it.

The following come out with their tests and fakes:

- `TabSessionRepository.delete` and `TabSessionDao.delete`
- `HistoryDao.getRecent`, `HistoryDao.getById` and `HistoryDao.delete(entry)`
- `TabRepository.getTabCount` and `IncognitoTabRepository.getCount`
- `DownloadManagerGateway.isAvailable` together with `isDownloadProviderDisabled`, `DISABLED_STATES` and `DOWNLOAD_PROVIDER_PACKAGE`
- `DownloadsRepository.count` and `DownloadRecordDao.count`
- `BookmarkRepository.getFolder`
- `RemoveSitePermissionsUseCase`, `RemoveProtectionExceptionsUseCase` and `RemoveFireproofSitesUseCase`

Each one is grep-checked again before deletion.

## State transitions

**Page loading state** (`PageViewModel`):

```text
Loading ──progress 100 / finished──▶ Loaded
Loading ──main-frame error / main-frame SSL──▶ Failed
Failed  ──progress / finished──▶ Failed          (was: Loaded)
Failed  ──load started──▶ Loading
Loaded  ──load started──▶ Loading
```

**Fireproof read at Fire time**: Room → `Set<String>`, read once per run before any clear. There is no in-memory cache on this path.

## Tests by fix

| Fix | Failing-first test |
|---|---|
| R1 policy | `ExternalLinkPolicyTest`: covers every row of the contract table |
| R1 launcher | `ExternalAppLauncherTest` (Robolectric): `BROWSABLE` set, component and selector cleared, `intent:` parsed, http(s) fallback returned, `javascript:` fallback refused |
| R2 | `AddressBarInputClassifierTest`: `javascript://…`, `foo://bar`, `how to use node.js`, `http://a.b/c d` |
| R3 | `FireDataTest` (instrumented): resumed Fire before the cache loads keeps fireproof cookies. `RunFireUseCaseTest`: a fake repository with an empty cache but sites in storage keeps them; `AndroidWebDataCleanerTest`: kept cookies are written back after a timeout |
| R4 | `PageViewModelTest`: Failed, then Progress(100) and Finished, stays Failed; Failed, then Started, becomes Loading. `MainFrameSslErrorTest`: a sub-resource URL does not fail the page |
| R6 | `SettingsDataStoreCorruptionTest` (Robolectric): a garbage file gives the defaults |
| R7 | `SearchBookmarksUseCaseTest`: paths built from the map, cycle guard, limit. `BookmarkSearchScaleTest` (instrumented): median < 300 ms with 10,000 bookmarks |
| R8 | `HistoryViewModelTest` and `BookmarksViewModelTest`: a tap with a negative active id updates that tab as incognito |
| R9 | `PageViewModelTest`: a screenshot that arrives after a switch is saved under the captured id; an incognito one is dropped |
| R10 | `ResolveDownloadStatusUseCaseTest` (name only gives Missing); `SanitizeDownloadFileNameUseCaseTest` (`+`, 150 CJK characters, surrogate at the cut, extension too long); `StartDownloadUseCaseTest` (`blob:` gives Unsupported, cookies passed through); `AndroidExternalFileOpenerTest` |
| R11 | `CaptureFilesTest`; `DiskScreenshotCacheTest` and `DiskFaviconCacheTest` (a failed write leaves no file) |
| R12 | `TabSessionRepositoryImplTest` (closed incognito id ignored; empty keep-set removes nothing); `CookieJarSnapshotManagerImplTest` (timeout unblocks) |
| R13 | `NetscapeBookmarkParserTest` (unclosed `H3`/`A`, invalid URL skipped); `TabRepositoryImplTest` (two concurrent creates at 49 tabs); `AndroidDownloadManagerGatewayTest` (1,200 ids are chunked) |
