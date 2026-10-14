@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.core.presentation.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
/**
 * Spec 016 / 019 FR-044 — a group title in the Settings list. Every group after the first is
 * separated from the one above by a hairline. The title is the shared [SectionHeader], which is
 * marked as a heading so TalkBack users can jump between groups (FR-039).
 */
@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier) {
        if (showDivider) {
            HorizontalDivider(
                thickness = Dimens.hairline,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = Spacing.sm, start = Spacing.md, end = Spacing.md),
            )
        }
        SectionHeader(text = title)
    }
}
