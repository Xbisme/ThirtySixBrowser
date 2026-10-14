package com.raumanian.thirtysix.browser.feature.privacy.data.datasource.local

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.raumanian.thirtysix.browser.core.domain.repository.PrivacyScriptBundles
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Spec 024 — [PrivacyScriptBundles] read from `assets/privacy/` on the IO dispatcher. */
@Singleton
class PrivacyScriptBundlesImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    dispatchers: DispatcherProvider,
) : PrivacyScriptBundles {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val started = AtomicBoolean(false)
    private val state = MutableStateFlow<List<String>?>(null)

    override val loaded: StateFlow<List<String>?> = state.asStateFlow()

    override fun preload() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            state.value = try {
                INSTALL_ORDER.map { path -> context.assets.open(path).bufferedReader().use { it.readText() } }
            } catch (e: IOException) {
                // The protections stay off rather than breaking pages (FR-022's posture).
                Log.e(TAG, "privacy scripts failed to load", e)
                emptyList()
            }
            Log.i(TAG, "privacy scripts loaded in ${SystemClock.elapsedRealtime() - startedAt} ms")
        }
    }

    private companion object {
        const val TAG = "PrivacyScripts"
        val INSTALL_ORDER = listOf(AssetPaths.PRIVACY_FINGERPRINT_SCRIPT, AssetPaths.PRIVACY_CONSENT_SCRIPT)
    }
}
