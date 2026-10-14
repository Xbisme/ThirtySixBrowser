package com.raumanian.thirtysix.browser.feature.tabs.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.raumanian.thirtysix.browser.core.presentation.util.FireButton
import com.raumanian.thirtysix.browser.feature.tabs.presentation.tabs.TabsScreen

const val TABS_ROUTE: String = "tabs"

fun NavGraphBuilder.tabsScreen(
    navController: NavHostController,
    fireButton: @Composable () -> FireButton,
    onShowBrowser: () -> Unit,
) {
    composable(TABS_ROUTE) {
        TabsScreen(navController = navController, fireButton = fireButton(), onShowBrowser = onShowBrowser)
    }
}

fun NavController.navigateToTabs() = navigate(TABS_ROUTE)
