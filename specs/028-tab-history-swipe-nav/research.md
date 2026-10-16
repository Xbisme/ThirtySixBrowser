# Research: Tab History and Swipe Navigation

## R1. What a tab must keep, and where it comes from

**Decision**: Keep the web engine's own navigation state for each tab. It is the serialized back and forward list. We get it with `WebViewCompat.saveState(webView, bundle, maxSizeBytes = 256 KiB, includeForwardState = true)` and give it back with `WebView.restoreState(bundle)`.

When `WebViewFeature.SAVE_STATE` is missing (an old WebView), we use `WebView.saveState(bundle)`. If that result is over the bound, it is not stored, and the tab falls back to its last address (FR-005).

**Rationale**:
- The engine's state carries entries, the current index and per-entry page state. Only the engine can rebuild that.
- `WebViewCompat.saveState` drops the oldest entries until the result fits, without touching the live list (FR-003, SC-005). It is present in our `androidx.webkit` 1.17.0: `saveState(WebView, Bundle, int, boolean)` and `WebViewFeature.SAVE_STATE`.
- The Android guide on WebView state recommends exactly this API over wiping history ("Manage WebView state efficiently").

**Alternatives considered**:
- Our own list of URLs, replayed with `loadUrl`. Rejected: it cannot rebuild a back stack. Each replay is a new navigation, with no page state and extra network loads.
- `onSaveInstanceState` alone. Rejected for two reasons. The shared 1 MB budget risks `TransactionTooLargeException`. And a swipe from Recents loses it.

## R2. How the state is stored

**Decision**: Store the bytes in a new Room table `tab_sessions`, one row per normal tab, with a foreign key to `tabs.id` and `ON DELETE CASCADE`. The schema goes v5 → v6 with an additive migration. This is the same shape as DuckDuckGo's `RoomWebViewSessionStorage`: table `webview_sessions(tabId PK, sessionBundle BLOB, savedAt)`, foreign key to tabs, cascade.

The `Bundle` becomes bytes in the browser's presentation layer, which owns Android types. We do not use `Parcel.marshall()`, whose documentation forbids persistent storage. Instead, a small `TabStateCodec` encodes the bundle's entries as a versioned list of `(key, ByteArray)`:
- Chromium's state is a single `byte[]` entry.
- A bundle holding any other value type is not stored, and the tab falls back to its last address.
- Unknown codec versions and malformed data decode to `null`, which also falls back.

**Rationale**:
- Room is already the app's store. It is excluded from backup (ADR 0024, FR-010), and it gives deletion with the tab for free.
- A codec we own does not depend on the platform's internal Parcel format, which can change with an OS update. DuckDuckGo uses Parcel bytes and accepts that risk.

**Alternatives considered**:
- Files in `noBackupFilesDir`. Rejected: deletion would be ours to get right in every place a tab disappears.
- `Parcel.marshall()` as DuckDuckGo does. Rejected per the platform's warning.

**Spike result (T002, Galaxy A55, API 36, WebView 154)**:
- The bundle holds exactly one entry, `WEBVIEW_CHROMIUM_STATE`, a `byte[]` of 876 B for one page. The codec's "bytes only" rule fits.
- `WebViewCompat.saveState` took 2.3–2.6 ms, and 7.8 ms on the first call after start. That is under the 5 ms budget, so the 1 s debounce stays.
- API 24 and 29 are re-checked on the AVDs during T028.

## R3. When to save and when to restore

**Decision**:

| Moment | Action |
|---|---|
| Page finished, or the visited history changed (`onPageFinished`, `doUpdateVisitedHistory`; the latter covers single-page sites such as YouTube and Facebook) | save, debounced by 1 s |
| The tab's WebView is about to be released (tab switch, leaving the browser screen) | save at once, before `release` |
| The screen stops (`ON_STOP`) | save at once |
| The tab's WebView is created | restore first; if nothing usable, load the tab's address as today |
| Renderer recovery creates a new WebView | restore from the last save, then load as today if that fails |

**Fallback**: if the spike measures a save above 5 ms on the main thread, saves after loads are debounced by 3 s instead of 1 s. The release and stop saves stay immediate.

**Rationale**:
- Saving on every finished load keeps the loss on process death to at most the page that was loading.
- Saving at release covers tab switches, because the app has one WebView, keyed by the active tab.
- `ON_STOP` covers leaving the app.
- DuckDuckGo saves only in `onSaveInstanceState`. That works for them because each of their tabs keeps a live WebView. Ours does not, so we need the save at release.

**The cold start**:
1. The browser needs the active tab's saved state before its WebView makes its first load.
2. A new `TabSessionViewModel` reads it for the active tab id. It exposes `loadedTabId` in its UI state, and the bytes through a lookup property `savedState`. The bytes are kept out of composable parameters, since `ByteArray` is not stable.
3. `TabWebView` composes the WebView only once `loadedTabId` equals that tab id.
4. The read is one primary-key row. It overlaps the tab flow that the screen already waits for (SC-006 measures it).

