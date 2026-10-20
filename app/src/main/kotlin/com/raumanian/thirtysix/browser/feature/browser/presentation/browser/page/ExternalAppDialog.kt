@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PillStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons

/** "Open in another app?" for an incognito page; dismissing opens nothing. */
@Composable
fun ExternalAppDialog(request: PageRequest.OpenExternalApp, done: () -> Unit) {
    val cancel = {
        request.cancel()
        done()
    }
    SiteDialogFrame(
        title = stringResource(R.string.external_app_confirm_title),
        body = stringResource(R.string.external_app_confirm_body),
        actions = listOf(
            SiteDialogAction(stringResource(R.string.popup_open), PillStyle.Filled, {
                request.open()
                done()
            }),
            SiteDialogAction(stringResource(R.string.site_dialog_cancel), PillStyle.Text, cancel),
        ),
        onDismiss = cancel,
        glyph = AppIcons.OpenInNew,
    )
}
