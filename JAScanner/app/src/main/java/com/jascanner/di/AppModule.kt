package com.jascanner.di

import android.content.Context
import com.jascanner.data.local.JAScannerDatabase
import com.jascanner.data.local.DocumentDao
import com.jascanner.editor.DocumentEditor
import com.jascanner.presentation.presets.PresetsManager
import com.jascanner.utils.FileManager
import com.jascanner.utils.PerformanceMonitor
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
    fun provideDatabase(
        @ApplicationContext context: Context
    ): JAScannerDatabase {
        return JAScannerDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideDocumentDao(database: JAScannerDatabase): DocumentDao {
        return database.documentDao()
    }

    @Provides
    @Singleton
    fun provideDocumentEditor(
        @ApplicationContext context: Context
    ): DocumentEditor {
        return DocumentEditor(context)
    }

    @Provides
    @Singleton
    fun providePresetsManager(
        @ApplicationContext context: Context
    ): PresetsManager {
        return PresetsManager(context)
    }

    @Provides
    @Singleton
    fun provideFileManager(
        @ApplicationContext context: Context
    ): FileManager {
        return FileManager(context)
    }

    @Provides
    @Singleton
    fun providePerformanceMonitor(
        @ApplicationContext context: Context
    ): PerformanceMonitor {
        return PerformanceMonitor(context)
    }
}