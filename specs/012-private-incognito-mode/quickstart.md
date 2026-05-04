# Quickstart: Verify Spec 012 — Private / Incognito Mode

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

This is the gate sequence to verify Spec 012 is "Done." Mirrors the Spec 011 quickstart pattern: build/test/static-analysis/16KB CI gate (automated) + 5 manual user-device gates.

## 1. Automated gates (run locally + on CI)

| # | Command | Pass criteria |
|---|---------|---------------|
| 1 | `./gradlew assembleDebug` | Build succeeds, no compile errors |
| 2 | `./gradlew testDebugUnitTest` | All unit tests pass — Spec 011 baseline 201 + ≥ 18 new (8 IncognitoTabRepositoryImpl + 8 CookieJarSnapshotManagerImpl + 2 BrowserViewModel cache-gating tests) ≈ **219+ total** |
| 3 | `./gradlew connectedDebugAndroidTest` | Instrumented sweep green on emulator API 29+ — includes IncognitoStressInstrumentedTest (SC-005), CookieRestoreInstrumentedTest, FlagSecureLifecycleInstrumentedTest, IncognitoSwitcherCardInstrumentedTest |
| 4 | `./gradlew lintDebug` | Zero warnings, zero errors |
| 5 | `./gradlew detekt` | Zero violations, baseline UNCHANGED from Spec 011 |
| 6 | `./gradlew ktlintCheck` | Zero violations |
| 7 | `./gradlew assembleRelease` | Build succeeds; APK size delta ≤ +100 KB vs Spec 011 baseline 2.1 MB (target ≤ 2.2 MB) |
| 8 | 16KB native lib alignment script (CI job `verify-16kb`) | All native lib entries `align=0x4000` (zero new `.so` expected) |
| 9 | Constitution Check 11/11 PASS | Documented in plan.md — re-verified post-implementation |

## 2. Manual user-device gates (run on real Android device)

Run after the automated gates pass on CI. Each gate corresponds to a concrete user-facing behaviour the automated suite cannot fully verify.

### Gate G1 — US1 + US2: Open + browse + close-last-incognito wipes session (P1)

1. Fresh install (or `adb shell pm clear com.raumanian.thirtysix.browser`).
2. Open the app, open the tab switcher, tap **"New incognito tab"**.
3. Verify visual differentiation: incognito card colour distinct from normal cards; incognito glyph in title row.
4. Type a URL into the address bar (e.g. `https://httpbin.org/cookies/set?test=value`) and submit.
5. Wait for page load. Open the History screen — verify NO entry for `httpbin.org`.
6. Return to the incognito tab. Tap address bar; verify NO autocomplete suggestion for the just-visited URL.
7. Close the incognito tab. Open a normal tab. Visit `https://httpbin.org/cookies` — verify the cookie `test=value` is NOT present in the response (it was wiped on close).

**Pass**: all 7 steps behave as described.

### Gate G2 — US3: Process death erases incognito tabs, normal tabs restore (P1)

1. Open 3 normal tabs (3 different URLs).
2. Open 2 incognito tabs (2 different URLs).
3. Switch to a normal tab as the active tab.
4. Force-stop the app: **Settings → Apps → ThirtySix Browser → Force Stop**.
5. Re-launch the app.
6. Open the tab switcher.

**Pass**: exactly 3 tabs visible, all of them normal (no incognito glyph anywhere), cookies set during incognito browsing are gone (verify via httpbin.org/cookies as in G1), and the app did not crash on launch.

### Gate G3 — US4 + FLAG_SECURE: Visual differentiation + recents thumbnail blank (P2)

1. Open one incognito tab; load any visually-rich page (e.g. `https://en.wikipedia.org`).
2. Open the system "recents" overview (gesture or button).

**Pass**: the recents thumbnail for ThirtySix Browser is BLANK (system-default placeholder) — no Wikipedia content visible.

3. Try to take a manual screenshot (Power+VolDown).

**Pass**: system blocks the screenshot with a "screenshot disabled" toast.

4. Switch to a normal tab. Repeat the recents check.

**Pass**: recents thumbnail now shows the normal-tab page content (FLAG_SECURE was cleared).

