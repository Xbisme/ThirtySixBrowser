package com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component

// Bundled to stay under detekt's `LongParameterList`; data classes are exempt from it.
data class DownloadActionSheetCallbacks(
    val onOpen: () -> Unit,
    val onCopyLink: () -> Unit,
    val onRemoveFromList: () -> Unit,
    val onDeleteFile: () -> Unit,
    val onDismiss: () -> Unit,
)
