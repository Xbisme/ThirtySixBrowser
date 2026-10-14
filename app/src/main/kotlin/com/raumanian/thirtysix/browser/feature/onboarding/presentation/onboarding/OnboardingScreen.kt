@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding

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
import androidx.compose.runtime.rememberUpdatedState
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
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.MeshArt
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PillButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PillStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconButton
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.RoundIconStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Dimens
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.core.presentation.util.findActivity
import com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding.component.LanguageSlide
import com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding.component.SearchEngineSlide
import com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding.component.ThemeSlide
import com.raumanian.thirtysix.browser.feature.onboarding.presentation.onboarding.component.WelcomeSlide
import kotlinx.coroutines.flow.collectLatest

/** Shown instead of the browser while the first-run flag is unset; `MainActivity` decides before the graph is built. */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // onFinish must pop the onboarding route inclusively, or Back from the browser returns here.
    LaunchedLeaveEffect(viewModel) { onFinish() }

    // On the first slide Back leaves the app without writing the first-run flag, so the flow returns next launch.
    BackHandler {
        if (!viewModel.onBack()) context.findActivity()?.finish()
    }

    val colors = ThirtySixTheme.colors
    Box(modifier = modifier.fillMaxSize().background(colors.heroDeep)) {
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

// One forward control, relabelled on the last slide: two controls with one hidden would move focus order.
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
    val currentOnLeave by rememberUpdatedState(onLeave)
    androidx.compose.runtime.LaunchedEffect(viewModel) {
        viewModel.leaveFlow.collectLatest { currentOnLeave() }
    }
}

const val TEST_TAG_ONBOARDING_SKIP: String = "onboarding_skip"
const val TEST_TAG_ONBOARDING_FORWARD: String = "onboarding_forward"
