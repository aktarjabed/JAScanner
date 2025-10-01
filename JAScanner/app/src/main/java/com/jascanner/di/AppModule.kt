package com.jascanner.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.jascanner.data.database.JAScannerDatabase
import com.jascanner.data.dao.DocumentDao
import com.jascanner.data.dao.ScanSessionDao
import com.jascanner.editor.DocumentEditor
import com.jascanner.repository.DocumentRepository
import com.jascanner.repository.SettingsRepository
import com.jascanner.scanner.pdf.PDFAGenerator
import com.jascanner.scanner.pdf.PDFGenerator
import com.jascanner.security.LTVSignatureManager
import com.jascanner.utils.FileManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun provideDataStore(@ApplicationContext ctx: Context): DataStore<Preferences> = ctx.dataStore
    @Provides @Singleton fun provideDatabase(@ApplicationContext ctx: Context): JAScannerDatabase =
        Room.databaseBuilder(ctx, JAScannerDatabase::class.java, "jascanner.db").fallbackToDestructiveMigration().build()
    @Provides fun provideDocumentDao(db: JAScannerDatabase): DocumentDao = db.documentDao()
    @Provides fun provideScanSessionDao(db: JAScannerDatabase): ScanSessionDao = db.scanSessionDao()
    @Provides @Singleton fun provideFileManager(@ApplicationContext ctx: Context) = FileManager(ctx)
    @Provides @Singleton fun providePDFAGenerator(@ApplicationContext ctx: Context) = PDFAGenerator(ctx)
    @Provides @Singleton fun provideSignatureManager(@ApplicationContext ctx: Context) = LTVSignatureManager(ctx)
    @Provides @Singleton fun providePDFGenerator(@ApplicationContext ctx: Context, pdfa: PDFAGenerator, sign: LTVSignatureManager) = PDFGenerator(ctx, pdfa, sign)
    @Provides @Singleton fun provideDocRepo(docDao: DocumentDao, sessionDao: ScanSessionDao, fm: FileManager) = DocumentRepository(docDao, sessionDao, fm)
    @Provides @Singleton fun provideSettingsRepo(ds: DataStore<Preferences>) = SettingsRepository(ds)
    @Provides @Singleton fun provideDocumentEditor(): DocumentEditor = DocumentEditor()
}

