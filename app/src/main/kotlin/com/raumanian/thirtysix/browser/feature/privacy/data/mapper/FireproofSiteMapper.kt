package com.raumanian.thirtysix.browser.feature.privacy.data.mapper

import com.raumanian.thirtysix.browser.core.data.datasource.local.entity.FireproofSiteEntity
import com.raumanian.thirtysix.browser.core.domain.model.FireproofSite
import java.time.Instant

fun FireproofSiteEntity.toDomain(): FireproofSite =
    FireproofSite(site = site, addedAt = Instant.ofEpochMilli(addedAt))
