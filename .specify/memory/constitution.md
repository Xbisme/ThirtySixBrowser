# ThirtySix Browser Constitution

Android app: Kotlin, Jetpack Compose, Hilt, feature-first Clean Architecture with MVVM.

**How this file works**

- Every MUST names the check that enforces it (**Enforced by**). A rule no machine can check
  says **Review** and is checked by the reviewer.
- A new MUST ships with its check in the same change. Otherwise it is written as SHOULD.
- Rules hold for new code, and for old code once it is touched. A file that breaks a rule
  before the rule existed is listed in that check's allowlist by path, never exempted in
  silence.
- Commands and the package map are in `AGENTS.md`. Reasons are in `docs/adr/`. This file
  holds only rules.

## Core Principles

### I. Feature-first Clean Architecture (NON-NEGOTIABLE)

- Code lives in `core/` or `feature/<name>/`. Each feature has `data/`, `domain/`,
  `presentation/` and `di/`. App-level wiring (`MainActivity`, `ThirtySixApplication`,
  `navigation/`) is the only code allowed to reference several features.
- Dependencies point inward: `presentation → domain ← data`. `di/` may see `domain` and `data`.
- `domain/` is pure Kotlin. It imports no `android.*`, `androidx.*`, Retrofit, OkHttp or
  kotlinx.serialization.
- A feature never imports another feature, and `core/` never imports a feature. Code used by
  two or more features moves to `core/`; one feature opens another only through callbacks
  that `navigation/AppNavHost` fills.
- Room entities never leave `data/`; mappers convert them at the boundary.

**Enforced by**: `ArchitectureTest` (Konsist).

### II. Every type has one home

| Type | Package |
|---|---|
| `*ViewModel`, `*UiState` | `feature/<f>/presentation/<screen>/` (or `core/presentation/`) |
| `*UseCase` | `feature/<f>/domain/usecase/` or `core/domain/usecase/` |
| `*Repository` contract (`interface` or `abstract class`) | `…/domain/repository/` |
| `*RepositoryImpl` | `feature/<f>/data/repository/` |
| `*Dao` | `core/data/datasource/local/` |
| `*Entity` | `core/data/datasource/local/entity/` |
| Hilt `@Module` | `feature/<f>/di/` or `core/di/` |

Domain models never carry a data-layer suffix (`Dto`, `Entity`, `Response`, `Request`).
Implementations are named `<Interface>Impl`, recorded in `docs/adr/0001`.

**Enforced by**: `PlacementTest` (Konsist).

### III. Unidirectional data flow

- `Screen → ViewModel → UseCase → Repository → DataSource`. A ViewModel may call a repository
  interface directly when the use case would only forward the call.
- A ViewModel never receives a DataSource, Dao, entity or a repository implementation.
- A ViewModel exposes state as `StateFlow<XUiState>`. Mutable flows and Compose state stay
  private. `UiState` is a `data class` or a `sealed` type with `val` properties only.
- User input reaches the ViewModel as `onAction(XAction)`. One-off effects (navigate, toast)
  are emitted as `XEvent`.
- Dispatchers are injected. Only `di/` names `Dispatchers.IO` or `Dispatchers.Default`.
  Long-lived collectors run in the injected `@ApplicationScope` scope.
- DI is Hilt. Annotation processing runs on KSP; kapt is forbidden.

**Enforced by**: `PlacementTest`, `ArchitectureTest.dispatchers are injected`,
`ArchitectureTest.view models take input only through onAction` (Konsist); compose-rules
`vm-forwarding-check` and `vm-injection-check` with no baseline.
**Review**: Action/Event shape inside a holder (sealed types in the screen folder), KSP.

### IV. Compose render discipline

- State that changes every frame (scroll, drag offset, progress, animation values) is read
  inside `Modifier.offset {}`, `graphicsLayer {}` or `drawBehind {}`, never in a composable
  body. A parameter that carries it is typed `() -> T`.
- A screen collects its `UiState` once, at the route, and passes each child only the fields it
  needs. A field that changes many times per second is exposed as its own flow.
- Values derived from changing state use `derivedStateOf`; expensive work uses `remember(keys)`.
- Composable parameters are stable: `@Immutable` models, `kotlinx.collections.immutable`, or
  types listed in `config/compose/stability.conf`.
