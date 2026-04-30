<!--
================================================================================
SYNC IMPACT REPORT
================================================================================
Version Change: (none) → 1.0.0 (INITIAL — project constitution created)
                1.0.0 → 1.1.0 (MINOR — Principle III expanded with explicit
                                No-Hardcode Rule + categorized constants table)
                1.1.0 → 1.2.0 (MINOR — Principle XI signing rule scoped to
                                "distribution" builds; debug-keystore fallback
                                explicitly permitted for local dev/CI iteration
                                with mandatory warning string)

Modified Principles:
- III. Code Quality & Safety — added "No-Hardcode Rule (NON-NEGOTIABLE)" subsection
       with 18-row category table mapping every literal type to its constants file,
       plus the Constants file organization layout under core/constants/ and code
       review enforcement guidance (grep red flags + Detekt MagicNumber rule).
- XI. Build Configuration & Environment Separation — signing config rule split into
       two scopes: (1) distribution builds MUST use a release keystore; (2) local
       dev / CI iteration MAY fall back to the debug keystore when no release
       keystore is configured, provided a mandatory warning is emitted and the
       artifact is never uploaded to Play Store. Resolves Spec 001 Q2 clarification.

Added Sections (v1.0.0 initial):
- I.    Privacy & Security First: on-device only, no analytics, no account
- II.   Google Play Compliance: category, permissions, scoped storage
- III.  Code Quality & Safety: Android Lint, Detekt, ktlint, theme/string/route rules
- IV.   Clean Architecture (MVVM): feature-first folder, layer isolation, Hilt DI
- V.    Performance Excellence: 60fps, cold start, APK size limits
- VI.   Testing Discipline: JUnit + Compose UI Test + Espresso gates
- VII.  Offline-First Architecture: Room + DataStore, atomic writes, no cloud
- VIII. Localization & Accessibility: 8 locales, TalkBack, contrast, touch target
- IX.   Dependency Currency & 16KB Page Size Compliance: pub lookup + 16KB-verified
- X.    Simplicity & Build Order: spec-driven, YAGNI, no speculative code
- XI.   Build Configuration: debug/release only, no flavors, no hardcoded values

Removed Sections: N/A

Templates Requiring Updates:
- .specify/templates/plan-template.md ✅ compatible — Constitution Check section dynamically loads
- .specify/templates/spec-template.md ✅ compatible — no structural change needed
- .specify/templates/tasks-template.md ✅ compatible — phase structure unchanged

Follow-up TODOs:
- TODO(SIGNING): Configure signing config in Spec 001 ✅ DONE (FR-013, debug
                 fallback active per v1.2.0 §XI). Generate real release keystore
                 — DEFERRED until user supplies; debug-fallback continues until then.
- TODO(PLAY_LISTING): Prepare Play Store listing assets (icon, screenshots, copy) before v1.0 release
================================================================================
-->

# ThirtySixBrowser Constitution

> Google Play App Name: "ThirtySix Browser" (Category: Tools / Productivity)

## Core Principles

### I. Privacy & Security First

ThirtySixBrowser MUST process all data entirely on-device. No user data — bookmarks,
history, tabs, settings, downloads, or browsing data — MUST ever be transmitted to any
ThirtySix-controlled server. The app's core promise is privacy and minimalism; its own
data practices MUST embody that promise without exception.

- All user-created content (bookmarks, history, tabs, settings) MUST be stored
  exclusively in on-device Room DB (`thirtysix_browser.db`) + DataStore Preferences —
  zero cloud sync to any ThirtySix-controlled backend
- No analytics SDK, crash reporting tool, or user tracking library MUST be included by
  default; if ever added (e.g., Firebase Crashlytics for opt-in QA), MUST be opt-in only
  with explicit user consent and disabled by default
- No PII MUST be collected; no third-party tracking SDKs are permitted
- WebView MUST disable third-party cookies by default in incognito mode and configure
  safe defaults: `setAllowFileAccess(false)`, `setAllowContentAccess(false)`,
  `setAllowFileAccessFromFileURLs(false)`, `setAllowUniversalAccessFromFileURLs(false)`
- Incognito tabs MUST NOT write to history, MUST clear cookies/cache on tab close
- The app MUST NOT request runtime permissions it does not actively use
- Search queries typed in the address bar MUST NOT be logged or sent anywhere except
  the user-selected search engine

