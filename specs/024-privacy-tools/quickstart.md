# Quickstart: validating Privacy Tools (Spec 024)

How to prove the feature works end to end. Contracts: [contracts/PrivacyToolsContracts.kt](contracts/PrivacyToolsContracts.kt). Steps and invariants: [data-model.md](data-model.md). Background: [research.md](research.md).

## Prerequisites

- `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- AVDs: `TA016_API36_16K` (WebView 133: `DOCUMENT_START_SCRIPT`, `GET_COOKIE_INFO`, `DELETE_BROWSING_DATA` all present — check in R1's spike) and `TA016_API24` (WebView 53: none of them — the fallback path, scripts off). CI's API 29 (WebView 74) also lacks the script feature, so script tests `assumeTrue` it.
- Galaxy A55 (WebView 153) for real sites. **Never change its default browser.**
- Before `connectedDebugAndroidTest`: `adb shell am force-stop com.raumanian.thirtysix.browser.debug` and clear any per-app language.
- Local fixtures (created in tasks) in `specs/024-privacy-tools/fixtures/`, served on two origins so first/third party and fireproof/non-fireproof can be told apart: `python3 -m http.server 8024` with `adb reverse tcp:8024 tcp:8024`, reached as `http://localhost:8024` and `http://127.0.0.1:8024` (⚠️ re-run `adb reverse` after every Gradle device run):
  - `storage.html` — on load sets a persistent cookie (`Max-Age`, `HttpOnly` via a `Set-Cookie` response from `fixtures/serve.py`), localStorage, an IndexedDB record, a Cache Storage entry and registers a service worker; `storage.html?check` prints which of the five exist.
  - `fingerprint.html` — reads a canvas (`toDataURL` + `getImageData`), an OfflineAudioContext rendering, `navigator.getBattery`, `enumerateDevices`; prints hashes.
  - `consent/onetrust.html`, `consent/cookiebot.html`, … — minimal pages reproducing each covered consent tool's markup and its consent cookie, one per tool in R7's list; `?check` prints the stored consent.
  - `frames.html` — embeds `fingerprint.html` and a consent fixture from the other origin.
- Proxy for G13 (mitmproxy/Charles, `adb shell settings put global http_proxy <host>:8888`).

