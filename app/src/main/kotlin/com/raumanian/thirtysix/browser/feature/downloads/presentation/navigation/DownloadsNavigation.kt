package com.raumanian.thirtysix.browser.feature.downloads.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.DownloadsScreen

const val DOWNLOADS_ROUTE: String = "downloads"

fun NavGraphBuilder.downloadsScreen(navController: NavHostController) {
    composable(DOWNLOADS_ROUTE) { DownloadsScreen(navController = navController) }
}

fun NavController.navigateToDownloads() = navigate(DOWNLOADS_ROUTE)