**Rationale**: A browser that leaks the URLs and search queries it handles is uniquely
dangerous. On-device-only architecture eliminates the attack surface entirely — not as
a marketing claim but as a structural constraint enforced at every layer.

### II. Google Play Compliance

All features MUST use Android's officially approved APIs. Google Play store positioning
MUST accurately reflect the app's purpose. Policy compliance is a first-class design
constraint.

- Google Play category: Tools / Productivity — NEVER positioned as VPN, proxy, ad-blocker,
  or privacy-focused security app (avoids stricter review categories)
- WebView MUST be `android.webkit.WebView` — Google Play approved, no custom Chromium
  build, no GeckoView (avoids large binary + sideload concerns)
- Permissions MUST be the minimum set: `INTERNET`, `ACCESS_NETWORK_STATE`,
  `POST_NOTIFICATIONS` (Android 13+ for download progress); any new permission
  request MUST be justified in the spec and visible to user
- Scoped Storage MUST be respected — downloads go to `MediaStore.Downloads` via
  `DownloadManager`; NO `MANAGE_EXTERNAL_STORAGE`, NO legacy storage permissions
- Foreground service notifications (if added later for downloads) MUST follow Android
  13+ runtime notification permission flow
- Google Play data safety form MUST be accurate — declare zero data collected
- Target SDK MUST stay current — `targetSdk = 36` for v1.0; bump within 12 months of
  any new Android release per Play policy
- App MUST NOT include WebView JavaScript bridges that expose privileged Java APIs to
  loaded web content (`addJavascriptInterface` is forbidden unless absolutely necessary
  and gated to allow-listed origins)

**Rationale**: Google Play is the primary distribution channel. Positioning ThirtySix
correctly (Tools, not VPN/Privacy) and using only Play-approved APIs is the compliant
path to launch and updates without policy strikes.

### III. Code Quality & Safety

All code MUST use strict Kotlin analysis. State MUST be immutable. UI MUST use the theme
system exclusively. **NO HARDCODED VALUES ANYWHERE — every literal MUST live in a named
constant, resource, theme token, or BuildConfig field.**

- Android Lint MUST be enforced — `./gradlew lintDebug` zero warnings/errors permitted
- Detekt + ktlint MUST be configured — zero violations permitted before merge; Detekt
  rule `MagicNumber` MUST be enabled (with the standard whitelist for `0`, `1`, `-1`,
  `2`)
- All ViewModel `UiState` classes MUST be immutable `data class` — exposed via
  `StateFlow<UiState>`, mutated only via `MutableStateFlow.update { }`

#### No-Hardcode Rule (NON-NEGOTIABLE)

EVERY literal value EXCEPT the trivial whitelist below MUST be declared as a named
constant in the appropriate location. Inline literals in feature code are a constitution
violation and MUST be rejected at code review.

**Trivial whitelist (allowed inline)**:
- Loop / index math: `0`, `1`, `-1`, `2`
- Empty strings: `""` for clearing, `" "` for separator (prefer named constant if reused)
- `Color.Transparent`, `Color.Unspecified`, `Modifier` defaults
- Test data inside `*Test.kt` files (test fixtures may be inline)

**Categorized rules — what MUST be extracted, where it lives, and the violation
example**:

