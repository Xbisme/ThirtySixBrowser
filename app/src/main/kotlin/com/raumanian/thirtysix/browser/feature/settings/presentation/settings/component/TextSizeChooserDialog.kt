package com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.TextSize
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

/** Locale-formatted, e.g. "125%" or, in French, "125 %". */
@Composable
fun textSizeLabel(size: TextSize): String = stringResource(R.string.text_size_level, size.percent)

const val TEST_TAG_SETTINGS_TEXT_SIZE_DIALOG: String = "settings_text_size_dialog"
