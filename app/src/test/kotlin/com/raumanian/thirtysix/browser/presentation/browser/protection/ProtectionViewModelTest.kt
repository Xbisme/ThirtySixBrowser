package com.raumanian.thirtysix.browser.presentation.browser.protection

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptBundles
import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptSession
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveFireproofSitesUseCase
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFireproofSiteRepository
import com.raumanian.thirtysix.browser.domain.model.PageProtectionStatus
import com.raumanian.thirtysix.browser.domain.usecase.SetSiteFireproofUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SetSiteProtectionUseCase
import com.raumanian.thirtysix.browser.presentation.browser.privacy.PrivacyScriptInputs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 023 T049 — the hub's view of the active WebView and the per-site switch. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ProtectionViewModelTest {
    private val test = ProtectionTestEngine()
    private val fireproof = FakeFireproofSiteRepository()
    private lateinit var viewModel: ProtectionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ProtectionViewModel(
            test.engine,
            SetSiteProtectionUseCase(test.exceptions, test.incognito),
            ObserveFireproofSitesUseCase(fireproof),
            SetSiteFireproofUseCase(fireproof),
            PrivacyScriptInputs(NoSession, NoBundles, test.exceptions, test.incognito),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `status follows the attached monitor and survives a late detach of the old one`() = runTest {
        val collected = mutableListOf<PageProtectionStatus>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.status.collect { collected += it } }
        val old = test.engine.newMonitor(isIncognito = false)
        val current = test.engine.newMonitor(isIncognito = false)

        viewModel.hooks.attach(old)
        viewModel.hooks.attach(current)
        viewModel.hooks.detach(old)
        current.onMainFrameRequest("https://news.com/")

        assertTrue(viewModel.status.value.isWebPage)
        assertEquals("news.com", viewModel.status.value.site)
        job.cancel()
    }

    @Test
    fun `switching off in a normal tab saves the site and asks for one reload`() = runTest {
        val monitor = test.engine.newMonitor(isIncognito = false).also { viewModel.hooks.attach(it) }
        monitor.onMainFrameRequest("https://www.shop.com/cart")
        val reload = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.reloadEvents.first() }

        viewModel.onProtectionSwitched(false)

        assertEquals(setOf("shop.com"), test.exceptions.currentSites())
        assertTrue(reload.isCompleted)
    }

    @Test
    fun `switching off in an incognito tab stays in memory`() = runTest {
        val monitor = test.engine.newMonitor(isIncognito = true).also { viewModel.hooks.attach(it) }
        monitor.onMainFrameRequest("https://shop.com/")

        viewModel.onProtectionSwitched(false)

        assertTrue(test.incognito.contains("shop.com"))
        assertTrue(test.exceptions.currentSites().isEmpty())
        viewModel.onProtectionSwitched(true)
        assertFalse(test.incognito.contains("shop.com"))
    }

    private object NoSession : PrivacyScriptSession {
        override val secret: StateFlow<String> = MutableStateFlow("00")
        override fun regenerate() = Unit
    }

    private object NoBundles : PrivacyScriptBundles {
        override val loaded: StateFlow<List<String>?> = MutableStateFlow(emptyList())
        override fun preload() = Unit
    }
}
