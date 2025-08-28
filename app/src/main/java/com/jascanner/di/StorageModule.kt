package com.jascanner.di

import android.content.Context
import androidx.work.WorkManager
import com.jascanner.compression.AdvancedCompressionEngine
import com.jascanner.export.ExportManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideCompressionEngine(@ApplicationContext context: Context): AdvancedCompressionEngine {
        return AdvancedCompressionEngine(context)
    }

    @Provides
    @Singleton
    fun provideExportManager(): ExportManager {
        return ExportManager()
    }
}