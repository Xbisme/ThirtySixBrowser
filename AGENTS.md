# AGENTS.md

ThirtySix Browser: a private, offline-first Android browser. Kotlin, Jetpack Compose, Hilt,
feature-first Clean Architecture with MVVM. Package `com.raumanian.thirtysix.browser`.
Rules: `.specify/memory/constitution.md`. Reasons: `docs/adr/`. History: git and `specs/`.

## Commands

Prefix every Gradle call with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
and check for `BUILD SUCCESSFUL`.

| Task | Command |
|---|---|
| Build | `./gradlew assembleDebug` |
| Unit tests, including architecture rules | `./gradlew testDebugUnitTest` |
| One test class | `./gradlew testDebugUnitTest --tests '*BrowserViewModelTest'` |
| Static checks | `./gradlew detekt ktlintCheck lintDebug` |
| Instrumented tests | `adb shell am force-stop com.raumanian.thirtysix.browser.debug && ANDROID_SERIAL=<emulator> ./gradlew connectedDebugAndroidTest` |
| Compose compiler report | `./gradlew assembleRelease -PcomposeReports` |
| 16 KB check | `./gradlew assembleRelease && .specify/scripts/bash/verify-16kb-alignment.sh app/build/outputs/apk/release/app-release.apk` |
| Comment edit check | `python3 scripts/comment-check.py <files>` |

Run the static checks and unit tests before calling any task done.

## Where code goes

```
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── MainActivity.kt, ThirtySixApplication.kt
├── navigation/            AppNavHost: the only place that joins features
├── core/                  shared by two or more features; never imports a feature
│   ├── data/              Room database, DAOs, entities, migrations, DataStore, Android impls
│   ├── domain/            shared models, repository contracts, use cases, util
│   ├── presentation/      designsystem/{theme,component}, util
│   └── di/
└── feature/<f>/           browser, tabs, bookmarks, history, downloads, settings,
    │                      onboarding, privacy, sitepermission, entrypoint
    ├── data/{datasource/local, mapper, repository}
    ├── domain/{model, repository, usecase}      pure Kotlin
    ├── presentation/<screen>/ and presentation/navigation/
    └── di/
```

`ArchitectureTest`, `PlacementTest`, `NoHardcodeTest` and `CommentPolicyTest` in
`app/src/test/.../architecture/` fail the build when a file is in the wrong place or a layer
reaches the wrong way. Fix the code, not the test. `KNOWN_VIOLATIONS` only shrinks.

## Writing code

- ViewModel → UseCase → Repository → DataSource. ViewModels expose `StateFlow<UiState>` only.
- Screens are `XRoute` + `XScreen(state, onAction)`; input is `onAction(XAction)`, one-off effects
  are `XEvent`. A use case that would only forward one call is not written: call the contract.
- One feature opens another only through callbacks filled in by `AppNavHost`.
- Inject `DispatcherProvider`; only `di/` names `Dispatchers.IO`. Long-lived work uses `@ApplicationScope`.
- Text from `stringResource` in all 8 locales, colors and type from `MaterialTheme`, no magic numbers.
- Lazy list items need a `key` and a `contentType`. Read per-frame state in `offset {}`,
  `graphicsLayer {}` or `drawBehind {}`.
- Every address the app loads goes through `loadProtected`; incognito writes nothing to normal browsing.
- Schema changes need an additive Room migration and the exported schema JSON.
- Look up the latest stable version before adding a dependency and put it in
  `gradle/libs.versions.toml`.

## Comments

Write none unless the code cannot say it: a non-obvious reason, a platform workaround (link
the issue), or an outside constraint. Keep it to 1–3 lines.

Never write spec/task IDs (`Spec 018`, `FR-015`, `T042`), file citations like `research.md`,
change history, ALL-CAPS or emoji banners, or comments that repeat the code. Put
traceability in the commit message and PR. `TODO` needs a ticket: `TODO(APP-123): …`.

detekt and `CommentPolicyTest` enforce this.

## Tests

Mirror `main/`. Fakes of core contracts are in `core/testing/fake/`; a feature's own fakes and
sample data in its `fake/` and `fixture/`. State-holder tests set the main dispatcher
with `MainDispatcherRule`. Instrumented tests that launch `MainActivity` replace
`SettingsDataStoreProviderModule` with a per-test DataStore.

## Devices

- Use the AVDs (`TA016_API36_16K`, `TA016_API29_CI`, `TA016_API24`) with animations off.
- The Galaxy A55 is the owner's phone: never run instrumented tests on it, and check
  `dumpsys window | grep mCurrentFocus` before scripted input (`docs/adr/0017`).
- Never install an older build over a newer one: Room has no downgrade migration.
