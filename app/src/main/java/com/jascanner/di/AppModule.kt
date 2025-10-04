package com.jascanner.di

import android.app.Application
import com.jascanner.ai.ocr.RobustOcrProcessor
import com.jascanner.core.EnhancedErrorHandler
import com.jascanner.data.database.JAScannerDatabase
import com.jascanner.data.dao.DocumentDao
import com.jascanner.data.dao.ScanDao
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
    fun provideDatabase(@ApplicationContext context: android.content.Context): JAScannerDatabase {
        return JAScannerDatabase.getInstance(context)
    }
    
    @Provides
    fun provideDocumentDao(database: JAScannerDatabase): DocumentDao = database.documentDao()
    
    @Provides
    fun provideScanDao(database: JAScannerDatabase): ScanDao = database.scanDao()
    
    @Provides
    @Singleton
    fun provideEnhancedErrorHandler(): EnhancedErrorHandler = EnhancedErrorHandler()
    
    @Provides
    @Singleton
    fun provideRobustOcrProcessor(errorHandler: EnhancedErrorHandler): RobustOcrProcessor = RobustOcrProcessor(errorHandler)

    @Provides
    @Singleton
    fun provideApplication(application: Application): Application = application

    @Provides
    @Singleton
    fun provideRobustCameraController(application: Application, errorHandler: EnhancedErrorHandler): RobustCameraController {
        return RobustCameraController(application, errorHandler)
    }
}