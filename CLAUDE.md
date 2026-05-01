# ThirtySixBrowser Development Guidelines

Auto-generated from project context. Last updated: 2026-05-01 — **✅ Specs 001–007 done. Foundation Phase 1 6/6 + Phase 2 first spec complete.** Build config, Clean Architecture + Hilt, theme system, 8-locale i18n, Room schema, DataStore settings persistence, WebView Compose wrapper. Constitution v1.2.0. Phase 2 Spec 008 (`navigation-controls`) unblocked.

> **Google Play Name**: "ThirtySix Browser" (Category: Tools / Productivity)
> Internal package: `com.raumanian.thirtysix.browser`

## Project Overview

ThirtySixBrowser là Android browser tối giản, lấy cảm hứng từ DuckDuckGo Browser nhưng đơn giản hơn — chỉ dùng những gì Android cung cấp sẵn (`WebView`, `DownloadManager`, Room, DataStore). Offline-first, không tài khoản, không cloud sync, không tracking. Toàn bộ data lưu on-device.

**Current Status:** ✅ **Specs 001–004 done (2026-05-01)**. Foundation phase 1 hoàn tất 4/6: build config, Clean Architecture skeleton + Hilt DI, theme/typography/dark mode, 8-locale localization foundation. Specs 005 (`room-database-schema`) and 006 (`datastore-settings`) are parallel-available next; user picks order.

## Active Technologies

> Section này sẽ được speckit auto-append mỗi lần `/speckit.plan` chạy, ghi version các package thêm cho mỗi spec. Đầu mục dưới là baseline đã có sau khi project khởi tạo bằng Android Studio template.

- Kotlin (latest stable từ version catalog) / AGP 8.x / Gradle Kotlin DSL / Java 11
- **Baseline (project init)** packages có sẵn từ template:
  - `androidx.core:core-ktx`
  - `androidx.lifecycle:lifecycle-runtime-ktx`
  - `androidx.activity:activity-compose`
  - `androidx.compose:compose-bom`
  - `androidx.compose.ui:ui`, `ui-graphics`, `ui-tooling-preview`
  - `androidx.compose.material3:material3`
  - Test: `junit`, `androidx.test.ext:junit`, `androidx.test.espresso:espresso-core`, `androidx.compose.ui:ui-test-junit4`, `androidx.compose.ui:ui-tooling`, `androidx.compose.ui:ui-test-manifest`

### Dependencies (final versions looked up at implementation time per Constitution §IX)

- All versions MUST be fetched from `central.sonatype.com` / official release notes at moment of `libs.versions.toml` addition
- NEVER use remembered or guessed version numbers
- **All packages with native `.so` MUST be 16KB-page-size verified before merge** (Constitution §IX)

## Project Structure

