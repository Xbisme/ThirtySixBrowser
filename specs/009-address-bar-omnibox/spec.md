# Feature Specification: Address Bar / Omnibox

**Feature Branch**: `009-address-bar-omnibox`
**Created**: 2026-05-02
**Status**: Draft
**Input**: User description: "Address bar / omnibox: top-of-screen TextField for URL or search query input. Parses input as URL (when contains '.' or '://') or search query (else) and submits to the browser. Hard-codes Google search URL temporarily; Spec 010 will refactor into SearchEngineRepository. No suggestions/autocomplete in v1 (deferred). Clear button included; voice input deferred. Display hostname only when not focused, full URL when focused. No auto-focus on cold-start. Zero new packages expected."

## Clarifications

### Session 2026-05-02

- Q: When the user submits a URL or query, what does the address bar display while the new page is still loading? → A: The bar switches immediately to the submitted target URL (after `https://` prefix logic if needed) and remains on that URL throughout the load. The previous URL is not retained, and the placeholder is not shown during load.
- Q: How does the address bar reflect URL changes during a server- or client-side redirect chain (HTTP 301/302, `Location`, JS redirect) before navigation commits? → A: Update at every URL transition in the redirect chain (live trace). User can see each intermediate URL the WebView follows; this surfaces unexpected cross-domain redirects without adding cost when redirects stay on the same host (since hostname-only display per FR-014 hides intra-domain flicker).
- Q: After a successful (non-empty) submit, what happens to the address bar's focus state and the soft keyboard? → A: Both are released automatically — the address bar loses focus and the soft keyboard dismisses immediately on submit. This matches the default behavior of Chrome, Safari, and Firefox mobile, returns the full screen to the loading WebView, and ensures Spec 008's `PredictiveBackHandler` is reachable on the very next back gesture without first being consumed by an IME-dismiss step.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Submit a URL to navigate (Priority: P1)

A user wants to visit a known website. They tap the address bar at the top of the browser, type or paste a URL (with or without `https://` prefix), and press the keyboard's "Go" / Enter action. The browser loads that URL.

**Why this priority**: This is the most common entry point for any browser session. Without it, the only way to navigate is by clicking links inside already-loaded pages or returning to the hard-coded home URL via Spec 008's Home button. P1 because it unblocks meaningful user testing of the browser as an actual browser.

**Independent Test**: With the app open on the home page, tap the address bar, type `developer.android.com`, press Enter on the soft keyboard. The page loads in the WebView. No other feature (suggestions, search, history) needs to be present for this scenario to deliver value.

**Acceptance Scenarios**:

1. **Given** the BrowserScreen is on the home page, **When** the user taps the address bar, types `https://developer.android.com`, and submits, **Then** the WebView navigates to that URL and the address bar reflects the loaded page.
2. **Given** the BrowserScreen is on the home page, **When** the user types `developer.android.com` (no scheme) and submits, **Then** the system prepends `https://` and navigates successfully.
3. **Given** the BrowserScreen has loaded any page, **When** the user taps the address bar, types a different URL, and submits, **Then** the WebView replaces the current page with the new URL.
4. **Given** the address bar is focused with text in it, **When** the user dismisses the keyboard without submitting, **Then** the page does not navigate and the previous URL remains displayed.

---

### User Story 2 — Submit a search query (Priority: P1)

A user does not have a specific URL in mind; they want to search. They type a free-form query (e.g., `android compose webview`) into the address bar and submit. The browser performs a Google search by loading a Google search results URL.

**Why this priority**: Real-world browser usage is roughly half URL navigation, half search. P1 because Spec 010 (`search-engine-google`) is the next planned spec and depends on this submit path being in place. Shipping queries as no-ops would block end-to-end testing for the browser. Spec 009 hard-codes the Google search URL template (already declared as a constant in the project) as a temporary inline implementation; Spec 010 will refactor this into a `SearchEngineRepository` with user-selectable engines without changing the address-bar UI surface.

**Independent Test**: With the app open, tap the address bar, type `kotlin coroutines`, press Enter. The browser loads a Google search results page for that query. Verify the loaded URL contains the query string.

