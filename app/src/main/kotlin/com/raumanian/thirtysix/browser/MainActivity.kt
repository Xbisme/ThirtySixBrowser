package com.raumanian.thirtysix.browser

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import com.raumanian.thirtysix.browser.domain.usecase.GetUserSettingsUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.presentation.navigation.AppDestination
import com.raumanian.thirtysix.browser.presentation.navigation.AppNavGraph
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.presentation.util.SecureWindowEffect
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
            val startDestination = if (getUserSettings().isOnboardingCompleted) {
                AppDestination.Browser.route
            } else {
                AppDestination.Onboarding.route
            }
            renderApp(startDestination)
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
                AppNavGraph(startDestination = startDestination)
            }
        }
    }
}
