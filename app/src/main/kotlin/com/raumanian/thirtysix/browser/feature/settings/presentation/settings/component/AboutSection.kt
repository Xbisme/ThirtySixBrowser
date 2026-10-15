@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.feature.settings.presentation.settings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SettingsIcon
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.SettingsIconTone
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.Spacing
/**
 * Plain text only, so it renders the same offline. The open-source notices the bundled lists'
 * licences require write addresses out and never open them.
 */
@Composable
fun AboutSection(
    appVersionName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SettingsIcon(icon = AppIcons.Shield, tone = SettingsIconTone.Primary)
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_about_version, appVersionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TEST_TAG_SETTINGS_ABOUT_VERSION),
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_about_privacy_statement),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_ABOUT_PRIVACY),
        )
        Text(
            text = stringResource(R.string.settings_about_notices_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            text = stringResource(R.string.settings_about_notice_trackers),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_ABOUT_NOTICE_TRACKERS),
        )
        Text(
            text = stringResource(R.string.settings_about_notice_public_suffix),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_ABOUT_NOTICE_PSL),
        )
        // The bundled cookie-banner rules (MPL-2.0).
        Text(
            text = stringResource(R.string.settings_about_notice_autoconsent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TEST_TAG_SETTINGS_ABOUT_NOTICE_AUTOCONSENT),
        )
    }
}

const val TEST_TAG_SETTINGS_ABOUT_VERSION: String = "settings_about_version"
const val TEST_TAG_SETTINGS_ABOUT_PRIVACY: String = "settings_about_privacy"
const val TEST_TAG_SETTINGS_ABOUT_NOTICE_TRACKERS: String = "settings_about_notice_trackers"
const val TEST_TAG_SETTINGS_ABOUT_NOTICE_PSL: String = "settings_about_notice_psl"
const val TEST_TAG_SETTINGS_ABOUT_NOTICE_AUTOCONSENT: String = "settings_about_notice_autoconsent"
