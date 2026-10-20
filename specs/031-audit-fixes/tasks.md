# Tasks: Audit Fixes

**Input**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/external-app-handoff.md](contracts/external-app-handoff.md), [quickstart.md](quickstart.md)

**Tests**: required by FR-035 and Constitution §VIII. Each fix's test is written first and must **fail on the unfixed code**. Run it red, then apply the fix.

**Paths**:

| Short form | Path |
|---|---|
| `M/` | `app/src/main/kotlin/com/raumanian/thirtysix/browser/` |
| `T/` | `app/src/test/kotlin/com/raumanian/thirtysix/browser/` |
| `A/` | `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/` |
| `BR` | `feature/browser/presentation/browser` |
| `RES/` | `app/src/main/res/` |

**Gate G1**: run `detekt ktlintCheck testDebugUnitTest compileDebugAndroidTestKotlin` after every story phase, prefixed with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. Everything must be green before that phase's commit.

**Commits**: one commit per story phase. The message names the story and the FR numbers. Source code never mentions an FR or task ID (Constitution §VI).

## Phase 1: Setup

- [X] T001 Record the baseline on `031-audit-fixes`, branched from `main` at `600c9a9`, in "Gate Results" at the end of this file:
  - unit test count;
  - instrumented test count;
  - release APK size;
  - `find app/src/main -name '*.kt' | xargs wc -l | tail -1`, the baseline for SC-009.

## Phase 2: Foundational (blocks the stories noted)

- [X] T002 [P] Add `docs/adr/0032-external-app-handoff.md`. It records:
  - the gesture and main-frame rule;
  - the snackbar offer for untapped attempts and the incognito confirmation;
  - the safe intent (`BROWSABLE`, no component or selector);
  - `intent:` fallback limited to `http(s)`;
  - HTTP status errors show the site's own page.

  Add one line to `docs/adr/README.md` if that index exists. Blocks US1 and US5 commits.
- [X] T003 [P] Add the new strings in `RES/values/strings.xml` and the 7 locale folders (`values-vi`, `-de`, `-ru`, `-ko`, `-ja`, `-zh`, `-fr`):
  - `external_app_offer` = "This page wants to open another app";
  - `external_app_confirm_title` = "Open in another app?";
  - `external_app_confirm_body` = "The app will see this link.";
  - `downloads_unsupported_snackbar` = "This kind of download isn't supported".

  For the "Open" action, reuse `popup_open` if its text in every locale is a plain "Open". Otherwise add `external_app_open`. Blocks US1 and US4.

## Phase 3: User Story 1 – Pages cannot open other apps on their own (P1) 🎯 MVP

**Goal**: no app opens without a tap on the main page; untapped attempts are offered; incognito confirms; `intent:` works with its fallback. **Independent test**: quickstart Q1, Q1b, Q2, Q3 and Q3b.

**Policy (pure)**

- [X] T004 [P] [US1] Write `T/feature/browser/domain/usecase/ExternalLinkPolicyTest.kt` first. It covers every row of the contract table:
  - sub-frame → `Drop`;
  - main frame, no gesture → `Offer`;
  - main frame, gesture, normal → `Launch`;
  - main frame, gesture, incognito → `Confirm`;
  - `javascript:`, `file:` and `content:` → `Drop` in every row.
- [X] T005 [US1] Create `M/feature/browser/domain/model/ExternalNavigation.kt` with `data class ExternalNavigation(val url: String, val scheme: String, val isMainFrame: Boolean, val hasGesture: Boolean, val isIncognito: Boolean)` and `sealed interface ExternalLinkDecision { Drop, Offer, Confirm, Launch }`. Then create `M/feature/browser/domain/usecase/ExternalLinkPolicy.kt`, a pure `object` with `fun decide(nav: ExternalNavigation): ExternalLinkDecision` and the set of never-handed-off schemes. Make T004 green.

**Launcher (Android)**

- [X] T006 [P] [US1] Write the Robolectric test `T/BR/page/ExternalAppLauncherTest.kt` first. It checks:
  - a `market:` URL builds `ACTION_VIEW` with `CATEGORY_BROWSABLE`, `component == null` and `selector == null`;
  - an `intent://x#Intent;component=com.evil/.Hidden;S.browser_fallback_url=https%3A%2F%2Fexample.com;end` has its component and selector cleared;
  - when no activity resolves (Robolectric `ShadowPackageManager` with no match), `launch` returns `"https://example.com"`;
  - a `javascript:` fallback returns `null`;
  - a malformed `intent:` returns `null` and does not throw.
- [X] T007 [US1] Create `M/BR/page/ExternalAppLauncher.kt` with `fun launch(context: Context, url: String): String?`. It returns a fallback URL to load, or `null`. It follows the contract's "Launch" steps 1–3, and its only log is `"External app hand-off failed"` with the throwable and no address. Make T006 green.

**Wiring**

