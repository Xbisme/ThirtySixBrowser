package com.raumanian.thirtysix.browser.presentation.downloads.components

/**
 * Spec 015 — click bundle for [DownloadActionSheet].
 *
 * Follows the `NavigationBottomBarCallbacks` / `BrowserWebViewCallbacks` precedent: bundling
 * keeps the composable's parameter count under detekt's `LongParameterList.functionThreshold`,
 * and a data class is exempt from the rule outright, so the bundle can grow with the sheet.
 */
data class DownloadActionSheetCallbacks(
    val onOpen: () -> Unit,
    val onCopyLink: () -> Unit,
    val onRemoveFromList: () -> Unit,
    val onDeleteFile: () -> Unit,
    val onDismiss: () -> Unit,
)
