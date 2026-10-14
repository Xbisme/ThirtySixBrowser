package com.raumanian.thirtysix.browser

import android.app.Application
import android.util.Log
import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptBundles
import com.raumanian.thirtysix.browser.core.domain.repository.ProtectionDataRepository
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.feature.history.domain.usecase.PruneOldHistoryUseCase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs the history retention sweep once per process: a user who never opens History would
 * never be pruned from a ViewModel. A failed sweep does not block start; the next launch retries.
 */
@HiltAndroidApp
class ThirtySixApplication : Application() {

    @Inject
    lateinit var pruneOldHistory: PruneOldHistoryUseCase

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var protectionData: ProtectionDataRepository

    @Inject
    lateinit var privacyScripts: PrivacyScriptBundles

    private val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + dispatchers.io)
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate: scheduling history retention sweep")
        applicationScope.launch {
            runCatching { pruneOldHistory() }
                .onSuccess { removed -> Log.i(TAG, "history retention sweep removed $removed row(s)") }
                .onFailure { cause -> Log.w(TAG, "history retention sweep failed", cause) }
        }
        // Parsed off the main thread now so the first page load rarely waits; never awaited here.
        protectionData.preload()
        privacyScripts.preload()
    }

    private companion object {
        const val TAG = "ThirtySixApplication"
    }
}