- [X] T008 [US1] In `M/BR/page/PageRequest.kt`, add `class OpenExternalApp(tabId, generation, origin, private val onOpen: () -> Unit) : PageRequest(...)` with `fun open() = settle(onOpen)` and `override fun cancel() = settle {}`.
- [X] T009 [US1] In `M/BR/page/PageRequestSink.kt`:
  - add `fun onExternalAppOffered(url: String, generation: Long) = Unit`;
  - implement it in `M/BR/page/PageSession.kt`, next to `override fun onPopupBlocked` (line 97). It emits an `ExternalAppOffer(url, generation)` event on a `Channel`/`SharedFlow`, the same way `PopupTabs.events` does, and drops offers whose generation is not the current page's.
- [X] T010 [US1] Rewrite `shouldOverrideUrlLoading` in `M/BR/BrowserWebView.kt` (lines 356-377):
  - web schemes keep today's `WEB_NATIVE_SCHEMES` branch;
  - otherwise, build `ExternalNavigation` from `request.url`, `request.isForMainFrame`, `request.hasGesture()` and `pageRequests.context()?.isIncognito`, then call `ExternalLinkPolicy.decide`:
    - `Drop` → `true`;
    - `Offer` → `pageRequests.onExternalAppOffered(url, generation)` and `true`;
    - `Confirm` → `pageRequests.enqueue(PageRequest.OpenExternalApp(…) { handOff(view, url) })` and `true`;
    - `Launch` → `handOff(view, url)` and `true`;
  - `handOff` calls `ExternalAppLauncher.launch` and, when it returns a fallback, loads it with `view.loadProtected(fallback, protection)`.

  Remove the old inline intent code and its `@Suppress("TooGenericExceptionCaught")`.
- [X] T011 [P] [US1] Create `M/BR/page/ExternalAppEffects.kt`, modelled on `PopupEffects.kt`. It collects offers, calls `showSnackbar(message = external_app_offer, actionLabel = open)`, and on `ActionPerformed` re-decides the offer with `hasGesture = true`, so normal tabs launch and incognito tabs enqueue `OpenExternalApp`. Wire it in `M/BR/BrowserScreen.kt` next to `PopupEffects`, using the same `snackbarHostState`.
- [X] T012 [P] [US1] Create `M/BR/page/ExternalAppDialog.kt` with `SiteDialogFrame`: title `external_app_confirm_title`, body `external_app_confirm_body`, buttons Open (`request.open()`) and the frame's Cancel (`request.cancel()`). Dispatch `PageRequest.OpenExternalApp` to it in `M/BR/page/PageRequestHost.kt`, in the same `when` as the other request kinds. Both buttons are 48 dp and labelled for TalkBack.

**Instrumented**

- [X] T013 [US1] Update `A/BR/ExternalIntentCrashSafetyInstrumentedTest.kt` to the new behavior: a malformed or unhandled external URL still does not crash, now through the launcher.
- [X] T014 [US1] Create `A/BR/ExternalAppHandoffTest.kt` with `AssetPageServer` pages in `app/src/androidTest/assets/`:
  - (a) a page `external-untapped.html` runs, one per load, 10 untapped attempts on the main frame: `location = …`, `location.replace`, `location.assign`, `<meta http-equiv=refresh>`, a `setTimeout` redirect, `window.open` in the same tab, an auto-submitted `<form action="market://…">`, an `<a>` `.click()` from script, `history` push then assign, and a 302 from the asset server to `market://`. Each starts no activity (Espresso-Intents `intended(...)` count 0) and shows the `external_app_offer` snackbar (SC-001);
  - (b) an iframe redirect to `tel:1` shows no snackbar and starts no activity;
  - (c) in an incognito tab, a tapped `market:` link shows the dialog; Cancel starts no activity, and Open starts one with `CATEGORY_BROWSABLE`;
  - (d) a recomposition counter on the page host stays ≤ 2 while the dialog is shown (gate 6).
- [X] T015 [US1] Run G1. Commit `fix(browser): pages open other apps only after a tap (031 US1)`.

## Phase 4: User Story 3 – Sign-ins and private data stay where the person put them (P1)

**Goal**: fireproof survives every Fire; no web data in backup; no cookie in logs; incognito never leaks through a race. **Independent test**: quickstart Q6 and Q7.

- [X] T016 [P] [US3] In `T/feature/privacy/domain/usecase/RunFireUseCaseTest.kt`, add a failing test:
  - `FakeFireproofSiteRepository` (`T/core/testing/fake/`) gets a mode where `currentSites()` returns empty while `observeAll()` emits `["example.com"]`;
  - Fire must take the keep-sites path, and `FakeWebDataCleaner` must record no `clearCookiesAndSiteData()`.
