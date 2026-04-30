# Feature Specification: Project Init & Build Config

**Feature Branch**: `001-project-init-build-config`
**Created**: 2026-04-30
**Status**: Draft
**Input**: User description: "Spec 001 — project-init-build-config: Thiết lập build foundation cho ThirtySixBrowser theo Constitution v1.1.0. Gradle Kotlin DSL + version catalog + build types + static analysis + CI 16KB-ready."

## Clarifications

### Session 2026-04-30

- Q: Cấu trúc source directory cuối Spec 001 — migrate template từ `java/` sang `kotlin/` hay giữ nguyên? → A: Migrate toàn bộ template (`MainActivity.kt` + `ui/theme/*`) từ `app/src/main/java/` sang `app/src/main/kotlin/`; xóa `java/` directory rỗng; canonical source set là `kotlin/` ngay từ Spec 001.
- Q: Khi keystore vắng mặt, `assembleRelease` output APK ở chế độ nào? → A: Debug-signed fallback — dùng debug keystore (`~/.android/debug.keystore`) để ký release APK khi không có release key; build log MUST in warning rõ ràng "release built with DEBUG signature — NOT for distribution". Khi user provide release keystore qua local.properties/env vars, signing config tự switch sang release key. **Note**: This answer triggered Constitution amendment v1.1.0 → v1.2.0 (§XI signing rule scoped to "distribution" builds; debug-fallback explicitly permitted for local dev/CI iteration).
- Q: Cách enforce Java 11 ở build level? → A: Gradle Java Toolchain (`kotlin { jvmToolchain(11) }` + `java { toolchain { languageVersion = JavaLanguageVersion.of(11) } }`). Gradle auto-provision JDK 11 nếu máy local/CI không có; CI workflow KHÔNG cần `actions/setup-java` step để pin JDK build target (vẫn cần một JDK khởi động Gradle, dùng default runner JDK).
- Q: Lint policy cho debug build (CI runs lintDebug)? → A: `warningsAsErrors = true` + `abortOnError = true` cho **mọi** build type (debug + release). CI bắt lint warning ngay từ `lintDebug`. Không có lint baseline ở Spec 001 — nếu code template gốc có warning thì spec MUST fix ngay; nếu là Detekt MagicNumber thì baseline xml được phép (FR-020), nhưng Lint warnings phải zero.
- Q: Detekt MagicNumber rule áp dụng phạm vi nào? → A: Apply MagicNumber chỉ cho production code (`src/main/**`); exclude `src/test/**` và `src/androidTest/**`. Test code được phép dùng số literal làm test data/fixture mà không bị flag. Cấu hình qua `detekt.yml` `MagicNumber.excludes` hoặc `excludesPattern`.

## User Scenarios & Testing *(mandatory)*

> **Lưu ý ngữ cảnh**: Spec 001 là spec nền móng — "user" trong spec này là **developer** (Xbism3 + future contributors + CI runner). Sản phẩm cuối là **trải nghiệm build/lint/test ổn định, đồng nhất, sẵn sàng cho mọi spec sau**. Không có UI người dùng cuối thay đổi.

### User Story 1 - Build & Run app trên thiết bị với câu lệnh tối thiểu (Priority: P1)

Developer (hoặc CI) clone repo về, chạy đúng một lệnh `./gradlew assembleDebug` và nhận được APK debug install được, không cần config thêm bất cứ gì (không cần edit file `build.gradle.kts`, không cần khai báo version thủ công, không cần đặt biến môi trường). Tất cả version (Kotlin, AGP, Compose, các thư viện) được khai báo tập trung tại một nơi duy nhất — bất kỳ ai mở repo cũng nhìn thấy "single source of truth".

**Why this priority**: Đây là yêu cầu sống còn — nếu build không pass, không có gì sau đó hoạt động được. Mọi spec từ 002 trở đi phụ thuộc vào việc build foundation này hoạt động.

