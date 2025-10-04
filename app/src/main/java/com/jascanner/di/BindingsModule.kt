package com.jascanner.di

import com.jascanner.ai.compression.AdvancedDocumentCompressor
import com.jascanner.ai.compression.DocumentCompressor
import com.jascanner.ai.ocr.AdvancedSmartOcrEngine
import com.jascanner.ai.ocr.SmartOcrEngine
import com.jascanner.ai.preprocess.AdvancedAiPreprocessor
import com.jascanner.ai.preprocess.AiPreprocessor
import com.jascanner.core.signature.DigitalSignatureManager
import com.jascanner.core.signature.PadesSignatureManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    abstract fun bindAiPreprocessor(
        preprocessor: AdvancedAiPreprocessor
    ): AiPreprocessor

    @Binds
    abstract fun bindSmartOcrEngine(
        ocrEngine: AdvancedSmartOcrEngine
    ): SmartOcrEngine

    @Binds
    abstract fun bindDocumentCompressor(
        compressor: AdvancedDocumentCompressor
    ): DocumentCompressor

    @Binds
    abstract fun bindDigitalSignatureManager(
        signatureManager: PadesSignatureManager
    ): DigitalSignatureManager
}