- [X] T017 [US3] In `M/feature/privacy/domain/usecase/RunFireUseCase.kt` line 56, use `val keepSites = fireproofSites.observeAll().first().mapTo(HashSet()) { it.site }`. In `M/feature/tabs/data/repository/IncognitoSnapshotOrigins.kt` line 25, read the same way, which makes its function `suspend` if it is not already, and update its caller. Make T016 green.
- [X] T017a [US3] In `A/feature/privacy/data/FireDataTest.kt`, add a case: mark `example.com` fireproof and set a cookie for it and one for `other.example`; write an interrupted-Fire marker; build a fresh `FireproofSiteRepositoryImpl` whose cache has not loaded yet; run `FireCoordinator.resumeIfInterrupted()`. The `example.com` cookie is kept and the other one is gone. It fails before T017.
- [X] T018 [US3] Make the cookie engine replaceable, then test:
  - `AndroidWebDataCleaner` (`M/feature/settings/data/datasource/local/AndroidWebDataCleaner.kt`) calls `CookieManager.getInstance()` directly, and Robolectric's cookie manager always answers at once. Add a constructor parameter `private val cookieManager: Provider<CookieManager>` (`javax.inject.Provider`), bound by a `@Provides` returning `CookieManager.getInstance()` in `M/feature/settings/di/`. Replace every `CookieManager.getInstance()` in the class with `cookieManager.get()`;
  - in `T/feature/settings/data/datasource/local/AndroidWebDataCleanerTest.kt` (Robolectric; create if missing), add a failing test with a `CookieManager` subclass whose `removeAllCookies` never calls back: after the timeout (virtual time), the kept cookies were written back with `setCookie`.
- [X] T019 [US3] In `M/feature/settings/data/datasource/local/AndroidWebDataCleaner.kt` (lines 98-104), call `writeCookies(kept)` after the wait whether it timed out or not. The timeout still returns the failure outcome. Make T018 green.
- [X] T020 [P] [US3] In `RES/xml/backup_rules.xml`, add `<exclude domain="root" path="app_webview/"/>`. In `RES/xml/data_extraction_rules.xml`, add the same line to both `<cloud-backup>` and `<device-transfer>`. Update each file's header comment to say web data stays on the device too.
- [X] T021 [P] [US3] In `M/feature/privacy/data/datasource/local/CookieJarSnapshotManagerImpl.kt`, remove `origin=$origin` and `pair=$trimmed` from the `Log` calls at lines 48 and 80, keeping a fixed message and the throwable. Then run `grep -rn "Log\." M/` and read every hit in files that handle URLs, hosts or cookies, including multi-line calls and `+` concatenation. Fix any log that includes a URL, host, cookie name or value.
- [X] T022 [P] [US3] In `T/feature/privacy/data/datasource/local/CookieJarSnapshotManagerImplTest.kt` (create if missing), add a failing test: a remove callback that never fires must not block a later call beyond `BrowserLimits.WEB_DATA_CLEAR_TIMEOUT_MS`. Use virtual time.
- [X] T023 [US3] In `CookieJarSnapshotManagerImpl.kt`, wrap the awaits at lines 71 and 103-111 in `withTimeoutOrNull(BrowserLimits.WEB_DATA_CLEAR_TIMEOUT_MS)`. Make T022 green.
- [X] T024 [P] [US3] In `T/feature/browser/presentation/browser/PageViewModelTest.kt`, add failing tests:
  - (a) after `setActiveTab(incognitoTab)`, `isIncognito` is true in the same emission as the tab;
  - (b) a screenshot result that arrives after a switch to another tab is saved under the captured tab id;
  - (c) a screenshot or icon captured from an incognito tab is never saved.
- [X] T025 [US3] In `M/feature/browser/data/repository/CurrentPageRepositoryImpl.kt`, `setActiveTab` sets `isIncognito = tab?.isIncognito ?: false` in the same `update`. Delete `setIncognito` there, in `M/feature/browser/domain/repository/CurrentPageRepository.kt`, and in its fake. In `M/BR/PageViewModel.kt`, remove the `ObserveActiveTabIsIncognitoUseCase` collection (lines 93-102). The use case itself stays, because `MainActivity` and `DefaultBrowserInviteViewModel` still use it.
- [X] T026 [US3] Make the PixelCopy result and `onReceivedIcon` carry the tab id and incognito flag captured when the WebView is built (`key(activeTab.id)` in `M/BR/BrowserWebView.kt` and `M/BR/BrowserWebViewCallbacks.kt`). `PageViewModel` saves under that id and drops the result when it is incognito. Make T024 green.
- [X] T027 [P] [US3] In `T/feature/tabs/data/repository/TabSessionRepositoryImplTest.kt`, add failing tests: (a) `save` for an incognito id after `retainOnly` dropped it leaves no entry; (b) `retainOnly(emptyList())` deletes nothing (FR-033, kept here because it is the same file).
- [X] T028 [US3] In `M/feature/tabs/data/repository/TabSessionRepositoryImpl.kt`:
  - add a `closedIncognito` set: `retainOnly` adds the negative ids it drops, and `save` ignores them;
  - clear the set when the incognito session ends, which is when no incognito ids remain;
  - make `retainOnly` return early when the positive id list is empty.

  Make T027 green.
- [X] T029 [US3] Run G1. Commit `fix(privacy): fireproof, backup, logs and incognito flag (031 US3)`.

## Phase 5: User Story 4 – Files are never deleted or broken by mistake (P1)