**Independent Test**: Có thể test độc lập bằng cách clone repo lên máy mới, chạy `./gradlew clean assembleDebug` và xác nhận build thành công, sau đó `grep` toàn bộ file `*.gradle.kts` để xác nhận không còn version literal nào (mọi version reference đều thông qua catalog).

**Acceptance Scenarios**:

1. **Given** một máy mới có Android SDK + JDK ≥ 17 (làm Gradle launcher; AGP 9.x yêu cầu), **When** developer chạy `./gradlew clean assembleDebug`, **Then** Gradle Toolchain auto-provision JDK 11 cho compile target, build thành công và sinh ra APK debug install được trên thiết bị Android API 24+.
2. **Given** developer mở `app/build.gradle.kts` và `build.gradle.kts` ở project root, **When** tìm bất kỳ version literal nào (số phiên bản dạng "1.2.3"), **Then** không tìm thấy — mọi version đều resolve qua version catalog.
3. **Given** version catalog là file duy nhất chứa version, **When** developer cần upgrade một thư viện, **Then** chỉ cần sửa một dòng tại đúng một file (`gradle/libs.versions.toml`).

---

### User Story 2 - Static analysis + lint chạy được với một lệnh, không cần fix code template (Priority: P1)

Developer chạy được toàn bộ chuỗi static analysis (`lintDebug`, `detekt`, `ktlintCheck`) trên codebase mới (template Android Studio chưa custom). Cả ba task phải execute thành công ngay từ lần đầu — không bị block bởi các violation từ code template gốc. Nếu có violation tồn tại, hệ thống phải có cơ chế baseline để chấp nhận state hiện tại nhưng vẫn enforce strict cho code mới sau này.

**Why this priority**: Constitution §III yêu cầu zero warnings/violations. Nếu lint/detekt/ktlint không sẵn sàng từ Spec 001, mọi spec sau sẽ tích lũy nợ kỹ thuật và việc enforce No-Hardcode Rule trở nên khó.

**Independent Test**: Chạy ba lệnh `./gradlew lintDebug detekt ktlintCheck` trên máy sạch và xác nhận cả ba đều exit code 0.

**Acceptance Scenarios**:

1. **Given** project mới với code template Android Studio, **When** developer chạy `./gradlew lintDebug`, **Then** task hoàn thành với exit code 0.
2. **Given** Detekt + ktlint plugin đã wire, **When** developer chạy `./gradlew detekt` và `./gradlew ktlintCheck`, **Then** cả hai đều exit code 0 (chấp nhận baseline xml nếu cần để không block code template gốc).
3. **Given** code mới được thêm vi phạm rule (ví dụ magic number, hardcoded color), **When** lint/detekt chạy, **Then** task fail với violation được report rõ ràng — baseline KHÔNG che giấu violation từ code mới.

---

### User Story 3 - Release build pass dù chưa có signing key (Priority: P1)

Developer chạy `./gradlew assembleRelease` trên máy chưa có release keystore. Build phải hoàn thành thành công (sinh ra APK unsigned hoặc signed bằng debug key fallback) với cảnh báo rõ ràng về việc thiếu signing config — tuyệt đối không fail build. Khi developer đặt keystore + credentials vào đúng vị trí (qua `local.properties` hoặc env vars), build release sẽ tự động ký APK đó mà không cần sửa script.

**Why this priority**: User chưa có keystore và muốn không bị block bởi việc setup signing. Đồng thời việc verify release build chạy được là điều kiện tối thiểu để biết R8/minify/proguard hoạt động đúng từ Spec 001.

**Independent Test**: Trên máy không có keystore, chạy `./gradlew assembleRelease` và xác nhận: (a) exit code 0, (b) có warning về signing được in ra, (c) APK output tồn tại tại `app/build/outputs/apk/release/`.

**Acceptance Scenarios**:

1. **Given** không có release keystore, không có entry trong `local.properties`, không có env vars signing, **When** developer chạy `./gradlew assembleRelease`, **Then** build thành công + APK được ký bằng debug keystore (fallback) + warning `"⚠️ release built with DEBUG signature — NOT for distribution"` được in ra + APK install được trên device.
2. **Given** developer đã đặt release keystore + credentials vào đúng vị trí (local.properties hoặc env vars), **When** chạy `./gradlew assembleRelease`, **Then** APK output được ký bằng release key (KHÔNG fallback debug) — không cần sửa file build script.
3. **Given** R8/minify được enable cho release, **When** build release chạy, **Then** không bị crash do thiếu proguard rules cho thư viện baseline.

---

### User Story 4 - CI chạy đủ pipeline trên mọi PR, sẵn sàng enforce 16KB (Priority: P2)

Mọi pull request được mở trên GitHub đều tự động chạy: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `detekt`, `ktlintCheck`. Pipeline phải fail nếu bất kỳ task nào fail. Riêng việc verify 16KB page-size alignment cho `.so`: pipeline có sẵn step thực thi script kiểm tra; nếu chưa có `.so` (Spec 001 chưa thêm thư viện native nào), step phải pass-through (skip với log "no native libs found"); khi spec sau thêm thư viện có `.so` thì step tự động enforce strict mà không cần sửa workflow.

**Why this priority**: CI là cơ chế bảo vệ Constitution. Nếu CI không bắt được vi phạm sớm, các spec sau sẽ phải fix retroactively. Tuy nhiên CI có thể được tinh chỉnh dần — quan trọng là Spec 001 wire đầy đủ skeleton.

**Independent Test**: Mở một PR nhỏ (ví dụ sửa README), xác nhận GitHub Actions chạy đúng 5 task (build/test/lint/detekt/ktlint) + 1 step verify 16KB; tất cả đều pass; thời gian chạy hợp lý (<10 phút trên runner mặc định).

**Acceptance Scenarios**:

1. **Given** một PR mới được mở, **When** CI workflow trigger, **Then** đủ 5 Gradle task chạy + 1 step verify 16KB chạy + tất cả pass.
2. **Given** PR vi phạm lint/detekt/ktlint rule, **When** CI chạy, **Then** workflow fail với log lỗi rõ ràng tại chỗ vi phạm.
3. **Given** Spec 001 chưa có `.so`, **When** step verify 16KB chạy, **Then** step exit 0 với message "no native libraries to verify (skip)".
4. **Given** spec tương lai thêm thư viện có `.so`, **When** CI build và step 16KB chạy, **Then** step tự động kiểm tra alignment và fail nếu không phải 0x4000+; không cần edit workflow.

---

### User Story 5 - Constants namespace skeleton sẵn sàng cho spec sau extend (Priority: P3)

Khi spec sau (002+) cần định nghĩa hằng số (URL templates, storage keys, browser limits, v.v.), namespace `core/constants/` đã tồn tại với một file stub (`AppConstants.kt`) — developer chỉ cần thêm file mới vào package này. Không cần Spec 001 tạo đủ 13 file constants — chỉ cần tạo namespace để các spec sau theo đúng cấu trúc.

**Why this priority**: Đây là affordance kiến trúc nhỏ; không tạo cũng không block Spec 002, nhưng giúp Spec 002+ không phải invent lại structure. P3 vì có thể trì hoãn nếu cần.

**Independent Test**: Mở Android Studio sau Spec 001, navigate tới `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/AppConstants.kt` — file tồn tại, build pass khi có file này.

**Acceptance Scenarios**:

1. **Given** Spec 001 hoàn tất, **When** developer mở repo, **Then** thư mục `core/constants/` tồn tại với ít nhất một file Kotlin (`AppConstants.kt`) chứa package declaration đúng + một stub object/class (có thể empty).
2. **Given** file stub tồn tại, **When** chạy `./gradlew assembleDebug` và `./gradlew detekt`, **Then** cả hai pass — file stub không gây lỗi build hay lint violation.

---

### Edge Cases