**Acceptance Scenarios**:

1. **Given** the BrowserScreen is on any page, **When** the user types `kotlin coroutines` (no dot, no scheme) and submits, **Then** the system constructs a Google search URL using the project's URL template and loads it.
2. **Given** the user types a query containing spaces and special characters, **When** they submit, **Then** the query is URL-encoded correctly before being inserted into the search template.
3. **Given** the user types a single non-URL word like `hello`, **When** they submit, **Then** the system treats it as a query (not a URL).

---

### User Story 3 — Address bar stays in sync with the loaded page (Priority: P2)

When the user navigates by means other than typing in the address bar — clicking a link inside a page, using Back/Forward buttons (Spec 008), or being redirected by the page itself — the address bar must update to reflect the new URL so the user always knows where they are.

**Why this priority**: Without this, the address bar becomes a stale label and users can no longer trust it as a location indicator. P2 (not P1) because the WebView itself still works correctly without it; this is a UX correctness requirement rather than a navigation blocker.

**Independent Test**: Open `https://example.com` (a page with a known link to `https://www.iana.org/domains/example`). Click the link inside the page. Observe that the address bar updates from `example.com` to `iana.org` (hostname-only display per FR-008) without the user touching the address bar.

**Acceptance Scenarios**:

1. **Given** the WebView has loaded `https://example.com`, **When** the user clicks an internal link to a different URL, **Then** the address bar updates to the new URL automatically.
2. **Given** the user presses Back or Forward (Spec 008), **When** the WebView navigates to the previous/next history entry, **Then** the address bar reflects the resulting URL.
3. **Given** a page issues an HTTP redirect, **When** the WebView follows the redirect, **Then** the address bar shows the final landing URL, not the original.

---

### User Story 4 — Clear input quickly (Priority: P2)

While the address bar is focused and contains text, a small clear button (X) appears at the trailing edge of the field. Tapping it empties the text without submitting and keeps focus on the field so the user can immediately type a new value.

**Why this priority**: Selecting all + delete on a long URL is friction. The clear button is a single tap. P2 because the field is still usable without it (manual delete works), but standard browser UX expects this affordance.

**Independent Test**: Tap the address bar (auto-shows full URL of the current page), tap the X button, observe field becomes empty, focus stays on the field, keyboard remains open.

**Acceptance Scenarios**:

1. **Given** the address bar is focused and contains the current URL, **When** the user taps the clear button, **Then** the field becomes empty, focus is retained, and the keyboard stays open.
2. **Given** the address bar is empty (or unfocused), **When** the user looks at the field, **Then** no clear button is visible.

---

### User Story 5 — Compact hostname display when idle (Priority: P3)

When the address bar is not focused and a page is loaded, it shows only the hostname of the current URL (e.g., `developer.android.com`) instead of the full URL with scheme, path, and query. When the user taps the bar to focus it, the field expands to show the full URL ready for editing.

**Why this priority**: Showing just the hostname improves readability on a narrow phone screen and reduces visual noise (no `https://...?utm_source=...` distraction). When the user wants to edit, they get the real value. P3 because it is a polish improvement; the bar still works correctly with the full URL always shown.

**Independent Test**: Load `https://developer.android.com/jetpack/compose?utm_source=test`. While the address bar is not focused, verify it shows only `developer.android.com`. Tap to focus. Verify the field switches to the full URL `https://developer.android.com/jetpack/compose?utm_source=test`. Tap elsewhere to unfocus. Verify it returns to hostname-only.

**Acceptance Scenarios**:

1. **Given** the WebView has loaded a URL with a path and query string, **When** the address bar is not focused, **Then** only the hostname is visible.
2. **Given** the address bar is showing hostname-only, **When** the user taps it, **Then** the field shows the full URL and selects all text for easy replacement.
3. **Given** the WebView has not yet loaded any URL (initial state, before any navigation), **When** the address bar is not focused, **Then** the field shows the placeholder hint, not a hostname.
4. **Given** the loaded URL has no host (e.g., `about:blank` or a local file URL), **When** the address bar is not focused, **Then** the field gracefully falls back to showing the full URL.

