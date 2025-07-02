package mobi.meddle.wehe.di

import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Dagger Hilt module that provides application-level dependencies related to replay functionality.
 *
 * This module is installed in the [SingletonComponent], meaning all dependencies provided here
 * will live as long as the application does.
 *
 * Provides:
 * - [Context]: Supplies the application context, which can be injected wherever needed.
 *
 * Usage:
 * Inject `Context` into other Hilt-enabled components (e.g., ViewModels, Services) using `@Inject`.
 *
 */
@Module
@InstallIn(SingletonComponent::class)
object ReplayModule {
    @Provides
    fun provideContext(application: Application): Context = application.applicationContext
}