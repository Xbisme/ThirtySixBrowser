# Research: Privacy Tools (Spec 024)

Evidence markers: **[V]** verified here (javap on the cached `androidx.webkit` 1.17.0 AAR, the codebase, or the upstream package/source), **[D]** official documentation/javadoc, **[A]** assumption to be confirmed by the named gate. Date of lookup: 2026-10-13.

## R1 — Web-engine features this spec relies on

**Decision**: Use three `androidx.webkit` 1.17.0 features, each feature-detected with `WebViewFeature.isFeatureSupported`. **No new dependency.**

| Feature | API (javap [V]) | Used for | When absent |
|---|---|---|---|
| `DOCUMENT_START_SCRIPT` | `WebViewCompat.addDocumentStartJavaScript(WebView, String, Set<String>) → ScriptHandler` (`remove()`) | cookie-banner + fingerprinting scripts (FR-019–FR-028) | both protections off, silently (FR-022) |
| `GET_COOKIE_INFO` | `CookieManagerCompat.getCookieInfo(CookieManager, String url) → List<String>` | fireproof cookie snapshot **with attributes** (R2) | name=value snapshot (Spec 012 R1 limitation) |
| `DELETE_BROWSING_DATA` | `WebStorageCompat.deleteBrowsingData(...)`, **`deleteBrowsingDataForSite(WebStorage, String, Executor?, Runnable) → String`** | complete clear (no fireproof site) and per-site clear (R3) | `Fallback` path (R3) |

**Engines in play**: Galaxy A55 WebView **153.0.8010.36** [V, `dumpsys webviewupdate`]; `TA016_API36_16K` WebView 133; CI API 29 WebView 74; `TA016_API24` WebView 53. Which features each engine reports is **[A] → G1 spike**, recorded here before implementation continues past the data layer. Expectation: all three on 153 and 133 (Spec 016 already took the `DELETE_BROWSING_DATA` path on 133); none on 53; `DOCUMENT_START_SCRIPT` absent on 74 → CI script tests use `assumeTrue`.

**G1 spike results (T021, 2026-10-13)** — `storage.html` on `localhost`, logged by a throw-away instrumented test:

| Engine | `DOCUMENT_START_SCRIPT` | `GET_COOKIE_INFO` | `DELETE_BROWSING_DATA` | `getCookieInfo` | `getOrigins()` |
|---|---|---|---|---|---|
| Galaxy A55, WebView 153.0.8010.36 | ✅ | ✅ | ✅ | `fp_persist=1; domain=localhost; path=/; expires=…; httponly; samesite=lax` | lists the fixture origin |
| `TA016_API36_16K`, WebView 133.0.6943.137 | ✅ | ✅ | ✅ | same format | lists the fixture origin **and an origin from an earlier session** (storage persists) |
| `TA016_API24`, WebView 53.0.2785.124 | ❌ | ❌ | ❌ | — (`getCookie` returns only `fp_js=1`: the HttpOnly cookie is not reported) | lists the fixture origin |

Consequences:
- On engines with `GET_COOKIE_INFO`, attributes survive the round trip. **A host-only cookie is reported as `domain=<host>` without a leading dot**, so restoring it with a `domain` attribute would widen it to subdomains. The restore therefore drops `domain=` when the value has no leading dot.
- On WebView 53, an HttpOnly cookie of a fireproof site is invisible to the fallback snapshot and is lost on Fire (FR-010a).
- All five storage kinds were present on all three engines before clearing.

**Alternatives rejected**: a JS bridge to learn page state (forbidden, §II); reading WebView's private `Cookies` SQLite file to delete rows except fireproof hosts (what DuckDuckGo's Android app does) — rejected for the reason Spec 012 rejected it: the file format and location are engine-private and change between versions, the crash-resilience priority stands.

## R2 — Keeping fireproof cookies through Fire

**Decision**: snapshot → wipe → restore.
1. After incognito teardown (data-model INV-2), for every **query URL** of a fireproof site — `https://` and `http://` of the site itself, of `www.` + site, and of every candidate host (step 1) whose registrable domain is fireproof — read `getCookieInfo(url)` (or `getCookie(url)` without `GET_COOKIE_INFO`). Deduplicate by (name, domain, path).
2. Clear (R3).
3. Write each snapshot entry back with `CookieManager.setCookie(url, cookieInfoString)`, then `flush()`.