| Category | NEVER (violation) | ALWAYS (compliant) | Location |
|----------|-------------------|--------------------|----------|
| **User-facing strings** | `Text("Bookmarks")` | `Text(stringResource(R.string.bookmarks_title))` | `res/values-<locale>/strings.xml` |
| **Colors** | `Color(0xFF1A73E8)` | `MaterialTheme.colorScheme.primary` | `presentation/theme/Color.kt` (theme tokens) |
| **Typography** | `fontSize = 16.sp, fontWeight = FontWeight.Bold` | `MaterialTheme.typography.titleMedium` | `presentation/theme/Type.kt` |
| **Shapes** | `RoundedCornerShape(12.dp)` | `MaterialTheme.shapes.medium` | `presentation/theme/Shape.kt` |
| **Dimensions (reused ≥2 times)** | `Modifier.padding(16.dp)` repeated | `Modifier.padding(Spacing.md)` or `dimensionResource(R.dimen.spacing_md)` | `presentation/theme/Spacing.kt` or `res/values/dimens.xml` |
| **Magic numbers / limits** | `if (tabs.size > 50) ...` | `if (tabs.size > BrowserLimits.MAX_TABS) ...` | `core/constants/BrowserLimits.kt` |
| **Animation durations** | `delay(300)` | `delay(AnimationDurations.SHORT_MS)` | `core/constants/AnimationDurations.kt` |
| **Timeouts** | `withTimeout(5_000)` | `withTimeout(NetworkTimeouts.WEBVIEW_LOAD_MS)` | `core/constants/NetworkTimeouts.kt` |
| **URLs (default home, search)** | `"https://www.google.com/search?q=..."` | `UrlConstants.GOOGLE_SEARCH_TEMPLATE` | `core/constants/UrlConstants.kt` |
| **Default values** | `searchEngine ?: "google"` | `searchEngine ?: AppDefaults.SEARCH_ENGINE` | `core/constants/AppDefaults.kt` |
| **DataStore preference keys** | `stringPreferencesKey("theme_mode")` | `StorageKeys.THEME_MODE` | `core/constants/StorageKeys.kt` |
| **Bundle / nav argument keys** | `arguments?.getString("tabId")` | `arguments?.getString(NavArgs.TAB_ID)` | `core/constants/NavArgs.kt` |
| **Notification channel IDs** | `"download_progress"` | `NotificationChannels.DOWNLOAD_PROGRESS_ID` | `core/constants/NotificationChannels.kt` |
| **Intent action / extra strings** | `Intent("com.raumanian...")` | `IntentActions.OPEN_TAB` | `core/constants/IntentActions.kt` |
| **Database table / column names** | inside `@Entity(tableName = "bookmarks")` literals scattered | use `BookmarkEntity.TABLE_NAME` companion const referenced by both DAO and entity | inside the `@Entity` companion object |
| **Regex patterns** | `Regex("^https?://...")` | `UrlPatterns.WEB_URL_REGEX` | `core/constants/UrlPatterns.kt` |
| **File / asset paths** | `"file:///android_asset/onboarding.svg"` | `AssetPaths.ONBOARDING_SLIDE_1` | `core/constants/AssetPaths.kt` |
| **Routes / destinations** | `navController.navigate("settings")` | `navController.navigate(AppDestination.Settings)` | `presentation/navigation/AppDestination.kt` |
| **Build-variant values** | `if (BuildConfig.DEBUG) "dev-api"` else `"prod-api"` inline | declare `BuildConfig.SEARCH_API_BASE` via `buildConfigField(...)` in `build.gradle.kts` | `app/build.gradle.kts` `buildConfigField` |
| **Date / time formats** | `SimpleDateFormat("yyyy-MM-dd")` | `DateFormats.ISO_DATE` | `core/constants/DateFormats.kt` |

**Constants file organization** (under `core/constants/`):

```
core/constants/
├── AppConstants.kt           # General app-wide (DB name, package suffixes, version markers)
├── AppDefaults.kt            # Default user-overridable values (default search engine, home page)
├── UrlConstants.kt           # Search engine templates, default home, about: URLs
├── UrlPatterns.kt            # Regex for URL detection, hostname extraction
├── StorageKeys.kt            # All DataStore preference keys
├── NavArgs.kt                # All Bundle / nav argument key strings
├── BrowserLimits.kt          # Max tabs, max history days, max bookmarks, etc.
├── AnimationDurations.kt     # Tween/spring durations in ms
├── NetworkTimeouts.kt        # WebView load timeout, download timeout
├── NotificationChannels.kt   # Channel IDs + importance levels
├── IntentActions.kt          # Custom intent action / extra strings
├── AssetPaths.kt             # Asset file paths used at runtime
└── DateFormats.kt            # SimpleDateFormat / DateTimeFormatter patterns
```

> A constants file MAY only declare `const val` (compile-time constants) or
> `@JvmStatic val` inside an `object`. Mutable `var` in a constants object is FORBIDDEN.

**Code review enforcement**:

