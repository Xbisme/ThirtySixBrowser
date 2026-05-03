# Research: Private / Incognito Mode

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)
**Date verified**: 2026-05-03

> All research items resolve specific implementation unknowns surfaced in [plan.md](plan.md). Each item ends with **Decision / Rationale / Alternatives considered**.

## R1 — Cookie snapshot/restore strategy via public `CookieManager` API

**Question**: Given the platform `CookieManager` is a global singleton with no per-WebView isolation primitive, how do we capture the current cookie jar before the first incognito tab opens and restore it when the last incognito tab closes (per FR-011a)?

**Findings**:

- `CookieManager` exposes only two public read operations: `getCookie(url): String?` (returns the `Cookie:` header that would be sent to `url`, i.e. the concatenated `name=value; name=value` of all cookies whose `Domain` + `Path` match) and `hasCookies(): Boolean`. There is **no public `getAllCookies()` API** — the canonical way to back up the full cookie store is to copy the underlying SQLite file (`/data/data/<pkg>/app_webview/Default/Cookies` on most Android versions).
- Direct file-copy is fragile: file path varies by Android version (added `Default/` profile dir at API 31+ for some OEMs), and the schema can change without notice. Crash-resilience is the user's #1 priority — file-copy is rejected.
- `getCookie(url)` returns ONLY name=value pairs — `Domain`, `Path`, `Expires`, `Secure`, `HttpOnly`, `SameSite` attributes are NOT preserved by the `getCookie → setCookie` round-trip. After restore, cookies revert to default attributes (session-scoped, `Path=/`, no explicit `Domain`, no `Secure`).
- `setCookie(url, "name=value")` is the public write API; takes one `name=value` pair at a time (no `Set-Cookie:` syntax with attributes).
- `removeAllCookies(callback)` (API 21+) is the bulk wipe primitive; minSdk 24 → safe.
- `flush()` forces pending cookies to disk; should be called between snapshot capture and any subsequent write to ensure the captured state is consistent.

**Decision**: **Per-origin snapshot/restore via public API.**

1. **Snapshot capture** (transition `0 → 1` incognito tabs): enumerate the distinct origins from currently-persisted normal tabs (`TabRepository.observeTabs().first()`). For each origin, call `CookieManager.getCookie(origin)` and store as `Map<String, String>` (origin → `name=value; name2=value2 …` header). Plus the default home URL origin. Held in memory inside `CookieJarSnapshotManager` (Singleton scope).
2. **Snapshot restore** (transition `1 → 0` incognito tabs): call `removeAllCookies` (callback-based, await on the suspend wrapper), then `flush()`, then for each `(origin, header)` entry, split the header on `; ` and call `setCookie(origin, eachPair)` once per pair, then `flush()` again.

**Rationale**:

- Public API only — no version-fragile file paths, no internal-schema dependency. Constitution §IX is satisfied implicitly (no new dependency, no `.so`).
- Crash-safe: every call wraps `CookieManager` exceptions; restoration failures degrade gracefully to "cookies wiped" (privacy-stronger fallback) rather than corrupting the cookie store.
- Bounded work: number of origins is ≤ `MAX_TABS = 50`, so capture is ≤ 200 ms even on slow devices. Memory is ≤ 50 entries × ~2 KB per cookie header = ~100 KB worst-case.

**Alternatives considered**:

- **Direct SQLite Cookies file copy** — rejected on crash-fragility grounds (path varies; schema can change; SELinux on newer Androids can deny direct read of the WebView profile).
- **Track new cookies via request interceptor** (Q3 option C) — rejected: brittle `Set-Cookie` parser, expanded attack surface (every response header touches our code), cannot intercept JS-set cookies from `document.cookie`.
- **Wipe ALL cookies on close-of-last-incognito** (Q3 option A) — rejected by user clarification (UX-hostile: silently logs user out of normal-tab sessions).
- **Per-WebView cookie store via `WebView.setProfile()`** — only API 33+; minSdk 24 ⇒ not viable.

**Documented limitation** (acknowledged trade-off):

