@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.
@file:OptIn(ExperimentalFoundationApi::class)

package com.raumanian.thirtysix.browser.feature.history.presentation.history.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.domain.model.HistoryEntry
import com.raumanian.thirtysix.browser.core.domain.repository.FaviconCache
import com.raumanian.thirtysix.browser.core.domain.util.extractHostnameOrSelf
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRow
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRowLead
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.ListRowLetter
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.rememberFileImage
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.feature.history.presentation.history.DateFormats
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Spec 014 FR-009 / FR-010 — single history row, drawn as the shared open [ListRow]
 * (Spec 019): favicon (or the host's first letter) · title over hostname · time of visit.
 * Title falls back to the URL's hostname when empty (FR-009 + edge-case handling).
 * Tap → [onClick] (replaces active tab); long-press → [onLongClick] (action sheet).
 *
 * The click is attached here rather than through [ListRow]'s own `onClick` so the row keeps
 * its spoken click label (title, host, time). The favicon is decoded off the main thread by
 * [rememberFileImage]; until then, or on a cache miss, the letter shows.
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

    ListRow(
        title = displayTitle,
        subtitle = hostname,
        leading = { FaviconOrLetter(file = file, hostname = hostname) },
        modifier = modifier
            .testTag(TEST_TAG_HISTORY_ROW_PREFIX + entry.id)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                role = Role.Button,
                onClickLabel = a11yLabel,
            ),
        trailing = {
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
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
private fun FaviconOrLetter(file: File?, hostname: String) {
    val bitmap by rememberFileImage(file)
    val image = bitmap
    if (image != null) {
        ListRowLead {
            Image(bitmap = image, contentDescription = null, modifier = Modifier.size(Dimens.icon))
        }
    } else {
        ListRowLetter(letter = hostname.take(1).uppercase())
    }
}

/** Test-tag prefix exposed for instrumented tests asserting per-row presence. */
const val TEST_TAG_HISTORY_ROW_PREFIX: String = "history_row_"
