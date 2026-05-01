# Research: DataStore Settings

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date verified**: 2026-05-01

Phase 0 research consolidating decisions for the DataStore Preferences persistence layer. Each entry follows: **Decision** / **Rationale** / **Alternatives considered**.

---

## R1 — DataStore version + 16 KB compliance

**Decision**: Adopt **`androidx.datastore:datastore-preferences:1.2.1`** (released 2026-03-11), pulled in via a single Maven coordinate at `implementation` scope.

**Rationale**:

- Latest stable per `developer.android.com/jetpack/androidx/releases/datastore` verified 2026-05-01.
- Compatible with project's existing **Kotlin 2.3.21 + AGP 9.1.1**: DataStore 1.2.x targets Kotlin 1.9+ and supports the latest 2.x line; no plugin requirements (KSP not used by DataStore Preferences — typed Preferences are key-value at runtime, no codegen).
- minSdk requirement is API 21 (DataStore 1.x baseline) — project minSdk 24 is comfortably above the floor.
- Pure Kotlin/Java with **zero native shared libraries** — Constitution §IX 16KB CI gate auto-passes. Confirmed by inspecting the published artifact's POM (no `<packaging>aar</packaging>` jniLibs declarations) and by spot-check of `androidx.datastore:datastore-core:1.2.1` (the transitive dep) which is also pure Kotlin.
- `1.2.1` is a maintenance patch over `1.2.0` (minor infra fixes, no API/behavior change) — safe drop-in for greenfield consumers.
- Bound under a new `datastore` version key in `gradle/libs.versions.toml` so future bumps stay catalog-managed.

**Alternatives considered**:

- **DataStore 1.3.0-alpha08** (2026-04-22): rejected — alpha-only, violates Constitution §IX hard requirement of "latest **stable** version". Headline new features in alpha (Tink encryption, OPFS, `DataStore.Builder<T>`) are not needed for v1.0 — encryption is out of scope per spec Assumptions; OPFS is web-platform-only; the new builder API is a quality-of-life improvement not a correctness fix. Re-evaluate when 1.3.0 reaches stable.
- **Proto DataStore (`androidx.datastore:datastore`)**: rejected — CLAUDE.md Architecture Decisions table fixes "DataStore Preferences" as the chosen variant. Proto DataStore would add a `.proto` schema file + protobuf-gradle-plugin + an extra typed-codec layer — overkill for 4 simple keys. The chosen Preferences variant gives us untyped key-value storage at the file boundary and we layer the typing through `SettingsMapper` ourselves; this keeps the dependency footprint smaller and aligns with the project Tech Stack already documented.
- **SharedPreferences**: rejected — Constitution §VII implicitly forbids the legacy SharedPreferences API by naming DataStore directly in Tech Stack. SharedPreferences also blocks the main thread on first read; DataStore is `suspend` everywhere.
- **Encrypted DataStore (`security-crypto-datastore`)**: rejected — out of scope per spec Assumptions ("Encryption-at-rest is not required for v1.0"). Reconsider if a future spec adds a credential-bearing key.

