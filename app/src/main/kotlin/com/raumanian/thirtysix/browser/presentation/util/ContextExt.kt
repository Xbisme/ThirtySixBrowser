package com.raumanian.thirtysix.browser.presentation.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * The [Activity] behind a composable's context, unwrapping `ContextWrapper`s (themes, locale
 * wrappers), or `null` when there is none — for example in a preview. Shared by onboarding
 * (Spec 018) and the external-tab Back (Spec 020 FR-006).
 */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
