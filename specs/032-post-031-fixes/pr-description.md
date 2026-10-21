# Spec 032: post-031 audit fixes

## Summary

Fixes the findings of the 2026-10-21 audit of Spec 031.

- Pages can no longer open other apps without a tap, and `intent:` links never re-enter this app.
- Downloads leak no cookies, and send a minimal referrer.
- Fire no longer crashes when it cannot read the fireproof list. It reports the steps it could not finish.
- Upload photos are swept. Clearing browsing data finishes even if you leave Settings.
- A failed tab no longer breaks history in the next tab. A certificate error after a redirect shows the error screen. Titles stay frozen while a page has failed.
- Deleting a forgotten download no longer deletes another file. Temp files are unique. File names lose their formatting characters.
- Bookmark import runs in linear time and keeps more addresses.

Spec, plan and tasks: `specs/032-post-031-fixes/`.

## Audit item → requirement → commit

| Audit item | FR | Commit |
|---|---|---|
| 1. Any finger lift counts as a tap | FR-001 | `ed9e9d0` |
| 2. Long-press download sends cookies through redirects | FR-003 | `bd04ef3` |
| 3. Tab switch after a failure loses history | FR-011 | `8a07beb` |
| 4. Fire crashes when the fireproof list cannot be read | FR-005 | `bd04ef3` |
| 5. Deleting a forgotten download deletes another file | FR-018 | `91a2e15` |
| 6. Import scan is quadratic | FR-024 | `fb45245` |
| 7. Fixed temp file name in cache writes | FR-019 | `91a2e15` |
| 8. Certificate error after a redirect shows a blank page | FR-012 | `8a07beb` |
| 9. `intent:` link can target this app | FR-002 | `ed9e9d0` |
| 10. Cookie restore timeout leaves incognito cookies | FR-006 | `bd04ef3`, `b835159` |
| 11. Full referrer across sites and from incognito | FR-004 | `bd04ef3` |
| 12. Import drops `#`, `[]`, IDN and underscore links | FR-025 | `fb45245` |
| 13. Upload photos outlive the session | FR-008 | `bd04ef3`, `b835159` |
| 14. Clear browsing data cancelled by leaving Settings | FR-009 | `bd04ef3` |
| 15. Tab switch undone by a title update | FR-015 | `8a07beb` |
| 16. Formatting characters in file names | FR-020 | `91a2e15` |
| 17. One-letter search says "no matches" | FR-026 | `fb45245` |
| 18. Fire site-permission errors ignored, one delete per origin | FR-007 | `bd04ef3` |
| 19. HTTPS-upgrade failures not cleared with history | FR-010 | `bd04ef3` |
| 20. Generic types missing | FR-021 | `91a2e15` |
| Late preview saved for a closed tab | FR-016 | `8a07beb` |
| Error page title overwrites the real title | FR-013 | `8a07beb`, `5f8434a` |
| No loading indication on retry | FR-014 | `8a07beb` |
| Closed-incognito race | FR-017 | `8a07beb` |
| File metadata read on the main thread | FR-027 | `7a496af` |
| Lazy item without content type | FR-028 | `7a496af` |
| One failing status chunk clears all | FR-023 | `91a2e15` |
| No room for the de-duplication suffix | FR-022 | `91a2e15` |
| Wrong suppression, pass-through property, duplicated hand-off | FR-029 | `ed9e9d0`, `8a07beb` |

`c9e1304` fixes a test only: the 10 MB import test ran out of heap on API 29.

## Device checks (quickstart)

All checks ran on `emulator-5554` (`TA016_API36_16K`, API 36).

