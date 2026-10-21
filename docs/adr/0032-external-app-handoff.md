# 0032. Pages open other apps only after a tap

- Status: accepted
- Date: 2026-10-20

## Context

Any page, or any iframe in it, could navigate to `market:`, `tel:`, `intent:` or another non-web
address, and the browser handed it straight to `startActivity` without a tap, without
`CATEGORY_BROWSABLE`, and with `intent:` either ignored or able to name a component. In incognito
the other app silently learned the link. Separately, an HTTP 4xx/5xx response replaced the site's
own error page with the browser's error screen.

## Decision

- A pure `ExternalLinkPolicy` decides every navigation to a scheme the WebView does not load itself
  (`http`, `https`, `about`, `data` and `blob` never reach it):
  - a sub-frame is dropped silently;
  - the main frame without a tap (`hasGesture() == false`) opens nothing and shows a snackbar,
    "This page wants to open another app", with Open; Open re-decides the navigation as tapped;
  - the main frame after a tap launches in a normal tab, and in incognito first asks "Open in
    another app? The app will see this link." as a `PageRequest`, cancelled with its page;
  - `javascript:`, `file:` and `content:` are dropped in every case.
- The launched intent is always safe: `CATEGORY_BROWSABLE`, `component = null`, `selector = null`,
  `FLAG_ACTIVITY_NEW_TASK`. `intent:` addresses are parsed with `URI_INTENT_SCHEME` and made safe
  the same way.
- When no app takes an `intent:` address, its `browser_fallback_url` is loaded in the same tab
  through `loadProtected`, and only when it is `http` or `https`. Loading it is not a hand-off and
  needs no confirmation.
- An HTTP status error is not a load failure: the site's own error page is shown, as other browsers
  do. Only network, DNS, SSL and renderer failures show the browser's error screen.

## Consequences

- A page can no longer bounce the person into another app; a tap, or the snackbar's Open, is always
  needed. WebView drops the gesture on a server redirect, so a main-frame redirect counts as tapped
  only when the main-frame navigation that started its chain had a gesture. Finger lifts are not
  tracked: a scroll's end is not a tap. An address the app loads itself (typed, restored, a
  fallback) starts an untapped chain, so its redirect to an app is offered.
- An `intent:` address that names this app's package, or carries an `http(s)` address, is never
  handed off: its address loads in the same tab.
- No `queryIntentActivities` before launch, so no `<queries>` declarations: the catch on
  `ActivityNotFoundException` covers a missing app.
- `ExternalLinkPolicyTest` covers the decision table, `ExternalAppLauncherTest` the safe intent and
  the fallback, and `ExternalAppHandoffTest` the untapped attempts on a device.
