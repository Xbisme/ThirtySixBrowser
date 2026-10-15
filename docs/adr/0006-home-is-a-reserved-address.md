# 0006. Home is a reserved address that never reaches the WebView

- Status: accepted
- Date: 2026-10-14

## Context

New tabs open on a native Home page. Loading a placeholder URL into the WebView would record it in
history, send it to Reload and the cookie snapshot, and show a blank page while it loads.

## Decision

- Home is the address `thirtysix:home`, provided as `@Named("default_home_url")`.
- `loadUrl`, the initial load, Reload, history recording and cookie snapshots skip it; the browser
  screen draws Home instead of a WebView.
- Home shown over a loaded page is an in-memory overlay, so Back returns to the page.
- The home address is a singleton Hilt binding (`UrlConfigModule`), not a constant, so instrumented
  tests can replace it with `@UninstallModules` and the singleton tab repository seeds an empty tab
  list with the same address.

## Consequences

- Code that loads or records addresses must check for the reserved address.
