# Research — Spec 001 Project Init & Build Config

**Branch**: `001-project-init-build-config`
**Date**: 2026-04-30
**Scope**: Resolve all version & compatibility unknowns required before implementing build foundation.

> Per Constitution §IX, all third-party versions MUST be looked up from official sources at the moment of addition. The table below records lookups performed on **2026-04-30** with source URL — no remembered versions.

## Decision Summary

| # | Decision | Choice | Rationale |
|---|----------|--------|-----------|
| 1 | Android Gradle Plugin | **9.1.1** | Pinned to 9.1.1 (NOT 9.2.0 latest) for Android Studio compat — user's Android Studio supports only AGP 9.1.x. Re-evaluate after IDE updates. |
| 2 | Kotlin | **2.3.21** | Latest stable bug-fix on 2.3.20; ships Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose`) at matching version |
| 3 | Gradle wrapper | **9.5.0** | Latest stable; AGP 9.2 requires Gradle ≥ 9.4.1 — picking 9.5 for newest fixes |
| 4 | Compose BOM | **2026.04.01** | Latest stable BOM; resolves Compose UI 1.11.0 + Material3 1.4.0 |
| 5 | androidx.core:core-ktx | **1.18.0** | Latest stable (2026-03-11) |
| 6 | androidx.lifecycle:lifecycle-runtime-ktx | **2.10.0** | Latest stable (2025-11-19) |
| 7 | androidx.activity:activity-compose | **1.13.0** | Latest stable (2026-03-11) |
| 8 | junit (JUnit 4) | **4.13.2** | Latest stable; baseline test runner |
| 9 | androidx.test.ext:junit | **1.3.0** | Latest stable (2025-07-30) |
| 10 | androidx.test.espresso:espresso-core | **3.7.0** | Latest stable (2025-07-30) |
| 11 | Detekt Gradle plugin | **1.23.8** ⚠️ | Latest stable as of lookup; built against Kotlin 2.0.21 — see compat caveat below |
| 12 | ktlint Gradle plugin | **14.2.0** | Latest stable (2026-03-12); supports Kotlin 2.x |
| 13 | NDK | not pinned in Spec 001 | No `.so` deps in baseline; pin in first spec adding native libs (constitution requires r27+) |
| 14 | JDK toolchain (compile) | **JDK 11** | Per FR-006 + Q3 clarification; Gradle Toolchain auto-provisions |
| 15 | JDK launcher (Gradle daemon) | **JDK 17+** | **Discovered constraint**: AGP 9.2 requires JDK 17 to run Gradle daemon (was JDK 11 in earlier AGP 8.x). Updates Q3 assumption. |

## Compatibility Caveats

### CV-01: AGP 9.1 ↔ Gradle minimum

- AGP 9.1.1 (pinned for IDE compat) supports Gradle 9.x. Wrapper at 9.5.0 is compatible.
- AGP 9.2.0 was the latest stable at lookup time but Android Studio (user's IDE) does not yet support it; downgraded to 9.1.1 (latest within IDE-supported 9.1.x range).
- Source: <https://developer.android.com/build/releases/gradle-plugin>

### CV-02: AGP 9.x ↔ JDK launcher = 17+ (deviation from Q3 assumption)

- AGP 9.x (both 9.1 and 9.2) requires **JDK 17 minimum** to run the Gradle daemon (the JVM that launches Gradle). This is stricter than the assumption recorded during clarification (Q3) where we said "any JDK ≥ 11 launcher".
- **Compile target stays at JDK 11** via Gradle Toolchain (`kotlin { jvmToolchain(11) }` + `java { toolchain { languageVersion = JavaLanguageVersion.of(11) } }`) — Toolchain auto-provisions a JDK 11 download for compilation regardless of the launcher JDK.
- **Action**: spec.md Assumption updated to reflect "launcher JDK 17+, compile target JDK 11 via Toolchain".
- Source: <https://developer.android.com/build/releases/gradle-plugin> (AGP 9.x system requirements)

### CV-03: Kotlin 2.3.21 ↔ Compose Compiler plugin

- Since Kotlin 2.0+, Compose Compiler plugin is shipped with Kotlin and **plugin version always matches Kotlin version**.
- Apply `id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"` (or share via version catalog reference). No separate `composeCompilerExtensionVersion` pinning needed.
- Source: <https://kotlinlang.org/docs/releases.html>

### CV-04: AGP 9.1 ↔ compileSdk 36 + minorApiLevel = 1

- AGP 9.1.1 supports compileSdk = 36 with `minorApiLevel = 1` via the new
  `compileSdk { version = release(36) { minorApiLevel = 1 } }` DSL.
- `minSdk = 24` and `targetSdk = 36` both fully supported by AGP 9.1.

