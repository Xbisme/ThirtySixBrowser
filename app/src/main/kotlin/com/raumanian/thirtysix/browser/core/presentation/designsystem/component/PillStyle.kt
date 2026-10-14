package com.raumanian.thirtysix.browser.core.presentation.designsystem.component

/** The looks of [PillButton]. */
enum class PillStyle {
    /** The one main action of a view, on `primary`. */
    Filled,

    /** The brand gradient: onboarding's "Start browsing". */
    Gradient,

    /** Secondary actions, on `primaryContainer`. */
    Tonal,

    /** Neutral alternatives (e.g. Skip on a surface), hairline ring. */
    Outline,

    /** Dismissive actions such as Cancel. */
    Text,

    /** A destructive action shown as text. */
    Danger,

    /** The confirming button of a destructive dialog. */
    DangerFilled,

    /** White pill with ink text, floating on the hero (onboarding Next). */
    Raised,

    /** Translucent pill on the hero (onboarding Skip). */
    Glass,
}
