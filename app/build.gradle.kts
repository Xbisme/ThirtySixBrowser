import java.util.Properties

plugins {
    // AGP 9.0+ has built-in Kotlin support — no separate `kotlin-android` plugin needed.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

// Release keystore from local.properties or env vars; without one, local and CI builds fall back
// to the debug keystore with a warning (never for distribution).
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
        // Boots HiltTestApplication so @HiltAndroidTest works.
        testInstrumentationRunner = "com.raumanian.thirtysix.browser.HiltTestRunner"
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
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("releaseConfig")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // Java target 11 via Toolchain — Gradle auto-provisions JDK 11 for compilation
    // regardless of the launcher JDK.

    // Desugaring brings `java.time` to minSdk 24.
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME for Settings › About; AGP 9 defaults this to false and ignores
        // the gradle.properties flag.
        buildConfig = true
    }

    // Robolectric needs the merged manifest and res/ to build an Application context.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // Lint is strict for every build type, with no baseline: issues are fixed, not recorded.
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkReleaseBuilds = true
        checkDependencies = false

        // Lint misreads the `release(36) { minorApiLevel = 1 }` DSL and compares against SDK 37;
        // AGP is pinned on purpose for Android Studio compatibility.
        disable += listOf(
            "OldTargetApi",
            "GradleDependency",
            "AndroidGradlePluginVersion",
        )

        // Translation completeness stays build-blocking even if `warningsAsErrors` is ever relaxed.
        error += listOf(
            "MissingTranslation",
            "ExtraTranslation",
        )
    }
}

// Room schema snapshots are committed so reviewers and migrations can diff schema versions.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin", "src/debug/kotlin")
    buildUponDefaultConfig = true
    allRules = false
    ignoreFailures = false
    // Detekt 1.23.8 analyses with its own bundled Kotlin 2.0.21, not the project's 2.3.
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
    baseline.set(file("$rootDir/config/ktlint/baseline.xml"))
}

kotlin {
    jvmToolchain(11)
}

// Konsist 0.18 ships Java 17 bytecode; tests run on the same JDK 21 as the Gradle daemon.
tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}

// Compose reports and metrics only with `-PcomposeReports`, so ordinary builds pay nothing.
composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("config/compose/stability.conf"))
    if (project.hasProperty("composeReports")) {
        reportsDestination = layout.buildDirectory.dir("compose_reports")
        metricsDestination = layout.buildDirectory.dir("compose_reports")
    }
}

// println so the warning is visible at the default log level.
gradle.taskGraph.whenReady {
    if (allTasks.any { it.name == "packageRelease" } && !hasReleaseKeystore) {
        println(
            "⚠️ release built with DEBUG signature — NOT for distribution. " +
                "Set KEYSTORE_PATH/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD via local.properties or env vars to use a real release key.",
        )
    }
}

dependencies {
    ktlintRuleset(libs.compose.rules.ktlint)
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)

    // KSP needs `hilt-compiler`; the kapt-era `hilt-android-compiler` silently generates nothing.
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Ships libdatastore_shared_counter.so (16 KB-aligned), despite being a Kotlin library.
    implementation(libs.androidx.datastore.preferences)

    // In-app language below Android 13 needs AppCompatDelegate.setApplicationLocales.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.webkit)

    implementation(libs.androidx.core.splashscreen)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.app.cash.turbine)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.konsist)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.web)
    androidTestImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // KSP must also run on androidTest sources to generate the test Hilt component.
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
