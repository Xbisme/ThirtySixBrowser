package com.raumanian.thirtysix.browser.domain.fire

import com.raumanian.thirtysix.browser.core.di.ApplicationScope
import com.raumanian.thirtysix.browser.domain.model.FireState
import com.raumanian.thirtysix.browser.domain.repository.FireMarkerRepository
import com.raumanian.thirtysix.browser.domain.usecase.RunFireUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Spec 024 contracts C12 — the one process-wide entry point to Fire (INV-1, INV-5). */
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
 * Spec 024 research R10 — runs Fire in the application scope, so it finishes even when the screen
 * that started it goes away, behind a durable marker:
 *  - the marker is written, and the write awaited, before the first destructive step (INV-1);
 *  - it is cleared only after the last step, so a run stopped part-way is finished on the next start.
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

    private suspend fun runMarked() = run {
        marker.setFireInProgress(true)
        runFire().also { marker.setFireInProgress(false) }
    }
}
