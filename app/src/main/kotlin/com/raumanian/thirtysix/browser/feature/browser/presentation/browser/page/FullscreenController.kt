package com.raumanian.thirtysix.browser.feature.browser.presentation.browser.page

import android.view.View
import android.webkit.WebChromeClient.CustomViewCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The page's fullscreen view while shown. The engine is told the view is gone exactly once,
 * whichever side ends fullscreen first: the page, Back or a tab switch. Main thread only.
 */
class FullscreenController {
    private val _view = MutableStateFlow<View?>(null)
    private var callback: CustomViewCallback? = null

    val view: StateFlow<View?> = _view.asStateFlow()

    /** The page asked for fullscreen. A second request while one is shown is refused. */
    fun enter(view: View, callback: CustomViewCallback) {
        if (_view.value != null) {
            callback.onCustomViewHidden()
            return
        }
        this.callback = callback
        _view.value = view
    }

    /** The browser ends fullscreen (Back, tab switch): the page is told. */
    fun exit() {
        val pending = callback ?: return
        callback = null
        _view.value = null
        pending.onCustomViewHidden()
    }

    /** The page ended fullscreen itself: nothing to tell it. */
    fun onHiddenByPage() {
        callback = null
        _view.value = null
    }
}
