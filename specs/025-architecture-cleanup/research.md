# Research: Architecture Cleanup

All numbers measured on `main` at `8eae890` (2026-10-14) with scripts run over `app/src/main/kotlin/com/raumanian/thirtysix/browser/` (prefix omitted below). Template reference: `/Users/xbism3/Documents/Template/android` at `e8f74f5`.

## R1. How features are cut

**Decision**: ten features plus `core/` and root wiring.

| Feature | Owns (today's location) |
|---|---|
| `browser` | `presentation/browser/**` except Fire UI, `presentation/home/**`, `presentation/incognito/**`; page capabilities (`browser/page`), protection and script glue (`browser/protection`, `browser/privacy`), suggestions, hub, page tools; the page-time protection logic `domain/protection/*` (`HttpsUpgradePolicy`, `NavigationPreparer`, `ProtectionMatchersHolder`, `PublicSuffixMatcher`, `TrackerMatcher`, `TrackingParamStripper`) and `BlockedHostGrouper`, `PrivacyScriptComposer` — used only by the browser screen; use cases `StartDownload`, `ObserveHasActiveDownloads`, `SanitizeDownloadFileName`, `IsSiteProtected`, `SetSiteProtection`, `IsSiteFireproof`, `SetSiteFireproof`, and the three default-browser invite use cases (only Home's invite card calls them); `UrlConfigModule` |
| `tabs` | `presentation/tabs/**`, `TabRepositoryImpl`, `IncognitoTabRepositoryImpl`, `IncognitoSnapshotOrigins`, screenshot cache impl, tab mapper |
| `bookmarks` | `presentation/bookmarks/**`, `BookmarkRepositoryImpl`, mappers, `BookmarkUrlValidator`, `BookmarkExceptions` |
| `history` | `presentation/history/**`, `HistoryRepositoryImpl`, mapper, `HistoryDayBucketExt` |
| `downloads` | `presentation/downloads/**`, `DownloadsRepositoryImpl`, download gateway/probe/opener/cursor mapper impls |
| `settings` | `presentation/settings/**` (main screen and its dialogs), `SettingsRepositoryImpl`, `SettingsMapper`, `SearchEngineRepositoryImpl`, `AppCompatAppLanguageController`, `AndroidWebDataCleaner` |
| `onboarding` | `presentation/onboarding/**` |
| `privacy` | All privacy **data** and the privacy screens: Fire (`domain/fire`, `RunFireUseCase`, `FireViewModel`, `FireConfirmDialog`, `FireOverlay`, `FireMarkerRepository` contract and impl), fireproof sites (screen, repo impl), protection exceptions (screen, repo impl), protection data (`data/local/protection`, `ProtectionDataRepositoryImpl`, `AssetPaths`), page-script bundles/session impls, cookie snapshot impl. The browser owns the page-time engine that reads this data through core contracts |
| `sitepermission` | `presentation/settings/sitepermissions/**`, `SitePermissionRepositoryImpl`, mapper |
| `entrypoint` | `presentation/entry/**`, `AndroidDefaultBrowserController`, `DefaultBrowserInviteRepositoryImpl`, `OpenIncomingRequest`, `ResolveIncomingRequest`, `RecordAppLaunch` (called by `MainActivity`) |

**Rationale**: matches what a person sees as one area, and each folder can answer "where is X" by name. Home and the incognito page are folded into `browser` because they are not navigation destinations: they are what an empty tab shows inside the browser screen (`thirtysix:home` overlay). Keeping them separate would force the browser screen to embed another feature.

**Alternatives**: `home` as its own feature (needs a composable slot from root for every tab render — more indirection for no independence gained); one `privacy` feature split into `fire` + `protection` (both are small and share fireproof state).

## R2. What lives in `core/` (cross-feature contracts)

**Decision**: a type moves to `core/` only when two or more features use it. Usage scan (presentation features referencing each domain type):

- **31 domain types are used by ≥ 2 features** and go to `core/domain`: `Tab`, `TabRepository`, `MaxTabsReachedException`, `MaxIncognitoTabsReachedException`, `Bookmark`, `HistoryEntry`, `SearchEngine`, `ThemeMode`, `TextSize`, `AppLanguage`, `SiteCapability`, `SiteOrigin`, `FireState`, and the use cases `CreateTab`, `CloseTab`, `CloseIncognitoTab`, `CreateIncognitoTab`, `SwitchActiveTab`, `ObserveTabs`, `ObserveAllTabs`, `ObserveActiveTab`, `ObserveActiveTabIsIncognito`, `UpdateActiveTabUrlAndTitle`, `AddBookmark`, `ObserveUserSettings`, `GetAppLanguage`, `SetAppLanguage`, `SetSearchEngine`, `SetThemeMode`, `IsDefaultBrowser`, `ObserveFireproofSites`.
- **Repository and platform contracts used by use cases of several features** also go to `core/domain/repository`: `BookmarkRepository`, `HistoryRepository`, `IncognitoTabRepository`, `SettingsRepository`, `DownloadsRepository` (browser starts downloads, hub shows activity), `FireproofSiteRepository`, `SitePermissionRepository`, `ProtectionExceptionRepository`, `IncognitoProtectionExceptions`, `CookieJarSnapshotManager`, `WebDataCleaner`, `AppLanguageController`, `DefaultBrowserController`, `DefaultBrowserInviteRepository`, `ProtectionDataRepository`, `PrivacyScriptBundles`, `PrivacyScriptSession`, `HttpsUpgradeFailures`, `DownloadManagerGateway`, `FaviconCache`, `ScreenshotCache`, `ClipboardWriter`, `OneTimeGrantStore`, `IncognitoWebProfile`.
- Everything else (≈ 110 types) stays in its single owning feature.

Use cases go to the feature whose **screen** uses them, not to the feature that owns the data: `ToggleBookmarkUseCase` and `RecordHistoryEntryUseCase` belong to `browser/domain/usecase`, calling contracts in `core/domain/repository`. Implementations stay in the data-owning feature (`bookmarks/data/repository/BookmarkRepositoryImpl` binds `core.domain.repository.BookmarkRepository` in `bookmarks/di`).

**Rationale**: the template rule ("shared code moves to core") applied literally, with the smallest possible core. A browser shares its browsing data with every screen, so the shared contracts are real, not a layering accident. Now in Android does the same (`core:data` / `core:model`).

**Alternatives**: browser as a privileged "host" feature allowed to import others (rejected by the owner, Q1); contracts left in their data-owning feature with an allowlist (an allowlist that never empties).

## R3. The Room database and the settings store live in `core/data`

**Decision**: `AppDatabase`, all 8 DAOs, all 8 entities and the 4 migrations go to `core/data/datasource/local/` (`entity/`, `migration/`). `SettingsDataStore` goes to `core/data/datasource/local/datastore/`. Feature repository impls import them from core.

**Rationale**:
- `@Database(entities = [...])` must name every entity, and the template forbids `core` importing a feature, so either all entities sit in core or the database sits outside core. One schema (v5) with migrations that span tables is shared infrastructure, as in Now in Android's `core:database`.
- The template's placement rules (`*Dao` in `..data.datasource.local..`, `*Entity` in `..data.datasource.local.entity..`) match `core.data.datasource.local` as well.
- The settings store file `thirtysix_settings` is written by settings, entrypoint (invite) and privacy (Fire marker).
- **Template gap**: its README shows `AppDatabase` in `core/data/local` and DAOs in features, which its own `ArchitectureTest` would reject once a feature has an entity. Recorded in ADR 0001 of this app and reported back to the template.

**Alternatives**: a root `database/` package beside `navigation/` (a fourth top-level folder, not in the template); one database per feature (schema change, forbidden by FR-001).

## R4. Contracts with Android types in their signature

Found by scanning: `domain/` imports `data/` in 9 use cases (`FaviconCache`, `ScreenshotCache`, `OneTimeGrantStore`, `DownloadManagerGateway`, `ExternalFileOpener`); presentation imports `data/` 14 times (`FaviconCache` 5, `ScreenshotCache` 3, `ClipboardWriter` 3, `OneTimeGrantStore` 1, `IncognitoProfiles` 2).

**Decision**: each contract moves to `core/domain/repository/` without Android types; the Android implementation stays in `data/`.

| Contract | Android type in signature | Change |
|---|---|---|
| `FaviconCache`, `ScreenshotCache` | `save(…, Bitmap)` | `save(…, writeImage: (OutputStream) -> Unit)`: the cache still opens the file and runs the writer on its IO dispatcher, so encoding happens in the same place, at the same time, into the same file. `Bitmap.pngWriter()` / `webpWriter()` in `core/presentation/util` carry the format and quality (PNG 100, WebP 75). Chosen over the `ByteArray` first planned, which would have moved encoding onto the caller's thread and held the whole image in memory. `fileFor` keeps `java.io.File` (allowed in domain). |
| `DownloadManagerGateway`, `ExternalFileOpener`, `ClipboardWriter` | none (only the impl uses Android) | split the interface out of the impl's file |
| `OneTimeGrantStore` | none (pure class) | moves as is to `core/domain` (no interface needed — it has no platform dependency) |
| `IncognitoWebProfile` / `IncognitoProfiles` | `prepare(WebView, …)` | split: session lifecycle (`ProfileStore`, begin/end, `activeProfileName(): String?`) stays a data impl of the domain contract; attaching a profile to a `WebView` (`WebViewCompat.setProfile`) moves to `browser/presentation` and reads the profile name from the injected contract. Call order (profile set before any other WebView call) is unchanged. |

**Rationale**: the only way to satisfy "domain is pure" and "presentation never sees data" together. Each change is mechanical and covered by existing tests (`FakeFaviconCache`, `FakeIncognitoWebProfile`, `IncognitoIsolationTest`).

## R5. Cross-feature UI is composed at the root, never inside a feature

Cross-feature presentation imports found: browser→tabs (`TabsErrorEvent`), browser→entry (`IncomingContentHooks`), browser→settings (`SettingsViewModel`, `SettingsDialogHost`, `SettingsSnackbarEffect` for the hub's quick settings), browser→bookmarks (`SheetActionRow`), tabs→home (`seedColorFor`), tabs→Fire UI, settings→entry and home→entry (`rememberDefaultBrowserRequest`), settings/sitepermissions→browser/page (capability labels), onboarding→settings (choice labels).

**Decision**:

| Kind | Resolution |
|---|---|
| Generic UI (`SheetActionRow`, `seedColorFor`) | `core/presentation/designsystem/component` |
| Labels and icons for core models (`themeModeLabel`, `searchEngineLabel`, `appLanguageLabel`, capability `labelRes`/`iconRes`) | `core/presentation/util` next to the core model they describe |
| Platform launchers used by several screens (`rememberDefaultBrowserRequest`) | `core/presentation/util` |
| Shared one-off messages (tab-cap reached) | `core/presentation/util` |
| A feature's screen hosting another feature's UI (hub quick settings, Fire dialog/overlay from hub and Tabs) | the consumer declares a slot or callback (`quickSettings: @Composable () -> Unit`, `onFireRequested: () -> Unit`); root `navigation/` fills it with the other feature's composable. Fire's dialog and overlay are hosted once at the root. |
| Interfaces a consumer needs from another feature (`IncomingContentHooks`) | the consumer (`browser`) owns the interface; root adapts `EntryPointViewModel` to it |

**Rationale**: dependency inversion at the composition root, as in Now in Android's app module. Behaviour does not change: the same composables render in the same places, only who constructs them moves.

## R6. Navigation

**Decision**: each feature gets `presentation/navigation/<F>Navigation.kt` with its route constant, `NavGraphBuilder.<f>Screen(...)` and `NavController.navigateTo<F>()`. Root `navigation/AppNavHost.kt` (today `AppNavGraph`) composes them; `AppDestination` is removed. **Route strings are unchanged** (`AppDestination` uses plain strings, so no saved back-stack depends on class names).

## R7. `core/` internal layout

| Today | Moves to |
|---|---|
| `core/result/Result`, `core/time/MonotonicClock`, `core/dispatcher/DispatcherProvider`, `core/di/ApplicationScope` (qualifier, pure `javax.inject`), `core/extensions/UrlExtensions`, `domain/privacy/UrlHost` | `core/domain/util/` |
| `core/dispatcher/DefaultDispatcherProvider` (names `Dispatchers.IO`) | `core/di/` |
| `data/platform/SystemMonotonicClock` | `core/data/util/` |
| `core/base/BaseViewModel`, `core/error/AppError` (maps `SQLiteException`, an Android type, and is used only by `BaseViewModel`), `presentation/util/*` | `core/presentation/util/` |
| `presentation/theme/*` | `core/presentation/designsystem/theme/` |
| `presentation/components/*` | `core/presentation/designsystem/component/` |
| `core/constants/*` (11 objects) | split by owner: a value used by one feature moves into it (`AssetPaths` → `privacy`, its only users being the protection and script data sources); values used by several stay in `core/domain/util` (pure) or the design system (UI). `StorageKeys` splits by the repository that owns each key; key strings unchanged. |
| `di/*` (17 modules) | each to its feature's `di/`; `DatabaseModule`, `DispatcherModule`, `ClockModule`, `ClipboardModule`, `FaviconCacheModule` to `core/di/` |

`presentation/components/FileImage.kt` uses `Dispatchers.IO` directly. It is a composable with 5 call sites, so it cannot take a Hilt injection: it reads a new `LocalIoDispatcher` (`staticCompositionLocalOf`, default throws), provided once in `MainActivity.setContent` from the injected `DispatcherProvider`. `LocalIoDispatcher`, `LocalThirtySixColors` and `LocalThirtySixShapes` are listed in `.editorconfig`'s `compose_allowed_composition_locals`.

## R8. Checks and their versions

Looked up on Maven Central 2026-10-14:

| Tool | Version | Use |
|---|---|---|
| `com.lemonappdev:konsist` | **0.18.1** (latest, released 2026-10-10) | `testImplementation`; four tests from the template, `ROOT = "com.raumanian.thirtysix.browser"` |
| `io.nlopez.compose.rules:ktlint` | **0.6.8** (latest, released 2026-10-09) | `ktlintRuleset` |
| detekt | 1.23.8 (already) | `ForbiddenComment`, `InjectDispatcher`, `OutdatedDocumentation` from the template config, merged into today's 814-line `detekt.yml` |
| ktlint-gradle | 14.2.0 (already) | — |

Gradle already runs on JDK 21 (`gradle/gradle-daemon-jvm.properties`), which detekt 1.23.8 needs. Konsist finds the project root by `gradlew`, present. All are test/lint-only: no runtime dependency, no `.so`.

**Migration allowlists**: Konsist uses `KNOWN_VIOLATIONS` in `KonsistScope.kt`; detekt uses its existing (empty) baseline file regenerated once; compose-rules uses a ktlint baseline. All three are generated in the first step and must be empty at the end.

## R9. Order of work on the branch

**Decision**: one commit per step, each green on CI (owner Q, 2026-10-14):

1. Baseline measurements (APK size, cold start, test counts) recorded.
2. Configs to `config/`; Konsist, compose-rules, comment rules wired; allowlists generated.
3. `core/` moves (R7), contracts split (R4), shared types moved (R2).
4. One commit per feature, leaf features first: settings → onboarding → sitepermission → downloads → history → bookmarks → tabs → entrypoint → privacy → browser. Each moves main and test files together with `git mv` (no content change except package/imports), then removes that feature's allowlist entries.
5. Root navigation (R6) and slots (R5).
6. Comment cleanup, one commit per feature (separate from moves so rename detection keeps history).
7. Governance set (R10), archive (R11).
8. Allowlists empty; device gates.

**Rationale**: moves without edits keep `git log --follow` and blame; leaf-first means each step only touches features nothing else depends on yet.

## R10. Governance set

- **Constitution 2.0.0** = template text with `[PLACEHOLDERS]` filled: privacy promise from §I/§VII, permission table from Technical Standards (`INTERNET`, `ACCESS_NETWORK_STATE`, `WRITE_EXTERNAL_STORAGE` ≤ 28, `CAMERA`, `RECORD_AUDIO`, `ACCESS_COARSE/FINE_LOCATION`, `MODIFY_AUDIO_SETTINGS`), budgets (cold start, APK), 8 locales, SDK 24/36, critical flows. Old rules not in the template but still wanted become Product Constraints: page scripts only APK-bundled and one-way; no `addJavascriptInterface` without origin gating; protection data ships in the APK; no analytics/crash reporting; Scoped Storage. Flavors policy: `debug` + `release` only.
- **Retired**: phase order (owner Q), the long Compose §V prose (replaced by template IV + compose-rules), the No-Hardcode 18-row table (replaced by template V).
- **ADRs**: 0001–0004 from the template adapted (0001 records `*RepositoryImpl` and R3), plus: 0005 phase order retired; 0006 home is the reserved address `thirtysix:home`, never loaded; 0007 incognito sessions use their own WebView profile (with the older-engine snapshot fallback); 0008 Fire step order and the interrupted-Fire marker; 0009 language change applied in place (`configChanges`); 0010 CI emulator always cold-boots (no AVD cache); 0011 `MainActivity` is `singleTask` and how incoming links are relayed; 0012 HTTPS-upgrade fallback traps. The plan's tasks list every "facts to keep" bullet from `CLAUDE.md` with its destination (ADR, check, test, or dropped).

## R11. Archive

`CLAUDE.md` Recent Changes → `docs/archive/changelog-001-024.md`; `.claude/claude-app/{dev-workflow,meeting-note,project-context,sdd-roadmap}.md` → `docs/archive/`; each starts with "Archive — not a rule; the rules are in `.specify/memory/constitution.md`." Nothing loaded by agents references `docs/archive/`. The memory notes in the user's agent memory that point to `CLAUDE.md` sections are updated in the same step.

## R12. Risks found

| Risk | Mitigation |
|---|---|
| Manifest and debug manifest name `MainActivity`, `ThirtySixApplication`, `HiltTestActivity`, `dev.*Seeder` | those stay in the root and `dev` packages (debug source set is not production scope) |
| `proguard-rules.pro` | contains no project package names (checked) |
| Room exports schemas to `app/schemas/<fully-qualified AppDatabase>/`; moving the class changes the folder | `git mv` the folder in the same commit; JSON files unchanged (no package names inside); `AppDatabaseMigrationTest` must pass on the renamed folder |
| Hilt test modules use `@UninstallModules(SettingsDataStoreProviderModule::class)` etc. | module class names unchanged; imports updated with the move |
| Comment cleanup deletes a reason that matters | each removed reason over one line is checked against the ADR list; reviewer samples 20 files |
| compose-rules flags signatures (modifier order, naming) | count measured in step 1; fixed after the moves in separate commits per folder, signature-only, never mixed with a move or a comment edit; the ktlint baseline shrinks to empty |
| Comment counting must match `CommentPolicyTest` | `scripts/comment-stats.py` implements the same rules (`/**`, `*/`, bare `*` and blank lines skipped; block = consecutive comment lines) and is used for the baseline and SC-005 |
