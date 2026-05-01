package com.raumanian.thirtysix.browser.core.dispatcher

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}
