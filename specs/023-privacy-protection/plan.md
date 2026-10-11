# Implementation Plan: Privacy Protection

**Branch**: `023-privacy-protection` | **Date**: 2026-10-11 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/023-privacy-protection/spec.md`

## Summary

This spec adds four network-level protections, all on by default and all driven by data bundled in the APK:
- tracker and ad-network blocking;
- HTTPS upgrade with silent fallback;
- tracking-parameter stripping;
- Global Privacy Control.

Each can be switched off per site. The hub shows the page's blocked-tracker count, a "Connection not secure" line and the per-site switch. Settings → Privacy gains **Sites without protection**, and Clear browsing data gains an opt-in category. Referrer trimming is the engine's default `strict-origin-when-cross-origin`, which the app never loosens.

How it is built (details in [research.md](research.md)):

- **Data** — three assets parsed once off the main thread from `Application.onCreate` (R10):
  - a host-level **EasyPrivacy + EasyList** subset (CC BY-SA 3.0, ≈108 KB gz) (R1);
  - the **Public Suffix List** (MPL-2.0, ≈90 KB gz) (R3);
  - our own tracking-parameter list (R8).
- **Blocking** — `BrowserWebViewClient.shouldInterceptRequest`, with an O(1) host-suffix lookup against a `HashSet` and a third-party test by registrable domain. Blocked requests get an empty 403 response (R4).
- **Navigation rewriting** — one pure `prepareNavigation` (strip parameters, then upgrade) applied at the app's only two `loadUrl` call sites and in `shouldOverrideUrlLoading` for page-initiated main-frame GETs. HTTPS failures fall back silently (R7).
- **GPC** — an app-wide profile header where `MULTI_PROFILE` + `CUSTOM_REQUEST_HEADERS` exist; otherwise a header on every app-issued `loadUrl` (R5).
- **Exceptions** — Room v3 → v4 additive table `site_protection_exceptions`, mirrored into an in-memory snapshot for the IO thread. Incognito exceptions live in memory and are cleared when incognito ends (R11).

## Technical Context

**Language/Version**: Kotlin 2.3.21, Java 11 target, AGP 9.1.1
**Primary Dependencies**:
- Existing only: Jetpack Compose (BOM 2026.04.01) / Material3, Hilt 2.59.2, Room 2.8.4, DataStore 1.2.1;
- `androidx.webkit` 1.17.0, already present: `Profile.addCustomHeader`, `WebViewFeature`, optional `NavigationListener` (R4, R5);
- `android.webkit.WebViewClient.shouldInterceptRequest` / `shouldOverrideUrlLoading`.

**Storage**:
- Room **v3 → v4** (additive `MIGRATION_3_4`, `4.json` exported).
- No DataStore key.
- New APK assets under `assets/protection/` and `assets/licenses/`.

**Testing**: JUnit + Turbine + Robolectric (unit), Room migration test, Compose UI test + Espresso-Web with local fixture pages and a Hilt-replaced tracker list (R14).
**Target Platform**: Android 7.0 (API 24) – Android 16 (API 36); 16 KB AVD for gates; Galaxy A55 for real-site gates.
**Project Type**: mobile-app (single `app` module, Clean Architecture + MVVM).
**Performance Goals**:
- SC-005: page load with protection no slower than without.
- SC-006: cold start ≤ +5%.
- Per-request decision in microseconds, no I/O or lock on the hot path.
- §V: the count never recomposes the browser chrome.

**Constraints**:
- No runtime fetch of any list (§I, FR-001/002).
- No script injection and no JS bridge (§II).
- No new permission.
- 8 locales.
- APK ≤ +500 KB (SC-010).
- Zero new `.so`.

**Scale/Scope**:
- 6 user stories, about 40 FRs.
- About 30 new production files and about 18 modified.
- About 25 string keys × 8 locales.

## Constitution Check

*GATE: checked before Phase 0 and re-checked after Phase 1.*

| Principle | Status | How |
|---|---|---|
| I Privacy & Security | ✅ | All protection data ships in the APK and changes only with a release. Nothing is fetched (FR-001/002; checked by SC-012 with a proxy capture). Exceptions are stored on the device only. Incognito exceptions are never written. |
| II Play Compliance | ✅ | No new permission. No `addJavascriptInterface` or `WebMessageListener`. **No injected script**: GPC uses a request header, and the JS property is explicitly out of scope (FR-031). Category stays Tools. |
| III Code Quality / No-Hardcode | ✅ | Asset paths go in a new `core/constants/AssetPaths.kt`. The load timeout goes in a new `NetworkTimeouts.kt`. The header name/value and blocked-response status/reason go in `UrlConstants`. The never-block hosts are a named constant set. All text is in string resources. |
| IV Clean Architecture | ✅ | The matchers (PSL, tracker, parameters, upgrade policy) are **pure Kotlin in `domain/`**. Asset parsing sits in `data/local/protection/`. The WebView glue stays in `presentation/browser/`. `ProtectionExceptionRepository` depends on no other repository. The incognito store is a data-layer store like Spec 021's `OneTimeGrantStore`, cleared by `IncognitoTabRepositoryImpl` exactly where the cookie snapshot is restored. |
| V Performance / Compose Phase Discipline | ✅ | The blocked count is its own conflated `StateFlow`, collected only by the hub (rule 3). The intercept path does no allocation-heavy work: suffix probes plus a cached registrable domain. The lists load off the main thread (SC-006 gate). The Sites-without-protection list uses `key`/`contentType` (rule 6). |
| VI Testing | ✅ | §VI names the Spec 023 engines (host matcher, HTTPS upgrade, parameter stripping, exception list); each gets unit tests (R14). Migration test v3 → v4. Instrumented blocking, fallback and GPC tests on local fixtures. |
| VII Offline-First | ✅ | Protections are purely local and work offline (SC-011). The exception write is a single Room upsert. |
| VIII Localization & A11y | ✅ | 8 locales, plurals for the count. The switch has toggle semantics. "Connection not secure" is text, not colour only. |
| IX Dependencies & 16 KB | ✅ | No new dependency. `androidx.webkit` 1.17.0 is already verified with zero `.so`. The list sources' licences were verified from source (R1, R3, R8). |
| X Simplicity & Build Order | ✅ with waiver | Phase 6, in order: 020 ✅ → 021 ✅ → 022 ✅ → **023** → 024. **"Design before spec" is waived by the owner (2026-10-11)**, as for 020–022. The Fire button, dashboard and scripts are left to 024. |
| XI Build Config | ✅ | No change. The list generator is a hand-run script, not a Gradle task. |

**Result: 11/11 PASS** (X with the owner's recorded design waiver).

## Project Structure

### Documentation (this feature)

```text
specs/023-privacy-protection/
├── spec.md
├── plan.md              # this file
├── research.md          # R1–R14
├── data-model.md        # entities, invariants, state
├── quickstart.md        # automated gates + device gates G1–G16
├── contracts/ProtectionContracts.kt
├── fixtures/            # trackers.html, gpc.html, referrer pages (local test pages)
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