- **Build máy không có `local.properties`**: build script không được crash khi đọc file không tồn tại — phải fallback sang env vars hoặc skip signing config.
- **Detekt baseline có sẵn violation từ code template Android Studio**: nếu phát hiện violation từ template gốc, baseline file (`detekt-baseline.xml`) phải được generate và commit cùng spec để CI không block; nhưng baseline không được rộng đến mức nuốt violation tương lai.
- **Compose Compiler plugin mismatch với Kotlin version**: Kotlin 2.x dùng plugin `org.jetbrains.kotlin.plugin.compose` riêng (không còn dùng `kotlinCompilerExtensionVersion`). Phải xác nhận plugin version match Kotlin version để Compose compile được.
- **CI runner không có Android SDK preinstalled**: workflow phải dùng action chính thức (ví dụ `android-actions/setup-android@v3`) để cài đặt SDK; không giả định runner có sẵn.
- **`assembleRelease` với R8 minify enabled trên code template trắng**: cần proguard rules tối thiểu để tránh strip nhầm Compose / Activity. Spec phải confirm proguard-rules.pro mặc định đủ dùng cho baseline; nếu không, document workaround.
- **Step verify 16KB chạy khi APK release build chưa được tạo**: script phải kiểm tra APK tồn tại trước khi unzip; nếu không có APK → skip với warning, không fail.
- **Developer dùng JDK launcher ≠ 17+**: Gradle daemon (AGP 9.x) yêu cầu JDK 17 trở lên để khởi động. Nếu launcher < 17 → Gradle in error message rõ ràng (out-of-scope của spec để tự cài JDK 17). Compile target JDK 11 vẫn được Toolchain auto-provision độc lập với launcher.

## Requirements *(mandatory)*

### Functional Requirements

#### Build Configuration

- **FR-001**: Hệ thống MUST có một file version catalog duy nhất (`gradle/libs.versions.toml`) chứa toàn bộ version, library, plugin, bundle declaration cho project.
- **FR-002**: Hệ thống MUST đảm bảo không có version literal (số phiên bản hardcoded) tồn tại trong bất kỳ file `*.gradle.kts` nào — mọi reference phải qua catalog. **Documented exception**: settings-level plugins inside `settings.gradle.kts plugins {}` block (e.g., `foojay-resolver-convention`) are resolved BEFORE the version catalog loads, so an inline version literal is the only working option. Each such literal MUST be accompanied by a comment explaining the exception.
- **FR-003**: Hệ thống MUST organize version catalog theo bốn section: `[versions]`, `[libraries]`, `[plugins]`, `[bundles]`.
- **FR-004**: Hệ thống MUST cung cấp một bundle `compose` gom các Compose UI dependencies (ui, ui-graphics, ui-tooling-preview, material3) để các module dependency block ngắn gọn.
- **FR-005**: Hệ thống MUST set `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`, `applicationId = "com.raumanian.thirtysix.browser"`, `namespace = "com.raumanian.thirtysix.browser"`.
- **FR-006**: Hệ thống MUST set Java source/target compatibility = 11 và Kotlin `jvmTarget = "11"` thông qua **Gradle Java Toolchain** (`kotlin { jvmToolchain(11) }` + `java { toolchain { languageVersion = JavaLanguageVersion.of(11) } }`); Gradle MUST auto-provision JDK 11 nếu môi trường thiếu, không phụ thuộc system JDK của developer.
- **FR-007**: Hệ thống MUST dùng Android Gradle Plugin (AGP) phiên bản ≥ 8.5 (yêu cầu của 16KB readiness — Constitution §IX).
- **FR-008**: Hệ thống MUST dùng Kotlin phiên bản stable mới nhất tại thời điểm implement (lookup từ kotlinlang.org tại implementation time, không hardcode từ trí nhớ — Constitution §IX).
- **FR-009**: Hệ thống MUST wire Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose`) khớp với Kotlin version đã chọn (Kotlin 2.0+).

#### Build Types

- **FR-010**: Hệ thống MUST cấu hình duy nhất hai build type: `debug` và `release`. KHÔNG có build flavors.
- **FR-011**: Build type `debug` MUST set `applicationIdSuffix = ".debug"`, `debuggable = true`, `minifyEnabled = false`.
- **FR-012**: Build type `release` MUST set `minifyEnabled = true`, `shrinkResources = true`, dùng `proguard-android-optimize.txt` (default) + project-specific `proguard-rules.pro`.
- **FR-013**: Hệ thống MUST khai báo signing config placeholder cho release theo Constitution §XI v1.2.0 (signing-config two-scope rule): đọc keystore path, store password, key alias, key password từ `local.properties` HOẶC env vars (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`); nếu cả hai nguồn đều thiếu, release build MUST fallback sang **debug keystore** (`~/.android/debug.keystore`, alias `androiddebugkey`, default debug password) để ký APK — APK output MUST install được trên device. Build log MUST chứa literal substring `"release built with DEBUG signature — NOT for distribution"` khi fallback active (constitutional requirement). Khi user provide release keystore qua bất kỳ nguồn nào (local.properties hoặc env vars), signing config tự switch sang release key mà không cần sửa build script. Artifacts từ debug-fallback MUST NOT được upload Play Store dưới bất kỳ hoàn cảnh nào.

