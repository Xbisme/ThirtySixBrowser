# Quickstart — Spec 007 Verification Gates

**Date**: 2026-05-01
**Branch**: `007-webview-compose-wrapper`

This document is the merge-readiness checklist for Spec 007. Every gate MUST pass before the PR is approved. Gates 1–6 run in CI; Gate 7 runs locally on a real Android 13+ device (per the Spec 004 manual-gate pattern); Gate 8 is the Constitution §IX 16 KB verification (also in CI).

---

## Gate 1 — Build (debug + release)

```bash
./gradlew clean assembleDebug assembleRelease --stacktrace
```

**Pass criteria**:
- Both APKs produced.
- Release APK size delta vs Spec 006 baseline (1.56 MB) is ≤ 200 KB (SC-008).
- Build log contains the `release built with DEBUG signature` warning when no release keystore is configured (Constitution §XI v1.2.0).

---

## Gate 2 — Unit tests

```bash
./gradlew testDebugUnitTest
```

**Pass criteria**:
- 100% pass.
- Project total = Spec 006 baseline (79) + new Spec 007 unit tests (`BrowserViewModelTest`, `ErrorReasonTest`). Expected ≥ 88 tests.
- Coverage of `presentation/browser/{BrowserViewModel, ErrorReason}` ≥ 70% line coverage (Constitution §VI).

---

## Gate 3 — Instrumented test on emulator (RE-ENABLED in this spec)

```bash
./gradlew connectedDebugAndroidTest
```

**Pass criteria**:
- `BrowserScreenInstrumentedTest` passes on emulator API 29 (the matrix value pinned in `.github/workflows/ci.yml`).
- The Espresso-Web atom asserts the loaded DOM contains the text "Example Domain" (FR-012, SC-005).
- CI's `instrumented-test` job is GREEN (re-enabled in this branch — uncommitted edit on `.github/workflows/ci.yml` ships with this PR).

> If the job hangs the way it did 2026-04-30, the workaround already in `.github/workflows/ci.yml` (lines 238–246: explicit `adb emu kill` + `pkill -9 -f qemu-system`) MUST kick in. If it doesn't, treat that as the discovery that this spec needs an additional plan iteration — NOT a reason to disable the job again.

---

## Gate 4 — Static analysis (Lint + Detekt + ktlint)

```bash
./gradlew lintDebug detekt ktlintCheck
```

**Pass criteria**:
- `lintDebug`: 0 warnings, 0 errors. `MissingTranslation` is treated as **error** (Spec 004 + Constitution §VIII) — adding any of the 4 new string keys to a locale's `strings.xml` while leaving another locale missing it → build fails.
- `detekt`: 0 violations. `MagicNumber` continues to gate; new code introduces zero numeric literals outside the trivial whitelist or the `@Suppress("MagicNumber")` rationale comments.
- `ktlintCheck`: 0 violations. The `class-signature` rule remains disabled per Spec 006's `.editorconfig` decision.

---

## Gate 5 — No-Hardcode grep audits (Constitution §III)

```bash
# 5a — No hardcoded URL literals in presentation/ or domain/
grep -REn '"https?://' app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/ \
                       app/src/main/kotlin/com/raumanian/thirtysix/browser/domain/
# Expected output: empty
```

```bash
# 5b — No addJavascriptInterface call sites (Constitution §I + FR-006)
grep -REn 'addJavascriptInterface' app/src/main/
# Expected output: empty
```

```bash
# 5c — Manifest permissions are exactly 3 (Constitution §II + FR-017)
grep -E 'uses-permission' app/src/main/AndroidManifest.xml | sort -u
# Expected output: exactly 3 lines — INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS
```

```bash
# 5d — All 4 file-access settings present in BrowserWebView (FR-013)
grep -E 'allowFileAccess|allowContentAccess|allowFileAccessFromFileURLs|allowUniversalAccessFromFileURLs' \
  app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt
# Expected output: exactly 4 matches, each set to false
```

```bash
# 5e — MIXED_CONTENT_NEVER_ALLOW present (FR-018)
grep -n 'MIXED_CONTENT_NEVER_ALLOW' \
  app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/browser/BrowserWebView.kt
# Expected output: 1 match
```

