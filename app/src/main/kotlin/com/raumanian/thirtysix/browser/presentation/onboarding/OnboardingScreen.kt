@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.constants.OnboardingSlides
import com.raumanian.thirtysix.browser.presentation.navigation.AppDestination
import com.raumanian.thirtysix.browser.presentation.onboarding.components.LanguageSlide
import com.raumanian.thirtysix.browser.presentation.onboarding.components.SearchEngineSlide
import com.raumanian.thirtysix.browser.presentation.onboarding.components.ThemeSlide
import com.raumanian.thirtysix.browser.presentation.onboarding.components.WelcomeSlide
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
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
        if (!viewModel.onBack()) context.findHostActivity()?.finish()
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SlideBody(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.weight(1f),
            )
            OnboardingControls(state = state, viewModel = viewModel)
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
 * Skip, the position indicator and the forward control.
 *
 * ⚠️ ONE forward control in ONE position across all four slides, relabelled on the last
 * (FR-002b, INV-12). Not two controls with one hidden: that moves focus order and breaks the
 * predictability a screen-reader user depends on. The two labels are separate string resources
 * rather than one string with a placeholder — "Next" and "Done" are different words, not a
 * substitution, and a placeholder produces untranslatable grammar across 8 locales (INV-13).
 */
@Composable
private fun OnboardingControls(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
) {
    val progress = stringResource(
        R.string.onboarding_progress_a11y,
        state.displayPosition,
        OnboardingSlides.COUNT,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // FR-014a — Skip is available on EVERY slide, including the first and the last.
        TextButton(
            onClick = viewModel::onSkip,
            modifier = Modifier.testTag(TEST_TAG_ONBOARDING_SKIP),
        ) {
            Text(text = stringResource(R.string.onboarding_action_skip))
        }

        // FR-003 — where the user is in the sequence, and how long it is.
        Text(
            text = progress,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.semantics { contentDescription = progress },
        )

        Button(
            onClick = viewModel::onForward,
            modifier = Modifier.testTag(TEST_TAG_ONBOARDING_FORWARD),
        ) {
            Text(
                text = stringResource(
                    if (state.isLastSlide) R.string.onboarding_action_done else R.string.onboarding_action_next,
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

/**
 * Walk the context chain to the hosting activity.
 *
 * `LocalContext.current` in a Compose hierarchy is typically a `ContextWrapper` rather than the
 * activity itself, so an unchecked cast would return null on some hosts. Kept private here
 * rather than widening the identical helper that is private to `DownloadPermissionGate.kt` —
 * two callers do not justify promoting it to shared API.
 */
private fun Context.findHostActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
