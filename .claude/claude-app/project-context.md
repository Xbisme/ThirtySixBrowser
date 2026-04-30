# ThirtySixBrowser Android — Project Context & Progress

> Cập nhật lần cuối: 2026-04-30 — **Project khởi tạo, chưa start spec 001**.
> Dùng để Claude hiểu ngữ cảnh dự án qua các cuộc hội thoại.
> **QUAN TRỌNG**: Đọc file này + sdd-roadmap.md + dev-workflow.md + constitution.md khi bắt đầu hội thoại mới.

## Trạng thái dự án: 🚧 Pre-Spec 001

Project mới khởi tạo bằng Android Studio template. Đã chốt scope, kiến trúc, tech stack qua `meeting-note.md`. Chuẩn bị bắt đầu Spec 001.

### Tổng quan dự án

- **App Name**: ThirtySixBrowser
- **Concept**: Android browser tối giản, lấy cảm hứng từ DuckDuckGo Browser nhưng đơn giản hơn — chỉ dùng những gì Android cung cấp sẵn (WebView, DownloadManager, Room, DataStore)
- **Platform**: Android (phone + tablet)
- **Category**: Productivity / Tools (Google Play)
- **Developer**: Xbism3 (solo)
- **Business Model**: Free (chưa có Pro tier)
- **Package**: `com.raumanian.thirtysix.browser`

---

## Chiến lược sản phẩm

### Vấn đề cần giải quyết
- Đa số browser bên thứ ba trên Android nặng nề, có cloud sync, bắt đăng ký tài khoản
- DuckDuckGo Browser tốt nhưng nhiều tính năng phức tạp (App Tracking Protection, Email Protection, Fireproof) — không dễ tự build
- Người dùng cần một trình duyệt nhẹ, riêng tư, offline-first, chỉ làm browser cơ bản tốt

### Core Differentiators
1. **Tối giản, không tài khoản** — không sync, không cloud, không tracking
2. **Native Compose UI** — Material3, dynamic color, dark/light/system
3. **Đa ngôn ngữ ngay từ đầu** — 8 locale (VN/EN/DE/RU/KR/JA/ZH/FR)
4. **16KB page size ready** — tương thích Android 15+/16+ devices

### Positioning statement
*"Trình duyệt Android tối giản, không tài khoản, không sync, không tracking — chỉ là browser tốt."*

---

## Tech Stack

### Core
- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **Compile SDK**: 36 (release with minorApiLevel 1)
- **Java**: 11
- **Architecture**: Clean Architecture + MVVM
- **State Management**: ViewModel + StateFlow / SharedFlow
- **DI**: Hilt
- **Navigation**: Navigation Compose
- **Theming**: Material3 ColorScheme + dynamic color (Android 12+) + light/dark/system
- **Persistence**: Room (bookmarks, history, tabs) + DataStore Preferences (settings)
- **Async**: Coroutines + Flow
- **WebView**: `android.webkit.WebView` bọc trong `AndroidView` Compose
- **Download**: Android `DownloadManager`
- **Splash**: `androidx.core:core-splashscreen`
- **Test**: JUnit + Compose UI Test + Espresso

### Build types
- `debug` — development
- `release` — production
- **KHÔNG dùng build flavors** (dev/staging/prod) — quyết định gọn

### Locales hỗ trợ
EN (default), VI, DE, RU, KO, JA, ZH, FR — total 8 locales

---

## ⚠️ Ràng buộc 16KB page size (CRITICAL)

Android 15 (API 35) đã giới thiệu hỗ trợ 16KB memory page size. Android 16 (API 36) làm cho thiết bị 16KB phổ biến hơn. App có native library (`.so`) align 4KB sẽ **KHÔNG load được** trên thiết bị 16KB → app crash khi mở.

### Bắt buộc khi thêm bất kỳ thư viện nào:

