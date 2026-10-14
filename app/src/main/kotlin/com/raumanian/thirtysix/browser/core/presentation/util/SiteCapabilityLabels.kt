package com.raumanian.thirtysix.browser.core.presentation.util

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
/** Spec 021 C2 — the capability's name as used inside a sentence ("camera"). */
@StringRes
fun SiteCapability.labelRes(): Int = when (this) {
    SiteCapability.Camera -> R.string.site_capability_camera
    SiteCapability.Microphone -> R.string.site_capability_microphone
    SiteCapability.Location -> R.string.site_capability_location
}

/** The capability's glyph (Site permissions rows, the permission dialog). */
@DrawableRes
fun SiteCapability.iconRes(): Int = when (this) {
    SiteCapability.Camera -> AppIcons.PhotoCamera
    SiteCapability.Microphone -> AppIcons.Mic
    SiteCapability.Location -> AppIcons.LocationOn
}

/** "camera", "camera and microphone" — in the fixed order camera, microphone, location. */
@Composable
fun capabilityList(capabilities: Set<SiteCapability>): String {
    val names = SiteCapability.entries.filter { it in capabilities }.map { stringResource(it.labelRes()) }
    return names.drop(1).fold(names.firstOrNull().orEmpty()) { acc, next ->
        stringResource(R.string.site_capability_pair, acc, next)
    }
}

/** The first letter upper-cased in the app's language, for a name standing alone ("Camera"). */
fun String.capitalizedForLocale(): String {
    val locale = java.util.Locale.forLanguageTag(Locale.current.toLanguageTag())
    return replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}
