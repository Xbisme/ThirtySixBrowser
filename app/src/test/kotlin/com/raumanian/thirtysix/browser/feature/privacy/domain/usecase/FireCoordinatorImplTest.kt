package com.raumanian.thirtysix.browser.feature.privacy.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.FireState
import com.raumanian.thirtysix.browser.core.domain.model.FireStep
import com.raumanian.thirtysix.browser.core.domain.repository.HttpsUpgradeFailures
import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptSession
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.domain.util.OneTimeGrantStore
import com.raumanian.thirtysix.browser.core.domain.util.Result
import com.raumanian.thirtysix.browser.core.testing.fake.FakeBookmarkRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeCookieJarSnapshotManager
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFaviconCache
import com.raumanian.thirtysix.browser.core.testing.fake.FakeFireproofSiteRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeHistoryRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeIncognitoTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeScreenshotCache
import com.raumanian.thirtysix.browser.core.testing.fake.FakeSitePermissionRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeTabRepository
import com.raumanian.thirtysix.browser.core.testing.fake.FakeWebDataCleaner
import com.raumanian.thirtysix.browser.feature.privacy.domain.repository.FireMarkerRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The durable marker, the single-run gate and resuming an interrupted run. */
class FireCoordinatorImplTest {

    private val marker = FakeMarker()
    private val markerDuringSteps = mutableListOf<Boolean>()
    private var gate: CompletableDeferred<Unit>? = null
    private var runs = 0

    private val observer = object : FireStepObserver {
        override suspend fun beforeStep(step: FireStep) {
            markerDuringSteps += marker.value
            if (step == FireStep.CollectHosts) {
                runs++
                gate?.await()
            }
        }
    }

    private fun coordinator(scope: TestScope) = FireCoordinatorImpl(runFire(), marker, scope)

    @Test
    fun `the marker is set before the first step and cleared after the last`() = runTest(StandardTestDispatcher()) {
        val coordinator = coordinator(this)

        coordinator.fire()
        advanceUntilIdle()

        assertTrue(markerDuringSteps.isNotEmpty())
        assertTrue("INV-1", markerDuringSteps.all { it })
        assertFalse(marker.value)
        assertEquals(listOf(true, false), marker.writes)
        assertTrue(coordinator.state.value is FireState.Finished)
    }

    @Test
    fun `a second fire while running is ignored`() = runTest(StandardTestDispatcher()) {
        val coordinator = coordinator(this)
        gate = CompletableDeferred()

        coordinator.fire()
        advanceUntilIdle()
        assertEquals(FireState.Running, coordinator.state.value)
        coordinator.fire()
        gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals("INV-5", 1, runs)
    }

    @Test
    fun `acknowledge returns a finished run to idle, and only a finished one`() = runTest(StandardTestDispatcher()) {
        val coordinator = coordinator(this)
        coordinator.acknowledge()
        assertEquals(FireState.Idle, coordinator.state.value)

        coordinator.fire()
        advanceUntilIdle()
        coordinator.acknowledge()

        assertEquals(FireState.Idle, coordinator.state.value)
    }

    @Test
    fun `resume runs only when the marker says a run was interrupted`() = runTest(StandardTestDispatcher()) {
        val coordinator = coordinator(this)

        coordinator.resumeIfInterrupted()
        assertEquals(0, runs)

        marker.value = true
        coordinator.resumeIfInterrupted()
        assertEquals(1, runs)
        assertFalse(marker.value)
        assertEquals(FireState.Idle, coordinator.state.value)
    }

    private fun runFire() = RunFireUseCase(
        FakeFireproofSiteRepository(),
        FakeHistoryRepository(),
        FakeTabRepository(),
        FakeIncognitoTabRepository(),
        FakeSitePermissionRepository(),
        OneTimeGrantStore(),
        FakeWebDataCleaner(),
        FakeCookieJarSnapshotManager(),
        FakeFaviconCache(),
        FakeScreenshotCache(),
        FakeBookmarkRepository(),
        object : HttpsUpgradeFailures {
            override fun markFailed(host: String) = Unit
            override fun snapshot(): Set<String> = emptySet()
            override fun clear() = Unit
        },
        object : PrivacyScriptSession {
            override val secret: StateFlow<String> = MutableStateFlow("00")
            override fun regenerate() = Unit
        },
        observer,
        object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val unconfined = Dispatchers.Unconfined
        },
    )

    private class FakeMarker : FireMarkerRepository {
        var value = false
        val writes = mutableListOf<Boolean>()
        override suspend fun isFireInProgress(): Boolean = value
        override suspend fun setFireInProgress(value: Boolean): Result<Unit> {
            writes += value
            this.value = value
            return Result.Success(Unit)
        }
    }
}
