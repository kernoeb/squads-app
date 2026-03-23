package com.squads.app.di

import android.content.Context
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
    fun provideApplicationContext(@ApplicationContext context: Context): Context = context

    // TODO: Provide SquadsClient (UniFFI) once native .so is built
    // @Provides @Singleton
    // fun provideSquadsClient(@ApplicationContext context: Context): SquadsClient { ... }
}
