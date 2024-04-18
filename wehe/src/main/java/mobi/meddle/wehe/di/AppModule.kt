package mobi.meddle.wehe.di

import android.app.Application
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mobi.meddle.wehe.data.DefaultAppsRepository
import mobi.meddle.wehe.data.LocalAppsSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppsSource(context: Application): LocalAppsSource {
        return LocalAppsSource(context)
    }

    @Provides
    @Singleton
    fun provideAppsRepository(appsSource: LocalAppsSource): DefaultAppsRepository {
        return DefaultAppsRepository(appsSource)
    }

}