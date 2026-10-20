# 031: Audit fixes

## Summary

This PR fixes the 20 numbered findings of the code audit and the 9 smaller ones listed with them. The changes are:

- Pages can open other apps only after a tap. Untapped attempts get a snackbar offer, and incognito tabs ask first.
- Fire keeps fireproof sign-ins, even when it is resumed after the app was killed.
- Web data is excluded from backups.
- Downloads are never deleted by name. They carry cookies and a referrer, and their names fit in 255 bytes.
- Error screens stay up until a new load starts. HTTP errors show the site's own page. Only main-page certificate errors fail a load.
- A damaged settings file is replaced with defaults instead of crashing the app.
- The address bar loads only http, https and about.
- History and bookmarks open in incognito tabs.
- Bookmark search stays fast at 10,000 bookmarks.
- Concurrent tab creation respects the tab limit, and download status works for any number of downloads.
- Unused and duplicated code is removed.

There is one commit per user story. Every fix has a unit test that failed before the fix. The rules are recorded in ADR 0032.

## Audit items

| # | Audit item | FR | Commit |
|---|---|---|---|
| 1 | Pages open other apps without a tap; `intent:` ignored | FR-001–FR-003, FR-002a | ab762a6 |
| 2 | Resumed Fire wipes fireproof cookies | FR-006 | dfc24fb |
| 3 | Deleting a forgotten download deletes another file | FR-014 | 392dfef |
| 4 | Error screen cleared by the engine's own error page | FR-021, FR-022 | 6a632e8 |
| 5 | Web data in cloud backup | FR-008 | dfc24fb |
| 6 | Damaged settings file crashes every launch | FR-024 | 6a632e8 |
| 7 | Bookmark search runs a query per result, with no limit | FR-026 | 180145c |
| 8 | History or bookmark tap does nothing in incognito | FR-025 | 180145c |
| 9 | Address bar runs `javascript://`; sentences with a dot become addresses | FR-004, FR-005 | 52b4132 |
| 10 | Incognito flag from a separate source; preview saved under the wrong tab | FR-011, FR-012 | dfc24fb |
| 11 | Certificate error in an embedded resource fails the page | FR-023 | 6a632e8 |
| 12 | Downloads without cookies and referrer; `blob:`/`data:` misreported | FR-016, FR-017 | 392dfef |
| 13 | Second photo deletes the first | FR-019 | 392dfef |
| 14 | Cookie contents in the log | FR-009 | dfc24fb |
| 15 | Cookie restore can hang forever | FR-010 | dfc24fb |
| 16 | Icon and preview caches write in place | FR-020 | 392dfef |
| 17 | Download names cut by characters; `+` lost | FR-018 | 392dfef |
| 18 | Generic-type downloads cannot be opened | FR-015 | 392dfef |
| 19 | Unused code, forwarding use cases, duplicates | FR-034 | 5b54f07 |
| 20 | Tab limit exceeded by concurrent creates | FR-029 | b001622 |
| – | Empty keep-set wipes saved tab state | FR-033 | dfc24fb |
| – | Incognito state re-added after close | FR-013 | dfc24fb |
| – | Pop-up kept in memory | FR-032 | b001622 |
| – | Download status fails above 999 records | FR-030 | b001622 |
| – | Onboarding double tap | FR-031 | b001622 |
| – | Imported addresses skip validation | FR-028 | 180145c, 2b6a9e7 |
| – | Unclosed folder name or title in import | FR-027 | 180145c |
| – | Fire cookie timeout loses fireproof cookies | FR-007 | dfc24fb |
| – | Misleading comment on closing a tab | FR-034 | 5b54f07 |

## Device checks (quickstart)

Q1–Q10 ran on `TA016_API36_16K`, and Q6 also ran on `TA016_API29_CI`. The A55 was not used. The Play Store on the API 36 image is a stub that cannot resolve `market://` (the start returns `INTENT_NOT_RESOLVED`). So the "app opens" steps used `tel:`, which opens the Dialer, with the same code path.