```text
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/
│   ├── constants/
│   │   ├── AppConstants.kt           # App name, DB filename, package suffix markers
│   │   ├── AppDefaults.kt            # Default search engine, default home URL, default theme
│   │   ├── UrlConstants.kt           # Search engine URL templates (Google), about: URLs
│   │   ├── UrlPatterns.kt            # Regex for URL detection, hostname extraction
│   │   ├── StorageKeys.kt            # All DataStore preference keys
│   │   ├── NavArgs.kt                # All Bundle / nav argument key strings
│   │   ├── BrowserLimits.kt          # Max tabs, max history days, max bookmarks
│   │   ├── AnimationDurations.kt     # Tween/spring durations in ms
│   │   ├── NetworkTimeouts.kt        # WebView load timeout, download timeout
│   │   ├── NotificationChannels.kt   # Channel IDs + importance levels
│   │   ├── IntentActions.kt          # Custom intent action / extra strings
│   │   ├── AssetPaths.kt             # Asset file paths used at runtime
│   │   └── DateFormats.kt            # SimpleDateFormat / DateTimeFormatter patterns
│   ├── di/                           # Hilt qualifiers, dispatcher modules
│   ├── error/                        # AppError sealed class, exceptions
│   ├── result/                       # Result<T> wrapper
│   ├── dispatcher/                   # DispatcherProvider
│   ├── base/                         # BaseViewModel, BaseUseCase abstractions
│   └── extensions/                   # Context, Flow, String extensions
├── data/
│   ├── local/
│   │   ├── database/                 # Room AppDatabase, DAOs
│   │   ├── entity/                   # BookmarkEntity, HistoryEntity, TabEntity
│   │   └── datastore/                # SettingsDataStore (DataStore Preferences)
│   ├── repository/                   # *RepositoryImpl
│   └── mapper/                       # Entity ↔ Domain model
├── domain/
│   ├── model/                        # Pure Kotlin models (no Android imports)
│   ├── repository/                   # *Repository interfaces
│   └── usecase/                      # *UseCase classes
├── presentation/
│   ├── theme/
│   │   ├── Color.kt                  # Light/dark color schemes (no inline Color(0xFF...))
│   │   ├── Type.kt                   # Material3 Typography
│   │   ├── Shape.kt                  # Material3 Shapes
│   │   ├── Spacing.kt                # Spacing tokens (xs, sm, md, lg, xl)
│   │   └── AppTheme.kt               # MaterialTheme wrapper, dynamic color logic
│   ├── navigation/
│   │   ├── AppNavGraph.kt            # NavHost composable
│   │   └── AppDestination.kt         # Sealed class for all routes
│   ├── browser/                      # BrowserScreen, BrowserViewModel, components (WebView wrapper)
│   ├── tabs/                         # TabsScreen, TabsViewModel (multi-tab + incognito)
│   ├── bookmarks/                    # BookmarksScreen, BookmarksViewModel
│   ├── history/                      # HistoryScreen, HistoryViewModel
│   ├── downloads/                    # DownloadsScreen, DownloadsViewModel
│   ├── settings/                     # SettingsScreen, SettingsViewModel
│   └── onboarding/                   # OnboardingScreen, OnboardingViewModel
├── di/                               # Hilt modules: DatabaseModule, RepositoryModule, NetworkModule
├── ThirtySixApplication.kt           # @HiltAndroidApp
└── MainActivity.kt                   # @AndroidEntryPoint, splash, navigation host

app/src/main/res/
├── values/                           # strings.xml (EN default), dimens.xml, themes.xml
├── values-vi/                        # VI strings.xml
├── values-de/                        # DE strings.xml
├── values-ru/                        # RU strings.xml
├── values-ko/                        # KO strings.xml
├── values-ja/                        # JA strings.xml
├── values-zh/                        # ZH strings.xml
├── values-fr/                        # FR strings.xml
├── drawable/                         # VectorDrawables (no PNG except branding)
├── mipmap-*/                         # Launcher icon
└── xml/                              # backup_rules.xml, data_extraction_rules.xml

gradle/
└── libs.versions.toml                # Single source of truth for ALL versions
```

## Commands

```bash
# Bootstrap
./gradlew clean

# Run app on connected device / emulator (use Android Studio Run config or)
./gradlew installDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run instrumented tests (requires emulator / device)
./gradlew connectedDebugAndroidTest

# Static analysis
./gradlew lintDebug
./gradlew detekt
./gradlew ktlintCheck

# Build release APK / AAB
./gradlew assembleRelease
./gradlew bundleRelease

# Verify 16KB page size alignment of native libraries (CRITICAL — Constitution §IX)
./gradlew assembleRelease
unzip -p app/build/outputs/apk/release/app-release.apk lib/arm64-v8a/lib*.so 2>/dev/null | \
  objdump -p - | grep LOAD | awk '{print $NF}'
# Expected: every value MUST be 0x4000 (16KB) or larger; 0x1000 (4KB) is a constitution violation

# Test on 16KB emulator (recommended before merge of any spec adding native libs)
# Android Studio → AVD Manager → create AVD with API 35+ system image with 16K page size
```

## Code Style

- **Strict analysis**: Android Lint + Detekt + ktlint enforced — zero warnings/violations
- **Architecture**: Clean Architecture + MVVM — `core/data/domain/presentation/di` mandatory; pure Kotlin domain layer
- **Constants** (Constitution §III No-Hardcode Rule): NO magic numbers, NO hardcoded strings, NO inline colors/dimens/timeouts/URLs/keys — all values live in `core/constants/` or theme tokens
- **Colors**: ALWAYS via `MaterialTheme.colorScheme.*` — NEVER hardcode `Color(0xFF...)` (allowed: `Color.Transparent`, `Color.Unspecified`)
- **Typography**: ALWAYS via `MaterialTheme.typography.*` — NEVER hardcode `fontSize`/`fontWeight`/`TextStyle(...)`; use `.copy()` for overrides
- **Shapes**: ALWAYS via `MaterialTheme.shapes.*`
- **Strings**: ALWAYS via `stringResource(R.string.*)` — NEVER hardcode UI strings in Composable
- **Navigation**: ALWAYS via `AppDestination` sealed class — NEVER hardcode path strings
- **State**: All ViewModel `UiState` are immutable `data class`, exposed via `StateFlow<UiState>`, mutated via `MutableStateFlow.update { }`
- **DI**: Hilt only; ALL `@Module` annotations live in `di/` package
- **Repository**: ViewModels NEVER access DAOs/DataStore directly — must go through `*Repository` interface
- **UseCase**: All business logic in `domain/usecase/`; Repositories MUST NOT depend on other Repositories
- **16KB alignment**: Every package with `.so` MUST be verified 16KB-compatible BEFORE merge (Constitution §IX)

## Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Classes, Interfaces, Objects | `UpperCamelCase` | `BrowserViewModel`, `AppDestination` |
| Files | `UpperCamelCase.kt` (matches primary class) | `BrowserViewModel.kt` |
| Functions, properties, params | `lowerCamelCase` | `currentTab`, `loadUrl()` |
| Composable functions | `UpperCamelCase` (PascalCase) | `BrowserScreen`, `AddressBar` |
| Constants (`const val`) | `SCREAMING_SNAKE_CASE` | `BrowserLimits.MAX_TABS` |
| Top-level vals (non-const) | `lowerCamelCase` | `defaultDispatcher` |
| Private members | `_lowerCamelCase` for backing fields | `_uiState`, `uiState` |
| Room entities (data layer) | `UpperCamelCase` + `Entity` suffix | `BookmarkEntity` |
| Domain models (domain layer) | `UpperCamelCase`, no suffix | `Bookmark` |
| Room DAO interfaces | `UpperCamelCase` + `Dao` | `BookmarkDao` |
| Repository interface | `UpperCamelCase` + `Repository` | `BookmarkRepository` |
| Repository impl | `UpperCamelCase` + `RepositoryImpl` | `BookmarkRepositoryImpl` |
| ViewModel | `FeatureViewModel` | `BookmarksViewModel` |
| UI state | `FeatureUiState` (`data class`) | `BookmarksUiState` |
| UseCase | `VerbNounUseCase` | `GetBookmarksUseCase`, `AddHistoryEntryUseCase` |
| Hilt modules | `*Module` | `DatabaseModule`, `RepositoryModule` |
| Compose preview | `FunctionPreview` | `BrowserScreenPreview` |
| String resource keys | `feature_section_purpose` | `bookmarks_empty_title` |
| Drawable names | `ic_action_name` (icons) / `bg_purpose` (backgrounds) | `ic_tab_close`, `bg_onboarding_slide_1` |

## Theme & Typography

| Layer | Source | Usage |
|-------|--------|-------|
| Color | `MaterialTheme.colorScheme` (M3) + dynamic color (Android 12+) | All semantic colors |
| Typography | `MaterialTheme.typography` (M3 default) | All text |
| Shape | `MaterialTheme.shapes` (M3 default) | All rounded corners |
| Spacing | `presentation/theme/Spacing.kt` | All padding / margin (`Spacing.xs`/`sm`/`md`/`lg`/`xl`) |

- Theme mode: Light / Dark / System (default System) — persisted via DataStore
- Dynamic color: enabled on Android 12+ via `dynamicLightColorScheme(LocalContext.current)`
- NO custom font for v1.0 (Material3 default Roboto). If added later → bundle as local `assets/fonts/`, NEVER fetch runtime
- All padding/margin via `Spacing.*` tokens — no inline `.padding(16.dp)` repeated

## Key Architecture Decisions

| Decision | Choice |
|----------|--------|
| Application ID | `com.raumanian.thirtysix.browser` |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |
| Compile SDK | 36 (release with `minorApiLevel = 1`) |
| Java target | 11 |
| Build types | `debug` + `release` ONLY (no flavors) |
| DB filename | `thirtysix_browser.db` |
| DB engine | Room (SQLite WAL mode) |
| Settings storage | DataStore Preferences (`thirtysix_settings`) |
| Credential storage | None (browser does not store user credentials in v1.0) |
| WebView engine | `android.webkit.WebView` only (no GeckoView, no Custom Tabs) |
| Download | `android.app.DownloadManager` + `MediaStore.Downloads` (Scoped Storage) |
| DI | Hilt (all `@Module` in `di/` package) |
| Navigation | Navigation Compose with typed `AppDestination` sealed class |
| State | `StateFlow<UiState>` (unidirectional MVVM) |
| Async | kotlinx-coroutines + Flow |
| Splash | `androidx.core:core-splashscreen` |
| Localization default | English (EN) |
| Supported locales | EN, VI, DE, RU, KO, JA, ZH, FR (8 locales) |
| Locale switching | `AppCompatDelegate.setApplicationLocales` (per-app language API on Android 13+, fallback on older) |
| Theme | Material3 ColorScheme + dynamic color (Android 12+) |
| 16KB page size | NDK r27+, AGP 8.5+, all `.so` libs verified 16KB-aligned (Constitution §IX) |
| CI/CD | GitHub Actions — build/test/lint per PR |

## Google Play Strategy

### Positioning
- **Category**: Tools / Productivity (NOT VPN, NOT Privacy/Security)
- **Content Rating**: Everyone
- **Privacy**: No analytics, no crash reporting by default, zero data transmitted to any ThirtySix-controlled server
- **Pricing**: Free (no Pro tier in v1.0)