- Lazy lists give every item a stable `key` and a `contentType`, and never nest lists that
  scroll the same direction.
- The app is edge-to-edge, so every bar, sheet and floating control consumes `WindowInsets`.
- Effect keys are exactly the values the effect reads; long-lived callbacks go through
  `rememberUpdatedState`.

**Enforced by**: ktlint compose-rules. A spec that adds UI attaches the Compose compiler
report (`-PcomposeReports`) with zero unexplained non-skippable composables. **Review**:
phase of each read, insets, effect keys.

### V. No hardcoded values

| Kind | Lives in |
|---|---|
| User-facing text | `res/values*/strings.xml` (all 8 locales) |
| Colors, typography, shapes, spacing | `core/presentation/designsystem/theme/` |
| Limits, timeouts, durations, defaults | a named `object` in `core/` or the feature that owns it |
| Preference keys, nav arguments, intent extras | constants next to their single owner |
| URLs and build-specific values | named constants or `BuildConfig` fields |

Allowed inline: `0`, `1`, `-1`, `2`, `""`, and anything in tests or `@Preview`.

**Enforced by**: `NoHardcodeTest` (Konsist: string literals in `Text`/`contentDescription`,
`Color(0x…)` outside the theme), detekt `MagicNumber`, Android Lint `HardcodedText`,
`MissingTranslation` and `ExtraTranslation` as errors. **Review**: `dp` values repeated in
several places.

### VI. Comments explain why, only where the code cannot (NON-NEGOTIABLE)

- The default is no comment. Names, types and small functions carry the meaning.
- Write a comment only for something the code cannot show: a non-obvious reason, a platform
  quirk or workaround (link the issue), an outside constraint (API contract, store policy),
  or an algorithm whose correctness is not evident.
- Keep it to 1–3 lines. KDoc goes on public API only when the name and signature are not
  enough. `@Suppress` gets a one-line reason.
- Never in source:
  - spec, task or requirement IDs and citations of planning documents or of this file;
  - change history ("previously", "used to be"), authors or dates;
  - comments that restate the code;
  - ALL-CAPS or emoji banners;
  - commented-out code;
  - `TODO` without a ticket (write `TODO(APP-123): …`).
- Traceability lives in commit messages, PR descriptions and `specs/`, not in code.

**Enforced by**: detekt `ForbiddenComment` over main, test, androidTest and debug sources,
`CommentPolicyTest` (a comment block is at most 5 lines; comments are at most 15% of a
production file of 30+ lines), detekt `OutdatedDocumentation`. **Review**: comments that
restate the code.

### VII. Build and dependencies

- Versions live only in `gradle/libs.versions.toml`. The latest stable version is looked up
  when a dependency is added, never taken from memory, and the date is recorded in the PR.
- AGP 9: no `org.jetbrains.kotlin.android` plugin. The Gradle daemon and unit tests run on
  JDK 21 (`gradle/gradle-daemon-jvm.properties`); compilation targets Java 11.
- Build types are `debug` and `release`; there are no product flavors.
- `release` has `isMinifyEnabled` and `isShrinkResources` on. Every keep rule in
  `proguard-rules.pro` has a one-line reason.
- Every native library (`.so`) is 16 KB page aligned.
- Signing keys and credentials are never committed. Distribution builds use the release
  keystore; local and CI builds may fall back to the debug key with a printed warning.
- The schema changes only through additive Room migrations with the exported schema JSON;
  `fallbackToDestructiveMigration` is never used.

**Enforced by**: `.specify/scripts/bash/verify-16kb-alignment.sh` on release builds,
`AppDatabaseMigrationTest`. **Review**: catalog use, version lookup, keep rules.

### VIII. Tests

- Unit tests cover use cases, repositories, mappers and ViewModels. ViewModels are tested with
  Turbine and a test main dispatcher.
- Tests use fakes (`Fake*Repository`) for the project's own interfaces; mocks only for
  third-party types.
- Test folders mirror `main/`. Fakes of `core` contracts are in `core/testing/fake/`; a
  feature's own fakes and sample data are in its `fake/` and `fixture/` folders.
