package com.raumanian.thirtysix.browser.presentation.navigation

sealed class AppDestination(val route: String) {
    data object Browser : AppDestination("browser")

    data object Tabs : AppDestination("tabs")

    data object Bookmarks : AppDestination("bookmarks")

    data object History : AppDestination("history")

    data object Downloads : AppDestination("downloads")

    data object Settings : AppDestination("settings")

    data object Onboarding : AppDestination("onboarding")
}