| # | Result | Device | Notes |
|---|---|---|---|
| Q1 | Pass | API 36 | An `onload` redirect to `market://` or `tel:` opens nothing, and the snackbar shows. "Open" sends `VIEW` + `BROWSABLE` with no component, and the Dialer opens. |
| Q1b | Pass (after b4350b2) | API 36 | A tapped link to `/redirect?to=tel%3A123` (302 to `tel:123`) first showed the offer snackbar: WebView drops `hasGesture` on a server redirect. With the R1 fallback, a main-frame redirect within 1 s of the last tap counts as tapped, and the Dialer intent starts with no snackbar. `ExternalAppHandoffTest.aTappedLinkThatRedirectsToAnotherApp_opensItWithoutAnOffer` drives this and failed before the fix. |
| Q2 | Pass | API 36 | An iframe setting `location` to `tel:123` opens nothing and shows no snackbar. |
| Q3 | Pass | API 36 | In incognito, tapping a `tel:` link shows "Open in another app?". Cancel stays on the page, and Open launches the Dialer. |
| Q3b | Pass | API 36 | An `intent://…;S.browser_fallback_url=https://example.com` link loads example.com in the same tab, and Back returns to the page. |
| Q4 | Pass | API 36 | The error screen is still showing 5 s after each of 10 runs: 5 fresh `*.invalid` loads and 5 reloads (SC-004). |
| Q4b | Pass | API 36 | `https://example.com/does-not-exist-404` (HTTP 404) shows the site's own page. |
| Q5 | Pass | API 36 | A page with `<img src="https://expired.badssl.com/…">` shows, and its history row is written. On `main` the same page gets no history row (checked on API 29). `https://expired.badssl.com/` itself shows the error screen. |
| Q6 | Pass (after 3035e19) | API 36, API 29 | `bmgr backupnow` to LocalTransport after setting a cookie. The backup set has no `app_webview/`, and `f/datastore/thirtysix_settings.preferences_pb` is present (SC-005). A later look found `sp/AwOriginVisitLoggerPrefs.xml` (WebView's hashed visited origins) and `sp/WebViewChromiumPrefs.xml` in the set; both are now excluded. Rechecked on API 36: the set holds only `f/datastore/…` and `f/profileInstalled`. |
| Q7 | Pass, 20/20 | API 36 | Two sites, `127.0.0.1` (fireproof) and `localhost` (not), each get a session cookie on disk. Fire runs with a 1 s step delay from `FIRE_STEP_DELAY`, and the process is killed with `kill -9` at 3.0–6.8 s, stepping 0.2 s across the cookie step. On relaunch, the fireproof site still sends its cookie, the other sends none, and the fireproof row remains (SC-002). |
| Q8 | Pass, 10/10 | API 36 | With garbage written to `files/datastore/thirtysix_settings.preferences_pb`, the app opens onboarding with no crash, and the bookmark is intact (SC-007). |
| Q9 | Pass | API 36 | `BookmarkSearchScaleTest`, with 10,000 bookmarks: median 8 ms over 5 runs (budget 300 ms; 15,211 ms before the fix) (SC-006). The by-eye typing check was not run. |
| Q10 | Pass | API 36 | In an incognito tab, a History tap loads the entry in that tab, sending no normal-jar cookie. History stays at 2 rows, and the normal tabs are unchanged. |

## Gates

| Gate | Result |
|---|---|
| `detekt ktlintCheck lintDebug testDebugUnitTest` | BUILD SUCCESSFUL. 1,140 unit tests, 0 failures (baseline 1,081). |
| `assembleRelease` + 16 KB script | All native libraries are 16 KB-aligned. |
| `connectedDebugAndroidTest`, `TA016_API36_16K` | 222 tests, 0 failures, 1 skipped (the GPC test's assumption). |
| `connectedDebugAndroidTest`, `TA016_API29_CI` | 222 tests, 0 failures, 9 skipped (protection tests that need a newer WebView). The first run after a cold boot had 1 failure: `BrowserScreenInstrumentedTest.rotation_preservesCurrentUrlInUiState`, an Espresso-web 10 s timeout loading example.com. It passed on two class reruns and on a second full run. |
| `assembleRelease -PcomposeReports` | `ExternalAppDialog` and `ExternalAppEffects` are restartable and skippable. No composable in the report is restartable but not skippable. |
| Cold start vs `main` | Release builds, 10 interleaved rounds on `TA016_API36_16K`, with a fresh install each round and 3 measured `am start -W` launches. Median TotalTime is 201.5 ms on `main` and 207.5 ms here (+3.0 %); the median of the per-round medians is 204.5 vs 208.5 ms (+2.0 %). Within ±5 %. |
| Release APK size | 3,843,719 B, which is +3,712 B over 3,840,007 B (budget +10 KB). |
| Comment policy | `scripts/comment-check.py` passes on all 169 changed `.kt` files. No `FR-`, task ID, `Spec 031` or `ADR` appears in added source lines. |
| SC-009 (cleanup commit) | 186 deleted − 57 added = **129** lines in `app/src/main` (target ≥ 150, not met; see below). |

## Deviations from the plan

- **T077 not done, so SC-009 is 129, short of 150.** The "nothing to export" pre-check in `BookmarkTransferViewModel` is not a duplicate in what the person sees. Without it, an empty library opens the system save picker, which creates an empty file before the use case reports "nothing to export". The cleanup phase must not change behavior, so the check stays.
- **FR-028 encodes rather than drops.** Imported addresses with characters that `java.net.URI` rejects but browsers export (space, `"`, `|`, `{`, `}`, `^`, `` ` ``, `<`, `>`, `\`, and a lone `%`) are percent-encoded before validation. Real links are kept this way. Only addresses that still fail are counted as skipped (2b6a9e7).
- **T047: the main-frame URL is also captured in `shouldInterceptRequest`.** On WebView 133, a main page's certificate error arrives before `onPageStarted`, so the address is recorded when the main-frame request goes out. `onPageStarted` still updates it.
- **T057a:** the 10,000-bookmark file is generated at test time by `LargeBookmarkFile`, not committed as an asset (about 0.8 MB, the same on every run).
- **FR-005 trade-off:** input with a dot and no whitespace, such as `kotlin.coroutines`, still opens as an address.
- **Q1b: the R1 last-tap fallback is built** (b4350b2). `BrowserPageView` records the last `ACTION_UP` without consuming it, and `ExternalLinkPolicy.isTapped` treats a main-frame redirect within `BrowserLimits.EXTERNAL_TAP_WINDOW_MS` (1 s) of it as tapped.
- **The open-app offer ends with its page** (d158826). The snackbar had an action, so it was Indefinite and stayed on later pages. It is now `Long`, and `PageSession` holds the offer as state that a new page generation clears. While testing this, a page that redirects from its first script turned out to reach `shouldOverrideUrlLoading` before its own `onPageStarted` is delivered, so its offer was tagged with the previous generation (Open then did nothing). The offer is now posted behind that queued callback.
- **WebView's shared prefs are excluded from backup** (3035e19): `AwOriginVisitLoggerPrefs.xml` and `WebViewChromiumPrefs.xml`, in all three rule blocks, with `BackupRulesTest`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
