package com.raumanian.thirtysix.browser.presentation.browser.page

import android.app.Application
import android.view.View
import android.webkit.WebChromeClient.CustomViewCallback
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Spec 021 T037 / research R4 — fullscreen state, the engine told once, a tab switch ends it. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FullscreenControllerTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val view = View(context)

    private class CountingCallback : CustomViewCallback {
        var hidden = 0

        override fun onCustomViewHidden() {
            hidden++
        }
    }

    @Test
    fun `enter shows the view and exit tells the page once`() {
        val controller = FullscreenController()
        val callback = CountingCallback()

        controller.enter(view, callback)
        assertSame(view, controller.view.value)

        controller.exit()
        controller.exit()
        assertNull(controller.view.value)
        assertEquals(1, callback.hidden)
    }

    @Test
    fun `the page leaving by itself is not told again`() {
        val controller = FullscreenController()
        val callback = CountingCallback()
        controller.enter(view, callback)

        controller.onHiddenByPage()
        controller.exit()

        assertNull(controller.view.value)
        assertEquals(0, callback.hidden)
    }

    @Test
    fun `a second request while fullscreen is refused`() {
        val controller = FullscreenController()
        val first = CountingCallback()
        val second = CountingCallback()
        controller.enter(view, first)

        controller.enter(View(context), second)

        assertSame(view, controller.view.value)
        assertEquals(1, second.hidden)
        assertEquals(0, first.hidden)
    }

    @Test
    fun `switching tab ends fullscreen, a new page in the same tab does not`() {
        val session = PageSession(PageRequestQueue())
        val callback = CountingCallback()
        session.onTabChanged(1L, incognito = false)
        session.onShowCustomView(view, callback)

        session.onNavigationStarted()
        session.onTabChanged(1L, incognito = false)
        assertSame(view, session.fullscreen.view.value)

        session.onTabChanged(2L, incognito = false)
        assertNull(session.fullscreen.view.value)
        assertEquals(1, callback.hidden)
    }
}
