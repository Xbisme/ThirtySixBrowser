# Quickstart: Search Engine Google (Spec 010)

**Branch**: `010-search-engine-google`
**Date**: 2026-05-03

This document defines the verification gates for Spec 010. Gates 1–6 are automated; Gates 7–11 are on-device manual checks aligned with the success criteria. Mirrors Spec 009's quickstart structure (gates → SC mapping).

## Gate 1 — Unit tests pass (SC-004)

```bash
./gradlew testDebugUnitTest
```

**Expected**: 143 (Spec 009 baseline) + ~12 new = ~155 tests pass, zero failures. The new tests live in:

- `data/repository/SearchEngineRepositoryImplTest` — 3 engines × 4 query shapes (ASCII, special chars, non-Latin, single char) ≈ 12 assertions across 6 parameterized methods + 1 unknown-`storageValue` fallback test.
- `domain/usecase/BuildSearchUrlUseCaseTest` — 1 delegation test.
- `domain/model/SearchEngineTest` — extended `fromStorageValueOrDefault` to cover all 3 entries + unknown value.
- `presentation/browser/BrowserViewModelTest` — Spec 009 happy-path tests adjusted to inject the fake use case + 4 new submit-routing tests.

## Gate 2 — Static analysis green (SC-007)

```bash
./gradlew lintDebug detekt ktlintCheck
```

**Expected**: zero warnings, zero violations. Detekt baseline UNCHANGED from Spec 009. No new `@Suppress` annotations beyond what Spec 009 already established.

## Gate 3 — Constitution Check (SC-008)

