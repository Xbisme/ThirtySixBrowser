# Quickstart: validating Privacy Protection (Spec 023)

How to prove the feature works end to end. Contracts: [contracts/ProtectionContracts.kt](contracts/ProtectionContracts.kt). Rules and invariants: [data-model.md](data-model.md). Background: [research.md](research.md).

## Prerequisites

- `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` — without it Gradle fails silently.
- AVDs: `TA016_API36_16K` (16 KB, API 36) and `TA016_API24` (minSdk, WebView 53 — the GPC/old-engine path). A Galaxy A55 for real-site gates; **never change its default browser** (cancel role dialogs).
- `adb shell am force-stop com.raumanian.thirtysix.browser.debug` before `connectedDebugAndroidTest`; clear any per-app language first.
- Local fixtures (created in tasks) in `specs/023-privacy-protection/fixtures/`, served with `python3 -m http.server 8023` + `adb reverse tcp:8023 tcp:8023` (⚠️ re-run `adb reverse` after every Gradle device run):
  - `trackers.html` — 30 requests (img/script/iframe/fetch) to 12 hosts `tracker-01.test` … `tracker-12.test` plus 10 requests to `localhost:8023`. `*.test` names never resolve, so they are refused before any DNS lookup. G1 runs as an instrumented test with a Hilt-replaced list containing those 12 hosts; real lists are exercised on real sites in G2.
  - `headers.html` + a tiny echo server (`fixtures/echo_headers.py`, prints request headers) — GPC and referrer checks.
  - HTTPS fallback cannot be reproduced locally (local hosts are never upgraded, FR-022), so it is checked on real HTTP-only sites in G6.
- A proxy for traffic capture (mitmproxy or Charles on the host; `adb shell settings put global http_proxy <host>:8888`). `adb emu network capture` records nothing on these AVDs (Spec 022 finding).

## Automated gates

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew detekt ktlintCheck
./gradlew connectedDebugAndroidTest      # TA016_API36_16K; API 29 in CI
./gradlew assembleRelease                # SC-010: ≤ +500 KB vs main (3,394,072 B); 16 KB check: no new .so
./gradlew assembleRelease -PcomposeReports=true
```

Expected:
- Unit tests green, including PSL, tracker matcher, stripper (SC-004's 10 cases), upgrade policy, preparer idempotence, exceptions repository, `MIGRATION_3_4`.
- `git grep -nE "https?://" -- app/src/main/kotlin/**/protection/` shows no fetch of list data; `git grep -n "addJavascriptInterface\|addWebMessageListener\|addDocumentStartJavaScript" -- app/src/main/` unchanged.
- `unzip -l app-release.apk | grep assets/protection` lists the three assets; `4.json` exported.

## Device gates

| Gate | Steps | Pass when |
|---|---|---|
| **G1 Blocking, fixture** (SC-001, SC-011) | Instrumented `TrackerBlockingTest` on `trackers.html`; repeat in airplane mode | 30 listed requests refused, 10 local load, hub reads **12**; same in airplane mode |
| **G2 Real sites** (SC-002) | 10 sites on the A55 (e.g. cnn.com, bbc.com, theguardian.com, vnexpress.net, amazon.com, ebay.com, bing.com, youtube.com, reddit.com, imdb.com) | Each shows ≥ 1 blocked; ≥ 9/10 work (content, navigation, sign-in link, an embedded video on youtube.com/news) |
| **G3 First party / never-block** (FR-004a, FR-008) | Open youtube.com and a page embedding a YouTube video and a Google sign-in button | `googlevideo.com`, `ytimg.com`, `accounts.google.com` load; video plays; sign-in opens |
| **G4 Count resets** (FR-010) | On a site, note the count; navigate to another page; change only `#hash` | New page restarts the count; hash change does not |
| **G5 Per-site switch** (US2, SC-007) | Hub → switch off on cnn.com; restart 3×; Settings → Sites without protection → remove | Page reloads, count 0, "protection off"; survives restarts; `www.`/subdomains covered; after removal protection is back on next load |
| **G6 HTTPS upgrade** (US3, SC-003) | 10 `http://` addresses of HTTPS sites (typed, link from a fixture page, bookmark, history, `adb shell am start -a VIEW -d http://…`); 3 HTTP-only sites (e.g. `http://neverssl.com`, `http://http.badssl.com`, a site redirecting https→http); then press Back | 10/10 open as `https://`; the 3 open over `http://` with no error page and no loop; a second http address on a failed host opens directly; **Back does not land on an error entry** (else record limitation, R7) |
| **G7 Not upgraded** (FR-022) | `http://localhost:8023/`, `http://192.168.1.1/`, `http://router/`; a site with protection off | none upgraded |
| **G8 Not-secure line** (FR-022a) | Hub on an http page (fallback, local, excepted) and on an https page | line shown on all http pages only; no snackbar/dialog |
| **G9 Parameters** (US4, SC-004) | Open `https://example.com/?a=1&utm_source=x&b=2&fbclid=y#top` from a link, typed, from another app; then Share; then History | Request (proxy) and address bar are `https://example.com/?a=1&b=2#top`; history and Share identical; on an excepted site the parameters remain |
| **G10 GPC** (US5, SC-009) | `headers.html` via echo server: normal, incognito, excepted site; on API 36 and API 24 | API 36: `Sec-GPC: 1` on page **and** resource requests in all three; API 24: on every page load the app starts (typed, bookmark, intent, tab switch) |
| **G11 Referrer** (US6, FR-028) | From `headers.html` on `localhost` open a link to `127.0.0.1:8023` and load an image from it; a same-site link | Cross-site referer ≤ origin; same-site full path; an upgraded/stripped re-issue sends no referer |
| **G12 Incognito** (FR-014, SC-008) | Switch off a site in incognito; check it in a normal tab; close all incognito tabs; open it in a new incognito tab; also open a normal-excepted site in incognito | Off only in that incognito session; protected again afterwards; normal exceptions not applied in incognito |
| **G13 Clear browsing data** (FR-016a) | Dialog shows the new category unticked; clear defaults; then clear with it ticked | Defaults leave exceptions; ticked removes Room + incognito exceptions |
| **G14 Performance** (SC-005, SC-006, R10) | 10 news sites × 5 loads on vs off (A55, release); cold start interleaved main vs branch, first run and returning; log list-parse time and heap | Median load on ≤ off; cold start ≤ +5%; parse time and heap recorded |
| **G15 No traffic of its own** (SC-012) | Proxy capture during G1 and G2 | No request to any list/update host; nothing initiated by the app besides page traffic |
| **G16 Locales + About** (FR-003, FR-033) | All 8 languages at 360dp: hub section, plurals (0/1/2/5/21 trackers), Settings screen + empty state, clear-data label, About notices | All translated, nothing clipped (DE, RU first); attribution present |

Screen-reader checks go to a person, as in Specs 016–022: the count, the switch's on/off state and the "not secure" line are announced.
