package com.raumanian.thirtysix.browser.presentation.navigation

sealed class AppDestination(val route: String) {
    data object Browser : AppDestination("browser")

    data object Tabs : AppDestination("tabs")

    data object Bookmarks : AppDestination("bookmarks")

    data object History : AppDestination("history")

    data object Downloads : AppDestination("downloads")

    data object Settings : AppDestination("settings")

    /** Spec 021 FR-009 — remembered camera / microphone / location decisions per site. */
    data object SitePermissions : AppDestination("settings/site_permissions")

    data object Onboarding : AppDestination("onboarding")
}