1. **Verify library hỗ trợ 16KB page size** trước khi thêm vào `libs.versions.toml`
2. **NDK version**: dùng NDK r27+ (build với 16KB alignment mặc định)
3. **AGP version**: tối thiểu AGP 8.5+ (recommend AGP 8.7+)
4. **Gradle property**:
   ```properties
   android.bundle.enableUncompressedNativeLibs=true
   ```
5. **Check `.so` alignment** sau build:
   ```bash
   unzip -p app-release.apk lib/arm64-v8a/lib*.so | \
     objdump -p - | grep LOAD | awk '{print $NF}'
   # Tất cả phải là 0x4000 (16KB) hoặc lớn hơn
   ```
6. **Thư viện chỉ Kotlin/Java** (không có `.so`) → không bị ảnh hưởng — vẫn cần check transitive deps
7. **Thư viện có `.so` cần verify**: WebView (system, OK), Room (Kotlin only, OK), bất kỳ thư viện ảnh, video, native crypto, Lottie, ML Kit, Tensorflow Lite, v.v.

### Quy trình mỗi khi thêm package mới:
1. Lookup phiên bản mới nhất trên `central.sonatype.com` hoặc Maven repo
2. Đọc release notes / GitHub README để xác nhận hỗ trợ 16KB
3. Nếu thư viện có `.so` mà chưa rõ → ưu tiên version release sau 2024-Q4 (khi Google bắt đầu push 16KB mạnh)
4. Sau khi build local → verify `.so` alignment bằng `objdump`
5. Document version + 16KB-verified date trong `project-context.md`

### Reference
- https://developer.android.com/guide/practices/page-sizes
- https://android-developers.googleblog.com/2024/08/adding-16-kb-page-size-to-android.html

---

## Architecture

### Pattern: Clean Architecture
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
│   │   ├── database/        # Room AppDatabase, Daos
│   │   ├── entity/          # BookmarkEntity, HistoryEntity, TabEntity
│   │   └── datastore/       # SettingsDataStore (DataStore Preferences)
│   ├── repository/          # *RepositoryImpl
│   └── mapper/              # Entity ↔ Domain model
├── domain/
│   ├── model/               # Bookmark, HistoryEntry, Tab (pure Kotlin)
│   ├── repository/          # *Repository interfaces
│   └── usecase/             # GetBookmarksUseCase, AddHistoryUseCase, ...
├── presentation/
│   ├── theme/               # Color, Typography, Shape, AppTheme
│   ├── navigation/          # AppNavGraph, AppDestination sealed class
│   ├── browser/             # BrowserScreen, BrowserViewModel, components
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

### Implementation Sequence (bắt buộc)
1. Project init + Gradle Kotlin DSL + version catalog + buildTypes (Spec 001)
2. Clean Architecture skeleton + Hilt + base classes (Spec 002)
3. Theme + Typography + Dark/Light/System (Spec 003)
4. Localization 8 locales (Spec 004)
5. Room schema + DataStore (Specs 005, 006)
6. WebView wrapper + nav controls + omnibox + search (Specs 007–010)
7. Tabs + incognito (Specs 011, 012)
8. Bookmarks + History + Downloads (Specs 013–015)
9. Settings + Splash + Onboarding (Specs 016–018)
10. (Optional) Tracker blocker (Spec 019)

---

## Theme & Design System

### Color
- Material3 ColorScheme — `lightColorScheme()` / `darkColorScheme()`
- Dynamic color (Android 12+) — `dynamicLightColorScheme(LocalContext.current)`
- Theme mode: Light / Dark / System (mặc định System)
- Không hardcode `Color(0xFF...)` trong UI code

### Typography
- Material3 default Typography (chưa custom font)
- Có thể bổ sung custom font sau qua bundled `assets/fonts/`
- Nếu thêm font → bundle local, KHÔNG fetch runtime

### Shape
- Material3 default Shapes

### Iconography
- Material Icons Extended (`androidx.compose.material:material-icons-extended`)

