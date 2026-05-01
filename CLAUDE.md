# ThirtySixBrowser Development Guidelines

Auto-generated from project context. Last updated: 2026-04-30 — **🚧 Pre-Spec 001**. Project khởi tạo, chưa start spec đầu tiên. Constitution v1.1.0 ratified.

> **Google Play Name**: "ThirtySix Browser" (Category: Tools / Productivity)
> Internal package: `com.raumanian.thirtysix.browser`

## Project Overview

ThirtySixBrowser là Android browser tối giản, lấy cảm hứng từ DuckDuckGo Browser nhưng đơn giản hơn — chỉ dùng những gì Android cung cấp sẵn (`WebView`, `DownloadManager`, Room, DataStore). Offline-first, không tài khoản, không cloud sync, không tracking. Toàn bộ data lưu on-device.

**Current Status:** 🚧 **Pre-Spec 001 (2026-04-30)**. Constitution + roadmap + dev-workflow + project-context đã hoàn tất. Sẵn sàng bàn chi tiết Spec 001 (`project-init-build-config`).

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
| — | Constitution | ✅ Done v1.1.0 | Project governing principles |
| 001 | `project-init-build-config` | ⬜ Next | Gradle Kotlin DSL + version catalog + 16KB-ready build |
| 002 | `clean-architecture-skeleton-di` | ⬜ | Module structure + Hilt + base classes |
| 003 | `theme-typography-darkmode` | ⬜ | Material3 + light/dark/system |
| 004 | `localization-multi-language` | ⬜ | 8 locales + locale switcher |
| 005 | `room-database-schema` | ⬜ | Bookmark/History/Tab entities + DAOs + WAL |
| 006 | `datastore-settings` | ⬜ | DataStore Preferences for settings |
| 007 | `webview-compose-wrapper` | ⬜ | `BrowserWebView` Composable bọc WebView |
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

- 2026-05-01: ✅ **Spec 001 done** — version catalog wired (AGP 9.1.1, Kotlin 2.3.21, Gradle 9.5.0, Compose BOM 2026.04.01); java/→kotlin/ source migrated; Detekt + ktlint + Lint strict (with documented disable list for AGP-9.x lint false-positives); debug-signed release fallback per Constitution v1.2.0; CI 6-job pipeline (build/unit-test/lint/static-analysis/verify-16kb) + 16KB-verify script (already detecting native lib `libandroidx.graphics.path.so` and confirming 16KB alignment). AGP held at 9.1.1 instead of 9.2.0 for Android Studio compat.
- 2026-05-01: Constitution v1.2.0 amended — Principle XI signing rule split into two scopes (distribution builds MUST use release keystore; local dev/CI MAY fall back to debug keystore with mandatory warning). Triggered by Spec 001 Q2 clarification.
- 2026-04-30: CI — disabled `instrumented-test` job until real UI test exists (avoid emulator hang on shutdown). Added explicit `adb emu kill` + `pkill` workaround in workflow for when re-enabled.
- 2026-04-30: Constitution v1.1.0 ratified — Principle III expanded with No-Hardcode Rule (18-row category table + `core/constants/` layout + Detekt MagicNumber gate)
- 2026-04-30: Project initialized — Constitution v1.0.0, meeting-note.md, project-context.md, sdd-roadmap.md, dev-workflow.md created

<!-- SPECKIT START -->

## Active Spec

**Current**: [Spec 002 — Clean Architecture Skeleton + Hilt DI](specs/002-clean-architecture-skeleton-di/plan.md) 🟡 Planned 2026-05-01

- Branch: `002-clean-architecture-skeleton-di`
- Status: 🟡 Planned — spec + 4 clarifications + research + plan + quickstart done; ready for `/speckit.tasks` then implement
- Previous: [Spec 001 — Project Init & Build Config](specs/001-project-init-build-config/plan.md) ✅ Implemented 2026-05-01
- Key new version pins (verified 2026-05-01 via `central.sonatype.com` + GitHub releases — see [research.md](specs/002-clean-architecture-skeleton-di/research.md)):
  - **Hilt 2.59.2** (first AGP-9 compatible release, 2026-02-20) via **KSP 2.3.7** (2026-04-22)
  - Critical: artifact ID is `com.google.dagger:hilt-compiler` (NOT `hilt-android-compiler` — that's kapt-era; KSP wiring with wrong ID compiles silently but generates zero Hilt code)
  - `androidx.hilt:hilt-navigation-compose:1.3.0` / `androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0` (NOT BOM-managed, pin separately) / `androidx.navigation:navigation-compose:2.9.8`
  - Test only: `kotlinx-coroutines-test:1.10.2` (for `BaseViewModel.launchSafely` test)
- Highest risk (per research.md Risk #1): Hilt × Kotlin 2.3.21 metadata not battle-tested. **Day-1 smoke test**: `./gradlew :app:kspDebugKotlin --info` must pass before building rest of spec. Fallback ladder: kapt → Kotlin 2.2.x → hold spec.

<!-- SPECKIT END -->
