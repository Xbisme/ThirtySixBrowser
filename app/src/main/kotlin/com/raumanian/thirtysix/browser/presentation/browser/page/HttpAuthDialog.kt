@file:Suppress("ktlint:standard:function-naming") // Composables are PascalCase by Compose convention.

package com.raumanian.thirtysix.browser.presentation.browser.page

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.raumanian.thirtysix.browser.R
import com.raumanian.thirtysix.browser.core.presentation.designsystem.component.PillStyle
import com.raumanian.thirtysix.browser.core.presentation.designsystem.theme.AppIcons
/**
 * Spec 021 US6 / C2.3 — a site's basic/digest sign-in (FR-032). What is typed lives only in this
 * dialog's state and goes to this one request; it is never stored, and leaving the dialog drops
 * it. Dismissing cancels.
 */
@Composable
fun HttpAuthDialog(request: PageRequest.HttpAuth, done: () -> Unit) {
    var username by remember(request) { mutableStateOf("") }
    var password by remember(request) { mutableStateOf("") }
    val cancel = {
        request.cancel()
        done()
    }
    SiteDialogFrame(
        title = stringResource(R.string.http_auth_title, request.host),
        body = request.realm,
        actions = listOf(
            SiteDialogAction(
                label = stringResource(R.string.http_auth_sign_in),
                style = PillStyle.Filled,
                onClick = {
                    request.proceed(username, password)
                    done()
                },
            ),
            SiteDialogAction(stringResource(R.string.http_auth_cancel), PillStyle.Text, cancel),
        ),
        onDismiss = cancel,
        glyph = AppIcons.Lock,
        dismissOnOutside = false,
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(R.string.http_auth_username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(TEST_TAG_HTTP_AUTH_USERNAME),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.http_auth_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth().testTag(TEST_TAG_HTTP_AUTH_PASSWORD),
        )
        Text(
            text = stringResource(R.string.http_auth_not_saved),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

const val TEST_TAG_HTTP_AUTH_USERNAME: String = "http_auth_username"
const val TEST_TAG_HTTP_AUTH_PASSWORD: String = "http_auth_password"
