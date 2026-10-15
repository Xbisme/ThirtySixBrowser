package com.raumanian.thirtysix.browser.feature.entrypoint.data.datasource.local

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.raumanian.thirtysix.browser.core.domain.repository.DefaultBrowserController
import com.raumanian.thirtysix.browser.core.domain.util.UrlConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

// API 29+ asks for the browser role; below that, the default resolver of a web link, which is
// reliable there because package-visibility filtering starts at API 30. Failures answer `false`.
class AndroidDefaultBrowserController @Inject constructor(
    @ApplicationContext private val context: Context,
) : DefaultBrowserController {

    override fun isDefaultBrowser(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) holdsBrowserRole() else resolvesWebLinksToSelf()
    }.getOrDefault(false)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun holdsBrowserRole(): Boolean {
        val roles = context.getSystemService(RoleManager::class.java) ?: return false
        return roles.isRoleAvailable(RoleManager.ROLE_BROWSER) && roles.isRoleHeld(RoleManager.ROLE_BROWSER)
    }

    private fun resolvesWebLinksToSelf(): Boolean {
        val probe = Intent(Intent.ACTION_VIEW, UrlConstants.DEFAULT_BROWSER_PROBE_URL.toUri())
        val resolved = context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }
}
