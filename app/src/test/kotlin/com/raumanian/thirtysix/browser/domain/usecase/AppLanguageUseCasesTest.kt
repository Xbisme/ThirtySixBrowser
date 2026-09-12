package com.raumanian.thirtysix.browser.domain.usecase

import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.testdoubles.FakeAppLanguageController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 016 T052 — [GetAppLanguageUseCase] and [SetAppLanguageUseCase].
 */
class AppLanguageUseCasesTest {

    @Test
    fun `get returns what the platform holds`() {
        val controller = FakeAppLanguageController(currentLanguage = AppLanguage.Korean)

        assertEquals(AppLanguage.Korean, GetAppLanguageUseCase(controller)())
    }

    @Test
    fun `setting the language already held makes no platform call`() = runTest {
        val controller = FakeAppLanguageController(currentLanguage = AppLanguage.German)

        val accepted = setAppLanguage(controller)(AppLanguage.German)

        assertTrue(accepted)
        assertTrue("FR-006", controller.applied.isEmpty())
    }

    @Test
    fun `setting a different language applies it exactly once`() = runTest {
        val controller = FakeAppLanguageController(currentLanguage = AppLanguage.English)

        val accepted = setAppLanguage(controller)(AppLanguage.Vietnamese)

        assertTrue(accepted)
        assertEquals(listOf(AppLanguage.Vietnamese), controller.applied)
    }

    @Test
    fun `a rejected change is reported as false`() = runTest {
        val controller = FakeAppLanguageController(applyResult = false)

        val accepted = setAppLanguage(controller)(AppLanguage.French)

        assertFalse(accepted)
        assertEquals(listOf(AppLanguage.French), controller.applied)
    }

    @Test
    fun `follow system to a specific language and back each apply once`() = runTest {
        val controller = FakeAppLanguageController(currentLanguage = AppLanguage.FollowSystem)
        val useCase = setAppLanguage(controller)

        useCase(AppLanguage.Japanese)
        useCase(AppLanguage.FollowSystem)

        assertEquals(listOf(AppLanguage.Japanese, AppLanguage.FollowSystem), controller.applied)
    }

    private fun TestScope.setAppLanguage(controller: FakeAppLanguageController): SetAppLanguageUseCase =
        SetAppLanguageUseCase(controller, TestDispatchers(StandardTestDispatcher(testScheduler)))

    /** Routes every dispatcher onto the test scheduler. */
    private class TestDispatchers(private val d: CoroutineDispatcher) : DispatcherProvider {
        override val main: CoroutineDispatcher get() = d
        override val io: CoroutineDispatcher get() = d
        override val default: CoroutineDispatcher get() = d
        override val unconfined: CoroutineDispatcher get() = d
    }
}