---

## Storage

### Room Database
- File: `thirtysix_browser.db`
- Tables (planned, có thể đổi khi vào Spec 005):
  - `bookmarks` — `id`, `title`, `url`, `parent_folder_id?`, `created_at`, `sort_order`
  - `bookmark_folders` — `id`, `name`, `parent_id?`, `created_at`
  - `history` — `id`, `url`, `title`, `visited_at`, `favicon_url?`
  - `tabs` — `id`, `url`, `title`, `is_incognito`, `position`, `created_at`, `last_active_at`

### DataStore Preferences
- File: `thirtysix_settings`
- Keys (planned):
  - `theme_mode` — Light/Dark/System
  - `language_code` — locale code
  - `search_engine` — Google/DuckDuckGo/Bing (default Google)
  - `default_home_url`
  - `is_onboarding_completed`
  - `tracker_blocker_enabled` (nếu làm Spec 019)

### Không dùng
- ❌ SharedPreferences (legacy)
- ❌ Cloud sync
- ❌ Account / login
- ❌ Analytics SDK
- ❌ Crash reporting tự động (cân nhắc opt-in sau)

---

## Performance Targets

- App cold start (splash → ready): ≤ 1.5s trên Pixel 5+
- WebView page load: phụ thuộc network, không phải metric chính
- UI: 60fps animation + scroll trên Pixel 5+
- Tab switcher: ≤ 200ms để hiển thị grid preview (10 tabs)
- App APK size: ≤ 15MB (debug), ≤ 10MB (release với R8)
- AAB upload size: ≤ 8MB

---

## Packages dự kiến (sẽ verify version + 16KB tại thời điểm thêm)

### Version Catalog (`gradle/libs.versions.toml`)

| Package | Mục đích | 16KB safe? |
|---------|----------|-----------|
| `androidx.core:core-ktx` | Kotlin extensions | ✅ |
| `androidx.lifecycle:lifecycle-runtime-ktx` | Lifecycle | ✅ |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | ViewModel + Compose | ✅ |
| `androidx.activity:activity-compose` | Activity + Compose | ✅ |
| `androidx.compose:compose-bom` | Compose BOM | ✅ |
| `androidx.compose.ui:ui` | Compose UI | ✅ |
| `androidx.compose.material3:material3` | Material 3 | ✅ |
| `androidx.compose.material:material-icons-extended` | Material Icons | ✅ |
| `androidx.navigation:navigation-compose` | Navigation | ✅ |
| `com.google.dagger:hilt-android` | DI | ✅ (Kotlin only) |
| `androidx.hilt:hilt-navigation-compose` | Hilt + Nav | ✅ |
| `androidx.room:room-runtime` | Room | ✅ |
| `androidx.room:room-ktx` | Room + Coroutines | ✅ |
| `androidx.room:room-compiler` | KSP | ✅ |
| `androidx.datastore:datastore-preferences` | DataStore | ✅ |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | Coroutines | ✅ |
| `androidx.webkit:webkit` | WebView extensions | ✅ |
| `androidx.core:core-splashscreen` | Splash API | ✅ |
| `androidx.appcompat:appcompat` | Locale switching | ✅ |
| `coil-kt:coil-compose` | Favicon loading (TBD) | ⚠️ verify khi thêm |

> Mọi version cụ thể sẽ được lookup tại thời điểm thêm. Không hardcode version từ trí nhớ.

---

## Key Decisions Log

### 2026-05-01 — Spec 001 hoàn tất

**Versions chốt** (verified `central.sonatype.com` + `developer.android.com` 2026-04-30, refined 2026-05-01):

