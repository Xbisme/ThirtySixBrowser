@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding.OnboardingScreen
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.SettingsScreen
import com.raumanian.thirtysix.browser.feature.sitepermission.presentation.sitepermissions.SitePermissionsScreen
import com.raumanian.thirtysix.browser.presentation.bookmarks.BookmarksScreen
import com.raumanian.thirtysix.browser.presentation.browser.BrowserScreen
import com.raumanian.thirtysix.browser.presentation.downloads.DownloadsScreen
import com.raumanian.thirtysix.browser.presentation.entry.EntryEvent
import com.raumanian.thirtysix.browser.presentation.entry.EntryPointViewModel
import com.raumanian.thirtysix.browser.presentation.history.HistoryScreen
import com.raumanian.thirtysix.browser.presentation.settings.fireproof.FireproofSitesScreen
import com.raumanian.thirtysix.browser.presentation.settings.protectionexceptions.ProtectionExceptionsScreen
import com.raumanian.thirtysix.browser.presentation.tabs.TabsScreen

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = AppDestination.Browser.route,
    // Spec 020 research R5 — the ONE instance, obtained here outside the NavHost so it is scoped
    // to the activity. No destination may call hiltViewModel<EntryPointViewModel>() itself.
    entryPointViewModel: EntryPointViewModel = hiltViewModel(),
) {
    // Spec 020 FR-005 — content from another app always lands on the browser, popping whatever
    // screen was above it. Onboarding routes itself out when it finishes (Spec 018 FR-010a); the
    // view model only emits once onboarding is complete, but the guard keeps the two from racing.
    LaunchedEffect(entryPointViewModel, navController) {
        entryPointViewModel.events.collect { event ->
            when (event) {
                EntryEvent.ShowBrowser -> showBrowser(navController)
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        // Spec 011 — BrowserScreen + TabsScreen receive NavController so the
        // 5th BottomAppBar button can navigate to AppDestination.Tabs (single
        // tap), and the switcher can pop back via TabsViewModel.popBackEvent.
        composable(AppDestination.Browser.route) {
            BrowserScreen(navController = navController, incomingContent = entryPointViewModel.browserHooks)
        }
        composable(AppDestination.Tabs.route) { TabsScreen(navController = navController) }
        composable(AppDestination.Bookmarks.route) { BookmarksScreen(navController = navController) }
        composable(AppDestination.History.route) { HistoryScreen(navController = navController) }
        composable(AppDestination.Downloads.route) { DownloadsScreen(navController = navController) }
        composable(AppDestination.Settings.route) { SettingsScreen(navController = navController) }
        composable(AppDestination.SitePermissions.route) { SitePermissionsScreen(navController = navController) }
        composable(AppDestination.ProtectionExceptions.route) {
            ProtectionExceptionsScreen(navController = navController)
        }
        composable(AppDestination.FireproofSites.route) {
            FireproofSitesScreen(navController = navController)
        }
        // Spec 018 — receives the controller so finishing or skipping can navigate to the
        // browser popping this route inclusively (FR-010a).
        composable(AppDestination.Onboarding.route) { OnboardingScreen(navController = navController) }
    }
}

/**
 * Spec 020 FR-005 — brings the browser to the front by popping the screens above it.
 *
 * Deliberately NOT `navigate(Browser) { launchSingleTop }`: on the browser itself that replaces
 * its back-stack entry, which recreates the whole screen — the WebView reloads and a snackbar the
 * entry feature just showed is cancelled (found on device). Already on the browser → nothing to
 * do; on onboarding → Spec 018 routes out itself.
 */
private fun showBrowser(navController: NavHostController) {
    when (navController.currentDestination?.route) {
        AppDestination.Browser.route, AppDestination.Onboarding.route -> Unit
        else -> if (!navController.popBackStack(AppDestination.Browser.route, inclusive = false)) {
            navController.navigate(AppDestination.Browser.route)
        }
    }
}