- Reviewers MUST grep new diffs for inline literals. Common red flags:
  - `Color(0x` → must be in `Color.kt`
  - `.dp)` standalone numbers → check for repetition; if repeated, extract
  - `delay(`, `withTimeout(`, `Duration.ofSeconds(` → must reference a constants object
  - `"http`, `"https` → must be in `UrlConstants.kt`
  - `Text("` with literal text → must be `stringResource(...)`
  - `stringPreferencesKey("` / `intPreferencesKey("` → must be in `StorageKeys.kt`
- Detekt rule `MagicNumber` MUST flag undeclared numeric literals; suppression
  (`@Suppress("MagicNumber")`) MUST include a code comment explaining the exemption
- A spec MAY introduce new constants files; doing so MUST be documented in the spec's
  `plan.md` so reviewers know where to look

#### Other Quality Rules

- Theme enforcement — MANDATORY:
  - Colors MUST use `MaterialTheme.colorScheme.*` (or theme extensions for browser-specific
    semantic colors like incognito-tint, address-bar-bg)
  - Text styles MUST use `MaterialTheme.typography.*` — use `.copy()` for one-off overrides
  - Shapes MUST use `MaterialTheme.shapes.*`
- Localization — MANDATORY: all user-facing strings MUST use
  `stringResource(R.string.*)` — NEVER hardcoded in Composable
- Navigation — MANDATORY: all routes MUST use a typed `AppDestination` sealed class
  (or Navigation 3 type-safe routes) — NEVER hardcode path strings
- Resources — MANDATORY: drawable VectorDrawable preferred over PNG; no raster icons
  unless required for branding (launcher icon exception)

**Rationale**: Hardcoded values are the #1 source of regressions, broken localization,
and brittle UI in Android apps. A literal scattered across 12 Composables means 12
places to change when the design tweaks 16dp → 20dp, or when the search engine endpoint
moves. Centralized constants make every cross-cutting change a one-line edit and make
testing trivial (override one constant in tests instead of mocking 12 call sites).
A code review that lets `Color(0xFF1A73E8)` slip through is a constitution violation.

### IV. Clean Architecture (MVVM, Mandatory)

The codebase MUST be organized by feature with three layers per feature.
Each feature MUST be independently developable and testable. Unidirectional data flow
is mandatory.

- Top-level structure: `core/`, `data/`, `domain/`, `presentation/`, `di/` — NO
  feature code outside these layers
- `domain/` layer MUST be pure Kotlin — zero Android SDK or framework imports permitted
  in `domain/model/`, `domain/repository/`, `domain/usecase/`
- Repository pattern MUST abstract all data sources; ViewModels MUST NOT access DAOs
  or DataStore directly
- UseCase pattern MUST be used for all business logic; Repositories MUST NOT depend on
  other Repositories (violates layer boundaries)
- Dependency injection MUST use Hilt only; ALL `@Module` annotations MUST be in `di/`
  package; no `@Inject` constructor parameters bypassing module boundaries
- State management MUST follow MVVM unidirectional flow:
  `UI Event → ViewModel → UseCase → Repository → DataSource → Flow → UiState → UI`

```
✅ Allowed:
  ViewModel → UseCase → Repository → DataSource (Room/DataStore/WebView)
  Repository → Core Service (e.g., DispatcherProvider)

❌ Forbidden:
  Repository → Repository
  ViewModel → DAO (bypasses repository)
  ViewModel → DataStore (bypasses repository)
  Feature A → Feature B internal class
  Composable → Repository (must go through ViewModel)
```

**Rationale**: Feature isolation enables the 18-spec roadmap to be implemented and
tested incrementally. Clean Architecture boundaries ensure each component has a single
responsibility — critical for solo developer maintainability.

### V. Performance Excellence

The app MUST feel native. 60fps is non-negotiable. WebView startup and tab switching
are the primary performance-critical surfaces.

- App cold start (intent → first frame ready) MUST be ≤ 1.5s on Pixel 5 / equivalent
- WebView first paint after URL submitted MUST be limited only by network latency —
  no app-side processing > 50ms before WebView starts loading
- UI MUST maintain 60fps during tab switcher animation, settings list scroll, and
  bookmark/history list scroll on Pixel 5+
- All UI interactions (tap, swipe, navigation transitions) MUST respond within 100ms
- Tab switcher grid MUST render ≤ 200ms for 10 tabs (snapshot bitmap-cached, not
  re-rendered)