**Source**: [developer.android.com/jetpack/androidx/releases/datastore](https://developer.android.com/jetpack/androidx/releases/datastore) — verified 2026-05-01.

---

## R2 — Hilt provider strategy for `DataStore<Preferences>`

**Decision**: Provide `DataStore<Preferences>` as a `@Singleton` via the top-level `app/.../di/SettingsModule.kt` Hilt module, using the official `PreferenceDataStoreFactory.create(...)` builder bound to a `Context.dataStoreFile(AppConstants.SETTINGS_DATASTORE_FILE_NAME)` location.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        dispatcherProvider: DispatcherProvider,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(dispatcherProvider.io + SupervisorJob()),
        produceFile = { context.dataStoreFile(AppConstants.SETTINGS_DATASTORE_FILE_NAME) },
    )
}
```

**Rationale**:

- `PreferenceDataStoreFactory.create(...)` is the official entry point for DataStore Preferences with full control over scope and file location. It is the same factory the official `preferencesDataStore` delegate uses internally.
- Singleton scope is required by DataStore: creating two `DataStore` instances over the same file from the same process throws `IllegalStateException` at runtime. Hilt's `@Singleton` enforces this structurally.
- `dispatcherProvider.io` reuses the existing `DispatcherProvider` from Spec 002 (`core/dispatcher/`). The `SupervisorJob()` ensures one consumer's failure does not cancel the entire DataStore pipeline.
- `context.dataStoreFile(...)` is a DataStore-provided extension that resolves to `<files-dir>/datastore/<name>.preferences_pb` per Android filesystem conventions. The `.preferences_pb` extension is appended automatically when the file is initially created.
- Module placement at top-level `app/.../di/SettingsModule.kt` matches the precedent set by Spec 005's `DatabaseModule.kt` (Constitution §IV "ALL @Module annotations live in `di/` package" — both `core/di/` and top-level `app/.../di/` are valid; we choose top-level for application-scoped composites).

**Alternatives considered**:

- **`Context.preferencesDataStore(name = ...)` Kotlin property delegate**: rejected — the delegate creates the DataStore once-per-Context-instance, which works for single-Activity apps but does NOT integrate cleanly with Hilt singleton injection. Tests cannot easily substitute a temp-file DataStore because the delegate captures the Context. The factory pattern lets us swap providers per test scope.
- **Provide `SettingsDataStore` (our wrapper) directly without exposing the underlying `DataStore<Preferences>`**: rejected — exposing `DataStore<Preferences>` as the Hilt-managed singleton gives us a clean test seam (substitute the singleton in tests with a temp-file DataStore) and lets future code (e.g., a hypothetical settings exporter spec) read raw `Preferences` if needed without touching `SettingsRepositoryImpl`. The wrapper layer is constructed in the impl, not in the module.

---

## R3 — Backup posture for settings file (FR-013, FR-014)

**Decision**: Modify both `app/src/main/res/xml/backup_rules.xml` (Android < 12) and `app/src/main/res/xml/data_extraction_rules.xml` (Android 12+) to **explicitly INCLUDE** the DataStore Preferences file in cloud backup AND device-to-device transfer. Add a top-of-file comment cross-referencing Spec 005's policy contrast (DB excluded vs settings included).

**Implementation**:

The default Android Auto Backup behavior (when `android:allowBackup="true"`) is to back up everything in `<files-dir>/`. Spec 005 added explicit `<exclude>` entries for the database; Spec 006 inherits that file but does NOT add any `<exclude>` for the DataStore file → settings remain included by default. The change Spec 006 ships is **a comment block documenting the asymmetric policy**, plus an explicit `<include>` block on the `device-transfer` side of `data_extraction_rules.xml` to make the "settings INCLUDED" decision visible at code review (rather than relying on default-include semantics that a future maintainer might overlook).

```xml
<!-- backup_rules.xml (Android < 12) -->
<!--
  Per-store backup policy (Spec 005 + Spec 006):
  - DATABASE (thirtysix_browser.db): EXCLUDED — privacy-sensitive
    (bookmarks, history, tabs may contain URLs the user wishes to
    keep on-device only). See Spec 005.
  - SETTINGS (thirtysix_settings.preferences_pb): INCLUDED (default) —
    no PII; restoring on a new device preserves theme/language/search-
    engine UX. See Spec 006 FR-013, FR-014.
-->
<full-backup-content>
    <exclude domain="database" path="thirtysix_browser.db"/>
    <exclude domain="database" path="thirtysix_browser.db-wal"/>
    <exclude domain="database" path="thirtysix_browser.db-shm"/>
    <include domain="file" path="datastore/thirtysix_settings.preferences_pb"/>
</full-backup-content>
```

```xml
<!-- data_extraction_rules.xml (Android 12+) -->
<!-- (same comment block as above) -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="thirtysix_browser.db"/>
        <exclude domain="database" path="thirtysix_browser.db-wal"/>
        <exclude domain="database" path="thirtysix_browser.db-shm"/>
        <include domain="file" path="datastore/thirtysix_settings.preferences_pb"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" path="thirtysix_browser.db"/>
        <exclude domain="database" path="thirtysix_browser.db-wal"/>
        <exclude domain="database" path="thirtysix_browser.db-shm"/>
        <include domain="file" path="datastore/thirtysix_settings.preferences_pb"/>
    </device-transfer>