## Automated gates

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew detekt ktlintCheck
./gradlew connectedDebugAndroidTest      # TA016_API36_16K; API 29 in CI
./gradlew assembleRelease                # SC-012: ≤ +200 KB vs main; 16 KB: no new .so
./gradlew assembleRelease -PcomposeReports=true   # 0 non-skippable
```

Expected:
- Unit: `RunFireUseCase` step order and failure boundaries (INV-1–INV-5) with fakes; resume after interruption at every step; fireproof filtering of hosts, permissions and favicons; `PrivacyScriptComposer` (JSON escaping, hostname validation, ordering); `BlockedHostGrouper`; `MIGRATION_4_5`; `ClearBrowsingDataUseCase` FireproofSites branch.
- `git grep -n "addJavascriptInterface\|addWebMessageListener\|evaluateJavascript\|javascript:" -- app/src/main/` shows nothing new; `addDocumentStartJavaScript` appears only in `PrivacyScriptInstaller`.
- `git grep -nE "postMessage|prompt\(|alert\(" -- app/src/main/assets/privacy/` shows nothing that targets the app (INV-9; the vendored engine's internal `postMessage` uses, if any, are listed and justified in research R7).
- `unzip -l app-release.apk | grep assets/privacy` lists the bundled scripts; `5.json` exported.

## Device gates

| Gate | Steps | Pass when |
|---|---|---|
| **G1 Spike** (R1) | Debug build, A55 + 16K AVD + API 24: log `isFeatureSupported` for the three features; on `storage.html` call `getCookieInfo`, `WebStorage.getOrigins` | Feature matrix recorded in research R1; `getOrigins` lists the fixture origin for IndexedDB, Cache Storage and service worker (record localStorage separately) |
| **G2 Fire basics** (US1, SC-001) | 20 tabs incl. 3 incognito, 1,000 seeded history rows; Fire from hub, cancel once, then confirm; repeat from Tabs screen | Cancel changes nothing; confirm → Home in one normal tab ≤ 2 s; incognito ended; History empty |
| **G3 Site data cleared** (SC-002) | `storage.html` on `localhost` and `127.0.0.1` (neither fireproof); Fire; `?check` | All five kinds gone on both (record `Fallback` path results on API 24 per R3) |
| **G4 Fireproof kept** (US2, SC-002) | Fireproof `localhost` (dashboard), not `127.0.0.1`; Fire; `?check` both | `localhost`: all five kept, cookie still `HttpOnly` and persistent after an app restart (proves attributes restored, R2); `127.0.0.1`: all gone |
| **G5 Real sign-in** (SC-003) | A55: sign in to 3 sites, fireproof 2; Fire; reopen | Signed in to the 2, signed out of the third |
| **G6 Kept data** (SC-004, SC-004a, SC-004b) | Before Fire: bookmarks, a running download, theme setting, a site without protection, camera "Always allow" on a fireproof and a non-fireproof site, favicons of 10 sites (2 bookmarked, 1 fireproof); Fire | All unchanged; download completes; non-fireproof camera decision gone, fireproof kept; only bookmarked/fireproof icons remain in `cacheDir/favicons` |
| **G7 Interrupted Fire** (FR-007, SC-005) | Debug-only delay hook after each step; `adb shell am force-stop` at steps 3, 5, 8 (5 runs) | Restart never shows an old tab; History empty; marker cleared |
| **G8 Session traces** (FR-003a, SC-008) | `fingerprint.html` hashes; Fire; read again; fail an HTTPS upgrade (`http://neverssl.com`), Fire, open it again | Hashes change after Fire; the upgrade is attempted again |
| **G9 Dashboard** (US3, SC-006) | Spec 023's `trackers.html` fixture; hub → tap count; toggle protection; toggle fireproof; rotate; switch language | 12 hosts grouped by site; switches work; state survives; no entry on Home / incognito start page; no fireproof switch in incognito |
| **G10 Settings list** (FR-012, FR-013) | Fireproof 3 sites; Settings → Privacy → Fireproof sites; remove one, remove all; Clear browsing data dialog | List newest first, empty state, remove-all confirms; new category unticked by default and empties the list when ticked; Cookies-and-site-data clears fireproof sites too |
| **G11 Cookie banners** (US4, SC-007) | Fixtures for each covered tool; 20 real banner sites on the A55 (EU-facing: e.g. bbc.co.uk, theguardian.com, spiegel.de, lemonde.fr, corriere.it, booking.com, …) with a VPN-free EU-consent fixture fallback; repeat one with protection off | Fixtures: refusal stored, banner gone; real: ≥ 12/20 refused, 0 accepted, 0 unusable; off-site untouched |
| **G12 Fingerprinting** (US5, SC-008, SC-009) | `fingerprint.html` twice on one origin, once on the other, inside `frames.html`, with protection off; 10 drawing-heavy real sites (maps, charts, a canvas game, a video call) | Same-site equal, cross-site different, frame uses the top site; off = real values; battery absent; device labels/ids empty without permission, real after granting; all 10 sites work |
| **G13 Offline + no traffic** (SC-013) | Airplane mode: G2–G4 on fixtures; proxy capture during G11 | Same results; no request initiated by these features |
| **G14 Performance** (SC-010, SC-011) | A55 release: 10 news sites × 5 loads, main vs branch; cold start interleaved, first run and returning | Median load ≤ +5%; cold start ≤ +5% |
| **G15 Old engine** (FR-022) | API 24 AVD: dashboard, Fire, fireproof, a consent fixture | Fire and fireproof work via the `Fallback` path (limitations as R3); scripts absent, no error |
| **G16 Locales + motion** (FR-006, FR-030) | 8 languages at 360dp: Fire dialog, tile, Tabs button, dashboard, Settings screen, clear-data label, About notice; then `animator_duration_scale 0` and Fire | All translated, nothing clipped (DE, RU first); with animations off the overlay appears and disappears without motion |

Screen-reader checks go to a person, as in Specs 016–023: the Fire tile/button names, the dialog, both dashboard switches' states and the grouped host list are announced.
