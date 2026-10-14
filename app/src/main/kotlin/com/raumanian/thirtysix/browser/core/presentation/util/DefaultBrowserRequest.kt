package com.raumanian.thirtysix.browser.core.presentation.util

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.raumanian.thirtysix.browser.MainActivity
/**
 * "Make ThirtySix the default browser": the role dialog where possible, else the default-apps
 * screen, else [onUnavailable]. The result is ignored; callers re-read the state on resume,
 * which also covers a change made later in system settings.
 */
@Composable
fun rememberDefaultBrowserRequest(onUnavailable: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnUnavailable = rememberUpdatedState(onUnavailable)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    return remember(context, launcher) {
        {
            (context.findActivity() as? MainActivity)?.markLeavingForDefaultBrowserScreen()
            val launched = defaultBrowserIntents(context).any { intent ->
                runCatching { launcher.launch(intent) }
                    .onFailure { if (it !is ActivityNotFoundException) throw it }
                    .isSuccess
            }
            if (!launched) currentOnUnavailable.value()
        }
    }
}

/** The screens to try, best first. */
private fun defaultBrowserIntents(context: Context): List<Intent> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roles = runCatching { context.getSystemService(RoleManager::class.java) }.getOrNull()
        val canRequestRole = roles != null &&
            roles.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
            !roles.isRoleHeld(RoleManager.ROLE_BROWSER)
        if (canRequestRole) add(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
    }
    add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
}