### CV-05: Detekt 1.23.8 ↔ Kotlin 2.3.21 (RISK)

- Detekt 1.23.8 was built against Kotlin 2.0.21. Running its bundled K2 analyzer against Kotlin 2.3.21 source code **may** surface edge-case rule mismatches.
- **Mitigation**:
  - Run `./gradlew detekt` against the migrated template code as part of acceptance test.
  - If false positives or crashes appear → either bump to Detekt 2.x (check release status before merge), or pin Detekt-managed Kotlin to 2.0.21 via `detekt { toolVersion = "1.23.8" }` while project uses Kotlin 2.3.21 (Detekt and project Kotlin can be decoupled).
  - Capture the outcome in `tasks.md` Done criteria; if real incompatibility found, raise as a blocker before marking spec complete.
- Source: <https://github.com/detekt/detekt/releases>

### CV-06: 16KB native libs

- **Spec 001 baseline contains zero `.so` libraries**. All listed deps are pure Kotlin/Java/AAR-resource. Per-lib 16KB verification is therefore deferred until the first spec adding a native dependency.
- AGP 9.2 + NDK r27d/r29 produce 16KB-aligned binaries by default (when NDK is eventually wired in).
- CI 16KB-verify step will be implemented as a fail-soft script per FR-016: it scans for `lib/*/*.so` and exits 0 with skip log if none present.

### CV-07: Compose BOM management

- Do **NOT** pin individual `compose.ui:*` and `compose.material3:*` versions in `libs.versions.toml`; let `compose-bom:2026.04.01` resolve them.
- Only pin the BOM version itself in `[versions]` and reference dependencies without a version in `[libraries]` (the BOM constraints kick in via `platform(libs.androidx.compose.bom)` in `build.gradle.kts`).

## Plugin & Gradle Wiring Pattern (informational, not mandate)

```kotlin
// settings.gradle.kts — NO change to dependencyResolutionManagement.versionCatalogs
//                       (libs.versions.toml is auto-loaded as `libs`)
pluginManagement {
    repositories { gradlePluginPortal(); google(); mavenCentral() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
```

```kotlin
// app/build.gradle.kts plugins block (illustrative)
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)        // Kotlin 2.0+ Compose plugin
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}
```

## Alternatives Considered

| Alternative | Why rejected |
|-------------|--------------|
| Stay on AGP 8.x (e.g., 8.13) | Older feature set; misses 16KB defaults of AGP 9.x; only marginal compat advantage (still requires JDK 17 launcher) |
| Pin individual Compose versions instead of BOM | Drifts; harder to upgrade; constitution-friendly but loses BOM safety |
| Use `kotlin-android-extensions` for Compose | Deprecated; Kotlin 2.0+ uses `kotlin.plugin.compose` |
| ktlint via CLI instead of Gradle plugin | Manual integration burden; jlleitschuh plugin de-facto standard |
| Detekt-baseline OFF (force fix template) | Unknown which template warnings exist; safer to allow baseline at first then strict for new code (FR-020 path) |
| Skip 16KB CI step in 001 | Constitution §IX wants gate from earliest spec; cheap to wire fail-soft now vs retrofit later |

## Risks & Open Items

1. **Detekt + Kotlin 2.3 compat** (CV-05) — discover at first `./gradlew detekt` run; mitigation noted.
2. **AGP 9.2 release maturity** — AGP 9.x is recent; if crash/regression encountered during implementation, fallback path is AGP 8.13.x (last 8.x stable at lookup time). Document fallback in `tasks.md` if needed.
3. **JDK 17+ launcher** (CV-02) — spec assumption updated; CI runner uses JDK 17 by default (`ubuntu-latest` ships JDK 17), so CI is fine without change. Local devs need JDK 17+ to launch Gradle.

## Sources Verified (2026-04-30)

- <https://developer.android.com/build/releases/gradle-plugin>
- <https://kotlinlang.org/docs/releases.html>
- <https://gradle.org/releases/>
- <https://developer.android.com/jetpack/compose/bom/bom-mapping>
- <https://developer.android.com/jetpack/androidx/releases/core>
- <https://developer.android.com/jetpack/androidx/releases/lifecycle>
- <https://developer.android.com/jetpack/androidx/releases/activity>
- <https://developer.android.com/jetpack/androidx/releases/test>
- <https://github.com/detekt/detekt/releases>
- <https://github.com/JLLeitschuh/ktlint-gradle/releases>
- <https://central.sonatype.com/artifact/junit/junit>
- <https://developer.android.com/ndk/downloads>

## Outcome

All NEEDS CLARIFICATION items from Technical Context resolved. One discovered constraint (CV-02 JDK 17 launcher) requires a one-line update to spec.md Assumptions. Ready for Phase 1 design artifacts.
