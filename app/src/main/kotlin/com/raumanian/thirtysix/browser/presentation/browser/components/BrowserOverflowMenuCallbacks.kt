package com.raumanian.thirtysix.browser.presentation.browser.components

/**
 * Spec 015 FR-046 — entry bundle for [BrowserOverflowMenu].
 *
 * A data class rather than loose parameters so Spec 016 can add Settings without pushing
 * the composable past detekt's parameter ceiling — which is precisely the "structured so a
 * further entry can be added without another redesign" that FR-046 asks for.
 */
data class BrowserOverflowMenuCallbacks(
    val onBookmarksClick: () -> Unit,
    val onHistoryClick: () -> Unit,
    val onDownloadsClick: () -> Unit,
)
