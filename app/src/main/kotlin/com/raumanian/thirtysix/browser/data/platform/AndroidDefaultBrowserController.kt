package com.raumanian.thirtysix.browser.data.platform

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.raumanian.thirtysix.browser.core.constants.UrlConstants
import com.raumanian.thirtysix.browser.domain.repository.DefaultBrowserController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Spec 020 research R8 — reads the default-browser state from the system.
 *
 *  - API 29+: the browser role (`RoleManager.ROLE_BROWSER`), the system's own record.
 *  - API 24–28: which activity the system resolves a web link to by default. Package-visibility
 *    rules start at API 30, so this query sees the real default here.
 *
 * Every platform call is wrapped; a failure answers `false` (project seam convention).
 */
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
