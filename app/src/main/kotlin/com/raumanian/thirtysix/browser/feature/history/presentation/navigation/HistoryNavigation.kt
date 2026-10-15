package com.raumanian.thirtysix.browser.feature.history.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.raumanian.thirtysix.browser.feature.history.presentation.history.HistoryRoute

const val HISTORY_ROUTE: String = "history"

fun NavGraphBuilder.historyScreen(navController: NavHostController, onShowBrowser: () -> Unit) {
    composable(HISTORY_ROUTE) { HistoryRoute(navController = navController, onShowBrowser = onShowBrowser) }
}

fun NavController.navigateToHistory() = navigate(HISTORY_ROUTE)