#### 16KB Page Size Readiness

- **FR-014**: File `gradle.properties` MUST set `android.bundle.enableUncompressedNativeLibs=true`.
- **FR-015**: Documentation (plan.md hoặc README) MUST ghi rõ yêu cầu NDK r27+ cho mọi spec tương lai thêm thư viện native; Spec 001 không bắt buộc khai báo NDK version vì chưa có `.so`.
- **FR-016**: CI workflow MUST có một step verify 16KB alignment chạy fail-soft: nếu APK release không có thư mục `lib/` hoặc không có `.so`, step exit 0 với log "no native libs to verify"; nếu có `.so`, step kiểm tra mọi LOAD segment alignment phải ≥ 0x4000.

#### Static Analysis

- **FR-017**: Hệ thống MUST enable Android Lint với `abortOnError = true` và `warningsAsErrors = true` cho **mọi** build type (debug + release) — không có exception. Nếu code template gốc của Android Studio sinh ra Lint warning, spec MUST fix code template để đạt zero warning; KHÔNG dùng `lint-baseline.xml` cho Lint (khác với Detekt baseline ở FR-020).
- **FR-018**: Hệ thống MUST wire Detekt plugin với config file (`detekt.yml`) và bật rule `MagicNumber` để comply với Constitution §III No-Hardcode Rule. Rule `MagicNumber` MUST:
  - exclude test sources (`**/src/test/**`, `**/src/androidTest/**`) qua `excludes` pattern;
  - preserve Detekt default trivial whitelist `MagicNumber.ignoreNumbers = ['-1', '0', '1', '2']` (Constitution §III trivial whitelist) — không override hoặc shrink whitelist;
  - chỉ enforce cho production code (`src/main/**`).
- **FR-019**: Hệ thống MUST wire ktlint plugin chạy được task `ktlintCheck`.
- **FR-020**: Nếu code template Android Studio gốc gây violation Detekt, hệ thống MUST cung cấp file `detekt-baseline.xml` để chấp nhận state hiện tại; baseline KHÔNG được phép cover violation thêm sau Spec 001.

#### CI Workflow

- **FR-021**: Workflow `.github/workflows/ci.yml` MUST chạy 5 Gradle task tối thiểu trên mọi PR: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `detekt`, `ktlintCheck`.
- **FR-022**: Workflow MUST có job hoặc step verify 16KB alignment — see FR-016 for the canonical fail-soft semantics (no `lib/` → exit 0 with skip log; `.so` present → enforce ≥ 0x4000 alignment).
- **FR-023**: Job `instrumented-test` MUST giữ trạng thái `if: false` (disabled) như hiện tại — sẽ được re-enable ở Spec 007 hoặc 011 khi có UI test thực sự.
- **FR-024**: Workflow KHÔNG cần job release build trong Spec 001 — sẽ thêm khi user có signing key sẵn sàng.
- **FR-025**: CI workflow MUST cài đặt Android SDK API 36 trước khi chạy Gradle task; KHÔNG cần step pin JDK 11 cho build target vì Gradle Toolchain tự provision (FR-006). CI có thể giữ JDK mặc định của runner làm Gradle launcher (JDK 17 hoặc 21 tuỳ runner version).

