package com.raumanian.thirtysix.browser.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope

internal const val ROOT = "com.raumanian.thirtysix.browser"

/**
 * Files that broke a rule before the rule existed, each with the ticket that removes it.
 * New code never goes here: fix it instead.
 */
internal val KNOWN_VIOLATIONS: Set<String> = emptySet()

internal fun productionScope(): KoScope = Konsist.scopeFromProduction(moduleName = "app").slice { file ->
    KNOWN_VIOLATIONS.none { file.path.endsWith(it) }
}
