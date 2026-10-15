@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.component

import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.DownloadListItem
import com.raumanian.thirtysix.browser.core.domain.model.DownloadStatus
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRowIcon
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ProgressLine
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.bytesSoFarOrNull
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.progressFractionOrNull
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.toLabelRes
import com.raumanian.thirtysix.browser.feature.downloads.presentation.downloads.totalBytesOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal const val TEST_TAG_DOWNLOAD_ROW: String = "download_row"
internal const val TEST_TAG_DOWNLOAD_CANCEL: String = "download_cancel"
internal const val TEST_TAG_DOWNLOAD_PROGRESS: String = "download_progress"

private const val INDETERMINATE_SWEEP_MS: Int = 1_200

private const val MIME_IMAGE = "image/"
private const val MIME_VIDEO = "video/"
private val ARCHIVE_MIME_MARKERS = listOf("zip", "compressed", "x-tar", "x-7z", "x-rar", "gzip")

/**
 * State is shown as a word, with colour only as a secondary cue. The cancel control appears only
 * while in flight, derived from `isTerminal` rather than decided here. There is no retry action.
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
    val stateColor = status.stateColor()
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val summary = buildAnnotatedString {
        withStyle(SpanStyle(color = stateColor)) { append(stateLabel) }
        append(" · $sizeLabel · $timeLabel")
    }

    ListRow(
        title = item.record.fileName,
        subtitle = "",
        leading = { ListRowIcon(icon = fileTypeIcon(item.record.mimeType)) },
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .testTag(TEST_TAG_DOWNLOAD_ROW)
            .semantics { contentDescription = rowDescription },
        below = {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = neutral,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (status.isInFlight) {
                DownloadProgress(status = status, modifier = Modifier.padding(top = Spacing.xs))
            }
        },
        trailing = if (status.isInFlight) {
            {
                RoundIconButton(
                    icon = AppIcons.Close,
                    contentDescription = stringResource(R.string.downloads_action_cancel),
                    onClick = onCancelClick,
                    style = RoundIconStyle.Bare,
                    tint = neutral,
                    modifier = Modifier.testTag(TEST_TAG_DOWNLOAD_CANCEL),
                )
            }
        } else {
            null
        },
    )
}

/** Colour is the secondary cue; the word always carries the meaning. */
@Composable
private fun DownloadStatus.stateColor(): Color = when (this) {
    is DownloadStatus.Complete -> ThirtySixTheme.colors.positive
    is DownloadStatus.Failed, DownloadStatus.Missing -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@DrawableRes
private fun fileTypeIcon(mimeType: String): Int {
    val type = mimeType.lowercase(Locale.ROOT)
    return when {
        type.startsWith(MIME_IMAGE) -> AppIcons.Image
        type.startsWith(MIME_VIDEO) -> AppIcons.Movie
        ARCHIVE_MIME_MARKERS.any { type.contains(it) } -> AppIcons.FolderZip
        else -> AppIcons.Description
    }
}

/**
 * Indeterminate sweep when the total is unknown, never a fabricated percentage. The sweep is read
 * only in [ProgressLine]'s `drawBehind`, so animating it redraws without recomposing.
 */
@Composable
private fun DownloadProgress(status: DownloadStatus, modifier: Modifier = Modifier) {
    val progressModifier = modifier.testTag(TEST_TAG_DOWNLOAD_PROGRESS)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh

    if (status.progressFractionOrNull == null) {
        val sweep = rememberInfiniteTransition(label = "download-sweep").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = INDETERMINATE_SWEEP_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "download-sweep-fraction",
        )
        ProgressLine(
            progress = { sweep.value },
            thickness = Dimens.progressBar,
            trackColor = trackColor,
            modifier = progressModifier,
        )
    } else {
        ProgressLine(
            progress = { status.progressFractionOrNull ?: 0f },
            thickness = Dimens.progressBar,
            trackColor = trackColor,
            modifier = progressModifier,
        )
    }
}

// Compose-observable locale bridge: `Locale.getDefault()` is rejected by `NonObservableLocale` in a Composable.
@Composable
private fun rememberFormattedTime(epochMillis: Long): String {
    val locale = Locale.forLanguageTag(androidx.compose.ui.text.intl.Locale.current.toLanguageTag())
    val formatter = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(epochMillis))
}

@Composable
private fun formattedSize(context: android.content.Context, status: DownloadStatus): String {
    val soFar = status.bytesSoFarOrNull
    val total = status.totalBytesOrNull
    return when {
        // A completed transfer reports only a total; "71 B / 71 B" would be noise.
        status is DownloadStatus.Complete -> total?.let { Formatter.formatFileSize(context, it) }
            ?: stringResource(R.string.downloads_size_unknown)
        soFar != null && total != null ->
            "${Formatter.formatFileSize(context, soFar)} / ${Formatter.formatFileSize(context, total)}"
        total != null -> Formatter.formatFileSize(context, total)
        soFar != null -> Formatter.formatFileSize(context, soFar)
        else -> stringResource(R.string.downloads_size_unknown)
    }
}
