@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.raumanian.thirtysix.browser.R

/**
 * Spec 011 — temporary minimal stub. Full switcher implementation lands in
 * task T031 (Phase 3 / US1) which rewrites this Composable to host the
 * `LazyVerticalGrid` of `TabSwitcherCard`s + the new-tab card + the
 * close-all dialog + the snackbar surface.
 *
 * Until T031 ships, this stub renders only the plural-aware count title from
 * FR-015 (M4 remediation) so the file compiles cleanly post-T003 (which
 * removed the legacy `tabs_screen_placeholder` key).
 */
@Composable
fun TabsScreen() {
    Text(
        text = pluralStringResource(R.plurals.tabs_switcher_title_count, 0, 0),
        style = MaterialTheme.typography.headlineMedium,
    )
}
