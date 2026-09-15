package com.raumanian.thirtysix.browser.presentation.onboarding

import androidx.lifecycle.SavedStateHandle
import com.raumanian.thirtysix.browser.core.constants.OnboardingSlides
import com.raumanian.thirtysix.browser.core.dispatcher.DispatcherProvider
import com.raumanian.thirtysix.browser.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.usecase.GetAppLanguageUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetAppLanguageUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetOnboardingCompletedUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetSearchEngineUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetThemeModeUseCase
import com.raumanian.thirtysix.browser.testdoubles.FakeAppLanguageController
import com.raumanian.thirtysix.browser.testdoubles.FakeSettingsRepository
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

/**
 * Spec 018 — the onboarding view model.
 *
 * Covers the logic a JVM test can actually observe: slide movement, the last-slide boundary,
 * WHICH exits write the first-run flag, and that navigation writes no settings. The three
 * hardest properties of this feature are all pinned here:
 *
 *  - forward on the last slide completes rather than advancing (FR-002b)
 *  - back on the first slide neither moves nor completes (FR-002a, FR-012)
 *  - skip writes the flag and nothing else (FR-011, INV-7)
 */
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

    // ──────── Which exits write the first-run flag (INV-6) ────────

    @Test
    fun `back on the first slide does NOT write the first-run flag`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onBack()
        advanceUntilIdle()

        // FR-012: leaving this way is not consent to never being asked again.
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

        // A4 / INV-7: skip records that the user was asked, not a preference they never
        // expressed. The defaults must be left as defaults, not written explicitly.
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

    // ──────── Navigation writes nothing (INV-4) ────────

    @Test
    fun `moving between slides writes nothing to settings`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = viewModel(settings = settings)

        vm.onForward()
        vm.onForward()
        vm.onBack()
        advanceUntilIdle()

        // INV-4: the slide index is session state, not a setting. A user who force-quits
        // mid-flow starts over rather than resuming where they left off.
        assertTrue("navigation must not persist anything", settings.writes.isEmpty())
    }

    // ──────── Save-on-select (FR-008) ────────

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

        // INV-1: the state shows what is stored, not a locally held copy.
        assertEquals(ThemeMode.Dark, vm.uiState.value.themeMode)
    }

    // ──────── Language (FR-015a) ────────

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

        // FR-015a: a no-op must not cost an activity recreation and its black flash.
        assertEquals(appliedBefore, language.applied.size)
    }

    // ──────── Position survives recreation (INV-2, INV-3) ────────

    @Test
    fun `slide position is restored from saved state`() = runTest {
        val savedState = SavedStateHandle(mapOf("onboarding_current_slide" to OnboardingSlides.THEME))

        val vm = viewModel(savedState = savedState)

        // INV-2: this is what makes a language change return the user to their slide rather
        // than to the first one.
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

        // INV-3: a corrupted or stale bundle must not render a slide that does not exist.
        assertEquals(OnboardingSlides.LAST, viewModel(savedState = tooHigh).uiState.value.currentSlide)
        assertEquals(OnboardingSlides.FIRST, viewModel(savedState = negative).uiState.value.currentSlide)
    }
}