11/11 PASS pre + post implementation per [plan.md Constitution Check](plan.md#constitution-check). Repository → Repository (Spec 010 R5) explicitly documented in [Complexity Tracking](plan.md#complexity-tracking).

## Gate 4 — APK release size (SC-005)

```bash
./gradlew assembleRelease
ls -lh app/build/outputs/apk/release/app-release.apk
```

**Expected**: APK size ≤ Spec 009 baseline (2.05 MB) + 20 KB = **≤ 2.07 MB**. Realistic — only 2 new `const val` strings, 1 new domain interface, 1 new repository implementation (single suspend method), 1 new use case (single line body), 1 new Hilt `@Binds`, and 2 new enum entries. No new Android resources.

## Gate 5 — 16 KB CI alignment (SC-006)

```bash
unzip -p app/build/outputs/apk/release/app-release.apk lib/arm64-v8a/lib*.so 2>/dev/null | \
  objdump -p - | grep LOAD | awk '{print $NF}'
```

**Expected**: every value `0x4000` (16 KB) or larger. No new `.so` files introduced (zero new packages), so the count + alignment match Spec 009 exactly (28/28 entries `0x4000`).

## Gate 6 — SearchEngineRepository → byte-identical Google output (SC-001)

A parameterized unit test in `SearchEngineRepositoryImplTest` covering at minimum these 10 query inputs:

| # | Input | Expected substring of returned URL (Google engine) |
|---|-------|-----------------------------------------------------|
| 1 | `android` | `q=android` |
| 2 | `kotlin coroutines` | `q=kotlin+coroutines` |
| 3 | `c++ tutorial` | `q=c%2B%2B+tutorial` |
| 4 | `cà phê sữa` | `q=c%C3%A0+ph%C3%AA+s%E1%BB%AFa` |
| 5 | `東京タワー` | `q=%E6%9D%B1%E4%BA%AC%E3%82%BF%E3%83%AF%E3%83%BC` |
| 6 | `красная площадь` | `q=%D0%BA%D1%80%D0%B0%D1%81%D0%BD%D0%B0%D1%8F+%D0%BF%D0%BB%D0%BE%D1%89%D0%B0%D0%B4%D1%8C` |
| 7 | `weather & forecast` | `q=weather+%26+forecast` |
| 8 | `?` | `q=%3F` |
| 9 | `🍕 pizza` | `q=%F0%9F%8D%95+pizza` |
| 10 | `검색` (Korean) | `q=%EA%B2%80%EC%83%89` |

Every produced URL MUST be byte-identical to what `String.format(UrlConstants.GOOGLE_SEARCH_URL_TEMPLATE, URLEncoder.encode(input, "UTF-8"))` produces directly (same encoder, same template). This is the SC-001 non-regression contract.

## Gate 7 — On-device — Default engine query (US1, SC-003 partial)

**Setup**: Fresh install (uninstall any prior debug build first). No DataStore writes have occurred.

**Steps**:
1. Launch the app.
2. Tap the address bar, type `kotlin coroutines`, press the keyboard's Enter / "Go" action.
3. Observe the WebView loads.

**Expected**: A Google search-results page renders with the title bar reflecting the query. Address bar shows `www.google.com` (hostname-only display from Spec 009). No crashes, no empty page, no layout regression vs Spec 009.

## Gate 8 — On-device — Switch engine to DuckDuckGo (US2, SC-002)

**Setup**: Build is freshly launched after Gate 7. The default engine is still Google.

**Steps**:
1. Use the **adb shell** (or any temporary internal hook) to flip the persisted engine to DuckDuckGo. Until Spec 016 ships the picker UI, the recommended approach is a debug-build-only test hook OR direct DataStore mutation via `adb shell run-as com.raumanian.thirtysix.browser <small kotlin script>`. Note: the precise mechanism is left to whatever the implementation pass uses; the canonical method is to call `SettingsRepository.setSearchEngine(DuckDuckGo)` from any temporary hook.
2. Without restarting the app, return to the address bar, type `android compose`, submit.

**Expected**: WebView loads `https://duckduckgo.com/?q=android+compose`. Address bar (hostname-only) shows `duckduckgo.com`. Repeat with `Bing` persisted → loads `https://www.bing.com/search?q=android+compose`, address bar shows `www.bing.com`.

## Gate 9 — On-device — Special characters across engines (US3, SC-003 full)

**Steps**: For each engine in `{Google, DuckDuckGo, Bing}` × each query in `{cà phê sữa, 東京タワー, weather & forecast}`:
1. Persist the engine via the Gate 8 mechanism.
2. Submit the query.
3. Verify the engine renders results page for the original query (not for a corrupted-encoding string).

**Expected**: 9 successful searches. The address bar (hostname-only) shows the correct host for each engine.

## Gate 10 — On-device — Zero-migration upgrade (SC-009)

**Setup**: A device or emulator running the previous Spec 009 build with `searchEngine = "google"` already on disk (the default; no user interaction required).

**Steps**:
1. `./gradlew installDebug` overwriting the Spec 009 build with the Spec 010 build.
2. Launch the app.
3. Submit a query.

**Expected**: Google search results, identical UX to before upgrade. The DataStore file format on disk is unchanged (`search_engine = "google"` round-trips). No first-launch flag flipped.

## Gate 11 — On-device — Build-time performance (SC-010)

**Setup**: Pixel 5+-class device or emulator. Spec 010 build installed.

**Steps**:
1. Open the BrowserScreen.
2. Submit any query.
3. Measure the elapsed time between Enter-key tap and `WebView.loadUrl(...)` actually being called. Use either an instrumented `Trace.beginSection(...)` block in `BrowserViewModel.onAddressBarSubmit` or a manual logcat timestamp comparison.

**Expected**: ≤ 50 ms (SC-010). Realistic measurement — `Flow.first()` on a warm DataStore is sub-1 ms; `URLEncoder.encode` is microseconds; `String.format` is microseconds; coroutine launch dispatch is sub-1 ms. Well under budget.

## Acceptance summary

| Gate | What | Aligned with | Automated? |
|------|------|--------------|------------|
| 1 | Unit tests pass | SC-004 | ✅ |
| 2 | Static analysis green | SC-007 | ✅ |
| 3 | Constitution Check | SC-008 | Manual review |
| 4 | APK size | SC-005 | ✅ |
| 5 | 16 KB alignment | SC-006 | ✅ (CI) |
| 6 | Google byte-identity | SC-001 | ✅ (unit tests) |
| 7 | Default Google query | US1 / SC-003 | Manual on-device |
| 8 | Engine switch effect | US2 / SC-002 | Manual on-device |
| 9 | Special chars × 3 engines | US3 / SC-003 | Manual on-device |
| 10 | Zero-migration upgrade | SC-009 | Manual on-device |
| 11 | Build-time perf | SC-010 | Manual on-device |

Manual gates 7–11 follow the Spec 008 / Spec 009 deferred-then-verified pattern (user runs them on a real device after the PR is opened).
