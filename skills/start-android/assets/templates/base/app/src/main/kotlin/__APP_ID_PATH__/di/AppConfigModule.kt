package __APP_ID__.di

import __APP_ID__.BuildConfig
import __PKG__.core.common.config.AppConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** The only place that reads BuildConfig: core and feature modules receive [AppConfig] instead. */
@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {
    @Provides
    @Singleton
    fun provideAppConfig(): AppConfig = AppConfig(
        baseUrl = BuildConfig.BASE_URL,
        isDebug = BuildConfig.DEBUG,
        flavor = BuildConfig.FLAVOR,
    )
}
