package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.BookmarksRoute

const val BOOKMARKS_ROUTE: String = "bookmarks"
private const val ARG_OPEN_TRANSFER = "openTransfer"
private const val BOOKMARKS_ROUTE_PATTERN = "$BOOKMARKS_ROUTE?$ARG_OPEN_TRANSFER={$ARG_OPEN_TRANSFER}"

fun NavGraphBuilder.bookmarksScreen(navController: NavHostController, onShowBrowser: () -> Unit) {
    composable(
        route = BOOKMARKS_ROUTE_PATTERN,
        arguments = listOf(
            navArgument(ARG_OPEN_TRANSFER) {
                type = NavType.BoolType
                defaultValue = false
            },
        ),
    ) { entry ->
        BookmarksRoute(
            navController = navController,
            openTransfer = entry.arguments?.getBoolean(ARG_OPEN_TRANSFER) ?: false,
            onShowBrowser = onShowBrowser,
        )
    }
}

/** [openTransfer] opens the screen with Import and Export offered, as Settings does. */
fun NavController.navigateToBookmarks(openTransfer: Boolean = false) =
    navigate(if (openTransfer) "$BOOKMARKS_ROUTE?$ARG_OPEN_TRANSFER=true" else BOOKMARKS_ROUTE)
