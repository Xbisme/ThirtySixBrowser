package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PillStyle
/** One full-width button of a [SiteDialogFrame]. */
data class SiteDialogAction(
    val label: String,
    val style: PillStyle,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)
