@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.BookmarksUiState
/**
 * A large title over a search well. The leading button closes search while searching; otherwise
 * it goes up a folder, or leaves the screen at the root. Focusing the well opens search.
 */
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
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.isSearchActive) {
        if (!state.isSearchActive) focusManager.clearFocus()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (state.isSearchActive) {
                RoundIconButton(
                    icon = AppIcons.Close,
                    contentDescription = stringResource(R.string.browser_address_bar_clear_cd),
                    onClick = onSearchClose,
                    style = RoundIconStyle.Bare,
                )
            } else {
                RoundIconButton(
                    icon = AppIcons.ArrowBack,
                    contentDescription = stringResource(R.string.bookmarks_action_back_to_parent_cd),
                    onClick = if (state.currentFolderId != null) onBackToParent else onExitScreen,
                    style = RoundIconStyle.Bare,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (!state.isSearchActive) {
                RoundIconButton(
                    icon = AppIcons.CreateNewFolder,
                    contentDescription = stringResource(R.string.bookmarks_action_new_folder_cd),
                    onClick = onCreateFolder,
                    style = RoundIconStyle.Bare,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = Spacing.sm)
                .semantics { heading() },
        )
        BookmarksSearchWell(
            query = state.searchQuery,
            onFocus = { if (!state.isSearchActive) onSearchOpen() },
            onQueryChange = onSearchQueryChange,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        )
    }
}

/** The rounded search well — the address well's resting look (`surfaceContainer`, pill). */
@Composable
private fun BookmarksSearchWell(
    query: String,
    onFocus: () -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = ThirtySixTheme.shapes.full
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocus() },
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .height(Dimens.searchWell)
                    .clip(shape)
                    .background(scheme.surfaceContainer, shape)
                    .padding(horizontal = Spacing.md - Spacing.xs),
            ) {
                Icon(
                    painter = painterResource(AppIcons.Search),
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconSmall),
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.bookmarks_action_search_cd),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}
