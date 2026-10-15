package com.raumanian.thirtysix.browser.feature.settings.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.SettingsDestinations
import com.raumanian.thirtysix.browser.feature.settings.presentation.settings.SettingsRoute

const val SETTINGS_ROUTE: String = "settings"

fun NavGraphBuilder.settingsScreen(navController: NavHostController, destinations: SettingsDestinations) {
    composable(SETTINGS_ROUTE) { SettingsRoute(navController = navController, destinations = destinations) }
}

fun NavController.navigateToSettings() = navigate(SETTINGS_ROUTE)
