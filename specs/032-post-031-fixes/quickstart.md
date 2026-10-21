# Quickstart: Validating Spec 032

Before running anything:

- Prefix every Gradle call with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- Use the emulators `TA016_API36_16K` and `TA016_API29_CI`, with the serial set explicitly (`ANDROID_SERIAL`, `adb -s`).
- The owner's A55 may be attached. Never run an instrumented test or send input to it.

## Gates

```bash
./gradlew detekt ktlintCheck lintDebug testDebugUnitTest
./gradlew assembleRelease && .specify/scripts/bash/verify-16kb-alignment.sh app/build/outputs/apk/release/app-release.apk
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
./gradlew :app:compileReleaseKotlin -PcomposeReports --rerun
```

## Device checks

| # | FR | Steps | Expected |
|---|---|---|---|
| Q1 | 001 | Page whose `touchend` handler sets `location` to `/redirect?to=tel%3A1`. Scroll. | No Dialer. The offer snackbar shows. |
| Q1b | 001 | Tap a link to `/redirect?to=tel%3A1`. | The Dialer intent starts directly. |
| Q2 | 002 | Tap `intent://x#Intent;scheme=https;package=<own package>;end`. | The address loads in the same tab, and the tab count is unchanged. |
| Q3 | 003 | Long-press a link to `/redirect?to=<second asset-server host>/echo-cookies` and choose Download. | The echo server logs no `Cookie` header on either hop. |
| Q4 | 012 | Load `/redirect?to=https://127.0.0.1:<self-signed port>/`. | The error screen shows. |
| Q5 | 011 | Tab A: two pages. Tab B: load an unused port, so it fails. Switch to A and press Back. | A's first page shows. |
| Q6 | 013 | Load a page, go offline, then reload. Check the tab title and the history row. | Both keep the real title. |
| Q7 | 014 | On the error screen, tap Retry with the server stalled for 3 s. | The progress indicator shows within 1 s. |
| Q8 | 024 | `BookmarkImportScaleTest`. | 10k links under 2 s. A 10 MB file with no closing tags under 5 s. |

Record each result in the PR description, with pass or fail and the device.