</data-extraction-rules>
```

**Rationale**:

- The `<include>` line is technically redundant when `allowBackup="true"` is the default, BUT it makes the policy explicit at the file level — satisfies FR-014's "documented in the same place that the per-store backup posture is configured".
- `domain="file"` + `path="datastore/thirtysix_settings.preferences_pb"` matches DataStore's actual on-disk layout (`<files-dir>/datastore/<name>.preferences_pb`). Verified against AOSP source: `androidx.datastore.core.MultiProcessCoordinator` and `Context.dataStoreFile()` extension both compute this path.
- Comment block is the audit trail for the asymmetric policy — Spec 005 already established the DB-exclude pattern, and a future maintainer reading either XML will see why settings differs.

**Alternatives considered**:

- **Rely on default-include without `<include>` line**: rejected — silent defaults invite future regressions (a maintainer adding an unrelated `<exclude>` could accidentally make settings exclude-by-omission). Explicit is better than implicit for a privacy-sensitive policy.
- **Set `android:allowBackup="false"` and selectively `<include>` settings**: rejected — flips the default for all future `<files-dir>/` content, requiring future specs to opt-in everything. Friction without benefit.

---

## R4 — Q1 implementation: setter `Result<Unit>` contract

**Decision**: Each setter on `SettingsDataStore` and `SettingsRepository` returns `Result<Unit>` (the existing `core/result/Result.kt` 2-state wrapper from Spec 002). The implementation pattern:

```kotlin
// SettingsDataStore.kt (data layer wrapper)
suspend fun setThemeMode(mode: ThemeMode): Result<Unit> = try {
    dataStore.edit { prefs ->
        prefs[StorageKeys.THEME_MODE] = mode.storageValue
    }
    Result.Success(Unit)
} catch (io: IOException) {
    Result.Error(AppError.from(io))
} // CancellationException is NOT caught — propagates per coroutine cancellation idiom
```

**Rationale**:

- Q1 clarification (see [spec.md § Clarifications](spec.md#clarifications)) chose Option A: return `Result<Unit>`. Consistent with Spec 002's `Result<T>` 2-state wrapper (`Success<T>` / `Error(AppError)`).
- `IOException` is the canonical exception class DataStore raises for disk failures (disk full, permission denied, file system corruption during write). The existing `AppError.from(IOException)` mapper from Spec 002 maps to `AppError.Database` automatically.
- `CancellationException` is NOT caught — re-thrown to preserve coroutine cancellation idiom. Same convention as `BaseViewModel.launchSafely` from Spec 002.
- `dataStore.edit { ... }` is the official mutation API — atomic at the protobuf-file level (single-file replace). Returns the new `Preferences` snapshot, which the wrapper discards (we just need success/failure).
- The `Result<Unit>` contract bubbles up unchanged through `SettingsRepositoryImpl` and the 4 use cases — no duplicate try/catch in the higher layers (use case signature: `suspend operator fun invoke(mode: ThemeMode): Result<Unit> = repository.setThemeMode(mode)`).

**Alternatives considered**:

- **Throw `IOException` from setters, let caller use `BaseViewModel.launchSafely`**: rejected per Q1 (Option B). Reasons restated: less type-safe at call site (caller must know which exceptions to catch); breaks the "explicit error handling" pattern the spec entity for `Result` already establishes; harder to test deterministically.
- **Fire-and-forget setters with separate `Flow<SettingsError>`**: rejected per Q1 (Option C). Adds a second observable stream in parallel to `Flow<UserSettings>` — two cognitive surfaces for consumers, error-prone in tests.

---

## R5 — Q2 implementation: `LanguageOverride` sealed type encoding

**Decision**: `LanguageOverride` lives in `domain/model/LanguageOverride.kt` as a sealed interface with two variants:

```kotlin
sealed interface LanguageOverride {
    data object FollowSystem : LanguageOverride
    data class Explicit(val bcp47: String) : LanguageOverride
}
```

On-disk representation in DataStore Preferences uses a nullable `String?` (key `language_override` of type `stringPreferencesKey`):

| Domain value | DataStore on-disk |
|---|---|
| `LanguageOverride.FollowSystem` | absent key OR explicit `null` write (treated identically by mapper) |
| `LanguageOverride.Explicit("vi")` | `"vi"` |
| `LanguageOverride.Explicit("zh")` | `"zh"` |

Mapper rule:

```kotlin
// SettingsMapper.kt — encode
fun LanguageOverride.toStorage(): String? = when (this) {
    LanguageOverride.FollowSystem -> null
    is LanguageOverride.Explicit -> bcp47
}

// decode
fun String?.toLanguageOverride(): LanguageOverride =
    if (this.isNullOrBlank()) LanguageOverride.FollowSystem
    else LanguageOverride.Explicit(this)
