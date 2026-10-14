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
 * Spec 014 — also the home of the once-per-process history retention sweep.
 *
 * The sweep lives here rather than in a ViewModel because it must bound the table
 * regardless of which screen the user opens; `HistoryViewModel` only exists while the
 * History screen is on the back stack, so pruning there would never run for a user who
 * simply never opens History — precisely the user whose table grows unchecked.
 *
 * Failures are swallowed: a retention sweep that cannot run is not a reason to block
 * app start, and the next launch retries.
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
        // Spec 023 research R10: parse the bundled protection lists off the main thread now,
        // so the first page load rarely has to wait for them. Never awaited here.
        protectionData.preload()
        // Spec 024 research R6: the page-start scripts, read once the same way.
        privacyScripts.preload()
    }

    private companion object {
        const val TAG = "ThirtySixApplication"
    }
}