`getCookieInfo` returns full `Set-Cookie`-style strings including `Domain`, `Path`, `Expires/Max-Age`, `Secure`, `HttpOnly`, `SameSite` [D, javadoc]; feeding them back preserves those attributes **[A] → G4** (cookie still `HttpOnly` and persistent across restart).

**Limitations (documented, not failures)**:
- A cookie whose `Path` is narrower than `/`, set on a fireproof host that is not a candidate host, is not read by any query URL and is lost. Sign-in cookies are almost always `Path=/` on the site or `www.`.
- Without `GET_COOKIE_INFO`, restored cookies lose their attributes (Spec 012 R1): they become session cookies without `HttpOnly`. On those engines (expected: API 24) a fireproof sign-in may not survive an app restart. Recorded for G15.
- Cookies written during incognito to a fireproof site are discarded by the incognito teardown before the snapshot (INV-2) — the desired behaviour.

**Alternatives rejected**: per-cookie expire-overwrite of non-fireproof cookies (the jar cannot be enumerated, so unknown hosts would keep their cookies); a separate WebView `Profile` for fireproof sites (`MULTI_PROFILE` exists [V], but data cannot move between profiles, and existing sign-ins would be lost on the switch).

## R3 — Clearing site data of non-fireproof sites only

**Decision**: three paths, chosen at run time.
- **`Complete`** — no fireproof site: Spec 016's `clearCookiesAndSiteData()` unchanged (fast, total).
- **`PerSite`** — fireproof sites exist and `DELETE_BROWSING_DATA` is supported: for every candidate site (registrable domains of the candidate hosts, step 1) that is **not** fireproof, `deleteBrowsingDataForSite(storage, site, callback)`, awaited one at a time with a per-call timeout (`BrowserLimits.SITE_DATA_DELETE_TIMEOUT_MS`); then `removeAllCookies` + restore (R2), which removes cookies of sites not in the candidate set.
- **`Fallback`** — older engines: `WebStorage.deleteOrigin(origin)` for every non-fireproof origin from `WebStorage.getOrigins()`; then cookies as above.

**Candidate hosts** (data-model step 1): distinct history hosts (new `HistoryRepository.distinctHosts()`), hosts of open normal **and** incognito tabs, remembered site-permission origins, and `WebStorage.getOrigins()`. This is the only enumeration the public API allows.

**Coverage, honestly stated**:
- Cookies: complete for non-fireproof sites (wipe-all + restore).
- Storage, service workers, Cache Storage on `PerSite`: complete for every site that appears in the candidate set. A site that set storage but is in none of the sources (its history pruned by retention, and no quota-managed storage) keeps that storage. `getOrigins()` is expected to report every quota-managed origin (IndexedDB, Cache Storage, service-worker caches) **[A] → G1**; whether localStorage is included is recorded in G1. If it is not, the gap is a documented limitation like Spec 016 A17.
- `Fallback` path: localStorage and service workers may survive (Spec 016 A17).
- Spec 016's SC on Clear browsing data is unaffected; it never uses these paths.

