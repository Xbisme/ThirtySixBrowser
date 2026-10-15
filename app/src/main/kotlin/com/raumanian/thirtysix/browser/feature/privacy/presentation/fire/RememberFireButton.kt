package com.raumanian.thirtysix.browser.feature.privacy.presentation.fire

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.core.domain.model.FireState
import com.raumanian.thirtysix.browser.core.presentation.util.FireButton

/**
 * A Fire button whose tap opens the confirmation; disabled while a run is in progress.
 * [onConfirm] runs before Fire starts, so the caller can return to the browser, whose overlay shows the run.
 */
@Composable
fun rememberFireButton(
    onConfirm: () -> Unit,
    fire: FireViewModel = hiltViewModel(),
): FireButton {
    val fireState by fire.state.collectAsStateWithLifecycle()
    var confirmOpen by rememberSaveable { mutableStateOf(false) }
    if (confirmOpen) {
        FireConfirmDialog(
            onConfirm = {
                confirmOpen = false
                onConfirm()
                fire.fire()
            },
            onDismiss = { confirmOpen = false },
        )
    }
    return FireButton(enabled = fireState == FireState.Idle, onClick = { confirmOpen = true })
}
