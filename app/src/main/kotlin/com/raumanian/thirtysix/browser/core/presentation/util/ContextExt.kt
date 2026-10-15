package com.raumanian.thirtysix.browser.core.presentation.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * The [Activity] behind a composable's context, unwrapping `ContextWrapper`s (themes, locale
 * wrappers), or `null` when there is none — for example in a preview.
 */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
