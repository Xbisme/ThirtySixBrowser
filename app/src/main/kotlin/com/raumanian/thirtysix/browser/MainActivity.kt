package com.raumanian.thirtysix.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.raumanian.thirtysix.browser.core.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.core.domain.model.UserSettings
import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserInviteRepository
import com.raumanian.thirtysix.browser.core.domain.repository.SettingsRepository
import com.raumanian.thirtysix.browser.core.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.core.presentation.util.LocalIoDispatcher
import com.raumanian.thirtysix.browser.core.presentation.util.SecureWindowEffect
import com.raumanian.thirtysix.browser.feature.browser.domain.usecase.SyncDefaultBrowserInviteUseCase
import com.raumanian.thirtysix.browser.feature.browser.presentation.navigation.BROWSER_ROUTE
import com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry.IncomingIntentReader
import com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry.IncomingRequestRelay
import com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry.LaunchCountPolicy
import com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry.PendingRequestState
import com.raumanian.thirtysix.browser.feature.onboarding.presentation.navigation.ONBOARDING_ROUTE
import com.raumanian.thirtysix.browser.feature.privacy.domain.usecase.FireCoordinator
import com.raumanian.thirtysix.browser.navigation.AppNavHost
import com.raumanian.thirtysix.browser.navigation.NavArgs
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** AppCompat because `AppCompatDelegate.setApplicationLocales` needs it below Android 13. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var observeActiveTabIsIncognito: ObserveActiveTabIsIncognitoUseCase

    @Inject
    lateinit var fireCoordinator: FireCoordinator

    @Inject
    lateinit var incomingRequestRelay: IncomingRequestRelay

    @Inject
    lateinit var incomingIntentReader: IncomingIntentReader

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var defaultBrowserInvite: DefaultBrowserInviteRepository

    @Inject
    lateinit var syncDefaultBrowserInvite: SyncDefaultBrowserInviteUseCase

    // What the next onStart is coming back from, so it is not counted as a launch.
    private var stoppedForConfigChange = false
    private var returningFromDefaultBrowserScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate(): it switches to the AppCompat post-splash theme, without which
        // AppCompatActivity throws at launch. The handle is unused so the splash is never held open.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        receiveLaunchIntent(savedInstanceState)
        // A rotation recreates the activity: the stop that preceded it happened on the old
        // instance, so the new one learns it from the bundle and skips its first count.
        stoppedForConfigChange = savedInstanceState != null

        // NavHost fixes its start destination at first composition, and the settings flow starts
        // at DEFAULT (onboarding not done), so the real flag is read before setContent.
        lifecycleScope.launch {
            // A Fire run stopped part-way finishes before any tab is shown.
            fireCoordinator.resumeIfInterrupted()
            val startDestination = if (settingsRepository.currentSettings().isOnboardingCompleted) {
                BROWSER_ROUTE
            } else {
                ONBOARDING_ROUTE
            }
            renderApp(startDestination)
        }
    }

    /**
     * Recreation and reopening from recents redeliver the original launch intent, so only a fresh
     * launch reads it; otherwise an unopened request comes back from the bundle (empty relay only).
     */
    private fun receiveLaunchIntent(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            PendingRequestState.decode(
                kind = savedInstanceState.getString(NavArgs.PENDING_REQUEST_KIND),
                raw = savedInstanceState.getString(NavArgs.PENDING_REQUEST_RAW),
            )?.let(incomingRequestRelay::restoreIfEmpty)
            return
        }
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        incomingIntentReader.read(intent)?.let(incomingRequestRelay::submit)
    }

    /** The default-browser state may have changed in system settings while the app was away. */
    override fun onStart() {
        super.onStart()
        val counts = LaunchCountPolicy.shouldCount(
            stoppedForConfigChange = stoppedForConfigChange,
            returningFromOwnSystemScreen = returningFromDefaultBrowserScreen,
        )
        stoppedForConfigChange = false
        returningFromDefaultBrowserScreen = false
        lifecycleScope.launch {
            syncDefaultBrowserInvite()
            if (counts) defaultBrowserInvite.recordLaunch()
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedForConfigChange = isChangingConfigurations
    }

    fun markLeavingForDefaultBrowserScreen() {
        returningFromDefaultBrowserScreen = true
    }

    /** `singleTask`: links arriving while the app runs come here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntentReader.read(intent)?.let(incomingRequestRelay::submit)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        incomingRequestRelay.pending.value?.let { request ->
            val (kind, raw) = PendingRequestState.encode(request)
            outState.putString(NavArgs.PENDING_REQUEST_KIND, kind)
            outState.putString(NavArgs.PENDING_REQUEST_RAW, raw)
        }
    }

    private fun renderApp(startDestination: String) {
        setContent {
            // The window background theme hides the gap until the persisted settings arrive.
            val settings by settingsRepository.observeSettings()
                .collectAsStateWithLifecycle(initialValue = UserSettings.DEFAULT)
            val darkTheme = when (settings.themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> isSystemInDarkTheme()
            }

            val isIncognito by observeActiveTabIsIncognito()
                .collectAsStateWithLifecycle(initialValue = false)

            ThirtySixTheme(darkTheme = darkTheme, dynamicColor = settings.isDynamicColorEnabled) {
                SecureWindowEffect(secure = isIncognito)
                CompositionLocalProvider(LocalIoDispatcher provides dispatchers.io) {
                    AppNavHost(startDestination = startDestination)
                }
            }
        }
    }
}
