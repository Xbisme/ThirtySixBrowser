package com.raumanian.thirtysix.browser.feature.privacy.presentation.fire

import androidx.lifecycle.ViewModel
import com.raumanian.thirtysix.browser.core.domain.model.FireState
import com.raumanian.thirtysix.browser.feature.privacy.domain.usecase.FireCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin pass-through: the run belongs to the process-wide [FireCoordinator], so it finishes even if
 * this screen goes away, and the browser and Tabs screens see the same state.
 */
@HiltViewModel
class FireViewModel @Inject constructor(
    private val coordinator: FireCoordinator,
) : ViewModel() {
    val state: StateFlow<FireState> = coordinator.state

    fun onAction(action: FireAction) {
        when (action) {
            FireAction.Fire -> coordinator.fire()
            FireAction.Acknowledge -> coordinator.acknowledge()
        }
    }
}
