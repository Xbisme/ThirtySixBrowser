# Quickstart: validating the action/event move

Every Gradle call is prefixed with the Android Studio JBR:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Check for `BUILD SUCCESSFUL` and the exit code. Run instrumented tests only on the AVDs, never on the Galaxy A55. Run `adb shell am force-stop com.raumanian.thirtysix.browser.debug` first.

## G0: Baseline (on `0372d0f`, before the first change)

```bash
./gradlew testDebugUnitTest                         # record unit test count
ANDROID_SERIAL=<TA016_API36_16K> ./gradlew connectedDebugAndroidTest   # record instrumented count
./gradlew assembleRelease                           # record release APK bytes
./gradlew assembleRelease -PcomposeReports          # record restartable / non-skippable
grep -c '<error' config/ktlint/baseline.xml         # expect 23
grep -rl 'Dispatchers.setMain' app/src/test | wc -l # expect 22
```

Cold start: 10 interleaved launches of the baseline and final release builds on the 16 KB AVD.

```bash
adb shell am force-stop com.raumanian.thirtysix.browser
adb shell am start -W -n com.raumanian.thirtysix.browser/.MainActivity | grep TotalTime
```

## G1: Every commit

```bash
./gradlew detekt ktlintCheck lintDebug testDebugUnitTest assembleDebug
```

The feature's instrumented tests also run on the 16 KB AVD when the commit touches a screen that has them (settings, history, downloads, fireproof, site permissions, protection exceptions, browser).

## G2: The checks fail on purpose (SC-008)

On a scratch commit, one at a time:

1. Add `fun onRefreshClicked() {}` to `SitePermissionsViewModel`. `testDebugUnitTest --tests '*ArchitectureTest'` should fail and name the file.
2. In `SitePermissionsScreen`, pass `viewModel` to a private child composable. `ktlintCheck` should fail with `compose:vm-forwarding-check`.
3. Add `Dispatchers.setMain(StandardTestDispatcher())` to any test. `ArchitectureTest` should fail and name the test file.

Drop the scratch commit afterwards.

## G3: Shape (SC-002 … SC-005)

```bash
test ! -f config/ktlint/baseline.xml && ! grep -q 'baseline' app/build.gradle.kts   # SC-003
grep -rl 'Dispatchers.setMain' app/src/test                                          # SC-005: only MainDispatcherRule.kt
ls app/src/main/kotlin/com/raumanian/thirtysix/browser/**/usecase/ | wc -l           # 82 → 49 (SC-004)
grep -rn 'fun consume' app/src/main/kotlin                                            # nothing
grep -rLn 'fun onAction' $(find app/src/main -name '*ViewModel.kt')                   # only PageTextSize, EntryPoint
```

## G4: Behaviour walk (SC-006)

Run on the 16 KB AVD with a release build and animations on. For each screen, do its main action, then rotate, then send the app to the background and return. Each message, dialog or navigation must appear exactly once.

| Screen | Walk |
|---|---|
| Browser | Type an address and submit (keyboard hides, page loads). Star, then unstar (snackbar each time). Start a download (snackbar). Toggle desktop site (reloads as desktop). Long-press new tab at the cap (limit snackbar). Turn protection off and on (page reloads). Use find-in-page. Back from a page opened from Home returns to Home |
| Home | Tab shortcut. Default-browser card dismiss. Resume refreshes the card |
| Hub | New incognito tab at the cap (snackbar) |
| Tabs | Open a tab (returns to the browser). Close all (confirm). Incognito mode. New tab at the cap (snackbar) |
| Bookmarks | Open a bookmark (returns to the browser). Create, rename and move a folder. A cycle move shows an error. Edit and delete a bookmark |
| History | Open an entry. Long-press, open in new tab, copy (snackbar), delete. Clear all |
| Downloads | Open, copy link (snackbar), delete file (confirm), cancel a running download |
| Settings | Each row's dialog. Language change applies in place. Clear browsing data (snackbar). Retention shorten (confirm). Dynamic color. Quick settings from the hub |
| Site permissions, protection exceptions, fireproof | Change, remove one, remove all (confirm) |
| Onboarding | Fresh install: forward, back, skip. Back on slide 1 leaves the app without finishing onboarding |
| Fire | From the hub and from Tabs: confirm, animation, Home in a new tab |
| Entry | A link from another app opens in a new tab |

Rotation mid-snackbar: the snackbar is not shown again (research R4; the one accepted difference).

## G5: Upgrade and size (SC-001, SC-007)

Install the `0372d0f` debug build, seed data as in Spec 025 T005, then install this build over it. Every store must be intact. Release APK size must be within ±2 % and cold start within ±5 %. The Compose report must still show 0 non-skippable.

## G6: CI

All 6 jobs green on the PR, including the API 29 instrumented job.
