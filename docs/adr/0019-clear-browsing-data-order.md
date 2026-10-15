# 0019. Clearing browsing data runs in a fixed order with bounded waits

- Status: accepted
- Date: 2026-10-14

## Context

Settings lets the person clear selected categories of browsing data. Web-engine clears are
asynchronous, differ by engine version, and interact with the incognito cookie snapshot that older
engines hold while an incognito session is open (see 0007).

## Decision

- `ClearBrowsingDataUseCase` runs the selected categories in this order: history; cookies and site
  data; cached images and files; site permissions with in-memory one-time grants; sites without
  protection with incognito exceptions; fireproof sites. Tabs, bookmarks, downloads and settings are
  never touched.
- Before the cookie wipe, the set-aside incognito snapshot is discarded
  (`CookieJarSnapshotManager.discardSetAsideCookies`). In the reverse order, closing the last
  incognito tab during the clear restores the cookies just cleared.
- A discarded snapshot becomes `CookieJarSnapshot.EMPTY`, never null: restore returns early on null
  and would skip its end-of-session wipe, leaking incognito cookies. `EMPTY` keeps the wipe, drops the
  write-back, and keeps the next capture a no-op.
- `WebDataCleaner.clearCookiesAndSiteData` has two paths. With `DELETE_BROWSING_DATA` it calls
  `WebStorageCompat.deleteBrowsingData` and reports Complete (the web cache is emptied too). Otherwise
  it removes all cookies, flushes, calls `WebStorage.deleteAllData` and reports Partial (service
  workers and Cache Storage survive). Both count as cleared; a throw is Failed.
- The web cache step is skipped after a Complete site-data clear; favicon and screenshot caches are
  always cleared. The web cache is cleared through a throw-away WebView (`clearCache(true)` applies
  to all WebViews).
- Every wait on a web-engine callback is bounded (`BrowserLimits.WEB_DATA_CLEAR_TIMEOUT_MS`,
  `SITE_DATA_DELETE_TIMEOUT_MS`), so an engine that never answers cannot trap the undismissable clear
  dialog or hold Fire.

## Consequences

- Fire (0008) reuses the same cleaner; a new category needs a place in this order and tests on both
  engine paths.
