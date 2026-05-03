# Quickstart: Tabs Management (Spec 011)

**Date**: 2026-05-03 | **Branch**: `011-tabs-management`

This is the verification checklist for Spec 011. Run the automated gates locally + verify the 5 manual user-device gates on a real Android 13+ device before marking the spec complete.

---

## Automated gates (CI + local)

| # | Gate | Command | Pass criteria |
|---|------|---------|---------------|
| G1 | Unit tests pass | `./gradlew testDebugUnitTest` | All 162 prior tests + ~25 new tests = ~187 pass |
| G2 | Lint clean | `./gradlew lintDebug` | Zero warnings, zero errors |
| G3 | Detekt clean | `./gradlew detekt` | Zero new violations; baseline UNCHANGED from Spec 010 |
| G4 | ktlint clean | `./gradlew ktlintCheck` | Zero violations |
| G5 | Release build | `./gradlew assembleRelease` | Build succeeds; APK delta ≤ +200 KB vs Spec 010 baseline (2.0 MB) → ≤ 2.2 MB |
| G6 | 16KB alignment | `./gradlew assembleRelease` + verify-16kb script | Every native-lib entry aligned `0x4000`; zero new `.so` |
| G7 | Instrumented tests pass | `./gradlew connectedDebugAndroidTest` | `TabSwitcherFlowTest`, `TabPersistenceProcessDeathTest`, `MaxTabsLimitTest` + existing Spec 008/009 component tests pass |

---

## Manual user-device gates (5)

These mirror the deferred-then-verified pattern from Specs 008 (T032 / T049) and 010 (T017 / T023 / T026 / T033 / T034). Run on a real Android 13+ device — emulator is acceptable but a real device gives a higher-fidelity check on the WebView memory profile (FR-027 / R8 trade-off) and on long-press haptic feedback (Q2 / R6).

### Gate M1 — US1 multi-tab switcher round-trip (P1 headline)

1. Force-stop the app from system Settings → Apps so we start fresh.
2. Launch the app — verify a single home tab is visible (`https://www.google.com/`) with the address bar showing the URL.
3. Long-press the **5th BottomAppBar button** (the tabs switcher button, rightmost). Verify haptic feedback fires AND a fresh home tab opens (still on `https://www.google.com/`) — the switcher does NOT open. This is the Q2 long-press shortcut.
4. Type a different URL in the address bar (e.g., `example.org`), submit. Verify the tab loads to example.org.
5. Single-tap the 5th BottomAppBar button. Verify the tabs switcher screen opens.
6. Verify the switcher shows **2 cards**: one for example.org (active — visually distinct, e.g., outlined or accent-tinted), one for the home page.
7. Tap the home page card. Verify the switcher closes AND the BrowserScreen now shows the home page with the address bar reflecting `https://www.google.com/`.
8. Single-tap the 5th BottomAppBar button again. Tap the example.org card. Verify the same round-trip works in reverse.

Expected: Tab switching is < 100ms perceived; address bar URL matches the switched-to tab's URL; bottom-bar tab-count badge shows "2" throughout.

### Gate M2 — US2 process-death restoration (P1 value prop)

1. Open the app with at least 3 tabs (use the long-press shortcut from M1 to create more tabs and the address bar to navigate each to distinct URLs — e.g., `wikipedia.org`, `news.ycombinator.com`, `developer.android.com`).
2. Make the **second** tab the active one (open the switcher → tap the second card).
3. Force-stop the app from system Settings → Apps. Confirm the kill.
4. Re-launch the app from the launcher icon.
5. Verify all 3 tabs are restored (open the switcher to confirm — 3 cards present).
6. Verify the second tab is the active one (BrowserScreen shows its URL in the address bar).
7. The tab order in the switcher MUST match the most-recently-active-first order (the tab that was active on kill is at the top).

Expected: Restoration completes within 500 ms perceived (SC-010 budget); no visual freeze on launch; the active tab's WebView begins loading immediately while inactive tab cards hydrate in the switcher.

### Gate M3 — US3 close + close-all (P2 hygiene)

1. Open the app with at least 3 tabs.
2. Open the switcher. Tap × on a non-active tab card. Verify the card disappears, the switcher remains open, the active tab is unchanged.
3. Tap × on the active tab card. Verify the card disappears, the next-most-recently-active tab becomes active (visual indicator moves to it), the switcher remains open showing 1 fewer card.
4. Now reduce to exactly 1 tab. Tap × on it. Verify a fresh home tab is auto-created (always ≥ 1 tab), the switcher closes (or stays open showing 1 fresh home card — whichever the implementation chose), and the BrowserScreen shows the home URL.
5. Open at least 3 tabs again. Open the switcher. Tap "Close all tabs". Verify a confirmation dialog appears with localized text.
6. Confirm. Verify all tabs are wiped, exactly 1 fresh home tab is auto-created, the switcher closes, and the BrowserScreen shows the home URL.
7. Tap "Close all tabs" again, then tap **Cancel** on the dialog. Verify nothing is wiped — all tabs remain.

