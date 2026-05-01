# Quickstart — Spec 002 Verification

> Cách verify Spec 002 (`clean-architecture-skeleton-di`) đã hoàn thành đúng. Chạy mọi step dưới đây trên branch `002-clean-architecture-skeleton-di` sau khi implementation merged into branch.

## Prerequisites

- macOS / Linux với JDK 17+ launcher (AGP 9.x requirement)
- Android emulator API 36 đã setup (cold-start measurement) hoặc device thật
- Repo đã clone và checkout branch `002-clean-architecture-skeleton-di`

## Step 1 — Day-1 Hilt smoke test (BLOCKING)

Per research.md Risk #1, verify Hilt + KSP + Kotlin 2.3.21 không có metadata mismatch:

```bash
./gradlew :app:kspDebugKotlin --info 2>&1 | tee /tmp/hilt-smoke.log
grep -E "metadata version|kotlinx.metadata" /tmp/hilt-smoke.log || echo "✅ no metadata errors"
```

**Pass**: output có `✅ no metadata errors` và lệnh exit code 0.
**Fail**: nếu có "Class metadata version mismatch" hoặc lỗi reference `kotlinx.metadata` → không tiếp tục, áp fallback ladder trong research.md (kapt → Kotlin 2.2 downgrade → hold spec).

## Step 2 — Verify correct Hilt artifact ID (Risk #4)

```bash
./gradlew :app:dependencies --configuration kspDebugKotlin | grep -E "hilt-compiler|hilt-android-compiler"
```

**Pass**: output có dòng `\--- com.google.dagger:hilt-compiler:2.59.2` (KHÔNG phải `hilt-android-compiler`).
**Fail**: nếu thấy `hilt-android-compiler` → fix `app/build.gradle.kts`: `ksp(libs.hilt.compiler)` (artifact ID trong `libs.versions.toml` phải là `hilt-compiler`, không phải `hilt-android-compiler`).

