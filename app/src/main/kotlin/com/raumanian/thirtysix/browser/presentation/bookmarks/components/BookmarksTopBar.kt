@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.presentation.bookmarks.BookmarksUiState
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 013 — top app bar for the bookmarks screen. When `isSearchActive`,
 * the title is replaced by an inline search field with a clear button.
 *
 * Otherwise renders: optional back-to-parent navigation icon (visible iff
 * not at root), title (current folder name or default), search action,
 * and a "new folder" action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList", "LongMethod")
fun BookmarksTopBar(
    state: BookmarksUiState,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onBackToParent: () -> Unit,
    onExitScreen: () -> Unit,
    onCreateFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rootTitle = stringResource(R.string.bookmarks_screen_title)
    val title = state.breadcrumb.lastOrNull()?.name ?: rootTitle
    if (state.isSearchActive) {
        TopAppBar(
            modifier = modifier,
            title = {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = Spacing.sm),
                    placeholder = {
                        Text(stringResource(R.string.bookmarks_action_search_cd))
                    },
                )
            },
            navigationIcon = {
                IconButton(onClick = onSearchClose) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.browser_address_bar_clear_cd),
                    )
                }
            },
        )
    } else {
        TopAppBar(
            modifier = modifier,
            title = { Text(title) },
            navigationIcon = {
                // Spec 013 — ArrowBack always visible. At non-root, navigates up
                // to parent folder; at root, exits the BookmarksScreen back to
                // the previous destination (BrowserScreen).
                IconButton(
                    onClick = if (state.currentFolderId != null) onBackToParent else onExitScreen,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.bookmarks_action_back_to_parent_cd),
                    )
                }
            },
            actions = {
                IconButton(onClick = onSearchOpen) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.bookmarks_action_search_cd),
                    )
                }
                IconButton(onClick = onCreateFolder) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.bookmarks_action_new_folder_cd),
                    )
                }
            },
        )
    }
}
