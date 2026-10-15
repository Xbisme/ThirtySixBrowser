package com.raumanian.thirtysix.browser.feature.sitepermission.data.mapper

import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.SitePermissionEntity
import com.raumanian.thirtysix.browser.core.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.core.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.core.domain.model.SitePermissionDecision
/** `null` for a value this build does not know, e.g. one written by a newer version. */
fun SitePermissionEntity.toDomainOrNull(): SitePermissionDecision? {
    val knownCapability = SiteCapability.fromStorage(capability)
    val knownDecision = SiteDecision.fromStorage(decision)
    return if (knownCapability == null || knownDecision == null) {
        null
    } else {
        SitePermissionDecision(origin, knownCapability, knownDecision, decidedAt)
    }
}
