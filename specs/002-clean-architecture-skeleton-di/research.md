# Research — Spec 002 Package Versions & Compatibility

> **Date verified**: 2026-05-01
> **Build environment baseline** (from Spec 001): AGP 9.1.1, Kotlin 2.3.21, Gradle 9.5.0, Compose BOM 2026.04.01, Java target 11, launcher JDK 17+.

## Summary

All 7 dependencies (6 packages + KSP plugin) have stable versions released on or before 2026-05-01 that are compatible with AGP 9.1.1. **Hilt 2.59.2** (2026-02-20) is the first Hilt line to support AGP 9, aligning naturally with our stack. KSP versioning has been decoupled from Kotlin compiler version since KSP 2.3.0 — **KSP 2.3.7** (2026-04-22) is the recommended pairing with Kotlin 2.3.21.

**Critical correction vs spec.md FR-022**: artifact ID for KSP wiring is `com.google.dagger:hilt-compiler` (NOT `com.google.dagger:hilt-android-compiler`). The latter is the legacy kapt-era artifact and using it with `ksp(...)` configuration produces a silent no-op (build passes but no Hilt components are generated). Spec 002 implementation MUST use `hilt-compiler`.

**Highest residual risk**: Hilt 2.59.2's POM still pulls `kotlin-stdlib:2.0.21`. Whether its bundled `kotlinx-metadata-jvm` can read Kotlin 2.3.x class metadata is **not documented**. If KSP fails at the first `@HiltAndroidApp` annotation, fallback to **kapt** (use `hilt-android-compiler` artifact instead) — see Risks section.

---

## Package Decisions

### 1. Hilt Android Runtime

- **Decision**: `com.google.dagger:hilt-android:2.59.2`
- **Released**: 2026-02-20
- **Source**: <https://github.com/google/dagger/releases/tag/dagger-2.59.2>
- **Kotlin 2.3.21 compat**: PROBABLE-BUT-UNVERIFIED. Hilt declares `kotlin-stdlib:2.0.21` in POM. Kotlin 2.3.x bumped metadata version; whether Hilt's bundled `kotlinx-metadata-jvm` reads 2.3 metadata is not stated in release notes. **Smoke test on Day 1 of Spec 002 PR**.
- **AGP 9.1.1 compat**: ✅ YES — Hilt 2.59 explicitly requires AGP 9 + Gradle 9.1+.
- **16KB**: Kotlin/Java only — N/A (no `.so`).
- **Alternatives**: 2.59.1 (2026-02-03, AGP 9 jetifier bugs); 2.58 (does NOT support AGP 9 — blocker).

### 2. Hilt Compiler (KSP processor)

- **Decision**: `com.google.dagger:hilt-compiler:2.59.2` ← **artifact rename vs spec FR-022**
- **Released**: 2026-02-20 (lockstep with hilt-android)
- **Source**: <https://github.com/google/dagger/releases/tag/dagger-2.59.2> + KSP wiring guide <https://dagger.dev/dev-guide/ksp.html>
- **Kotlin 2.3.21 compat**: tied to runtime — same caveat as #1.
- **AGP 9.1.1 compat**: ✅ YES.
- **16KB**: Annotation processor only — N/A.
- **Important**: User prompt (and spec FR-022) referenced `hilt-android-compiler`. That ID is the kapt-era artifact. Per official Dagger KSP guide, KSP wiring REQUIRES `hilt-compiler`. Using `ksp("...hilt-android-compiler:...")` compiles successfully but generates zero Hilt code → app crashes at runtime with "...inject not registered". **Spec 002 implementation MUST use `hilt-compiler`**. Add verification step in `quickstart.md`.
- **Alternatives**: kapt fallback `kapt("com.google.dagger:hilt-android-compiler:2.59.2")` — see Risk #1.

### 3. Hilt Android Gradle Plugin

- **Decision**: `com.google.dagger:hilt-android-gradle-plugin:2.59.2` (apply via plugins DSL: `id("com.google.dagger.hilt.android") version "2.59.2"`)
- **Released**: 2026-02-20
- **Source**: <https://github.com/google/dagger/releases/tag/dagger-2.59.2>
- **AGP 9.1.1 compat**: ✅ YES — 2.59 added AGP 9 support and made it a hard floor; 2.59.2 fixed HiltSyncTask incremental + jetifier compile errors.
- **16KB**: Build-time plugin — N/A.
- **Alternatives**: None — anything pre-2.59 cannot run with AGP 9.
- **Note**: Keep `android.enableJetifier=false` in `gradle.properties` (already default in our project; do not flip).

### 4. AndroidX Hilt Navigation Compose