- Compose `LazyColumn` / `LazyVerticalGrid` MUST use stable `key`s — never index-only
- Main APK release size MUST remain ≤ 10MB (after R8); AAB upload ≤ 8MB
- Performance benchmark device: Pixel 5 (minimum target for all 60fps validations);
  also test on a low-end device (e.g., Pixel 3a or emulator with `--memory=2048`)
- Process death recovery: app state (active tab, open tabs, scroll positions) MUST
  restore correctly after Android kills the process in background

**Rationale**: A browser that stutters, takes >1.5s to cold start, or loses tabs on
process death feels broken. 60fps on Pixel 5 ensures the experience holds across the
typical Android user base.

### VI. Testing Discipline

Unit tests are REQUIRED for all business logic. Compose UI tests are REQUIRED for
critical user flows. Integration tests for Room and DataStore are REQUIRED.

- Unit tests MUST cover: repositories, ViewModels, use cases, mappers, URL parsing,
  search engine URL builders, host blocklist matcher (if Spec 019)
- Compose UI tests MUST cover: address bar input flow, tab switcher tap, bookmark
  add/delete, settings toggles, onboarding flow
- Instrumented tests MUST cover: Room DAOs, DataStore read/write, WebView basic load
  (using a localhost test page)
- `./gradlew testDebugUnitTest` MUST pass with zero failures before any spec is
  marked complete
- `./gradlew lintDebug` MUST pass with zero warnings before any spec is marked
  complete
- `./gradlew detekt` MUST pass with zero violations before any spec is marked complete
- Compose UI tests MUST run on at least one emulator API level (recommend API 35 with
  16KB page size enabled) per CI run
- Test coverage target: ≥ 70% for `domain/` and `data/` layers (no hard target for
  `presentation/` due to Compose test cost)

**Rationale**: A browser that fails to render bookmarks, drops history, or crashes on
common URLs causes direct harm to users. Tests prevent regressions across an 18-spec
roadmap.

### VII. Offline-First Architecture

All non-network features MUST operate without connectivity. All writes MUST be atomic.
No data loss under any failure mode is acceptable.

- ALL features (bookmarks, history, tabs, settings, downloads list view) MUST function
  fully offline — the ONLY exception is WebView page load + outbound search query
- All user data MUST be persisted to Room DB (with `journal_mode = WAL`) within 500ms
  of any change — partial writes MUST NOT corrupt existing data
- No data loss on app crash — all user-created content MUST be recoverable on next
  launch (Room + WAL provides this guarantee)
- DataStore writes MUST use `suspend` API; never block main thread
- Settings changes MUST be reflected in UI within 100ms (Flow-driven recomposition)
- Process death MUST not lose tab state — open tabs persist via `TabEntity` rows in
  Room; active tab ID persists via DataStore
- Imports / data migrations MUST be atomic — Room migration failures MUST roll back,
  not corrupt the DB
- Database backups (Android Auto Backup) MUST be opt-in per `android:fullBackupContent`
  rules in `AndroidManifest.xml` — bookmarks/history MAY be backed up; secrets (none
  in v1) MUST be excluded

**Rationale**: A browser is used in airplane mode, on the metro, in places with bad
signal. Loss of bookmarks or open tabs because of a crash undermines trust.

### VIII. Localization & Accessibility

ThirtySixBrowser targets a global user audience. All locales MUST be supported from
launch. All UI MUST comply with Android Accessibility guidelines.

- Default locale: **English (EN)**
- Supported locales: EN, VI, DE, RU, KO, JA, ZH, FR — all via `res/values-<locale>/strings.xml`;
  no hardcoded strings in UI code
- All user-facing strings MUST be externalized in `strings.xml` before any feature
  implementation begins — never add a string directly to a Composable
- TalkBack MUST be supported — all interactive elements require `contentDescription`
  or `Modifier.semantics { contentDescription = ... }`
- Decorative-only icons MUST set `contentDescription = null`
- Color contrast MUST meet WCAG AA (4.5:1 for body text, 3:1 for large text) on both
  light and dark themes
- All interactive elements MUST have minimum touch target of 48×48dp
- Dynamic font scaling MUST be supported — Compose default (`sp` units) handles this
  if no hardcoded `dp` font sizes
- RTL layout support is NOT required for v1.0 (no RTL locales in supported set)
- Locale switching MUST take effect without app restart (use `AppCompatDelegate.setApplicationLocales`
  with per-app language preferences API on Android 13+, fallback config-change recreate
  on older)

