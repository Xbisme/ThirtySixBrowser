@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.DialogProperties
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PillButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
/**
 * The frame every page-request dialog shares: a glyph, the title naming the site, optional body
 * and content, then stacked full-width buttons. Dismissing calls [onDismiss], which answers the
 * page as its cancel button would.
 */
@Composable
@Suppress("LongParameterList")
fun SiteDialogFrame(
    title: String,
    actions: List<SiteDialogAction>,
    onDismiss: () -> Unit,
    @DrawableRes glyph: Int,
    modifier: Modifier = Modifier,
    body: String? = null,
    dismissOnOutside: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = dismissOnOutside),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier
                .widthIn(max = Dimens.dialogMaxWidth)
                .semantics { paneTitle = title },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(Dimens.dialogGlyph)
                        .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.large),
                ) {
                    Icon(
                        painter = painterResource(glyph),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.icon),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                body?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content?.invoke()
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    actions.forEach { action ->
                        PillButton(
                            label = action.label,
                            onClick = action.onClick,
                            style = action.style,
                            enabled = action.enabled,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
