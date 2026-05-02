# Phase 0 — Research: Address Bar / Omnibox

**Feature**: Address Bar / Omnibox (Spec 009)
**Date**: 2026-05-02
**Status**: Complete — all research items resolved; no `NEEDS CLARIFICATION` markers remain.

The spec's three clarifications (Q1 loading-state display, Q2 redirect-chain update, Q3 auto-unfocus on submit) settled the major UX questions. Research below resolves the remaining implementation choices that the spec deliberately left to planning.

## R1 — Composable choice for the address bar surface

**Decision**: Material3 `OutlinedTextField` wrapped in a thin `AddressBar` Composable that lives in `presentation/browser/components/`.

**Rationale**:
- `OutlinedTextField` provides everything we need out of the box: leading/trailing slot, IME action wiring, single-line mode, content-description hooks, default 48 dp+ touch target, and full Material3 theming (colorScheme + typography + shapes inherited).
- It is BOM-managed (Compose BOM 2026.04.01 / Material3 1.4.0) — already on the classpath since Spec 003. **Zero new packages.**
- It works on API 24+ (no API-gated behavior), matching project minSdk.
- It composes cleanly with our existing `Spacing.*` tokens (Constitution §III).

**Alternatives considered**:
- **Material3 `SearchBar`** — purpose-built for this UX pattern (search + suggestions), but the docked/expanded variants own their own scrim + back-handling state machine and are designed around an in-bar suggestions list. Spec 009 explicitly defers suggestions (FR-028); using `SearchBar` for v1 would require us to disable or work around its expanded mode and would couple us to a more complex contract for the spec that adds suggestions later. Reconsider when we ship suggestions.
- **`BasicTextField` + custom decoration box** — more flexible but reimplements every Material3 affordance we get for free (border, label, leading/trailing alignment, accessibility). Premature complexity for this spec's surface.
- **Custom `Composable` over `Modifier.combinedClickable`** — over-engineered; no requirement justifies it.

**16 KB / dependency note**: All three alternatives are in the same `androidx.compose.material3` artifact already on the classpath; the choice has no APK-size or 16 KB-alignment implications.

## R2 — URL vs query classification heuristic

**Decision**: After trim + newline-strip (FR-006 / FR-007), input is classified as **URL** if it matches either of:
1. The regex `^[a-zA-Z][a-zA-Z0-9+\-.]*://.*` (i.e., contains a scheme followed by `://` near the start), OR
2. Contains at least one `.` (dot) anywhere in the trimmed value.

Otherwise, it is classified as a **query**. Both rules are encoded into a single regex constant `UrlPatterns.URL_HEURISTIC_REGEX` for testability and to satisfy Constitution §III (no inline regex).

**Rationale**:
- The user's Q3 clarification (during /speckit-specify scope discussion) explicitly chose the simple `://` or `.` heuristic.
- Faster than a true RFC-3986 parser; correct enough for v1 of a consumer browser per spec assumption A6.
- Edge cases like `localhost` (no dot → classified as query) are already documented in spec edge cases as accepted v1 trade-offs.
- Putting both rules in a single named regex makes the unit-test surface trivial (`AddressBarInputClassifierTest`).

**Scheme normalization**:
- If a URL is detected and lacks a scheme (per the regex above, it has a dot but not `://`), prepend `https://` (FR-009).
- If the input already starts with `http://`, `https://`, or any other valid scheme, do NOT change it. Allows the user to explicitly type `http://` for legacy hosts.
- The regex is case-insensitive on the scheme letters (FR edge case: uppercase `HTTPS://`).

**Alternatives considered**:
- `android.webkit.URLUtil.isValidUrl(str)` — uses platform classifier, but is somewhat strict and would reject `developer.android.com` without scheme. Doesn't match user-chosen heuristic.
- Custom multi-token classifier (TLD list, IP regex, etc.) — premature for v1; same false-positive cost (`kotlin.coroutines` → URL) is documented and accepted.

## R3 — Query encoding + Google search URL construction

**Decision**: When input is classified as a query, build the Google search URL by:
1. URL-encoding the trimmed input via `java.net.URLEncoder.encode(query, "UTF-8")`.
2. Substituting it into the Google search URL template `https://www.google.com/search?q=%s` declared as `UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE`.

