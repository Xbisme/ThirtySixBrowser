@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.bookmarks.presentation.bookmarks.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SheetActionRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SheetFrame
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SheetTitle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkTransferSheet(
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetFrame(onDismiss = onDismiss, modifier = modifier.testTag(TEST_TAG_BOOKMARK_TRANSFER_SHEET)) {
        Column(modifier = Modifier.padding(bottom = Spacing.md)) {
            SheetTitle(text = stringResource(R.string.bookmarks_transfer_sheet_title))
            SheetActionRow(stringResource(R.string.bookmarks_transfer_import), AppIcons.FileOpen, onImport)
            SheetActionRow(stringResource(R.string.bookmarks_transfer_export), AppIcons.FileSave, onExport)
        }
    }
}

const val TEST_TAG_BOOKMARK_TRANSFER_SHEET: String = "bookmark_transfer_sheet"
