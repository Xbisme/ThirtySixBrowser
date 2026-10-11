package com.raumanian.thirtysix.browser.presentation.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.TextSize

/**
 * Spec 022 FR-029 / FR-030 — chooses the app-wide text size of web pages from the nine levels.
 * Opened from the Settings row and from the hub's Text size tile, through the same view model,
 * so the two can never disagree.
 */
@Composable
fun TextSizeChooserDialog(
    selected: TextSize,
    onSelect: (TextSize) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceDialog(
        title = stringResource(R.string.settings_text_size_title),
        options = TextSize.entries,
        selected = selected,
        optionLabel = { textSizeLabel(it) },
        onSelect = onSelect,
        onDismiss = onDismiss,
        modifier = modifier.testTag(TEST_TAG_SETTINGS_TEXT_SIZE_DIALOG),
    )
}

/** The localized percentage of [size], e.g. "125%" or, in French, "125 %". */
@Composable
fun textSizeLabel(size: TextSize): String = stringResource(R.string.text_size_level, size.percent)

const val TEST_TAG_SETTINGS_TEXT_SIZE_DIALOG: String = "settings_text_size_dialog"
