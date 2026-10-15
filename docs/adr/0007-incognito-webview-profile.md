# 0007. Each incognito session has its own WebView profile

- Status: accepted
- Date: 2026-10-14

## Context

Incognito first shared the cookie jar with normal browsing: it snapshotted the jar, wiped it when
the session ended and wrote back only the cookies of sites open in normal tabs. Ending a session
signed the person out of every other site, and storage written in incognito stayed in normal
browsing.

## Decision

- Where the engine has `MULTI_PROFILE`, each incognito session gets a profile of its own with a
  unique name, attached to every incognito WebView (pop-ups included, which take their opener's
  profile) before any other call on it.
- The engine keeps a profile "in use" for the rest of the process once a WebView used it, so
  `deleteProfile` would throw: at session end the profile is emptied (`removeAllCookies` and
  `deleteBrowsingData`), and the emptied profile is deleted by the next process's first WebView.
- Older engines keep the snapshot path, over open tabs, every history host and fireproof sites.
- The session state and profile store live in `IncognitoProfiles` (`core/data/util`) behind the
  `IncognitoWebProfile` contract; attaching a profile to a WebView is `WebViewProfiles` in the
  browser presentation.
- Incognito tabs never write favicons, screenshots or history.
- Without profiles, the snapshot is a per-origin `getCookie`/`setCookie` round-trip over
  `CookieManager`, not a copy of WebView's cookie file. Cookie attributes are lost on restore, failures
  degrade to "cookies wiped", and the snapshot is memory-only. It is captured before the first
  incognito tab is added and restored after the last is removed, so the tab neither biases the
  snapshot nor keeps its cookies.
- Incognito tabs live in memory with negative ids, so the merged tab stream never has colliding keys.
- While any incognito tab is active the window has `FLAG_SECURE` (`SecureWindowEffect`): blank recents
  thumbnail, no screenshots. The effect always clears the flag on dispose.

## Consequences

- `IncognitoIsolationTest` fails on the shared-jar design and passes now.
