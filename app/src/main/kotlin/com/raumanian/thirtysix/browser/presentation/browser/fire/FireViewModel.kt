package com.raumanian.thirtysix.browser.presentation.browser.fire

import androidx.lifecycle.ViewModel
import com.raumanian.thirtysix.browser.core.domain.model.FireState
import com.raumanian.thirtysix.browser.domain.fire.FireCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Spec 024 US1 — the Fire action for a screen. A thin pass-through: the run itself belongs to the
 * process-wide [FireCoordinator], so it finishes even if the screen that started it goes away, and
 * the browser and Tabs screens see the same state.
 */
@HiltViewModel
class FireViewModel @Inject constructor(
    private val coordinator: FireCoordinator,
) : ViewModel() {
    val state: StateFlow<FireState> = coordinator.state

    fun fire() = coordinator.fire()

    fun acknowledge() = coordinator.acknowledge()
}