- **AGP 9.1.1** — pinned vào 9.1.x range vì Android Studio chưa support 9.2.0. Re-evaluate khi IDE update.
- **Kotlin 2.3.21** — bundle Compose Compiler plugin (matches version).
- **Gradle wrapper 9.5.0** — `distributionUrl=gradle-9.5.0-bin.zip` (note: lookup phải dùng path `9.5.0` không phải `9.5`).
- **Compose BOM 2026.04.01** — resolves Compose UI 1.11.0 + Material3 1.4.0; KHÔNG pin individual Compose versions.
- **Detekt 1.23.8 / ktlint plugin 14.2.0** — Detekt CV-05 risk (built against Kotlin 2.0.21) chưa surface vấn đề thực tế trong template code.

**JDK requirements**:

- Launcher: **JDK 17+** (AGP 9.x daemon requirement).
- Compile target: JDK 11 via Gradle Toolchain (auto-provision).
- Local dev: dùng `JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home` (JBR JDK 21) hoặc bất kỳ JDK ≥ 17 nào.

**Signing**:

- Constitution v1.2.0 §XI two-scope rule: distribution = release keystore (chưa có), dev = debug keystore fallback với warning mandatory.
- Implementation: `app/build.gradle.kts` đọc `KEYSTORE_PATH/PASSWORD/ALIAS/KEY_PASSWORD` từ `local.properties` hoặc env vars; nếu thiếu → fallback `signingConfigs.debug` + `println` warning.

**Static analysis disable list** (documented):

- Lint: `OldTargetApi`, `GradleDependency`, `AndroidGradlePluginVersion` — AGP 9.x DSL false positives.
- ktlint: `function-naming` (Compose PascalCase) via `@file:Suppress` per file + `.editorconfig` safety net.
- Detekt baseline: `app/detekt-baseline.xml` accepts 9 template-only violations (3 FunctionNaming + 6 MagicNumber on theme colors). Spec 003 sẽ rewrite những file này, baseline sẽ được clear.

**16KB readiness verified**:

- Compose ships native lib `libandroidx.graphics.path.so` cho 4 ABIs (arm64-v8a, armeabi-v7a, x86, x86_64) — TẤT CẢ aligned 0x4000 (16KB) ✅.
- Gradle property `android.bundle.enableUncompressedNativeLibs` đã removed (deprecated in AGP 8.1+, default true).
- CI script `.specify/scripts/bash/verify-16kb-alignment.sh` đã active strict mode (không phải fail-soft skeleton nữa).

**CI structure** (6 jobs):

- build (assembleDebug)
- unit-test (testDebugUnitTest)
- lint (lintDebug)
- static-analysis (detekt + ktlintCheck)
- verify-16kb (assembleRelease + 16KB script)
- instrumented-test (`if: false` đến Spec 007/011)

**Source migration**: `app/src/{main,test,androidTest}/java/` → `kotlin/` qua `git mv` (preserve history). Theme name `ThirdtySixBrowserTheme` (typo từ template) tạm giữ — sẽ rename ở Spec 003 khi rewrite theme.

---

## App Store Strategy

### Positioning
- **Category**: Tools / Productivity (Google Play)
- **Content Rating**: Everyone
- **Privacy**: No analytics, no crash reporting by default, zero data transmitted to ThirtySix servers
- **Permissions tối thiểu**: Internet, ACCESS_NETWORK_STATE (cho download), READ/WRITE_EXTERNAL_STORAGE chỉ khi cần (Scoped Storage cho Android 11+)

### Permissions cần
| Permission | Khi |
|------------|-----|
| `android.permission.INTERNET` | v1 (bắt buộc cho WebView) |
| `android.permission.ACCESS_NETWORK_STATE` | v1 (offline detection) |
| `android.permission.POST_NOTIFICATIONS` | v1 (download progress, Android 13+) |

---

## CI/CD

GitHub Actions hiện đã có sẵn workflow `.github/workflows/`. Chạy cho mỗi PR:

- `./gradlew assembleDebug`
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew connectedDebugAndroidTest` (instrumented, optional)

Sẽ bổ sung step verify 16KB alignment cho `.so` files (nếu có) ở Spec 001.
