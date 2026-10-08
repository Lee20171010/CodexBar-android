package com.codexbar.android.di

import com.codexbar.android.core.data.ClaudeRepositoryImpl
import com.codexbar.android.core.data.CodexRepositoryImpl
import com.codexbar.android.core.data.GeminiRepositoryImpl
import com.codexbar.android.core.data.OpenCodeGoRepositoryImpl
import com.codexbar.android.core.data.OpenRouterRepositoryImpl
import com.codexbar.android.core.data.CopilotRepositoryImpl
import com.codexbar.android.core.data.DeepSeekRepositoryImpl
import com.codexbar.android.core.network.deepseek.DeepSeekApiService
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

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenRouterRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CopilotRepository

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeepSeekRepository

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    @DeepSeekRepository
    fun provideDeepSeekRepository(api: DeepSeekApiService): QuotaRepository = DeepSeekRepositoryImpl(api)

    @Provides
    @Singleton
    @CopilotRepository
    fun provideCopilotRepository(client: NativeCodexBarClient): QuotaRepository = CopilotRepositoryImpl(client)

    @Provides
    @Singleton
    @OpenRouterRepository
    fun provideOpenRouterRepository(client: NativeCodexBarClient): QuotaRepository = OpenRouterRepositoryImpl(client)

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
