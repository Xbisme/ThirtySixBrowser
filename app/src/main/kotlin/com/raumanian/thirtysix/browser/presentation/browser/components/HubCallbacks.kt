package com.raumanian.thirtysix.browser.presentation.browser.components

/**
 * Spec 019 FR-031 — every destination the hub reaches. The sheet closes itself before
 * invoking one (FR-032), so callers only navigate or act.
 */
internal data class HubCallbacks(
    val onSettings: () -> Unit,
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onDownloadsInProgress: () -> Unit,
    val onPrivacy: () -> Unit,
    val onNewIncognitoTab: () -> Unit,
    val onDownloads: () -> Unit,
    val onHistory: () -> Unit,
    val onBookmarks: () -> Unit,
    val onQuickSetting: (QuickSetting) -> Unit,
)

/** Spec 019 — the hub's quick-setting tiles, each opening an existing Settings chooser. */
internal enum class QuickSetting { Theme, Language, SearchEngine, ClearData }