**Rationale**: 8 target locales cover the user communities where minimalist tool apps
have strong demand. Accessibility is required for Play Store compliance and expands
the addressable audience.

### IX. Dependency Currency & 16KB Page Size Compliance (NON-NEGOTIABLE)

All third-party packages MUST use the latest stable versions at the moment of addition.
**ALL packages with native libraries (`.so` files) MUST be 16KB page size compatible**
to support Android 15+ / Android 16+ devices.

- **Before adding any package, the latest stable version MUST be looked up at that
  exact moment** — NEVER use remembered, guessed, or previously researched version
  numbers, even from the same session
- Sources for version lookup: `central.sonatype.com`, `androidx` release notes,
  `mvnrepository.com`, official GitHub releases
- Hardcoded or memorized version numbers MUST NOT be used; always verify current versions
- Version pinning MUST live in `gradle/libs.versions.toml` (version catalog) — no
  inline version strings in `build.gradle.kts`
- Major version upgrades MUST include explicit compatibility verification before adoption

#### 16KB Page Size — CRITICAL

Android 15 (API 35) introduced 16KB memory page size support. Android 16 (API 36)
makes 16KB devices common. Native libraries (`.so`) compiled with the legacy 4KB
alignment **WILL NOT LOAD** on 16KB devices, causing app crash on launch.

- **Every package with `.so` files MUST be verified 16KB-compatible BEFORE merge**:
  1. Check the library's release notes / changelog for "16KB page size" support
  2. Build a release APK and verify alignment:
     ```bash
     ./gradlew assembleRelease
     unzip -p app/build/outputs/apk/release/app-release.apk lib/arm64-v8a/lib*.so | \
       objdump -p - | grep LOAD | awk '{print $NF}'
     # All values MUST be 0x4000 (16KB) or larger
     ```
  3. If any `.so` is < 0x4000 alignment → bump library version, find alternative, or
     reject the addition
- **NDK version**: NDK r27+ MUST be used (default 16KB-aligned `.so` output)
- **AGP version**: AGP 8.5+ MINIMUM, AGP 8.7+ recommended
- **Gradle properties** MUST include:
  ```properties
  android.bundle.enableUncompressedNativeLibs=true
  ```
- **CI gate**: A release build job MUST run `objdump` alignment check on every PR
  that touches `libs.versions.toml` or `build.gradle.kts`
- Pure Kotlin / Java libraries (no `.so`) are AUTOMATICALLY 16KB-safe — but transitive
  dependencies MUST still be checked
- Research documentation in `research.md` MUST include the date when versions were
  verified AND 16KB compliance was confirmed

#### Reference
- https://developer.android.com/guide/practices/page-sizes
- https://android-developers.googleblog.com/2024/08/adding-16-kb-page-size-to-android.html

**Rationale**: Maven artifact versions change continuously. A version pinned during
planning may introduce breaking changes by implementation time. The 16KB page size
constraint is non-negotiable: shipping a `.so` aligned to 4KB on a 16KB device causes
immediate crash on launch — a Play Store policy violation and a broken user experience
on every modern Pixel device.

### X. Simplicity & Build Order

v1.0 MUST ship Phase 1–4 (Specs 001–018) complete and quality-gated. Feature additions
MUST follow the spec roadmap. No speculative code is permitted.

- **Phase order is MANDATORY** — no spec may be skipped or reordered without updating
  `sdd-roadmap.md` first:
  - Phase 1 (Foundation): 001–006 — project setup, architecture, theme, l10n, Room,
    DataStore
  - Phase 2 (Core Browser): 007–012 — WebView, navigation, omnibox, search, tabs,
    incognito
  - Phase 3 (Data Features): 013–015 — bookmarks, history, downloads
  - Phase 4 (Settings & Polish): 016–018 — settings, splash, onboarding
  - Phase 5 (Optional): 019 — tracker blocker host list (only after 001–018 complete)
- **Implementation sequence within Phase 1 is MANDATORY**: 001 → 002 → (003, 004,
  005, 006 parallel)
- YAGNI applies to all implementation decisions: no speculative code, no future-use
  abstractions, no features without a corresponding spec in the roadmap
