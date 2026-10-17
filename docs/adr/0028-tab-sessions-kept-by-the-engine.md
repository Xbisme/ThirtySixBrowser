# 0028. Tab sessions kept by the engine

- Status: accepted
- Date: 2026-10-16

## Context

A tab remembered only its last address. The app has one WebView, keyed by the active tab. So switching tabs, reopening the app, or the system ending the app all left the tab with no back or forward. The owner reported it: YouTube, then Facebook, close the app, reopen it, and Back did nothing.

Forward was only in the hub. On Android, the system edge gesture means Back on both edges.

DuckDuckGo's Android browser solves the first problem by storing each tab's `WebView.saveState` bundle. It keeps them in memory, or in a Room table behind a remote flag. The Android guide on WebView state recommends `WebViewCompat.saveState(webView, bundle, maxSizeBytes, includeForwardState)`. That call drops the oldest entries until the state fits, without touching the live list.

## Decision

- **What is kept.** For each tab, the engine's own navigation state, captured with `WebViewCompat.saveState` (256 KiB, forward entries kept). An older WebView falls back to `WebView.saveState`, and a result over the bound is not kept. The app never reads inside the state.
- **How it is encoded.** `TabStateCodec` writes the bundle as a versioned list of `(key, bytes)` pairs. `Parcel.marshall()` is documented as unfit for persistent storage. Chromium's state is a single byte array, so nothing is lost. Any other value type, an unknown version, or malformed bytes means "nothing saved".
- **Where it is kept.**
  - Normal tabs: the `tab_sessions` table (schema v6, additive migration), with a foreign key to `tabs` and `ON DELETE CASCADE`. The database is already excluded from backup.
  - Incognito tabs (negative ids): memory only, inside the same repository. They never reach the DAO.
- **When it is saved and restored.**
  - Saves, batched by 1 s: after a finished load, and after a history change (this covers single-page sites).
  - Immediate saves: before the tab's WebView is released, and when the screen stops.
  - Restore: when the tab's WebView is built. The WebView waits until the tab's state has been read. If nothing usable is there, it loads the tab's address as before.
  - A renderer loss restores from the newest save. A renderer crash still waits for Reload.
- **Restores and the protection rule.** A restore goes through `restoreState`, not `loadProtected`. The rule "every address the app loads goes through `loadProtected`" is about addresses the app chooses. A restore chooses none: the engine reloads its own entry. The `WebViewClient` and the page protections are attached before `restoreState`, so request interception and the per-page hooks apply as for any navigation. The tab switch's load is skipped once.
- **Privacy.**
  - `ClearBrowsingHistoryUseCase` is used by Fire, Settings and History. It clears history rows and every saved session. The open page also drops its back and forward.
  - Sessions of closed tabs are swept. A sweep never runs before a normal tab exists.
  - The history retention period does not trim a tab's saved history: it lives as long as the tab.
- **Swipe navigation.**
  - **The view**: `BrowserPageView`, a `WebView` subclass, reports touches and `onOverScrolled(clampedX)`.
  - **When it starts**: three conditions must hold. The drag is mostly sideways, the page reported overscroll since touch-down, and the page cannot scroll further that way.
  - **Feedback**: an arrow is drawn in `graphicsLayer`. Releasing past the threshold goes back or forward, mirrored for right-to-left layouts.
  - **When it is off**: on Home, on the incognito start page, while the address bar or the find bar is in use, and when its Settings switch (on by default) is off.
  - **What is unchanged**: the system Back gesture.

## Consequences

- Back and forward survive tab switches, app restarts and process death, up to the bound. At most the page that was loading when the process died is missing.
- A system or WebView update that changes the engine's state format only costs the history: the tab loads its address as before.
- A tab's WebView is built one database read later than before, on a switch and at start.
- Chromium reports root overscroll only for scroll the page did not use, so a drag on a carousel or a map does not start a swipe.
- The tab preview is copied from the screen with `PixelCopy`. The older `view.draw()` capture left a page's inner scrollers unable to take a drag after the next Back, so those drags turned into swipes. That was what blocked inner scrollers in the spike. API 24 and 25 have no `PixelCopy` for a window and keep the placeholder preview.
- Pop-up WebViews are `BrowserPageView` too, so an adopted pop-up tab can be swiped like any other.
