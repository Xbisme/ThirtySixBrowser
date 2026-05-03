package com.raumanian.thirtysix.browser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.domain.model.UserSettings
import com.raumanian.thirtysix.browser.domain.usecase.ObserveActiveTabIsIncognitoUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ObserveUserSettingsUseCase
import com.raumanian.thirtysix.browser.presentation.navigation.AppNavGraph
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.presentation.util.SecureWindowEffect
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var observeUserSettings: ObserveUserSettingsUseCase

    @Inject
    lateinit var observeActiveTabIsIncognito: ObserveActiveTabIsIncognitoUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

            ThirtySixTheme(darkTheme = darkTheme) {
                SecureWindowEffect(secure = isIncognito)
                AppNavGraph()
            }
        }
    }
}