| # | FR | Result | How |
|---|---|---|---|
| Q1 | 001 | Pass | `ExternalAppHandoffTest.aSwipeWhoseEndRedirectsToAnotherApp_opensNothing_andOffersIt` |
| Q1b | 001 | Pass | `ExternalAppHandoffTest.aTappedLinkThatRedirectsToAnotherApp_opensItWithoutAnOffer` |
| Q2 | 002 | Pass | By hand. Tapped `intent://localhost:8711/landing#Intent;scheme=http;package=…debug;end`. The landing page loaded in the same tab. The tab count stayed at 1. |
| Q3 | 003 | Pass | By hand. Both hosts had cookies set. A long-press Download on `localhost:8711/redirect?to=127.0.0.1:8712/echo-cookies` logged `Cookie=None` on both hops. |
| Q4 | 012 | Pass | `BrowserScreenOfflineErrorTest.errorState_showsForARedirectToABadCertificate` (in-test self-signed server) |
| Q5 | 011 | Pass | `ErrorRecoveryTest.backAfterSwitchingAwayFromAFailedTab_showsThePreviousPage` |
| Q6 | 013 | Pass | By hand. Loaded "Real Title Q6", stopped the server and reloaded (`ERR_EMPTY_RESPONSE`, error screen). The tab switcher and the History row both still show "Real Title Q6". |
| Q7 | 014 | Pass | `BrowserScreenOfflineErrorTest.retry_showsTheIndicatorBeforeAStalledServerAnswers` |
| Q8 | 024 | Pass | `BookmarkImportScaleTest`. Medians of 5: 10k links in 248 ms, 10 MB unclosed in 3,860 ms. On `TA016_API29_CI`: 124 ms and 2,626 ms. Budgets are 2 s and 5 s. |

## Gates

| Gate | Result |
|---|---|
| `detekt ktlintCheck lintDebug testDebugUnitTest` | Green. 1,193 unit tests, 0 failures (baseline 1,147). |
| `connectedDebugAndroidTest` on `TA016_API36_16K` | 233 run, 1 flake. `TrackerBlockingTest` read the blocked count before it settled; it passed 3 of 3 reruns, and 032 does not touch that code. |
| `connectedDebugAndroidTest` on `TA016_API29_CI` | 241 run, 2 failures. The 10 MB import test ran out of heap; `c9e1304` fixes it and it now passes on both emulators. `BrowserScreenInstrumentedTest.rotation_preservesCurrentUrlInUiState` hit an Espresso-Web timeout; it passed 4 of 4 reruns. |
| `assembleRelease` + 16 KB script | All native libs are 16 KB-aligned. The APK is 3,860,303 B, the same as the baseline, because alignment padding absorbs the growth. Compressed entries grew by 5,551 B (3,715,040 → 3,720,591), and `classes.dex` by 11,284 B raw. |
| Compose compiler report | 0 restartable-but-not-skippable composables. |
| Comment policy | `scripts/comment-check.py` passes on the 82 changed `.kt` files. No added source line names an FR, task, spec or ADR. |
| `main` Kotlin lines | 31,763 → 32,116 |

## Deviations from tasks.md

- **FR-006, no full cookie wipe.** `bd04ef3` wiped every cookie after a failed restore. `b835159` reverted that to the FR-006 wording. A failed restore now only marks CloseIncognito as failed. The cookie step keeps its normal keep-set path, so fireproof sign-ins stay.
- **`IncognitoTabRepository.closeAll(): Boolean`** carries the restore result up to Fire. Fire does not call the cookie manager itself.
- **`CaptureCacheImpl` clears the capture folder itself.** It lists `cacheDir/<capture dir>` on the IO dispatcher. The data layer may not import the presentation-layer `CaptureFiles`. The page-session sweep runs in a `LaunchedEffect` on `LocalIoDispatcher`.
- **`TabDao.update` removed.** Its last whole-row callers moved to `setLastActiveAt` and `setUrlAndTitle`.
- **Retry uses the top-bar reload.** The error screen has no Retry button of its own, so the reload action sets `Loading` before the WebView reloads.
- **A failed reload keeps the title** (`5f8434a`). While `Failed`, saving the tab keeps its own title when the address has not changed.
- **`FileImage` is keyed by path and `File`.** A path-only key would never reload a favicon that appears later at the same path. `lastModified()` is no longer read.
- **Deleting a forgotten download keeps the file.** When the download service has nothing to remove, `deleteFile` returns `true` and touches no file. The row goes away, and the file stays in Downloads.
- **Temp-file sweeping needed no change.** `retainOnly` and `clearAll` already delete stray `.tmp` files. The race test runs 10 rounds, because one round did not always fail on the old code.
- **The file-name filter uses `CharCategory.FORMAT`.** A base made only of format characters becomes `download`.

## Open points for review

- **Long-press downloads send only the origin as referrer, even on the same site** (follow-up commit). Q3 showed that a same-site link redirecting to another site handed that site the full page address, because the download service sends the same headers on every hop. Page-started downloads keep the full same-origin rule, since the WebView has already followed the redirects.
- Two flakes are left as they are. `TrackerBlockingTest` (code this spec does not touch) waits on the blocked host set, then asserts the count. The rotation test failed once on API 29. Both passed every rerun.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
