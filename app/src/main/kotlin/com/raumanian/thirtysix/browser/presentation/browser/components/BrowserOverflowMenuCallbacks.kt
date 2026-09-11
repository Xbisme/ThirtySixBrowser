package com.raumanian.thirtysix.browser.presentation.browser.components

/**
 * Spec 015 FR-046 — entry bundle for [BrowserOverflowMenu].
 *
 * A data class rather than loose parameters so a further entry can be added without pushing
 * the composable past detekt's parameter ceiling — which is precisely the "structured so a
 * further entry can be added without another redesign" that FR-046 asks for.
 *
 * Spec 016 FR-001 filled the slot Spec 015 reserved: [onSettingsClick].
 */
data class BrowserOverflowMenuCallbacks(
    val onBookmarksClick: () -> Unit,
    val onHistoryClick: () -> Unit,
    val onDownloadsClick: () -> Unit,
    val onSettingsClick: () -> Unit,
)
