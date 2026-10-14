package com.raumanian.thirtysix.browser.core.presentation.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                // Safety net for ViewModel coroutines; AppError.from(...) gives callers a typed error.
                @Suppress("TooGenericExceptionCaught")
                e: Throwable,
            ) {
                onError(AppError.from(e))
            }
        }
}
