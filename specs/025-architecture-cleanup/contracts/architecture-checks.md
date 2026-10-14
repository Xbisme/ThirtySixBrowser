# Contract: Architecture checks

The interface this spec exposes to contributors is the set of checks the build runs. Each row is a rule, where it lives, how it fails, and the constitution principle it enforces. Source of the check code: the template's `app/src/test/java/com/example/app/architecture/*` and `config/`, adapted only in `ROOT` and paths.

## Unit-test checks (`./gradlew testDebugUnitTest`)

Location: `app/src/test/kotlin/com/raumanian/thirtysix/browser/architecture/`.

| Test | Rule | Principle |
|---|---|---|
| `ArchitectureTest.layers depend only inward` | domain → nothing; data → domain; presentation → domain; di → domain, data | I |
| `ArchitectureTest.domain is pure Kotlin` | no `android.`, `androidx.`, `retrofit2.`, `okhttp3.`, `kotlinx.serialization.` imports in `..domain..` | I |
| `ArchitectureTest.features do not import other features` | `feature.<a>` never imports `feature.<b>` | I |
| `ArchitectureTest.core does not import features` | `core..` never imports `feature..` | I |
| `PlacementTest.*` (8 tests) | placement table in [data-model.md](../data-model.md#placement-rules-what-the-checks-enforce); view models take no data-layer types; view models expose no mutable state; `*UiState` is `data`/`sealed` with `val`s only | II, III |
| `NoHardcodeTest.composables take text from string resources` | no `Text("…")` / `contentDescription = "…"` outside `@Preview` | V |
| `NoHardcodeTest.colors come from the theme` | no `Color(0x` outside `..designsystem.theme` | V |
| `CommentPolicyTest.comment blocks stay short` | ≤ 5 comment lines in a row | VI |
| `CommentPolicyTest.files are mostly code` | ≤ 15 % comment lines in production files of ≥ 30 lines | VI |

Failure output: Konsist's assertion message lists every offending declaration with its file path, plus the `additionalMessage` that says where it belongs.

`KonsistScope.kt`: `ROOT = "com.raumanian.thirtysix.browser"`; `productionScope()` = `Konsist.scopeFromProduction()` minus `KNOWN_VIOLATIONS` (empty at the end of this spec). The `debug` source set (`HiltTestActivity`, `dev.*` seeders) is not production scope.

## Static checks (`./gradlew detekt ktlintCheck lintDebug`)

| Check | Config | Rule | Principle |
|---|---|---|---|
| detekt `ForbiddenComment` | `config/detekt/detekt.yml`; detekt `source` set to `src/main/kotlin`, `src/test/kotlin`, `src/androidTest/kotlin`, `src/debug/kotlin` so test comments are checked too | spec / requirement / task IDs, planning-doc citations, constitution citations, change history, emoji and ALL-CAPS banners, TODO without `(ABC-123)`, FIXME/STOPSHIP | VI |
| detekt `OutdatedDocumentation` | same | KDoc matches signature | VI |
| detekt `InjectDispatcher` | same, excludes `**/di/**`, tests | no `Dispatchers.IO/Default` outside `di/` | III |
| detekt `MagicNumber` | same (existing settings kept), `ignoreAnnotated: [Preview]` | V |
| ktlint + compose-rules 0.6.8 | `.editorconfig` (`compose_allowed_composition_locals` lists this app's locals) | Compose API rules | IV |
| Android Lint | `app/build.gradle.kts` (unchanged: `warningsAsErrors`, `MissingTranslation`, `ExtraTranslation` as errors) | resources, translations, platform API | V, product constraints |

`.editorconfig` `compose_allowed_composition_locals = LocalThirtySixColors,LocalThirtySixShapes,LocalIoDispatcher`.

Config moves: `detekt.yml` → `config/detekt/detekt.yml`; `compose_stability.conf` → `config/compose/stability.conf`; `app/detekt-baseline.xml` → `config/detekt/baseline.xml` (temporary, deleted at the end).

## Release checks

| Check | Command | Principle |
|---|---|---|
| 16 KB alignment | `./gradlew assembleRelease && .specify/scripts/bash/verify-16kb-alignment.sh app/build/outputs/apk/release/app-release.apk` (existing script, kept where CI calls it) | VII |
| Compose compiler report | `./gradlew assembleRelease -PcomposeReports` — zero unexplained non-skippable composables (today 206 restartable, 0 non-skippable) | IV |

## Proof each rule bites (SC-004)

For each row above, a throwaway commit adds one violation, the matching check fails with a message naming the file, and the commit is dropped. Results recorded in tasks.md.
