@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.
@file:OptIn(ExperimentalFoundationApi::class)

package com.raumanian.thirtysix.browser.presentation.history.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.constants.DateFormats
import com.raumanian.thirtysix.browser.core.extensions.extractHostnameOrSelf
import com.raumanian.thirtysix.browser.data.local.cache.FaviconCache
import com.raumanian.thirtysix.browser.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Spec 014 FR-009 / FR-010 — single history row.
 *
 * Layout: 32 dp favicon (or globe placeholder) · column[ title · hostname ] · time-of-visit.
 * Title falls back to the URL's hostname when empty (FR-009 + edge-case handling).
 * Tap → [onClick] (replaces active tab); long-press → [onLongClick] (action sheet — wired in US3).
 *
 * Favicon path is read synchronously via [FaviconCache.fileFor]; the bitmap is decoded
 * once per `(path, length)` combination via `remember`. Cache miss falls back to the
 * generic globe icon.
 */
@Composable
fun HistoryRow(
    entry: HistoryEntry,
    faviconCache: FaviconCache,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hostname = entry.url.extractHostnameOrSelf()
    val displayTitle = entry.title.ifBlank { hostname.ifBlank { entry.url } }
    val faviconVersion by faviconCache.version.collectAsStateWithLifecycle()
    val file: File? = remember(entry.url, faviconVersion) { faviconCache.fileFor(entry.url) }
    val timeText = formatTimeOfVisit(entry.visitedAt)
    val a11yLabel = stringResource(
        R.string.history_row_content_description,
        displayTitle,
        hostname,
        timeText,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TEST_TAG_HISTORY_ROW_PREFIX + entry.id)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                role = Role.Button,
                onClickLabel = a11yLabel,
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FaviconOrGlobe(file = file)
        TitleAndHostnameColumn(
            displayTitle = displayTitle,
            hostname = hostname,
            modifier = Modifier
                .padding(start = Spacing.md)
                .weight(1f),
        )
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = Spacing.sm)
                .wrapContentWidth(),
        )
    }
}

@Composable
private fun formatTimeOfVisit(visitedAt: Long): String {
    val locale: Locale = Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())
    return remember(visitedAt, locale) {
        DateTimeFormatter
            .ofLocalizedTime(DateFormats.HISTORY_TIME_OF_VISIT_STYLE)
            .withLocale(locale)
            .format(Instant.ofEpochMilli(visitedAt).atZone(ZoneId.systemDefault()))
    }
}

@Composable
private fun TitleAndHostnameColumn(
    displayTitle: String,
    hostname: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = hostname,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FaviconOrGlobe(file: File?) {
    val bitmap = remember(file?.absolutePath, file?.length()) {
        file?.takeIf { it.exists() && it.length() > 0L }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
            ?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(FAVICON_DISPLAY_SIZE_DP),
        )
    } else {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(FAVICON_DISPLAY_SIZE_DP),
        )
    }
}

private val FAVICON_DISPLAY_SIZE_DP = 24.dp

/** Test-tag prefix exposed for instrumented tests asserting per-row presence. */
const val TEST_TAG_HISTORY_ROW_PREFIX: String = "history_row_"
