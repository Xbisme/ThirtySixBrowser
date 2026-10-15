package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.component

import com.raumanian.thirtysix.browser.core.presentation.util.QuickSetting
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.PageTool
/** Every destination the hub reaches; the sheet closes itself before invoking one. */
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
    val onPageTool: (PageTool) -> Unit,
    val onProtectionSwitched: (on: Boolean) -> Unit = {},
    /** The caller confirms before anything is cleared. */
    val onFire: () -> Unit = {},
    /** The blocked-tracker line on a web page; Home and the incognito page keep [onPrivacy]. */
    val onDashboard: () -> Unit = {},
)
