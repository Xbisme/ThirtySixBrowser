package com.raumanian.thirtysix.browser.feature.onboarding.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding.OnboardingScreen

const val ONBOARDING_ROUTE: String = "onboarding"

fun NavGraphBuilder.onboardingScreen(onFinish: () -> Unit) {
    composable(ONBOARDING_ROUTE) { OnboardingScreen(onFinish = onFinish) }
}
