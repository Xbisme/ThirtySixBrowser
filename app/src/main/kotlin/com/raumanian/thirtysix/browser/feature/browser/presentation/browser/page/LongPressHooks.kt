package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import androidx.compose.material3.SnackbarHostState

/** The long-press dependencies; [onDownload] goes through the storage-permission gate. */
class LongPressHooks(
    val actions: LongPressActions,
    val snackbarHostState: SnackbarHostState,
    val isIncognito: () -> Boolean,
    val onDownload: (url: String) -> Unit,
)