```

**Rationale**:

- Q2 clarification chose Option B: sealed interface. Domain consumers branch via exhaustive `when`, never null-check.
- `data object FollowSystem` (Kotlin 1.9+ syntax) gives free `equals`/`hashCode`/`toString` and enables the `data` keyword consistency between both variants (cosmetic but readable).
- Storage uses nullable `String?` rather than a reserved sentinel string ("system", "follow_system", etc.) — avoids the risk of future regional BCP-47 tags ever colliding with a sentinel. Absent-key + null-write are normalized to `FollowSystem` at decode time so a future maintainer can't accidentally produce divergent behavior by writing one form vs the other.
- `isNullOrBlank()` defensive check: handles the "user downgraded from a future build that wrote an empty string" edge case (FR-009 spirit applied to language).
- BCP-47 validation is NOT done at the model boundary in v1.0 — Assumptions explicitly defer validation to Spec 016's picker which constrains the input set to Spec 004's eight locales. If a future caller writes an unsupported tag, Spec 016's `AppCompatDelegate.setApplicationLocales` will simply not match any locale resource and Android will fall back per its own rules — the same fallback that already protects Spec 004.

**Alternatives considered**:

- **`String?` directly in `UserSettings.languageCode`** (Option A from Q2): rejected. Spec entity language: "first-class value, not absence of a value." Null-as-sentinel is exactly the pattern we are avoiding.
- **`enum class LanguageOverride { FollowSystem, Vi, De, ... }`**: rejected — couples the enum to the hardcoded 8-locale set; a future spec adding a 9th locale would force a domain-model change AND a DataStore migration. Sealed interface with `Explicit(bcp47)` keeps the locale list as a runtime constraint not a compile-time one.
- **Sentinel string `"system"`**: rejected per Q2 (Option C). BCP-47 reserved-tag risk already discussed.

---

## R6 — `UserSettings.DEFAULT` initial value for `collectAsStateWithLifecycle`

**Decision**: Expose a `companion object DEFAULT` on `UserSettings` that returns the documented defaults from `AppDefaults`. `MainActivity` collects the `Flow<UserSettings>` with `collectAsStateWithLifecycle(initialValue = UserSettings.DEFAULT)` to render the first frame without blocking on disk I/O.

```kotlin
data class UserSettings(
    val themeMode: ThemeMode,
    val languageOverride: LanguageOverride,
    val searchEngine: SearchEngine,
    val isOnboardingCompleted: Boolean,
) {
    companion object {
        val DEFAULT = UserSettings(
            themeMode = AppDefaults.THEME_MODE,
            languageOverride = AppDefaults.LANGUAGE_OVERRIDE,
            searchEngine = AppDefaults.SEARCH_ENGINE,
            isOnboardingCompleted = AppDefaults.IS_ONBOARDING_COMPLETED,
        )
    }
}
```

**Rationale**:

- Resolves the SC-002 "first-frame settings availability" concern: `collectAsStateWithLifecycle` requires an initial value when the Flow has not yet emitted; we provide one that is **identical** to what FR-008 says the system MUST return for a never-written key, so the initial composition is **always** visually correct on a fresh install (no flash, no fallback UI).
- For a returning user with persisted settings, the initial composition shows defaults momentarily then recomposes once when the real value arrives from disk. The Spec 003 windowBackground-flash-fix in `themes.xml` + `values-night/themes.xml` already handles the milliseconds-of-flash gap for the dark theme case (by setting the window background per system-night-mode).
- For US1 acceptance scenario 1 ("user previously chose Dark, kill+restart, no flash of Light or System"): the windowBackground-flash-fix protects against the cold-start gap; the recomposition into the persisted theme happens before any visible Compose content. SC-001 is achievable.

**Alternatives considered**:

- **`runBlocking { dataStore.data.first() }` in `MainActivity.onCreate`**: rejected — blocks the main thread on disk I/O, violates Constitution §VII "DataStore writes MUST use suspend API; never block main thread" (extends to reads in spirit).
- **Splash-screen-managed delay until first emission**: rejected — Spec 017 has not landed yet; we cannot couple Spec 006 to a future spec.
- **Synchronous read via reflection/internals**: rejected — fragile, breaks across DataStore versions, and unnecessary given the Flow-with-initial-value pattern works cleanly.

---

## R7 — Schema-evolution rule (FR-019) — implementation

**Decision**: Add a doc-comment block at the top of `core/constants/StorageKeys.kt` with the schema-evolution rules, and reference it from `data/local/datastore/SettingsDataStore.kt`. No runtime enforcement; this is a maintainer-facing rule.

```kotlin
// core/constants/StorageKeys.kt
/**
 * DataStore Preferences keys — single source of truth.
 *
 * SCHEMA EVOLUTION RULES (Spec 006 FR-019):
 * 1. Once a key in this file has shipped to a user via a release build,
 *    its name MUST NOT be reused for a different meaning.
 * 2. Once a key in this file has shipped to a user, its stored value type
 *    MUST NOT change (e.g., String → Int requires a new key).
 * 3. To change semantics: introduce a NEW key, write a one-time migration
 *    that reads the old key, transforms, writes the new key, and clears
 *    the old key. The old key declaration MUST stay in this file marked
 *    @Deprecated until at least one full release cycle has passed.
 * 4. To remove a no-longer-used key: mark @Deprecated for one release
 *    cycle, then delete in the following release.
 *
 * The negative-path test SettingsMapperTest#renamedKey_returnsDefault_oldValueGone
 * codifies rule 1 by simulating a rename and asserting the documented
 * "default returned, old value not silently inherited" behavior.
 */
object StorageKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val LANGUAGE_OVERRIDE = stringPreferencesKey("language_override")
    val SEARCH_ENGINE = stringPreferencesKey("search_engine")
    val IS_ONBOARDING_COMPLETED = booleanPreferencesKey("is_onboarding_completed")
}
```

**Rationale**:

- DataStore Preferences has no built-in schema versioning (Proto DataStore does, but the project chose Preferences). The rule must live in code review + documentation.
- Placing the rule in `StorageKeys.kt` puts it where every key-touching change MUST visit — maximum visibility.
- The negative-path test (SC US7 acceptance scenario 2) demonstrates the rule cannot be silently broken: the test simulates a rename in fixture data and asserts no value-leak.

**Alternatives considered**:

- **Rule in `plan.md` only**: rejected — plan.md is rarely re-opened after implementation; the rule belongs at the maintenance touch point.
- **Runtime version field in DataStore**: rejected — adds complexity without preventing the underlying mistake (a maintainer changing a key's type still produces a runtime read crash; the rule is cheaper than the version-bump migration logic).

---

## R8 — Test strategy: pure JVM `PreferenceDataStoreFactory`

**Decision**: All settings tests use `PreferenceDataStoreFactory.create(scope, file)` with JUnit `@TempFolder` (or `kotlin.io.path.createTempDirectory`) for the file location. **No Robolectric.** Tests run as pure JVM `testDebugUnitTest`.

```kotlin
class SettingsDataStoreTest {
    @get:Rule val tempFolder = TemporaryFolder()
    private lateinit var dataStore: DataStore<Preferences>

    @Before fun setup() {
        val testScope = TestScope(StandardTestDispatcher() + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope.backgroundScope,
            produceFile = { File(tempFolder.newFolder(), "test.preferences_pb") },
        )
    }
}
```

**Rationale**:

- DataStore Preferences (unlike Room) has no Android system dependency — it talks directly to the filesystem via `java.io.File`. Pure JVM works.
- Eliminates Robolectric's per-test-class JVM startup cost (~2 s/class with SDK shadowing) — Spec 005's Room tests pay this; Spec 006 should not.
- Faster CI: full Spec 006 unit-test suite expected to run in < 5 s (vs Spec 005's ~30 s for 29 tests due to Robolectric).
- The `TemporaryFolder` rule auto-cleans the file between tests, preventing cross-test contamination.
- For the concurrent-write test (US6), `runTest { ... }` with `StandardTestDispatcher()` + `launch { ... }` × 2 + `joinAll()` produces deterministic interleaving without real concurrency overhead.

**Alternatives considered**:

- **Robolectric-based tests** (Spec 005 pattern): rejected — Robolectric is not needed because DataStore does not call into the Android framework. Adding it would slow the test suite for no benefit.
- **Instrumented tests on emulator**: rejected — instrumented-test job remains `if: false` in CI per Pending CI / Tooling Tasks (re-enable trigger is Spec 007 or 011). Process-death verification on a real device is the manual gate per spec Assumptions.

---

## R9 — Verification of DataStore atomicity claim (FR-012, US6)

**Decision**: Trust the DataStore atomicity contract as documented; verify with a dedicated concurrent-write unit test (`SettingsDataStoreTest#concurrentWrites_bothPersist`).

**Rationale**:

- DataStore guarantees that all writes are serialized through a single `actor`-style internal coroutine. Two `dataStore.edit { }` calls from different coroutines queue and execute one-at-a-time. The official docs say:
  > "DataStore ensures that updates to a given DataStore happen sequentially. There won't be races between concurrent updates."
