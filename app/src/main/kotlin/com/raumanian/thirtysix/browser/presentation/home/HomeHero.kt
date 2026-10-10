@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.domain.model.GreetingPeriod
import com.raumanian.thirtysix.browser.domain.model.HomeShortcut
import com.raumanian.thirtysix.browser.domain.model.SearchEngine
import com.raumanian.thirtysix.browser.presentation.components.MeshArt
import com.raumanian.thirtysix.browser.presentation.components.RoundIconButton
import com.raumanian.thirtysix.browser.presentation.components.RoundIconStyle
import com.raumanian.thirtysix.browser.presentation.theme.AppIcons
import com.raumanian.thirtysix.browser.presentation.theme.Dimens
import com.raumanian.thirtysix.browser.presentation.theme.Spacing
import com.raumanian.thirtysix.browser.presentation.theme.ThirtySixTheme
import com.raumanian.thirtysix.browser.presentation.theme.softShadow

internal const val TEST_TAG_HOME_SEARCH: String = "home_search"
internal const val TEST_TAG_HOME_GREETING: String = "home_greeting"

private val HeroGap = 22.dp
private val EngineDisc = 28.dp
private const val COLLAPSE_DISTANCE_FRACTION = 0.6f
private const val PARALLAX = 0.4f

/**
 * Spec 019 FR-011 / FR-013 — the Home hero (design system Hero): a dark mesh in both themes,
 * the brand mark, a glass "new incognito tab" button, the greeting, one privacy line, the
 * floating search bar and the shortcut cards.
 *
 * Collapse on scroll reads [listState] **only inside `graphicsLayer { }`** (research R6), so
 * scrolling the Home page fades and lifts the hero without recomposing it.
 */
@Composable
@Suppress("LongParameterList")
fun HomeHero(
    state: HomeUiState,
    listState: LazyListState,
    onSearchSubmit: (String) -> Unit,
    onShortcut: (HomeShortcut) -> Unit,
    onNewIncognitoTab: () -> Unit,
    searchFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = ThirtySixTheme.colors
    Box(modifier = modifier.fillMaxWidth()) {
        MeshArt(
            base = colors.heroDeep,
            glowA = colors.heroViolet,
            glowB = colors.heroRose,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(HeroGap),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = Spacing.sm, bottom = Spacing.lg)
                .graphicsLayer {
                    val scrolled = if (listState.firstVisibleItemIndex == 0) {
                        listState.firstVisibleItemScrollOffset.toFloat()
                    } else {
                        size.height
                    }
                    alpha = (1f - scrolled / (size.height * COLLAPSE_DISTANCE_FRACTION)).coerceIn(0f, 1f)
                    translationY = scrolled * PARALLAX
                },
        ) {
            HeroTopRow(onNewIncognitoTab = onNewIncognitoTab)
            HeroGreeting(greeting = state.greeting)
            HomeSearchBar(
                engine = state.searchEngine,
                onSubmit = onSearchSubmit,
                focusRequester = searchFocus,
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            if (state.shortcuts.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(horizontal = Spacing.md),
                ) {
                    items(
                        items = state.shortcuts,
                        key = { it.shortcut.stableKey },
                        contentType = { it.shortcut.kind },
                    ) { item ->
                        ShortcutCard(item = item, onClick = { onShortcut(item.shortcut) })
                    }
                }
            }
        }
    }
}

/** Brand mark and the glass "new incognito tab" button. */
@Composable
private fun HeroTopRow(onNewIncognitoTab: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
    ) {
        Image(
            painter = painterResource(AppIcons.BrandMark),
            contentDescription = null,
            modifier = Modifier.size(Dimens.brandMark),
        )
        RoundIconButton(
            icon = AppIcons.Incognito,
            contentDescription = stringResource(R.string.tabs_action_new_incognito_tab),
            onClick = onNewIncognitoTab,
            style = RoundIconStyle.Glass,
        )
    }
}

/** Time-of-day greeting and the one privacy line (FR-011). */
@Composable
private fun HeroGreeting(greeting: GreetingPeriod) {
    val colors = ThirtySixTheme.colors
    Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
        Text(
            text = stringResource(greeting.label()),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onGlass,
            modifier = Modifier.semantics { heading() }.testTag(TEST_TAG_HOME_GREETING),
        )
        Text(
            text = stringResource(R.string.home_privacy_line),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onGlassMuted,
        )
    }
}

/** The white floating search pill; submits exactly as the address bar does (FR-012). */
@Composable
private fun HomeSearchBar(
    engine: SearchEngine,
    onSubmit: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val scheme = MaterialTheme.colorScheme
    val shape = ThirtySixTheme.shapes.full
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
        cursorBrush = SolidColor(scheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit(text) }),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .testTag(TEST_TAG_HOME_SEARCH),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .height(Dimens.searchBar)
                    .softShadow(shape, ThirtySixTheme.colors.shadow)
                    .background(scheme.surfaceContainerLow, shape)
                    .padding(horizontal = Spacing.md),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(EngineDisc).background(scheme.surfaceContainer, shape),
                ) {
                    Text(
                        text = engine.name.take(1),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                    )
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.browser_address_bar_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            }
        },
    )
}

private fun GreetingPeriod.label(): Int = when (this) {
    GreetingPeriod.Morning -> R.string.home_greeting_morning
    GreetingPeriod.Afternoon -> R.string.home_greeting_afternoon
    GreetingPeriod.Evening -> R.string.home_greeting_evening
}
