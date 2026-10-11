package com.raumanian.thirtysix.browser.presentation.browser.page

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.raumanian.thirtysix.browser.data.local.permission.OneTimeGrantStore
import com.raumanian.thirtysix.browser.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.domain.usecase.ApplySitePermissionChoiceUseCase
import com.raumanian.thirtysix.browser.domain.usecase.ResolveSitePermissionUseCase
import com.raumanian.thirtysix.browser.domain.usecase.SiteChoiceEffect
import com.raumanian.thirtysix.browser.domain.usecase.SitePermissionChoice
import com.raumanian.thirtysix.browser.domain.usecase.SitePermissionOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Spec 021 research R2 — the Android permissions each capability needs. Location: either grant counts. */
fun SiteCapability.androidPermissions(): List<String> = when (this) {
    SiteCapability.Camera -> listOf(Manifest.permission.CAMERA)
    SiteCapability.Microphone -> listOf(Manifest.permission.RECORD_AUDIO)
    SiteCapability.Location -> listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
}

/**
 * Spec 021 research R2 — decides site permission requests for the browser screen: the stored and
 * one-time decisions (domain use cases) and whether the app already holds the Android permission.
 * Holds no platform callback object; requests are passed in and answered by the caller.
 */
@HiltViewModel
class SitePermissionGate @Inject constructor(
    private val resolveSitePermission: ResolveSitePermissionUseCase,
    private val applySitePermissionChoice: ApplySitePermissionChoiceUseCase,
    private val oneTimeGrants: OneTimeGrantStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    suspend fun resolve(request: PageRequest.Permission, hasUnsupported: Boolean): SitePermissionOutcome =
        resolveSitePermission(
            origin = request.origin,
            capabilities = request.capabilities,
            isIncognito = request.isIncognito,
            hasUnsupported = hasUnsupported,
            grantedOnce = { oneTimeGrants.has(request.tabId, request.generation, request.origin, it) },
        )

    suspend fun apply(choice: SitePermissionChoice, request: PageRequest.Permission): SiteChoiceEffect {
        val effect = applySitePermissionChoice(choice, request.origin, request.askedCapabilities, request.isIncognito)
        if (effect.grantOnce) {
            oneTimeGrants.grant(request.tabId, request.generation, request.origin, request.askedCapabilities)
        }
        return effect
    }

    /** Whether the app holds the Android permission for every capability (location: fine or coarse). */
    fun holdsSystemPermissions(capabilities: Set<SiteCapability>): Boolean = capabilities.all { capability ->
        capability.androidPermissions().any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** A tab's page changed: its one-time grants end (FR-003). */
    fun onPageChanged(tabId: Long) = oneTimeGrants.dropForTab(tabId)
}
