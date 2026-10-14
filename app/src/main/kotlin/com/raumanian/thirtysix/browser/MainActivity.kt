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
import com.raumanian.thirtysix.browser.core.constants.NavArgs
import com.raumanian.thirtysix.browser.core.domain.util.DispatcherProvider
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.core.presentation.util.LocalIoDispatcher
import com.raumanian.thirtysix.browser.core.presentation.util.SecureWindowEffect
import com.raumanian.thirtysix.browser.domain.fire.FireCoordinator
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import com.raumanian.thirtysix.browser.domain.usecase.GetUserSettingsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.RecordAppLaunchUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SyncDefaultBrowserInviteUseCase
import com.raumanian.thirtysix.browser.presentation.entry.IncomingIntentReader
import com.raumanian.thirtysix.browser.presentation.entry.IncomingRequestRelay
import com.raumanian.thirtysix.browser.presentation.entry.LaunchCountPolicy
import com.raumanian.thirtysix.browser.presentation.entry.PendingRequestState
import com.raumanian.thirtysix.browser.presentation.navigation.AppDestination
import com.raumanian.thirtysix.browser.presentation.navigation.AppNavGraph
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Spec 016 research R2 — an [AppCompatActivity] rather than a plain `ComponentActivity`: below
 * Android 13, `AppCompatDelegate.setApplicationLocales` only takes effect on an AppCompat
 * activity. It is still a `ComponentActivity` underneath, so `setContent`, `enableEdgeToEdge`,
 * Hilt and Spec 012's window flag behave exactly as before. The app never calls
 * `AppCompatDelegate.setDefaultNightMode`; the in-app theme stays a Compose concern.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject
    lateinit var observeUserSettings: ObserveUserSettingsUseCase

    @Inject
    lateinit var observeActiveTabIsIncognito: ObserveActiveTabIsIncognitoUseCase

    /**
     * Spec 018 — the ONE-SHOT read behind the start-up decision. Deliberately not the
     * observing use case above: see the block in [onCreate].
     */
    @Inject
    lateinit var getUserSettings: GetUserSettingsUseCase

    /** Spec 024 FR-007 — finishes an interrupted Fire run before the first tab is shown. */
    @Inject
    lateinit var fireCoordinator: FireCoordinator

    /** Spec 020 — hand-off of links, shares and searches from other apps to the UI (research R5). */
    @Inject
    lateinit var incomingRequestRelay: IncomingRequestRelay

    @Inject
    lateinit var incomingIntentReader: IncomingIntentReader

    @Inject
    lateinit var dispatchers: DispatcherProvider

    /** Spec 020 FR-024a — counts launches toward the Home invitation's threshold. */
    @Inject
    lateinit var recordAppLaunch: RecordAppLaunchUseCase

    /** Spec 020 FR-026 — retires the invitation once the app is seen as the default browser. */
    @Inject
    lateinit var syncDefaultBrowserInvite: SyncDefaultBrowserInviteUseCase

    // Spec 020 research R9 — what the next onStart is coming back from (LaunchCountPolicy).
    private var stoppedForConfigChange = false
    private var returningFromDefaultBrowserScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Spec 017 — MUST be the first statement, before super.onCreate().
        //
        // The activity is declared with Theme.ThirtySix.Splash, whose parent descends
        // from android:Theme.DeviceDefault rather than AppCompat (deliberately — see
        // res/values/themes.xml). installSplashScreen() resolves that theme's
        // postSplashScreenTheme attribute and calls setTheme(Theme.ThirtySix) here, and
        // AppCompatActivity.onCreate reads the theme while building its delegate. Move
        // this call below super.onCreate() and the activity stays on a non-AppCompat
        // theme, which throws at launch (research.md R3, contracts Contract 2, INV-11).
        //
        // The returned handle is discarded on purpose: setKeepOnScreenCondition and
        // setOnExitAnimationListener are its only uses, and both are forbidden here.
        // The launch screen must end at the first drawable frame, never be held open
        // for an animation or a disk read (FR-008, INV-12, INV-13).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        receiveLaunchIntent(savedInstanceState)
        // A rotation recreates the activity: the stop that preceded it happened on the old
        // instance, so the new one learns it from the bundle and skips its first count.
        stoppedForConfigChange = savedInstanceState != null

        // Spec 018 T008 — resolve the start destination BEFORE composing anything.
        //
        // The obvious implementation is to read `isOnboardingCompleted` from the settings flow
        // inside setContent, alongside the theme below. That is WRONG, and wrong in a way that
        // does not look like a flicker: collectAsStateWithLifecycle starts at
        // UserSettings.DEFAULT, whose isOnboardingCompleted is `false`, and NavHost captures
        // its startDestination at FIRST COMPOSITION. Routing on that first emission makes
        // Onboarding the graph's actual start route on every launch — including for a user who
        // finished it months ago (research.md R2, data-model.md INV-5).
        //
        // So the flag is read once, off the main thread, and setContent runs only once the real
        // value is in hand. The launch screen covers the gap, which is a DataStore read.
        //
        // ⚠️ NOT via setKeepOnScreenCondition: Spec 017's INV-12 forbids that call outright —
        // its absence is what makes Spec 017's FR-008 ("never held open") true.
        lifecycleScope.launch {
            // Spec 024 FR-007 — a Fire run stopped part-way finishes before any tab is shown. Only an
            // interrupted run costs anything here: otherwise this is one DataStore read.
            fireCoordinator.resumeIfInterrupted()
            val startDestination = if (getUserSettings().isOnboardingCompleted) {
                AppDestination.Browser.route
            } else {
                AppDestination.Onboarding.route
            }
            renderApp(startDestination)
        }
    }

    /**
     * Spec 020 research R3 — an intent is handed to the app exactly once.
     *
     * A fresh launch carries its intent here. A rotation or a recreation after process death
     * hands back the ORIGINAL launch intent with a non-null bundle, and reopening from recents
     * after the task was cleared redelivers it flagged LAUNCHED_FROM_HISTORY: acting on either
     * would reopen the link (FR-008). Instead, a request that had not been opened yet is
     * restored from the bundle — only into an empty relay, which is what distinguishes process
     * death from a rotation (FR-019).
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

    /**
     * Spec 020 FR-024a / FR-026 — every foreground re-checks the default-browser state (which
     * may have changed in system settings) and, unless this start is only a rotation or the
     * return from a system screen the app opened, counts one launch.
     */
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
            if (counts) recordAppLaunch()
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedForConfigChange = isChangingConfigurations
    }

    /**
     * Spec 020 research R9 — called just before the app opens the system's default-browser
     * confirmation or default-apps screen, so coming back from it is not counted as a launch.
     */
    fun markLeavingForDefaultBrowserScreen() {
        returningFromDefaultBrowserScreen = true
    }

    /** Spec 020 — `singleTask`: links arriving while the app runs come here (research R2). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntentReader.read(intent)?.let(incomingRequestRelay::submit)
    }

    /** Spec 020 research R3 — keep a request that has not been opened yet across process death. */
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
            // Spec 006 FR-020: theme mode now sourced from DataStore via the
            // settings-observe use case. Initial value is UserSettings.DEFAULT
            // so first composition is non-blocking and visually correct on a
            // fresh install (SC-002). Re-emission with the persisted snapshot
            // recomposes once disk I/O completes — Spec 003's windowBackground
            // theme fix protects the cold-start gap from any visible flash.
            val settings by observeUserSettings()
                .collectAsStateWithLifecycle(initialValue = UserSettings.DEFAULT)
            val darkTheme = when (settings.themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> isSystemInDarkTheme()
            }

            // Spec 012 (FR-016 / FR-016a) — collect active-tab incognito
            // state and toggle the activity window's FLAG_SECURE accordingly.
            // Recents thumbnail is blanked + screenshots/screen-recording
            // are blocked while incognito is the active tab.
            val isIncognito by observeActiveTabIsIncognito()
                .collectAsStateWithLifecycle(initialValue = false)

            // Spec 016 FR-009 / FR-010 — the user's dynamic color choice. ThirtySixTheme itself
            // ignores it below Android 12, where dynamic color does not exist (research R9).
            ThirtySixTheme(darkTheme = darkTheme, dynamicColor = settings.isDynamicColorEnabled) {
                SecureWindowEffect(secure = isIncognito)
                CompositionLocalProvider(LocalIoDispatcher provides dispatchers.io) {
                    AppNavGraph(startDestination = startDestination)
                }
            }
        }
    }
}
