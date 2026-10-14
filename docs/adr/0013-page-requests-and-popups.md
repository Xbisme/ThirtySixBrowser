# 0013. Page requests are settled once, per tab and page

- Status: accepted
- Date: 2026-10-14

## Context

Pages ask for files, permissions, dialogs, HTTP sign-in and new windows while the person switches
tabs and navigates. An answer delivered to the wrong page, or twice, is a privacy and stability bug.

## Decision

- One `PageRequestQueue` per browser screen. Every request is tied to tab and page generation and
  settled exactly once; `PageSession` advances the generation on every navigation and tab switch and
  cancels stale requests.
- Pop-up WebViews are registered before their tab exists, under a unique placeholder address, and get
  a `WebViewClient` the moment they are created: a page-created WebView without a client hands its
  navigation to another app.
- Windows the page opens on its own are blocked with an Open notice; blocked pop-ups are caught by a
  throw-away WebView that shares the opener's profile.
- `mediaPlaybackRequiresUserGesture` is false: with the default, a camera stream never played.
- Site permission prompts name the site first; "Allow this time" is never remembered, and incognito
  never remembers.
- Only the head request is shown, so at most one page-request dialog is visible. Identical permission
  requests (same tab, page, site and capabilities) are merged and get the same answer. Disposing the
  browser screen answers everything still pending.
- The blocked-pop-up catcher has JavaScript off, is never shown, and is destroyed after reporting the
  first real address.

## Consequences

- `PageRequestQueue`'s invariants are covered by unit tests; pop-up flows by `PopupInstrumentedTest`,
  which needs animations off on the emulator.