Expected: All operations are durable — force-stop + relaunch after a close does NOT bring closed tabs back (mini variant of M2).

### Gate M4 — SC-004 max-tabs limit + 8-locale message

1. Programmatically (or via repeated long-press) open tabs until you reach `BrowserLimits.MAX_TABS = 50`. The 5th BottomAppBar button's count badge MUST show "50".
2. Long-press the 5th button. Verify a localized snackbar appears with a "max tabs reached" message AND the count remains 50 (no new tab created).
3. Switch the device locale to each of the other 7 supported locales (VI, DE, RU, KO, JA, ZH, FR) — re-launch the app, verify the message is properly translated in each locale.
4. Inside the switcher, tap "new tab" card while at the cap → same localized message surfaces; count remains 50.
5. Close one tab → re-attempt the long-press → tab is created (count becomes 50 again).

Expected: Message is identical in semantics across all 8 locales; the affordance is visually disabled (not just message-on-tap) — the long-pressed button shows a disabled state (e.g., reduced alpha) when tabCount == MAX_TABS.

### Gate M5 — SC-010 cold-start with 50 tabs (perf)

1. Open 50 tabs (via repeated long-press from M4).
2. Force-stop the app. Wait 5 seconds.
3. Re-launch from the launcher icon. Start a stopwatch on tap.
4. Stop when the BrowserScreen renders the address bar + content area (active tab's WebView begins loading).
5. Verify perceived time ≤ 500 ms (SC-010 budget on Pixel 5+-class device).
6. Open the switcher. Verify the grid renders all 50 cards within ≤ 200 ms (SC-005 budget). Scroll the grid — must be 60fps.
7. Verify the cold-start did NOT pre-load any inactive tab's WebView (memory check via `adb shell dumpsys meminfo com.raumanian.thirtysix.browser` — total native heap should be near baseline + 1 WebView's worth, NOT 50 × WebView).

Expected: Single active WebView memory profile holds even with 50 persisted tabs; switcher is responsive at the upper bound; cold-start budget is met on mid-range hardware.

---

## Constitution gates (re-verify post-impl)

| # | Principle | Method |
|---|-----------|--------|
| I | Privacy & Security First | `git diff main...011-tabs-management` — verify no analytics, no logging, no network surface beyond Spec 005's existing Room writes |
| III | No-Hardcode Rule | grep red flags: `Color(0x`, inline `.dp)` numbers (≥ 2), inline `Text("...")`, `delay(`, `withTimeout(`, `"http`, `stringPreferencesKey("` — all should reference constants/theme/resources |
| IV | Clean Architecture | grep `Repository → Repository`: `find app/src/main/kotlin/com/raumanian/thirtysix/browser/data/repository -name '*.kt' -exec grep -l 'Repository' {} \;` — only `TabRepositoryImpl` should appear, depending on `TabDao` not other repositories |
| VIII | Localization | confirm 6 new keys present in all 8 locale `strings.xml` (lint `MissingTranslation` error gate from Spec 004 catches this on build) |
| IX | 16KB | run `verify-16kb-alignment.sh` script — zero new `.so`, all entries `0x4000` |

---

## Quick verification one-liner (for PR description)

```bash
./gradlew clean testDebugUnitTest lintDebug detekt ktlintCheck assembleRelease && \
  echo "=== Spec 011 automated gates G1-G6 PASS ===" && \
  ls -lh app/build/outputs/apk/release/app-release.apk
```

Manual gates M1–M5 require a real Android 13+ device with the APK side-loaded (`./gradlew installDebug` on a connected device).

---

## Dependencies on prior specs (verification)

Spec 011 depends on these prior-spec assets being unchanged:

- **Spec 005**: `TabEntity` schema (no migration), `TabDao` API. **Verify**: `git diff main app/src/main/kotlin/com/raumanian/thirtysix/browser/data/local/entity/TabEntity.kt` — should be empty.
- **Spec 006**: `SettingsRepository` is NOT touched (active-tab pointer is in Room, not DataStore).
- **Spec 007**: `BrowserWebView` factory + WebView lockdown settings — preserved per FR-028. The single-active-WebView strategy from R8 means the lockdown re-applies on every fresh tab activation.
- **Spec 008**: `NavigationBottomBar` 4-button bar + `PredictiveBackHandler` — extended (5th button + bundle expansion) per R6, NOT replaced.
- **Spec 009**: `AddressBar` + `AddressBarInputClassifier` — completely unchanged. Address bar continues to display the active tab's URL via `BrowserUiState.currentUrl`.
- **Spec 010**: `BuildSearchUrlUseCase` query path — unchanged. The query path inside `onAddressBarSubmit` continues to wrap in `viewModelScope.launch { val url = buildSearchUrl(...); loadUrl(url) }`.
