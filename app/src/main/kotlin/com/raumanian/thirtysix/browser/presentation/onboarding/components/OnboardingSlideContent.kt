@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.onboarding.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.DarkPrimary
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.DarkSurface
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.LightPrimary
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.LightSurface
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.domain.model.AppLanguage
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.domain.model.ThemeMode
import com.raumanian.thirtysix.browser.presentation.settings.components.appLanguageLabel
import com.raumanian.thirtysix.browser.presentation.settings.components.searchEngineLabel
import com.raumanian.thirtysix.browser.presentation.settings.components.themeModeLabel
private val ThemePreviewHeight = 96.dp
private val ThemePreviewBar = 10.dp
private const val THEME_PREVIEW_BAR_FRACTION = 0.6f

/**
 * Spec 018's four slides, restyled by Spec 019 FR-052 for the hero: white type on the dark mesh,
 * choices as glass rows. Every slide scrolls — the language slide carries nine options and must
 * fit a small screen at a large font setting (Spec 018 FR-019, SC-011).
 *
 * Option labels come from Spec 016's public label functions (INV-8).
 */
@Composable
private fun SlideScaffold(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val colors = ThirtySixTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onGlass,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onGlassMuted,
            modifier = Modifier.padding(bottom = Spacing.md),
        )
        content()
    }
}

/**
 * FR-004 — the welcome slide states that the app collects nothing and sends nothing anywhere.
 *
 * ⚠️ It reuses `settings_about_privacy_statement` verbatim (INV-10): two wordings of the app's
 * central promise would drift, and it is also a Play Store data-safety commitment.
 */
@Composable
fun WelcomeSlide(modifier: Modifier = Modifier) {
    val colors = ThirtySixTheme.colors
    SlideScaffold(
        title = stringResource(R.string.onboarding_welcome_title),
        body = stringResource(R.string.onboarding_welcome_body),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.settings_about_privacy_statement),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onGlass,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.glass, MaterialTheme.shapes.large)
                .border(Dimens.hairline, colors.glassStroke, MaterialTheme.shapes.large)
                .padding(Spacing.md),
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

/** FR-007 — Light, Dark and System default, as three preview cards (FR-052). */
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
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.System).forEach { mode ->
                ThemePreviewCard(
                    mode = mode,
                    isSelected = mode == selected,
                    onClick = { onSelect(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A miniature of the browser in that theme, with its name below. The preview paints the
 * theme's own surface and primary — that is the point of a preview — so it uses the scheme
 * constants rather than the current theme's tokens.
 */
@Composable
private fun ThemePreviewCard(
    mode: ThemeMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThirtySixTheme.colors
    val shape = MaterialTheme.shapes.large
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier
            .clip(shape)
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .testTag(TEST_TAG_ONBOARDING_OPTION),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ThemePreviewHeight)
                .clip(shape)
                .border(
                    if (isSelected) Dimens.activeRing else Dimens.hairline,
                    if (isSelected) MaterialTheme.colorScheme.surfaceContainerLow else colors.glassStroke,
                    shape,
                ),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                when (mode) {
                    ThemeMode.Light -> PreviewHalf(LightSurface, LightPrimary, Modifier.weight(1f))
                    ThemeMode.Dark -> PreviewHalf(DarkSurface, DarkPrimary, Modifier.weight(1f))
                    ThemeMode.System -> {
                        PreviewHalf(LightSurface, LightPrimary, Modifier.weight(1f))
                        PreviewHalf(DarkSurface, DarkPrimary, Modifier.weight(1f))
                    }
                }
            }
        }
        Text(
            text = themeModeLabel(mode),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onGlass,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PreviewHalf(surface: Color, accent: Color, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize().background(surface).padding(Spacing.sm)) {
        Box(
            modifier = Modifier
                .fillMaxWidth(THEME_PREVIEW_BAR_FRACTION)
                .height(ThemePreviewBar)
                .background(accent, ThirtySixTheme.shapes.full),
        )
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
