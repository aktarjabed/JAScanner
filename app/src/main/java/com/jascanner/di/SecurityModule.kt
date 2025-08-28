package com.jascanner.di

import android.content.Context
import com.jascanner.security.BiometricHelper
import com.jascanner.security.SecurityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideBiometricHelper(@ApplicationContext context: Context): BiometricHelper {
        return BiometricHelper(context)
    }

    @Provides
    @Singleton
    fun provideSecurityManager(
        @ApplicationContext context: Context,
        biometricHelper: BiometricHelper
    ): SecurityManager {
        return SecurityManager(context, biometricHelper)
    }
}