- Every new feature MUST have a corresponding spec in `sdd-roadmap.md` before
  implementation begins
- Code added speculatively for "future use" MUST NOT be merged
- Pro tier / monetization is OUT OF SCOPE for v1.0 — entire app is free

**Rationale**: Shipping a complete, polished basic browser is what defines v1.0
success. A stripped-down or unpolished v1.0 with a half-built tracker blocker would
undermine the "minimalist, just works" positioning. Phase-gating enforces this.

### XI. Build Configuration & Environment Separation

Builds MUST use only `debug` and `release` types. NO build flavors. No environment-specific
value MUST be hardcoded.

- ONLY `debug` and `release` build types MUST be configured — NO `dev`, `staging`,
  `prod` flavors (decision documented in `meeting-note.md`)
- `debug` and `release` MAY have distinct application IDs ONLY if needed for parallel
  install (e.g., `com.raumanian.thirtysix.browser` vs `com.raumanian.thirtysix.browser.debug`)
- API endpoints, default search engine URLs, and other configurable values MUST be
  defined in `BuildConfig` fields or `core/constants/` `object` — NEVER hardcoded
  in Composable/ViewModel code
- Signing config — two scopes:
  - **Distribution builds** (Play Store upload, public release, any artifact shipped
    to end users): `release` build type MUST use a release keystore loaded via
    `local.properties` or environment variable. The release keystore file and
    credentials MUST NEVER be committed to git.
  - **Local dev / CI iteration** (when no release keystore is configured):
    `assembleRelease` MAY fall back to the debug keystore (`~/.android/debug.keystore`)
    so developers can verify R8/minify/proguard end-to-end without first generating a
    release keystore. The fallback MUST:
    1. emit a build-log warning containing the literal substring
       `"release built with DEBUG signature — NOT for distribution"`,
    2. activate ONLY when neither `local.properties` nor environment variables provide
       release keystore credentials,
    3. produce artifacts that MUST NOT be uploaded to Play Store under any circumstance.
  - When release keystore credentials are present (via either source), the build MUST
    automatically switch to the release keystore — no script changes required.
- ProGuard / R8 MUST be enabled for `release` (`isMinifyEnabled = true` and
  `isShrinkResources = true`) — Spec 001 enables R8 from the start (stricter than the
  earlier "may start disabled" allowance, intentional baseline)
- Java target: 11 (matches existing scaffold)
- Kotlin JVM target: 11

**Rationale**: Avoiding flavors keeps the build matrix simple for a solo dev. The 16KB
constraint already complicates the dependency story; build flavors would multiply
that complexity without adding value for a free, single-tier app.

## Technical Standards

### Platform & Stack

- **Language**: Kotlin (latest stable from version catalog)
- **UI**: Jetpack Compose + Material3 (Compose BOM)
- **Min SDK**: 24 (Android 7.0+)
- **Target SDK**: 36
- **Compile SDK**: 36 (release with `minorApiLevel = 1`)
- **Java target**: 11
- **State Management**: ViewModel + StateFlow / SharedFlow (unidirectional data flow)
- **Dependency Injection**: Hilt (all `@Module` in `di/` package)
- **Navigation**: Navigation Compose with typed `AppDestination` sealed class
- **Theming**: Material3 ColorScheme + dynamic color (Android 12+); light/dark/system
- **Persistence**: Room (WAL mode) + DataStore Preferences
- **Async**: kotlinx-coroutines + Flow
- **WebView**: `android.webkit.WebView` wrapped in Compose `AndroidView`
- **Download**: Android `DownloadManager`
- **Splash**: `androidx.core:core-splashscreen`
- **Locale**: `androidx.appcompat:appcompat` + per-app language API on Android 13+
- **Test**: JUnit, Compose UI Test, Espresso, Truth, MockK / Turbine

### Android Permissions & Entitlements

| Permission | Purpose | Spec |
|------------|---------|------|
| `android.permission.INTERNET` | WebView page load | 007 |
| `android.permission.ACCESS_NETWORK_STATE` | Offline detection | 007 |
| `android.permission.POST_NOTIFICATIONS` | Download progress (Android 13+) | 015 |

### Architecture

