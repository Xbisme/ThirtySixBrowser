# Implementation Plan: Privacy Tools

**Branch**: `024-privacy-tools` | **Date**: 2026-10-13 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/024-privacy-tools/spec.md`

## Summary

The last spec of Phase 6 adds four privacy tools on top of Spec 023's protections:
- **Fire** — one confirmed action that closes every tab and clears history, cookies, site data, cache, site permissions, favicons and in-memory session traces, keeping fireproof sites;
- **Fireproof sites** — a switch in the dashboard, a list in Settings → Privacy, an opt-in Clear-browsing-data category;
- **Privacy dashboard** — a sheet from the hub's blocked-tracker line: blocked hosts grouped by site, connection state, protection and fireproof switches;
- **Two bundled scripts** — cookie-banner auto-decline (DuckDuckGo autoconsent, opt-out only) and basic fingerprinting protection, installed one-way at document start (Constitution §II).

How it is built (details in [research.md](research.md)):

- **Fire** is a `RunFireUseCase` of eleven ordered, idempotent steps behind a `FireCoordinator` singleton, with a DataStore marker so an interrupted run finishes on the next start (R10, data-model "Fire run").
- **Keeping fireproof data** = fireproof cookie snapshot with full attributes (`CookieManagerCompat.getCookieInfo`) → per-site deletion of every other candidate site (`WebStorageCompat.deleteBrowsingDataForSite`) → wipe all cookies → restore the snapshot. Older engines fall back to `WebStorage.deleteOrigin` with documented gaps (R2, R3).
- **Scripts** — one `addDocumentStartJavaScript(…, "*")` per WebView. A locally generated prelude carries the session secret and the excepted sites; the script decides by the top-level page (`ancestorOrigins`) whether to act (R5, R6).
- **Cookie banners** — autoconsent **16.51.0** (MPL-2.0) rebuilt with our own entry: `optOut` only, heuristics off, no prehide, no cosmetic hiding, generic CMP rules only (≈ 150 KB raw). It is produced by a hand-run generator and committed (R7).
- **Fireproof storage** — Room **v4 → v5** additive, mirroring Spec 023's exception slice (R12).
- **No new dependency** — every API is in `androidx.webkit` 1.17.0, verified with `javap` (R1).

## Technical Context

**Language/Version**: Kotlin 2.3.21, Java 11 target, AGP 9.1.1
**Primary Dependencies**:
- Existing only: Jetpack Compose (BOM 2026.04.01) / Material3, Hilt 2.59.2, Room 2.8.4, DataStore 1.2.1, kotlinx-coroutines.
- `androidx.webkit` 1.17.0 (already present, zero `.so`): `DOCUMENT_START_SCRIPT`, `GET_COOKIE_INFO`, `DELETE_BROWSING_DATA` — signatures confirmed by `javap` (R1).
- Vendored JS asset: DuckDuckGo autoconsent 16.51.0 (MPL-2.0), built by `tools/privacy-scripts/` (pinned npm + esbuild, hand-run, not part of the Gradle build).

**Storage**:
- Room **v4 → v5** (`fireproof_sites`, additive `MIGRATION_4_5`, `5.json` exported).
- One DataStore key, `fire_in_progress`.
- New assets under `assets/privacy/`.

**Testing**: JUnit + Turbine + Robolectric (unit); Room migration test; Compose UI + Espresso-Web on local fixtures. Script tests run behind `assumeTrue(DOCUMENT_START_SCRIPT)` (R15).
**Target Platform**: Android 7.0 (API 24) – Android 16 (API 36). 16 KB AVD (WebView 133) for gates; API 24 AVD for the fallback path; Galaxy A55 (WebView 153) for real sites.
**Project Type**: mobile-app (single `app` module, Clean Architecture + MVVM).
**Performance Goals**:
- SC-001: Fire → Home in ≤ 2 s with 20 tabs and 1,000 history rows.
- SC-010: page load ≤ +5% with scripts installed — the script is parsed in every frame, hence the generic-rules trim.
- SC-011: cold start ≤ +5%; the marker check adds nothing unless a run was interrupted.
- §V: overlay and dashboard state read in `graphicsLayer` / own flows; nothing new recomposes the browser chrome.

**Constraints**:
- §II one-way scripts only.
- No runtime fetch (§I).
- No new permission.
- 8 locales.
- APK ≤ +200 KB (SC-012).
- Zero new `.so`.
- No destructive migration.

**Scale/Scope**:
- 5 user stories, 32 FRs + 2 sub-FRs, 14 SCs.
- About 30 new production files and about 20 modified.
- About 30 string keys × 8 locales.
- 2 bundled scripts.

## Constitution Check

*GATE: checked before Phase 0 and re-checked after Phase 1.*

| Principle | Status | How |
|---|---|---|
| I Privacy & Security | ✅ | Scripts and rules ship in the APK and change only with a release; nothing is fetched (FR-019, FR-029, G13 proxy capture). Fire deletes traces; it adds no new store of browsing data — candidate hosts live in memory only for the run (R3). The fireproof list is on-device only. |
| II Play Compliance | ✅ | All five script conditions met point by point in R5: bundled; `addDocumentStartJavaScript` with feature detection; protections defined in Spec 024; no bridge, no listener, nothing read back (INV-9, R8 drops the one feature that would have needed it); origin rules `"*"` narrowed in-script by the top-site rule. The prelude is generated from local data only. No new permission. |
| III Code Quality / No-Hardcode | ✅ | New constants: `StorageKeys.FIRE_IN_PROGRESS`, `AssetPaths.PRIVACY_*`, `BrowserLimits.FIRE_OVERLAY_MS`, `BrowserLimits.SITE_DATA_DELETE_TIMEOUT_MS`, `BrowserLimits.FIRE_SECRET_BYTES`, `AppDestination.FireproofSites`. Colours, type and shapes come from the theme; all text is in string resources. |
| IV Clean Architecture | ✅ | Fire orchestration is a **use case** joining repositories and platform seams (as `ClearBrowsingDataUseCase`); no repository gains a repository dependency. Platform work stays behind the existing `WebDataCleaner` seam (extended). The script composer and host grouper are pure Kotlin in `domain/privacy/`. |
| V Performance / Compose Phase Discipline | ✅ | Blocked hosts get their own conflated flow, collected only by the dashboard. The overlay animates in `graphicsLayer`. The Fireproof list uses `key`/`contentType`. The script size is trimmed for page-load cost, and the scripts load off the main thread with Spec 023's data. |
| VI Testing | ✅ | §VI names "the fireproof-site rules (Spec 024)": unit tests for fireproof matching, `RunFireUseCase`, repository and migration. Compose UI tests for the Fire flow, dashboard and Settings list. Instrumented Fire tests on local fixtures (R15). |
| VII Offline-First | ✅ | Everything is local (SC-013). Fire's only "network" is the engine's own deletion callbacks. |
| VIII Localization & A11y | ✅ | 8 locales. Switches have toggle semantics, the overlay is announced as a state change, and motion follows the system animator scale (FR-006). |
| IX Dependencies & 16 KB | ✅ | No new Gradle dependency. The vendored script is pinned (16.51.0, lockfile), with its licence (MPL-2.0) verified and attributed (R7, R14). No `.so`. |
| X Simplicity & Build Order | ✅ with waiver | Phase 6, in order: 020 ✅ → 021 ✅ → 022 ✅ → 023 ✅ → **024**. "Design before spec" is **waived by the owner (2026-10-13)**, as for 020–023. Out-of-scope items stay out (spec "Out of Scope"). |
| XI Build Config | ✅ | No Gradle change; the script generator is a hand-run tool like Spec 023's list script. |

**Result: 11/11 PASS** (X with the owner's recorded design waiver).

## Project Structure

### Documentation (this feature)

```text
specs/024-privacy-tools/
├── spec.md
├── plan.md              # this file
├── research.md          # R1–R15
├── data-model.md        # entities, Fire run steps, invariants INV-1–INV-10
├── quickstart.md        # automated gates + device gates G1–G16
├── contracts/PrivacyToolsContracts.kt
├── fixtures/            # storage.html, fingerprint.html, frames.html, consent/*.html, serve.py (tasks)
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

