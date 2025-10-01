package com.jascanner.di

import android.content.Context
import com.jascanner.data.local.JAScannerDatabase
import com.jascanner.data.local.DocumentDao
import com.jascanner.editor.DocumentEditor
import com.jascanner.data.managers.PresetsManager
import com.jascanner.scanner.pdf.PDFGenerator
import com.jascanner.data.repository.DocumentRepository
import com.jascanner.scanner.thz.TerahertzScanner
import com.jascanner.security.LTVSignatureManager
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

    @Provides
    @Singleton
    fun providePdfGenerator(): PDFGenerator {
        return PDFGenerator()
    }

    @Provides
    @Singleton
    fun provideLtvSignatureManager(): LTVSignatureManager {
        return LTVSignatureManager()
    }

    @Provides
    @Singleton
    fun provideTerahertzScanner(): TerahertzScanner {
        return TerahertzScanner()
    }

    @Provides
    @Singleton
    fun provideDocumentRepository(
        documentDao: DocumentDao
    ): DocumentRepository {
        return DocumentRepository(documentDao)
    }
}