```
app/src/main/kotlin/com/raumanian/thirtysix/browser/
├── core/
│   ├── base/                # BaseViewModel, BaseUseCase
│   ├── di/                  # Hilt qualifiers, dispatcher modules
│   ├── error/               # AppError sealed class, Result wrapper
│   ├── extensions/          # Context, Flow, String extensions
│   ├── result/              # Result<T> wrapper
│   ├── dispatcher/          # DispatcherProvider
│   └── constants/           # AppConstants, UrlConstants, StorageKeys
├── data/
│   ├── local/
│   │   ├── database/        # Room AppDatabase, DAOs
│   │   ├── entity/          # *Entity classes
│   │   └── datastore/       # SettingsDataStore
│   ├── repository/          # *RepositoryImpl
│   └── mapper/              # Entity ↔ Domain model
├── domain/
│   ├── model/               # Pure Kotlin models
│   ├── repository/          # *Repository interfaces
│   └── usecase/             # *UseCase
├── presentation/
│   ├── theme/               # Color, Typography, Shape, AppTheme
│   ├── navigation/          # AppNavGraph, AppDestination
│   ├── browser/             # BrowserScreen, BrowserViewModel
│   ├── tabs/                # TabsScreen, TabsViewModel
│   ├── bookmarks/           # BookmarksScreen, BookmarksViewModel
│   ├── history/             # HistoryScreen, HistoryViewModel
│   ├── downloads/           # DownloadsScreen, DownloadsViewModel
│   ├── settings/            # SettingsScreen, SettingsViewModel
│   └── onboarding/          # OnboardingScreen, OnboardingViewModel
├── di/                      # Hilt modules: DatabaseModule, RepositoryModule, ...
├── ThirtySixApplication.kt  # @HiltAndroidApp
└── MainActivity.kt          # @AndroidEntryPoint, splash, navigation host
```

## Development Workflow

### Testing Gates

All specs MUST pass before marking complete:

1. All unit tests pass (`./gradlew testDebugUnitTest`)
2. All Compose UI tests pass (`./gradlew connectedDebugAndroidTest` if applicable)
3. Static analysis with zero warnings (`./gradlew lintDebug`)
4. Detekt + ktlint pass (`./gradlew detekt ktlintCheck`)
5. No hardcoded strings in UI (`stringResource(R.string.*)` validation)
6. No hardcoded colors / typography / magic numbers in feature code
7. **Release APK builds and `.so` files are 16KB-aligned** (when applicable)
8. App runs without crash on emulator API 35 with 16KB page size enabled

### Review Requirements

- All code changes MUST be reviewed before merge
- Performance-sensitive changes MUST include before/after measurements (60fps,
  cold start timing, APK size)
- New permission additions MUST be justified in the spec
- Any new package addition MUST have version + 16KB compliance lookup documented
  inline in `research.md` and `project-context.md`

### Quality Checks

- WebView code MUST be tested on at least one Android 7.0 (min SDK) emulator AND one
  Android 16 (target SDK) device per significant change
- App APK size MUST be measured after each new dependency is added
- Tab switcher MUST be benchmarked on Pixel 5 with 10+ open tabs at each change to
  the switcher code
- Cold start MUST be benchmarked on Pixel 5 at the end of each phase
- Locale switching MUST be visually verified across all 8 locales before v1.0 release

## Governance

This constitution establishes non-negotiable principles for ThirtySixBrowser development.
All implementation decisions MUST align with these principles.

### Amendment Process

1. Proposed amendments MUST be documented with rationale
2. Amendments MUST be reviewed for impact on existing specs and roadmap phases
3. Breaking changes require migration plan before approval
4. Version MUST be incremented according to semantic versioning:
   - MAJOR: Principle removal or incompatible redefinition
   - MINOR: New principle or material expansion of existing principle
   - PATCH: Clarification or wording refinement

### Compliance

- All specs MUST verify compliance with relevant principles before implementation
- Complexity exceeding these standards MUST be explicitly justified in the plan's
  Complexity Tracking table
- Deviations MUST be documented with rationale and approved by project lead (Xbism3)
- Google Play policy changes MUST trigger an immediate constitution review
- 16KB page size violations MUST trigger an immediate fix-or-revert — no merging on
  red 16KB CI gate
- Constitution supersedes all other practices; in case of conflict, the constitution wins

**Version**: 1.2.0 | **Ratified**: 2026-04-30 | **Last Amended**: 2026-05-01
