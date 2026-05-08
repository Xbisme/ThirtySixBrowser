@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.
@file:OptIn(ExperimentalMaterial3Api::class)

package com.raumanian.thirtysix.browser.presentation.history.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R

/**
 * Spec 014 — top bar for the History screen. US1 surface: title + back IconButton.
 * US2 will add an inline search field; US4 will add the Clear all action icon.
 */
@Composable
fun HistoryTopBar(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = { Text(text = stringResource(R.string.history_screen_title)) },
        navigationIcon = {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.testTag(TEST_TAG_HISTORY_BACK),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.history_action_back),
                )
            }
        },
    )
}

const val TEST_TAG_HISTORY_BACK: String = "history_back"
