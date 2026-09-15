@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.onboarding.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 018 — one selectable option on a choice slide.
 *
 * ⚠️ This is deliberately NOT Spec 016's `SingleChoiceOptionRow`, despite the three Settings
 * choosers carrying KDoc that says onboarding "can reuse it unchanged". That claim is accurate
 * about statelessness and wrong about presentation (research.md R1):
 *
 *  - `SingleChoiceOptionRow` is `private` inside `SingleChoiceDialog.kt` and cannot be imported.
 *  - All three public choosers render an `AlertDialog`. FR-009 requires the options to be
 *    visible ON the slide, and routing through a dialog would cost two extra taps per slide,
 *    breaking SC-008's four-tap budget outright.
 *
 * What IS reused is what matters for consistency: the option lists (`ThemeMode.entries` and
 * friends) and Spec 016's public label functions, so the two screens can never disagree about
 * what is on offer or what it is called (INV-8).
 *
 * The row carries the click and the semantics; the radio button is visual only, so TalkBack
 * announces one control with its selected state rather than two (FR-018).
 */
@Composable
fun OnboardingOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .testTag(TEST_TAG_ONBOARDING_OPTION)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = isSelected, onClick = null)
        Spacer(modifier = Modifier.width(Spacing.md))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Every option row on every onboarding slide carries this tag, so tests can count and pick. */
const val TEST_TAG_ONBOARDING_OPTION: String = "onboarding_option"
