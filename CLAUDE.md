# ThirtySixBrowser Development Guidelines

Auto-generated from project context. Last updated: 2026-05-01 — **✅ Specs 001–003 done**. Foundation hoàn tất (build config, Clean Architecture + Hilt, theme system). Constitution v1.2.0. Active: Spec 004 (`localization-multi-language`).

> **Google Play Name**: "ThirtySix Browser" (Category: Tools / Productivity)
> Internal package: `com.raumanian.thirtysix.browser`

## Project Overview

ThirtySixBrowser là Android browser tối giản, lấy cảm hứng từ DuckDuckGo Browser nhưng đơn giản hơn — chỉ dùng những gì Android cung cấp sẵn (`WebView`, `DownloadManager`, Room, DataStore). Offline-first, không tài khoản, không cloud sync, không tracking. Toàn bộ data lưu on-device.

**Current Status:** ✅ **Specs 001–003 done (2026-05-01)**. Foundation phase 1 (build config, Clean Architecture skeleton + Hilt DI, theme/typography/dark mode) hoàn tất. Active spec: **004 `localization-multi-language`** — 8 locale + locale switcher.

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
| 004 | `localization-multi-language` | ⬜ Next | 8 locales + locale switcher |
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

- 2026-05-01: ✅ **Spec 003 done** — Theme + Typography + Dark Mode shipped (PR #4 merged commit `856f0bc`). `presentation/theme/` migrated from `ui/theme/` (rename + structural cleanup). `ThirtySixTheme` Composable wires Light/Dark/System + dynamic color (Android 12+) via `dynamicLightColorScheme`/`dynamicDarkColorScheme`. Brand: Deep Teal seed `#0F766E` (light primary) / `#5EEAD4` (dark primary), Cyan tertiary `#0891B2` / `#67E8F9` — full M3 ColorScheme (~30 roles × 2 schemes) in [Color.kt](app/src/main/kotlin/com/raumanian/thirtysix/browser/presentation/theme/Color.kt) with file-level `@Suppress("MagicNumber")` + rationale comment (hex literals ARE the constants per Constitution §III). Typography: Poppins (heading: Medium 500 + SemiBold 600) + Inter (body: Regular 400 + Medium 500), 4 `.ttf` bundled in [res/font/](app/src/main/res/font/) (~160KB total — within asset budget). Spacing tokens 5 levels (xs=4 / sm=8 / md=16 / lg=24 / xl=32 dp). `ThemeMode` enum (Light/Dark/System) — persistence deferred to Spec 006, UI toggle deferred to Spec 016 (currently in-memory `MutableState` in `MainActivity`). Cold-start window flash fix via `themes.xml` + `values-night/themes.xml` (FR-026/027). 3 unit tests pass (`SpacingTest`, `ThemeModeTest`, `TypographyTest`). Detekt baseline cleanup: 9 entries (3 FunctionNaming + 6 MagicNumber on theme colors) cleared post-rewrite. NO new packages (Compose BOM 2026.04.01 / Material3 1.4.0 sufficient). 2 clarifications applied: WCAG SC-010 gate (option A — Material Theme Builder verify pre-export); cold-start window background fix (option A).
- 2026-05-01: ✅ **Spec 002 done** — Clean Architecture skeleton + Hilt DI wired. Hilt 2.59.2 via KSP 2.3.7 (Day-1 smoke test passed first-try — no kapt/Kotlin-downgrade fallback needed). 4 core utilities (`Result<T>` 2-state terminal, `AppError` Network/Database/Unknown with `from()` mapper, `DispatcherProvider` interface + `DefaultDispatcherProvider` Hilt singleton, `BaseViewModel.launchSafely(onError, block)` with type-based exception → AppError mapping + CancellationException re-throw). 7 placeholder Composable Screens via `AppDestination` sealed class + `AppNavGraph`. APK release size delta = 88KB (well under 1MB SC-007 budget). 8 unit tests pass. Detekt baseline UNCHANGED — added `FunctionNaming.ignoreAnnotated: ['Composable']` config (structural fix, not baseline cover) + targeted `@Suppress("TooGenericExceptionCaught")` on `BaseViewModel.launchSafely` catch with rationale comment.
- 2026-05-01: ✅ **Spec 001 done** — version catalog wired (AGP 9.1.1, Kotlin 2.3.21, Gradle 9.5.0, Compose BOM 2026.04.01); java/→kotlin/ source migrated; Detekt + ktlint + Lint strict (with documented disable list for AGP-9.x lint false-positives); debug-signed release fallback per Constitution v1.2.0; CI 6-job pipeline (build/unit-test/lint/static-analysis/verify-16kb) + 16KB-verify script (already detecting native lib `libandroidx.graphics.path.so` and confirming 16KB alignment). AGP held at 9.1.1 instead of 9.2.0 for Android Studio compat.
- 2026-05-01: Constitution v1.2.0 amended — Principle XI signing rule split into two scopes (distribution builds MUST use release keystore; local dev/CI MAY fall back to debug keystore with mandatory warning). Triggered by Spec 001 Q2 clarification.
- 2026-04-30: CI — disabled `instrumented-test` job until real UI test exists (avoid emulator hang on shutdown). Added explicit `adb emu kill` + `pkill` workaround in workflow for when re-enabled.
- 2026-04-30: Constitution v1.1.0 ratified — Principle III expanded with No-Hardcode Rule (18-row category table + `core/constants/` layout + Detekt MagicNumber gate)
- 2026-04-30: Project initialized — Constitution v1.0.0, meeting-note.md, project-context.md, sdd-roadmap.md, dev-workflow.md created

<!-- SPECKIT START -->

## Active Spec

**Current**: Spec 004 — `localization-multi-language` ⬜ Next (not yet specified)

- Branch: `004-localization-multi-language` (to be created via `/speckit.specify`)
- Status: ⬜ Pre-specify — Foundation phase (001+002+003) complete. Specs 004/005/006 unblocked and runnable in parallel after 002 per [sdd-roadmap.md](.claude/claude-app/sdd-roadmap.md#dependency-graph).
- Previous: [Spec 003 — Theme + Typography + Dark Mode](specs/003-theme-typography-darkmode/plan.md) ✅ Done 2026-05-01 (PR #4 merged)
- Scope (from roadmap): 8 locales (EN default, VI, DE, RU, KO, JA, ZH, FR) + locale switcher via `AppCompatDelegate.setApplicationLocales` (per-app language API on Android 13+, fallback on older). String resource files in `res/values/`, `res/values-{vi,de,ru,ko,ja,zh,fr}/`. UI toggle in Settings deferred to Spec 016 — Spec 004 ships locale infra + initial baseline strings only.
- Dependencies: Spec 002 ✅ (Hilt DI), Spec 003 ✅ (theme strings won't conflict). Independent of 005/006.
- New packages expected (verify at plan time): `androidx.appcompat:appcompat` (already in dependency wishlist per [project-context.md](.claude/claude-app/project-context.md#packages-dự-kiến-sẽ-verify-version--16kb-tại-thời-điểm-thêm)) — Kotlin/Java only, 16KB-safe.
- Next: bàn scope 004 → `/speckit.specify` → `/speckit.clarify` (if needed) → `/speckit.plan` → `/speckit.tasks` → implement → PR.

<!-- SPECKIT END -->
