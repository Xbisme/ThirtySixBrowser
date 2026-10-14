# Quickstart: validating the architecture cleanup

Every Gradle call is prefixed with the Android Studio JBR:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Check the `BUILD SUCCESSFUL` line and the exit code, not only the absence of errors.

## G0 — Baseline (before the first move, on `main` @ 8eae890)

```bash
./gradlew testDebugUnitTest            # record: 932 tests
./gradlew assembleRelease              # record: release APK bytes
./gradlew connectedDebugAndroidTest    # 16 KB AVD TA016_API36_16K, record: 189 tests
```

Install the baseline release build on the 16 KB AVD, then measure cold start (10 interleaved rounds against the cleaned-up build later):

```bash
adb shell am force-stop com.raumanian.thirtysix.browser
adb shell am start -W -n com.raumanian.thirtysix.browser/.MainActivity | grep TotalTime
```

Seed data on a **debug** install of the baseline for G6 (history, downloads, tabs seeders; see `app/src/debug/AndroidManifest.xml` for action names), add a bookmark folder, a fireproof site, a site-permission decision and a protection exception by hand.

## G1 — Every commit on the branch

```bash
./gradlew detekt ktlintCheck lintDebug testDebugUnitTest assembleDebug assembleRelease
.specify/scripts/bash/verify-16kb-alignment.sh app/build/outputs/apk/release/app-release.apk
```

CI must be green on each pushed commit, including the API 29 instrumented job.

## G2 — Each rule fails on purpose (SC-004)

For every row of [contracts/architecture-checks.md](contracts/architecture-checks.md), add one violation on a scratch commit (for example `import android.net.Uri` in a `domain` file, `import …feature.bookmarks…` from `feature/history`, a `// Spec 012 …` comment, `Text("Hi")`), run the matching check, confirm it fails naming the file, then drop the commit.

## G3 — Layout (SC-007)

```bash
ls app/src/main/kotlin/com/raumanian/thirtysix/browser
# expect: core feature navigation MainActivity.kt ThirtySixApplication.kt
ls app/src/main/kotlin/com/raumanian/thirtysix/browser/feature
# expect: bookmarks browser downloads entrypoint history onboarding privacy settings sitepermission tabs
```

## G4 — Allowlists empty

`KNOWN_VIOLATIONS` is an empty set; `config/detekt/baseline.xml` and the ktlint baseline no longer exist.

## G5 — Comments (SC-005)

Run `python3 scripts/comment-stats.py app/src/main/kotlin` (same counting as `CommentPolicyTest`): comment share ≤ 12 %, 0 files over 15 %, 0 blocks over 5 lines; `grep -rE '\b[Ss]pec[ -]?[0-9]{3}\b|\b(FR|SC|NFR)-[0-9]+|\bT[0-9]{3}\b|§' app/src` returns nothing.

## G6 — Upgrade keeps all data (SC-001)

Install the cleaned-up **debug** build over the seeded baseline debug install (`adb install -r`). Open the app and confirm: tabs, bookmarks and folders, history, downloads list, theme/language/search engine/text size/retention, fireproof site, site-permission decision, protection exception — all present. Then cold-start 5 times with no crash in `adb logcat -b crash`.

## G7 — Tests (SC-002)

```bash
./gradlew testDebugUnitTest            # ≥ 932, plus the new architecture tests
adb shell am force-stop com.raumanian.thirtysix.browser.debug
./gradlew connectedDebugAndroidTest    # 189 on the 16 KB AVD; also green on CI API 29
```

Known flake: `BrowserWebViewRendererRecoveryTest.rendererCrash_…` (passes alone).

## G8 — Size and start-up (SC-003)

Release APK within ±2 % of G0; cold start within ±5 % over 10 interleaved rounds (baseline, cleaned-up, baseline, …) on the same device.

## G9 — Governance (SC-006)

`wc -l .specify/memory/constitution.md AGENTS.md CLAUDE.md` → < 250, < 100, 1. Every **MUST** in the constitution is followed by **Enforced by** or **Review**. `grep -rn 'docs/archive' AGENTS.md CLAUDE.md .specify/memory/constitution.md` returns nothing.

## G10 — Smoke on device

On the 16 KB AVD, release build: open a link from another app, load a page, star it, open History, open Downloads, Settings → change theme and language, open a new incognito tab, run Fire from the hub and from Tabs, toggle protection on the dashboard. Everything behaves as on the baseline.
