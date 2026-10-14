package com.raumanian.thirtysix.browser.core.presentation.designsystem.component

import androidx.compose.ui.graphics.Color

private const val SEED_SATURATION = 0.55f
private const val SEED_VALUE = 0.72f
private const val HUE_RANGE = 360

/**
 * A stable colour per site, from a hash of its host: the same site always gets the same mesh.
 * Replaces research R13's favicon average, which needed a bitmap decode per card (see tasks.md).
 */
internal fun seedColorFor(host: String): Color =
    Color.hsv(
        hue = Math.floorMod(host.hashCode(), HUE_RANGE).toFloat(),
        saturation = SEED_SATURATION,
        value = SEED_VALUE,
    )
