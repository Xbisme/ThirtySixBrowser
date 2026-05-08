@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.history.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.constants.DateFormats
import com.raumanian.thirtysix.browser.domain.model.HistoryDayBucket
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Spec 014 FR-008 / FR-029 — section header rendered above each [HistoryDayBucket]
 * group. Discrete buckets render localized strings; [HistoryDayBucket.OnDate] uses
 * `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` against the current
 * configuration locale so the format follows user conventions.
 */
@Composable
fun HistoryDayHeader(
    bucket: HistoryDayBucket,
    modifier: Modifier = Modifier,
) {
    val label = when (bucket) {
        HistoryDayBucket.Today -> stringResource(R.string.history_day_today)
        HistoryDayBucket.Yesterday -> stringResource(R.string.history_day_yesterday)
        is HistoryDayBucket.OnDate -> {
            // Spec 014 FR-029 — read locale from Compose-observable source so the header
            // recomposes when the user switches device language at runtime.
            val locale: Locale = Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())
            DateTimeFormatter
                .ofLocalizedDate(DateFormats.HISTORY_DAY_HEADER_STYLE)
                .withLocale(locale)
                .format(bucket.date)
        }
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    )
}
