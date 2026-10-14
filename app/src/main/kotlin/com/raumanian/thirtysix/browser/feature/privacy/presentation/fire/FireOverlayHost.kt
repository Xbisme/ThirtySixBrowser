package com.raumanian.thirtysix.browser.feature.privacy.presentation.fire

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** The full-screen Fire animation while a run is in progress. */
@Composable
fun FireOverlayHost(fire: FireViewModel = hiltViewModel()) {
    val fireState by fire.state.collectAsStateWithLifecycle()
    FireOverlay(state = fireState, onFinish = fire::acknowledge)
}
