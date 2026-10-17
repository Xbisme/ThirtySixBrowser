package com.raumanian.thirtysix.browser.core.presentation.util

import android.animation.ValueAnimator
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class AnimationsEnabledTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun tearDown() {
        setDurationScale(1f)
    }

    @Test
    @Config(sdk = [25])
    fun `below Android 8 a zero animator scale in settings turns animations off`() {
        assertTrue(animationsEnabled(context))

        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)

        assertFalse(animationsEnabled(context))
    }

    @Test
    fun `from Android 8 the animator's own switch decides`() {
        assertTrue(animationsEnabled(context))

        setDurationScale(0f)

        assertFalse(animationsEnabled(context))
    }

    // The platform setter is hidden; Robolectric's ValueAnimator honours it.
    private fun setDurationScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }
}
