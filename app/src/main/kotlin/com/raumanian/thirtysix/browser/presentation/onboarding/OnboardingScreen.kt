@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.constants.OnboardingSlides
import com.raumanian.thirtysix.browser.presentation.components.MeshArt
import com.raumanian.thirtysix.browser.presentation.components.PillButton
import com.raumanian.thirtysix.browser.presentation.components.PillStyle
import com.raumanian.thirtysix.browser.presentation.components.RoundIconButton
import com.raumanian.thirtysix.browser.presentation.components.RoundIconStyle
import com.raumanian.thirtysix.browser.presentation.navigation.AppDestination
import com.raumanian.thirtysix.browser.presentation.onboarding.components.LanguageSlide
import com.raumanian.thirtysix.browser.presentation.onboarding.components.SearchEngineSlide
import com.raumanian.thirtysix.browser.presentation.onboarding.components.ThemeSlide
import com.raumanian.thirtysix.browser.presentation.onboarding.components.WelcomeSlide
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Dimens
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.presentation.util.findActivity
import kotlinx.coroutines.flow.collectLatest

/**
 * Spec 018 — the first-run onboarding flow.
 *
 * Shown instead of the browser when the first-run flag is unset; the decision is made in
 * `MainActivity` before this graph is built (research.md R2).
 */
@Composable
fun OnboardingScreen(
    navController: NavHostController,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // ⚠️ Leaving MUST pop the onboarding route INCLUSIVELY, or Back from the browser's first
    // page returns here — the flow is meant to appear once per install (FR-010a, SC-015).
    // An assertion that "the browser is showing" would not catch a missing pop; SC-015 presses
    // Back, and that is the check that matters.
    LaunchedLeaveEffect(viewModel) {
        navController.navigate(AppDestination.Browser.route) {
            popUpTo(AppDestination.Onboarding.route) { inclusive = true }
        }
    }

    // FR-002a — Back moves to the previous slide; on the FIRST slide it leaves the app, and
    // leaving this way does NOT write the first-run flag, so the flow returns next launch
    // (FR-012). Enabled unconditionally so the first-slide case is ours to handle rather than
    // the system's default "finish the activity" with no say in it.
    BackHandler {
        if (!viewModel.onBack()) context.findActivity()?.finish()
    }

    val colors = ThirtySixTheme.colors
    // Spec 019 FR-052 — the flow sits on the hero mesh in both themes.
    Box(modifier = Modifier.fillMaxSize().background(colors.heroDeep)) {
        MeshArt(
            base = colors.heroDeep,
            glowA = colors.heroViolet,
            glowB = colors.heroRose,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            OnboardingTopBar(state = state, onBack = { viewModel.onBack() }, onSkip = viewModel::onSkip)
            SlideBody(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
            OnboardingControls(state = state, onForward = viewModel::onForward)
        }
    }
}

@Composable
private fun SlideBody(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier,
) {
    when (state.currentSlide) {
        OnboardingSlides.WELCOME -> WelcomeSlide(modifier = modifier)
        OnboardingSlides.LANGUAGE -> LanguageSlide(
            selected = state.language,
            onSelect = viewModel::onLanguageSelected,
            modifier = modifier,
        )
        OnboardingSlides.THEME -> ThemeSlide(
            selected = state.themeMode,
            onSelect = viewModel::onThemeSelected,
            modifier = modifier,
        )
        else -> SearchEngineSlide(
            selected = state.searchEngine,
            onSelect = viewModel::onSearchEngineSelected,
            modifier = modifier,
        )
    }
}

/**
 * Back (from the second slide on), the step text and Skip — on EVERY slide, including the
 * first and the last (FR-014a).
 */
@Composable
private fun OnboardingTopBar(state: OnboardingUiState, onBack: () -> Unit, onSkip: () -> Unit) {
    val progress = stringResource(
        R.string.onboarding_progress_a11y,
        state.displayPosition,
        OnboardingSlides.COUNT,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        if (state.isFirstSlide) {
            Image(
                painter = painterResource(AppIcons.BrandMark),
                contentDescription = null,
                modifier = Modifier.size(Dimens.brandMark),
            )
        } else {
            RoundIconButton(
                icon = AppIcons.ArrowBack,
                contentDescription = stringResource(R.string.browser_action_back),
                onClick = onBack,
                style = RoundIconStyle.Glass,
            )
        }
        // FR-003 — where the user is in the sequence, and how long it is.
        Text(
            text = progress,
            style = MaterialTheme.typography.labelLarge,
            color = ThirtySixTheme.colors.onGlassMuted,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = progress },
        )
        PillButton(
            label = stringResource(R.string.onboarding_action_skip),
            onClick = onSkip,
            style = PillStyle.Glass,
            modifier = Modifier.testTag(TEST_TAG_ONBOARDING_SKIP),
        )
    }
}

/**
 * Page dots and the forward control.
 *
 * ⚠️ ONE forward control in ONE position across all four slides, relabelled on the last
 * (FR-002b, INV-12) — not two controls with one hidden, which would move focus order. The two
 * labels are separate strings: "Next" and "Start browsing" are different words, not a
 * substitution (INV-13).
 */
@Composable
private fun OnboardingControls(state: OnboardingUiState, onForward: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.md),
    ) {
        PageDots(current = state.currentSlide)
        PillButton(
            label = stringResource(
                if (state.isLastSlide) R.string.onboarding_action_start_browsing else R.string.onboarding_action_next,
            ),
            onClick = onForward,
            style = PillStyle.Gradient,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TEST_TAG_ONBOARDING_FORWARD),
        )
    }
}

/** Decorative: the step text already says where the user is, so the dots are hidden from TalkBack. */
@Composable
private fun PageDots(current: Int) {
    val colors = ThirtySixTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.clearAndSetSemantics { },
    ) {
        repeat(OnboardingSlides.COUNT) { index ->
            Box(
                modifier = Modifier
                    .size(
                        width = if (index == current) Dimens.pageDotActive else Dimens.pageDot,
                        height = Dimens.pageDot,
                    )
                    .background(
                        if (index == current) colors.onGlass else colors.glassStroke,
                        ThirtySixTheme.shapes.full,
                    ),
            )
        }
    }
}

@Composable
private fun LaunchedLeaveEffect(viewModel: OnboardingViewModel, onLeave: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.leaveFlow.collectLatest { onLeave() }
    }
}

/** Test tags for the two controls every slide carries. */
const val TEST_TAG_ONBOARDING_SKIP: String = "onboarding_skip"
const val TEST_TAG_ONBOARDING_FORWARD: String = "onboarding_forward"
