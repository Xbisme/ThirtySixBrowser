package com.raumanian.thirtysix.browser.feature.privacy.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.raumanian.thirtysix.browser.feature.privacy.presentation.fireproof.FireproofSitesScreen
import com.raumanian.thirtysix.browser.feature.privacy.presentation.protectionexceptions.ProtectionExceptionsScreen

const val PROTECTION_EXCEPTIONS_ROUTE: String = "settings/protection_exceptions"
const val FIREPROOF_SITES_ROUTE: String = "settings/fireproof_sites"

fun NavGraphBuilder.protectionExceptionsScreen(navController: NavHostController) {
    composable(PROTECTION_EXCEPTIONS_ROUTE) { ProtectionExceptionsScreen(navController = navController) }
}

fun NavGraphBuilder.fireproofSitesScreen(navController: NavHostController) {
    composable(FIREPROOF_SITES_ROUTE) { FireproofSitesScreen(navController = navController) }
}

fun NavController.navigateToProtectionExceptions() = navigate(PROTECTION_EXCEPTIONS_ROUTE)

fun NavController.navigateToFireproofSites() = navigate(FIREPROOF_SITES_ROUTE)
