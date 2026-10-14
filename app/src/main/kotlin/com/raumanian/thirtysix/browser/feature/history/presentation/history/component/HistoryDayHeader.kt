@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.history.presentation.history.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SectionHeader
import com.raumanian.thirtysix.browser.feature.history.domain.model.HistoryDayBucket
import com.raumanian.thirtysix.browser.feature.history.presentation.history.DateFormats
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryDayHeader(
    bucket: HistoryDayBucket,
    modifier: Modifier = Modifier,
) {
    val label = when (bucket) {
        HistoryDayBucket.Today -> stringResource(R.string.history_day_today)
        HistoryDayBucket.Yesterday -> stringResource(R.string.history_day_yesterday)
        is HistoryDayBucket.OnDate -> {
            // Compose-observable locale, so the header recomposes when the language changes at runtime.
            val locale: Locale = Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())
            DateTimeFormatter
                .ofLocalizedDate(DateFormats.HISTORY_DAY_HEADER_STYLE)
                .withLocale(locale)
                .format(bucket.date)
        }
    }
    SectionHeader(text = label, modifier = modifier)
}