- **Decision**: `androidx.hilt:hilt-navigation-compose:1.3.0`
- **Released**: 2025-09-10
- **Source**: <https://developer.android.com/jetpack/androidx/releases/hilt>
- **Kotlin 2.3.21 compat**: ✅ YES — pure androidx, consumes host Kotlin compiler.
- **AGP 9.1.1 compat**: ✅ YES.
- **16KB**: Kotlin only — N/A.
- **Alternatives**: 1.2.0 — superseded; missing `hilt-lifecycle-viewmodel-compose` extraction.
- **Note**: 1.3.0 introduced sibling artifact `androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0` (holds `hiltViewModel()` without nav-compose dependency). NOT NEEDED for Spec 002 because `hilt-navigation-compose:1.3.0` re-exports `hiltViewModel()`. Revisit if a future module needs it without nav.

### 5. AndroidX Lifecycle ViewModel Compose

- **Decision**: `androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0` — **pin explicitly, NOT BOM-managed**
- **Released**: 2025-11-19
- **Source**: <https://developer.android.com/jetpack/androidx/releases/lifecycle>
- **Kotlin 2.3.21 compat**: ✅ YES.
- **AGP 9.1.1 compat**: ✅ YES.
- **16KB**: Kotlin only — N/A.
- **BOM-managed?**: ❌ **NO**. Per <https://developer.android.com/develop/ui/compose/bom/bom-mapping>, Compose BOM 2026.04.01 manages only `androidx.compose.{animation,foundation,material,material3,runtime,ui}`. `androidx.lifecycle:*` is a separate group → MUST pin in `libs.versions.toml`.
- **Alternatives**: 2.11.0-beta01 (2026-04-22) — beta, violates Constitution stable-only stance. Stay on 2.10.0.

### 6. AndroidX Navigation Compose

- **Decision**: `androidx.navigation:navigation-compose:2.9.8`
- **Released**: 2026-04-22
- **Source**: <https://developer.android.com/jetpack/androidx/releases/navigation>
- **Kotlin 2.3.21 compat**: ✅ YES.
- **AGP 9.1.1 compat**: ✅ YES.
- **16KB**: Kotlin only — N/A.
- **Alternatives**: 2.9.7 / 2.9.6 — superseded; 2.9.8 contains predictive-back NPE fixes relevant to Spec 008 (`navigation-controls`).

### 7. KSP Gradle Plugin

- **Decision**: `com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.7` (apply via `id("com.google.devtools.ksp") version "2.3.7"`)
- **Released**: 2026-04-22
- **Source**: <https://github.com/google/ksp/releases/tag/2.3.7>
- **Kotlin 2.3.21 compat**: ✅ YES (recommended pairing). KSP 2.3.7 release note: *"Bumped Kotlin target language version to 2.3"*. Kotlin docs example pairs Kotlin 2.3.21 with KSP 2.3.6; 2.3.7 supersedes with Gradle Isolated Projects fix.
- **Version-format note**: Since KSP 2.3.0, the legacy `<kotlin>-<patch>` (e.g. `2.0.21-1.0.27`) format was abandoned. KSP versions are now plain semver `2.3.7`. The original spec FR-022 expectation of a `2.3.21-x.y.z` artifact is outdated — that scheme no longer exists.
- **AGP 9.1.1 compat**: ✅ YES — KSP runs orthogonal to AGP.
- **16KB**: Build-time plugin — N/A.
- **Alternatives**: KSP 2.3.6 (2026-02-17) — works fine with Kotlin 2.3.21 per Kotlin docs example, but lacks 2.3.7's Gradle Isolated Projects fix. Listed as fallback if 2.3.7 misbehaves with Hilt.

---

## Cross-cutting Decisions

### KSP × Kotlin 2.3.21 status

- **Verdict**: COMPATIBLE. KSP 2.3.7 (and 2.3.6) explicitly support Kotlin 2.3.x. KSP versioning is decoupled from Kotlin compiler version — the `<kotlin>-<patch>` heuristic mentioned in spec edge case is outdated.
- **Recommendation**: Pin `ksp = "2.3.7"` in version catalog. Track upgrades from <https://github.com/google/ksp/releases> independently from Kotlin version.

### Hilt Gradle plugin × AGP 9.x status

- **Verdict**: SUPPORTED with version floor. Dagger 2.59 was first to support AGP 9 (hard requirement). 2.59.2 patched two follow-up regressions. Use **only** 2.59.2 — 2.59 and 2.59.1 each have known AGP 9 issues.
- **Recommendation**: Single source of truth `hilt = "2.59.2"` in `libs.versions.toml`.

### `lifecycle-viewmodel-compose` source

- **Verdict**: NOT BOM-managed.
- **Recommendation**: Add top-level `lifecycle = "2.10.0"` entry in `[versions]` of `libs.versions.toml`, reference via library entry. Reuse same key for any future lifecycle artifacts (e.g. `lifecycle-runtime-compose`) so they stay lockstep.