New files are marked **+**, modified files **~**. The root is `app/src/main/kotlin/com/raumanian/thirtysix/browser/`.

```text
core/constants/
  ~ StorageKeys.kt              # FIRE_IN_PROGRESS
  ~ AssetPaths.kt               # privacy/consent.js, privacy/fingerprint.js
  ~ BrowserLimits.kt            # FIRE_OVERLAY_MS, FIRE_OVERLAY_MIN_MS, SITE_DATA_DELETE_TIMEOUT_MS (beside WEB_DATA_CLEAR_TIMEOUT_MS), FIRE_SECRET_BYTES
domain/model/
  + FireproofSite.kt, FireState.kt (FireState, FireResult, FireStep, SiteDataPath)
  ~ ClearBrowsingDataCategory.kt # + FireproofSites (not in DEFAULT_SELECTION)
domain/privacy/                 # pure Kotlin
  + PrivacyScriptComposer.kt
  + BlockedHostGrouper.kt
domain/repository/
  + FireproofSiteRepository.kt
  + PrivacyScriptSession.kt
  + PrivacyScriptBundles.kt     # interface; impl in data/local/privacy/
  ~ WebDataCleaner.kt           # storageOrigins(), clearExcept(...)
  ~ HistoryRepository.kt        # distinctHosts()
  ~ HttpsUpgradeFailures.kt     # clear()
domain/usecase/
  + RunFireUseCase.kt
  + SetSiteFireproofUseCase.kt, ObserveFireproofSitesUseCase.kt, RemoveFireproofSitesUseCase.kt, IsSiteFireproofUseCase.kt
  ~ ClearBrowsingDataUseCase.kt # FireproofSites branch
data/local/
  + entity/FireproofSiteEntity.kt, dao/FireproofSiteDao.kt
  ~ database/AppDatabase.kt     # v5
  + database/migrations/Migration4To5.kt
  ~ dao/HistoryDao.kt           # distinct URLs query
  ~ cache/FaviconCache.kt (+ DiskFaviconCache)  # retainOnly(hosts)
  ~ permission/OneTimeGrantStore.kt      # retainOnly(predicate)
  ~ protection/HttpsUpgradeFailureStore.kt # clear()
  + privacy/PrivacyScriptBundlesImpl.kt  # loads assets once, off main thread
  + privacy/PrivacyScriptSessionImpl.kt  # SecureRandom secret, regenerate()
  ~ webdata/AndroidWebDataCleaner.kt     # PerSite / Fallback paths, cookie snapshot+restore
data/mapper/       + FireproofSiteMapper.kt
data/repository/
  + FireproofSiteRepositoryImpl.kt (+ @Singleton snapshot)
  ~ HistoryRepositoryImpl.kt
fire/                           # app-level coordinator (no Android UI)
  + FireCoordinatorImpl.kt      # mutex, marker, resume
di/
  + PrivacyToolsModule.kt
  ~ DatabaseModule.kt           # MIGRATION_4_5, new DAO
MainActivity.kt                 # ~ await resumeIfInterrupted() when the marker is set
ThirtySixApplication.kt         # ~ start script-bundle load beside protection data
presentation/browser/
  ~ BrowserWebView.kt           # attach PrivacyScriptInstaller (incl. pop-up WebViews)
  + privacy/PrivacyScriptInstaller.kt
  ~ protection/PageProtectionMonitor.kt  # blockedHostsFlow
  + privacy/PrivacyDashboardViewModel.kt
  + components/PrivacyDashboardSheet.kt
  ~ components/HubProtectionSection.kt   # count line opens the dashboard
  ~ components/HubSheet.kt               # Fire tile
  + components/FireOverlay.kt, components/FireConfirmDialog.kt (shared ConfirmDialog)
  + fire/FireViewModel.kt                # exposes FireState, fire(), acknowledge()
  ~ BrowserScreen.kt                      # overlay host, dashboard host
presentation/tabs/
  ~ TabsScreen.kt / TabsTopBar           # Fire button → confirm → pop to browser
presentation/settings/
  ~ SettingsScreen.kt                     # Privacy → Fireproof sites row
  + fireproof/ (Screen, UiState, ViewModel)
  ~ components/ClearBrowsingDataDialog.kt # new category
  ~ components/AboutSection.kt            # autoconsent MPL-2.0 notice + source link
presentation/navigation/
  ~ AppDestination.kt, AppNavGraph.kt     # settings/fireproof_sites
presentation/theme/
  ~ AppIcons.kt + res/drawable/ic_fire.xml (Material Symbols local_fire_department)
app/src/main/assets/privacy/
  + consent.js (generated, MPL-2.0 header), fingerprint.js (own)
res/values*/strings.xml                   # ~30 keys × 8 locales
app/schemas/.../AppDatabase/5.json
tools/privacy-scripts/                    # package.json + lockfile (autoconsent 16.51.0, esbuild), entry.ts, build.sh, README.md
app/src/debug/…                           # Fire step-delay hook for G7 (debug source set only)
app/src/test/… , app/src/androidTest/…    # per R15
```