---

### Edge Cases

- **Empty submit**: User taps Enter on an empty address bar → no-op, no navigation, focus and keyboard state unchanged.
- **Whitespace-only input**: Input is trimmed before classification; trimmed-empty is treated as empty.
- **Leading/trailing whitespace around URL or query**: Trimmed before submission.
- **URL with uppercase scheme** (e.g., `HTTPS://example.com`): Treated as a valid URL (scheme is case-insensitive).
- **Input that looks like both URL and query** (e.g., `kotlin.coroutines`): Classified as URL because it contains a dot. Acceptable false positive in v1; Spec 010 may refine the heuristic if needed.
- **IP-address input** (e.g., `192.168.1.1`): Classified as URL because it contains dots. Loads as `https://192.168.1.1`.
- **Localhost or single-word host without dot** (e.g., `localhost`): Classified as query in v1 (no dot). Trade-off accepted; rare on a mobile browser.
- **Very long URL** (> 2000 chars): Allowed; WebView handles. Address bar text may truncate visually with ellipsis when not focused.
- **System back gesture while address bar is focused**: Should unfocus the address bar and close the keyboard before invoking Spec 008's PredictiveBackHandler. Standard Android IME behavior; ensure the predictive-back handler from Spec 008 does not consume the back event meant for IME dismissal.
- **Configuration change (rotation)**: The text currently in the address bar and the focus state must survive rotation; must not lose user-typed input.
- **Submit during page load**: A new submit while a previous load is still in progress cancels the previous load and starts the new one.
- **Pasting a multiline string**: Newlines are stripped or replaced with spaces before classification.
- **TalkBack / accessibility**: Address bar is announced with an appropriate localized label; clear button has its own content description.

## Requirements *(mandatory)*

### Functional Requirements

#### Address bar surface

- **FR-001**: System MUST display an address bar at the top of the BrowserScreen, present in all states (idle, loading, error overlay) and across both portrait and landscape orientations.
- **FR-002**: Address bar MUST occupy the top toolbar slot of the existing BrowserScreen Scaffold (introduced by Spec 008), parallel to the bottom navigation bar.
- **FR-003**: Address bar MUST remain visible when the on-screen error overlay (Spec 007) is shown, so the user can edit the URL and retry without first dismissing the error.
- **FR-004**: All visible address-bar text (placeholder hint, clear-button content description, error/empty announcements) MUST be available in all 8 supported locales (EN/VI/DE/RU/KO/JA/ZH/FR) per Spec 004.

#### Input handling

- **FR-005**: User MUST be able to enter text into the address bar via the standard soft keyboard.
- **FR-006**: Address bar MUST trim leading and trailing whitespace from the input before classification.
- **FR-007**: Address bar MUST replace any internal newline characters with spaces (handles paste of multi-line strings) before classification.
- **FR-008**: System MUST classify trimmed input as a URL when it contains either `://` or at least one `.` (dot); otherwise classify it as a search query.
- **FR-009**: When the input is classified as a URL and lacks a scheme, system MUST prepend `https://` before loading.
- **FR-010**: When the input is classified as a search query, system MUST build a Google search URL by URL-encoding the trimmed input and substituting it into the project's documented Google search URL template, then load that URL.
- **FR-011**: Submit action MUST be triggered by the keyboard's IME action (typically rendered as "Go" or "Enter") on the soft keyboard.
- **FR-012**: Empty or whitespace-only submit MUST be a no-op (no navigation, no error, focus and keyboard unchanged).
- **FR-013**: A new submit while a previous load is in progress MUST cancel the previous load and start the new one.
- **FR-013a**: On any successful (non-empty) submit, the address bar MUST automatically lose focus and the soft keyboard MUST be dismissed before the navigation begins. This applies whether the input was classified as a URL or a search query. Empty / whitespace-only submits (FR-012) do NOT trigger focus or keyboard release.

#### Display behavior