- Cookie attributes (`Domain`, `Path`, `Expires`, `Secure`, `HttpOnly`, `SameSite`) are NOT preserved. After restore, all cookies revert to default attributes. For sites that rely on tight attribute scoping (e.g. cookies with `Domain=.example.com` for cross-subdomain sharing), session behaviour after restore may differ subtly. Mitigation: SC-003 verifies "no cookie *value* leaks across the boundary" rather than "exact attributes preserved." Documented in spec FR-011a.
- Cookies set by sub-resource origins (CDN domains, embedded iframes) that are NOT top-level in any normal tab's URL list are NOT in the snapshot. Acceptable because: (a) those cookies are typically tracking-related and not user-session-critical, and (b) the alternative (enumerating every visited subresource) requires the request interceptor that R1 already rejected.

## R2 — `FLAG_SECURE` lifecycle binding pattern (FR-016 + FR-016a)

**Question**: How do we toggle `WindowManager.LayoutParams.FLAG_SECURE` on the host Activity in response to a Flow-driven boolean (`isActiveTabIncognito`), idempotently and survive configuration changes?

**Findings**:

- `FLAG_SECURE` is set via `activity.window.addFlags(FLAG_SECURE)` and cleared via `activity.window.clearFlags(FLAG_SECURE)`. Both calls are idempotent (re-adding an already-set flag is a no-op; same for clear).
- Configuration changes (rotation, theme) destroy + recreate the Activity, triggering re-collection of any state Flow. As long as we re-set the flag in `onCreate` (or `LaunchedEffect(true)` on a Composable) based on the current Flow value, the post-recreate window will be correct.
- The Compose-friendly pattern is a `DisposableEffect` keyed on the Boolean: when `true`, add the flag; when `false`, clear; in the `onDispose`, clear (defensive against the activity being torn down mid-incognito).
- `LocalView.current.context as Activity` works inside a Compose tree hosted by `setContent { … }`. No reflection, no platform-version branching.

**Decision**: New `presentation/util/SecureWindowEffect.kt` Composable:

```kotlin
@Composable
fun SecureWindowEffect(secure: Boolean) {
    val view = LocalView.current
    DisposableEffect(secure) {
        val window = (view.context as? Activity)?.window
        if (secure) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
```

`MainActivity.kt` collects `ObserveActiveTabIsIncognitoUseCase().collectAsStateWithLifecycle(initialValue = false)` and feeds the boolean into `SecureWindowEffect(secure = isIncognito)` placed inside the top-level `ThirtySixTheme { … }` body.

**Rationale**:

- `DisposableEffect(secure)` keyed on the boolean re-runs only when the value changes — no per-frame work.
- `onDispose` clears defensively so a teardown mid-incognito (process death precursor) does not leave the flag stuck on a recreated window.
- Idempotent set/clear satisfies FR-016a explicitly.

**Alternatives considered**:

- Set FLAG_SECURE permanently in the manifest — rejected: blocks ALL screenshots forever, even in normal tabs, breaking user expectation.
- Toggle via `LifecycleEventObserver` in `MainActivity.onCreate` — works but couples lifecycle code to incognito state (single-responsibility violation).
- `WindowCompat.setDecorFitsSystemWindows` API path — unrelated to FLAG_SECURE, not applicable.

## R3 — Incognito tab ID space (collision-free merge with Room auto-increment)

**Question**: `Tab.id: Long` is currently the Room auto-increment PK for normal tabs. Incognito tabs need an ID too — how do we guarantee non-collision when the merged `ObserveAllTabsUseCase` Flow emits both kinds in one list?

**Findings**:

