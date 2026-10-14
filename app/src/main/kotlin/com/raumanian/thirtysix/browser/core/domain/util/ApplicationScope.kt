package com.raumanian.thirtysix.browser.core.domain.util

import javax.inject.Qualifier

/**
 * Spec 024 research R10 — a process-wide `CoroutineScope` for work that must outlive the screen
 * that started it (a Fire run). Provided in `di/PrivacyToolsModule`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
