package com.codexbar.android.di

import com.codexbar.android.core.data.ClaudeRepositoryImpl
import com.codexbar.android.core.data.CodexRepositoryImpl
import com.codexbar.android.core.data.GeminiRepositoryImpl
import com.codexbar.android.core.data.OpenCodeGoRepositoryImpl
import com.codexbar.android.core.nativecli.NativeCodexBarClient
import com.codexbar.android.core.domain.repository.QuotaRepository
import com.codexbar.android.core.network.claude.ClaudeApiService
import com.codexbar.android.core.network.claude.ClaudeTokenRefreshService
import com.codexbar.android.core.network.codex.CodexApiService
import com.codexbar.android.core.network.codex.CodexTokenRefreshService
import com.codexbar.android.core.network.gemini.GeminiApiService
import com.codexbar.android.core.network.gemini.GeminiTokenRefreshService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ClaudeRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CodexRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenCodeGoRepository

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    @OpenCodeGoRepository
    fun provideOpenCodeGoRepository(
        client: NativeCodexBarClient
    ): QuotaRepository = OpenCodeGoRepositoryImpl(client)

    @Provides
    @Singleton
    @ClaudeRepository
    fun provideClaudeRepository(
        apiService: ClaudeApiService,
        tokenRefreshService: ClaudeTokenRefreshService
    ): QuotaRepository = ClaudeRepositoryImpl(apiService, tokenRefreshService)

    @Provides
    @Singleton
    @CodexRepository
    fun provideCodexRepository(
        apiService: CodexApiService,
        tokenRefreshService: CodexTokenRefreshService
    ): QuotaRepository = CodexRepositoryImpl(apiService, tokenRefreshService)

    @Provides
    @Singleton
    @GeminiRepository
    fun provideGeminiRepository(
        apiService: GeminiApiService,
        tokenRefreshService: GeminiTokenRefreshService
    ): QuotaRepository = GeminiRepositoryImpl(apiService, tokenRefreshService)
}
