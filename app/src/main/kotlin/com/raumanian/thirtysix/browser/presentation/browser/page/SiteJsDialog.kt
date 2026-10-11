@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.SiteOrigin
import com.raumanian.thirtysix.browser.presentation.components.PillStyle
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 021 US6 / C2.2 — a page's alert / confirm / prompt, headed by the site that asks
 * (FR-029). From the page's second dialog a checkbox stops its further dialogs (FR-031).
 * Dismissing answers Cancel.
 */
@Composable
fun SiteJsDialog(request: PageRequest.JsDialog, onSuppress: () -> Unit, done: () -> Unit) {
    var text by remember(request) { mutableStateOf(request.defaultText.orEmpty()) }
    var suppress by remember(request) { mutableStateOf(false) }
    val answer: (Boolean) -> Unit = { ok ->
        if (suppress) onSuppress()
        if (ok) request.confirm(if (request.kind == JsDialogKind.Prompt) text else null) else request.cancel()
        done()
    }
    val ok = SiteDialogAction(stringResource(R.string.site_dialog_ok), PillStyle.Filled, { answer(true) })
    val cancel = SiteDialogAction(stringResource(R.string.site_dialog_cancel), PillStyle.Text, { answer(false) })
    SiteDialogFrame(
        title = stringResource(R.string.site_dialog_from, siteName(request.origin)),
        body = request.message.takeIf { it.isNotEmpty() },
        actions = if (request.kind == JsDialogKind.Alert) listOf(ok) else listOf(ok, cancel),
        onDismiss = { answer(false) },
        glyph = AppIcons.Globe,
    ) {
        if (request.kind == JsDialogKind.Prompt) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag(TEST_TAG_SITE_PROMPT_FIELD),
            )
        }
        if (request.showSuppress) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = suppress, role = Role.Checkbox, onValueChange = { suppress = it })
                    .testTag(TEST_TAG_SITE_SUPPRESS),
            ) {
                Checkbox(checked = suppress, onCheckedChange = null)
                Text(text = stringResource(R.string.site_dialog_suppress), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Spec 021 FR-030 — "Leave site?"; dismissing stays. */
@Composable
fun SiteLeaveDialog(request: PageRequest.LeavePage, done: () -> Unit) {
    val stay = {
        request.cancel()
        done()
    }
    SiteDialogFrame(
        title = stringResource(R.string.site_leave_title),
        body = stringResource(R.string.site_leave_body),
        actions = listOf(
            SiteDialogAction(stringResource(R.string.site_leave_leave), PillStyle.Filled, {
                request.leave()
                done()
            }),
            SiteDialogAction(stringResource(R.string.site_leave_stay), PillStyle.Text, stay),
        ),
        onDismiss = stay,
        glyph = AppIcons.Warning,
    )
}

/** The site as shown to the person: its host, or the raw origin when it has none. */
internal fun siteName(origin: String): String = SiteOrigin.display(origin).ifEmpty { origin }

const val TEST_TAG_SITE_PROMPT_FIELD: String = "site_prompt_field"
const val TEST_TAG_SITE_SUPPRESS: String = "site_suppress"
