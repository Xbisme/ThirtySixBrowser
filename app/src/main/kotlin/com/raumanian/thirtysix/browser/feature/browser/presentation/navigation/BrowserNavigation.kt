package com.raumanian.thirtysix.browser.feature.browser.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.BrowserScreen
import com.raumanian.thirtysix.browser.feature.browser.presentation.browser.BrowserSlots

const val BROWSER_ROUTE: String = "browser"

fun NavGraphBuilder.browserScreen(slots: @Composable () -> BrowserSlots) {
    composable(BROWSER_ROUTE) { BrowserScreen(slots = slots()) }
}

fun NavController.navigateToBrowser() = navigate(BROWSER_ROUTE)

/** Back to the browser, popping what is above it; false when the browser is not on the stack. */
fun NavController.popBackToBrowser(): Boolean = popBackStack(BROWSER_ROUTE, inclusive = false)
