@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.navigation.bookmarksScreen
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.navigation.navigateToBookmarks
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.BrowserDestinations
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.BrowserSlots
import com.raumanian.thirtysix.browser.feature.browser.presentation.navigation.BROWSER_ROUTE
import com.raumanian.thirtysix.browser.feature.browser.presentation.navigation.browserScreen
import com.raumanian.thirtysix.browser.feature.browser.presentation.navigation.navigateToBrowser
import com.raumanian.thirtysix.browser.feature.browser.presentation.navigation.popBackToBrowser
import com.raumanian.thirtysix.browser.feature.downloads.presentation.navigation.downloadsScreen
import com.raumanian.thirtysix.browser.feature.downloads.presentation.navigation.navigateToDownloads
import com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry.EntryEvent
import com.raumanian.thirtysix.browser.feature.entrypoint.presentation.entry.EntryPointViewModel
import com.raumanian.thirtysix.browser.feature.history.presentation.navigation.historyScreen
import com.raumanian.thirtysix.browser.feature.history.presentation.navigation.navigateToHistory
import com.raumanian.thirtysix.browser.feature.onboarding.presentation.navigation.ONBOARDING_ROUTE
import com.raumanian.thirtysix.browser.feature.onboarding.presentation.navigation.onboardingScreen
import com.raumanian.thirtysix.browser.feature.privacy.presentation.fire.FireOverlayHost
import com.raumanian.thirtysix.browser.feature.privacy.presentation.fire.rememberFireButton
import com.raumanian.thirtysix.browser.feature.privacy.presentation.navigation.fireproofSitesScreen
import com.raumanian.thirtysix.browser.feature.privacy.presentation.navigation.navigateToFireproofSites
import com.raumanian.thirtysix.browser.feature.privacy.presentation.navigation.navigateToProtectionExceptions
import com.raumanian.thirtysix.browser.feature.privacy.presentation.navigation.protectionExceptionsScreen
import com.raumanian.thirtysix.browser.feature.settings.presentation.navigation.navigateToSettings
import com.raumanian.thirtysix.browser.feature.settings.presentation.navigation.settingsScreen
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.QuickSettingsHost
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.SettingsDestinations
import com.raumanian.thirtysix.browser.feature.sitepermission.presentation.navigation.navigateToSitePermissions
import com.raumanian.thirtysix.browser.feature.sitepermission.presentation.navigation.sitePermissionsScreen
import com.raumanian.thirtysix.browser.feature.tabs.presentation.navigation.navigateToTabs
import com.raumanian.thirtysix.browser.feature.tabs.presentation.navigation.tabsScreen

/** The one place that joins features: every destination, and how one feature opens another. */
@Composable
fun AppNavHost(
    startDestination: String,
    navController: NavHostController = rememberNavController(),
    // One instance for the activity, obtained outside the NavHost; no destination may create its own.
    entryPointViewModel: EntryPointViewModel = hiltViewModel(),
) {
    // Content from another app always lands on the browser.
    LaunchedEffect(entryPointViewModel, navController) {
        entryPointViewModel.events.collect { event ->
            when (event) {
                EntryEvent.ShowBrowser -> showBrowser(navController)
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        browserScreen(slots = { browserSlots(navController, entryPointViewModel) })
        tabsScreen(
            navController = navController,
            // Back to the browser first; its overlay shows the run.
            fireButton = { rememberFireButton(onConfirm = { navController.popBackToBrowser() }) },
            onShowBrowser = { navController.popBackToBrowser() },
        )
        bookmarksScreen(navController = navController, onShowBrowser = { navController.popBackToBrowser() })
        historyScreen(navController = navController, onShowBrowser = { navController.popBackToBrowser() })
        downloadsScreen(navController = navController)
        settingsScreen(
            navController = navController,
            destinations = SettingsDestinations(
                onSitePermissions = navController::navigateToSitePermissions,
                onProtectionExceptions = navController::navigateToProtectionExceptions,
                onFireproofSites = navController::navigateToFireproofSites,
                onBookmarkTransfer = { navController.navigateToBookmarks(openTransfer = true) },
            ),
        )
        sitePermissionsScreen(navController = navController)
        protectionExceptionsScreen(navController = navController)
        fireproofSitesScreen(navController = navController)
        // Leaving pops onboarding inclusively, or Back from the browser would return to it.
        onboardingScreen(
            onFinish = {
                navController.navigate(BROWSER_ROUTE) {
                    popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
    }
}

@Composable
private fun browserSlots(navController: NavHostController, entryPointViewModel: EntryPointViewModel) =
    BrowserSlots(
        fireButton = rememberFireButton(onConfirm = {}),
        fireOverlay = { FireOverlayHost() },
        quickSettings = { request, onConsumeRequest, snackbar ->
            QuickSettingsHost(
                request = request,
                onConsumeRequest = onConsumeRequest,
                snackbarHostState = snackbar,
            )
        },
        incomingContent = entryPointViewModel.browserHooks,
        destinations = BrowserDestinations(
            openTabs = navController::navigateToTabs,
            openSettings = navController::navigateToSettings,
            openDownloads = navController::navigateToDownloads,
            openHistory = navController::navigateToHistory,
            openBookmarks = { navController.navigateToBookmarks() },
        ),
    )

/**
 * Brings the browser to the front by popping the screens above it. Not
 * `navigate(browser) { launchSingleTop }`: on the browser itself that replaces its back-stack
 * entry and recreates the screen, reloading the WebView and cancelling the entry snackbar.
 */
private fun showBrowser(navController: NavHostController) {
    when (navController.currentDestination?.route) {
        BROWSER_ROUTE, ONBOARDING_ROUTE -> Unit
        else -> if (!navController.popBackToBrowser()) navController.navigateToBrowser()
    }
}
