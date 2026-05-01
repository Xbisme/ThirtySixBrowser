@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R

@Composable
fun OnboardingScreen() {
    Text(
        text = stringResource(R.string.onboarding_screen_placeholder),
        style = MaterialTheme.typography.headlineMedium,
    )
}
