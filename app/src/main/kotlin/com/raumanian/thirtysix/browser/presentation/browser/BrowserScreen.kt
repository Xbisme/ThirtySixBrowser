@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R

@Composable
fun BrowserScreen() {
    Text(
        text = stringResource(R.string.browser_screen_placeholder),
        style = MaterialTheme.typography.headlineMedium,
    )
}
