@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Dimens
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme

/**
 * Spec 019 — confirmation dialog (design system Dialog, confirm form): a pastel glyph, the
 * question as [title], one sentence on what will be lost ([body]), then a full-width button
 * naming the action above Cancel (FR-045). [destructive] paints the action `error`.
 */
@Composable
@Suppress("LongParameterList")
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes glyph: Int = AppIcons.DeleteFilled,
    destructive: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    val colors = ThirtySixTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier
                .widthIn(max = Dimens.dialogMaxWidth)
                .semantics { paneTitle = title },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.padding(Spacing.lg),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(Dimens.dialogGlyph)
                        .background(
                            if (destructive) colors.tilePeach else MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.shapes.large,
                        ),
                ) {
                    Icon(
                        painter = painterResource(glyph),
                        contentDescription = null,
                        tint = if (destructive) colors.tilePeachInk else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.icon),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                content?.invoke()
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    PillButton(
                        label = confirmLabel,
                        onClick = onConfirm,
                        style = if (destructive) PillStyle.DangerFilled else PillStyle.Filled,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PillButton(
                        label = cancelLabel,
                        onClick = onDismiss,
                        style = PillStyle.Text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