**Goal**: delete removes only its own file; downloads open, carry sign-ins and keep their names; captures and caches never break. **Independent test**: the US4 scenarios in the spec, plus the unit tests below.

- [X] T030 [P] [US4] In `T/core/domain/usecase/ResolveDownloadStatusUseCaseTest.kt`, add failing tests:
  - a forgotten record with `localUri == null` whose name exists on disk gives `Missing`;
  - the same record with `localUri != null` and an existing file gives `Complete`.
- [X] T031 [US4] In `M/core/domain/usecase/ResolveDownloadStatusUseCase.kt` lines 56-61, `resolveForgotten` returns `Missing` when `record.localUri == null`, and otherwise probes as today. Make T030 green.
- [X] T032 [P] [US4] In `T/feature/downloads/data/datasource/local/AndroidExternalFileOpenerTest.kt` (Robolectric; create if missing), add a failing test: a record named `a.pdf` with type `application/octet-stream`, and one with an empty type, both send `ACTION_VIEW` with type `application/pdf`. An unknown extension sends `*/*`.
- [X] T033 [US4] In `M/feature/downloads/data/datasource/local/AndroidExternalFileOpener.kt` lines 20-29, take the type from `MimeTypeMap.getSingleton().getMimeTypeFromExtension(...)` when the stored type is blank or `application/octet-stream`, falling back to `*/*`. Make T032 green.
- [X] T034 [P] [US4] In `T/feature/browser/domain/usecase/SanitizeDownloadFileNameUseCaseTest.kt`, add failing tests:
  - `filename*=UTF-8''C++Primer.pdf` gives `C++Primer.pdf`;
  - 150 CJK characters plus `.pdf` give a name of ≤ 255 UTF-8 bytes ending in `.pdf`;
  - a cut that would fall inside a surrogate pair (emoji) keeps the pair whole;
  - an extension longer than 255 bytes on its own gives a base name of ≤ 255 bytes with no extension;
  - a base name of 4-byte characters that cannot all fit keeps at least one whole character before the extension.
- [X] T035 [US4] In `M/feature/browser/domain/usecase/SanitizeDownloadFileNameUseCase.kt` lines 62-75:
  - decode with `URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8)`;
  - replace the character cap with a byte cap of `BrowserLimits.MAX_FILE_NAME_BYTES = 255`, added in `M/core/domain/util/BrowserLimits.kt`: drop whole code points from the end of the base name until the full name fits, and drop the extension only if the extension alone exceeds the cap.

  Make T034 green.
- [X] T036 [P] [US4] In `T/feature/browser/domain/usecase/StartDownloadUseCaseTest.kt`, add failing tests:
  - `blob:https://x/1` and `data:text/plain,hi` return `StartDownloadResult.Unsupported` and never call the gateway;
  - `cookies` and `referer` arrive unchanged in the `DownloadOrder` the fake gateway receives.
- [X] T037 [US4] Change the download types:
  - `M/core/domain/model/DownloadOrder.kt`: add `val cookies: String?`, with KDoc "Never logged or stored";
  - `M/feature/browser/domain/usecase/StartDownloadUseCase.kt`: add a `cookies: String? = null` parameter, return `Unsupported` for any scheme other than `http(s)` before sanitising, and pass `cookies` into the order;
  - add `data object Unsupported` to `StartDownloadResult`, and to `DownloadSnackbarEvent` in `M/BR/DownloadSnackbarEvent.kt`;
  - map it in `M/BR/DownloadRequestViewModel.kt`, and show `downloads_unsupported_snackbar` in `DownloadRequestEventEffect` in `M/BR/BrowserEffects.kt`.

  Make T036 green.
- [X] T038 [US4] Pass the cookies and referer from the WebView:
  - in `M/BR/BrowserWebView.kt` `setDownloadListener` (line 199), read the cookies from the tab's own profile cookie manager:
    - an incognito tab on an engine with `WebViewFeature.MULTI_PROFILE` uses its profile's `cookieManager` (`M/BR/WebViewProfiles.kt`, `M/core/data/util/IncognitoProfiles.kt`);
    - an incognito tab without profiles uses `CookieManager.getInstance()`, which at that moment holds the incognito jar swapped in by `CookieJarSnapshotManager`;
    - a normal tab uses `CookieManager.getInstance()`;
  - put this choice in one function, `cookieManagerFor(isIncognito)`, next to the profile lookup, and cover both incognito branches with a Robolectric test in `T/BR/WebViewProfilesTest.kt` (`WebViewFeature` stubbed);
  - pass the cookies and `wv.url` as the referer through `callbacks.onDownloadRequested` to the view model;
  - in `M/feature/downloads/data/datasource/local/AndroidDownloadManagerGateway.kt` lines 25-33, call `addRequestHeader("Cookie", it)` and `addRequestHeader("Referer", it)` when the values are non-null.