- **FR-014**: When the address bar is not focused and the WebView has a loaded URL with a host, the field MUST display the hostname only (no scheme, path, or query).
- **FR-015**: When the address bar is focused, the field MUST display the full URL of the currently loaded page (or the user's in-progress edit if they have started typing).
- **FR-016**: When the address bar is not focused and the loaded URL has no host (e.g., `about:blank`, file URLs), the field MUST display the full URL as a fallback.
- **FR-017**: When the address bar is not focused and no URL has yet been loaded (initial cold-start state), the field MUST display a localized placeholder hint and no URL text.
- **FR-018**: When the user taps an unfocused address bar that is showing hostname-only, the field MUST switch to the full URL and select all text for easy replacement.
- **FR-019**: Address bar MUST update to reflect the WebView's current URL whenever the WebView navigates by any means (link click, redirect, back/forward, programmatic load) — i.e., the address bar is a live mirror of the loaded URL while not focused.
- **FR-019a**: When the user submits a URL or query from the address bar, the bar MUST switch immediately to the resolved target URL (after `https://` prefix or Google search URL construction) and continue to display that URL for the entire duration of the load. The bar MUST NOT temporarily revert to the previously loaded URL or to the placeholder hint while the new page is still loading.
- **FR-019b**: During a server- or client-side redirect chain (HTTP 3xx, `Location` header, JavaScript-initiated navigation, meta-refresh), the address bar MUST update at every URL transition the WebView follows. The user MUST see each intermediate URL surface in the bar in real time, not only the final committed landing URL. Intra-host transitions (same hostname, different path) will visually appear unchanged when the bar is unfocused due to the hostname-only display rule (FR-014); cross-host transitions will visibly update the displayed hostname.

#### Clear button

- **FR-020**: A clear button (X icon) MUST appear at the trailing edge of the address bar when the field is focused AND contains non-empty text.
- **FR-021**: Tapping the clear button MUST empty the field, retain focus, and keep the keyboard open.
- **FR-022**: Clear button MUST have a localized content description for accessibility (TalkBack).
- **FR-023**: Clear button MUST NOT be visible when the address bar is unfocused.
- **FR-024**: Clear button MUST NOT be visible when the field is empty.

#### Focus and lifecycle

- **FR-025**: Address bar MUST NOT auto-focus on cold-start; the keyboard MUST stay closed until the user taps the field.
- **FR-026**: Pressing the system back gesture while the address bar is focused MUST first unfocus the field and dismiss the keyboard before propagating the back event to other handlers (so Spec 008's PredictiveBackHandler does not consume keyboard-dismiss intent).
- **FR-027**: Address bar text content and focus state MUST survive configuration changes (rotation, theme change).

#### Out-of-scope (explicitly deferred)

- **FR-028**: Suggestions/autocomplete from history or bookmarks are OUT OF SCOPE for Spec 009; planned for a later spec after history (014) and bookmarks (013) UIs exist.
- **FR-029**: Voice input (microphone icon) is OUT OF SCOPE for v1.
- **FR-030**: User-selectable search engines are OUT OF SCOPE for Spec 009; the Google search URL is hard-coded in this spec and Spec 010 will introduce the abstraction.
- **FR-031**: HTTPS-upgrade for typed-without-scheme URLs is the only scheme adjustment; no protocol downgrade, no scheme alternatives, no smart guessing of `www.` prefixes.

### Key Entities *(include if feature involves data)*

This spec introduces no persistent data and no new database tables. It does introduce one transient UI state shape:

- **AddressBarUiState**: Represents the address bar's current input value, focus state, and the URL being mirrored from the WebView. Pure presentation-layer state; lifecycle is the BrowserScreen ViewModel; no persistence. Lives in the same `presentation/browser/` package as the existing `BrowserUiState` (Spec 007 / 008), either as part of `BrowserUiState` or as a sibling shape — implementation detail decided in plan.md.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can navigate to a typed URL (`developer.android.com`, no scheme) and have the page begin loading within 1 second of pressing Enter on the soft keyboard, on a Pixel 5+-class device.
- **SC-002**: A user can submit a free-form query and reach a Google search results page for that query in 100% of cases (excluding device offline / no-network cases, which are handled by the existing Spec 007 error UI).
- **SC-003**: Address bar correctly classifies 95%+ of inputs from a representative sample of common entries (URLs with/without scheme, URLs with paths/queries, IP addresses, single-word queries, multi-word queries) per the documented heuristic.
- **SC-004**: Address bar text and focus state survive device rotation in 100% of test cases — no input loss, no spurious submit.
- **SC-005**: When unfocused, address bar displays hostname-only for any URL with a host; visual inspection across all 8 locales confirms the hostname renders correctly with the locale's typography (no clipping, no font fallback issues).
- **SC-006**: Address bar is fully usable via TalkBack on Android 13+: the field is announced with its localized label, the clear button has a distinct localized content description, and the IME submit action is reachable via swipe gestures.
- **SC-007**: APK release size delta vs the previous spec's baseline (1.67 MB after Spec 008) is at most +50 KB. (Realistic: zero new packages, only new strings × 8 locales + a few new Composables and ViewModel methods.)
- **SC-008**: 16 KB CI alignment gate continues to pass: every native library entry in the release APK aligns to `0x4000` or larger. No new `.so` files introduced by this spec.
- **SC-009**: Static analysis stays green — `lintDebug` zero warnings, `detekt` baseline UNCHANGED from Spec 008, `ktlintCheck` zero violations.
- **SC-010**: Constitution Check 11/11 PASS pre and post implementation.

## Assumptions

- **A1**: Address bar is implemented as a single-line `TextField`-style Composable in the existing `presentation/browser/` package using Material3 components already on the classpath (Compose BOM 2026.04.01). No new packages are introduced. If Material3's `SearchBar` is found preferable in `/speckit-plan` research, the choice will still come from the BOM-managed Compose surface — no new artifact.
- **A2**: The Google search URL template is already declared as a `const val` in `core/constants/UrlConstants.kt` per the Spec 008 / project structure documentation. This spec uses the existing constant inline; Spec 010 (`search-engine-google`) will introduce a `SearchEngineRepository` abstraction and may relocate or supplement the constant.
- **A3**: The existing `BrowserScreen` Scaffold from Spec 008 has a free top slot or can accept a `topBar` parameter without restructuring. If structural changes to the Scaffold are required, they remain trivial (additive, no Spec 008 regression). This is verified during `/speckit-plan`.
- **A4**: The existing `BrowserViewModel` from Specs 007/008 is the correct host for the new submit / sync methods. No new ViewModel is introduced.
- **A5**: Existing `WebViewActionsHandle` (Spec 008) provides — or can be extended with — a `loadUrl(String)` action that the address-bar submit path calls. This handle keeps the raw `WebView` reference out of the ViewModel per Constitution §IV.
- **A6**: URL classification heuristic (`contains "://" or "."`) is intentionally simple. Edge cases like `localhost` (no dot) being misclassified as query are acceptable for v1 of a consumer mobile browser.
- **A7**: The placeholder string and clear-button content description are new resource keys; they will be added to all 8 locale `strings.xml` files. Estimated 2 new keys × 8 locales = 16 new translations.
- **A8**: Suggestions, history-driven autocomplete, recent searches, voice input, and clipboard auto-paste are all explicitly OUT OF SCOPE per the user's incremental-scope preference and will be addressed in a later spec after Specs 013 (bookmarks) and 014 (history) provide the data UI.
- **A9**: The "back-during-focus closes keyboard before consuming for predictive back" behavior is achievable via standard Compose `BackHandler` ordering or a custom focus-aware handler; concrete approach is decided in `/speckit-plan`. Spec 008's `PredictiveBackHandler` enabled-flag wiring is preserved unchanged.
- **A10**: Test coverage approach mirrors Spec 008: a small set of pure-unit tests for URL/query classification + URL-encoding + scheme-prefix logic, plus a Compose UI test class for the address-bar component (analogous to `NavigationBottomBarTest`). Full instrumented integration tests for the address bar interacting with a real WebView are likely deferred to a later instrumented test pass per the precedent established by Specs 007/008.