- The unit test uses `runTest { }` + 100 iterations to cover SC-004's "100% of the time across 100 test repetitions" success criterion. Each iteration:
  1. Fresh `DataStore` instance (per `@Before`)
  2. `launch { setThemeMode(Dark) }` and `launch { setSearchEngine(Google) }` simultaneously
  3. `joinAll()` then read snapshot
  4. Assert both values present (last-writer-wins acceptable for same-key tests; both-present mandatory for different-key tests)

**Alternatives considered**:

- **Synthetic delay between writes to "guarantee" no race**: rejected — defeats the purpose of the test, which IS to exercise the race window.
- **Real device + thread-sanitizer style instrumentation**: rejected — overkill for a key-value store; the underlying serialization is fully testable in-process.

---

## R10 — `MainActivity` integration (FR-020)

**Decision**: Replace the in-memory `MutableState<ThemeMode>` in `MainActivity.onCreate` with a Hilt-injected `ObserveUserSettingsUseCase`, collected via `collectAsStateWithLifecycle(initialValue = UserSettings.DEFAULT)`. The `darkTheme` boolean is derived from the snapshot's `themeMode` field.

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var observeUserSettings: ObserveUserSettingsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by observeUserSettings()
                .collectAsStateWithLifecycle(initialValue = UserSettings.DEFAULT)
            val darkTheme = when (settings.themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> isSystemInDarkTheme()
            }
            ThirtySixTheme(darkTheme = darkTheme) {
                AppNavGraph()
            }
        }
    }
}
```

**Rationale**:

- `@AndroidEntryPoint` + `@Inject lateinit var` is the supported Hilt pattern for Activities. No constructor injection (Activities are framework-instantiated).
- `ObserveUserSettingsUseCase()` operator-invokes the use case which returns `Flow<UserSettings>`.
- `collectAsStateWithLifecycle` is the lifecycle-aware variant of `collectAsState` from `androidx.lifecycle:lifecycle-runtime-compose` (already in catalog from Spec 002 via lifecycle bundle). Stops collecting when `STOPPED`, restarts on `STARTED` — correct for Activity lifecycle.
- `initialValue = UserSettings.DEFAULT` ensures the first composition has a valid snapshot before disk arrives → no fallback UI needed (SC-002).
- Imports: drop `androidx.compose.runtime.{getValue, mutableStateOf, remember, setValue}` and the old `presentation.theme.ThemeMode`. Add `androidx.lifecycle.compose.collectAsStateWithLifecycle` + `domain.model.{ThemeMode, UserSettings}` + `domain.usecase.ObserveUserSettingsUseCase` + `javax.inject.Inject`.

**Alternatives considered**:

- **Inject `SettingsRepository` directly**: rejected — violates Constitution §IV's UseCase principle (the repository is a data-layer interface even though declared in domain; use cases are the consumer-facing API). Use case adds zero overhead and keeps the rule visible.
- **Convert MainActivity to MVVM with a `MainViewModel`**: rejected as scope creep. The Activity composes one `Flow → State` consumer; a ViewModel adds a layer without adding functionality. Spec 016 may introduce `MainViewModel` if more cross-cutting state lands.

---

## Summary of decisions

| # | Topic | Decision |
|---|---|---|
| R1 | DataStore version | 1.2.1 stable, single artifact, zero `.so` |
| R2 | Hilt provider | `PreferenceDataStoreFactory.create(...)` as `@Singleton` in top-level `di/SettingsModule.kt` |
| R3 | Backup posture | Settings INCLUDED, DB still EXCLUDED, asymmetry documented inline |
| R4 | Setter contract | `Result<Unit>` per Q1 — `IOException → AppError.Database` via `AppError.from()` |
| R5 | Language model | Sealed interface `LanguageOverride { FollowSystem; Explicit(bcp47) }` per Q2; storage = nullable `String?` |
| R6 | First-frame init | `UserSettings.DEFAULT` companion + `collectAsStateWithLifecycle(initialValue = ...)` |
| R7 | Schema evolution | Doc-comment rule in `StorageKeys.kt` + negative-path mapper test |
| R8 | Test strategy | Pure JVM `PreferenceDataStoreFactory.create` + `@TempFolder` — no Robolectric |
| R9 | Atomicity verification | Trust DataStore contract + 100-iteration concurrent-write test for SC-004 |
| R10 | MainActivity wiring | `@AndroidEntryPoint` + `@Inject` use case + `collectAsStateWithLifecycle(DEFAULT)` |
