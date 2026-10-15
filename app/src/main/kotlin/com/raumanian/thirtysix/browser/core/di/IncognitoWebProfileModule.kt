package com.raumanian.thirtysix.browser.core.di

import com.raumanian.thirtysix.browser.core.data.util.IncognitoProfiles
import com.raumanian.thirtysix.browser.core.domain.repository.IncognitoWebProfile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object IncognitoWebProfileModule {
    /** One process-wide object, because WebViews get their profile outside injection. */
    @Provides
    fun provideIncognitoWebProfile(): IncognitoWebProfile = IncognitoProfiles
}
