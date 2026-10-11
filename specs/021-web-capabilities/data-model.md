# Data Model: Web Capabilities (Spec 021)

Room goes from **v2 to v3** (one new table). Everything else is in memory.

## 1. `SitePermissionEntity` → domain `SitePermissionDecision` (Room, table `site_permissions`)

| Column | Type | Rule |
|---|---|---|
| `origin` | TEXT, PK part | `scheme://host[:port]`, lower-cased scheme and host, default port omitted; https only (http is never stored, FR-007) |
| `capability` | TEXT, PK part | `camera` \| `microphone` \| `location` |
| `decision` | TEXT | `allow` \| `deny` — "Allow this time" is never stored (FR-003) |
| `decided_at` | INTEGER | epoch millis of the last change |

Primary key `(origin, capability)` → one decision per site per capability; writing again replaces.
Migration `MIGRATION_2_3`: `CREATE TABLE site_permissions (…)` only; schema `3.json` exported.

Domain:

```kotlin
enum class SiteCapability { Camera, Microphone, Location }
enum class SiteDecision { Allow, Deny }
data class SitePermissionDecision(val origin: String, val capability: SiteCapability,
                                  val decision: SiteDecision, val decidedAt: Long)
```

## 2. `OneTimeGrantStore` (presentation singleton, in memory)

Key `(tabId, pageGeneration, origin, capability)`. Added by "Allow this time" (normal and
incognito tabs); read by `ResolveSitePermissionUseCase`; entries for a tab are dropped when its
page generation changes or it closes; everything is gone when the process ends; also cleared by
Clear browsing data when Site permissions is ticked (FR-010a).

## 3. `PageRequest` (presentation, in memory) — one per pending interaction

Common fields: `tabId`, `pageGeneration`, `origin` (shown as the site's address).

| Variant | Holds | Answered by |
|---|---|---|
| `Permission(capabilities)` | `PermissionRequest` or geolocation `Callback` | `grant`/`deny`, `invoke(origin, allow, retain = false)` |
| `FileChooser(params)` | `ValueCallback<Array<Uri>>` | `onReceiveValue(uris or null)` |
| `JsDialog(kind, message, defaultText, showSuppress)` | `JsResult` / `JsPromptResult` | `confirm()` / `confirm(text)` / `cancel()` |
| `LeavePage` | `JsResult` | `confirm()` (leave) / `cancel()` (stay) |
| `HttpAuth(host, realm)` | `HttpAuthHandler` | `proceed(user, pass)` / `cancel()` |
| `PopupBlocked(url?)` | — | notice; Open → background-free new active tab |
| `LongPressMenu(menu)` | — | the chosen action |

`PageRequestQueue`: FIFO; the host shows only the head (FR-035). A request whose `tabId` /
`pageGeneration` no longer matches the active tab's is answered refused/cancelled and dropped
(FR-034). Two `Permission` requests from the same origin for the same capability while one is
showing are merged.

## 4. `PageGeneration` (per tab, in memory)

Integer advanced on each new main-frame navigation and on tab switch. Used by §2, §3 and §5.

## 5. `DialogSuppression` (per tab, in memory)

`(pageGeneration, dialogCount, suppressed)`; reset when the generation changes (FR-031).

## 6. `PopupWebViewRegistry` (presentation singleton, in memory)

`tabId → (WebView, openerTabId)` for tabs created by a user-gesture `onCreateWindow` (research
R5). `BrowserWebView` adopts the registered WebView when composing that tab; the entry is
removed when the WebView is released (tab switch/close). `onCloseWindow` on such a WebView closes
its tab and activates `openerTabId` if it exists.

## 7. `LongPressMenu` (domain, pure)

```kotlin
data class LongPressMenu(val header: String, val actions: List<LongPressAction>)
enum class LongPressAction { OpenInNewTab, OpenInIncognitoTab, CopyLink, ShareLink, DownloadLink,
                             OpenImageInNewTab, DownloadImage, CopyImageAddress, ShareImageLink }
```

Built by `BuildLongPressMenuUseCase(type, linkUrl, imageUrl)`; header = link URL when present,
else image URL; link group before image group; non-http(s) addresses → only the matching Copy.

## 8. `ClearBrowsingDataCategory.SitePermissions` (domain enum, Spec 016)

New value; **not** in the default selection (FR-010a).

## Invariants

- **INV-1**: No `allow`/`deny` row is ever written from an incognito tab (FR-004).
- **INV-2**: No `site_permissions` row has an `http://` origin (FR-007).
- **INV-3**: Every platform callback in a `PageRequest` is answered exactly once — by the host,
  by staleness (FR-034), or on `BrowserScreen` disposal.
- **INV-4**: An Android runtime permission is requested only inside the handling of a
  `Permission` request (or capture in a `FileChooser` request) after the person chose an allow
  option (FR-002, SC-003).
- **INV-5**: HTTP-auth credentials exist only in the dialog's state and the single `proceed`
  call; never in Room, DataStore, `WebViewDatabase` or logs (FR-032).
- **INV-6**: Geolocation is always answered with `retain = false` — WebView's own permission
  store is never used.
- **INV-7**: A user-gesture window gets exactly one new tab; a non-gesture window gets none
  unless the person taps Open (FR-025, FR-026, FR-026a).
- **INV-8**: Only tabs in `PopupWebViewRegistry` can be closed by their page (FR-027).
