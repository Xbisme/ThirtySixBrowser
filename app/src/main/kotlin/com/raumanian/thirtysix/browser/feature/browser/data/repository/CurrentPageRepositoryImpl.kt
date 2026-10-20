package com.raumanian.thirtysix.browser.feature.browser.data.repository

import com.raumanian.thirtysix.browser.core.domain.model.Tab
import com.raumanian.thirtysix.browser.feature.browser.domain.model.CurrentPage
import com.raumanian.thirtysix.browser.feature.browser.domain.repository.CurrentPageRepository
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CurrentPageRepositoryImpl @Inject constructor(
    @param:Named("default_home_url") homeUrl: String,
) : CurrentPageRepository {
    private val state = MutableStateFlow(CurrentPage(url = homeUrl))

    override val page: StateFlow<CurrentPage> = state.asStateFlow()

    override fun setUrl(url: String) = state.update { it.copy(url = url) }

    override fun setTitle(title: String) = state.update { it.copy(title = title) }

    override fun setActiveTab(tab: Tab?) = state.update { current ->
        val incognito = tab?.isIncognito ?: false
        if (current.activeTab?.id == tab?.id) {
            current.copy(activeTab = tab, isIncognito = incognito)
        } else {
            current.copy(activeTab = tab, isIncognito = incognito, title = "", generation = current.generation + 1)
        }
    }

    override fun startNewPage() = state.update { it.copy(title = "", generation = it.generation + 1) }
}