- Room auto-increment PKs are always **positive `Long` ≥ 1** (Spec 005's `TabEntity` declares `@PrimaryKey(autoGenerate = true)`). They never go negative.
- Across the project, every `tabId: Long` parameter is treated as opaque — there is no callsite that does `tabId > 0` arithmetic checks.
- Using **negative Long IDs for incognito** creates a guaranteed disjoint key space without any schema change.

**Decision**: `IncognitoTabRepositoryImpl` maintains an `AtomicLong nextId = AtomicLong(-1L)` and decrements on each new incognito tab. IDs are never reused (incognito tab close removes the entry; the counter never resets).

**Rationale**:

- Zero risk of collision with Room PKs (positive vs negative half-plane).
- O(1) ID generation, lock-free.
- The merged Flow consumer (`TabsViewModel`, `BrowserViewModel`) treats IDs as opaque; no caller introspects sign.
- Process death wipes the counter (in-memory only) — incognito tabs are gone anyway, so a fresh `-1L` start is correct.

**Alternatives considered**:

- UUID-string IDs for incognito — would require changing `Tab.id` type to a sealed-class `TabId{ Persistent(Long); Incognito(String) }`. Touches every callsite, blast-radius too large.
- Positive Long IDs above some "incognito start" sentinel (e.g., `1_000_000_000L+`) — fragile; any normal tab ID climbing past the sentinel breaks the disjoint-set guarantee.

## R4 — Hilt scoping for `IncognitoTabRepository` and `CookieJarSnapshotManager`

**Question**: What Hilt scope should the new singletons use? They need to outlive ViewModels but are local to a single app process.

**Decision**: Both `@Singleton` (application-process-scoped). Bound in a new `app/.../di/IncognitoModule.kt` with `@InstallIn(SingletonComponent::class)`.

**Rationale**:

- Incognito session spans multiple ViewModels (`BrowserViewModel`, `TabsViewModel`) and survives Activity recreation. ViewModel-scope is too narrow; `@ActivityScoped` is too narrow for the same reason.
- Process death = Singleton dies = incognito state wiped — the desired behaviour per FR-012 is automatic.
- Mirrors the Spec 011 `UrlConfigModule` post-amendment scope promotion (ViewModel → Singleton).

**Alternatives considered**:

- `@ActivityRetainedScoped` — survives configuration change but dies on Activity-finish; works for our case but is a less common scope and easier to misuse. Rejected on familiarity grounds.

## R5 — WebView lockdown delta for incognito tabs

**Question**: Beyond Spec 007's universal lockdown (file-access denied, mixed-content NEVER, JS bridges forbidden, permissions silently denied), what additional `WebSettings` and lifecycle calls does an incognito WebView need?

**Findings** (verified against `android.webkit` reference, Android 14 API):

- `WebSettings.setSaveFormData(false)` — disables form-data autofill capture. Deprecated since API 26 but still respected on older devices in our minSdk window. Setting it costs nothing.
- `WebSettings.setCacheMode(LOAD_NO_CACHE)` — forces every request to bypass the disk cache; combined with the per-tab `clearCache(true)` on destroy, no incognito request leaves a cached resource artefact behind.
- `WebSettings.setSavePassword(false)` — removed in API 18, not applicable.
- `WebSettings.setGeolocationEnabled(false)` — already covered by Spec 007's universal permission-deny posture, but explicit-belt-and-braces is acceptable.
- `WebView.clearHistory()` — wipes the per-WebView back/forward list. Called on destroy.
- `WebView.clearFormData()` — wipes any in-memory form data captured before `setSaveFormData(false)` could take effect (race-defensive).
- `WebView.clearMatches()` — wipes find-on-page state.
- `WebView.clearSslPreferences()` — wipes SSL-error-bypass decisions.
- `WebView.clearCache(true)` — wipes per-app caches (note: this is global per WebView config but only matters for incognito-touched data given LOAD_NO_CACHE setting).

**Decision**: `BrowserWebView.kt` factory branch on `state.isIncognito`. The incognito branch applies the additional settings + registers a `DisposableEffect` cleanup that calls the four `clear*` methods in order before the existing Spec 007 destroy sequence (`loadUrl("about:blank") → removeAllViews() → destroy()`).

**Order of teardown** (FR-017 — fixed sequence):

```
1. webView.stopLoading()                    // cancel any in-flight nav
2. webView.clearHistory()                   // wipe back/forward
3. webView.clearFormData()                  // wipe captured form values
4. webView.clearMatches()                   // wipe find-on-page
5. webView.clearSslPreferences()            // wipe SSL exceptions
6. webView.clearCache(true)                 // wipe any cached resources (also clears disk cache for this WebView's profile)
7. webView.loadUrl("about:blank")           // detach renderer from prior URL
8. (parent as? ViewGroup)?.removeView(webView)
9. webView.removeAllViews()
10. webView.destroy()                       // free native resources
```

**Rationale**: This sequence is the union of Spec 007's destroy pattern (steps 7–10) plus the data-clearing prefix mandated by FR-011 / FR-017. Each step is independently safe (each `clear*` method is a no-op on an already-cleared WebView) so re-entry under stress is harmless.

**Alternatives considered**:

- Skip `clearCache(true)` and rely on `LOAD_NO_CACHE` alone — rejected: `LOAD_NO_CACHE` only affects future requests; any cached resource set BEFORE the incognito tab was attached (via shared global cache) could remain and bias future loads.

## R6 — Switcher card: suppress screenshot read for incognito without touching `ScreenshotCache`

**Question**: Spec 011's `ScreenshotCache` reads by tab ID; for incognito tabs we want the switcher card to render a placeholder regardless of cache contents (FR-014).

**Decision**: Gate at the **composable callsite** (`TabSwitcherCard`), not at the cache. The composable branch:

```kotlin
when {
    tab.isIncognito -> IncognitoPlaceholderPreview(tab)
    else -> SiteScreenshotPreview(tab)  // existing Spec 011 path
}
```

`ScreenshotCache` and `FaviconCache` interfaces and impls remain UNCHANGED — they continue to be hostname-keyed / tab-id-keyed. The "no incognito write" behaviour is enforced upstream at the writer (`BrowserViewModel.onScreenshotReady` and `onIconReceived` short-circuit when `state.isIncognito = true` per R7) so the cache directories never receive an incognito entry in the first place.

**Rationale**:

- Cache impls remain a single-responsibility key-value store. Knowledge of "incognito" stays in presentation + ViewModel layer.
- Read-side gate is defensive: even if a stale cache entry survived from a prior bug, the composable would not render it.
- Zero risk of breaking the existing `TabSwitcherCard` snapshot tests (Spec 011) — they all use `isIncognito = false` tabs.

## R7 — Cache-write gating point in `BrowserViewModel`

**Question**: Where exactly in `BrowserViewModel` do we short-circuit favicon + screenshot cache writes when the active tab is incognito (FR-009 / FR-010)?

**Findings**:

- `BrowserViewModel` has two existing mutators that fan out to caches: `onIconReceived(host, bitmap)` (writes to `FaviconCache`) and `onScreenshotReady(tabId, bitmap)` (writes to `ScreenshotCache`).
- The simplest gate is a single early-return at the top of each method: `if (uiState.value.isIncognito) return`.

**Decision**: Single-line guards in both methods. NO changes to `FaviconCache` / `ScreenshotCache` interfaces. NO changes to `BrowserNavigationCallbacks` shape (the gate is downstream of the callback dispatch, hidden inside the ViewModel).

**Rationale**:

- Minimal blast radius: two-line diff in `BrowserViewModel.kt`.
- Easy to unit-test: `BrowserViewModelTest` adds two tests asserting "icon/screenshot received while incognito → cache.put(...) is NEVER called."
- Caches retain their unmodified Spec 011 contract → reduces regression risk on the Spec 011 test suite (39 tests, all green).

## R8 — Stress-test harness for SC-005 (100 rapid open/close cycles)

**Question**: How do we run 100 open-then-close cycles inside an instrumented test without the test itself becoming flaky / starving the UI thread?

**Decision**: New instrumented test class `IncognitoStressInstrumentedTest.kt` using:

- `@HiltAndroidTest` boot via the existing Spec 007 `HiltTestRunner`.
- A single test method `incognito_open_close_stress_runs_100_cycles_without_crash`.
- The body: `repeat(100) { viewModel.openIncognitoTab(); composeTestRule.waitForIdle(); viewModel.closeActiveTab(); composeTestRule.waitForIdle() }`.
- Wrapped in `try-catch (Throwable)`; assertion is `fail("crash detected: ${e.message}")` — a passing test is one that completes the loop with no exception.
- Memory check: `Runtime.getRuntime().totalMemory()` snapshot before + after; assert delta < 2 MB (heap-stability sanity check, not a hard 0-bytes claim because GC timing is non-deterministic).

**Rationale**:

- `composeTestRule.waitForIdle()` between operations ensures recomposition and DisposableEffect cleanups complete before the next iteration — without it, race conditions between WebView destroy and the next create can cause spurious flakes.
- Heap-delta sanity check catches obvious leaks (e.g. retaining all 100 WebView instances) without becoming flaky on GC scheduling jitter.
- The test asserts crash-resilience (the SC-005 goal) by completing rather than by introspecting — Android test infrastructure surfaces any JVM exception or native crash to the test runner automatically.

**Alternatives considered**:

- ANRWatchdog or `testTimeout` per cycle — overkill; the implicit test timeout (default 60s) is more than enough for 100 cycles at ~50 ms each.
- Profile heap with LeakCanary — useful but adds a debug-only dependency for v1.0; deferred to a future stability-focused spec.

## R9 — Active-tab pointer fallback when the last incognito tab closes

**Question**: When the last incognito tab closes while normal tabs remain, where does `BrowserViewModel.activeTabId` point next (FR-006)?

**Decision**: The merged `ObserveAllTabsUseCase` orders the unioned list by `lastActiveAt DESC, isIncognito ASC, id ASC` so that:

- Normal tabs are the natural fallback (they retain their `lastActiveAt` from before the incognito session).
- The `closeIncognitoTab` use case re-emits via the merged Flow; the next emission's `head` is the most-recently-active normal tab.
- `BrowserViewModel.observeActiveTab()` consumes the merged Flow's head as the active-tab pointer — no special-case logic needed in the ViewModel.

**Rationale**: Pure data-flow fallback, no imperative "if last incognito then switch to X" branch in the ViewModel. Reduces logic surface.

**Alternatives considered**:

- Track `mostRecentNormalTabId` separately in `IncognitoTabRepository` — rejected: needless coupling, makes the incognito repo know about normal tabs.

## R10 — External-intent crash safety (FR-018)

**Question**: How do we guarantee that `tel:`, `mailto:`, `intent://`, and other custom schemes opened from an incognito tab dispatch without crashing when no app handles them?

**Findings**:

- `Context.startActivity(intent)` throws `ActivityNotFoundException` when no Activity matches.
- `intent://` scheme parsing throws `URISyntaxException` for malformed URIs.
- A defensive `WebViewClient.shouldOverrideUrlLoading` override that wraps both `Intent.parseUri` and `startActivity` in a single `try { … } catch (Throwable) { return true (consume the URL silently) }` guarantees no propagation of platform exceptions into the Compose tree.

**Decision**: Add try/catch wrapper to `BrowserWebView.kt`'s existing `shouldOverrideUrlLoading` for ALL non-http(s) schemes (incognito or not — strengthens Spec 007/008 baseline). Optionally surface a brief localized toast on `ActivityNotFoundException` (via a Channel-driven side-effect into BrowserViewModel) — DEFERRED to Spec 016 unless user demand surfaces.

**Rationale**: The strengthening also benefits normal tabs (Spec 007/008 didn't have explicit catch). FR-018 makes it a hard requirement for incognito; we extend it universally for free.

**Alternatives considered**:

- Pre-validate every URI via `PackageManager.queryIntentActivities` before `startActivity` — costs a binder call per nav, slower and provides no additional safety beyond catch.

---

## Summary of new dependencies, version verification, 16KB compliance

**No new packages introduced**. All implementation uses existing platform APIs (`android.webkit.CookieManager`, `WindowManager.LayoutParams.FLAG_SECURE`, `kotlinx.coroutines.sync.Mutex`, Compose `DisposableEffect`) and existing project libraries (Hilt, Compose BOM, Material3, kotlinx-coroutines, Turbine for tests).

16KB CI gate result: expected ✅ — zero new `.so` files, the existing 8/8 native lib entries (all from Compose-managed libs) remain `align=0x4000`. Spec 011 baseline preserved.

Constitution §IX dependency-currency verification: N/A — no `libs.versions.toml` changes.

**Date verified**: 2026-05-03.
