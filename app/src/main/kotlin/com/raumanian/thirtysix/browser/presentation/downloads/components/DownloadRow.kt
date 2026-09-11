@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.
@file:OptIn(ExperimentalFoundationApi::class)

package com.raumanian.thirtysix.browser.presentation.downloads.components

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.presentation.downloads.bytesSoFarOrNull
import com.raumanian.thirtysix.browser.presentation.downloads.progressFractionOrNull
import com.raumanian.thirtysix.browser.presentation.downloads.toLabelRes
import com.raumanian.thirtysix.browser.presentation.downloads.totalBytesOrNull
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal const val TEST_TAG_DOWNLOAD_ROW: String = "download_row"
internal const val TEST_TAG_DOWNLOAD_CANCEL: String = "download_cancel"
internal const val TEST_TAG_DOWNLOAD_PROGRESS: String = "download_progress"

/**
 * Spec 015 FR-020 – FR-022, FR-036 — one row of the Downloads list.
 *
 * Shows filename, size, state and start time. A transfer still in flight also shows live
 * progress and a **one-tap cancel control** (FR-036); that control is absent on every
 * terminal state (FR-036a), which it derives from the single `isTerminal` definition rather
 * than re-deciding for itself.
 */
@Composable
fun DownloadRow(
    item: DownloadListItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val status = item.status
    val stateLabel = stringResource(status.toLabelRes())
    val timeLabel = rememberFormattedTime(item.record.createdAt)
    val sizeLabel = formattedSize(context, status)
    val rowDescription = stringResource(
        R.string.downloads_row_content_description,
        item.record.fileName,
        stateLabel,
        timeLabel,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .testTag(TEST_TAG_DOWNLOAD_ROW)
            .semantics { contentDescription = rowDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            // material-icons-core carries no download glyph (research.md R9); the downward
            // arrow is the closest honest substitute in the 48 available.
            imageVector = if (status is DownloadStatus.Failed || status == DownloadStatus.Missing) {
                Icons.Filled.Warning
            } else {
                Icons.Filled.KeyboardArrowDown
            },
            contentDescription = null,
            tint = if (status is DownloadStatus.Failed || status == DownloadStatus.Missing) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )

        DownloadRowDetails(
            fileName = item.record.fileName,
            summary = "$stateLabel · $sizeLabel · $timeLabel",
            status = status,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.md),
        )

        // FR-036 / FR-036a — present only while in flight, gone on every terminal state.
        if (status.isInFlight) {
            IconButton(onClick = onCancelClick, modifier = Modifier.testTag(TEST_TAG_DOWNLOAD_CANCEL)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.downloads_action_cancel),
                )
            }
        }
    }
}

@Composable
private fun DownloadRowDetails(
    fileName: String,
    summary: String,
    status: DownloadStatus,
    modifier: Modifier = Modifier,
) {
    val isProblem = status is DownloadStatus.Failed || status == DownloadStatus.Missing
    Column(modifier = modifier) {
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = if (isProblem) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (status.isInFlight) {
            DownloadProgress(status = status, modifier = Modifier.padding(top = Spacing.xs))
        }
    }
}

/**
 * Determinate when the server declared a length, indeterminate otherwise — never a
 * fabricated percentage against an unknown total.
 */
@Composable
private fun DownloadProgress(status: DownloadStatus, modifier: Modifier = Modifier) {
    val fraction = status.progressFractionOrNull
    val progressModifier = modifier
        .fillMaxWidth()
        .testTag(TEST_TAG_DOWNLOAD_PROGRESS)

    if (fraction == null) {
        LinearProgressIndicator(modifier = progressModifier)
    } else {
        LinearProgressIndicator(progress = { fraction }, modifier = progressModifier)
    }
}

/**
 * FR-050 — locale-formatted start time.
 *
 * Uses the compose-observable locale bridge rather than `Locale.getDefault()`, which the
 * `NonObservableLocale` lint rule rejects inside a Composable — the trap Spec 014 hit.
 */
@Composable
private fun rememberFormattedTime(epochMillis: Long): String {
    val locale = Locale.forLanguageTag(androidx.compose.ui.text.intl.Locale.current.toLanguageTag())
    val formatter = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(epochMillis))
}

/**
 * FR-020 / FR-050 — locale-formatted size. While transferring, shows progress against the
 * total when one is known; falls back to a localized "unknown size" rather than showing a
 * misleading zero.
 */
@Composable
private fun formattedSize(context: android.content.Context, status: DownloadStatus): String {
    val soFar = status.bytesSoFarOrNull
    val total = status.totalBytesOrNull
    return when {
        // A completed transfer reports only a total; showing "71 B / 71 B" would be noise.
        status is DownloadStatus.Complete -> total?.let { Formatter.formatFileSize(context, it) }
            ?: stringResource(R.string.downloads_size_unknown)
        soFar != null && total != null ->
            "${Formatter.formatFileSize(context, soFar)} / ${Formatter.formatFileSize(context, total)}"
        total != null -> Formatter.formatFileSize(context, total)
        soFar != null -> Formatter.formatFileSize(context, soFar)
        else -> stringResource(R.string.downloads_size_unknown)
    }
}
