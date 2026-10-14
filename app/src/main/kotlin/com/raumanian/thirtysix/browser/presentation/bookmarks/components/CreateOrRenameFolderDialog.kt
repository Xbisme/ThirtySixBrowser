@file:Suppress("ktlint:standard:function-naming")

package com.raumanian.thirtysix.browser.presentation.bookmarks.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.util.BrowserLimits
/** Spec 013 — create/rename folder dialog; Spec 019 — on [BookmarkInputDialog]. */
@Composable
fun CreateOrRenameFolderDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val error = name.trim().isEmpty()
    BookmarkInputDialog(
        title = title,
        confirmEnabled = !error,
        onConfirm = { if (!error) onConfirm(name.trim()) },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= BrowserLimits.MAX_FOLDER_NAME_LENGTH) name = it },
            singleLine = true,
            isError = error,
            shape = bookmarkFieldShape(),
            supportingText = if (error) {
                { Text(stringResource(R.string.bookmarks_validation_folder_name_required)) }
            } else {
                null
            },
            label = { Text(stringResource(R.string.bookmarks_field_folder_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
