package com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding

import androidx.lifecycle.SavedStateHandle
import com.raumanian.thirtysix.browser.core.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.core.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.usecase.GetAppLanguageUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SetAppLanguageUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SetSearchEngineUseCase
import com.raumanian.thirtysix.browser.core.domain.usecase.SetThemeModeUseCase
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.testing.fake.FakeAppLanguageController
import com.raumanian.thirtysix.browser.core.testing.fake.FakeSettingsRepository
import com.raumanian.thirtysix.browser.feature.onboarding.domain.usecase.SetOnboardingCompletedUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

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
        observeUserSettings = ObserveUserSettingsUseCase(settings),
        getAppLanguage = GetAppLanguageUseCase(language),
        setThemeMode = SetThemeModeUseCase(settings),
        setSearchEngine = SetSearchEngineUseCase(settings),
        setAppLanguage = SetAppLanguageUseCase(language, TestDispatchers),
        setOnboardingCompleted = SetOnboardingCompletedUseCase(settings),
    )

    // ──────── Navigation ────────

    @Test
    fun `starts on the first slide`() = runTest {
        assertEquals(OnboardingSlides.FIRST, viewModel().uiState.value.currentSlide)
    }

    @Test
    fun `forward advances one slide at a time`() = runTest {
        val vm = viewModel()

        vm.onForward()
        assertEquals(OnboardingSlides.LANGUAGE, vm.uiState.value.currentSlide)
        vm.onForward()
        assertEquals(OnboardingSlides.THEME, vm.uiState.value.currentSlide)
        vm.onForward()
        assertEquals(OnboardingSlides.SEARCH_ENGINE, vm.uiState.value.currentSlide)
        assertTrue(vm.uiState.value.isLastSlide)
    }

    @Test
    fun `back moves to the previous slide and reports true`() = runTest {
        val vm = viewModel()
        vm.onForward()

        assertTrue(vm.onBack())
        assertEquals(OnboardingSlides.FIRST, vm.uiState.value.currentSlide)
    }

    @Test
    fun `back on the first slide reports false and does not move`() = runTest {
        val vm = viewModel()

        assertFalse("caller must be told to leave the app", vm.onBack())
        assertEquals(OnboardingSlides.FIRST, vm.uiState.value.currentSlide)
    }

    // ──────── Which exits write the first-run flag ────────

    @Test
    fun `back on the first slide does NOT write the first-run flag`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onBack()
        advanceUntilIdle()

        // Leaving this way is not consent to never being asked again.
        assertFalse(settings.current.isOnboardingCompleted)
        assertTrue(settings.writes.isEmpty())
    }

    @Test
    fun `forward on the last slide completes the flow`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)
        repeat(OnboardingSlides.LAST) { vm.onForward() }

        vm.onForward()
        advanceUntilIdle()

        assertTrue(settings.current.isOnboardingCompleted)
    }

    @Test
    fun `skip writes the first-run flag`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onSkip()
        advanceUntilIdle()

        assertTrue(settings.current.isOnboardingCompleted)
    }

    @Test
    fun `skip changes no setting other than the flag`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onSkip()
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
            repeat(slide) { vm.onForward() }

            vm.onSkip()
            advanceUntilIdle()

            assertTrue("skip failed from slide $slide", settings.current.isOnboardingCompleted)
        }
    }

    // ──────── Navigation writes nothing ────────

    @Test
    fun `moving between slides writes nothing to settings`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onForward()
        vm.onForward()
        vm.onBack()
        advanceUntilIdle()

        // The slide index is session state, not a setting.
        assertTrue("navigation must not persist anything", settings.writes.isEmpty())
    }

    // ──────── Save-on-select ────────

    @Test
    fun `choosing a theme writes it immediately`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onThemeSelected(ThemeMode.Dark)
        advanceUntilIdle()

        assertEquals(ThemeMode.Dark, settings.current.themeMode)
    }

    @Test
    fun `choosing a search engine writes it immediately`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onSearchEngineSelected(SearchEngine.DuckDuckGo)
        advanceUntilIdle()

        assertEquals(SearchEngine.DuckDuckGo, settings.current.searchEngine)
    }

    @Test
    fun `state reflects values read back from the settings snapshot`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onThemeSelected(ThemeMode.Dark)
        advanceUntilIdle()

        // The state shows what is stored, not a locally held copy.
        assertEquals(ThemeMode.Dark, vm.uiState.value.themeMode)
    }

    // ──────── Language ────────

    @Test
    fun `choosing a different language applies it`() = runTest {
        val language = FakeAppLanguageController()
        val vm = viewModel(language = language)

        vm.onLanguageSelected(AppLanguage.Vietnamese)
        advanceUntilIdle()

        assertEquals(AppLanguage.Vietnamese, language.current())
    }

    @Test
    fun `choosing the language already in effect does not reapply it`() = runTest {
        val language = FakeAppLanguageController()
        val vm = viewModel(language = language)
        vm.onLanguageSelected(AppLanguage.Vietnamese)
        advanceUntilIdle()
        val appliedBefore = language.applied.size

        vm.onLanguageSelected(AppLanguage.Vietnamese)
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

        vm.onForward()

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
