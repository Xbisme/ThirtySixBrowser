# Research: Audit Fixes

Decisions for each fix, smallest first. Paths are under `app/src/main/kotlin/com/raumanian/thirtysix/browser/`.

## R1. Hand-off to other apps (FR-001 – FR-003, FR-002a)

- **Decision**: `shouldOverrideUrlLoading` stops building intents itself. It describes the navigation as an `ExternalNavigation(url, isMainFrame, hasGesture)` and asks a pure `ExternalLinkPolicy` in `feature/browser/domain/` what to do:
  - `Ignore` for web schemes (handled as today);
  - `Drop` for a sub-frame;
  - `Offer` for an untapped main frame (snackbar);
  - `Confirm` for a tapped one in incognito (dialog);
  - `Launch` for a tapped one in a normal tab.

  The Android side lives in one presentation helper, `ExternalAppLauncher`:
  - It parses `intent:` with `Intent.parseUri(…, URI_INTENT_SCHEME)` and builds a plain `ACTION_VIEW` for other schemes.
  - It always sets `addCategory(CATEGORY_BROWSABLE)`, `component = null` and `selector = null`.
  - On `ActivityNotFoundException` it returns the `browser_fallback_url` extra when that is `http(s)`. The tab then loads it through `loadProtected`.
- **Snackbar**: `PageRequestSink` gets `onExternalAppOffered(url, generation)`, the twin of `onPopupBlocked`. `ExternalAppEffects` shows "This page wants to open another app" with "Open", like `PopupEffects`. A newer offer replaces an older one, because a snackbar shows one message at a time. "Open" goes back through the policy as tapped.
- **Dialog**: a new `PageRequest.OpenExternalApp(tabId, generation, origin, onOpen)` goes through the existing `PageRequestQueue`. It is a dialog tied to the page and is cancelled when the page goes stale, the same as `LeavePage`. It reuses `SiteDialogFrame`.
- **Gesture across redirects**: Chromium keeps the user-activation bit on a server redirect, so a tap on `https://shop/x` that 302s to `market://` reports `hasGesture() == true`. This is checked on the emulator (quickstart Q1). If it does not hold, the fallback is to also accept `isRedirect && lastTapWithinMs(BrowserLimits.EXTERNAL_TAP_WINDOW_MS)`. That fallback is not built unless the check fails.
- **Rejected**:
  - A WebView-wide "block all" with no offer: the user chose the snackbar.
  - Resolving activities with `queryIntentActivities` before launch: on API 30+ it needs `<queries>` declarations, and the catch on `ActivityNotFoundException` covers the same case.

## R2. Address bar schemes (FR-004, FR-005)

- **Decision**: `classifyAddressBarInput` takes the scheme branch only for `http`, `https` and `about`. A dot-only match requires no whitespace. Everything else is a `Query`.
- **Effect on the regex**: `URL_HEURISTIC_REGEX` stays as is. The scheme check is a small allowlist set in `UrlConstants`, and the whitespace check is one `any(Char::isWhitespace)`.
- **Rejected**: a guard in `loadProtected`. The classifier is the single entry point for typed input. Links and bookmarks are already limited to `http(s)` elsewhere.

## R3. Fireproof list at Fire time (FR-006, FR-007)

- **Decision**: `RunFireUseCase` and `IncognitoSnapshotOrigins` read `fireproofSites.observeAll().first()`, which is a Room read, instead of the in-memory `currentSites()`. `currentSites()` stays for `IsSiteFireproofUseCase`, whose callers are UI checks made long after start.
- **FR-007**: in `AndroidWebDataCleaner`, the kept cookies are written back after `removeAllCookies` whether it finished in time or not. A timeout still returns the failure outcome.
- **Rejected**: making `currentSites()` suspend everywhere. That widens the change to the UI callers for no gain.

## R4. Error state (FR-021, FR-022, FR-023)