- [X] T039 [P] [US4] Write `T/BR/page/CaptureFilesTest.kt` (Robolectric) first, failing: two calls to `newPhoto` leave both files on disk, and `clear` removes both.
- [X] T040 [US4] In `M/BR/page/CaptureFiles.kt` lines 21-22, remove the `clear(context)` call from `newPhoto`. Make T039 green. Confirm that the session dispose in `M/BR/page/BrowserPageRequests.kt` (line 31) still calls `clear`.
- [X] T041 [P] [US4] Write failing tests in `T/core/data/datasource/local/cache/DiskScreenshotCacheTest.kt` (new) and `DiskFaviconCacheTest.kt`: a save whose write throws partway (a bitmap compress stub that throws, or a read-only directory) leaves no final file and no `.tmp` file.
- [X] T042 [US4] Create `M/core/data/datasource/local/cache/AtomicFile.kt` with `internal inline fun File.writeAtomically(block: (OutputStream) -> Unit)`. It writes to `File(parentFile, "$name.tmp")` and then calls `renameTo(this)`, deleting the temp file on any throwable before rethrowing. Use it in `DiskFaviconCache.kt` (lines 39-46) and `DiskScreenshotCache.kt` (lines 33-39). Make T041 green.
- [X] T043 [US4] Run G1. Commit `fix(downloads): safe delete, open type, cookies, names, captures and caches (031 US4)`.

## Phase 6: User Story 5 – Errors are shown correctly; a bad settings file does not lock the person out (P1)

**Goal**: the error screen stays; site error pages show; embedded SSL errors do not fail the page; a corrupt DataStore resets. **Independent test**: quickstart Q4, Q4b, Q5 and Q8.

- [ ] T044 [P] [US5] In `T/feature/browser/presentation/browser/PageViewModelTest.kt`, add failing tests:
  - `LoadStarted`, `LoadFailed(DnsFailure)`, `Progress(100)`, `LoadFinished` ends in `Failed(DnsFailure)` and writes no history row;
  - a following `LoadStarted` gives `Loading`.
- [ ] T045 [US5] In `M/BR/PageViewModel.kt`:
  - `onProgressChanged` (lines 135-156): when `clamped >= MAX_PROGRESS` and the state is `Failed`, keep it, but still reset `_loadProgress`;
  - `onLoadFinished` (lines 158-167): after consuming `pendingHistoryUrl`, return early if the state is `Failed`.

  Make T044 green.
