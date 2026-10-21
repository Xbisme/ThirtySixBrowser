# Research: Post-031 Audit Fixes

Paths are under `app/src/main/kotlin/com/raumanian/thirtysix/browser/`. Line numbers are from `main` at `29fe731`.

## R1. Tap rule for redirects (FR-001, FR-002, FR-029)

- **Decision**: `BrowserWebViewClient` keeps one `navigationTapped: Boolean`.
  - In `shouldOverrideUrlLoading`, a main-frame request that is **not** a redirect sets it to `request.hasGesture()`. That applies to web and non-web schemes alike.
  - A main-frame redirect keeps the value. The external-link decision uses `hasGesture || (isRedirect && navigationTapped)`.
  - App-issued loads (`loadProtected`) never pass through `shouldOverrideUrlLoading`. They reset the value to `false` through a callback before `loadUrl`, so a redirect of a typed address is offered, never launched.
  - `BrowserPageView.lastTapUptimeMs`, `BrowserLimits.EXTERNAL_TAP_WINDOW_MS` and the time argument of `ExternalLinkPolicy.isTapped` are deleted.
- **Why**: WebView drops the gesture on a server redirect but reports `isRedirect`. The first hop of the same navigation still carries the true `hasGesture`.
- **FR-002**: `ExternalAppLauncher.launch` parses the intent first.
  - If its `package` is the app's own, or its data scheme is `http(s)`, it returns that data URL as the fallback, so it loads in the tab.
  - Otherwise it launches as before.
- **FR-029**: `ExternalAppEffects` stops re-deciding Launch or Confirm itself. It calls the same `handOff` path through `PageSession`.
- **Rejected**: a shorter time window. Any window lets a scroll end count as a tap.

## R2. Download cookies and referrer (FR-003, FR-004)

- **Decision**:
  - The long-press `onDownload` in `BrowserCallbacks.kt:93-102` passes `cookies = null`.
  - A pure `DownloadReferrer.of(pageUrl, downloadUrl, isIncognito): String?` in `feature/browser/domain/` gives:
    - `null` when incognito, when the page is not http(s), or when https → http;
    - the origin when the hosts differ;
    - the full page URL without its fragment when they match.
  - Both download paths use it.
- **Rejected**: following redirects ourselves to scope cookies per hop. That is a lot of code for a rare need.

## R3. Tab switch while Failed, retry, error-page title (FR-011, FR-013, FR-014)

- **Decision**:
  - When the active tab id changes, `PageViewModel` resets `loadingState` to `Idle`. That is where `pageStore.setActiveTab` is called (lines 89-97).
  - The `Failed` condition in `BrowserWebView.kt:234` then never applies to a different tab. It stays for the seeded-error tests, which use one tab.
  - `onTitleReceived` returns early while the state is `Failed`.
  - The error screen's Retry action sets the state to `Loading` immediately, before `reload()`. `onLoadStarted` is unchanged.
- **Rejected**: dropping the `Failed` check in `BrowserWebView`. `BrowserScreenOfflineErrorTest` relies on it for a seeded error.

## R4. Certificate error after a redirect (FR-012)

- **Decision**: in `shouldOverrideUrlLoading`, a main-frame request on a web scheme that is not overridden sets `mainFrameUrl` to the request URL, redirects included. `@Volatile` stays. `onPageStarted` no longer writes the field, because a late `onPageStarted(A)` can overwrite B.
- **Check**: an emulator test with the asset server redirecting to a local self-signed host (quickstart Q4).

## R5. Fire robustness (FR-005, FR-006, FR-007, FR-008)

- **Decision**:
  - `RunFireUseCase` reads the keep set with `runCatching`. On failure, the steps that need it (`CookiesAndSiteData`, favicons, site permissions) are skipped and added to `failed`. The rest run.
  - `FireCoordinatorImpl.fire()` wraps the run in `runCatching`, so an unexpected throw ends as a failed result and clears the marker. The app scope's lack of an exception handler then no longer matters.
  - `CookieJarSnapshotManager.restoreSnapshot()` returns `Boolean`. It returns `false` on a timeout or failure. Fire's `CloseIncognito` step treats `false` as failed and forces the full wipe path in its cookie step.
  - `SitePermissionDao.deleteOrigins(origins: List<String>): Int` runs one `DELETE … WHERE origin IN (:origins)`. The repository adds `removeSites(origins): Result<Unit>`. Fire throws on `Result.Error` so the step is marked failed.
  - `CaptureFiles.clear(context)` runs in Fire's cleanup through a small `CaptureCache` contract bound in `di/`, since Fire is in domain. It also runs once when `rememberPageSession` is created.