#### Source Code Skeleton

- **FR-026**: Hệ thống MUST tạo file `app/src/main/kotlin/com/raumanian/thirtysix/browser/core/constants/AppConstants.kt` với package declaration đúng và một stub `object AppConstants` (có thể chỉ chứa một const placeholder hoặc empty body với comment giải thích rằng các constants thực sẽ được điền bởi spec sau).
- **FR-027**: Cấu trúc package `core/constants/` MUST tồn tại như namespace; KHÔNG bắt buộc tạo đủ 13 file constants ở Spec 001 (theo CLAUDE.md project-structure list, các file đó là ownership của các spec liên quan).
- **FR-027a**: Hệ thống MUST migrate toàn bộ Kotlin source files có sẵn từ template (`MainActivity.kt`, `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/theme/Type.kt`, và các file test tương ứng) từ `app/src/main/java/` sang `app/src/main/kotlin/`; tương tự cho `app/src/test/java/` → `app/src/test/kotlin/` và `app/src/androidTest/java/` → `app/src/androidTest/kotlin/`.
- **FR-027b**: Sau migrate, các thư mục `java/` MUST rỗng và được xóa; source set Gradle MUST resolve đúng `kotlin/` (Android Gradle Plugin auto-detect `kotlin/` source set, nhưng spec MUST verify build pass sau migrate).
- **FR-027c**: Migrate KHÔNG được đổi package declaration của bất kỳ file nào; chỉ thay đổi physical location.

#### Documentation Updates (post-implementation)

- **FR-028**: Sau khi spec đã merge, [CLAUDE.md](../../CLAUDE.md) MUST có entry mới ở "Recent Changes" với date 2026-04-30 (hoặc date thực tế merge) ghi nhận: AGP version chốt, Kotlin version chốt, Gradle wrapper version chốt, signing config placeholder ready.
- **FR-029**: Sau khi spec đã merge, [project-context.md](../../.claude/claude-app/project-context.md) "Key Decisions Log" MUST có entry mới với cùng nội dung version chốt.
- **FR-030**: Sau khi spec đã merge, [sdd-roadmap.md](../../.claude/claude-app/sdd-roadmap.md) MUST đánh dấu Spec 001 là ✅ Done.

### Key Entities *(không áp dụng — spec foundation, không có data entity)*

Spec 001 không tạo data model. Bookmark/History/Tab entities sẽ được định nghĩa ở Spec 005.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Developer mới clone repo và chạy `./gradlew clean assembleDebug` thành công trong **dưới 5 phút** trên máy đã có Android SDK + JDK ≥ 17 (không tính thời gian download dependency và JDK 11 Toolchain lần đầu).
- **SC-002**: **Zero version literal** tồn tại trong bất kỳ file `*.gradle.kts` nào sau spec — verify bằng `grep -r '"[0-9]\+\.[0-9]\+\.[0-9]\+"' --include="*.gradle.kts" .` returns at most ONE hit: the documented exception in `settings.gradle.kts` for `foojay-resolver-convention` (per FR-002 exception clause). All other matches MUST be zero.
- **SC-003**: Chạy `./gradlew assembleDebug testDebugUnitTest lintDebug detekt ktlintCheck` trên một máy sạch — **cả 5 task đều pass** với exit code 0.
- **SC-004**: Chạy `./gradlew assembleRelease` trên máy không có release keystore — exit code 0, có warning rõ "release built with DEBUG signature", APK output tồn tại tại `app/build/outputs/apk/release/` và install được trên device qua `adb install`.
- **SC-005**: PR mới mở trên GitHub — workflow CI chạy đủ pipeline (5 Gradle task + 16KB verify step) và hoàn thành trong **dưới 10 phút** trên runner default.
- **SC-006**: Khi developer thêm code mới vi phạm rule (test bằng cách cố ý thêm `val x = 42` không có comment giải thích), `./gradlew detekt` phát hiện và fail — chứng minh baseline không che giấu violation tương lai.
- **SC-007**: 100% các version trong project resolve qua `gradle/libs.versions.toml` — verify thủ công bằng cách đếm số lần file `libs.versions.toml` được reference từ `build.gradle.kts` so với số dependency declared.
- **SC-008**: Sau Spec 001, các spec từ 002 trở lên có thể bắt đầu mà KHÔNG cần thay đổi gì trong build infrastructure baseline (chỉ cần thêm version + library entry mới vào catalog).

