package com.raumanian.thirtysix.browser.feature.sitepermission.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.raumanian.thirtysix.browser.feature.sitepermission.presentation.sitepermissions.SitePermissionsRoute

const val SITE_PERMISSIONS_ROUTE: String = "settings/site_permissions"

fun NavGraphBuilder.sitePermissionsScreen(navController: NavHostController) {
    composable(SITE_PERMISSIONS_ROUTE) { SitePermissionsRoute(navController = navController) }
}

fun NavController.navigateToSitePermissions() = navigate(SITE_PERMISSIONS_ROUTE)