### Critical Rules
- v1.0 phải ship đầy đủ Phase 1–4 (Specs 001–018) trước Phase 5 (optional 019)
- Phase order is MANDATORY (Constitution §X) — no spec may be skipped or reordered
- Permissions giữ tối thiểu — chỉ `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`
- `addJavascriptInterface` FORBIDDEN unless allow-listed origin gating + spec justification
- Scoped Storage MUST be respected — no `MANAGE_EXTERNAL_STORAGE`
- `targetSdk = 36` for v1.0; bump within 12 months of any new Android release per Play policy

## Spec Roadmap

| # | Spec | Status | Description |
|---|------|--------|-------------|
| — | Constitution | ✅ Done v1.2.0 | Project governing principles |
| 001 | `project-init-build-config` | ✅ Done 2026-05-01 | Gradle Kotlin DSL + version catalog + 16KB-ready build |
| 002 | `clean-architecture-skeleton-di` | ✅ Done 2026-05-01 | Module structure + Hilt + base classes |
| 003 | `theme-typography-darkmode` | ✅ Done 2026-05-01 | Material3 + light/dark/system |
| 004 | `localization-multi-language` | ✅ Done 2026-05-01 | 8 locales + system per-app picker (Android 13+) |
| 005 | `room-database-schema` | ✅ Done 2026-05-01 | Bookmark/BookmarkFolder/History/Tab entities + DAOs + WAL + Hilt DatabaseModule + DB excluded from backup |
| 006 | `datastore-settings` | ✅ Done 2026-05-01 | DataStore Preferences (theme/language/search-engine/onboarding) + full Clean Arch data slice + ThemeMode moved to domain + MainActivity wired via use case + settings included in Auto Backup |
| 007 | `webview-compose-wrapper` | ✅ Done 2026-05-01 | `BrowserWebView` Composable bọc WebView + LinearProgressIndicator + localized error UI + Espresso-Web + Hilt URL injection |
| 008 | `navigation-controls` | ⬜ | Back/Forward/Reload/Stop/Home + predictive back |
| 009 | `address-bar-omnibox` | ⬜ | TextField nhập URL/query + suggestions |
| 010 | `search-engine-google` | ⬜ | Build Google search URL |
| 011 | `tabs-management` | ⬜ | Multi-tab + grid switcher + persist |
| 012 | `private-incognito-mode` | ⬜ | Tab incognito tách biệt cookie/history |
| 013 | `bookmarks-crud` | ⬜ | Add/edit/delete + folders |
| 014 | `history-view` | ⬜ | Group theo ngày + search + clear |
| 015 | `downloads-manager` | ⬜ | DownloadManager + scoped storage |
| 016 | `settings-screen` | ⬜ | Theme/language/search engine/clear data |
| 017 | `splash-screen` | ⬜ | SplashScreen API + branding |
| 018 | `onboarding-flow` | ⬜ | 3–4 slides chọn ngôn ngữ/theme/search |
| 019 | `tracker-blocker-hostlist` | ⬜ Optional | Host blocklist (only after 001–018 done) |

> Full details: `.claude/claude-app/project-context.md` and `.claude/claude-app/sdd-roadmap.md`

## Known Runtime Considerations

> Section này sẽ được điền khi từng spec phát hiện edge case / quyết định non-obvious cần ghi nhớ. Hiện chưa có entry vì project chưa start spec.

## Pending CI / Tooling Tasks

| Task | Trigger | Action |
|------|---------|--------|
| Re-enable `instrumented-test` job in `.github/workflows/ci.yml` | Khi bắt đầu **Spec 007** (`webview-compose-wrapper`) hoặc **Spec 011** (`tabs-management`) — spec đầu tiên có UI test thực sự cần emulator | Set `if: true` (hoặc xóa dòng `if: false`) trong job `instrumented-test`. Disabled 2026-04-30 vì `reactivecircus/android-emulator-runner@v2` hang trên default trivial test. |

## Recent Changes

