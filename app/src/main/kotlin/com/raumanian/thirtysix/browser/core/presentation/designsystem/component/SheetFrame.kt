@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.core.presentation.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
private val HandleTop = 10.dp
private val HandleBottom = 6.dp
private const val SCRIM_ALPHA = 0.35f

/**
 * Spec 019 — the frame for the hub and every action sheet (design system BottomSheet):
 * `radius-sheet` top corners, raised surface, a slim handle. The drag offset is owned by
 * [ModalBottomSheet], which applies it in the layout phase, so dragging never recomposes
 * [content] (contracts C6). The sheet's default window insets keep content above the
 * navigation bar (FR-008).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetFrame(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = ThirtySixTheme.shapes.sheet,
        containerColor = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        scrimColor = scheme.scrim.copy(alpha = SCRIM_ALPHA),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = HandleTop, bottom = HandleBottom)
                    .size(width = Dimens.sheetHandleWidth, height = Dimens.sheetHandleHeight)
                    .background(scheme.outlineVariant, ThirtySixTheme.shapes.full),
            )
        },
        content = content,
    )
}