## R6. Files (FR-016, FR-018 – FR-023)

- **FR-018**: `AndroidDownloadManagerGateway.deleteFile` deletes the file by name only when `downloadManager.remove(handle) > 0`.
- **FR-019**: `writeAtomically` uses `File.createTempFile(name, ".tmp", parentFile)`. A temp file left behind by a crash is swept by the caches' existing `clearAll` and `retainOnly`, which delete everything not named `<key>.<ext>`. The task checks that this holds.
- **FR-020**: the sanitiser drops `Character.getType(c) == Character.FORMAT`. If the base name is then empty, the existing default name is used.
- **FR-021**: the opener's generic set becomes `application/octet-stream`, `binary/octet-stream`, `application/force-download`, `application/x-download` and `application/download`.
- **FR-022**: `BrowserLimits.MAX_FILE_NAME_BYTES = 245`. The constant is renamed in KDoc only.
- **FR-023**: `queryStatuses` catches per chunk and merges the chunks that succeeded.
- **FR-016**: `PageViewModel` saves a screenshot only if `tabId` is among the open tabs. It checks that against `ObserveAllTabsUseCase`'s latest value, which it already has.

## R7. Settings and history (FR-009, FR-010)

- **FR-009**: `SettingsViewModel` launches `clearBrowsingData` in the injected `@ApplicationScope` and posts the result event back. The result is dropped if the view model is gone.
- **FR-010**: `ClearBrowsingDataUseCase`'s History step calls `HttpsUpgradeFailures.clear()`. That type is in `core/domain/repository`, so no feature import is needed.

## R8. Tabs (FR-015, FR-017)

- **FR-015**: `TabDao` gets `setLastActiveAt(id, at)` and `setUrlAndTitle(id, url, title, updatedAt)` as `@Query UPDATE`. `TabRepositoryImpl.switchActiveTab`, `updateTabUrlAndTitle` and `handActiveMark` use them, so no whole-row write remains.
- **FR-017**: `TabSessionRepositoryImpl` adds to `closedIncognito` only `lastOpenIncognito - open`.

## R9. Import and search (FR-024 – FR-026)

- **FR-024**: `textUntilClose` searches `<dt` and `<dl` only in `s.substring(pos, close)`, using an index-bounded loop rather than a substring copy. A per-tag "no close tag left" flag stops repeated end-of-file scans. Entry boundaries apply only to `h3` and `a`.
- **FR-025**: before validation, `encodeUnsafe`:
  - also encodes every `#` after the first one;
  - encodes `[` and `]` after the authority;
  - converts the host with `IDN.toASCII(host, ALLOW_UNASSIGNED)`.

  `BookmarkUrlValidator` accepts an underscore host. `URI.host` is null for such hosts, so the host is read from `rawAuthority` when `host` is null. An IDN failure counts the link as skipped.
- **FR-026**: in `BookmarksScreen.kt:196`, the condition becomes `searchQuery.trim().length >= BrowserLimits.SEARCH_MIN_CHARS`.

## R10. Rendering and lean (FR-027 – FR-029)

- **FR-027**: `FileImage` reads `lastModified()` inside its existing `produceState` or IO block, keyed by the path.
- **FR-028**: `SitePermissionsScreen.kt:94` gets `contentType`.
- **FR-029**: remove the `@Suppress("UNUSED_PARAMETER")` and its comment from `PageViewModel`. Inline the pass-through `activeTab`.

## R11. Delivery

One branch with one commit per story, and one PR. No ADR, because ADR 0032 already covers the hand-off rule. Its "gesture" paragraph is updated to state the navigation-chain rule.
