# Quickstart: Validating the Audit Fixes

Before running, make sure of three things:

- **Gradle:** run every Gradle command with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` and check the output for `BUILD SUCCESSFUL`.
- **Devices:** use the `TA016_API36_16K` emulator, plus `TA016_API29_CI` for Q6. Never use the A55.
- **Install order:** never install an older build over a newer one.

## Gates

```bash
./gradlew detekt ktlintCheck lintDebug testDebugUnitTest
./gradlew assembleRelease && .specify/scripts/bash/verify-16kb-alignment.sh app/build/outputs/apk/release/app-release.apk
adb shell am force-stop com.raumanian.thirtysix.browser.debug && ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
./gradlew assembleRelease -PcomposeReports
```

All of these must pass. The unit tests listed in [data-model.md](data-model.md) ("Tests by fix") must exist, and each must have failed against `main` before its fix.

## Device checks

The test pages are served from `app/src/androidTest/assets/` by `AssetPageServer`, or with `adb reverse tcp:8080 tcp:8080` and a local `python3 -m http.server`. `adb reverse` drops after an emulator restart, so set it again after each restart.

| # | Covers | Steps | Expected |
|---|---|---|---|
| Q1 | FR-001 | Open a page whose `onload` runs `location = "market://details?id=com.android.chrome"`. | No app opens. The "wants to open another app" snackbar shows. "Open" launches the store. |
| Q1b | FR-001, redirect | Tap a link to `/redirect-to-market`, which answers with a 302 to a `market://` address. | The store opens directly, so the tap carries through the redirect. If not, apply the R1 fallback. |
| Q2 | FR-001 | Open a page with an `<iframe>` whose script sets `location` to `tel:123`. | Nothing opens and no snackbar shows. |
| Q3 | FR-002a | In an incognito tab, tap `<a href="market://…">`. | The dialog shows. Cancel leaves the page. Open launches the store. |
| Q3b | FR-003 | Tap `intent://x#Intent;scheme=nope;S.browser_fallback_url=https%3A%2F%2Fexample.com;end`. | `example.com` loads in the same tab. |
| Q4 | FR-021 | Load `https://nonexistent.invalid`. Wait 5 s. | The app's error screen is still showing. Retry starts a new load. Run 10 times (SC-004). |
| Q4b | FR-022 | Load `https://example.com/does-not-exist-404`. | The site's own 404 page shows. |
| Q5 | FR-023 | Load a page with `<img src="https://expired.badssl.com/icon.png">`. | The page shows without an error, and its history entry exists. `https://expired.badssl.com/` itself shows the error screen. |
| Q6 | FR-008 | Sign in to a test site. Run `adb shell bmgr enable true`, `adb shell bmgr transport com.android.localtransport/.LocalTransport` and `adb shell bmgr backupnow com.raumanian.thirtysix.browser.debug`. Then list `/data/data/com.android.localtransport/files/` as root. | No `app_webview` path in the backup set. `datastore/` is present. (SC-005) |
| Q7 | FR-006 | Mark a site fireproof and sign in. Start Fire, then run `adb shell am kill` during the clearing step, using the step observer log. Relaunch. | Fire completes, and the fireproof site is still signed in. Repeat 20 times (SC-002). |
| Q8 | FR-024 | `adb shell run-as … sh -c 'echo garbage > files/datastore/settings.preferences_pb'`, then launch. | The app starts on onboarding, and bookmarks are intact (SC-007). |
| Q9 | FR-026 | Import `app/src/androidTest/assets/bookmarks-10k.html` (T057a). In bookmark search, type `ht`, then `htt`. | Results show in under 300 ms per keystroke. Measure with `BookmarkSearchScaleTest` (T057a) on the AVD; by eye, the list updates with no visible pause (SC-006). |
| Q10 | FR-025 | In an incognito tab, open History and tap an entry. | It loads in the incognito tab, and normal history gets no new row. |

Record the results in the PR description, one line per Q with pass or fail and the device.
