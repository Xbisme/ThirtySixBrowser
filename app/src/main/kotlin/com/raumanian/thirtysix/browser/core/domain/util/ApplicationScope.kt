package com.raumanian.thirtysix.browser.core.domain.util

import javax.inject.Qualifier

/** A process-wide `CoroutineScope` for work that must outlive the screen that started it (a Fire run). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