- [ ] T046 [US5] In `M/BR/BrowserWebView.kt`, `onReceivedHttpError` (lines 477-486) no longer calls `onLoadFailed`. Delete the override if nothing else remains in it. Delete `ErrorReason.HttpError` in `M/BR/ErrorReason.kt` and its case in `T/BR/ErrorReasonTest.kt`. Add a one-line comment that states the reason itself (a site's own error page is shown, as other browsers do), with no reference to an ADR, spec or FR (Constitution §VI).
- [ ] T047a [P] [US5] Write `T/BR/MainFrameSslErrorTest.kt` first, failing: `isMainFrameSslError(errorUrl = "https://cdn.bad/x.png", mainFrameUrl = "https://good.example/")` is false; equal URLs are true; a `null` main-frame URL (an error before `onPageStarted`) is true.
- [ ] T047 [US5] Add `internal fun isMainFrameSslError(errorUrl: String?, mainFrameUrl: String?): Boolean` in `M/BR/MainFrameSslError.kt`; it is true when `mainFrameUrl == null || errorUrl == mainFrameUrl`. Then, in `BrowserWebViewClient` (`M/BR/BrowserWebView.kt`):
  - store `mainFrameUrl` in `onPageStarted`;
  - in `onReceivedSslError`, keep `handler?.cancel()` first and the HTTPS-upgrade fallback second, then call `onLoadFailed(SslError)` only when `isMainFrameSslError(error?.url, mainFrameUrl)`. Make T047a green.
- [ ] T048 [US5] Change `A/BR/BrowserScreenOfflineErrorTest.kt` to also drive a real failing load: a host under `.invalid`, or the asset server returning a connection reset. Assert that the error screen is still displayed after `waitForIdle` plus 2 s. Keep the existing seeded case.
- [ ] T049 [P] [US5] Write the Robolectric test `T/core/di/SettingsDataStoreCorruptionTest.kt` first: write garbage bytes to the DataStore file, build the store the way the module does, and check that `data.first()` returns empty preferences without throwing.
- [ ] T050 [US5] In `M/core/di/SettingsDataStoreProviderModule.kt` (lines 32-35), pass `corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }`. Make T049 green.
- [ ] T051 [US5] Run G1. Commit `fix(browser): sticky error screen, main-frame SSL only, DataStore reset (031 US5)`.

## Phase 7: User Story 2 – The address bar never runs script (P1)

**Goal**: only web schemes load, and sentences with a dot are searched. **Independent test**: the US2 scenarios.

- [ ] T052 [P] [US2] In `T/core/domain/model/AddressBarInputClassifierTest.kt`, add failing cases:
  - `javascript://%0Aalert(1)`, `foo://bar` and `how to use node.js` give `Query`;
  - `example.com`, `https://x.y`, `http://a.b/c d` and `about:blank` keep their current result;
  - `kotlin.coroutines` stays a `Url`, the accepted trade-off.
- [ ] T053 [US2] In `M/core/domain/util/UrlConstants.kt`, add `ADDRESS_BAR_SCHEMES = setOf("http", "https", "about")`. In `M/core/domain/model/AddressBarInputClassifier.kt`:
  - input containing `://` is a `Url` only if its scheme, lower-cased, is in that set;
  - input with no scheme becomes a URL only if it matches the dot heuristic and has no whitespace;
  - everything else is a `Query`.

  Make T052 green.
- [ ] T054 [US2] Run G1. Commit `fix(browser): address bar loads only web schemes (031 US2)`.

## Phase 8: User Story 6 – History, bookmarks and search work in every mode and at any size (P2)

**Goal**: opening works from incognito, search stays fast, import recovers. **Independent test**: quickstart Q9 and Q10.

- [ ] T055 [P] [US6] In `T/feature/history/presentation/history/HistoryViewModelTest.kt` and `T/feature/bookmarks/presentation/bookmarks/BookmarksViewModelTest.kt`, add failing tests: with active tab id `-3`, a tap calls `updateActiveTabUrlAndTitle(tabId = -3, isIncognito = true)` and `FakeHistoryRepository` records no new row, and with no active tab (`0L`) it calls nothing.
- [ ] T056 [US6] In `M/feature/history/presentation/history/HistoryViewModel.kt` (line 121) and `M/feature/bookmarks/presentation/bookmarks/BookmarksViewModel.kt` (line 165), use the guard `activeTabId != 0L` with `isIncognito = activeTabId < 0L`. Remove the `ObserveActiveTabIsIncognitoUseCase` dependency and `lastKnownIsIncognito` from both, and from their test setup. Make T055 green.
- [ ] T057 [P] [US6] In `T/feature/bookmarks/domain/usecase/SearchBookmarksUseCaseTest.kt`, add failing tests:
  - folder paths are built without any `getAncestorChain` call (the fake counts calls; expect 0);
  - a folder cycle A→B→A ends without looping;
  - at most `BOOKMARK_SEARCH_MAX_RESULTS` results come back.

  In `T/core/data/datasource/local/BookmarkDaoTest.kt`, add a test that `searchByTitleOrUrl(query, limit = 2)` returns the 2 newest.
- [ ] T057a [P] [US6] Add the scale check:
  - generate `app/src/androidTest/assets/bookmarks-10k.html` once with `NetscapeBookmarkWriter`: 10,000 `https://` links in 200 folders nested up to 5 deep, written by a small test-only helper `A/feature/bookmarks/fixture/LargeBookmarkFile.kt`;
  - add `A/feature/bookmarks/data/BookmarkSearchScaleTest.kt`: import the file into an in-memory database, then time `SearchBookmarksUseCase("ht").first()` with `measureTimeMillis` over 5 runs after one warm-up. Assert the median is < 300 ms (SC-006) and record it in Gate Results;
  - the test fails on the unfixed code (thousands of ancestor reads).
- [ ] T058 [US6] Make the search fast:
  - add `BOOKMARK_SEARCH_MAX_RESULTS: Int = 50` to `M/core/domain/util/BrowserLimits.kt`;
  - `M/core/data/datasource/local/BookmarkDao.kt` (lines 85-93): add `LIMIT :limit` and a `limit: Int` parameter, then thread it through `BookmarkRepository.searchBookmarks` and `BookmarkRepositoryImpl`;
  - `M/feature/bookmarks/domain/usecase/SearchBookmarksUseCase.kt`: `combine(repository.searchBookmarks(query, LIMIT), repository.observeAllFolders())`, then build each path from an `associateBy { it.id }` map. Walk parents with a `visited` set, root first, and remove the old comment;
  - `BookmarksViewModel.kt` (lines 89-100): skip queries with `trim().length < BrowserLimits.SEARCH_MIN_CHARS`.

  Make T057 green.
- [ ] T059 [P] [US6] In `T/feature/bookmarks/data/datasource/local/NetscapeBookmarkParserTest.kt`, add failing tests:
  - `<DT><H3>Work<DL><p><DT><A HREF="https://a.example">a</A></DL>` imports folder "Work" with link "a";
  - `<DT><A HREF="https://1.example">t1<DT><A HREF="https://2.example">t2</A>` imports both links, titled "t1" and "t2";
  - `HREF="https:// x"` is skipped and counted as skipped.
- [ ] T060 [US6] In `M/feature/bookmarks/data/datasource/local/NetscapeBookmarkParser.kt`:
  - lines 157-163: end the text at the smallest index among `</tag`, `<dt` and `<dl`, case-insensitive;
  - lines 127-129: accept a link only when `BookmarkUrlValidator.validate(url)` (`M/core/domain/util/BookmarkUrlValidator.kt`) succeeds, store its normalized value, and count it as skipped otherwise.

  Make T059 green.
- [ ] T061 [US6] Run G1. Commit `fix(bookmarks): incognito open, fast search, tolerant import (031 US6)`.

## Phase 9: User Story 7 – Limits hold under pressure (P3)

**Goal**: the tab cap, many downloads, a double tap and the pop-up release all hold. **Independent test**: the US7 scenarios.

- [ ] T062 [P] [US7] In `T/feature/tabs/data/repository/TabRepositoryImplTest.kt` (in-memory Room), add a failing test: with 49 tabs, two `async { createTab() }` calls on a multi-thread dispatcher give exactly 50 tabs, one limit refusal and distinct positions.
- [ ] T063 [US7] In `M/feature/tabs/data/repository/TabRepositoryImpl.kt` (lines 55-69), wrap count, `maxPosition` and insert in `seedMutex.withLock { … }`. Make T062 green.
- [ ] T064 [P] [US7] In `T/feature/downloads/data/datasource/local/AndroidDownloadManagerGatewayTest.kt` (Robolectric; create if missing), add a failing test: `queryStatuses` with 1,200 handles issues at least 3 queries of ≤ 500 ids, and returns a map that covers every known handle.
- [ ] T065 [US7] Create `M/feature/downloads/domain/model/DownloadLimits.kt` with `object DownloadLimits { const val STATUS_QUERY_CHUNK: Int = 500 }`. In `AndroidDownloadManagerGateway.kt` (lines 37-49, 88-89), run the query per `chunked(STATUS_QUERY_CHUNK)` and merge the maps. Make T064 green.
- [ ] T066 [US7] In `M/navigation/AppNavHost.kt` (lines 84-88), add `launchSingleTop = true` to the onboarding-exit `navigate` options.
- [ ] T067 [US7] In `M/BR/page/PopupWebViewRegistry.kt`, add `fun forgetTab(tabId: Long)`, which destroys and removes any WebView waiting for that tab, and `fun clear()`. Call `forgetTab` where a tab is closed in the browser screen, and `clear` in the browser screen's dispose, next to `PageRequestQueue.dispose()`. Add a unit test in `T/BR/page/PopupWebViewRegistryTest.kt` (Robolectric): a waiting WebView is destroyed and removed by both calls.
- [ ] T068 [US7] Run G1. Commit `fix(tabs): tab cap, download chunks, onboarding, pop-up release (031 US7)`.

## Phase 10: User Story 8 – Less code to keep correct (P3)

**Goal**: remove what nothing uses and merge the copies; nothing the person sees changes. **Independent test**: G1 green, and each name below has 0 grep hits in `app/src`.

Before each deletion, run `grep -rn "<name>" app/src` again. Delete a test only when it tests nothing but the removed member.

- [ ] T069 [P] [US8] Delete `TabSessionRepository.delete` (`M/core/domain/repository/TabSessionRepository.kt`), its implementation, `TabSessionDao.delete(tabId)`, the fake's override and their tests.
- [ ] T070 [P] [US8] Delete `HistoryDao.getRecent`, `getById` and `delete(entry)` (`M/core/data/datasource/local/HistoryDao.kt`) and their DAO tests.
- [ ] T071 [P] [US8] Delete `TabRepository.getTabCount` and `IncognitoTabRepository.getCount`, with their implementations, fakes and tests. Rewrite any androidTest that used `getTabCount` to count from `observeAllTabs().first()`.
- [ ] T072 [P] [US8] Delete `DownloadManagerGateway.isAvailable` (`M/core/domain/repository/DownloadManagerGateway.kt`) and, in `AndroidDownloadManagerGateway.kt` (lines 82-102), `isDownloadProviderDisabled`, `DISABLED_STATES` and `DOWNLOAD_PROVIDER_PACKAGE`. Remove the override from all 5 fakes.
- [ ] T073 [P] [US8] Delete `DownloadsRepository.count`, `DownloadsRepositoryImpl.count` (lines 35-36), `DownloadRecordDao.count` and `BookmarkRepository.getFolder` (`BookmarkRepositoryImpl.kt` lines 101-102), with their fakes and tests.
- [ ] T074 [US8] Replace the forwarding use cases with direct repository calls (Constitution III):
  - delete `RemoveSitePermissionsUseCase`, `RemoveProtectionExceptionsUseCase` and `RemoveFireproofSitesUseCase`;
  - in `FireproofSitesViewModel.kt`, `ProtectionExceptionsViewModel.kt` and `SitePermissionsViewModel.kt`, call `if (x == null) repository.clearAll() else repository.remove(x)` directly;
  - move their test cases into the view-model tests.
- [ ] T075 [US8] Create `M/core/data/util/RunIoCatching.kt` with `internal suspend fun <T> runIoCatching(dispatcher: CoroutineDispatcher, block: suspend () -> T): Result<T>`. Make it behave exactly like the existing private `runIo`, including that `CancellationException` is rethrown. Replace the three private copies in `SitePermissionRepositoryImpl.kt` (line 51), `FireproofSiteRepositoryImpl.kt` (line 57) and `ProtectionExceptionRepositoryImpl.kt` (line 58).
- [ ] T076 [US8] Create `M/core/domain/model/FireproofSites.kt` with `object FireproofSites { fun originsFor(site: String): List<String> = listOf(site, "www.$site") }`. Use it in `RunFireUseCase.kt` (lines 143-160, removing `WWW_PREFIX`), `IncognitoSnapshotOrigins.kt` (line 25, removing `WWW`) and `AndroidWebDataCleaner.kt` (line 89). Add `T/core/domain/model/FireproofSitesTest.kt`.
- [ ] T077 [P] [US8] In `BookmarkTransferViewModel.kt` (lines 69-77), remove the "nothing to export" pre-check and rely on `ExportBookmarksUseCase`'s result. Keep the view-model test for the empty case and point it at the use case's outcome.
- [ ] T078 [P] [US8] In `M/ThirtySixApplication.kt` (lines 34-36), inject `@ApplicationScope CoroutineScope` instead of building `CoroutineScope(SupervisorJob() + io)`.
- [ ] T079 [P] [US8] In `M/core/domain/usecase/CloseTabUseCase.kt` (line 16), delete the comment about a tab re-created with the same id, because ids are AUTOINCREMENT. If the guard under it exists only for that case, delete the guard too and keep its test passing.
- [ ] T080 [US8] Run G1. For SC-009, run `git diff --numstat HEAD~1 -- app/src/main` on the US8 commit: deleted minus added lines must be ≥ 150. Record it in Gate Results. Commit `refactor: remove unused and duplicated code (031 US8)`.

## Phase 11: Polish and cross-cutting

- [ ] T081 Run the full gates from [quickstart.md](quickstart.md):
  - `detekt ktlintCheck lintDebug testDebugUnitTest`;
  - `assembleRelease` with the 16 KB script;
  - `connectedDebugAndroidTest` on `TA016_API36_16K` and `TA016_API29_CI`;
  - `assembleRelease -PcomposeReports`, with no new non-skippable composable left unexplained.
- [ ] T082 Run the device checks Q1–Q10 from the quickstart on `TA016_API36_16K`, plus Q6 on `TA016_API29_CI`. Never use the A55. If Q1b fails, implement the redirect fallback from research R1 (a last-tap window) with a test, then rerun Q1b.
- [ ] T083 Measure cold start in interleaved rounds against `main` on the AVD (within 5 %). Check that the release APK grew ≤ 10 KB.
- [ ] T084 Run `python3 scripts/comment-check.py` on every changed `.kt` file. Grep the diff for `FR-`, `T0` and `Spec 031` in source, and expect none.
- [ ] T085 Fill in "Gate Results" below. Write the PR description with:
  - one line per audit item and its FR;
  - the quickstart Q results;
  - the SC-009 line count.

  End it with the attribution line. Update `AGENTS.md` only if a command or rule changed; none is expected.

## Dependencies

- **Phase 2**: T002 blocks the US1 and US5 commits. T003 blocks T011, T012 and T037.
- **Stories**: they are independent of each other, apart from these shared files:
  - US3 (T024–T026) and US5 (T044–T045) both edit `PageViewModel.kt` and `PageViewModelTest.kt`, so do them in order: US3 first.
  - US1 (T010), US3 (T026), US4 (T038) and US5 (T046–T047) all edit `BrowserWebView.kt`, in that order.
  - US3 T017 and US8 T076 both touch `RunFireUseCase.kt` and `IncognitoSnapshotOrigins.kt`, and US8 comes after.
  - US6 T056 must come before US8, because US8 grep-checks callers.
  - US8 runs last.
- **Within a story**: the test task comes before its fix task. Tasks marked [P] touch different files.
- **Added by `/speckit-analyze`**: T017a (after T017), T047a (before T047) and T057a (with T057). Suffixed IDs keep the original numbering stable.

## Parallel examples

- **US1**: T004 and T006 can run together, both tests in different files. Then T005 and T007. T011 and T012 can also run together.
- **US3**: T016, T020, T021, T022 and T027 can all start at once. T018 changes `AndroidWebDataCleaner`'s constructor, so it runs alone.
- **US4**: T030, T032, T034, T036, T039 and T041 can all start at once.
- **US8**: T069–T073 and T077–T079 can run together. T074–T076 run one at a time.

## Implementation strategy

- **MVP**: Phases 1 and 2, then US1 (Phase 3). That closes the only issue that is a security hole on every page. It can merge on its own.
- **Order**: then US3 → US4 → US5, the data loss and broken error UI. Then US2 → US6 → US7, and US8 last.
- **Review**: each phase is one commit. A reviewer can stop after any phase with a green build.

## Gate Results

| Gate | Baseline (T001) | Final (T081–T083) |
|---|---|---|
| Unit tests | 1081, 0 failures (`52a6080`) | |
| Instrumented tests | 218 `@Test` methods | |
| Release APK size | 3,840,007 B | |
| `main` Kotlin lines | 31,443 | |
| Cold start vs `main` | n/a | |
| Quickstart Q1–Q10 | n/a | |
