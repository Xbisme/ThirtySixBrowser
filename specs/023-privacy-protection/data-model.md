# Data Model: Privacy Protection (Spec 023)

## Persisted (Room v4)

### `site_protection_exceptions` — `SiteProtectionExceptionEntity`

| Column | Type | Notes |
|---|---|---|
| `site` | TEXT, PRIMARY KEY | Registrable domain, lower-case ASCII (IDN as punycode), e.g. `shop.example`, `example.co.uk`, `alice.github.io`. For an IP or single-label host, the host itself. |
| `added_at` | INTEGER NOT NULL | Epoch millis; newest first in Settings. |

- Created by `MIGRATION_3_4` (additive `CREATE TABLE`, no existing table touched). `4.json` is exported and committed. There is no destructive fallback.
- Domain model: `SiteProtectionException(site: String, addedAt: Instant)`.

## In memory

| Holder | Scope | Content | Cleared |
|---|---|---|---|
| `ProtectionDataSource` (`@Singleton`) | process | `ProtectionData` = `TrackerHostSet` (≈16k hosts) + `PublicSuffixRules` + `TrackingParamRules`; immutable once loaded | never (process death) |
| `ProtectionExceptionSnapshot` (`@Singleton`) | process | `StateFlow<Set<String>>` mirror of the Room table | follows Room |
| `IncognitoProtectionExceptionStore` (`@Singleton`) | incognito session | `Set<String>` of sites switched off in incognito | incognito 1 → 0 transition; Clear browsing data with the category ticked |
| `HttpsUpgradeFailureStore` (`@Singleton`) | process | `Set<String>` of hosts whose HTTPS attempt failed | process death only |
| `PageProtectionMonitor` (one per WebView) | page | top URL + its site, `protectionOn`, blocked-host set, pending upgrade `(host, originalHttpUrl, httpsUrl)?`, `StateFlow<PageProtectionStatus>` | reset in `onMainFrameRequest` (IO thread) for every new page; `PageSession.advance()` covers restores; disposed with the WebView |
| Registrable-domain cache (inside `PublicSuffixMatcher`) | process | host → site, bounded at `BrowserLimits.REGISTRABLE_DOMAIN_CACHE_SIZE` | eviction |

### `PageProtectionStatus`

```
data class PageProtectionStatus(
    val blockedTrackerCount: Int,   // distinct hosts, FR-010
    val protectionOn: Boolean,      // false when the top site is excepted (normal or incognito store)
    val connectionSecure: Boolean,  // false when the top URL scheme is http (FR-022a)
    val isWebPage: Boolean,         // false on Home / incognito start page → hub hides the section (FR-011)
)
```

## Rule data (assets, read-only)

| Asset | Format | Licence / header |
|---|---|---|
| `protection/tracker_hosts.txt` | one host per line; `#` comments | CC BY-SA 3.0 header naming EasyList/EasyPrivacy authors, generation date, "modified: host-level subset" |
| `protection/public_suffix_list.dat` | upstream PSL format, unchanged | MPL-2.0 header kept intact |
| `protection/tracking_params.txt` | one name per line; `name*` = prefix; `#` comments | own work, no licence obligation |
| `licenses/CC-BY-SA-3.0.txt`, `licenses/MPL-2.0.txt` | plain text | — |

## Decision rules (pure functions, `domain/protection/`)

1. **`registrableDomain(host)`**: lower-case. IP literal or single label → host. Otherwise apply PSL (exception `!` beats wildcard `*.` beats normal; longest match wins; unknown TLD → implicit `*` rule) and take the suffix plus one label. If the host *is* a public suffix → host.
2. **`isThirdParty(requestHost, topHost)`** = `registrableDomain(requestHost) != registrableDomain(topHost)`.
3. **`TrackerMatcher.shouldBlock(requestHost, topHost, protectionOn)`** = `protectionOn` ∧ third party ∧ no suffix of `requestHost` in the never-block set ∧ some suffix of `requestHost` (the host itself or a parent, stopping at its registrable domain) in `TrackerHostSet`.
4. **`TrackingParamStripper.strip(url)`**: parse the raw query by `&`. Drop a pair whose name (before `=`, percent-decoded for comparison only) equals a listed name, or starts with a listed prefix. Keep the other raw pairs in order. Rebuild; omit `?` when empty; keep `#fragment` verbatim. Only `http`/`https` URLs are touched.
5. **`HttpsUpgradePolicy.upgrade(url, failedHosts)`** → the https URL, or `null` when: the scheme is not `http`; the host is `localhost`, an IP literal or single-label; or the host is in `failedHosts`. Port, path, query and fragment are preserved; an explicit `:80` is kept as written.
6. **`NavigationPreparer.prepare(url, isIncognito)`**: if the destination site is excepted (normal snapshot, or the incognito store when `isIncognito`) → `url` unchanged. Otherwise `upgrade(strip(url)) ?: strip(url)`.

## State transitions

**HTTPS upgrade (per WebView)**

```
Idle --prepare() upgraded--> Pending(host, http, https)
Pending --main-frame error / SSL error on https / redirect to http same host--> record host failed
        --> (error/SSL) loadUrl(http) without onLoadFailed  |  (redirect) allow redirect
        --> Idle
Pending --page commits (onPageStarted for https URL)--> Idle
```

**Per-site switch**

```
on  --hub switch off (normal)---> Room upsert(site) → snapshot updates → page reload
on  --hub switch off (incognito)--> incognito store add(site) → page reload
off --hub switch on / Settings remove / Clear data category--> delete → reload (hub only)
incognito store --last incognito tab closed--> cleared
```

## Naming

The UI says **Sites without protection**; code says **protection exceptions** (`SiteProtectionException`, `ProtectionExceptionRepository`, `ProtectionExceptionsScreen`). Both mean the same records.

Presentation code learns whether a site is protected only through `IsSiteProtectedUseCase(site, isIncognito)` (§IV); it never reads `ProtectionExceptionSnapshot` or `IncognitoProtectionExceptionStore` directly.

## Invariants

1. No protection code path opens a network connection (SC-012).
2. The intercept path never touches Room, DataStore or the main thread.
3. A blocked request never produces error UI; only main-frame errors reach `onLoadFailed`.
4. An HTTPS fallback never reports `onLoadFailed` for the failed secure attempt.
5. `prepare(prepare(u)) == prepare(u)` (idempotent), so `shouldOverrideUrlLoading` never re-issues the same URL.
6. Incognito never reads the Room exceptions (FR-014); the incognito store is never persisted.
7. The blocked count counts distinct hosts and resets on every new page, not on same-document changes.
8. GPC is sent whether or not protection is on for the site (FR-016).
9. Main-frame navigations are never blocked (FR-005); downloads are not intercepted (FR-006).
10. The asset files are read only through `ProtectionDataSource`; the app never writes them.