New files are marked **+**, modified files **~**. The root is `app/src/main/kotlin/com/raumanian/thirtysix/browser/`.

```text
core/constants/
  + AssetPaths.kt               # protection/tracker_hosts.txt, public_suffix_list.dat, tracking_params.txt, licenses/
  + NetworkTimeouts.kt          # PROTECTION_DATA_LOAD_TIMEOUT_MS
  ~ UrlConstants.kt             # GPC header name/value, blocked response status/reason, HTTP/HTTPS schemes
  ~ BrowserLimits.kt            # REGISTRABLE_DOMAIN_CACHE_SIZE
domain/model/
  + ProtectionData.kt           # TrackerHostSet, PublicSuffixRules, TrackingParamRules (immutable)
  + PageProtectionStatus.kt     # blockedCount, protectionOn, connectionSecure
  + SiteProtectionException.kt
  ~ ClearBrowsingDataCategory.kt # + SitesWithoutProtection (not in DEFAULT_SELECTION)
domain/protection/              # pure Kotlin, no Android imports
  + PublicSuffixMatcher.kt      # registrableDomain(host)
  + TrackerMatcher.kt           # isBlocked(requestHost, topHost) incl. never-block set
  + TrackingParamStripper.kt
  + HttpsUpgradePolicy.kt       # eligibility; failed-host set is injected
  + NavigationPreparer.kt       # prepareNavigation = strip, then upgrade, honouring exceptions
domain/repository/
  + ProtectionDataRepository.kt
  + ProtectionExceptionRepository.kt
domain/usecase/
  + ObserveProtectionExceptionsUseCase.kt
  + SetSiteProtectionUseCase.kt       # normal → Room, incognito → in-memory store
  + RemoveProtectionExceptionsUseCase.kt  # site or all
  ~ ClearBrowsingDataUseCase.kt       # SitesWithoutProtection branch
data/local/protection/
  + ProtectionAssetParser.kt          # three parsers, header/comment tolerant
  + ProtectionDataSource.kt           # @Singleton, async load + bounded await
  + ProtectionExceptionSnapshot.kt    # @Singleton StateFlow<Set<String>> from Room
  + IncognitoProtectionExceptionStore.kt
  + HttpsUpgradeFailureStore.kt       # in-memory, process lifetime
data/local/entity/ + SiteProtectionExceptionEntity.kt
data/local/dao/    + SiteProtectionExceptionDao.kt
data/local/database/
  ~ AppDatabase.kt                    # v4, + entity, + dao
  + migrations/Migration3To4.kt
data/mapper/       + SiteProtectionExceptionMapper.kt
data/repository/
  + ProtectionDataRepositoryImpl.kt
  + ProtectionExceptionRepositoryImpl.kt
  ~ IncognitoTabRepositoryImpl.kt     # clear incognito exception store on 1 → 0
di/
  + ProtectionModule.kt
  ~ DatabaseModule.kt                 # MIGRATION_3_4, new DAO
ThirtySixApplication.kt               # ~ start protection data load (no wait)
presentation/browser/
  ~ BrowserWebView.kt                 # intercept, override rewrite, upgrade fallback, GPC header, prepareNavigation at loadUrl sites
  + protection/PageProtectionMonitor.kt   # per WebView: top URL, blocked set, pending upgrade, StateFlow
  + protection/GpcHeaderInstaller.kt      # feature-detected profile header, once per process
  + protection/ProtectionViewModel.kt     # hub state + per-site switch
  ~ BrowserScreen.kt                  # pass monitor flow to hub, reload after switch
  ~ components/HubSheet.kt            # + HubProtectionSection (count, not-secure line, switch)
  + components/HubProtectionSection.kt
  ~ page/PageSession.kt               # reset monitor on advance()
presentation/settings/
  ~ SettingsScreen.kt                 # Privacy → Sites without protection row
  + protectionexceptions/ (Screen, UiState, ViewModel)
  ~ components/ClearBrowsingDataDialog.kt # new category label/description
  ~ components/AboutSection.kt        # Open-source notices
presentation/navigation/
  ~ AppDestination.kt, AppNavGraph.kt # settings/protection_exceptions
app/src/main/assets/
  + protection/tracker_hosts.txt, public_suffix_list.dat, tracking_params.txt
  + licenses/CC-BY-SA-3.0.txt, MPL-2.0.txt
res/values*/strings.xml               # ~25 keys × 8 locales (+ plurals)
app/schemas/.../AppDatabase/4.json
tools/protection-lists/build_tracker_hosts.py   # hand-run generator (R1), with README
app/src/test/…                        # unit tests per R14
app/src/androidTest/…                 # blocking, fallback, GPC, exceptions, migration
```

**Structure Decision**: The existing single-module Clean Architecture layout is used. Every protection rule is a pure function in a new `domain/protection/` package, so it can be unit-tested without Android. The WebView keeps a thin per-WebView `PageProtectionMonitor`, the only state the IO-thread callbacks touch. Hub state gets its own small `ProtectionViewModel` so that `BrowserViewModel` does not grow, following Spec 022's `PageTextSizeViewModel` precedent.

## Post-Design Constitution Re-check

After Phase 1 (data-model, contracts, quickstart): **11/11 PASS, unchanged.**

Flagged risks:
- **The GPC origin rule `"*"` (R5) is [B].** The fallback rule set is defined.
- **Back-stack entry after an HTTPS fallback (R7) is [B].** Gate G6 checks it. If an entry remains, it is recorded as a limitation; the experimental navigate API is not adopted.
- **The EasyList trim method (R1)** depends on the trim source's licence. The measured budget is met either way.
- **First page load may wait on list parsing (R10).** It is bounded and fails open. The time is measured in G14.

## Complexity Tracking

No constitution violation needs justifying. The §X design waiver is an owner decision recorded in the spec's Assumptions, as for Specs 020–022.
