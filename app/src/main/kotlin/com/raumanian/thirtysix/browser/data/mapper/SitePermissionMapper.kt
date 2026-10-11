package com.raumanian.thirtysix.browser.data.mapper

import com.raumanian.thirtysix.browser.data.local.entity.SitePermissionEntity
import com.raumanian.thirtysix.browser.domain.model.SiteCapability
import com.raumanian.thirtysix.browser.domain.model.SiteDecision
import com.raumanian.thirtysix.browser.domain.model.SitePermissionDecision

/** Spec 021 — a stored row, or `null` for a value this build does not know (forward compatibility). */
fun SitePermissionEntity.toDomainOrNull(): SitePermissionDecision? {
    val knownCapability = SiteCapability.fromStorage(capability)
    val knownDecision = SiteDecision.fromStorage(decision)
    return if (knownCapability == null || knownDecision == null) {
        null
    } else {
        SitePermissionDecision(origin, knownCapability, knownDecision, decidedAt)
    }
}
