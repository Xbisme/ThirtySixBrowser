package com.raumanian.thirtysix.browser.core.presentation.designsystem.component

import androidx.compose.ui.graphics.Color

private const val SEED_SATURATION = 0.55f
private const val SEED_VALUE = 0.72f
private const val HUE_RANGE = 360

/**
 * A stable colour per site, from a hash of its host: the same site always gets the same mesh.
 * A hash avoids decoding a favicon bitmap per card.
 */
internal fun seedColorFor(host: String): Color =
    Color.hsv(
        hue = Math.floorMod(host.hashCode(), HUE_RANGE).toFloat(),
        saturation = SEED_SATURATION,
        value = SEED_VALUE,
    )