## Step 3 — Verify lifecycle-viewmodel-compose resolved version (Risk #3)

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep "lifecycle-viewmodel-compose"
```

**Pass**: dòng đầu tiên (resolved) là `lifecycle-viewmodel-compose:2.10.0`. Nếu version khác → conflict; thêm `resolutionStrategy.eachDependency` trong `app/build.gradle.kts`.

## Step 4 — Build all configurations

```bash
./gradlew clean
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug detekt ktlintCheck
```

**Pass**: tất cả 6 task `BUILD SUCCESSFUL`. Lint zero warnings; Detekt zero violations; ktlint zero violations.

## Step 5 — 16KB alignment verification (Constitution §IX)

```bash
bash .specify/scripts/bash/verify-16kb-alignment.sh
```

**Pass**: script in ra mọi `.so` align ≥ `0x4000`. Expected: chỉ có `libandroidx.graphics.path.so` (4 ABIs từ Compose, đã verified ở Spec 001 — Spec 002 không thêm `.so` mới).

## Step 6 — App khởi động trên emulator (Acceptance Scenarios US1.1)

```bash
./gradlew :app:installDebug
adb shell am start -n com.raumanian.thirtysix.browser/.MainActivity
```

**Pass**: app mở trên emulator, hiển thị text "Browser" (label `R.string.browser_screen_placeholder`) ở giữa màn hình. Không crash. Không có Hilt error trong logcat:
```bash
adb logcat -d | grep -iE "hilt|injection|crash" | head -20
```

## Step 7 — Cold start time (SC-001)

Reset app, đo cold start:

```bash
adb shell am force-stop com.raumanian.thirtysix.browser
adb shell am start -W -n com.raumanian.thirtysix.browser/.MainActivity | grep -E "TotalTime|WaitTime"
```

**Pass**: `TotalTime` ≤ 1500ms trên emulator API 36 (Pixel 5 profile recommended). Lặp 3 lần lấy median.

## Step 8 — Manual navigation through all 7 destinations (SC-002, US1.3)

Vì Spec 002 chưa có UI để trigger navigation (chỉ Browser placeholder hiển thị), verify bằng instrumented test tạm hoặc dùng `adb` để gọi NavController:

**Option A — instrumented test (recommended)**: thêm `app/src/androidTest/.../NavGraphTest.kt`:
```kotlin
@HiltAndroidTest
class NavGraphTest {
    // ... uses createComposeRule() + navController.navigate(AppDestination.<X>.route)
    // for X in [Browser, Tabs, Bookmarks, History, Downloads, Settings, Onboarding]
    // assert each Screen's placeholder text is displayed
}
```

> **Note**: Spec 002 KHÔNG re-enable instrumented-test job trong CI (vẫn `if: false`). Test này chạy local trước khi merge để verify navigation, KHÔNG phải gate CI. Spec 007/011 sẽ re-enable CI job.

**Option B — manual via adb (lighter)**: KHÔNG có deep link cho 7 routes ở Spec 002 (Spec sau có thể thêm). Skip option B; rely on Option A.

**Pass**: 7/7 Screen render đúng placeholder text khi navigate; không có "destination not found".

## Step 9 — Unit tests cover utilities (SC-005, FR-020)

```bash
./gradlew :app:testDebugUnitTest --info | grep -E "ResultTest|BaseViewModelTest"
```

**Pass**: ít nhất các test sau xanh:
- `ResultTest.map_success_appliesTransform`
- `ResultTest.map_error_preservesError`
- `BaseViewModelTest.launchSafely_ioException_mapsToNetworkError`
- `BaseViewModelTest.launchSafely_sqliteException_mapsToDatabaseError`
- `BaseViewModelTest.launchSafely_genericException_mapsToUnknown`
- `BaseViewModelTest.launchSafely_cancellation_isRethrown`

Coverage cho 2 utility files ≥ 80%:
```bash
./gradlew :app:testDebugUnitTest jacocoDebugTestReport  # nếu jacoco đã setup
# hoặc đọc coverage từ IDE
```

## Step 10 — APK size delta (SC-007)

```bash
ls -la app/build/outputs/apk/release/app-release.apk
```

So với Spec 001 baseline (note size từ commit Spec 001 release APK).

**Pass**: delta ≤ 1MB. Nếu lớn hơn → có thể do một transitive dep nặng; investigate `./gradlew :app:dependencies` size report.

## Step 11 — CI run (SC-003)

Push branch lên GitHub:
```bash
git push -u origin 002-clean-architecture-skeleton-di
```

Đợi CI run trên PR. Pass = 5 job xanh + 1 skipped:

| Job | Expected |
|---|---|
| build (assembleDebug) | ✅ green |
| unit-test (testDebugUnitTest) | ✅ green |
| lint (lintDebug) | ✅ green |
| static-analysis (detekt + ktlintCheck) | ✅ green |
| verify-16kb (assembleRelease + script) | ✅ green |
| instrumented-test | ⏭ skipped (`if: false`) |

## Done criteria

✅ Tất cả 11 step trên pass → Spec 002 ready for merge to `main`.
❌ Bất kỳ step nào fail → fix root cause; không bypass quality gates.

## After merge — update tracking docs

1. Update [CLAUDE.md](../../CLAUDE.md) Recent Changes section với entry `2026-XX-XX: ✅ Spec 002 done — ...`
2. Update [.claude/claude-app/sdd-roadmap.md](../../.claude/claude-app/sdd-roadmap.md) Spec 002 row → ✅ Done
3. Update [.claude/claude-app/project-context.md](../../.claude/claude-app/project-context.md) Key Decisions Log với version pins, fallback ladder kết quả thực tế (KSP work hay phải fallback kapt?)
4. Speckit auto-updates `<!-- SPECKIT START / END -->` markers trong CLAUDE.md để point sang spec kế tiếp khi `/speckit.plan` cho Spec 003 chạy.