- 2026-05-01: ✅ **Spec 007 done** — WebView Compose Wrapper shipped on branch `007-webview-compose-wrapper` (48 + T042 user-gate task; 94/94 unit tests pass — Spec 006 baseline 79 → +15 new across `BrowserViewModelTest` (10) + `ErrorReasonTest` (5)). **First feature-bearing UI in the project.** Pipeline: launcher tap → `MainActivity` → `AppNavGraph` → `BrowserScreen` → `BrowserWebView` (`AndroidView` wrapping `android.webkit.WebView`) → `https://example.com` rendered. **Espresso-Web** added as `androidTestImplementation` (groupId `androidx.test.espresso`, name `espresso-web`, version pinned to existing `espressoCore = "3.7.0"` per Constitution §IX — single Espresso train; pure-Java JAR, zero `.so` → 16KB-safe by construction). **Hilt instrumented-test runtime** also wired (`hilt-android-testing` + `kspAndroidTest(libs.hilt.compiler)`) for `@HiltAndroidTest` + `HiltAndroidRule` + `@UninstallModules` per-test override pattern. **Material-icons-core** added (Compose-BOM-managed, no version key, used by `BrowserErrorState` for `Icons.Filled.Warning`). Custom `HiltTestRunner` boots `HiltTestApplication`; `HiltTestActivity` provides a minimal `@AndroidEntryPoint` Compose host for the two instrumented test classes. **WebView lockdown** = 4 file-access settings off (FR-013) + zero `addJavascriptInterface` (FR-006) + `MIXED_CONTENT_NEVER_ALLOW` (FR-018) + permissions denied silently (FR-017) + `domStorageEnabled = true` (modern web requirement) + cookies persist via Android default (FR-016, local-only). Manifest expanded to 2 permissions (`INTERNET`, `ACCESS_NETWORK_STATE`); POST_NOTIFICATIONS reserved for Spec 015. **Lifecycle** via `DisposableEffect(Unit)` cleanup with `loadUrl("about:blank") + removeAllViews() + destroy()` for the WebView 116+ native-resource race; `LifecycleEventObserver` for `ON_PAUSE` / `ON_RESUME`. **State** = `BrowserUiState(currentUrl, loadingState)` + sealed `LoadingState{Idle, Loading(progress: Float), Loaded, Failed(reason: ErrorReason)}` + sealed `ErrorReason{NetworkUnavailable, DnsFailure, HttpError(statusCode), SslError, Generic}` with `toUserMessageRes()` mapping → 4 string keys × 8 locales = 32 translations. All shapes live in `presentation/browser/` (no `domain/model/` promotion — incremental scope per memory feedback). **Hilt URL injection** via `UrlConfigModule` (`@InstallIn(ViewModelComponent::class)`, `@Named("default_home_url")`); test override per-class via `@UninstallModules(UrlConfigModule::class)` + nested `FakeUrlConfigModule` inside `BrowserScreenOfflineErrorTest` — chosen over `@TestInstallIn` after analysis surfaced that the latter would globally replace the binding and break the happy-path/rotation tests. **CI** — `instrumented-test` job re-enabled in [.github/workflows/ci.yml](.github/workflows/ci.yml) (uncommitted change carried from `main` ships in this PR). Browser placeholder string `browser_screen_placeholder` removed from all 8 locales since `BrowserScreen` now does real work. **`@param:Named` annotation form** adopted for Kotlin 2.3 forward-compat warning. **Detekt fix**: 4-callback bundle in `BrowserWebViewCallbacks` data class to keep `BrowserWebView` parameter count under `LongParameterList.functionThreshold = 6`. **APK release size = 1.61 MB** (delta +50 KB vs Spec 006 baseline 1.56 MB; well under SC-008 ≤ 200 KB budget — most of the delta is `material-icons-core`). 16KB CI gate green: 24/24 native lib entries `align=0x4000` (zero new `.so` from this spec; system WebView is OS-provided, Espresso-Web is `androidTestImplementation` only). Constitution Check 11/11 PASS pre + post implementation. **T042 manual emulator UX gate (cold-start ≤ 5s, loading indicator 200ms first-show, locale verification across 8 locales, rotation no-reload-flash) DEFERRED to user device verification** — mirrors Spec 004 / Spec 006 manual-gate pattern. **Spec acceptance reframed during plan (R3)**: rotation preserves URL in `BrowserUiState` only; full DOM/scroll preservation is Spec 011 territory.
- 2026-05-01: ✅ **Spec 006 done** — DataStore Preferences settings persistence shipped on branch `006-datastore-settings` (47 + 1 deferred-to-user tasks, 79/79 unit tests pass). **DataStore Preferences 1.2.1** (verified `developer.android.com/jetpack/androidx/releases/datastore` 2026-05-01) — single new artifact `androidx.datastore:datastore-preferences`. **DISCOVERY post-impl**: DataStore Preferences DOES introduce native lib `libdatastore_shared_counter.so` for all 4 ABIs (used for multi-process counter coordination — present even in single-process apps). Plan + research had claimed "zero `.so`" — that claim was wrong, but **all native entries verified 16KB-aligned (`align=0x4000`)** by the project's CI script → SC-006 still PASS. Documentation correction noted; future specs should never claim "zero `.so`" without verifying the released APK. **First complete Clean Architecture data slice** in the project: `data/local/datastore/SettingsDataStore.kt` (wrapper) + `data/mapper/SettingsMapper.kt` + `data/repository/SettingsRepositoryImpl.kt` + `domain/model/{ThemeMode,LanguageOverride,SearchEngine,UserSettings}.kt` + `domain/repository/SettingsRepository.kt` + 5 use cases under `domain/usecase/` + Hilt `app/.../di/SettingsModule.kt` (object provider + abstract @Binds in one file). 4 settings keys ship v1: `theme_mode` (enum), `language_override` (sealed `LanguageOverride{FollowSystem,Explicit(bcp47)}` per Q2), `search_engine` (enum, Google), `is_onboarding_completed` (Boolean). Two clarifications applied: Q1 setters return `Result<Unit>` carrying raw `Throwable` (matches existing Spec 002 `Result.Error(throwable)` constructor — note: my contracts/research initially claimed `AppError` wrapping inside Error which the actual Spec 002 wrapper does not support; resolved by returning raw `Throwable` and letting consumers map via `AppError.from(...)` if needed; **caveat: `AppError.from(IOException) = Network` not `Database` semantically — known limitation, deferred to future Spec 002 amendment**); Q2 sealed type for language. **`ThemeMode` moved** from `presentation/theme/` → `domain/model/` via `git mv` (history preserved); test file moved similarly. **MainActivity rewired** — `MutableState<ThemeMode>` replaced by `@Inject ObserveUserSettingsUseCase` + `collectAsStateWithLifecycle(initialValue = UserSettings.DEFAULT)`. **Backup posture (revised post-lint)**: settings file is included in Auto Backup by Android's default behavior when `allowBackup="true"` and no `<include>` blocks are present. `backup_rules.xml` + `data_extraction_rules.xml` updated with comment blocks documenting the asymmetric policy (DB excluded vs settings included by default), but NO explicit `<include>` line because Android Lint correctly flagged that adding any `<include>` would flip semantics to "exclude-by-default" and conflict with the existing DB excludes. New constants: `core/constants/StorageKeys.kt` (with full schema-evolution doc-comment per FR-019), `core/constants/AppDefaults.kt`, plus `AppConstants.SETTINGS_DATASTORE_FILE_NAME = "thirtysix_settings"` extension. Test strategy: pure JVM `PreferenceDataStoreFactory.create` + JUnit `@TempFolder` — no Robolectric for the data-layer tests (faster than Spec 005 Room tests). One config tweak: ktlint `class-signature` rule disabled in `.editorconfig` (Spec 006 note) because it forced single-line constructor params and conflicted with detekt MaxLineLength; the conventional `class X @Inject constructor(\n  param,\n) : Y {` form is now the project standard. **APK release size = 1.56 MB** (up from Spec 005 baseline 1.4 MB; delta ≈ 160 KB driven mostly by the new `libdatastore_shared_counter.so` x 4 ABIs). 28 new unit tests across 5 test files (Spec 005 baseline 51 → 79 total; SettingsDataStoreTest 9 / SettingsMapperTest 8 / SettingsRepositoryImplTest 2 / SettingsModuleSmokeTest 1 / SettingsUseCasesTest 5 / domain.model.ThemeModeTest expanded 2→5). Concurrent-write test (US6) runs 100 iterations in 174ms — well under SC-004 budget. **Constitution Check 11/11 PASS pre + post implementation** (M1 from /speckit-analyze). **T043b Gate 8 (manual emulator no-flash visual) DEFERRED to user device verification** — cannot be executed by automated agent; mirrors Spec 004's manual-gate pattern (M2 from /speckit-analyze).
- 2026-05-01: ✅ **Spec 005 done** — Room database schema shipped on branch `005-room-database-schema` (pre-PR). Room 2.8.4 (verified `developer.android.com/jetpack/androidx/releases/room` 2026-05-01) + Turbine 1.2.1 + Robolectric 4.16.1 (test-only, pinned to SDK 33 via `app/src/test/resources/robolectric.properties` to keep Kotlin toolchain at JDK 11). Four `@Entity` classes with companion-object column-name `const val`s per Constitution §III, four `@Dao` interfaces with `Flow<List<...>>` observers, single `@Database(version=1, exportSchema=true)` `AppDatabase`, top-level `app/.../di/DatabaseModule.kt` providing `AppDatabase` singleton + 4 DAOs. FK on `bookmark.parent_folder_id` and self-FK on `bookmark_folders.parent_id` both `ON DELETE SET NULL` (orphan-to-root preserves user data). 3 indexes shipped: `history.visited_at`, `bookmark.parent_folder_id`, `tab.position`. WAL journal mode active (Room default; verified via `PRAGMA journal_mode`). Schema export wired to `app/schemas/com.raumanian.thirtysix.browser.data.local.database.AppDatabase/1.json` (committed to git). Strict-no-destructive migration policy: `git grep "fallbackToDestructiveMigration"` in `app/src/main/` = 0 matches; negative-path test asserts `IllegalStateException` on schema mismatch + verifies pre-existing data preserved. **DB excluded from Auto Backup** + **Device-to-Device Transfer** in both `backup_rules.xml` (Android <12) and `data_extraction_rules.xml` (Android 12+) — stricter than Constitution §VII baseline (which only requires opt-in). 29 new unit tests across 7 test files (51 total project unit tests). `WalConcurrencyTest` uses `runBlocking` for real-wall-clock concurrency assertion (1 s production-device target relaxed to 5 s for Robolectric SQLite shadow overhead). APK release size = 1.4 MB (DOWN from Spec 004 baseline 1.49 MB after R8 re-shrink absorbed Room runtime classes). 16KB CI gate ✅ — Room introduced zero `.so`. Detekt baseline UNCHANGED. Repository / Mapper / Domain model layer + favicon column + incognito tab persistence + encryption + ContentProvider + data seeding all explicitly DEFERRED per incremental-scope preference. Constitution Check 11/11 PASS pre- and post-design.
- 2026-05-01: ✅ **Spec 004 done** — Multi-Language Localization Foundation shipped on branch `004-localization-multi-language` (pre-PR). Pure XML + manifest + Gradle lint config, **zero Kotlin code changes** — the 7 placeholder Screens already wired `stringResource(R.string.*_screen_placeholder)` in Spec 002, so translations flow through automatically. 7 new locale files (`values-{vi,de,ru,ko,ja,zh,fr}/strings.xml`) covering 8 baseline keys (app_name + 7 placeholder Screen titles) per [data-model.md per-locale value table](specs/004-localization-multi-language/data-model.md#entity-2--baseline-string-catalog). EN baseline `app_name` typo fixed `ThirdtySixBrowser` → `ThirtySix Browser` (Q1 clarification — Play Store canonical). Brand "ThirtySix" preserved in Latin script across all locales; only descriptor "Browser" translated per [research R6](specs/004-localization-multi-language/research.md#r6--app_name-translation-strategy-across-8-locales-q1-clarification-implementation) (e.g., VI = "Trình duyệt ThirtySix", FR = "Navigateur ThirtySix", RU = "ThirtySix Браузер"). Single `values-zh/` (Simplified) covers all Chinese region variants via Android resource fallback. New `res/xml/locales_config.xml` declares 8 BCP-47 tags; `AndroidManifest.xml` `<application>` gets `android:localeConfig="@xml/locales_config"` + `tools:targetApi="33"` (suppresses `UnusedAttribute` since attribute requires API 33+ but minSdk = 24). Lint enforcement in `app/build.gradle.kts`: `error += listOf("MissingTranslation", "ExtraTranslation")` (Q2 clarification — belt-and-suspenders on top of `warningsAsErrors = true`). Both negative-path tests verified locally — lint blocks build on missing or extra translation. Late-discovered cleanup: `settings.gradle.kts:31` `rootProject.name` typo also fixed (`ThirdtySixBrowser` → `ThirtySixBrowser`); doc files keep historical typo references intentionally as decision-log entries. APK release size = 1.49 MB unchanged (resource files compile to negligible delta in `resources.arsc`); 16KB CI gate green (12/12 native lib entries aligned 0x4000); `lintDebug` + `testDebugUnitTest` (12/12) + `detekt` + `ktlintCheck` all green. NO new packages introduced — `androidx.appcompat:appcompat` deferred to Spec 016 when in-app switcher needs `AppCompatDelegate.setApplicationLocales`. **Manual emulator verification (Gates 3-7 in [quickstart.md](specs/004-localization-multi-language/quickstart.md)) deferred to user on a real Android 13+ device — not run in this implementation pass.** Constitution Check 11/11 PASS pre- and post-design.
- 2026-05-01: ✅ **Spec 003 done** — Theme + Typography + Dark Mode shipped (PR #4 merged commit `856f0bc`). `presentation/theme/` migrated from `ui/theme/` (rename + structural cleanup). `ThirtySixTheme` Composable wires Light/Dark/System + dynamic color (Android 12+) via `dynamicLightColorScheme`/`dynamicDarkColorScheme`. Brand: Deep Teal seed `#0F766E` (light primary) / `#5EEAD4` (dark primary), Cyan tertiary `#0891B2` / `#67E8F9` — full M3 ColorScheme (~30 roles × 2 schemes) in [Color.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/theme/Color.kt) with file-level `@Suppress("MagicNumber")` + rationale comment (hex literals ARE the constants per Constitution §III). Typography: Poppins (heading: Medium 500 + SemiBold 600) + Inter (body: Regular 400 + Medium 500), 4 `.ttf` bundled in [res/font/](app/src/main/res/font/) (~160KB total — within asset budget). Spacing tokens 5 levels (xs=4 / sm=8 / md=16 / lg=24 / xl=32 dp). `ThemeMode` enum (Light/Dark/System) — persistence deferred to Spec 006, UI toggle deferred to Spec 016 (currently in-memory `MutableState` in `MainActivity`). Cold-start window flash fix via `themes.xml` + `values-night/themes.xml` (FR-026/027). 3 unit tests pass (`SpacingTest`, `ThemeModeTest`, `TypographyTest`). Detekt baseline cleanup: 9 entries (3 FunctionNaming + 6 MagicNumber on theme colors) cleared post-rewrite. NO new packages (Compose BOM 2026.04.01 / Material3 1.4.0 sufficient). 2 clarifications applied: WCAG SC-010 gate (option A — Material Theme Builder verify pre-export); cold-start window background fix (option A).
- 2026-05-01: ✅ **Spec 002 done** — Clean Architecture skeleton + Hilt DI wired. Hilt 2.59.2 via KSP 2.3.7 (Day-1 smoke test passed first-try — no kapt/Kotlin-downgrade fallback needed). 4 core utilities (`Result<T>` 2-state terminal, `AppError` Network/Database/Unknown with `from()` mapper, `DispatcherProvider` interface + `DefaultDispatcherProvider` Hilt singleton, `BaseViewModel.launchSafely(onError, block)` with type-based exception → AppError mapping + CancellationException re-throw). 7 placeholder Composable Screens via `AppDestination` sealed class + `AppNavGraph`. APK release size delta = 88KB (well under 1MB SC-007 budget). 8 unit tests pass. Detekt baseline UNCHANGED — added `FunctionNaming.ignoreAnnotated: ['Composable']` config (structural fix, not baseline cover) + targeted `@Suppress("TooGenericExceptionCaught")` on `BaseViewModel.launchSafely` catch with rationale comment.
- 2026-05-01: ✅ **Spec 001 done** — version catalog wired (AGP 9.1.1, Kotlin 2.3.21, Gradle 9.5.0, Compose BOM 2026.04.01); java/→kotlin/ source migrated; Detekt + ktlint + Lint strict (with documented disable list for AGP-9.x lint false-positives); debug-signed release fallback per Constitution v1.2.0; CI 6-job pipeline (build/unit-test/lint/static-analysis/verify-16kb) + 16KB-verify script (already detecting native lib `libandroidx.graphics.path.so` and confirming 16KB alignment). AGP held at 9.1.1 instead of 9.2.0 for Android Studio compat.
- 2026-05-01: Constitution v1.2.0 amended — Principle XI signing rule split into two scopes (distribution builds MUST use release keystore; local dev/CI MAY fall back to debug keystore with mandatory warning). Triggered by Spec 001 Q2 clarification.
- 2026-04-30: CI — disabled `instrumented-test` job until real UI test exists (avoid emulator hang on shutdown). Added explicit `adb emu kill` + `pkill` workaround in workflow for when re-enabled.
- 2026-04-30: Constitution v1.1.0 ratified — Principle III expanded with No-Hardcode Rule (18-row category table + `core/constants/` layout + Detekt MagicNumber gate)
- 2026-04-30: Project initialized — Constitution v1.0.0, meeting-note.md, project-context.md, sdd-roadmap.md, dev-workflow.md created

<!-- SPECKIT START -->

## Active Spec

**Current**: None active — Spec 007 implementation complete (PR pending). Phase 2 first feature-bearing UI shipped. Spec 008 (`navigation-controls`) unblocked.

- Previous: [Spec 007 — WebView Compose Wrapper](specs/007-webview-compose-wrapper/plan.md) ✅ Implemented 2026-05-01 on branch `007-webview-compose-wrapper` (local; PR pending). 48 + T042 user-gate task; 94/94 unit tests pass; APK release 1.61 MB; 16KB CI gate green; Constitution 11/11 PASS.
- Phase progress: 001 ✅ / 002 ✅ / 003 ✅ / 004 ✅ / 005 ✅ / 006 ✅ / 007 ✅ — Phase 1 done; Phase 2 started.
- Suggested next: discuss scope of Spec 008 (`navigation-controls`) — Back / Forward / Reload / Stop / Home + predictive back. Builds on Spec 007's `BrowserWebView` (will need `WebView.canGoBack()`, `goBack()`, `reload()`, etc. surfaced through `BrowserViewModel`). Open instrumented-test PR observation: Gate 7 manual UX verification (cold-start ≤ 5 s, loading 200 ms first-show, error UI in 8 locales, rotation no-flash) deferred to user device verification per T042.

<!-- SPECKIT END -->
