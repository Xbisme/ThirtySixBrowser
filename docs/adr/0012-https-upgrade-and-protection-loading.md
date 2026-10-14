# 0012. Every app load is protected, and HTTPS falls back from the error page

- Status: accepted
- Date: 2026-10-14

## Context

Tracker blocking, HTTPS upgrade, tracking-parameter stripping and Global Privacy Control are on by
default. Two fallback bugs were found on devices: the browser issues two loads in a row when a tab
opens, and the cancelled one still reports `onPageFinished`; and a fallback `loadUrl` started from
`onReceivedError` could be replaced by the error page's own commit.

## Decision

- Every app-issued load goes through `WebView.loadProtected` (strip, upgrade, GPC headers where they
  are not app-wide). Page-initiated main-frame GETs are rewritten in `shouldOverrideUrlLoading`;
  redirects never are.
- A pending upgrade ends only after its page has started. The fallback to `http://` is loaded from
  the error page's `onPageFinished`, which is not recorded as a visit.
- The per-page blocked count resets in `shouldInterceptRequest` for the main frame, before any
  resource of the new page; `onPageStarted` covers redirects and history restores.
- GPC is app-wide only where the engine has `MULTI_PROFILE` and `CUSTOM_REQUEST_HEADERS`.
- Protection data is three APK assets under `assets/protection/` (EasyPrivacy/EasyList subset,
  CC BY-SA 3.0; Public Suffix List, MPL-2.0; own tracking-parameter list), regenerated only by hand
  with `tools/protection-lists/`, parsed once off the main thread at start-up.
- Rewritten page-initiated loads are re-issued without a referrer. An HTTPS attempt redirected back to
  `http://` is let through and its host remembered as failed.
- App-wide GPC is installed once per WebView profile (`GpcHeaderInstaller`), trying the `*` origin
  rule first and `http://*` plus `https://*` if the engine rejects it.
- `usesCleartextTraffic` is true, because a browser must open `http://` sites; mixed content stays
  blocked.

## Consequences

- A new code path that loads an address must use `loadProtected`.
