# Contract: Download Request Headers

## Cookie

| Start of the download | Cookie |
|---|---|
| The page itself (WebView download listener) | The tab's own jar for the final URL, as in Spec 031 |
| Long-press "Download link" or "Download image" | None |

## Referer (`DownloadReferrer.of`)

Evaluated in order:

1. Incognito tab → none.
2. Page URL missing or not `http(s)` → none.
3. Page `https` and download `http` → none.
4. Same origin (scheme + host + port) → the page URL without its fragment.
5. Otherwise → the page origin (`scheme://host[:port]/`).

Long-press downloads use `DownloadReferrer.originOnly`: the same rules, but step 4 also gives the origin. Their URL is the link before any redirect, and the download service sends the same headers on every hop.

## Tap rule for hand-off (updates the Spec 031 contract)

A navigation to another app counts as tapped when one of these holds:

- `request.hasGesture()`;
- it is a main-frame redirect whose chain started with a main-frame request that had `hasGesture()`.

Finger lifts are not tracked.
