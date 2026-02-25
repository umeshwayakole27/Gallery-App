package com.uw.simplegallery.di

import android.content.Context
import com.uw.simplegallery.data.repository.MediaManager
import com.uw.simplegallery.data.repository.MediaRepository
import com.uw.simplegallery.data.repository.MediaRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for application-level dependency bindings.
 *
 * Installed in [SingletonComponent] so that all provided dependencies
 * live for the entire application lifecycle.
 *
 * Provides:
 * - [MediaManager]: Singleton data source wrapping MediaStore queries
 * - [MediaRepository]: Singleton repository interface bound to [MediaRepositoryImpl]
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMediaManager(
        @ApplicationContext context: Context
    ): MediaManager {
        return MediaManager(context)
    }

    @Provides
    @Singleton
    fun provideMediaRepository(
        mediaManager: MediaManager
    ): MediaRepository {
        return MediaRepositoryImpl(mediaManager)
    }
}