The template constant is added to `core/constants/UrlConstants.kt` (file already exists from Spec 008 home-URL flip; this spec adds one new `const val`). The classifier returns `AddressBarSubmitResult.Query(query: String)` — the **substitution happens in the ViewModel**, not in the classifier — so Spec 010's refactor to `SearchEngineRepository` only needs to touch the ViewModel and the new repository, not the classifier.

**Rationale**:
- `URLEncoder.encode(..., "UTF-8")` correctly handles spaces (`+`), Unicode, and reserved characters per the WHATWG/W3C `application/x-www-form-urlencoded` form, which Google Search accepts.
- Keeping the constant in `UrlConstants.kt` follows Constitution §III "URLs (default home, search) → `core/constants/UrlConstants.kt`" mapping verbatim.
- The classifier returns a sealed result rather than the final URL so the URL builder is replaceable in Spec 010 without changing classification semantics.

**Alternatives considered**:
- `Uri.Builder` from `android.net` — would force the domain layer to import an Android type, even though the URL is a simple template substitution. Overkill.
- `URLEncoder.encode(query)` (no charset) — deprecated since Java 1.4; explicit `"UTF-8"` is required.
- Hard-coding the URL inline in the ViewModel — Constitution §III violation.

## R4 — Hostname extraction for unfocused display (FR-014 / FR-016)

**Decision**: Use `android.net.Uri.parse(url).host` for hostname extraction, with a fallback to displaying the full URL when `host` is null or empty.

The extraction lives in a small extension function `String.extractHostnameOrSelf(): String` placed in `core/extensions/UrlExtensions.kt` (new file). The function handles all three FR cases:
- URL with host (`https://developer.android.com/jetpack/compose?utm=x`) → returns `developer.android.com`
- URL without host (`about:blank`, `file:///...`) → returns the original URL string (FR-016 fallback)
- Empty / null URL → returns empty string (FR-017 placeholder is shown by the Composable, not by this extractor)

**Rationale**:
- `Uri.parse` is on the existing classpath, robust against malformed URLs (returns `null` host instead of throwing), and handles all schemes Android understands.
- Living in `core/extensions/` keeps the helper trivially unit-testable (pure function over a `String`) and reusable by future specs (e.g., bookmarks display, history list).
- A standalone test class (`HostnameExtractionTest`) covers the happy paths + the `about:blank` / `file:///` fallback paths so we don't regress.

**`www.` stripping policy** (the Outstanding item flagged at end of /speckit-clarify):

**Decision**: **Do NOT strip `www.` in v1.** Display the host exactly as `Uri.host` returns it.

**Rationale**:
- Stripping `www.` is a polish choice that some browsers (Chrome desktop) make and others (Safari mobile) do not. There is no consistent industry norm.
- Keeping the raw host means the displayed string is always equal to `Uri.host` — easier to assert in unit + UI tests, no asymmetry between display and copy/paste, no surprise when the user wants to verify they're on `www.example.com` vs `example.com` (subdomain check matters for cookies and SSO).
- Cost to add stripping later is one line in `extractHostnameOrSelf()` plus one test. Reversible with negligible cost; safe to defer.

**Alternatives considered**:
- `URL` from `java.net` — throws `MalformedURLException` on schemes Android accepts but Java doesn't (e.g., `content://`). Worse safety profile than `Uri.parse`.
- Manual string splitting — ad-hoc, hard to audit, can't handle IPv6 hosts, port numbers, or userinfo.

## R5 — Auto-unfocus + soft-keyboard dismiss on submit (FR-013a)

