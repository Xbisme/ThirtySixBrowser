# Tasks: Architecture Cleanup

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Path shorthands** used below (always write the full path in code and commits):

| Short | Full path |
|---|---|
| `M/` | `app/src/main/kotlin/com/raumanian/thirtysix/browser/` |
| `UT/` | `app/src/test/kotlin/com/raumanian/thirtysix/browser/` |
| `IT/` | `app/src/androidTest/kotlin/com/raumanian/thirtysix/browser/` |
| `F/<f>/` | `M/feature/<f>/` |
| `C/` | `M/core/` |

**Rules for every task**

- Gradle is always run as `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew …`; check for `BUILD SUCCESSFUL`.
- Moves use `git mv`. A move commit changes only `package` lines, imports and fully qualified references; no other edit (keeps `git log --follow`). Comment edits come later (Phase 5).
- A type used by two or more features goes to `C/` (research R2); everything else stays in its feature. Any type named in a `C/` signature also moves to `C/`.
- Tests move with their subject, mirroring the main path (`UT/x/y/FooTest.kt` follows `M/x/y/Foo.kt`).
- Every task group that ends with **Commit** must pass quickstart G1 locally and on CI before the next group starts. Allowlist entries for moved files are removed in that same commit; entries are never added after T014.
- Tests are not a separate deliverable here: the existing suites are the safety net (FR-026), plus the four architecture tests the spec requires (FR-012).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US5 from spec.md

**Phase order** follows the work order on the branch (research R9), not story priority: checks first (US2) so every later move is verified, then the layout (US3), comments (US5), governance (US4), and the final no-change proof (US1). US1's baseline is captured in Setup.

---

## Phase 1: Setup

**Purpose**: a clean branch and the numbers every later step is compared with.

- [X] T001 Confirm branch `025-architecture-cleanup` contains `main` @ `8eae890` and the working tree is clean except `specs/025-architecture-cleanup/`; commit the spec documents (`docs(025): spec, plan, tasks`).
- [X] T002 Add rows for Spec 025 `architecture-cleanup` (owner-requested 2026-10-14, outside Phases 1–6, retires the phase-order rule) and Spec 026 `viewmodel-action-event` (planned) to `.claude/claude-app/sdd-roadmap.md`, citing the owner's approval in this spec's Clarifications; this satisfies constitution 1.6.0 §X until T058 replaces it.
- [X] T003 Create `scripts/comment-stats.py`: walk the given folder's `.kt` files, classify lines exactly as `CommentPolicyTest`'s `CommentStats` does (skip blank, `import`, `package` and comment lines that are only `/`, `*`; count comment lines, code lines, longest consecutive comment block), print total comment share, number of files of ≥ 30 lines over 15 %, number of files with a block over 5 lines. Check it reproduces 21.9 % / 117 / 160 on `app/src/main/kotlin` at `8eae890`.
- [X] T004 Record baseline G0 from [quickstart.md](quickstart.md) on `main` @ `8eae890` in the "Baseline" table at the end of this file: unit test count (expect 932), instrumented count on `TA016_API36_16K` (expect 189), release APK bytes (`app/build/outputs/apk/release/app-release.apk`), cold-start `TotalTime` for 10 launches, comment share from `scripts/comment-stats.py` (expect 21.9 %), Compose report counts (`-PcomposeReports`: 206 restartable, 0 non-skippable). Copy the merged release manifest (`find app/build/intermediates -path '*release*' -name AndroidManifest.xml` under `merged_manifests`) to `specs/025-architecture-cleanup/baseline/AndroidManifest-release.xml`.
- [X] T005 Build and install the baseline **debug** APK on `TA016_API36_16K`, seed data for G6 (history, downloads, tabs seeders from `app/src/debug/AndroidManifest.xml`; by hand: one bookmark folder with two bookmarks, one fireproof site, one "Always allow" camera decision, one protection exception, theme Dark, language Deutsch, search engine DuckDuckGo, text size 125 %, retention 30 days, one open incognito tab). Do not uninstall this app until T063.

---

## Phase 2: Foundational — config locations

**Purpose**: move check configuration to the template's paths so the next phase only adds rules.

- [X] T006 `git mv detekt.yml config/detekt/detekt.yml` and update `app/build.gradle.kts` line `config.setFrom(files("$rootDir/detekt.yml"))` → `config.setFrom(files("$rootDir/config/detekt/detekt.yml"))`.
- [X] T007 `git mv compose_stability.conf config/compose/stability.conf`; update `stabilityConfigurationFiles.add(…)` in `app/build.gradle.kts`.
- [X] T008 `git mv app/detekt-baseline.xml config/detekt/baseline.xml`; set `baseline = file("$rootDir/config/detekt/baseline.xml")` in the `detekt {}` block of `app/build.gradle.kts`.
- [X] T009 Run G1; grep `.github/workflows/ci.yml` for any of the three old paths and update them. **Commit** `build(025): move check configs under config/`.

---

## Phase 3: User Story 2 — architecture rules break the build (P1)

**Goal**: every rule in [contracts/architecture-checks.md](contracts/architecture-checks.md) runs in the build, strict for new code, with current violations listed by name.

**Independent test**: quickstart G2 — one deliberate violation per rule fails with a message naming the file.

