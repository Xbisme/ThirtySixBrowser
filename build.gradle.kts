// Top-level build file. Plugin versions live in gradle/libs.versions.toml (single source of truth).
// Note: AGP 9.0+ has built-in Kotlin support — no separate `kotlin-android` plugin needed.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}