**Structure Decision**: The existing single-module Clean Architecture layout is used. Fire is a use case, as the project's other multi-store operations are. The app-wide coordinator lives in a small `fire/` package because Fire outlives any one screen. The script composer and host grouper are pure functions under `domain/privacy/`, so they can be unit-tested. WebView glue stays per WebView: Spec 023 has `PageProtectionMonitor`, and this spec adds `PrivacyScriptInstaller`. The dashboard and Fire get small ViewModels of their own, so `BrowserViewModel` does not grow; this follows the Spec 022/023 precedent.

## Post-Design Constitution Re-check

After Phase 1 (data-model, contracts, quickstart): **11/11 PASS, unchanged.**

Flagged risks:
- **Engine feature matrix (R1) is [A] until G1.** G1 is the first device task. If `getOrigins()` does not list localStorage, the gap is recorded as a limitation (R3), not designed around.
- **Attribute-preserving cookie restore (R2) is [A] until G4.** If attributes are lost even with `GET_COOKIE_INFO`, fireproofing still works but sign-ins may not survive a restart. That would be reported to the owner before continuing.
- **Script cost per frame (R7).** The generic-rules trim is the first answer, and G14 measures the cost. Widening the rules is the lever if SC-007 misses.
- **Accept-click trap in autoconsent heuristics (R7).** It is prevented by configuration. G11 asserts that no accept is ever recorded on the fixtures.
- **Spec amended by research (R8).** The "banner declined" dashboard line is dropped and recorded in the spec's Clarifications.

## Complexity Tracking

No constitution violation needs justifying. The §X design waiver is an owner decision recorded in the spec's Owner-decisions table and Assumptions, as for Specs 020–023.
