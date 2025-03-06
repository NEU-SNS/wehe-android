package mobi.meddle.wehe.ui.replay

import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent


@Module
@InstallIn(SingletonComponent::class)
object ReplayModule {
    @Provides
    fun provideContext(application: Application): Context = application.applicationContext
}