**Decision**: On the IME action callback (i.e., when `OutlinedTextField`'s `keyboardActions = KeyboardActions(onGo = { … })` fires), the Composable calls in this exact order:

1. `softwareKeyboardController?.hide()` — dismiss the soft keyboard.
2. `focusManager.clearFocus()` — release focus from the address bar.
3. `viewModel.onAddressBarSubmit()` — invoke the ViewModel submit method, which classifies + URL-builds + calls `actionsHandle.loadUrl(...)`.

`LocalSoftwareKeyboardController.current` and `LocalFocusManager.current` are both standard Compose `CompositionLocal`s, available since Compose 1.0 — no API-level guards needed.

The empty-submit case (FR-012) returns early from `onAddressBarSubmit()` before any focus/keyboard side effect happens — but per FR-013a, the empty-submit path explicitly does NOT trigger focus or keyboard release either, which falls out naturally when the Composable inverts the order: the keyboard/focus dismiss happens *only* if `viewModel.onAddressBarSubmit()` returns `true` (signal that a submit actually fired).

**Final order** (refined):
1. `viewModel.onAddressBarSubmit()` returns `Boolean` — `true` if non-empty input was submitted, `false` if empty/whitespace-only no-op.
2. If `true`: `keyboardController?.hide()` then `focusManager.clearFocus()`.
3. If `false`: do nothing — focus and keyboard stay as they were.

This matches FR-012 + FR-013a exactly.

**Rationale**:
- Standard Compose pattern; no third-party helper needed.
- Explicit ordering documented in research means tasks.md can produce one focused unit/UI test that asserts the keyboard dismiss happens iff the ViewModel submit returned `true`.

**Alternatives considered**:
- Putting the dismiss inside the ViewModel — violates Constitution §IV (ViewModel must not import Compose UI types like `SoftwareKeyboardController`).
- Putting the dismiss via a side-effect `LaunchedEffect` watching state — works but is harder to test and adds an indirection (state field just to trigger an imperative side-effect).

## R6 — Back-during-focus closes keyboard first, then propagates to PredictiveBackHandler (FR-026)

**Decision**: `OutlinedTextField` (more precisely, the `BasicTextField` it wraps) already implements the system-back-while-keyboard-is-open contract via the standard Android IME flow: when the soft keyboard is open and the user invokes back, the platform first dismisses the keyboard. Compose receives the back gesture only when the keyboard is *not* open. So the ordering required by FR-026 is the platform default — no custom `BackHandler` chaining needed.

To make this safe under Spec 008's `PredictiveBackHandler(enabled = state.canGoBack)`:

- The text field's IME consumes the back to close itself (platform).
- The Compose-level `PredictiveBackHandler` does not see the event during keyboard dismissal.
- Subsequent back presses (after focus lost / keyboard gone) reach the predictive handler normally.

We additionally add a `BackHandler(enabled = state.isAddressBarFocused)` inside the `BrowserScreen` Composable that runs *before* the `PredictiveBackHandler`. It catches the rare case where the address bar is focused but the keyboard is hidden (e.g., user dismissed keyboard manually) — in that case, back unfocuses the bar without invoking predictive WebView-back. Compose `BackHandler` invocation order is: most-recently-added, innermost-first; we declare ours before the `PredictiveBackHandler` so it wins when both are enabled.

**Rationale**:
- Avoids re-implementing IME-dismiss-on-back, which is a well-trodden Android contract.
- The added in-screen `BackHandler` covers the niche path where the keyboard is already hidden but focus remains (rare but possible — e.g., hardware keyboard, voice typing dismiss).
- Both handlers' `enabled` flags are derived from the existing `BrowserUiState`, so the predictive-back gating from Spec 008 is preserved unchanged when address bar is unfocused.

**Alternatives considered**:
- Single global `BackHandler` that decides between focus-clear and predictive-back via if/else — fragile, requires us to manually re-implement predictive preview, and would defeat Spec 008's `PredictiveBackHandler`.
- Listening to `WindowInsetsCompat.isVisible(IME)` — possible but unnecessary; the platform IME-dismiss-on-back behavior already gives us what we need.

## R7 — Listening to WebView URL changes for the live mirror (FR-019, FR-019b)

**Decision**: Reuse the existing `BrowserWebViewCallbacks` mechanism that Spec 007 / 008 set up. Spec 008 already calls `WebViewClient.doUpdateVisitedHistory(view, url, isReload)` and forwards `view.url` into `BrowserViewModel.onCanGoBackChanged / onCanGoForwardChanged`. Spec 009 adds **one** new callback `onUrlChanged(url: String)` fired from the same `doUpdateVisitedHistory` and additionally from `WebViewClient.onPageStarted(view, url, favicon)`.

The reason for both callbacks:
- `onPageStarted` fires on the very first network request of a redirect chain — gives us the "live trace" that FR-019b requires.
- `doUpdateVisitedHistory` fires on every navigation transition that affects the back/forward list (including SPA `pushState`/`replaceState`).

`BrowserViewModel.onUrlChanged(url)` updates `BrowserUiState.currentUrl` via `MutableStateFlow.update { }`. The `AddressBar` Composable observes `currentUrl` (when unfocused) for hostname display per FR-014 and ignores it (when focused) per FR-015.

**Rationale**:
- Adds the minimum surface to the existing callback bundle (`BrowserWebViewCallbacks` already at the 6-callback `LongParameterList` detekt threshold per Spec 008 — wait, this might push us over). Let me cross-check: Spec 008 says "BrowserWebViewCallbacks + 2 fields (6 total = exactly at detekt LongParameterList threshold)". Adding `onUrlChanged` makes 7 → would exceed. **Required structural follow-up**: bundle the callbacks differently — e.g., introduce a sub-shape `WebViewNavigationCallbacks` for the four nav callbacks (`onCanGoBackChanged`, `onCanGoForwardChanged`, `onLoadStopped`, `onUrlChanged`) and keep the original `BrowserWebViewCallbacks` at 3–4 fields. This is exactly the same detekt mitigation pattern Spec 008 used when it split `NavigationBottomBarCallbacks` from the main bundle.
- Avoids creating a second indirection layer (e.g., a Flow off the WebView) — the existing callback pipeline is well-tested and synchronous.

**Alternatives considered**:
- Polling `webView.url` from the ViewModel via a tick coroutine — re-derived state, wastes CPU, and lags actual changes.
- Subclassing `WebViewClient` further — would force more `WebView`-side type leakage out of `BrowserWebView.kt`. The callback bundle already provides isolation.
- Skipping `onPageStarted` and only using `doUpdateVisitedHistory` — would miss the first hop of cross-domain redirect chains, contradicting the Q2 clarification.

## R8 — Display-switching strategy (hostname ↔ full URL on focus toggle, FR-014/15/18)

**Decision**: The displayed text is computed in the Composable as a function of `isFocused × addressBarText × currentUrl`:

| isFocused | addressBarText | Display |
|---|---|---|
| `false` | (any — typically empty) | Hostname extracted from `currentUrl` (or full URL if no host; or empty string if no URL loaded yet — placeholder hint then renders) |
| `true` (just focused) | populated from `currentUrl` on focus-acquisition | Full `currentUrl`, all-text-selected |
| `true` (mid-typing) | the user's in-progress text | The user's text |

The "populate `addressBarText` from `currentUrl` on focus-acquisition" step happens in a `LaunchedEffect(isFocused)` — when focus flips false → true, the Composable copies `currentUrl` into the bound text-field state and invokes `selectAll()` via the field's `TextFieldValue.copy(selection = TextRange(0, text.length))`.

When focus flips true → false (without submit), the Composable clears its local text state — next render falls back to hostname display. (Submit path explicitly clears focus first per R5.)

**Rationale**:
- Declarative + idempotent: state derives from focus and the current URL; no spooky interactions.
- "Select all on focus" is the standard browser behavior the user expects on FR-018.
- `TextFieldValue` (over plain `String`) gives us the selection control we need for the select-all behavior without any custom modifier.

**Alternatives considered**:
- Always showing full URL — fails FR-014 (compact display).
- Two text fields (one display-only label + one editor) swapped on focus — works but creates a focus-jump animation glitch and complicates accessibility (two `Modifier.semantics` to keep in sync).

## R9 — Text/focus state preservation across rotation (FR-027)

**Decision**: Address-bar text and focus state are stored in `BrowserUiState` (which already lives in the `BrowserViewModel` and survives rotation by virtue of the ViewModel scope). The `OutlinedTextField` reads/writes via the ViewModel through callbacks — no `rememberSaveable` needed at the Composable level because the source of truth is the ViewModel `StateFlow`.

The two new state fields:
- `addressBarText: String` — empty by default; populated by user input.
- `isAddressBarFocused: Boolean` — `false` by default.

Both are part of the same `BrowserUiState` `data class`; rotation re-attaches the same ViewModel and the StateFlow value drives the new Composition.

**Rationale**:
- Single source of truth for transient UI state.
- Aligns with Spec 008's pattern (`canGoBack` / `canGoForward` also on `BrowserUiState`).
- Avoids the dual-source-of-truth bug where `rememberSaveable` and ViewModel disagree after rotation.

**Alternatives considered**:
- `rememberSaveable` only — works but loses the value on process death. ViewModel-backed survives both rotation and the standard `SavedStateHandle`-restored ViewModel re-creation.

## R10 — Test strategy

**Decision**: Three test surfaces, mirroring the Spec 007 / 008 precedent.

1. **Pure-unit tests** (`app/src/test/`):
   - `AddressBarInputClassifierTest` — classification table-driven: ~12 representative inputs covering URLs with/without scheme, IPs, queries, mixed-bag (`kotlin.coroutines` → URL false-positive accepted), edge cases (uppercase scheme, leading whitespace, newlines, empty).
   - `BrowserViewModelTest` — extend the existing class; new tests for `onAddressBarTextChange`, `onAddressBarFocusChange`, `onAddressBarSubmit` happy/empty paths, `onAddressBarClear`, `onUrlChanged` mirror behavior.
   - `HostnameExtractionTest` (or merged into `UrlExtensionsTest`) — `Uri.parse`-backed hostname extraction over the same coverage table as the classifier (host present, host absent, malformed). **Robolectric required** because `android.net.Uri` is on the Android stack — already configured for Spec 005 at SDK 33; reused for free.

2. **Compose UI test** (`app/src/androidTest/`, component-level):
   - `AddressBarTest` — drive the `AddressBar` Composable in isolation (no Hilt, no real WebView). Assertions: focus toggle changes displayed text shape, clear button visibility, click on clear empties the field, IME submit path invokes the supplied callback exactly once. Parallels `NavigationBottomBarTest` from Spec 008.

3. **Full instrumented integration** with a real `WebView` and a network: **deferred** to a later instrumented-test pass, mirroring the Spec 008 deferred T028/T031/T036/T040 pattern. Address-bar interactions with a real WebView would require Hilt + Espresso-Web + a known-good test page, and the `BrowserScreenOfflineErrorTest` notes already document that real-WebView integration tests are flaky.

**Rationale**:
- The bulk of the logic (classification, URL building, hostname extraction, state derivation) is testable as pure JVM, which keeps the suite fast and CI-stable.
- The component-level Compose UI test exercises the user-visible interaction contract without the WebView flakiness.
- The deferred integration tests do not block the spec — Spec 008 set the precedent that 4/8 integration tests can be follow-ups.

## R11 — Dependency / version verification (Constitution §IX)

**Verified**: 2026-05-02. **No new packages added.** All UI APIs used (`androidx.compose.material3:material3:1.4.0`, `androidx.compose.material:material-icons-core` BOM-managed, `androidx.compose.ui:ui` for `LocalSoftwareKeyboardController` / `LocalFocusManager`, `androidx.activity:activity-compose` for `BackHandler`) are already on the classpath via Compose BOM 2026.04.01 (verified at Spec 001 implementation; carried forward). Hilt 2.59.2 / KSP 2.3.7 unchanged.

**16 KB compliance**: Zero new `.so`. The CI gate (`.specify/scripts/bash/verify-16kb-alignment.sh`) auto-passes by construction. SC-008 satisfied at plan time.

**APK size delta budget**: SC-007 = ≤ 50 KB. New surface = 1 small Composable + 1 small classifier + 4 ViewModel methods + 2 string keys × 8 locales (~16 short translations). Conservative estimate < 25 KB — well under budget.

## Summary table

| # | Item | Decision | Constitution / SC link |
|---|------|----------|------------------------|
| R1 | Composable choice | M3 `OutlinedTextField` | §III, SC-007 |
| R2 | URL/query classifier | Single regex `://` or `.` heuristic in `UrlPatterns.kt` | §III, FR-008 |
| R3 | Query → search URL | `URLEncoder.encode(..., "UTF-8")` + `UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE` | §III, §X (Spec 010 will refactor) |
| R4 | Hostname extraction | `Uri.parse(url).host` extension fn; no `www.` stripping | §III, FR-014/16 |
| R5 | Submit unfocus + IME dismiss | `viewModel.onSubmit() → if (true) keyboard.hide() + focus.clear()` | FR-012 / FR-013a |
| R6 | Back-during-focus | Platform IME-dismiss + screen-level `BackHandler(enabled=isFocused)` ahead of Spec 008's `PredictiveBackHandler` | FR-026 |
| R7 | URL change reactivity | New `onUrlChanged` callback fired from `onPageStarted` + `doUpdateVisitedHistory`; sub-shape extraction to satisfy detekt `LongParameterList` | FR-019 / FR-019b |
| R8 | Display switch | `TextFieldValue` driven by focus + currentUrl; `LaunchedEffect(isFocused)` populates from URL on focus + selectAll | FR-014/15/18 |
| R9 | Rotation persistence | `BrowserUiState` stores `addressBarText` + `isAddressBarFocused` | FR-027 |
| R10 | Test strategy | Pure unit + component Compose UI test; full integration deferred | §VI, SC-006 |
| R11 | Version / 16 KB | Zero new packages; CI gate auto-passes | §IX, SC-008 |

All Phase 0 items resolved. Ready for Phase 1 design.
