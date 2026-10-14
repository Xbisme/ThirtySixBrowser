@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PillButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PillStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
/**
 * Spec 019 — the text-input dialog frame shared by the folder and bookmark dialogs: an
 * [AlertDialog] at radius 28 (`shapes.extraLarge`) on the raised surface, with the design
 * system's pill buttons (filled OK, text Cancel). The shared `ConfirmDialog`/`ChoiceDialog`
 * have no slot for input fields, so this stays AlertDialog-based.
 */
@Composable
internal fun BookmarkInputDialog(
    title: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { content() }
        },
        confirmButton = {
            PillButton(
                label = stringResource(android.R.string.ok),
                onClick = onConfirm,
                style = PillStyle.Filled,
                enabled = confirmEnabled,
            )
        },
        dismissButton = {
            PillButton(
                label = stringResource(android.R.string.cancel),
                onClick = onDismiss,
                style = PillStyle.Text,
            )
        },
    )
}

/** Spec 019 — corner shape of the input fields inside [BookmarkInputDialog]. */
@Composable
@ReadOnlyComposable
internal fun bookmarkFieldShape(): Shape = MaterialTheme.shapes.medium