### `BaseViewModel.launchSafely` testing approach

Per spec FR-020, unit test for `launchSafely` requires testing coroutine behavior on `viewModelScope`. `viewModelScope` runs on `Dispatchers.Main`, which is unavailable in JVM unit tests by default.

- **Decision**: Add test dependency `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2` (2026-01-15, latest stable). Use `Dispatchers.setMain(StandardTestDispatcher())` in `@Before` and `Dispatchers.resetMain()` in `@After`.
- **Source**: <https://github.com/Kotlin/kotlinx.coroutines/releases/tag/1.10.2>
- **16KB**: Kotlin only — N/A.
- **Note**: This is a TEST-only dependency, scope `testImplementation` — does not ship in APK.

---

## Risks & Fallbacks

### Risk 1 — Hilt × Kotlin 2.3.21 metadata (HIGHEST)

Hilt 2.59.2's POM pulls `kotlin-stdlib:2.0.21`. Kotlin 2.3.x changed class metadata version. If Hilt's bundled `kotlinx-metadata-jvm` cannot parse 2.3 metadata, KSP build fails at first `@HiltAndroidApp` annotation.

**Smoke test (Day 1 of Spec 002 PR)**:
```bash
./gradlew :app:kspDebugKotlin --info 2>&1 | tee /tmp/ksp-spec002.log
# Look for: "Class metadata version mismatch", "kotlinx.metadata", or KSP error referencing Hilt
```

**Fallback ladder** (apply in order):
- **A. kapt fallback** — switch from `ksp("com.google.dagger:hilt-compiler:2.59.2")` to `kapt("com.google.dagger:hilt-android-compiler:2.59.2")`. Slower build (~30%) but historically more tolerant of metadata skew. Apply `kotlin-kapt` plugin instead of KSP plugin.
- **B. Kotlin downgrade** — pin `kotlin = "2.2.20"` in version catalog (last 2.2.x stable). Reverts Spec 001's "latest stable" choice; document in `recent changes` log.
- **C. Hold-and-watch** — track <https://github.com/google/dagger/issues> for 2026-Q2 issues mentioning Kotlin 2.3 metadata; hold Spec 002 merge until 2.60+ ships with refreshed metadata. Last resort.

### Risk 2 — KSP 2.3.7 freshness (LOW)

Released 2026-04-22, only 9 days before our cutoff. Insufficient field hardening.
- **Fallback**: pin `ksp = "2.3.6"` (2026-02-17, ~10 weeks soak time).

### Risk 3 — Compose BOM transitive lifecycle drift (LOW)

Compose artifacts may transitively pull `lifecycle-viewmodel-ktx` of a different version than our pin.
- **Mitigation**: Run `./gradlew :app:dependencies | grep lifecycle` after Spec 002 build to confirm 2.10.0 is the resolved version. Add `resolutionStrategy.eachDependency` only if conflict surfaces.

### Risk 4 — `hilt-android-compiler` vs `hilt-compiler` artifact silent failure (CRITICAL)

Using `ksp("com.google.dagger:hilt-android-compiler:...")` produces a build that compiles but generates ZERO Hilt code. App will crash at runtime with "Hilt component not generated".

**Mitigation in `quickstart.md`**: explicit verification step
```bash
./gradlew :app:dependencies --configuration kspDebugKotlin | grep hilt
# Expected: "+--- com.google.dagger:hilt-compiler:2.59.2"
# WRONG:    "+--- com.google.dagger:hilt-android-compiler:2.59.2"
```

### Risk 5 — Lifecycle 2.11.0-beta01 temptation

Released 2026-04-22 with Navigation3-targeted ViewModel integration.
- **Decision**: Hold at stable 2.10.0; Spec 002 doesn't introduce Navigation3. Revisit only if a future spec adopts Navigation3.

### Risk 6 — Hilt + AGP 9.1.1 jetifier interaction

Hilt 2.59.0 had compile error when `enableJetifier=true`.
- **Mitigation**: Verify `gradle.properties` does NOT contain `android.enableJetifier=true`. If present, remove. Spec 001 confirmed `enableJetifier` defaults false.

---

## Final Version Catalog Additions (preview for Phase 1 / Spec 002 implementation)

```toml
[versions]
# ... existing versions from Spec 001 ...
hilt = "2.59.2"
ksp = "2.3.7"
hiltNavigationCompose = "1.3.0"
lifecycle = "2.10.0"
navigationCompose = "2.9.8"
coroutinesTest = "1.10.2"

[libraries]
# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }  # KSP, NOT hilt-android-compiler
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

# Lifecycle (NOT BOM-managed)
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }

# Navigation
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

# Test
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }

[plugins]
# ... existing plugins ...
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

## Open Questions

None blocking implementation. All version pins decided; risk fallbacks ladder documented.