- DAOs are tested on in-memory Room. Critical flows have instrumented tests: opening a link
  from another app, normal and incognito tabs, the bookmark star, history recording, Fire,
  tracker blocking and incognito isolation.

**Enforced by**: `./gradlew testDebugUnitTest` and `connectedDebugAndroidTest` in CI;
`ArchitectureTest.main dispatcher is set only by the rule` (Konsist).
Coverage target: every use case, repository, mapper and ViewModel has a test.

## Product Constraints

- **Data and privacy**: no account, no cloud sync, no analytics or crash reporting, and
  nothing is sent to a server run by ThirtySix. All user data stays on the device; the
  database is excluded from backup and device transfer, settings are included. Incognito
  writes no history, favicons, screenshots or cookies to normal browsing.
- **Web content**: `android.webkit.WebView` only. Page scripts come only from files in the
  APK, run one way (nothing comes back to the app) and are installed in one place. No
  `addJavascriptInterface` unless it is origin-gated and justified in a spec. Tracker lists
  and other protection data ship in the APK and are never fetched.
- **Permissions**: only the permissions in this table may be declared, and each one is
  requested at the moment it is used.

  | Permission | Why | Spec |
  |---|---|---|
  | `android.permission.INTERNET` | Load web pages | 007 |
  | `android.permission.ACCESS_NETWORK_STATE` | Detect being offline | 007 |
  | `android.permission.WRITE_EXTERNAL_STORAGE` (`maxSdkVersion="28"`) | Save downloads to the public Downloads folder on Android 7–9, before Scoped Storage | 015 |
  | `android.permission.CAMERA` | A site the person allowed uses the camera; photo capture for an upload | 021 |
  | `android.permission.RECORD_AUDIO` | A site the person allowed uses the microphone | 021 |
  | `android.permission.ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | A site the person allowed uses location (approximate is enough) | 021 |
  | `android.permission.MODIFY_AUDIO_SETTINGS` | Normal permission WebView needs with `RECORD_AUDIO` | 021 |

  `POST_NOTIFICATIONS` is not declared: the system download service posts its own
  notifications. `MANAGE_EXTERNAL_STORAGE` is never declared.

- **Performance budgets**: cold start no more than 5% slower than the previous release,
  measured in interleaved rounds on the same device; release APK ≤ 4 MB. Frame-time targets
  are measured on a release build on Pixel 5-class hardware, never on an emulator
  (`docs/adr/0017`).
- **Locales**: EN (default), VI, DE, RU, KO, JA, ZH, FR. Accessibility: 48dp touch targets,
  WCAG AA contrast, TalkBack labels on every interactive element.
- **SDK levels**: minSdk 24, targetSdk 36, compileSdk 36. targetSdk is raised within 12
  months of each Android release.
- **Store**: Google Play, category Tools, content rating Everyone, free.

## Quality Gates

A spec is done only when all of these pass:

1. `./gradlew detekt ktlintCheck`
2. `./gradlew testDebugUnitTest`, which includes the architecture, placement, hardcode and comment tests
3. `./gradlew lintDebug`
4. Release builds: `./gradlew assembleRelease` and the 16 KB alignment script
5. `./gradlew connectedDebugAndroidTest` (CI on API 29; locally on the 16 KB AVD)
6. Specs with UI: the Compose compiler report, and a recomposition count for each changed
   screen's hot interaction: an instrumented test that runs the interaction and asserts an
   upper bound on recompositions, or a Layout Inspector count when no test can drive it

## Governance

- This constitution overrides other project documents. `/speckit.plan` turns every
  **Enforced by** line into a Constitution Check gate. A justified exception goes in the
  plan's Complexity Tracking table.
- When the code and this file disagree, one of them is fixed in the same change. Drift is
  never left for later.
- An amendment updates `AGENTS.md` and, when it changes a decision, adds an ADR, all in the
  same commit. It adds one line to `docs/constitution-history.md`.
- Versioning: MAJOR when a principle is removed or redefined, MINOR when a principle or
  a check is added, PATCH for wording.

**Version**: 2.2.0 | **Ratified**: 2026-04-30 | **Last Amended**: 2026-10-17