- **Decision**: in `PageViewModel`, `onProgressChanged(100)` and `onLoadFinished` leave `LoadingState.Failed` untouched. Only `onLoadStarted` replaces it. `onLoadFinished` still consumes `pendingHistoryUrl`, so no history row is written for a failed page.
- **FR-022**: `onReceivedHttpError` no longer calls `onLoadFailed`. `ErrorReason.HttpError` and its test case are deleted (lean).
- **FR-023**: `BrowserWebViewClient` keeps the main-frame URL it saw in `onPageStarted`. `onReceivedSslError` fails the page only when `error.url` equals it, and still always calls `handler.cancel()`. The HTTPS-upgrade fallback runs first, as today.
- **Check**: quickstart Q4 and Q5 on the emulator, since the order of callbacks is platform behavior.

## R5. Backup (FR-008)

- **Decision**: add `<exclude domain="root" path="app_webview/"/>` to `backup_rules.xml` and to both blocks of `data_extraction_rules.xml`. `app_webview/` holds cookies, Local Storage, IndexedDB and the service-worker cache. WebView's HTTP cache is under `cacheDir`, which is never backed up.
- **Incognito profiles**: their data lives under `app_webview/` too (named profiles), so the same rule covers them.
- **Check**: `adb shell bmgr backupnow` with the local transport, then list the backup set (quickstart Q6).

## R6. Settings file corruption (FR-024)

- **Decision**: `PreferenceDataStoreFactory.create(corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }, …)` in `SettingsDataStoreProviderModule`. The defaults then show onboarding again. That was accepted in the spec's edge cases.

## R7. Bookmark search (FR-026)

- **Decision**:
  - `BookmarkDao.searchByTitleOrUrl` gets `LIMIT :limit`, with a new `BrowserLimits.BOOKMARK_SEARCH_MAX_RESULTS = 50`. The comment already promised 50.
  - `SearchBookmarksUseCase` combines the search flow with `observeAllFolders()`. It builds each path from an `id → folder` map in memory, stopping at a folder it has already visited, so a corrupt cycle cannot loop.
  - `BookmarksViewModel` skips queries shorter than `BrowserLimits.SEARCH_MIN_CHARS`, which History already uses.
- **Rejected**: a recursive CTE in SQL. The folder list is small (hundreds at most) and already observed by the screen.

## R8. Opening from History and Bookmarks in incognito (FR-025)

- **Decision**: both view models call `updateActiveTabUrlAndTitle` when `activeTabId != 0L`, with `isIncognito = activeTabId < 0L`. This removes `ObserveActiveTabIsIncognitoUseCase` from `HistoryViewModel` and `BookmarksViewModel`, together with `lastKnownIsIncognito`. The negative-id rule is the existing incognito convention (`TabMapper`).

## R9. One source for the incognito flag (FR-011, FR-012)

- **Decision**:
  - `CurrentPageRepositoryImpl.setActiveTab(tab)` sets `isIncognito = tab?.isIncognito ?: false` in the same update as the tab.
  - `PageViewModel` stops collecting `ObserveActiveTabIsIncognitoUseCase`, and `CurrentPageRepository.setIncognito` goes with it. The use case itself stays, because `MainActivity` and `DefaultBrowserInviteViewModel` still use it.
  - The PixelCopy callback and `onReceivedIcon` capture the tab id and incognito flag when the WebView is built and hand them back with the result. `PageViewModel` saves under the captured id and drops the result when it is incognito.

## R10. Downloads (FR-014 – FR-018)

- **FR-014**: `ResolveDownloadStatusUseCase.resolveForgotten` uses the name probe only when `record.localUri != null`. Otherwise it returns `Missing`. `DownloadActionAvailability` already enables delete only when the file is present (`canDeleteFile = fileIsPresent`).
- **FR-015**: `AndroidExternalFileOpener` uses `MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)` when the stored type is blank or `application/octet-stream`, then falls back to `*/*`.
- **FR-016**:
  - `DownloadOrder` gets `cookies: String?`. The WebView's download listener reads them from the tab's own `CookieManager`: the profile's for incognito when `MULTI_PROFILE` is supported, otherwise the default one, which during an incognito session holds the swapped-in incognito jar and passes the page URL as `referer`.
  - The gateway adds `Cookie` and `Referer` headers when present.
  - The cookie string is never logged or stored, and `DownloadRecord` does not get it.