## Assumptions

- Developer có sẵn Android SDK API 36 và **JDK ≥ 17** (làm Gradle launcher) trên máy local. Compile target vẫn JDK 11 — Gradle Toolchain tự provision JDK 11 cho compilation, độc lập với launcher JDK. Lý do JDK 17 launcher: AGP 9.x (chốt ở research.md) yêu cầu JDK 17+ để chạy Gradle daemon. CI runner `ubuntu-latest` ship sẵn JDK 17 → CI không cần thay đổi.
- GitHub Actions runner mặc định (`ubuntu-latest`) có đủ tài nguyên để build Android project; không cần self-hosted runner ở Spec 001.
- User chưa có release keystore và sẽ setup riêng — do đó signing config phải graceful về "no key" state. Spec 001 không bao gồm việc tạo keystore.
- Code template Android Studio gốc (đã commit ở `MainActivity.kt`, `ui/theme/*`) có thể có vài Detekt violation (ví dụ MagicNumber trong template); chấp nhận tạo `detekt-baseline.xml` để pass nhưng baseline phải được commit và document rõ.
- 16KB verify CI step ở Spec 001 chỉ là skeleton fail-soft; spec đầu tiên thực sự thêm thư viện native (có thể là Spec 015 nếu DownloadManager kéo theo native deps, hoặc bất kỳ spec nào trước đó) sẽ trigger việc enforce strict.
- AGP/Kotlin/Gradle wrapper version cụ thể sẽ được lookup tại `/speckit.plan` time từ nguồn chính thức (developer.android.com/build, kotlinlang.org, gradle.org/releases) — KHÔNG hardcode từ trí nhớ (Constitution §IX).
- Plan phase sẽ confirm version Compose Compiler plugin tương ứng với Kotlin chọn ở plan time.
- ktlint plugin được chọn là `org.jlleitschuh.gradle.ktlint` (de-facto standard cho Gradle Kotlin DSL); Detekt plugin là `io.gitlab.arturbosch.detekt` (official). Phiên bản cụ thể lookup tại plan time.
- Locale `instrumented-test` job remain disabled là intentional debt được track ở CLAUDE.md "Pending CI / Tooling Tasks" — spec này KHÔNG re-enable.

## Out of Scope (sẽ làm ở spec sau)

- Hilt + KSP wiring → Spec 002
- Room/DataStore/Navigation → Spec 005, 006, 002 (navigation host setup)
- Module structure (`core/data/domain/presentation/di`) skeleton đầy đủ → Spec 002 (Spec 001 chỉ tạo `core/constants/AppConstants.kt`)
- Theme files thực sự (Color/Type/Shape/Spacing) → Spec 003
- Localization 8 locales + LocaleManager → Spec 004
- Splash screen API → Spec 017
- Release build CI job với signing key thật → khi user provide keystore (post-Spec 001)
- 16KB strict enforcement → spec đầu tiên thêm thư viện có `.so`
- Detekt custom rules → có thể thêm về sau khi project lớn hơn; Spec 001 chỉ dùng default + MagicNumber enabled

## Dependencies

- **Constitution v1.2.0** (amended 2026-05-01 from v1.1.0) — đặc biệt §III (No-Hardcode), §IX (16KB), §X (Phase Order), §XI (Build Config — signing two-scope rule introduced by this spec's Q2 clarification)
- **CLAUDE.md** — project structure, naming conventions, commands
- **Project context docs** — meeting-note.md, project-context.md, sdd-roadmap.md, dev-workflow.md