- [X] T010 [US2] Add to `gradle/libs.versions.toml`: `konsist = "0.18.1"`, `compose-rules = "0.6.8"`; libraries `konsist = { module = "com.lemonappdev:konsist", version.ref = "konsist" }`, `compose-rules-ktlint = { module = "io.nlopez.compose.rules:ktlint", version.ref = "compose-rules" }`. Re-check both on Maven Central at the moment of adding; record the date in the commit message.
- [X] T011 [US2] In `app/build.gradle.kts` add `testImplementation(libs.konsist)` and `ktlintRuleset(libs.compose.rules.ktlint)`.
- [X] T012 [P] [US2] Create `UT/architecture/KonsistScope.kt`, `ArchitectureTest.kt`, `PlacementTest.kt`, `NoHardcodeTest.kt`, `CommentPolicyTest.kt` copied from `/Users/xbism3/Documents/Template/android/app/src/test/java/com/example/app/architecture/` with `package com.raumanian.thirtysix.browser.architecture` and `ROOT = "com.raumanian.thirtysix.browser"`; no rule changed.
- [X] T013 [P] [US2] Merge the template's `config/detekt/detekt.yml` sections into `config/detekt/detekt.yml`: `comments.OutdatedDocumentation`, `style.ForbiddenComment` (all ten patterns verbatim), `coroutines.InjectDispatcher` with `excludes: ['**/di/**', '**/test/**', '**/androidTest/**']`, `ignoreAnnotated: ['Preview']` on `MagicNumber`/`UnusedPrivateMember`. Keep every existing project setting; where a key exists in both, keep the stricter value. In `app/build.gradle.kts` set detekt `source.setFrom("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin", "src/debug/kotlin")` so comment-content rules cover tests too. Add `compose_allowed_composition_locals = LocalThirtySixColors,LocalThirtySixShapes,LocalIoDispatcher` to `.editorconfig`.
- [X] T014 [US2] Generate the allowlists from the current code: run `testDebugUnitTest --tests '*architecture*'` and put every failing file path into `KNOWN_VIOLATIONS` (one path per line, no comment beside it — the ticket rule would flag one); run `./gradlew detektBaseline` to rewrite `config/detekt/baseline.xml`; run `./gradlew ktlintGenerateBaseline` (ktlint-gradle task) for compose-rules findings into `config/ktlint/baseline.xml` and point `ktlint { baseline.set(file(...)) }` at it. Record the three counts in the "Allowlist counts" table at the end of this file. Classify every `KNOWN_VIOLATIONS` entry by failing rule: layout/layering/placement entries are removed by Phase 4 and comment entries by Phase 5; any other entry (hard-coded text, color outside the theme, mutable public view-model state, non-immutable `UiState` — none were found by grep on 2026-10-14) gets its own fix task appended to Phase 4c before T041's commit.
- [X] T015 [US2] Run quickstart G2: for each row of contracts/architecture-checks.md make a scratch violation in a file **not** in an allowlist, confirm the failure names the file, revert. Record pass/fail per row in the "Rule proof" table at the end of this file.
- [X] T016 [US2] Run G1. **Commit** `test(025): architecture, placement, hardcode and comment checks`.

**Checkpoint**: from here on, any new violation fails CI.

---

## Phase 4: User Story 3 — everything about one feature in one place (P1)

**Goal**: the layout of [data-model.md](data-model.md); `KNOWN_VIOLATIONS` entries for layout and layering gone.

**Independent test**: quickstart G3, plus the Konsist tests passing with fewer allowlist entries after each commit.

### 4a. `core/` (blocks every feature)

- [X] T017 [US3] Move utilities: `M/core/result/Result.kt`, `M/core/error/AppError.kt`, `M/core/time/MonotonicClock.kt`, `M/core/dispatcher/DispatcherProvider.kt`, `M/core/di/ApplicationScope.kt`, `M/core/extensions/UrlExtensions.kt`, `M/domain/privacy/UrlHost.kt` → `C/domain/util/`; `M/core/dispatcher/DefaultDispatcherProvider.kt` → `C/di/`; `M/core/di/DispatcherModule.kt` stays `C/di/`; `M/data/platform/SystemMonotonicClock.kt` → `C/data/util/`; `M/di/ClockModule.kt` → `C/di/`.
- [X] T018 [US3] Move UI foundation: `M/presentation/theme/*` → `C/presentation/designsystem/theme/`; `M/presentation/components/*` → `C/presentation/designsystem/component/`; `M/core/base/BaseViewModel.kt`, `M/presentation/util/*` → `C/presentation/util/`. In `FileImage.kt` replace `Dispatchers.IO` with `LocalIoDispatcher.current`: add `val LocalIoDispatcher = staticCompositionLocalOf<CoroutineDispatcher> { error("LocalIoDispatcher not provided") }` in `C/presentation/util/LocalIoDispatcher.kt`, and in `M/MainActivity.kt` wrap `setContent`'s content in `CompositionLocalProvider(LocalIoDispatcher provides dispatchers.io)` using the injected `DispatcherProvider` (check its property name). Compose UI tests that render `FileImage` outside `MainActivity` provide it too (the one non-move edit here, needed by `InjectDispatcher`).
- [X] T019 [US3] Extract shared UI helpers to core (research R5): `SheetActionRow` (from `M/presentation/bookmarks/components/SheetActionRow.kt`) and `seedColorFor` (from `M/presentation/home/ShortcutCard.kt`) → `C/presentation/designsystem/component/`; `themeModeLabel`, `searchEngineLabel`, `appLanguageLabel` (from `M/presentation/settings/components/{ThemeModeChooserDialog,SearchEngineChooserDialog,AppLanguageChooserDialog}.kt`), `M/presentation/browser/page/SiteCapabilityLabels.kt`, `M/presentation/entry/DefaultBrowserRequest.kt` → `C/presentation/util/`; `M/presentation/tabs/TabsErrorEvent.kt` → `C/presentation/util/`. Functions keep their names and bodies.
- [X] T020 [US3] Move persistence: `M/data/local/database/AppDatabase.kt` → `C/data/datasource/local/AppDatabase.kt`; `M/data/local/database/migrations/*` → `C/data/datasource/local/migration/`; `M/data/local/dao/*` → `C/data/datasource/local/`; `M/data/local/entity/*` → `C/data/datasource/local/entity/`; `M/data/local/datastore/SettingsDataStore.kt` → `C/data/datasource/local/datastore/`; `M/di/DatabaseModule.kt` → `C/di/`; `SettingsDataStoreProviderModule` (from `M/di/SettingsModule.kt`) → `C/di/SettingsDataStoreProviderModule.kt`, class name unchanged. In the same commit `git mv app/schemas/com.raumanian.thirtysix.browser.data.local.database.AppDatabase app/schemas/com.raumanian.thirtysix.browser.core.data.datasource.local.AppDatabase`; `1.json`–`5.json` must stay byte-identical (`git diff --stat -M` shows 100 % renames). Move `UT/data/local/database/*` and `UT/data/local/dao/*` tests along.
- [X] T021 [US3] Move shared models and contracts (research R2 list) from `M/domain/model/` and `M/domain/repository/` to `C/domain/model/` and `C/domain/repository/`: models `Tab`, `Bookmark`, `HistoryEntry`, `SearchEngine`, `ThemeMode`, `TextSize`, `AppLanguage`, `SitePermission` (`SiteCapability`, `SiteOrigin`, decisions), `FireState`, `UserSettings`, `HistoryRetention`, `DownloadRecord`, `DownloadRequest`, `DownloadStatus`, `FireproofSite`, `SiteProtectionException`, `SiteDataClearOutcome`, and the `Max*ReachedException` types; contracts `TabRepository`, `IncognitoTabRepository`, `BookmarkRepository`, `HistoryRepository`, `SettingsRepository`, `SearchEngineRepository`, `DownloadsRepository`, `FireproofSiteRepository`, `SitePermissionRepository`, `ProtectionExceptionRepository`, `IncognitoProtectionExceptions`, `CookieJarSnapshotManager`, `WebDataCleaner`, `AppLanguageController`, `DefaultBrowserController`, `DefaultBrowserInviteRepository`, `ProtectionDataRepository`, `PrivacyScriptBundles`, `PrivacyScriptSession`, `HttpsUpgradeFailures`. After the move, `grep` every remaining `M/domain/model` type for use by two features (rerun the usage script from research) and move any you missed.
- [X] T022 [US3] Split contracts out of `data/` (research R4), each interface to `C/domain/repository/` with the impl left in its data file: `DownloadManagerGateway`, `ExternalFileOpener` (from `M/data/local/download/`), `ClipboardWriter` (from `M/data/local/clipboard/ClipboardWriter.kt`); move `M/data/local/permission/OneTimeGrantStore.kt` (pure class) → `C/domain/util/OneTimeGrantStore.kt`.
- [X] T023 [US3] Change `FaviconCache.save` and `ScreenshotCache.save` from `Bitmap` to an image writer `(OutputStream) -> Unit` (research R4; done this way instead of `ByteArray`, see R4): interfaces → `C/domain/repository/`; `DiskFaviconCache`/`DiskScreenshotCache` write the bytes as given; add `C/presentation/util/BitmapEncoding.kt` that encodes with exactly the format and quality the disk caches use today (read them first), run on the injected default dispatcher; update the callers in `BrowserViewModel`/`BrowserWebView`. Update `UT/testdoubles/FakeFaviconCache.kt`, `FakeScreenshotCache.kt`. Verify `cacheDir/favicons/*.png` and `cacheDir/screenshots/*.webp` files written by the new build open and are the same format (`file` command on files pulled from the AVD).
- [X] T024 [US3] Split `M/data/local/webview/IncognitoProfiles.kt` (research R4): `IncognitoWebProfile` interface → `C/domain/repository/` gaining `activeProfileName(): String?`; the profile-store lifecycle (`ProfileStore`, begin/end, deletion, `existingProfileNames`) stays a data impl in `C/data/util/`; the `WebView` attach functions (`prepare`, `prepareLike`) move to `M/presentation/browser/WebViewProfiles.kt` (it becomes `F/browser/…` in T038) and read the name from the injected contract. Call order "profile set before any other WebView call" unchanged. `IT/incognito/IncognitoIsolationTest` must pass on the 16 KB AVD.
- [X] T025 [US3] Move the use cases called by two or more features' screens (research R2) from `M/domain/usecase/` to `C/domain/usecase/`: `CreateTab`, `CloseTab`, `CloseIncognitoTab`, `CreateIncognitoTab`, `SwitchActiveTab`, `ObserveTabs`, `ObserveAllTabs`, `ObserveActiveTab`, `ObserveActiveTabIsIncognito`, `UpdateActiveTabUrlAndTitle`, `AddBookmark`, `ObserveUserSettings`, `GetUserSettings`, `GetAppLanguage`, `SetAppLanguage`, `SetSearchEngine`, `SetThemeMode`, `IsDefaultBrowser`, `ObserveFireproofSites` (`…UseCase.kt`); move their tests from `UT/domain/usecase/`.
- [X] T026 [US3] Move fakes of core contracts `UT/testdoubles/*.kt` → `UT/core/testing/fake/`, (the project has no `MainDispatcherRule`: tests call `Dispatchers.setMain` themselves, which Spec 026 can unify). Move `IT/testing/*` → `IT/core/testing/`.
- [X] T027 [US3] Split `M/core/constants/*` by owner (research R7): for each constant, if one feature uses it, leave it in a temporary `C/domain/util/` file and note it in the "Constants to hand over" table at the end of this file for the feature commit; if several features use it, move it to `C/domain/util/` (pure values), `C/presentation/designsystem/theme/` (UI values) or `C/data/datasource/local/` (`StorageKeys`). `AssetPaths` is handed to `privacy` (only `ProtectionDataSource` and `PrivacyScriptBundlesImpl` use it). Key strings and values unchanged.
- [X] T028 [US3] Run G1 and the instrumented suite on the 16 KB AVD (`adb shell am force-stop com.raumanian.thirtysix.browser.debug` first). Remove allowlist entries for files that now pass. **Commit** `refactor(025): core layer`.

### 4b. Features, leaf first (one commit each)

Each feature task moves the listed main files to `F/<f>/` with the template sub-folders, its DI modules to `F/<f>/di/`, its tests to `UT/feature/<f>/…` and `IT/feature/<f>/…`, feature-only fakes to `UT/feature/<f>/fake/`, sample data to `UT/feature/<f>/fixture/`, and the constants handed over in T027. Each screen gets its own package under `F/<f>/presentation/<screen>/` with its components in `component/`. Then run G1 (and the instrumented suite when the feature has instrumented tests), remove its allowlist entries, and **commit** `refactor(025): feature/<f>`. While moving, write each use case whose body only forwards one repository call into the "Spec 026 — forwarding use cases" table at the end of this file.

- [X] T029 [US3] **settings** — `M/presentation/settings/{SettingsEvent,SettingsScreen,SettingsUiState,SettingsViewModel}.kt` + `components/*` → `F/settings/presentation/settings/`; use cases `ChangeHistoryRetention`, `ClearBrowsingData`, `SetDynamicColorEnabled`, `SetTextSize` → `F/settings/domain/usecase/`; models `ClearBrowsingDataCategory`, `ClearBrowsingDataResult`, `HistoryRetentionChangeResult` → `F/settings/domain/model/`; `M/data/repository/{SettingsRepositoryImpl,SearchEngineRepositoryImpl}.kt` → `F/settings/data/repository/`; `M/data/mapper/SettingsMapper.kt` → `F/settings/data/mapper/`; `M/data/local/locale/AppCompatAppLanguageController.kt`, `M/data/local/webdata/AndroidWebDataCleaner.kt` → `F/settings/data/datasource/local/`; `SettingsRepositoryBindingModule` (rest of `M/di/SettingsModule.kt`), `M/di/SettingsPlatformModule.kt`, `M/di/SearchEngineModule.kt` → `F/settings/di/`.
- [X] T030 [US3] **onboarding** — `M/presentation/onboarding/**` → `F/onboarding/presentation/onboarding/`; `SetOnboardingCompletedUseCase` → `F/onboarding/domain/usecase/`; `OnboardingSlides` constants.
- [X] T031 [US3] **sitepermission** — `M/presentation/settings/sitepermissions/**` → `F/sitepermission/presentation/sitepermissions/`; use cases `ObserveSitePermissions`, `RemoveSitePermissions`, `SetSitePermission`; `M/data/repository/SitePermissionRepositoryImpl.kt`, `M/data/mapper/SitePermissionMapper.kt`; `M/di/SitePermissionModule.kt`.
- [X] T032 [US3] **downloads** — `M/presentation/downloads/**` → `F/downloads/presentation/downloads/`; use cases `CancelDownload`, `DeleteDownloadedFile`, `ObserveDownloads`, `OpenDownloadedFile`, `RemoveDownloadRecord`, `ResolveDownloadStatus`; models `DownloadActionAvailability`, `DownloadFailureCause`, `DownloadListItem`; `M/data/repository/DownloadsRepositoryImpl.kt`, `M/data/mapper/DownloadRecordMapper.kt`, `M/data/local/download/{AndroidDownloadManagerGateway impl,DownloadStatusCursorMapper,DownloadedFileProbe,ExternalFileOpener impl}` → `F/downloads/data/datasource/local/`; `M/di/DownloadsModule.kt`. `StartDownload`, `ObserveHasActiveDownloads` and `SanitizeDownloadFileName` are browser-only and move in T038, not here; remove `SanitizeDownloadFileName` from this task's list.
- [X] T033 [US3] **history** — `M/presentation/history/**`; use cases `ClearAllHistory`, `DeleteHistoryEntry`, `ObserveHistoryEntries`, `PruneOldHistory` (called from `ThirtySixApplication` — root may reference it); models `HistoryDayBucket` + `M/core/extensions/HistoryDayBucketExt.kt`; `M/data/repository/HistoryRepositoryImpl.kt`, `M/data/mapper/HistoryEntryMapper.kt`; `M/di/HistoryModule.kt`.
- [ ] T034 [US3] **bookmarks** — `M/presentation/bookmarks/**`; use cases `CountFolderDescendants`, `CreateFolder`, `DeleteBookmark`, `DeleteFolder`, `MoveBookmark`, `MoveFolder`, `ObserveAllFolders`, `ObserveBookmarksByFolder`, `ObserveFolderPath`, `RenameFolder`, `SearchBookmarks`, `UpdateBookmark`; models `BookmarkFolder`, `BookmarkDescendantCount`, `BookmarkSearchResult`; `M/domain/validator/BookmarkUrlValidator.kt` → `F/bookmarks/domain/usecase/` (or `domain/model/` if it is a pure rule object — keep name), `M/domain/error/BookmarkExceptions.kt` → `F/bookmarks/domain/model/`; `M/data/repository/BookmarkRepositoryImpl.kt`, `M/data/mapper/{BookmarkMapper,BookmarkFolderMapper}.kt`; `M/di/BookmarkModule.kt`.
- [ ] T035 [US3] **tabs** — `M/presentation/tabs/**` (except `TabsErrorEvent`, moved in T019); use cases `CloseAllTabs`, `CloseAllIncognitoTabs`; `M/data/repository/{TabRepositoryImpl,IncognitoTabRepositoryImpl,IncognitoSnapshotOrigins}.kt`, `M/data/mapper/TabMapper.kt`, screenshot cache impl (`DiskScreenshotCache` from `M/data/local/cache/ScreenshotCache.kt`) → `F/tabs/data/datasource/local/`; `M/di/TabsModule.kt`, `bindIncognitoTabRepository` part of `M/di/IncognitoModule.kt` → `F/tabs/di/`. The Fire button calls a new `onFireRequested: () -> Unit` parameter instead of showing `FireConfirmDialog` itself (hosting moves to root in T039); behaviour identical. `bindScreenshotCache` moves with it if only tabs binds it, otherwise `C/di/FaviconCacheModule.kt` keeps both.
- [ ] T036 [US3] **entrypoint** — `M/presentation/entry/**` (except `DefaultBrowserRequest.kt`, moved in T019) → `F/entrypoint/presentation/entry/`; use cases `OpenIncomingRequest`, `ResolveIncomingRequest`, `RecordAppLaunch` (called from `MainActivity`; root may import a feature) → `F/entrypoint/domain/usecase/`; model `IncomingRequest`. The three invite use cases (`DismissDefaultBrowserInvite`, `ObserveDefaultBrowserInvite`, `SyncDefaultBrowserInvite`) are called only by Home's `DefaultBrowserInviteViewModel` and move with browser in T038; their contract `DefaultBrowserInviteRepository` is already in core (T021); `M/data/platform/AndroidDefaultBrowserController.kt`, `M/data/repository/DefaultBrowserInviteRepositoryImpl.kt`; `M/di/DefaultBrowserModule.kt`. `IncomingContentHooks` moves to the browser feature in T038 (consumer owns the interface); `EntryPointViewModel` is adapted to it at the root in T039.
- [ ] T037 [US3] **privacy** — Fire: `M/domain/fire/*`, `RunFireUseCase`, `M/presentation/browser/fire/FireViewModel.kt`, `M/presentation/browser/components/{FireConfirmDialog,FireOverlay}.kt` → `F/privacy/presentation/fire/`, `M/data/repository/FireMarkerRepositoryImpl.kt`; fireproof: `M/presentation/settings/fireproof/**` → `F/privacy/presentation/fireproof/`, `RemoveFireproofSites` use case (`IsSiteFireproof`, `SetSiteFireproof` are browser-only, T038), `M/data/repository/FireproofSiteRepositoryImpl.kt`, `M/data/mapper/FireproofSiteMapper.kt`; protection: `M/presentation/settings/protectionexceptions/**` → `F/privacy/presentation/protectionexceptions/`, `ObserveProtectionExceptions`, `RemoveProtectionExceptions` use cases (`M/domain/protection/*` is browser-only and moves in T038), `M/data/local/protection/*` → `F/privacy/data/datasource/local/` with the `AssetPaths` constants handed over in T027, `FireMarkerRepository` contract → `F/privacy/domain/repository/`, `M/data/repository/{ProtectionDataRepositoryImpl,ProtectionExceptionRepositoryImpl}.kt`, `M/data/mapper/SiteProtectionExceptionMapper.kt`; scripts: `M/data/local/privacy/*` (`PrivacyScriptComposer` and `BlockedHostGrouper` are browser-only and move in T038); cookies: `M/data/local/cookies/*`; DI: `M/di/{PrivacyToolsModule,ProtectionModule}.kt`, `bindCookieJarSnapshotManager`/`provideIncognitoWebProfile` from `M/di/IncognitoModule.kt`. `provideApplicationScope` → `C/di/`. Tests: `UT/domain/{fire,privacy,protection}`, `IT/{fire,privacy,protection}` → `UT/feature/privacy/…`, `IT/feature/privacy/…` (core parts to `UT/core/…`).
- [ ] T038 [US3] **browser** — everything left in `M/presentation/browser/**`, `M/presentation/home/**`, `M/presentation/incognito/**` → `F/browser/presentation/{browser,home,incognito,suggestions,page,protection}/` per data-model.md; remaining use cases (`BuildLongPressMenu`, `BuildSearchUrl`, `DecidePopup`, `FilterAddressSuggestions`, `LoadAddressSuggestionsSource`, `ObserveHomeContent`, `OpenInBackgroundTab`, `RecordHistoryEntry`, `UpdateHistoryEntryTitle`, `IsUrlBookmarked`, `ToggleBookmark`, `StartDownload`, `ObserveHasActiveDownloads`, `SanitizeDownloadFileName`, `IsSiteProtected`, `SetSiteProtection`, `IsSiteFireproof`, `SetSiteFireproof`, `ApplySitePermissionChoice`, `ResolveSitePermission`, `SwitchToTab`, `DismissDefaultBrowserInvite`, `ObserveDefaultBrowserInvite`, `SyncDefaultBrowserInvite`) → `F/browser/domain/usecase/`; `M/domain/protection/*` → `F/browser/domain/protection/`; `M/domain/privacy/{PrivacyScriptComposer,BlockedHostGrouper}.kt` → `F/browser/domain/model/`; remaining models (`AddressBarInputClassifier`, `AddressBarSubmitResult`, `AddressSuggestion`, `GreetingPeriod`, `HomeShortcut`, `LongPressMenu`, `PageProtectionStatus`) → `F/browser/domain/model/`; `IncomingContentHooks` (from entrypoint) → `F/browser/presentation/browser/`; `M/di/UrlConfigModule.kt` → `F/browser/di/`. The hub's quick settings takes a `quickSettings: @Composable () -> Unit` slot and the hub's Fire tile calls `onFireRequested` (filled at root in T039) instead of importing `SettingsViewModel`/`SettingsDialogHost`/`SettingsSnackbarEffect` and Fire UI. Tests: `UT/presentation/{browser,home}/**`, `IT/presentation/{browser,home,util}/**` → `…/feature/browser/…`.
- [ ] T039 [US3] **root navigation** (research R5, R6) — each feature gets `F/<f>/presentation/navigation/<F>Navigation.kt` with its route string copied from `M/presentation/navigation/AppDestination.kt` (unchanged), `NavGraphBuilder.<f>Screen(…)` and `NavController.navigateTo<F>()`; `M/presentation/navigation/AppNavGraph.kt` → `M/navigation/AppNavHost.kt` composing them; delete `AppDestination.kt`. Root fills the slots: `quickSettings` with the settings feature's quick-settings composable, `onFireRequested` for browser hub and Tabs with one Fire host (`FireConfirmDialog` + `FireOverlay` + `FireViewModel`) placed in `AppNavHost`; root adapts `EntryPointViewModel` to `IncomingContentHooks`. Instrumented suite + quickstart G10 smoke (Fire from hub and from Tabs, quick settings, link from another app).
### 4c. Compose-rules findings

- [ ] T040 [US2] Fix every compose-rules finding left in `config/ktlint/baseline.xml`, one commit per folder group (the same groups as T044–T052), signature-only changes (parameter order with `modifier` first optional, naming, `@Composable` defaults); never in a move or comment commit. Each commit removes its baseline lines and passes G1 and the instrumented suite. Also fix here any non-layout Konsist entry found in T014. **Commit** per group `style(025): compose rules in <folder>`.

- [ ] T041 [US3] Delete the now-empty `M/data/`, `M/domain/`, `M/presentation/`, `M/di/`, `M/core/{base,constants,dispatcher,error,extensions,result,time}` and the matching empty test folders; run quickstart G3. Fill the "Spec 026 — screens without `onAction`" table at the end of this file (every `*ViewModel` with more than one public `fun on…`). **Commit** `refactor(025): root navigation`.

- [ ] T042 [US2] Rename the domain model `DownloadRequest` (fails "domain models are not data types") to `DownloadOrder` in its file and every use; no behaviour change. **Commit** `refactor(025): DownloadRequest is a domain model`.
- [ ] T043 [US2] The debug seeders `app/src/debug/kotlin/…/dev/{DownloadSeeder,HistorySeeder,TabSeeder}.kt` name `Dispatchers.IO`; inject `DispatcherProvider` (they are `@AndroidEntryPoint`) and use its IO dispatcher. **Commit** `refactor(025): seeders use the injected dispatcher`.

**Checkpoint**: Konsist allowlist holds only comment-policy entries; ktlint baseline is empty; layout matches G3.

---

## Phase 5: User Story 5 — comments say only what code cannot (P2)

**Goal**: detekt `ForbiddenComment` baseline and `CommentPolicyTest` allowlist empty; comment share ≤ 12 % (SC-005).

**Independent test**: quickstart G5.

Per task: for every file in the folder, delete ID citations, planning-doc and constitution citations, change history, banners, commented-out code and comments that restate code; shorten surviving reasons to 1–3 lines; turn `TODO` without ticket into a ticket or remove it. A reason longer than three lines that is still true goes into a new ADR in `docs/adr/` and the code keeps one line pointing to it by file name. Code is not changed. Then regenerate `config/detekt/baseline.xml` (only removals), drop the folder's `KNOWN_VIOLATIONS` entries, run G1, **commit** `docs(025): comments in <folder>`.

- [ ] T044 [P] [US5] `C/` (main) and `UT/core/`, `IT/core/`
- [ ] T045 [P] [US5] `F/settings/`, `F/onboarding/` and their tests
- [ ] T046 [P] [US5] `F/sitepermission/`, `F/downloads/` and their tests
- [ ] T047 [P] [US5] `F/history/`, `F/bookmarks/` and their tests
- [ ] T048 [P] [US5] `F/tabs/`, `F/entrypoint/` and their tests
- [ ] T049 [P] [US5] `F/privacy/` and its tests
- [ ] T050 [P] [US5] `F/browser/presentation/browser/` and `…/home`, `…/incognito`, `…/suggestions` and their tests
- [ ] T051 [P] [US5] `F/browser/presentation/page/`, `…/protection/`, `F/browser/domain/`, `F/browser/data/`, `F/browser/di/` and their tests
- [ ] T052 [P] [US5] `M/navigation/`, `M/MainActivity.kt`, `M/ThirtySixApplication.kt`, `app/src/debug/**`, `app/build.gradle.kts`, `gradle/libs.versions.toml` (catalog comments: spec references → plain reasons), `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `res/xml/*.xml` comments, `.github/workflows/ci.yml` comments
- [ ] T053 [US5] Delete `config/detekt/baseline.xml` and the `baseline =` line; `KNOWN_VIOLATIONS` must be `emptySet()`; run quickstart G5 and record the numbers in the "Results" table. **Commit** `docs(025): comment policy met`.

---

## Phase 6: User Story 4 — rules fit on a page (P2)

**Goal**: the governance set in [contracts/governance-set.md](contracts/governance-set.md).

**Independent test**: quickstart G9.

- [ ] T054 [US4] Create `docs/archive/changelog-001-024.md` with `CLAUDE.md`'s sections from "Known Runtime Considerations" to the end, verbatim, first line `> Archive — not a rule. The rules are in .specify/memory/constitution.md.`; `git mv .claude/claude-app/{dev-workflow,meeting-note,project-context,sdd-roadmap}.md docs/archive/` and add the same first line to each.
- [ ] T055 [US4] Build the "facts to keep" table in this file: every bullet under "Facts to keep", "⚠️" notes and "Known carry-forward debt" in the old `CLAUDE.md` and `.specify/memory/constitution.md` 1.6.0, each with its destination: constitution clause, ADR number, check/test name, `AGENTS.md` line, or "obsolete" with reason. No row may be left without a destination.
- [ ] T056 [P] [US4] Write ADRs `docs/adr/0000-template.md` (from template) and `0001`–`0004` adapted from `/Users/xbism3/Documents/Template/android/docs/adr/` (0001 records `*RepositoryImpl`, shared contracts and the database in `core/` per research R2–R3, and the template gap; 0004 cites this app's own before/after numbers from the Results table).
- [ ] T057 [P] [US4] Write ADRs `0005`–`0012` listed in contracts/governance-set.md, each from the matching facts in T055 (context, decision, consequences; ≤ 1 page each), plus any ADR created during Phase 5.
- [ ] T058 [US4] Replace `.specify/memory/constitution.md` with the template's `/Users/xbism3/Documents/Template/android/.specify/memory/constitution.md`, `[PROJECT_NAME]` = ThirtySix Browser, Product Constraints filled from contracts/governance-set.md, `[FLAVORS_POLICY]` = "no product flavors", `[CRITICAL_FLOWS]` from governance-set.md, `[COVERAGE_TARGET]` = "every use case, repository, mapper and view model has a test", budgets from the Baseline table, Version **2.0.0**, Ratified 2026-04-30, Last Amended = commit date. Principle II's implementation-naming sentence names `*RepositoryImpl`. Under 250 lines; every MUST followed by **Enforced by** or **Review**; no Sync Impact Report comment left.
- [ ] T059 [US4] Create `docs/constitution-history.md`: one line per version 1.0.0 → 1.6.0 (from the old file's amendment notes, dates from git log) and 2.0.0 "Adopted the cleanarch Android template; phase order retired (ADR 0005)".
- [ ] T060 [US4] Create `AGENTS.md` from the template's, adapted: commands with the `JAVA_HOME` prefix, `connectedDebugAndroidTest` with the force-stop note, the 16 KB script path, package map for `com.raumanian.thirtysix.browser` with the ten features, writing/comment/test rules; Spec 026 pending work in one line. Under 100 lines. Replace `CLAUDE.md` with the single line `@AGENTS.md`.
- [ ] T061 [US4] Update the user's agent memory notes in `/Users/xbism3/.claude/projects/-Users-xbism3-Documents-SelfProject-ThirdtySixBrowser/memory/` whose text points to `CLAUDE.md` sections or old package paths, and its `MEMORY.md` index lines, so none refers to removed content.
- [ ] T062 [US4] Run quickstart G9. **Commit** `docs(025): constitution 2.0.0, AGENTS.md, ADRs, archive`.

---

## Phase 7: User Story 1 — nothing changes for the person using the browser (P1)

**Goal**: proof of SC-001–SC-003, SC-008.

**Independent test**: quickstart G6–G8, G10.

- [ ] T063 [US1] On the AVD from T005 (seeded baseline debug install), `adb install -r` the branch's debug APK; run quickstart G6 and record each store's result in the "Results" table.
- [ ] T064 [US1] Run quickstart G7: unit and instrumented suites (16 KB AVD; CI API 29). Every baseline test present (compare the test-class list from T004's reports to now: same class and method names, under new packages).
- [ ] T065 [US1] Run quickstart G8: release APK size vs baseline (±2 %), cold start 10 interleaved rounds against the baseline release (±5 %); Compose report counts unchanged (206 / 0).
- [ ] T066 [US1] Run quickstart G10 smoke on the 16 KB AVD release build and on API 24 AVD debug build. Diff the merged release manifest against `specs/025-architecture-cleanup/baseline/AndroidManifest-release.xml`: no difference allowed (FR-003).

---

## Phase 8: Polish & cross-cutting

- [ ] T067 Run quickstart G3, G4 and G9 once more on the final tree; delete `config/ktlint/baseline.xml` (empty since 4c) and the `ktlint { baseline… }` line.
- [ ] T068 `git merge main` if `main` moved since T001, moving any new files into the layout in the merge commit; rerun G1.
- [ ] T069 Open the PR `025-architecture-cleanup` → `main` with: the step list (one line per commit), Baseline vs Results tables, rule-proof table, the Spec 026 hand-off tables, and the attribution footer. Merge only with CI 6/6.

---

## Dependencies & execution order

```text
Phase 1 Setup ─► Phase 2 configs ─► Phase 3 US2 checks ─► Phase 4 US3 layout
                                                           ├─ 4a core (T017–T028) blocks all of 4b
                                                           ├─ 4b features T029 → … → T038 → T039 (sequential)
                                                           └─ 4c compose-rules T040 → T041
Phase 4 ─► Phase 5 US5 comments (T044–T052 parallel by folder) ─► T053
Phase 5 ─► Phase 6 US4 governance (needs Phase 5's ADRs and final numbers)
Phase 6 ─► Phase 7 US1 proof ─► Phase 8
```

- Feature commits are sequential because each edits the shared allowlist and several import the same core files; the order (leaf first) means no feature ever imports one that has not moved yet.
- Within Phase 5 the folder tasks touch disjoint files and can run in parallel; each still lands as its own commit.
- T056/T057 are parallel; T058 needs T055–T057.

## Parallel examples

```text
Phase 1: T002 ∥ T003
Phase 3: T012 (Konsist tests) ∥ T013 (detekt + editorconfig rules)
Phase 5: T044 ∥ T045 ∥ T046 ∥ T047 ∥ T048 ∥ T049 ∥ T050 ∥ T051 ∥ T052
Phase 6: T056 ∥ T057
```

## Implementation strategy

- **First mergeable point**: after T016 the branch already gives US2 (checks on, allowlisted). Because the branch merges once (owner decision), this is a safe stopping point, not a merge.
- **Incremental**: every commit after T016 shrinks an allowlist and keeps all gates green; if work pauses, the branch is still mergeable.
- **Do not combine** a move and a content edit in one commit, except the few edits listed in T018, T023, T024, T035, T038, T039.

---

## Baseline (T004)

| Measure | Value |
|---|---|
| Unit tests | 932 (0 failed) |
| Instrumented tests (16 KB AVD `TA016_API36_16K`) | 189 (0 failed) |
| Release APK bytes | 3,725,735 |
| Cold start median (ms), release, first-run state (onboarding), 10 launches | 271 |
| Comment share / files > 15 % / blocks > 5 lines | 21.9 % / 117 / 160 (`scripts/comment-stats.py`) |
| Compose report restartable / non-skippable | 206 / 0 |
| Seeded debug install (T005) | tabs 20 · history 20 · downloads 20 · bookmarks 2 in 1 folder · fireproof 1 · site permission 1 · protection exception 1 · Dark · Deutsch · DuckDuckGo · text 125 % · retention 30 days · 1 incognito tab open |

## Allowlist counts (T014)

| Allowlist | Entries at T014 | After Phase 4 | After Phase 5 |
|---|---|---|---|
| `KNOWN_VIOLATIONS` | 214 files (layering 10, placement 15, colors 2, dispatcher 1, comments ≈ 190) | | 0 |
| detekt baseline | 1,726 (ForbiddenComment 1,682 · MaxLineLength 28 · OutdatedDocumentation 12 · UnusedPrivateProperty 3 · LoopWithTooManyJumpStatements 1 — the last four newly seen because detekt now scans androidTest and debug) | | deleted |
| ktlint baseline | 77 (compose-rules 69: vm-forwarding 18, parameter-naming 16, modifier-missing 16, vm-injection 6, modifier-without-default 5, lambda-param-in-effect 5, multiple-emitters 3; standard 8) | | deleted |

## Rule proof (T015)

| Rule | Scratch violation | Failed with file named |
|---|---|---|
| layers depend only inward | `domain/` file importing `data.repository.TabRepositoryImpl` | ✅ `ProofDomain.kt` |
| domain is pure Kotlin | `import android.net.Uri` in `domain/` | ✅ `ProofDomain.kt` |
| features do not import other features | `feature/proofa` importing `feature/proofb` | ✅ `ProofA.kt` |
| core does not import features | `core/proof` importing `feature/proofb` | ✅ `ProofCore.kt` |
| dispatchers are injected (added, see note) | `Dispatchers.IO` in a presentation file | ✅ `ProofScreen.kt` |
| placement: use cases / contracts in domain | `ProofUseCase` outside `domain.usecase` | ✅ `ProofDomain.kt`, `ProofViewModel.kt` |
| placement: view models and UI state in presentation | `ProofViewModel` outside `presentation` | ✅ `ProofViewModel.kt` |
| placement: data types in their data folder | `ProofEntity` in `domain.model` | ✅ `ProofModels.kt` |
| placement: domain models are not data types | `ProofDto` in `domain.model` | ✅ `ProofModels.kt` |
| placement: hilt modules in di | `@Module object ProofModule` outside `di` | ✅ `ProofModule.kt` |
| placement: view models never reach past the domain | view model taking `TabDao` | ✅ `ProofViewModel.kt` |
| placement: view models expose read-only state | public `MutableStateFlow` | ✅ `ProofViewModel.kt` |
| placement: UI state is immutable | `class ProofUiState(var x: Int)` | ✅ `ProofViewModel.kt` |
| no hard-coded text | `Text("Hello hardcoded")` | ✅ `ProofScreen.kt` |
| colors from the theme | `Color(0xFF00FF00)` outside the theme | ✅ `ProofScreen.kt` (also detekt `MagicNumber`) |
| comment blocks ≤ 5 lines | 6-line KDoc | ✅ `ProofScreen.kt` |
| files ≤ 15 % comments | 38-line file, 12 comment lines | ✅ `ProofDense.kt` |
| detekt ForbiddenComment: spec ID | `// Spec 012 says…` | ✅ `ProofDomain.kt:10` |
| detekt ForbiddenComment: TODO without ticket | `// TODO: fix later` | ✅ `ProofDomain.kt:11` |
| compose-rules | composable emitting without a `modifier` | ✅ `ProofScreen.kt:21` (`compose:modifier-missing-check`) |
| detekt InjectDispatcher | `Dispatchers.IO` outside `di` | ❌ did not fire: the rule needs type resolution, which `./gradlew detekt` does not run. Replaced by the Konsist test `dispatchers are injected` (row above) |

Findings while wiring the checks (template gaps, also to report back to the template):
1. Konsist 0.18.1 is Java 17 bytecode; unit tests now run on a JDK 21 launcher (`tasks.withType<Test>`), compilation stays on toolchain 11.
2. `Konsist.scopeFromProduction()` also scanned the sample `.kt` files under `specs/*/contracts/`; the scope is now `scopeFromProduction(moduleName = "app")`.
3. detekt `InjectDispatcher` is inert without type resolution (row above).

## Constants to hand over (T027)

| Constant object / member | Owner feature | Moved in |
|---|---|---|
| `AppConstants`, `AppDefaults`, `BrowserLimits`, `UrlConstants`, `UrlPatterns` | several → `core/domain/util` (whole objects; template allows a named object in `core/`) | T027 |
| `StorageKeys` | settings, entrypoint, privacy data → `core/data/datasource/local/datastore` | T027 |
| `AssetPaths` | privacy | T037 |
| `DateFormats` | history | T033 |
| `NetworkTimeouts` | browser | T038 |
| `OnboardingSlides` | onboarding | T030 |
| `NavArgs` | root `navigation/` | T039 |

## Spec 026 — forwarding use cases (T029–T038)

| Use case | Forwards to | Feature |
|---|---|---|

## Spec 026 — screens without `onAction` (T041)

| View model | Public `on…` functions |
|---|---|

## Facts to keep (T055)

| Fact (old source) | Destination |
|---|---|

## Checkpoint results

| When | Where | Result |
|---|---|---|
| T028 (core layer done, `88140f1`) | G1 local | ✅ detekt, ktlint, lint, 949 unit tests, debug + release builds, 16 KB |
| T028 | `G9_API36_Clean` (WebView 134), full instrumented | 188/189; `SiteDialogsTest#back_cancels` timed out waiting for window focus (`RootViewWithoutFocusException`) and passed 2/2 run alone. The 16 KB AVD holds T005's seeded baseline until T061, so checkpoints run on this AVD |
| T023 check | same AVD, debug build, wikipedia.org | `cache/favicons/*.png` = PNG 48×48 RGBA; `cache/screenshots/*.webp` = WebP — same formats as before the writer change |

## Results (T053, T063–T065)

| Measure | Baseline | After | Within budget |
|---|---|---|---|
