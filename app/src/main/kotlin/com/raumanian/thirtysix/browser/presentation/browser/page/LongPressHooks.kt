package com.raumanian.thirtysix.browser.presentation.browser.page

import androidx.compose.material3.SnackbarHostState

/**
 * Spec 021 US4 — the browser screen's long-press dependencies. [onDownload] goes through the
 * Spec 015 storage-permission gate; snackbars land in [snackbarHostState].
 */
class LongPressHooks(
    val actions: LongPressActions,
    val snackbarHostState: SnackbarHostState,
    val isIncognito: () -> Boolean,
    val onDownload: (url: String) -> Unit,
)
