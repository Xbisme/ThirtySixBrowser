@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.onboarding.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.presentation.settings.components.appLanguageLabel
import com.raumanian.thirtysix.browser.presentation.settings.components.searchEngineLabel
import com.raumanian.thirtysix.browser.presentation.settings.components.themeModeLabel
import com.raumanian.thirtysix.browser.presentation.theme.Spacing

/**
 * Spec 018 — the four slides.
 *
 * Every slide scrolls. That is not defensive padding: the language slide carries nine options
 * and does not fit a small screen at a large font setting unscrolled (FR-019, INV-11, SC-011).
 *
 * The option labels come from Spec 016's public label functions, so Settings and onboarding
 * cannot drift apart in what they call a theme, a language or an engine (INV-8).
 */
@Composable
private fun SlideScaffold(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.lg),
        )
        content()
    }
}

/**
 * FR-004 — the welcome slide MUST state that the app collects nothing and sends nothing
 * anywhere. That is a requirement, not decoration: it is the app's central claim.
 *
 * ⚠️ The statement reuses `settings_about_privacy_statement` verbatim rather than a second
 * sentence of its own (INV-10, A9). Two wordings of the same promise drift apart, and of every
 * string in this app, the privacy claim is the one where drift would be most damaging — it is
 * also a Play Store data-safety commitment.
 */
@Composable
fun WelcomeSlide(modifier: Modifier = Modifier) {
    SlideScaffold(
        title = stringResource(R.string.onboarding_welcome_title),
        body = stringResource(R.string.onboarding_welcome_body),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.settings_about_privacy_statement),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** FR-005 — "Follow system" plus the eight languages, each written in its own language. */
@Composable
fun LanguageSlide(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    SlideScaffold(
        title = stringResource(R.string.onboarding_language_title),
        body = stringResource(R.string.onboarding_language_body),
        modifier = modifier,
    ) {
        AppLanguage.entries.forEach { language ->
            OnboardingOptionRow(
                label = appLanguageLabel(language),
                isSelected = language == selected,
                onClick = { onSelect(language) },
            )
        }
    }
}

/** FR-007 — Light, Dark and System default. */
@Composable
fun ThemeSlide(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    SlideScaffold(
        title = stringResource(R.string.onboarding_theme_title),
        body = stringResource(R.string.onboarding_theme_body),
        modifier = modifier,
    ) {
        ThemeMode.entries.forEach { mode ->
            OnboardingOptionRow(
                label = themeModeLabel(mode),
                isSelected = mode == selected,
                onClick = { onSelect(mode) },
            )
        }
    }
}

/** FR-007 — Google, DuckDuckGo and Bing. */
@Composable
fun SearchEngineSlide(
    selected: SearchEngine,
    onSelect: (SearchEngine) -> Unit,
    modifier: Modifier = Modifier,
) {
    SlideScaffold(
        title = stringResource(R.string.onboarding_search_title),
        body = stringResource(R.string.onboarding_search_body),
        modifier = modifier,
    ) {
        SearchEngine.entries.forEach { engine ->
            OnboardingOptionRow(
                label = searchEngineLabel(engine),
                isSelected = engine == selected,
                onClick = { onSelect(engine) },
            )
        }
    }
}
