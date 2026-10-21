package com.raumanian.thirtysix.browser.feature.browser.presentation.browser

import android.content.Context
import android.view.MotionEvent
import android.webkit.WebView

/** The tab's WebView. It reports touches and overscroll, so a sideways swipe the page did not use can navigate. */
internal class BrowserPageView(context: Context) : WebView(context) {
    var swipe: SwipeNavigationHooks? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        swipe?.onTouch(this, event)
        return super.dispatchTouchEvent(event)
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        if (clampedX) swipe?.onOverscrolledX()
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
    }
}
