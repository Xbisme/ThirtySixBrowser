package com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding

import androidx.lifecycle.SavedStateHandle
import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.usecase.SetAppLanguageUseCase
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.testing.MainDispatcherRule
import com.raumanian.thirtysix.browser.core.testing.fake.FakeAppLanguageController
import com.raumanian.thirtysix.browser.core.testing.fake.FakeSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private object TestDispatchers : DispatcherProvider {
        override val main: CoroutineDispatcher get() = Dispatchers.Main
        override val io: CoroutineDispatcher get() = Dispatchers.Main
        override val default: CoroutineDispatcher get() = Dispatchers.Main
        override val unconfined: CoroutineDispatcher get() = Dispatchers.Main
    }

    private fun viewModel(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        language: FakeAppLanguageController = FakeAppLanguageController(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ) = OnboardingViewModel(
        savedStateHandle = savedState,
        settings = settings,
        languages = language,
        setAppLanguage = SetAppLanguageUseCase(language, TestDispatchers),
    )

    // ──────── Navigation ────────

    @Test
    fun `starts on the first slide`() = runTest {
        assertEquals(OnboardingSlides.FIRST, viewModel().uiState.value.currentSlide)
    }

    @Test
    fun `forward advances one slide at a time`() = runTest {
        val vm = viewModel()

        vm.onAction(OnboardingAction.Forward)
        assertEquals(OnboardingSlides.LANGUAGE, vm.uiState.value.currentSlide)
        vm.onAction(OnboardingAction.Forward)
        assertEquals(OnboardingSlides.THEME, vm.uiState.value.currentSlide)
        vm.onAction(OnboardingAction.Forward)
        assertEquals(OnboardingSlides.SEARCH_ENGINE, vm.uiState.value.currentSlide)
        assertTrue(vm.uiState.value.isLastSlide)
    }

    @Test
    fun `back moves to the previous slide`() = runTest {
        val vm = viewModel()
        vm.onAction(OnboardingAction.Forward)
        assertFalse(vm.uiState.value.isFirstSlide)

        vm.onAction(OnboardingAction.Back)
        assertEquals(OnboardingSlides.FIRST, vm.uiState.value.currentSlide)
    }

    @Test
    fun `back on the first slide does not move`() = runTest {
        val vm = viewModel()

        // The screen leaves the app when the state says it is on the first slide.
        assertTrue(vm.uiState.value.isFirstSlide)
        vm.onAction(OnboardingAction.Back)
        assertEquals(OnboardingSlides.FIRST, vm.uiState.value.currentSlide)
    }

    // ──────── Which exits write the first-run flag ────────

    @Test
    fun `back on the first slide does NOT write the first-run flag`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onAction(OnboardingAction.Back)
        advanceUntilIdle()

        // Leaving this way is not consent to never being asked again.
        assertFalse(settings.current.isOnboardingCompleted)
        assertTrue(settings.writes.isEmpty())
    }

    @Test
    fun `forward on the last slide completes the flow`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)
        repeat(OnboardingSlides.LAST) { vm.onAction(OnboardingAction.Forward) }

        vm.onAction(OnboardingAction.Forward)
        advanceUntilIdle()

        assertTrue(settings.current.isOnboardingCompleted)
    }

    @Test
    fun `skip writes the first-run flag`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onAction(OnboardingAction.Skip)
        advanceUntilIdle()

        assertTrue(settings.current.isOnboardingCompleted)
    }

    @Test
    fun `skip changes no setting other than the flag`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onAction(OnboardingAction.Skip)
        advanceUntilIdle()

        // Skip records that the person was asked; the defaults stay unwritten.
        assertEquals(ThemeMode.System, settings.current.themeMode)
        assertEquals(SearchEngine.Google, settings.current.searchEngine)
        assertEquals(
            listOf(FakeSettingsRepository.CALL_SET_ONBOARDING_COMPLETED),
            settings.writes,
        )
    }

    @Test
    fun `skip works from every slide`() = runTest {
        for (slide in OnboardingSlides.FIRST..OnboardingSlides.LAST) {
            val settings = FakeSettingsRepository()
            val vm = viewModel(settings = settings)
            repeat(slide) { vm.onAction(OnboardingAction.Forward) }

            vm.onAction(OnboardingAction.Skip)
            advanceUntilIdle()

            assertTrue("skip failed from slide $slide", settings.current.isOnboardingCompleted)
        }
    }

    // ──────── Navigation writes nothing ────────

    @Test
    fun `moving between slides writes nothing to settings`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onAction(OnboardingAction.Forward)
        vm.onAction(OnboardingAction.Forward)
        vm.onAction(OnboardingAction.Back)
        advanceUntilIdle()

        // The slide index is session state, not a setting.
        assertTrue("navigation must not persist anything", settings.writes.isEmpty())
    }

    // ──────── Save-on-select ────────

    @Test
    fun `choosing a theme writes it immediately`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onAction(OnboardingAction.ThemeSelected(ThemeMode.Dark))
        advanceUntilIdle()

        assertEquals(ThemeMode.Dark, settings.current.themeMode)
    }

    @Test
    fun `choosing a search engine writes it immediately`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onAction(OnboardingAction.SearchEngineSelected(SearchEngine.DuckDuckGo))
        advanceUntilIdle()

        assertEquals(SearchEngine.DuckDuckGo, settings.current.searchEngine)
    }

    @Test
    fun `state reflects values read back from the settings snapshot`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onAction(OnboardingAction.ThemeSelected(ThemeMode.Dark))
        advanceUntilIdle()

        // The state shows what is stored, not a locally held copy.
        assertEquals(ThemeMode.Dark, vm.uiState.value.themeMode)
    }

    // ──────── Language ────────

    @Test
    fun `choosing a different language applies it`() = runTest {
        val language = FakeAppLanguageController()
        val vm = viewModel(language = language)

        vm.onAction(OnboardingAction.LanguageSelected(AppLanguage.Vietnamese))
        advanceUntilIdle()

        assertEquals(AppLanguage.Vietnamese, language.current())
    }

    @Test
    fun `choosing the language already in effect does not reapply it`() = runTest {
        val language = FakeAppLanguageController()
        val vm = viewModel(language = language)
        vm.onAction(OnboardingAction.LanguageSelected(AppLanguage.Vietnamese))
        advanceUntilIdle()
        val appliedBefore = language.applied.size

        vm.onAction(OnboardingAction.LanguageSelected(AppLanguage.Vietnamese))
        advanceUntilIdle()

        // A no-op must not cost an activity recreation and its black flash.
        assertEquals(appliedBefore, language.applied.size)
    }

    // ──────── Position survives recreation ────────

    @Test
    fun `slide position is restored from saved state`() = runTest {
        val savedState = SavedStateHandle(mapOf("onboarding_current_slide" to OnboardingSlides.THEME))

        val vm = viewModel(savedState = savedState)

        // This is what returns the person to their slide after a language change.
        assertEquals(OnboardingSlides.THEME, vm.uiState.value.currentSlide)
    }

    @Test
    fun `moving slides records the position into saved state`() = runTest {
        val savedState = SavedStateHandle()
        val vm = viewModel(savedState = savedState)

        vm.onAction(OnboardingAction.Forward)

        assertEquals(OnboardingSlides.LANGUAGE, savedState.get<Int>("onboarding_current_slide"))
    }

    @Test
    fun `an out-of-range restored position is clamped`() = runTest {
        val tooHigh = SavedStateHandle(mapOf("onboarding_current_slide" to 99))
        val negative = SavedStateHandle(mapOf("onboarding_current_slide" to -5))

        // A corrupted or stale bundle must not render a slide that does not exist.
        assertEquals(OnboardingSlides.LAST, viewModel(savedState = tooHigh).uiState.value.currentSlide)
        assertEquals(OnboardingSlides.FIRST, viewModel(savedState = negative).uiState.value.currentSlide)
    }
}
