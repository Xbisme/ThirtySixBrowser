package com.raumanian.thirtysix.browser.feature.privacy.domain.usecase

import com.raumanian.thirtysix.browser.core.domain.model.FireResult
import com.raumanian.thirtysix.browser.core.domain.model.FireState
import com.raumanian.thirtysix.browser.core.domain.model.FireStep
import com.raumanian.thirtysix.browser.core.domain.util.ApplicationScope
import com.raumanian.thirtysix.browser.feature.privacy.domain.repository.FireMarkerRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The one process-wide entry point to Fire. */
interface FireCoordinator {
    val state: StateFlow<FireState>

    /** Starts a run unless one is already running; a second trigger while running is ignored. */
    fun fire()

    /** Before the first tab is shown: finishes an interrupted run, if the marker says there was one. */
    suspend fun resumeIfInterrupted()

    /** The UI has finished showing [FireState.Finished]; back to [FireState.Idle]. */
    fun acknowledge()
}

/**
 * Runs Fire in the application scope so it outlives the screen that started it, behind a durable
 * marker written before the first destructive step and cleared after the last one.
 */
@Singleton
class FireCoordinatorImpl @Inject constructor(
    private val runFire: RunFireUseCase,
    private val marker: FireMarkerRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) : FireCoordinator {

    private val mutableState = MutableStateFlow<FireState>(FireState.Idle)
    override val state: StateFlow<FireState> = mutableState.asStateFlow()

    private val runLock = Mutex()

    override fun fire() {
        var started = false
        mutableState.update { current ->
            if (current == FireState.Idle) {
                started = true
                FireState.Running
            } else {
                current
            }
        }
        if (!started) return
        scope.launch {
            val result = runLock.withLock { runMarked() }
            mutableState.value = FireState.Finished(result)
        }
    }

    override suspend fun resumeIfInterrupted() {
        if (!marker.isFireInProgress()) return
        runLock.withLock { runMarked() }
    }

    override fun acknowledge() {
        mutableState.update { current -> if (current is FireState.Finished) FireState.Idle else current }
    }

    // Any throw ends as a failed run with the marker cleared, so a resumed run cannot block every launch.
    private suspend fun runMarked(): FireResult {
        marker.setFireInProgress(true)
        val result = runCatching { runFire() }.getOrElse { error ->
            if (error is CancellationException && !currentCoroutineContext().isActive) throw error
            FireResult(failedSteps = FireStep.entries.toSet(), siteDataPath = null)
        }
        marker.setFireInProgress(false)
        return result
    }
}
