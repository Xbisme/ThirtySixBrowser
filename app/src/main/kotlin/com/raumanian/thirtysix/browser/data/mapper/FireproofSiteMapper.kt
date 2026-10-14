package com.raumanian.thirtysix.browser.data.mapper

import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.FireproofSiteEntity
import com.raumanian.thirtysix.browser.domain.model.FireproofSite
import java.time.Instant

/** Spec 024 — Room row ↔ domain model for fireproof sites. */
fun FireproofSiteEntity.toDomain(): FireproofSite =
    FireproofSite(site = site, addedAt = Instant.ofEpochMilli(addedAt))
