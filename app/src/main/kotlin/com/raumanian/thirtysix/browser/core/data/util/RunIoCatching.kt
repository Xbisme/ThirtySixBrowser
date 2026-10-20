package com.raumanian.thirtysix.browser.core.data.util

import com.raumanian.thirtysix.browser.core.domain.util.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A failed write is reported as [Result.Error], never thrown into the UI; cancellation still propagates. */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> runIoCatching(dispatcher: CoroutineDispatcher, block: suspend () -> T): Result<T> =
    withContext(dispatcher) {
        try {
            Result.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
