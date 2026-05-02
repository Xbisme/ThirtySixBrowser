# Quickstart — Address Bar / Omnibox (Spec 009)

**Feature**: Address Bar / Omnibox (Spec 009)
**Date**: 2026-05-02
**Use this doc**: After implementation, walk through these gates in order. Each gate maps to one or more SC / FR. A spec is "ready to merge" only when every gate (except the explicitly user-deferred ones) is green.

## Pre-merge automated gates

Run all of these from a clean checkout of the `009-address-bar-omnibox` branch.

### Gate 1 — Build clean

```bash
./gradlew clean assembleDebug
```

**Expected**: BUILD SUCCESSFUL, zero warnings.

### Gate 2 — Unit tests pass

```bash
./gradlew testDebugUnitTest
```

**Expected**: BUILD SUCCESSFUL, all tests pass. Spec 008 baseline = 102 tests; Spec 009 should add at least:

- `AddressBarInputClassifierTest` — ~12 tests covering URL with scheme, URL without scheme, URL with path/query, IP address, single-word query, multi-word query, uppercase scheme, leading whitespace, embedded newline, empty input, whitespace-only input, mixed-bag false-positive (`kotlin.coroutines` → URL).
- `BrowserViewModelTest` (extending Spec 008's class) — ~7 new tests for the 5 new ViewModel methods + the `onLoadStarted` responsibility-narrowing test pair.
- `HostnameExtractionTest` (or `UrlExtensionsTest`) — ~6 tests covering host present, host absent (`about:blank`), host absent (`file:///`), malformed URL, empty input, IPv4 host.

Total target: **127+ unit tests pass**.

### Gate 3 — Static analysis green

```bash
./gradlew lintDebug detekt ktlintCheck
```

**Expected**:
- `lintDebug`: zero warnings.
- `detekt`: zero violations, baseline file UNCHANGED (no new entries; the callback-bundle refactor must satisfy `LongParameterList` without baseline cover).
- `ktlintCheck`: zero violations.

### Gate 4 — Component-level Compose UI test

```bash
./gradlew :app:connectedDebugAndroidTest --tests \
  com.raumanian.thirtysix.browser.presentation.browser.components.AddressBarTest
```

**Expected**: BUILD SUCCESSFUL. Tests cover at minimum:
1. Initial render shows placeholder hint when no URL is loaded (FR-017).
2. Initial render shows hostname-only when a URL with a host is loaded (FR-014).
3. Tapping the bar shifts to full URL display + selects all text (FR-015 / FR-018).
4. Clear button is invisible when bar is unfocused (FR-023).
5. Clear button is invisible when bar is focused but empty (FR-024).
6. Clear button appears when bar is focused with text and tapping it empties the field while keeping focus (FR-020 / FR-021).
7. IME submit fires the `onSubmit` callback exactly once for non-empty input.
8. IME submit on empty input does NOT fire `onSubmit` (FR-012 / FR-013a).
9. Long pasted multiline string is collapsed to spaces before submit-classification (FR-007 — assertion may live in unit test instead).
10. Localized hint text renders correctly (smoke test in EN; full 8-locale verification deferred to Gate 7).

### Gate 5 — Release APK builds + 16 KB alignment

```bash
./gradlew assembleRelease
.specify/scripts/bash/verify-16kb-alignment.sh
```

**Expected**:
- `app-release.apk` builds.
- 16 KB alignment script reports every `lib/*/lib*.so` aligned `0x4000` or larger. Spec 009 introduces zero new `.so`, so the entry count should equal Spec 008's baseline (28/28).
- APK release size: ≤ 1.72 MB (Spec 008 baseline 1.67 MB + SC-007 budget 50 KB).

### Gate 6 — Constitution Check

Open [plan.md Constitution Check](plan.md#constitution-check) and confirm 11/11 PASS post-implementation.

## Manual user-device gates (DEFERRED — mirrors Spec 008 T032 / T049 pattern)

These cannot be run in CI / by an automated agent. The implementing developer or user runs them on a real Android 14+ device after the automated gates above are green.

### Gate 7 — UX smoke on device

1. **Cold-start hint**: Launch the app from the launcher. The address bar at the top is visible, shows the localized placeholder, **does not have focus**, and the soft keyboard is **not** open (FR-025).
2. **Hostname display**: After the home page (`https://www.google.com/`) loads, the address bar shows `www.google.com` (FR-014). (No `www.` stripping per [research R8](research.md#r4--hostname-extraction-for-unfocused-display-fr-014--fr-016).)
3. **Tap to focus**: Tap the address bar. The display switches to the full URL with all text selected (FR-018), the soft keyboard opens, the trailing clear button (X) appears.
4. **Clear button**: Tap the X. The field empties; focus stays; the keyboard stays open (FR-021).
5. **URL submit (with scheme)**: Type `https://example.com` and press the keyboard's Go action. The bar dismisses focus + keyboard (FR-013a), the WebView begins loading, the bar shows `https://example.com` during load (Q1), and `example.com` once load completes (FR-014).
6. **URL submit (without scheme)**: Tap the bar, type `developer.android.com`, submit. The bar dismisses, the WebView loads with `https://` prepended (FR-009).
7. **Query submit**: Tap the bar, type `kotlin coroutines`, submit. The bar dismisses, the WebView loads a Google search results page, the bar shows `www.google.com` once the result page loads.
8. **Live URL trace during redirect** (cross-domain): Submit a URL with a known cross-domain redirect (e.g., a `bit.ly` short link or a known HTTP-301 endpoint). Confirm the address bar visibly changes hostname mid-navigation (FR-019b).
9. **Sync on link click**: From the loaded Google results page, tap a result link. Confirm the address bar updates to the new hostname automatically without the user touching the bar (FR-019).
10. **Back gesture during focus**: Tap the bar to focus it (keyboard up). Press the system back gesture. Confirm: keyboard dismisses + focus released; a SECOND back press then triggers Spec 008's `PredictiveBackHandler` (preview animation if `canGoBack`) (FR-026).
11. **Rotation preserves text + focus**: Tap the bar, type `partial input`, rotate the device. Confirm `partial input` is still in the field and the field is still focused (FR-027).
12. **Empty submit no-op**: Focus the bar, ensure it is empty (clear if needed), tap Go on the keyboard. Confirm: nothing navigates, the bar stays focused, the keyboard stays open (FR-012 / FR-013a).
13. **TalkBack sweep** (≥ EN + 1 non-Latin locale, e.g., JA): Enable TalkBack, navigate to the BrowserScreen. Confirm the address bar is announced with its localized label, the clear button has its own localized content description, and the IME submit action is reachable via swipe gestures (SC-006).
14. **8-locale visual sweep** (deferrable): Switch system language to each of the 8 supported locales in turn (EN/VI/DE/RU/KO/JA/ZH/FR), reload the BrowserScreen, confirm the placeholder hint and the clear button content description render in the locale's typography without clipping (SC-005). May be deferred to a single user-device pass; not a CI-blocking gate.

### Gate 8 — Performance spot-check (manual)

1. Cold-start time-to-interactive on Pixel 5+ should remain ≤ 5 s (Spec 007 SC carry-over).
2. Submit-to-WebView-load-start delay should be ≤ 1 s on Pixel 5+ (SC-001) — visually verify by submitting a known-fast URL (`https://example.com`) and confirming the WebView begins navigating "instantly".
3. Address bar focus animation should be 60 fps (no jank when expanding to full URL display).

## Test failure flowchart

| Failure mode | First-line check |
|---|---|
| `lintDebug` fails on `MissingTranslation` | Confirm all 8 locale `strings.xml` files received the 2 new keys. |
| `detekt` fails on `LongParameterList` for `BrowserWebView(...)` | The callback-bundle refactor must split into `BrowserWebViewCallbacks` (4 fields) + `BrowserNavigationCallbacks` (3 fields). Confirm the Composable signature accepts both as separate `internal data class` parameters. |
| `AddressBarInputClassifierTest` fails on `kotlin.coroutines` | Spec accepts this as a known false-positive (URL classification per heuristic). The test should assert `Url("https://kotlin.coroutines")` not `Query`. |
| `AddressBarTest` fails on the IME submit assertion | Confirm `keyboardActions = KeyboardActions(onGo = { … })` plus `keyboardOptions.imeAction = ImeAction.Go`. |
| 16 KB alignment script reports a new `.so` | Spec 009 should NOT introduce any. Audit `gradle/libs.versions.toml` for accidental new dependency. |

## Out-of-scope reminder

The following are **deliberately not part of Spec 009** and any test failure relating to them is misattributed:

- Suggestions / autocomplete from history or bookmarks (FR-028 — deferred).
- Voice input (FR-029).
- User-selectable search engines (FR-030 — Spec 010).
- Schemes other than `https://` prepended for schemeless URLs (FR-031).
- Address-bar persistence across process death (data-model.md §Persistence).

If a test asserts behavior in any of the above, it is testing a future spec.