**Alternatives rejected**: delete everything then ask the person to sign in again (defeats fireproofing); keep a shadow list of every host ever seen (a new store of browsing traces, contrary to Fire's purpose).

## R4 — Web page cache

**Decision**: whole cache via Spec 016's `clearWebCache()`, skipped after `Complete` (A16). Per-site deletion on `PerSite` also removes that site's cache entries **[D]**, but fireproof sites' cache is not kept by design (spec Assumption), so the whole-cache call runs on `PerSite` and `Fallback` too.

## R5 — Installing the scripts (Constitution §II)

**Decision**: per WebView, when `DOCUMENT_START_SCRIPT` is supported, one `addDocumentStartJavaScript(webView, text, setOf("*"))` call. `"*"` matches every frame origin [D]; the androidx javadoc states the script runs "in any frame whose origin matches allowedOriginRules" [V, sources javadoc via the R7 agent].

**Text** = `PrivacyScriptComposer.compose(secret, exceptedSites, bundles)`:
- a prelude declaring one frozen object literal `{v:1, s:"<hex secret>", x:["site", …]}`, built only from local inputs and JSON-escaped; `x` entries are validated hostnames (letters, digits, `-`, `.`), anything else is dropped;
- then the bundled assets, each wrapped so it reads the object from a closure argument — **no global is created** (`window` gains nothing a page can enumerate).

**Top-site rule (FR-020, clarified)**: inside the script, `top = location.ancestorOrigins?.length ? hostOf(ancestorOrigins[last]) : location.hostname` (`ancestorOrigins` exists in Chromium since long before WebView 53 [A]). If `top` equals or ends with `"." + x[i]` for any excepted site, the script returns immediately, in every frame of that page. Subdomain matching on the registrable domain matches Spec 023's exception rule without shipping the PSL into the page.

**§II conditions, one by one**:
1. bundled in the APK; the prelude is generated from **local** data only (session secret, local exception list) — no remote data ever reaches it;
2. installed with `addDocumentStartJavaScript`, feature-detected; absent → off (FR-022);
3. implements protections defined in this spec (Spec 024);
4. exposes no native API; the app registers no listener and reads nothing from the page (INV-9);
5. origin rules documented here: `"*"`, narrowed in-script by the top-site rule above.

**Alternatives rejected**: removing the `ScriptHandler` when the top page is an excepted site (races the frame's document start); `allowedOriginRules` listing every non-excepted site (impossible — the rule is an allow-list of frame origins, not of top sites); `evaluateJavascript` after load (too late for fingerprinting, and not the §II mechanism).

## R6 — Script lifecycle

**Decision**: `PrivacyScriptInstaller`, one per WebView, created in `BrowserWebView`'s factory next to Spec 023's `PageProtectionMonitor`. It collects `combine(session.secret, exceptions(incognito))` and, on each change, `handler?.remove()` then installs the new text; the new text applies from the next document. The per-site switch already reloads the page (Spec 023 FR-012), so the dashboard/hub toggle takes effect immediately. A change from Settings applies on the next load of each page. Pop-up WebViews (Spec 021) get an installer the moment they are created, like their `WebViewClient`.

The installed text is held only as long as the handler; assets are read once per process off the main thread, behind a domain interface `PrivacyScriptBundles` (implemented in `data/local/privacy/`, as Spec 023's `ProtectionDataRepository`), beside Spec 023's protection data load (R10 of 023).

## R7 — Cookie-banner engine: DuckDuckGo autoconsent

**Decision**: bundle **autoconsent v16.51.0** (published 2026-10-13 [V, GitHub releases API]), **MPL-2.0** [V], rebuilt from the npm package source with our own entry file.

Facts [V, from `npm pack @duckduckgo/autoconsent@16.51.0` and its `lib/` sources]:
- Constructor `new AutoConsent(sendContentMessage, config?, declarativeRules?)`; passing `config` skips the messaging "init" round-trip. Upstream's `standalone/content.ts` already runs it with a local no-op message sink.
- `isMainWorld: true` makes "eval" snippets compiled-in functions called directly; the dist contains **no** `eval` / `new Function`.
- `autoAction: 'optOut'` runs only the rules' opt-out steps; opt-in steps run only with `'optIn'`.
- **Trap**: `heuristicMode: 'tier2'` (upstream standalone default) clicks **Accept** when a banner has no reject/settings button. FR-024 forbids that.
- Covered consent tools include OneTrust, Cookiebot, Sourcepoint (frame), TrustArc, Didomi, Quantcast, Usercentrics, Google Funding Choices, Complianz, CookieYes, Iubenda, Osano, Termly, Axeptio, Borlabs, consentmanager, Klaro, Ketch — every tool named in the spec's input. No public worldwide ranking of consent tools exists (only a 2025 US crawl and a 2022 top-1,000 study), so FR-025 names the 10 tools directly; they are the fixtures of quickstart G11.

**Our entry** (`tools/privacy-scripts/entry.ts`, own code):
- config `{ autoAction: 'optOut', isMainWorld: true, heuristicMode: 'off', enableHeuristicDetection: false, enableGeneratedRules: false, enablePrehide: false, enableCosmeticRules: false, logs: all false }`;
- `sendContentMessage` = no-op; no `window.autoconsent*` globals (upstream standalone sets two — not reproduced);
- exits early under the top-site rule (R5).

`enableCosmeticRules: false` and `enablePrehide: false` because hiding a banner records no choice — spec US4 #3 says an unhandled banner is left alone.

**Trim** — rules limited to the **CMP-generic range** (`genericRuleRange` [0, 281] of 950 compact rules), dropping the 463 generated `auto_*` rules and site-specific rules. Rationale: the script is parsed in **every frame of every page** before the page continues ([D] javadoc: it "will block the loading of the page until it's finished"), so its size is a page-load cost (SC-010), not only an APK cost. Measured sizes [V, esbuild 0.28.2 `--minify`, gzip -9]: engine 96 KB raw / 29 KB gz; generic rules estimated ≈ 50–60 KB raw **[A] → measured by the generator** → **≈ 150 KB raw, ≈ 45 KB gz** total. If G11 misses SC-007 (≥ 12/20), the site-specific rules are added back (≈ 290–330 KB raw, ≈ 80 KB gz — still within +200 KB APK) and G14 re-measured.

**Build**: a hand-run generator like Spec 023's list script — `tools/privacy-scripts/build.sh` with a pinned `package.json`/lockfile (`@duckduckgo/autoconsent` 16.51.0, `esbuild` pinned), writes `app/src/main/assets/privacy/consent.js` + `consent.LICENSE` header. Not a Gradle task; no npm at app build time; the output is committed and reviewed.

**MPL-2.0 obligations**: keep the licence notice in the built file, show it in Settings → About with the upstream source link (our Spec 023 MPL-2.0 text asset is reused), and keep our modifications — none to MPL files; our entry and rule filter are our own files — documented in `tools/privacy-scripts/README.md`.

**Alternatives rejected**: Consent-O-Matic (MIT, `Rules.json` 377 KB; engine is WebExtension-bound and would need a port; last release 2025-06); hand-written rules for ~10 tools (smaller, but re-implements what a maintained engine does, and misses frame-based tools like Sourcepoint).

## R8 — "Cookie banner declined on this page" in the dashboard

**Decision**: **dropped**. With no data coming back from the page (§II-4, FR-021) the app cannot know. Reading consent cookies from the jar was considered and rejected: it is a heuristic per tool, and it would report a refusal the person made by hand. Spec FR-016 already allows dropping the line; the spec is amended accordingly (Clarifications, research amendment).

## R9 — Fingerprinting shield (basic)

**Decision**: own small script `assets/privacy/fingerprint.js` (≈ 4 KB), no third-party code.
- **Seed** = 32-bit FNV-1a of `secret + "|" + top` (R5's top host); a xorshift PRNG from it. Stable per top site per secret; the secret changes per process and on Fire (FR-003a).
- **Canvas**: wrap `HTMLCanvasElement.prototype.toDataURL`, `toBlob`, `CanvasRenderingContext2D.prototype.getImageData` (and `OffscreenCanvas` equivalents where present): read-outs are computed on a **copy** whose pixels get ±1 on a seeded sparse subset of channels; the visible canvas is never modified (US5 #6).
- **Audio**: wrap `AudioBuffer.prototype.getChannelData` (copy-on-read with ±1e-7 seeded noise on a sparse subset) and `AnalyserNode.prototype.getFloatFrequencyData` / `getByteFrequencyData`.
- **Battery**: `navigator.getBattery` removed from `Navigator.prototype` (feature-detecting pages carry on; FR-027).
- **Media devices**: wrap `MediaDevices.prototype.enumerateDevices`; when every returned entry has an empty `label` (the engine's state before camera/mic permission), also blank `deviceId` and `groupId`; when labels are present (permission granted, Spec 021), pass through unchanged (FR-028). Decided entirely in the page, no app data.
- Wrapped functions keep their `name`/`length` and a native-looking `toString`; a determined page can still detect tampering (spec edge case).
- WebGL, fonts, screen, `hardwareConcurrency`: out of scope (owner: basic level).

## R10 — Fire: coordination, interruption, UI

**Decision**:
- `FireCoordinator` (`@Singleton`, app-scope coroutine) owns `StateFlow<FireState>` and a `Mutex.tryLock` gate (INV-5). Fire continues if the screen that started it goes away.
- **Marker** `StorageKeys.FIRE_IN_PROGRESS` in DataStore, written and awaited before step 1 (INV-1). On start-up, `MainActivity` already awaits `currentSettings()` before `setContent` (Spec 018); when the marker is `true`, it also awaits `resumeIfInterrupted()`. Only an interrupted run pays this cost (SC-011 unaffected otherwise).
- **WebViews**: emptying the tab store makes `BrowserScreen` dispose every tab's WebView through the existing close paths (Spec 011 close-all, Spec 012 9-step destroy for incognito). Fire from the Tabs screen pops back to the browser after confirmation.
- **Overlay**: a full-screen `FireOverlay` composable (scrim + fire glyph that grows and fades, `BrowserLimits.FIRE_OVERLAY_MS`) shown while `Running`, dismissed when both the run and the minimum animation time are done, then `acknowledge()`. Compose animations follow the system animator scale, so with animations off the overlay appears and disappears without motion (FR-006). Values are read in `graphicsLayer` (§V).
- **Entry points**: a `QuickTile` in the hub's tile grid and an icon button in the Tabs top bar; both open the shared `ConfirmDialog` (destructive style). The glyph is Material Symbols `local_fire_department` as a VectorDrawable via `AppIcons` (Apache-2.0, as every other icon since Spec 019).
- **Downloads** keep running: Fire never touches `DownloadManager` (FR-004).

**Alternatives rejected**: WorkManager for the run (new dependency, and a run must finish before the UI anyway); restarting the process after Fire (DuckDuckGo's approach — visible restart, cold-start cost, loses the in-process WebView state that we clear anyway).

## R11 — Dashboard

**Decision**: `PrivacyDashboardSheet` (Spec 019 `ModalBottomSheet` styling), opened by making Spec 023's `HubProtectionSection` count line clickable. State from a small `PrivacyDashboardViewModel` (like `ProtectionViewModel`), combining the active tab's `PageProtectionMonitor.status`, its new `blockedHostsFlow` and `FireproofSiteRepository.observeAll()`. Grouping by registrable domain via `BlockedHostGrouper` + Spec 023's `PublicSuffixMatcher` (clarified: by site, not company — Spec 023 R2: no licence-compatible owner data). The host list is a `LazyColumn` with `key`/`contentType` (§V rule 6). The protection switch reuses Spec 023's `SetSiteProtectionUseCase` + reload (INV-7).

`blockedHostsFlow` is a conflated `StateFlow<List<String>>` updated from the IO thread only when a **new** host is added to the set (distinct hosts, so at most one emission per tracker host), and reset with the count.

## R12 — Fireproof storage, Settings, Clear browsing data

**Decision**: mirror Spec 023's exception slice exactly: Room **v4 → v5** additive `MIGRATION_4_5`, `FireproofSiteEntity`/`Dao`/`Mapper`, `FireproofSiteRepositoryImpl` with a `@Singleton` snapshot for synchronous reads, Settings → Privacy → **Fireproof sites** screen cloned from `protectionexceptions/` plus a remove-all `ConfirmDialog`, and the opt-in `ClearBrowsingDataCategory.FireproofSites`. ⚠️ As Spec 023 noted, a v5 install cannot be downgraded to a v4 build.

## R13 — Favicons

**Decision**: `FaviconCache.retainOnly(hosts)`: compute `sha1(host)` for every host to keep, delete every other file in `cacheDir/favicons/`. Keep set per data-model step 8. Bookmark hosts come from `BookmarkRepository` (new `allBookmarkUrls()` or existing observe-all `.first()`); history hosts are gone by then, which is the point.

## R14 — Size and attribution

| Item | Raw | APK (deflated) [A → G14] |
|---|---|---|
| `privacy/consent.js` (engine + generic rules) | ≈ 150 KB | ≈ 45 KB |
| `privacy/fingerprint.js` | ≈ 4 KB | ≈ 2 KB |
| Kotlin + resources (≈ 25 files, ≈ 30 keys × 8) | — | ≈ 60–80 KB |
| **Total** | | **≈ 110–130 KB ≤ 200 KB (SC-012)** |

About gains "Cookie-banner rules: autoconsent by DuckDuckGo, MPL-2.0" with the source link, in 8 languages (FR-023).

## R15 — Testing

- **Unit (JVM)**: `RunFireUseCase` with fakes (order, failure boundaries, path selection, idempotent resume after a throw at each step); fireproof filtering helpers; `PrivacyScriptComposer`; `BlockedHostGrouper`; `FireproofSiteRepositoryImpl`; `MIGRATION_4_5` (real `Room.databaseBuilder` path, as Spec 015/023); `ClearBrowsingDataUseCase` new branch; `FireCoordinator` single-run gate.
- **Instrumented** (16K AVD; script tests `assumeTrue(DOCUMENT_START_SCRIPT)`): Fire end to end on `storage.html` with and without fireproofing; dashboard; Settings list; consent fixtures per covered tool; fingerprint fixture incl. cross-origin frame; interrupted-Fire resume via a debug-only hook.
- **JS**: the composed script is exercised only in WebView (no Node test runner added — YAGNI); the generator's output is diffed in review.
- Device gates G1–G16 in [quickstart.md](quickstart.md).
