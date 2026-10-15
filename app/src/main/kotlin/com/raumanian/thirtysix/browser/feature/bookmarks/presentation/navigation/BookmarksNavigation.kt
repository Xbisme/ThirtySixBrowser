package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.BookmarksScreen

const val BOOKMARKS_ROUTE: String = "bookmarks"

fun NavGraphBuilder.bookmarksScreen(navController: NavHostController, onShowBrowser: () -> Unit) {
    composable(BOOKMARKS_ROUTE) { BookmarksScreen(navController = navController, onShowBrowser = onShowBrowser) }
}

fun NavController.navigateToBookmarks() = navigate(BOOKMARKS_ROUTE)