**Pass criteria**: All 5 sub-greps produce the expected output. Any deviation = block merge.

---

## Gate 6 — Localization completeness

```bash
# Verify all 4 new keys exist in all 8 locales
for loc in '' '-vi' '-de' '-ru' '-ko' '-ja' '-zh' '-fr'; do
  for key in browser_loading_a11y browser_error_title browser_error_offline_hint browser_error_generic; do
    grep -q "name=\"$key\"" "app/src/main/res/values$loc/strings.xml" || \
      echo "MISSING: $key in values$loc"
  done
done
# Expected output: empty
```

**Pass criteria**: No "MISSING" lines. Lint already enforces this (Gate 4) but explicit grep makes the omission obvious in code review.

---

## Gate 7 — Manual emulator UX verification (DEFERRED to user)

This gate cannot run in automated CI. Mirrors the Spec 004 / Spec 006 manual-gate pattern.

**Steps** (user runs on a real Android device or AVD):

1. Install debug APK from latest CI artifact.
2. **Cold start**: tap launcher → `BrowserScreen` opens → page renders within 5 s on Wi-Fi (SC-001).
3. **Loading indicator (US2)**: throttle network to 2G in emulator settings → re-launch → top `LinearProgressIndicator` visible within 200 ms, hides on `onPageFinished`.
4. **Error state (US3)**: airplane mode ON → re-launch → localized error UI visible within 5 s, no raw `net::ERR_*` text.
5. **Locale verification**: switch device language to each of VI, DE, RU, KO, JA, ZH, FR via Settings → Per-app languages → ThirtySix Browser → trigger error state → verify error text is in the selected locale (no fallback to EN).
6. **Rotation (SC-004)**: with page loaded, rotate portrait ↔ landscape twice → URL value unchanged (a fresh reload may run for v1; this is acceptable per R3).
7. **Background/foreground**: open app → home button → wait 30 s → re-open → no crash, page still rendered.
8. **No JS bridge**: open Chrome DevTools (USB debugging) attached to WebView → run `console.log(typeof Android)` → expect `undefined` (FR-006).

**Pass criteria**: All 8 sub-steps observed working as described.

---

## Gate 8 — 16 KB native-lib alignment (Constitution §IX, CI)

```bash
.specify/scripts/bash/verify-16kb-alignment.sh app/build/outputs/apk/release/app-release.apk
```

**Pass criteria**:
- Script exits 0.
- All `.so` LOAD entries align to `0x4000` (16 KB) or larger.
- Spec 007 introduces zero new `.so` (Espresso-Web is `androidTestImplementation` only and pure-Java; system WebView is OS-provided). Existing entries continue to be `libdatastore_shared_counter.so` × 4 ABIs and `libandroidx.graphics.path.so` × 4 ABIs (no change from Spec 006).

---

## Constitution Re-check (final)

Before marking Spec 007 complete, re-run the 11/11 Constitution gate from `plan.md`:

| # | Principle | Status |
|---|-----------|--------|
| I | Privacy & Security First | ✅ |
| II | Google Play Compliance | ✅ |
| III | Code Quality & Safety | ✅ |
| IV | Clean Architecture | ✅ |
| V | Performance | ✅ |
| VI | Testing | ✅ |
| VII | Offline-First | ✅ (with documented WebView exception) |
| VIII | Localization | ✅ |
| IX | Dep Currency & 16 KB | ✅ |
| X | Simplicity | ✅ |
| XI | Build Configuration | ✅ |

If any becomes ❌ during implementation, halt and revisit `plan.md` Complexity Tracking.

---

## What "Spec 007 done" means

When all 8 gates pass:

- The first user-visible browser UI is live.
- Phase 2 is unblocked: Spec 008 (`navigation-controls`) can begin.
- CLAUDE.md "Recent Changes" entry MUST be added with: instrumented-test job re-enable confirmation, exact `example.com` cold-start timing observed, APK size delta, new dependency (Espresso-Web) verified version + 16 KB attestation, count of new unit + instrumented tests.
- `Active Spec` block in CLAUDE.md is updated to "Suggested next: Spec 008".
