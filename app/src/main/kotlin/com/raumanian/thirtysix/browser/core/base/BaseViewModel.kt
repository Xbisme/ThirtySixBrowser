package com.raumanian.thirtysix.browser.core.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raumanian.thirtysix.browser.core.error.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class BaseViewModel : ViewModel() {
    protected fun launchSafely(
        onError: (AppError) -> Unit = {},
        block: suspend CoroutineScope.() -> Unit,
    ): Job =
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (
                // Intentional generic catch: this is the safety-net for ViewModel
                // coroutines per FR-009/FR-009a. AppError.from(...) classifies the
                // throwable into Network/Database/Unknown so callers see a typed error.
                @Suppress("TooGenericExceptionCaught")
                e: Throwable,
            ) {
                onError(AppError.from(e))
            }
        }
}