- **FR-017**:
  - `StartDownloadUseCase` returns a new `StartDownloadResult.Unsupported` for any scheme other than `http(s)`, before the gateway is called.
  - A new `DownloadSnackbarEvent.Unsupported` has its string in all 8 locales.
- **FR-018**: `SanitizeDownloadFileNameUseCase`:
  - decodes with `URLDecoder.decode(value.replace("+", "%2B"), UTF_8)`;
  - caps the name with a byte-aware trim that drops whole code points from the end of the base name until `toByteArray(UTF_8).size <= 255`;
  - drops the extension only if the extension alone does not fit.
- **FR-030**: `AndroidDownloadManagerGateway.queryStatuses` queries in chunks of 500, a constant in a new `DownloadLimits` object in `feature/downloads/domain/model/`.

## R11. Page uploads and caches (FR-019, FR-020)

- **FR-019**: `CaptureFiles.newPhoto` no longer calls `clear`. The session's dispose in `BrowserPageRequests` already clears the folder.
- **FR-020**: a private `File.writeAtomically(block)` in `core/data/datasource/local/cache/` writes to `<name>.tmp` and then `renameTo`, deleting the temp file on any failure. Both disk caches use it. One helper serves two callers in the same package.

## R12. Cookie jar (FR-009, FR-010, FR-013)

- **FR-009**: the two `Log` calls in `CookieJarSnapshotManagerImpl` drop the origin and the cookie pair, keeping the throwable.
- **FR-010**: `removeAllCookiesAwait` and the restore path are wrapped in `withTimeoutOrNull(BrowserLimits.WEB_DATA_CLEAR_TIMEOUT_MS)` inside the mutex.
- **FR-013**: `TabSessionRepositoryImpl` keeps a small `closedIncognito` set. `retainOnly` adds the dropped negative ids, and `save` ignores them. Ids are never reused within the process, so the set needs no eviction beyond the incognito tab limit per session. It is cleared when the incognito session ends.

## R13. Limits and leftovers (FR-027 – FR-033)

- **FR-027**: `NetscapeBookmarkParser` ends text at the earliest of `</tag`, `<dt` and `<dl`, matched case-insensitively.
- **FR-028**: the parser accepts a link only when `BookmarkUrlValidator.validate(url)` succeeds, and stores the normalized URL. A rejected link is counted as skipped.
- **FR-029**: `TabRepositoryImpl.createTab` runs its count, position and insert inside the existing `seedMutex`.
- **FR-031**: `AppNavHost`'s onboarding exit uses `launchSingleTop = true`.
- **FR-032**: `PopupWebViewRegistry.forgetTab(tabId)` is called from tab close. The browser screen's dispose destroys and clears everything still waiting.
- **FR-033**: `TabSessionRepositoryImpl.retainOnly` returns early on an empty normal-id list.

## R14. Cleanup (FR-034)

Each item is grep-checked again at implementation time (tasks phase "Cleanup"). Forwarding use cases are replaced by direct repository calls from the view models, as constitution III allows. The three `runIo` wrappers become one `internal suspend fun <T> runIoCatching(dispatcher, block)` in `core/data/util/`. The `www.` expansion becomes `FireproofSites.originsFor(site)` in `core/domain/model/`, next to `IsSiteFireproofUseCase.matches`.

## R15. Delivery

- **Decision**: one branch with one commit per user story, and one PR. Every story compiles and passes the gates on its own, so a review can stop at any commit.
- **ADR**: `docs/adr/0032-external-app-handoff.md` records the gesture rule, the snackbar offer, the incognito confirmation, and why HTTP status errors show the site's page.
