@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.BookmarkFolder
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SheetActionRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SheetFrame
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SheetTitle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
/** Spec 013 — long-press actions for a folder; Spec 019 — on [SheetFrame]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun FolderActionSheet(
    folder: BookmarkFolder,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetFrame(onDismiss = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.padding(bottom = Spacing.md)) {
            SheetTitle(text = folder.name)
            SheetActionRow(stringResource(R.string.bookmarks_action_open), AppIcons.FolderFilled, onOpen)
            SheetActionRow(stringResource(R.string.bookmarks_action_rename), AppIcons.Edit, onRename)
            SheetActionRow(stringResource(R.string.bookmarks_action_move), AppIcons.MoveToFolder, onMove)
            SheetActionRow(
                label = stringResource(R.string.bookmarks_action_delete),
                icon = AppIcons.DeleteFilled,
                onClick = onDelete,
                destructive = true,
            )
        }
    }
}