5. **(SC-010 visual-identification timed step)** Open the tab switcher with a mix of 3 normal + 2 incognito tabs. Hand the device to someone unfamiliar with the feature; ask them to point at incognito tabs.

**Pass**: identification completes in **< 2 seconds** (SC-010). If the rater hesitates > 2 s, the visual differentiation is too subtle — file a UX-polish ticket and revisit `MaterialTheme.colorScheme` token choice in T051.

### Gate G4 — US5: Close all incognito + bulk wipe (P2)

1. Open 4 normal + 3 incognito tabs.
2. Open the switcher; verify **"Close all incognito"** affordance is visible.
3. Tap "Close all incognito"; confirm the dialog.

**Pass**: exactly 4 tabs remain (all normal), affordance is no longer visible (incognitoTabCount == 0), and visiting httpbin.org/cookies in a normal tab shows no incognito-set cookies.

4. Open the switcher with no incognito tabs open.

**Pass**: the "Close all incognito" affordance is NOT visible.

### Gate G5 — Stress / crash-resilience (SC-005)

This gate is run as **instrumented test** (`IncognitoStressInstrumentedTest`) in the automated suite, but also verified manually as a smoke test on a low-end device:

1. On a 2 GB emulator (`-memory 2048`) running API 35 with 16 KB page size.
2. Manually open + close an incognito tab 20 times in a row (informal stress).

**Pass**: no crash, no ANR, no visible memory pressure (Android system "low memory" toast does not appear).

### Gate G6 — US4 + US5: 8-locale visual sweep of incognito strings (SC-007)

`./gradlew lintDebug` (`MissingTranslation` + `ExtraTranslation` enforced) verifies completeness — every key exists in every locale. This manual gate verifies **visual correctness** (truncation, ellipsis, layout overflow) that lint cannot catch. Mirrors the Spec 004 / Spec 011 8-locale visual-sweep pattern.

For each of the 8 supported locales (EN, VI, DE, RU, KO, JA, ZH, FR):

1. Switch the device locale via **Settings → System → Languages & input → Languages → Add a language → drag to top**, or via the per-app picker on Android 13+ (**Settings → Apps → ThirtySix Browser → Language**).
2. Re-launch the app.
3. Open the tab switcher.
   - Verify the **"New incognito tab"** affordance label renders without truncation.
   - Verify the **incognito card placeholder label** ("Incognito" / equivalent) renders without truncation.
4. Tap "New incognito tab"; navigate to any URL.
   - Verify the **incognito indicator** label / a11y description beside the address bar renders without truncation.
5. Open the tab switcher; tap **"Close all incognito"**.
   - Verify the affordance label and the confirmation dialog body (`tabs_close_all_incognito_confirm`) render without truncation, with the count substitution working correctly (e.g. for VI: "Đóng tất cả 3 tab ẩn danh?").
6. (If applicable) Trigger the **max-incognito-tabs reached** error by opening 51 incognito tabs.
   - Verify the localized error message renders without truncation.

**Pass criteria** (all of):

- [ ] EN (baseline): no string truncation, no fallback, no raw-key tokens.
- [ ] VI: same.
- [ ] DE: same — note German compound nouns are typically the longest; pay extra attention to `tabs_action_close_all_incognito`.
- [ ] RU: same — Cyrillic glyphs render correctly (no � placeholders).
- [ ] KO: same — Hangul renders correctly.
- [ ] JA: same — Japanese renders correctly (mixed kana + kanji).
- [ ] ZH: same — Simplified Chinese renders correctly.
- [ ] FR: same — French diacritics render correctly.

If ANY locale fails, file a fix in T031/T054/T063 (or the locale's `strings.xml` directly) before marking T077a complete.

## 3. Constitution gate re-check (post-implementation)

Walk through Constitution v1.2.0 §I–§XI. Document any deviations in plan.md Complexity Tracking.

**Expected result**: 11/11 PASS with one documented exception (use-case-coordination in `CloseIncognitoTabUseCase` per plan.md §IV row).

## 4. PR open

```bash
gh pr create --base main --head 012-private-incognito-mode \
  --title "feat: Implement Private / Incognito Mode (Spec 012)" \
  --body "..."
```

PR body MUST link to plan.md Complexity Tracking row for the use-case-coordination exception (continues the Spec 010/011 ack pattern).
