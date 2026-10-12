package com.wifishare.di

import android.content.Context
import com.wifishare.data.api.ApiClient
import com.wifishare.data.repository.FileRepository
import com.wifishare.service.NsdDiscoveryService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApiClient(): ApiClient = ApiClient()

    @Provides
    @Singleton
    fun provideFileRepository(
        @ApplicationContext context: Context,
        apiClient: ApiClient
    ): FileRepository = FileRepository(context, apiClient)

    @Provides
    @Singleton
    fun provideNsdDiscoveryService(
        @ApplicationContext context: Context
    ): NsdDiscoveryService = NsdDiscoveryService(context)
}
