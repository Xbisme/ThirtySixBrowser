@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R

internal const val TEST_TAG_OVERFLOW_MENU: String = "browser_overflow_menu"
internal const val TEST_TAG_OVERFLOW_BOOKMARKS: String = "browser_overflow_bookmarks"
internal const val TEST_TAG_OVERFLOW_HISTORY: String = "browser_overflow_history"
internal const val TEST_TAG_OVERFLOW_DOWNLOADS: String = "browser_overflow_downloads"
internal const val TEST_TAG_OVERFLOW_SETTINGS: String = "browser_overflow_settings"

/**
 * Spec 015 FR-042 – FR-046 — the browser's overflow menu.
 *
 * Bookmarks, History and Downloads — the browser's three collections — live here rather
 * than on the bottom bar. Seven affordances already crowded a 360dp-wide device and an
 * eighth would have overflowed it; consolidating also reserved the slot Settings needed.
 * Spec 016 FR-001 fills it: Settings is the fourth entry, after the three collections.
 *
 * This is a deliberate one-tap regression for Bookmarks and History, which shipped with
 * direct bottom-bar entries in Specs 013 and 014. Both still reach exactly the screens and
 * behaviour they did before (FR-044) — only the entry point moved.
 *
 * A [DropdownMenu] rather than a bottom sheet: it anchors to the control that opened it,
 * and dismisses natively on outside-tap and system back, which is FR-045 for free.
 */
@Composable
fun BrowserOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    callbacks: BrowserOverflowMenuCallbacks,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(TEST_TAG_OVERFLOW_MENU),
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.bookmarks_screen_title)) },
            onClick = callbacks.onBookmarksClick,
            modifier = Modifier.testTag(TEST_TAG_OVERFLOW_BOOKMARKS),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.history_screen_title)) },
            onClick = callbacks.onHistoryClick,
            modifier = Modifier.testTag(TEST_TAG_OVERFLOW_HISTORY),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.downloads_screen_title)) },
            onClick = callbacks.onDownloadsClick,
            modifier = Modifier.testTag(TEST_TAG_OVERFLOW_DOWNLOADS),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.settings_screen_title)) },
            onClick = callbacks.onSettingsClick,
            modifier = Modifier.testTag(TEST_TAG_OVERFLOW_SETTINGS),
        )
    }
}
