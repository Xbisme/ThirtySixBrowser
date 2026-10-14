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
 * A group title in the Settings list, marked as a heading so TalkBack users can jump between
 * groups. Every group after the first gets a hairline above it.
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