## R4. Restore and the protections

**Decision**: A restored tab goes through `restoreState`, not `loadProtected`. Two rules keep this safe:
- The `WebViewClient` and the `ProtectionHooks` are attached before `restoreState`. Request interception, tracker blocking and the per-page hooks then apply to the reload of the restored entry exactly as for any other navigation.
- The tab-switch `loadUrl` that follows a restore is skipped once. This uses the same mechanism as an adopted pop-up's `skipNextLoad`, so the restored page is not loaded twice.

**Rationale**: The rule "every address the app loads goes through `loadProtected`" is about addresses the app chooses. A restore does not choose an address. The engine reloads its own entry, and the page-level protections are attached by then. ADR 0028 records this so the rule's intent stays clear.

## R5. Detecting a sideways swipe that the page did not use

**Decision**: Use the WebView's overscroll report. A small subclass, `BrowserPageView : WebView`, overrides `onOverScrolled(scrollX, scrollY, clampedX, clampedY)` and watches touches in `dispatchTouchEvent`.

Chromium reports overscroll only for scroll the page did not consume at its root. A carousel or map that takes the drag therefore produces none (FR-015, SC-008).

Once a drag is mostly horizontal and an overscroll arrives with `clampedX`, the gesture:
1. starts;
2. follows the finger to a threshold, with progress written to a `SwipeNavigationState`;
3. on finger up past the threshold, calls `goBack` or `goForward`;
4. otherwise cancels.

Pop-up WebViews use the same subclass.

**Rationale**: This mirrors Chrome's "unconsumed overscroll" navigation, the only signal that respects the page's own horizontal scrollers. `canScrollHorizontally` alone cannot see inner scrollers.

**Fallback if the spike (T002) shows no overscroll report for horizontal drags on some API level**:
- The gesture starts only when the touch begins within 24 dp inside the page's own edge, outside the system gesture insets.
- `canScrollHorizontally(direction)` is false.
- The drag is at least 3:1 horizontal.

This fallback is recorded in the spike result before the gesture tasks start.

**Alternatives considered**:
- Map the system Back gesture's right edge to Forward (`BackEventCompat.swipeEdge`). Rejected with the owner: it breaks Android's "both edges are Back" convention and does nothing with three-button navigation.
- A visible Forward button only. Rejected: the owner wants a gesture.

## R6. Indicator and direction

**Decision**: A circular arrow at the vertical centre of the page, on the side the drag comes from.
- **Position**: its offset and scale are read in `graphicsLayer {}` from the `SwipeNavigationState` progress. The state is per-frame, so no recomposition happens during a drag (constitution IV).
- **Look**: it fills to `MaterialTheme.colorScheme.primary` at the threshold. When there is no page in that direction, it shows a disabled tint and does nothing on release (FR-014).
- **Direction**: comes from `LocalLayoutDirection`. In LTR, dragging right is back; RTL mirrors it (FR-013).
- **Accessibility**: the indicator's semantics say "Back" or "Forward". A swipe is also a pointer action, so TalkBack users keep the hub's buttons (FR-017).

## R7. Clearing history empties live back and forward

**Decision**:
- Add a core use case `ClearBrowsingHistoryUseCase(history, tabSessions)`. It clears history rows and every saved tab session, normal and in memory. Fire, Settings' "Clear browsing data" and the History screen's "Clear all" call it instead of `HistoryRepository.clearAll()`.
- `TabSessionRepository` exposes a `clears` counter. `TabSessionViewModel` turns each increment into `TabSessionEvent.ClearLiveHistory`, which the route turns into `WebView.clearHistory()` (FR-009). The tab keeps its current page.

This is a real use case, not a forward: it makes two calls.

## R8. Incognito

**Decision**: Incognito sessions live only in memory, inside the same repository implementation, keyed by the negative tab id. They are never passed to the DAO.
- `TabSessionViewModel` sweeps sessions whose tab id is no longer in `ObserveAllTabsUseCase`. Closing an incognito tab, "Close all incognito" and Fire then drop them without another repository depending on this one (ADR 0022).
- Normal sessions are also removed by the cascade (FR-007, FR-008).

## R9. The swipe setting

**Decision**:
- **Settings plumbing**: a DataStore key `swipe_navigation_enabled` (default `true`), a `UserSettings.isSwipeNavigationEnabled` field, `SettingsRepository.setSwipeNavigationEnabled`, and a switch row in Settings with title and summary in all 8 locales.
- **Browser holder**: `PageTextSizeViewModel` becomes `PageSettingsViewModel`, with `PageSettingsUiState(textZoomPercent, swipeNavigationEnabled)`. It is the browser's one reader of settings, and it now exposes a `UiState` instead of a bare flow.
