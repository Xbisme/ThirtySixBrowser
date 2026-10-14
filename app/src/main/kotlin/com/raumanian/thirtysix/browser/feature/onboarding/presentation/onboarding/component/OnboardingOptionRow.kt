@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
/**
 * Not the Settings chooser rows, which live in dialogs; the option lists and label functions are
 * shared instead. The row carries click and selected state, so a screen reader announces one control.
 */
@Composable
fun OnboardingOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThirtySixTheme.colors
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touchTarget + Spacing.xs)
            .clip(shape)
            .background(if (isSelected) scheme.surfaceContainerLow else colors.glass)
            .border(Dimens.hairline, if (isSelected) Color.Transparent else colors.glassStroke, shape)
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .testTag(TEST_TAG_ONBOARDING_OPTION)
            .padding(horizontal = Spacing.md),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) scheme.onSurface else colors.onGlass,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                painter = painterResource(AppIcons.CheckCircle),
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(Dimens.iconSmall),
            )
        }
    }
}

/** Every option row on every onboarding slide carries this tag, so tests can count and pick. */
const val TEST_TAG_ONBOARDING_OPTION: String = "onboarding_option"
