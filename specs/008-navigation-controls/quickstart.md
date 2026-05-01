# Quickstart: Verify Spec 008 — Navigation Controls

> Run these gates locally + on device after `/speckit-implement` completes. Mirrors the Spec 007 quickstart pattern (automated gates → manual UX gate).

## Prerequisites

- Branch: `008-navigation-controls`
- Working tree clean OR all Spec 008 changes staged
- Android emulator OR connected device, API 24+
- For Gate 7 (predictive back animation): physical Android 14+ (API 34+) device

## Automated gates (must all pass before PR)

### Gate 1 — Build

```bash
./gradlew clean assembleDebug
```

Expected: SUCCESS, zero warnings.

### Gate 2 — Unit tests

```bash
./gradlew testDebugUnitTest
```

Expected: SUCCESS. Spec 007 baseline = 94 tests; Spec 008 adds:
- `BrowserViewModelTest` — new tests for `onCanGoBackChanged`, `onCanGoForwardChanged`, state derivation, `BrowserUiState` defaults
- (Possibly) `NavigationBottomBarSemanticTest` — Compose state-helper tests if any pure logic is extracted

Target: ≥ 100 unit tests after Spec 008 lands.

### Gate 3 — Lint (with translation completeness)

```bash
./gradlew lintDebug
```

Expected: SUCCESS, zero warnings. The Spec 004 lint config (`MissingTranslation` + `ExtraTranslation` at error severity) MUST flag if any of the 5 new `browser_action_*` keys are missing from any of the 8 locale files.

### Gate 4 — Static analysis

```bash
./gradlew detekt ktlintCheck
```

Expected: SUCCESS, zero violations, **detekt baseline UNCHANGED** from Spec 007. Specifically:
- `LongParameterList.functionThreshold = 6` — `BrowserWebViewCallbacks` now has 6 fields, exactly at threshold (still OK).
- `MagicNumber` — no new magic numbers; `AppDefaults.HOME_URL` is a `const val` String.

### Gate 5 — Release build + 16KB CI gate

```bash
./gradlew assembleRelease
.specify/scripts/bash/verify-16kb-alignment.sh
```

Expected:
- Release APK builds.
- 16KB script: all native lib entries `align=0x4000`. Spec 008 introduces zero new `.so`, so the entry count remains at 24 (same as Spec 007 baseline).
- APK size: ≤ ~1.62 MB (Spec 007 baseline 1.61 MB + ~7 KB expected from R9). SC-008 budget is ≤ 200 KB delta — easy pass.

### Gate 6 — Instrumented tests (emulator/device required)

```bash
./gradlew connectedDebugAndroidTest
```

Expected: SUCCESS. Spec 008 adds:
- `NavigationBottomBarTest` — verifies bottom-bar renders all 4 affordances; disabled state when `canGoBack=false`; Reload↔Stop semantic toggle when `LoadingState` transitions; Home click triggers home URL load.
- `BrowserScreenBackGestureTest` — Espresso `pressBack()` on screen with prior history navigates back; on root entry exits screen.

Existing Spec 007 instrumented tests (`BrowserScreenHappyPathTest`, `BrowserScreenOfflineErrorTest`, `BrowserScreenRotationTest`) MUST continue to pass — Spec 008's bottom-bar wrapping inside `Scaffold` should not change rotation / happy-path / offline-error semantics.

## Manual UX gate (deferred to user device verification)

> Mirrors the T042 pattern from Spec 007 / Gates 3-7 from Spec 004 / Gate 8 from Spec 006. These checks cannot be reliably automated.

### Gate 7 — Visual & interaction verification (Android 14+ device required for predictive)

Cold-start the app and verify:

- [ ] **Bottom bar renders** with 4 affordances in left-to-right order: **Back · Forward · Reload/Stop · Home**.
- [ ] **Initial state**: Back disabled, Forward disabled, Reload visible (not Stop), Home enabled.
- [ ] **First-load**: Bottom bar appears immediately on cold start (no flash, no layout shift). Status bar + navigation bar insets handled correctly (bar respects gesture-nav inset on devices with gesture nav).
- [ ] **Tap Home**: Loads `https://www.google.com/`. URL display updates. Loading indicator (Spec 007) shows then completes.
- [ ] **Tap a Google search-result link**: Navigates to result page. Back becomes enabled (immediately after page commit). Forward remains disabled.
- [ ] **Tap Back**: Returns to Google homepage. Forward becomes enabled. Back becomes disabled.
- [ ] **Tap Forward**: Returns to result page. Back enabled, Forward disabled.
- [ ] **Mid-load tap Stop**: Reload icon transforms to Stop while `Loading`. Tapping Stop cancels the load. Page settles.
- [ ] **Idle tap Reload**: Stop icon transforms back to Reload after Loaded. Tapping Reload re-fetches.
- [ ] **Long URL with redirects**: `canGoBack` updates correctly after each redirect commits (use a known multi-hop redirect like `https://t.co/...` or a self-referential test page).
- [ ] **Rotation mid-load**: Rotate device while loading — bottom bar persists, affordance states correct after rotation, no flash.
- [ ] **8-locale sweep**: Switch device language across EN, VI, DE, RU, KO, JA, ZH, FR (Android 13+ per-app picker). For each locale, long-press each affordance to verify TalkBack content description is in the active locale.

### Gate 7a — Predictive back (Android 14+ only)

- [ ] **Multi-step back gesture**: Navigate A → B → C. Begin a slow edge-swipe back from C. **Verify the system shows a preview of B during the gesture** (not just a black or current-page frame). Release to commit → land on B. Begin another swipe, but cancel mid-gesture → C remains current, no navigation.
- [ ] **Edge case**: At root entry (history empty), perform back gesture → app exits to launcher (no in-app preview, system standard exit animation).

### Gate 7b — Pre-Android-14 fallback

- [ ] **API 24–33 device**: Same back semantics work (back navigates history, then exits) but WITHOUT the predictive preview animation. Verify no crash, no broken state.

### Gate 7c — Disabled-state accessibility (TalkBack)

- [ ] **TalkBack on**: Tap a disabled Back button. TalkBack announces something like "Back, button, dimmed" (or locale equivalent) — confirms disabled state is communicated to assistive tech.

## Performance verification

- [ ] **SC-002**: System back gesture commit-to-frame ≤ 300 ms on Pixel 5+ (visual estimate; precise measurement via `dumpsys gfxinfo` is overkill for v1.0).
- [ ] **SC-003**: Predictive preview begins within 100 ms of gesture start (perceived as immediate).
- [ ] **SC-004**: Reload begins ≤ 200 ms; Stop cancels ≤ 200 ms (both perceived as immediate).
- [ ] **60fps**: No janky animation when scrolling content above the bar; the bar itself never recomposes during scroll (it should only recompose on `canGoBack`/`canGoForward`/`loadingState` changes).

## Failure-mode verification

- [ ] **Offline + Home tap**: Toggle airplane mode. Tap Home. Spec 007 `LoadingState.Failed(NetworkUnavailable)` UI appears with localized error string. Bottom bar remains visible. Reload affordance is actionable (re-attempt Home URL once airplane mode is toggled off).
- [ ] **Rapid taps**: Hammer Back 10× rapidly while history depth is 3 — never crash, never state desync. Final state: at root, Back disabled, Forward enabled.

## Sign-off

When all gates above pass:

1. Mark `tasks.md` complete (every task ✅).
2. Update `CLAUDE.md` Recent Changes with Spec 008 summary (test counts, APK size, 16KB result, manual gate status).
3. Move Spec 008 status in `sdd-roadmap.md` from 🔄 Specified → ✅ Done.
4. Open PR.
5. After merge: update `MEMORY.md` if any non-obvious decision was made (e.g., a quirk discovered at impl time).
