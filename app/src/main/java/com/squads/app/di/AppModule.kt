package com.squads.app.di

import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.network.httpHeaders
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageResult
import com.squads.app.data.TeamsApiClient
import com.squads.app.data.USER_AGENT
import com.squads.app.data.db.ChatDao
import com.squads.app.data.db.MailDao
import com.squads.app.data.db.SquadsDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideApplicationContext(
        @ApplicationContext context: Context,
    ): Context = context

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        baseClient: OkHttpClient,
        api: TeamsApiClient,
    ): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { baseClient }))
                add(AuthHeaderInterceptor(api))
            }.memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, 0.15)
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }.build()

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): SquadsDatabase =
        Room
            .databaseBuilder(context, SquadsDatabase::class.java, "squads.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideChatDao(db: SquadsDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideMailDao(db: SquadsDatabase): MailDao = db.mailDao()
}

private class AuthHeaderInterceptor(
    private val api: TeamsApiClient,
) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data as? String ?: return chain.proceed()
        val token = api.getTokenForUrl(url) ?: return chain.proceed()
        val newHeaders =
            chain.request.httpHeaders
                .newBuilder()
                .set("Authorization", "Bearer $token")
                .set("User-Agent", USER_AGENT)
                .build()
        val newRequest =
            chain.request
                .newBuilder()
                .httpHeaders(newHeaders)
                .build()
        return chain.withRequest(newRequest).proceed()
    }
}
