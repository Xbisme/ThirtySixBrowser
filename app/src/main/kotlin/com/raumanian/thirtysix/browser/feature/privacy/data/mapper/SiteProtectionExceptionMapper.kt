package com.raumanian.thirtysix.browser.feature.privacy.data.mapper

import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.SiteProtectionExceptionEntity
import com.raumanian.thirtysix.browser.core.domain.model.SiteProtectionException
import java.time.Instant

/** Spec 023 — Room row ↔ domain model for sites without protection. */
fun SiteProtectionExceptionEntity.toDomain(): SiteProtectionException =
    SiteProtectionException(site = site, addedAt = Instant.ofEpochMilli(addedAt))
