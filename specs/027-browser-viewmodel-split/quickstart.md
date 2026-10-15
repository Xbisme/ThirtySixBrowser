# Quickstart: validating the browser split

Prefix Gradle with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. AVDs only.

## G0 Baseline (`90cd024`)
Unit count, instrumented count on `TA016_API36_16K`, release APK bytes, Compose report, file sizes:
`wc -l app/src/main/kotlin/com/raumanian/thirtysix/browser/feature/browser/presentation/browser/*.kt`,
size-suppression count in `feature/browser`. Keep `0372d0f`-style baseline APKs of `90cd024` in the scratchpad.

## G1 Every commit
`./gradlew detekt ktlintCheck lintDebug testDebugUnitTest assembleDebug compileDebugAndroidTestKotlin`

## G2 Shape (SC-002 … SC-004, SC-006)
- no file in `feature/browser/presentation/browser/` over 300 lines except `BrowserWebView.kt`, `BrowserChromeClient.kt`
- size suppressions in `feature/browser` ≤ 19
- each new holder ≤ 10 constructor parameters, ≤ 12 action cases
- `grep -r BaseViewModel app/src` → nothing

## G3 Device (SC-001, SC-005)
Instrumented suite; upgrade over seeded `90cd024` debug; 20+ interleaved cold starts; behaviour walk as Spec 026 (Home and address submit, star across rotation, desktop site, find in page after a new load and a tab switch, download request, Back to Home, history rows after redirects, incognito writes nothing).

## G4 CI 6/6 on the PR.
