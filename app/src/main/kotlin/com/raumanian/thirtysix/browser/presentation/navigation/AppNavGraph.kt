@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.raumanian.thirtysix.browser.presentation.bookmarks.BookmarksScreen
import com.raumanian.thirtysix.browser.presentation.browser.BrowserScreen
import com.raumanian.thirtysix.browser.presentation.downloads.DownloadsScreen
import com.raumanian.thirtysix.browser.presentation.history.HistoryScreen
import com.raumanian.thirtysix.browser.presentation.onboarding.OnboardingScreen
import com.raumanian.thirtysix.browser.presentation.settings.SettingsScreen
import com.raumanian.thirtysix.browser.presentation.tabs.TabsScreen

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = AppDestination.Browser.route,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppDestination.Browser.route) { BrowserScreen() }
        composable(AppDestination.Tabs.route) { TabsScreen() }
        composable(AppDestination.Bookmarks.route) { BookmarksScreen() }
        composable(AppDestination.History.route) { HistoryScreen() }
        composable(AppDestination.Downloads.route) { DownloadsScreen() }
        composable(AppDestination.Settings.route) { SettingsScreen() }
        composable(AppDestination.Onboarding.route) { OnboardingScreen() }
    }
}
