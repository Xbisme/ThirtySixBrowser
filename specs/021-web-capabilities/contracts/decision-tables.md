# Contract C1 — Pure decision tables (every row is a unit test, §VI)

## C1.1 `ResolveSitePermissionUseCase(origin, capabilities, isIncognito, isSecure, tabId, pageGen)`

Result: `Grant` (all), `Deny` (all) or `Ask(capabilities)`.

| # | Situation | Result |
|---|---|---|
| 1 | `isSecure = false` (http) | `Deny`, no question (FR-007) |
| 2 | request contains a capability outside camera/microphone/location | `Deny` (FR-008) |
| 3 | normal tab, remembered `allow` for all | `Grant` |
| 4 | normal tab, remembered `deny` for any | `Deny` |
| 5 | normal tab, nothing remembered | `Ask(all)` |
| 6 | normal tab, camera `allow` remembered, microphone undecided | `Ask(microphone)` |
| 7 | normal tab, one-time grant for this tab+page | `Grant` |
| 8 | normal tab, one-time grant for this tab but an older page generation | `Ask` |
| 9 | incognito, remembered `allow` exists from a normal tab | `Ask` — remembered decisions are ignored (FR-004) |
| 10 | incognito, one-time grant for this tab+page | `Grant` |

## C1.2 Answering a dialog — `ApplySitePermissionChoiceUseCase(choice, …)`

| Choice | Normal tab | Incognito tab |
|---|---|---|
| Always allow | store `allow` for each capability, then request Android permission(s) if missing | not offered |
| Allow this time | one-time grant for tab+page, then request Android permission(s) if missing | same |
| Don't allow | store `deny` for each capability | nothing stored |
| Back / dismiss | nothing stored, page refused | same |

After an allow choice: Android permission granted → `grant`; denied → page refused (the stored
`allow` stays — the site is not to blame); permanently denied → explanation + Open settings.

## C1.3 Popup policy — `DecidePopupUseCase(isUserGesture, targetUrl, isOpenerIncognito, tabCounts)`

| Situation | Result |
|---|---|
| user gesture, under the cap | `OpenInNewTab(incognito = isOpenerIncognito)` — adopted WebView (research R5) |
| user gesture, at the cap | `TabLimit` (existing message) |
| no gesture, http(s) target | `Blocked(openUrl = target)` → notice with Open |
| no gesture, blank / `javascript:` / `data:` / unknown target | `Blocked(openUrl = null)` → notice without Open |

## C1.4 Long-press menu — `BuildLongPressMenuUseCase(type, linkUrl, imageUrl)`

| Hit | Header | Actions (in order) |
|---|---|---|
| link `https://a.org/x` | link | OpenInNewTab, OpenInIncognitoTab, CopyLink, ShareLink, DownloadLink |
| image `https://a.org/i.png` | image | OpenImageInNewTab, DownloadImage, CopyImageAddress, ShareImageLink |
| image inside link | link | link actions, then image actions |
| link `javascript:void(0)` | link | CopyLink only (FR-023) |
| image `data:image/png;base64,…` | image | CopyImageAddress only |
| anything else | — | no menu (text selection keeps working) |

## C1.5 Dialog suppression

| Dialogs this page | Suppressed? | Behaviour |
|---|---|---|
| 1st | no | shown, no checkbox |
| 2nd+ | no | shown with "Don't let this site show more dialogs" |
| any | yes | `cancel()` without showing, until the page changes |
| new page | reset | count 0, not suppressed |
