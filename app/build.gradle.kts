import java.util.Properties

plugins {
    // AGP 9.0+ has built-in Kotlin support — no separate `kotlin-android` plugin needed.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// --- Signing config (Constitution §XI v1.2.0 — two-scope rule) ----------------
// Distribution builds: read release keystore from local.properties OR env vars.
// Local dev / CI iteration: fall back to debug keystore with mandatory warning.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String): String? = (keystoreProperties.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }

val releaseKeystorePath: String? = signingValue("KEYSTORE_PATH")
val hasReleaseKeystore: Boolean = releaseKeystorePath != null

android {
    namespace = "com.raumanian.thirtysix.browser"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.raumanian.thirtysix.browser"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("releaseConfig") {
            if (hasReleaseKeystore) {
                storeFile = file(releaseKeystorePath!!)
                storePassword = signingValue("KEYSTORE_PASSWORD")
                keyAlias = signingValue("KEY_ALIAS")
                keyPassword = signingValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Two-scope signing per Constitution §XI v1.2.0:
            //   - release keystore present  → use it
            //   - absent                    → fall back to debug keystore + warning (FR-013)
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("releaseConfig")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // Java target 11 via Toolchain — Gradle auto-provisions JDK 11 for compilation
    // regardless of the launcher JDK. (Spec 001 Q3 clarification + FR-006.)

    buildFeatures {
        compose = true
    }

    // Constitution §III + Q4 clarification: Lint strict for ALL build types.
    // No `lint-baseline.xml` — fix issues directly. (Different from Detekt baseline policy.)
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        checkDependencies = false

        // AGP 9.x compatibility false positives — lint hasn't fully caught up with the
        // `compileSdk { version = release(36) { minorApiLevel = 1 } }` DSL form. These
        // checks compare against a hypothetical newer SDK (37) and ignore minorApiLevel.
        // Constitution §X mandates targetSdk = 36 for v1.0. Re-evaluate when AGP/Lint
        // ship support for the new DSL.
        // `AndroidGradlePluginVersion` is also disabled because we intentionally pin
        // AGP for Android Studio compat — lint nagging about non-latest is noise.
        disable += listOf(
            "OldTargetApi",
            "GradleDependency",
            "AndroidGradlePluginVersion",
        )
    }
}

// --- Detekt (Constitution §III No-Hardcode Rule, FR-018) ---------------------
detekt {
    config.setFrom(files("$rootDir/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    ignoreFailures = false
    // CV-05 mitigation: pin Detekt's bundled Kotlin analyzer independently of the
    // project Kotlin version — Detekt 1.23.8 was built against Kotlin 2.0.21.
    // Adjust if upstream releases a newer build supporting Kotlin 2.3.
}

// --- ktlint (FR-019) ---------------------------------------------------------
ktlint {
    android.set(true)
    ignoreFailures.set(false)
}

kotlin {
    jvmToolchain(11)
}

// Print warning when release falls back to debug-signing (Constitution §XI v1.2.0 mandatory).
// Use println so the message is visible at the default log level.
gradle.taskGraph.whenReady {
    if (allTasks.any { it.name == "packageRelease" } && !hasReleaseKeystore) {
        println(
            "⚠️ release built with DEBUG signature — NOT for distribution. " +
                "Set KEYSTORE_PATH/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD via local.properties or env vars to use a real release key.",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
